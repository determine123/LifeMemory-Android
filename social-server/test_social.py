import base64
import io
import sqlite3

import pytest
from fastapi.testclient import TestClient
from PIL import Image
from app import create_app


@pytest.fixture
def circle(tmp_path):
    path = tmp_path / "circle.db"
    client = TestClient(create_app(path, "test-invite"))
    users = {}
    for name in ("alice", "bob", "carol"):
        response = client.post("/v1/register", json={"username": name, "password": "test-password-42",
                               "display_name": name.title(), "invite_code": "test-invite"})
        assert response.status_code == 200, response.text
        value = response.json()
        users[name] = (value["user"]["id"], {"Authorization": "Bearer " + value["token"]})
    return client, users, path


def befriend(client, users, first="alice", second="bob"):
    assert client.post("/v1/friends/requests", json={"username": second}, headers=users[first][1]).status_code == 200
    request = client.get("/v1/friends", headers=users[second][1]).json()["items"][0]
    assert client.post(f'/v1/friends/requests/{request["request_id"]}/accept', json={}, headers=users[second][1]).status_code == 200


def publish(client, header, **fields):
    response = client.post("/v1/posts", json={"text": "A real shared moment", **fields}, headers=header)
    assert response.status_code == 200, response.text
    return response.json()


def test_accounts_require_auth_and_do_not_store_plain_credentials(circle):
    client, users, path = circle
    assert client.get("/v1/feed").status_code == 401
    assert client.get("/v1/me", headers=users["alice"][1]).json()["username"] == "alice"
    assert client.post("/v1/login", json={"username": "alice", "password": "wrong-password"}).status_code == 401
    with sqlite3.connect(path) as d:
        assert d.execute("SELECT password_hash FROM users").fetchone()[0] != "test-password-42"
        assert d.execute("SELECT token_hash FROM sessions").fetchone()[0] != users["alice"][1]["Authorization"][7:]


def test_pending_request_does_not_expose_posts_and_cannot_self_accept(circle):
    client, users, _ = circle
    post = publish(client, users["alice"][1])
    client.post("/v1/friends/requests", json={"username": "bob"}, headers=users["alice"][1])
    request_id = client.get("/v1/friends", headers=users["bob"][1]).json()["items"][0]["request_id"]
    assert client.post(f"/v1/friends/requests/{request_id}/accept", json={}, headers=users["alice"][1]).status_code == 404
    assert client.get(f'/v1/posts/{post["id"]}', headers=users["bob"][1]).status_code == 404
    assert client.get("/v1/feed", headers=users["bob"][1]).json()["items"] == []


def test_friends_can_view_but_strangers_and_friends_of_friends_cannot(circle):
    client, users, _ = circle
    befriend(client, users)
    befriend(client, users, "bob", "carol")
    post = publish(client, users["alice"][1])
    assert client.get(f'/v1/posts/{post["id"]}', headers=users["bob"][1]).status_code == 200
    assert client.get(f'/v1/posts/{post["id"]}', headers=users["carol"][1]).status_code == 404
    assert client.get("/v1/feed", headers=users["carol"][1]).json()["items"] == []


def test_private_post_stays_owner_only(circle):
    client, users, _ = circle
    befriend(client, users)
    post = publish(client, users["alice"][1], visibility="private")
    assert client.get(f'/v1/posts/{post["id"]}', headers=users["alice"][1]).status_code == 200
    assert client.get(f'/v1/posts/{post["id"]}', headers=users["bob"][1]).status_code == 404


def test_likes_comments_and_owner_deletion_are_authorized(circle):
    client, users, _ = circle
    befriend(client, users)
    post = publish(client, users["alice"][1]); route = f'/v1/posts/{post["id"]}'
    for _ in range(2):
        assert client.post(route + "/like", json={"liked": True}, headers=users["bob"][1]).status_code == 200
    assert client.get(route, headers=users["alice"][1]).json()["likes"] == 1
    assert client.post(route + "/comments", json={"text": "intrusion"}, headers=users["carol"][1]).status_code == 404
    comment = client.post(route + "/comments", json={"text": "Nice!"}, headers=users["bob"][1]).json()
    assert client.delete(f'/v1/comments/{comment["id"]}', headers=users["carol"][1]).status_code == 404
    assert client.delete(route, headers=users["bob"][1]).status_code == 404
    assert client.delete(f'/v1/comments/{comment["id"]}', headers=users["alice"][1]).status_code == 200
    assert client.delete(route, headers=users["alice"][1]).status_code == 200


