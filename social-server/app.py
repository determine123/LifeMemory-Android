"""Small self-hosted friends-only service; unrelated to the private journal database."""
import base64
import hashlib
import hmac
import io
import os
import re
import secrets
import sqlite3
import threading
import time
from collections import defaultdict, deque
from contextlib import contextmanager
from pathlib import Path
from typing import Literal

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from PIL import Image, ImageOps, UnidentifiedImageError


class Credentials(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=r"^[a-zA-Z0-9_]+$")
    password: str = Field(min_length=10, max_length=128)
    display_name: str = Field(default="", max_length=40)
    invite_code: str = Field(default="", max_length=256)


class FriendRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32)


class Post(BaseModel):
    text: str = Field(default="", max_length=2000)
    visibility: Literal["friends", "private"] = "friends"
    photos: list[str] = Field(default_factory=list, max_length=9)


class Comment(BaseModel):
    text: str = Field(min_length=1, max_length=500)


class Like(BaseModel):
    liked: bool


def create_app(database=None, signup_code=None):
    db_path = Path(database or os.getenv("SOCIAL_DB", "data/social.db"))
    db_path.parent.mkdir(parents=True, exist_ok=True)
    code = signup_code if signup_code is not None else os.getenv("SOCIAL_SIGNUP_CODE", "")

    @contextmanager
    def db():
        connection = sqlite3.connect(db_path, timeout=15)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    with db() as d:
        d.executescript("""
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS users(
          id INTEGER PRIMARY KEY, username TEXT UNIQUE NOT NULL,
          display_name TEXT NOT NULL, salt TEXT NOT NULL, password_hash TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS sessions(
          token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users ON DELETE CASCADE,
          expires INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS friends(
          id INTEGER PRIMARY KEY, sender INTEGER NOT NULL REFERENCES users ON DELETE CASCADE,
          recipient INTEGER NOT NULL REFERENCES users ON DELETE CASCADE,
          low INTEGER NOT NULL, high INTEGER NOT NULL, status TEXT NOT NULL,
          UNIQUE(low,high), CHECK(sender!=recipient));
        CREATE TABLE IF NOT EXISTS posts(
          id INTEGER PRIMARY KEY AUTOINCREMENT, author INTEGER NOT NULL REFERENCES users ON DELETE CASCADE,
          text TEXT NOT NULL, visibility TEXT NOT NULL, created INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS photos(
          id INTEGER PRIMARY KEY AUTOINCREMENT, post_id INTEGER NOT NULL REFERENCES posts ON DELETE CASCADE,
          jpeg BLOB NOT NULL);
        CREATE TABLE IF NOT EXISTS comments(
          id INTEGER PRIMARY KEY AUTOINCREMENT, post_id INTEGER NOT NULL REFERENCES posts ON DELETE CASCADE,
          author INTEGER NOT NULL REFERENCES users ON DELETE CASCADE, text TEXT NOT NULL, created INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS likes(
          post_id INTEGER NOT NULL REFERENCES posts ON DELETE CASCADE,
          user_id INTEGER NOT NULL REFERENCES users ON DELETE CASCADE, PRIMARY KEY(post_id,user_id));
        CREATE INDEX IF NOT EXISTS posts_author ON posts(author,id);
        CREATE INDEX IF NOT EXISTS comments_post ON comments(post_id,id);
        """)

    api = FastAPI(title="LifeMemory Circle", docs_url=None, redoc_url=None)
    attempts = defaultdict(deque)
    rate_lock = threading.Lock()

    @api.exception_handler(RequestValidationError)
    async def invalid_input(request, error):
        # Pydantic error details can contain submitted passwords; never reflect inputs.
        return JSONResponse({"detail": "Invalid input; check fields and length limits"}, status_code=422)

    @api.middleware("http")
    async def body_limit(request: Request, call_next):
        if request.method in {"POST", "PUT", "PATCH"}:
            try:
                length = int(request.headers.get("content-length", "-1"))
            except ValueError:
                return JSONResponse({"detail": "Invalid content length"}, status_code=400)
            if length < 0:
                return JSONResponse({"detail": "Content-Length required"}, status_code=411)
            if length > 12 * 1024 * 1024:
                return JSONResponse({"detail": "Request too large"}, status_code=413)
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    def rate(request, kind, maximum, window=60):
        key = (request.client.host if request.client else "unknown", kind)
        now = time.monotonic()
        with rate_lock:
            queue = attempts[key]
            while queue and queue[0] <= now - window:
                queue.popleft()
            if len(queue) >= maximum:
                raise HTTPException(429, "Too many attempts; try later")
            queue.append(now)

    def user(authorization: str = Header(default="")):
        if not authorization.startswith("Bearer "):
            raise HTTPException(401, "Sign in required")
        digest = hashlib.sha256(authorization[7:].encode()).hexdigest()
        with db() as d:
            row = d.execute("SELECT u.id,u.username,u.display_name FROM users u JOIN sessions s "
                            "ON s.user_id=u.id WHERE s.token_hash=? AND s.expires>?",
                            (digest, int(time.time()))).fetchone()
        if row is None:
            raise HTTPException(401, "Session expired; sign in again")
        return dict(row)

    def session(d, row):
        token = secrets.token_urlsafe(32)
        expires = int(time.time()) + 30 * 86400
        d.execute("DELETE FROM sessions WHERE expires<=?", (int(time.time()),))
        d.execute("INSERT INTO sessions VALUES(?,?,?)",
                  (hashlib.sha256(token.encode()).hexdigest(), row["id"], expires))
        return {"token": token, "expires": expires,
                "user": {k: row[k] for k in ("id", "username", "display_name")}}

    def visible(d, post_id, viewer):
        row = d.execute("SELECT * FROM posts WHERE id=?", (post_id,)).fetchone()
        if row and (row["author"] == viewer or
                    (row["visibility"] == "friends" and d.execute(
                        "SELECT 1 FROM friends WHERE low=? AND high=? AND status='accepted'",
                        (min(row["author"], viewer), max(row["author"], viewer))).fetchone())):
            return row
        raise HTTPException(404, "Post unavailable")

    def post_json(d, row, viewer):
        author = d.execute("SELECT id,username,display_name FROM users WHERE id=?", (row["author"],)).fetchone()
        return {"id": row["id"], "text": row["text"], "visibility": row["visibility"],
                "created": row["created"], "author": dict(author),
                "photos": [r[0] for r in d.execute("SELECT id FROM photos WHERE post_id=? ORDER BY id", (row["id"],))],
                "likes": d.execute("SELECT count(*) FROM likes WHERE post_id=?", (row["id"],)).fetchone()[0],
                "liked": bool(d.execute("SELECT 1 FROM likes WHERE post_id=? AND user_id=?", (row["id"], viewer)).fetchone()),
                "comments": d.execute("SELECT count(*) FROM comments WHERE post_id=?", (row["id"],)).fetchone()[0]}

    def image(encoded):
        if len(encoded) > 1400000:
            raise HTTPException(413, "Photo too large")
        try:
            raw = base64.b64decode(encoded, validate=True)
            with Image.open(io.BytesIO(raw)) as source:
                if source.width * source.height > 16000000:
                    raise HTTPException(413, "Photo dimensions too large")
                source = ImageOps.exif_transpose(source)
                source.thumbnail((1600, 1600))
                output = io.BytesIO()
                source.convert("RGB").save(output, "JPEG", quality=85)
                return output.getvalue()
        except HTTPException:
            raise
        except (ValueError, OSError, UnidentifiedImageError, Image.DecompressionBombError):
            raise HTTPException(422, "Invalid photo")

    @api.get("/health")
    def health():
        return {"status": "ok", "api": "lifememory-social-v1"}

    @api.post("/v1/register")
    def register(body: Credentials, request: Request):
        rate(request, "register", 10, 3600)
        if not code:
            raise HTTPException(503, "Registration disabled; configure server invitation code")
        if not hmac.compare_digest(code.encode(), body.invite_code.encode()):
            raise HTTPException(403, "Invalid server invitation code")
        salt = secrets.token_hex(16)
        password_hash = hashlib.pbkdf2_hmac("sha256", body.password.encode(), bytes.fromhex(salt), 600000).hex()
        with db() as d:
            try:
                cursor = d.execute("INSERT INTO users(username,display_name,salt,password_hash) VALUES(?,?,?,?)",
                                   (body.username.lower(), body.display_name.strip() or body.username, salt, password_hash))
            except sqlite3.IntegrityError:
                raise HTTPException(409, "Username unavailable")
            return session(d, d.execute("SELECT * FROM users WHERE id=?", (cursor.lastrowid,)).fetchone())

    @api.post("/v1/login")
    def login(body: Credentials, request: Request):
        rate(request, "login", 20)
        with db() as d:
            row = d.execute("SELECT * FROM users WHERE username=?", (body.username.lower(),)).fetchone()
            salt = bytes.fromhex(row["salt"]) if row else bytes(16)
            digest = hashlib.pbkdf2_hmac("sha256", body.password.encode(), salt, 600000).hex()
            if row is None or not hmac.compare_digest(digest, row["password_hash"]):
                raise HTTPException(401, "Invalid username or password")
            return session(d, row)

    @api.get("/v1/me")
    def me(who=Depends(user)):
        return who

    @api.post("/v1/logout")
    def logout(who=Depends(user), authorization: str = Header()):
        with db() as d:
            d.execute("DELETE FROM sessions WHERE token_hash=?", (hashlib.sha256(authorization[7:].encode()).hexdigest(),))
        return {"ok": True}

    @api.delete("/v1/account")
    def delete_account(who=Depends(user)):
        with db() as d:
            d.execute("DELETE FROM users WHERE id=?", (who["id"],))
        return {"ok": True}

    @api.get("/v1/friends")
    def friends(who=Depends(user)):
        with db() as d:
            result = []
            for row in d.execute("SELECT * FROM friends WHERE sender=? OR recipient=?", (who["id"], who["id"])):
                other = row["recipient"] if row["sender"] == who["id"] else row["sender"]
                profile = dict(d.execute("SELECT id,username,display_name FROM users WHERE id=?", (other,)).fetchone())
                result.append({"request_id": row["id"], "status": row["status"],
                               "incoming": row["recipient"] == who["id"], "user": profile})
            return {"items": result}

    @api.post("/v1/friends/requests")
    def request_friend(body: FriendRequest, who=Depends(user)):
        with db() as d:
            other = d.execute("SELECT id FROM users WHERE username=?", (body.username.lower(),)).fetchone()
            if other is None:
                raise HTTPException(404, "Username not found")
            uid, other = who["id"], other[0]
            if uid == other:
                raise HTTPException(422, "Cannot add yourself")
            try:
                d.execute("INSERT INTO friends(sender,recipient,low,high,status) VALUES(?,?,?,?,?)",
                          (uid, other, min(uid, other), max(uid, other), "pending"))
            except sqlite3.IntegrityError:
                raise HTTPException(409, "Friendship or request already exists; check incoming requests")
        return {"ok": True}

    @api.post("/v1/friends/requests/{request_id}/accept")
    def accept(request_id: int, who=Depends(user)):
        with db() as d:
            changed = d.execute("UPDATE friends SET status='accepted' WHERE id=? AND recipient=? AND status='pending'",
                                (request_id, who["id"])).rowcount
            if not changed:
                raise HTTPException(404, "Incoming request unavailable")
        return {"ok": True}

    @api.delete("/v1/friends/{other}")
    def remove_friend(other: int, who=Depends(user)):
        with db() as d:
            d.execute("DELETE FROM friends WHERE low=? AND high=?", (min(other, who["id"]), max(other, who["id"])))
        return {"ok": True}

    @api.post("/v1/posts")
    def publish(body: Post, request: Request, who=Depends(user)):
        rate(request, "post:" + str(who["id"]), 30)
        if not body.text.strip() and not body.photos:
            raise HTTPException(422, "Write something or choose a photo")
        pictures = [image(value) for value in body.photos]
        with db() as d:
            cursor = d.execute("INSERT INTO posts(author,text,visibility,created) VALUES(?,?,?,?)",
                               (who["id"], body.text.strip(), body.visibility, int(time.time())))
            pid = cursor.lastrowid
            for picture in pictures:
                d.execute("INSERT INTO photos(post_id,jpeg) VALUES(?,?)", (pid, picture))
            return post_json(d, visible(d, pid, who["id"]), who["id"])

    @api.get("/v1/feed")
    def feed(before: int = 9223372036854775807, who=Depends(user)):
        with db() as d:
            rows = d.execute("SELECT p.* FROM posts p WHERE p.id<? AND (p.author=? OR "
                             "(p.visibility='friends' AND EXISTS(SELECT 1 FROM friends f WHERE "
                             "f.status='accepted' AND f.low=min(p.author,?) AND f.high=max(p.author,?)))) "
                             "ORDER BY p.id DESC LIMIT 20", (before, who["id"], who["id"], who["id"])).fetchall()
            return {"items": [post_json(d, row, who["id"]) for row in rows],
                    "next": rows[-1]["id"] if len(rows) == 20 else None}

    @api.get("/v1/posts/{pid}")
    def detail(pid: int, who=Depends(user)):
        with db() as d:
            result = post_json(d, visible(d, pid, who["id"]), who["id"])
            result["replies"] = [dict(row) for row in d.execute(
                "SELECT c.id,c.author,c.text,c.created,u.display_name FROM comments c JOIN users u "
                "ON c.author=u.id WHERE c.post_id=? ORDER BY c.id DESC LIMIT 100", (pid,))]
            return result

    @api.get("/v1/photos/{photo_id}")
    def photo(photo_id: int, who=Depends(user)):
        with db() as d:
            row = d.execute("SELECT * FROM photos WHERE id=?", (photo_id,)).fetchone()
            if row is None:
                raise HTTPException(404, "Photo unavailable")
            visible(d, row["post_id"], who["id"])
            return Response(row["jpeg"], media_type="image/jpeg", headers={"Cache-Control": "no-store"})

    @api.post("/v1/posts/{pid}/like")
    def like(pid: int, body: Like, who=Depends(user)):
        with db() as d:
            visible(d, pid, who["id"])
            if body.liked:
                d.execute("INSERT OR IGNORE INTO likes VALUES(?,?)", (pid, who["id"]))
            else:
                d.execute("DELETE FROM likes WHERE post_id=? AND user_id=?", (pid, who["id"]))
        return {"ok": True}

    @api.post("/v1/posts/{pid}/comments")
    def comment(pid: int, body: Comment, request: Request, who=Depends(user)):
        rate(request, "comment:" + str(who["id"]), 60)
        if not body.text.strip():
            raise HTTPException(422, "Comment is empty")
        with db() as d:
            visible(d, pid, who["id"])
            cursor = d.execute("INSERT INTO comments(post_id,author,text,created) VALUES(?,?,?,?)",
                               (pid, who["id"], body.text.strip(), int(time.time())))
            return {"id": cursor.lastrowid}

    @api.delete("/v1/comments/{cid}")
    def remove_comment(cid: int, who=Depends(user)):
        with db() as d:
            row = d.execute("SELECT c.author,p.author owner FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.id=?", (cid,)).fetchone()
            if row is None or who["id"] not in (row["author"], row["owner"]):
                raise HTTPException(404, "Comment unavailable")
            d.execute("DELETE FROM comments WHERE id=?", (cid,))
        return {"ok": True}

    @api.delete("/v1/posts/{pid}")
    def remove_post(pid: int, who=Depends(user)):
        with db() as d:
            if not d.execute("DELETE FROM posts WHERE id=? AND author=?", (pid, who["id"])).rowcount:
                raise HTTPException(404, "Own post unavailable")
        return {"ok": True}

    return api


app = create_app()