def test_photos_have_same_visibility_and_are_removed_with_post(circle):
    client, users, _ = circle
    befriend(client, users)
    output = io.BytesIO(); Image.new("RGB", (10, 10), "green").save(output, "PNG")
    post = publish(client, users["alice"][1], photos=[base64.b64encode(output.getvalue()).decode()])
    photo = f'/v1/photos/{post["photos"][0]}'
    assert client.get(photo, headers=users["bob"][1]).headers["content-type"] == "image/jpeg"
    assert client.get(photo, headers=users["carol"][1]).status_code == 404
    client.delete(f'/v1/friends/{users["bob"][0]}', headers=users["alice"][1])
    assert client.get(photo, headers=users["bob"][1]).status_code == 404
    assert client.get(f'/v1/posts/{post["id"]}', headers=users["bob"][1]).status_code == 404
    client.delete(f'/v1/posts/{post["id"]}', headers=users["alice"][1])
    assert client.get(photo, headers=users["alice"][1]).status_code == 404


def test_feed_pagination_has_no_duplicate_or_missing_posts(circle):
    client, users, _ = circle
    for number in range(23):
        publish(client, users["alice"][1], text=str(number))
    first = client.get("/v1/feed", headers=users["alice"][1]).json()
    second = client.get("/v1/feed", params={"before": first["next"]}, headers=users["alice"][1]).json()
    ids = [p["id"] for p in first["items"] + second["items"]]
    assert len(ids) == len(set(ids)) == 23


def test_logout_revokes_session_and_account_delete_cascades(circle):
    client, users, path = circle
    publish(client, users["alice"][1])
    assert client.post("/v1/logout", json={}, headers=users["bob"][1]).status_code == 200
    assert client.get("/v1/me", headers=users["bob"][1]).status_code == 401
    assert client.delete("/v1/account", headers=users["alice"][1]).status_code == 200
    assert client.get("/v1/feed", headers=users["alice"][1]).status_code == 401
    with sqlite3.connect(path) as d:
        assert d.execute("SELECT count(*) FROM posts").fetchone()[0] == 0


def test_invalid_input_does_not_echo_password_and_invites_are_required(circle):
    client, users, _ = circle
    response = client.post("/v1/register", json={"username": "x", "password": "secret"})
    assert response.status_code == 422 and "secret" not in response.text
    assert client.post("/v1/register", json={"username": "david", "password": "test-password-42", "invite_code": "bad"}).status_code == 403
    assert client.post("/v1/posts", json={"text": "", "photos": []}, headers=users["alice"][1]).status_code == 422
    assert client.post("/v1/posts", json={"photos": ["invalid"]}, headers=users["alice"][1]).status_code == 422


def test_registration_disabled_without_owner_code_and_login_throttled(tmp_path):
    client = TestClient(create_app(tmp_path / "disabled.db", ""))
    assert client.post("/v1/register", json={"username": "alice", "password": "test-password-42"}).status_code == 503
    for _ in range(20):
        assert client.post("/v1/login", json={"username": "alice", "password": "test-password-42"}).status_code == 401
    assert client.post("/v1/login", json={"username": "alice", "password": "test-password-42"}).status_code == 429


def test_photo_orientation_is_applied_and_metadata_is_not_shared(circle):
    client, users, _ = circle
    source = Image.new("RGB", (20, 10), "blue")
    exif = source.getexif(); exif[274] = 6; exif[271] = "private-camera-marker"
    output = io.BytesIO(); source.save(output, "JPEG", exif=exif)
    post = publish(client, users["alice"][1], photos=[base64.b64encode(output.getvalue()).decode()])
    photo = client.get(f'/v1/photos/{post["photos"][0]}', headers=users["alice"][1])
    with Image.open(io.BytesIO(photo.content)) as normalized:
        assert normalized.size == (10, 20)
        assert 271 not in normalized.getexif() and 274 not in normalized.getexif()


def test_body_and_photo_count_limits_are_enforced(circle):
    client, users, _ = circle
    assert client.post("/v1/posts", content="{}", headers={**users["alice"][1], "Content-Length": str(13 * 1024 * 1024)}).status_code == 413
    assert client.post("/v1/posts", content=iter([b'{}']), headers=users["alice"][1]).status_code == 411
    assert client.post("/v1/posts", json={"photos": ["x"] * 10}, headers=users["alice"][1]).status_code == 422
