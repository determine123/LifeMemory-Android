"""Actual loopback HTTP contract check with synthetic accounts; server is stopped afterwards."""
import json
import os
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path


def main():
    with tempfile.TemporaryDirectory(prefix="lifememory-circle-") as folder:
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0)); port = sock.getsockname()[1]
        env = dict(os.environ, SOCIAL_DB=str(Path(folder) / "social.db"), SOCIAL_SIGNUP_CODE=secrets.token_urlsafe(24))
        server = subprocess.Popen([sys.executable, "-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", str(port), "--no-proxy-headers"],
                                  cwd=Path(__file__).parent, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        def call(method, path, data=None, token=None):
            headers = {"Content-Type": "application/json"}
            if token: headers["Authorization"] = "Bearer " + token
            request = urllib.request.Request(f"http://127.0.0.1:{port}" + path,
                                             data=json.dumps(data).encode() if data is not None else None,
                                             headers=headers, method=method)
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=5) as response:
                return json.load(response)
        try:
            for _ in range(50):
                try:
                    if call("GET", "/health")["status"] == "ok": break
                except (OSError, ValueError): time.sleep(0.1)
            else: raise AssertionError("HTTP service did not start")
            accounts = [call("POST", "/v1/register", {"username": name, "password": "smoke-only-password-42", "invite_code": env["SOCIAL_SIGNUP_CODE"]})
                        for name in ("alice", "bob")]
            alice, bob = [a["token"] for a in accounts]
            call("POST", "/v1/friends/requests", {"username": "bob"}, alice)
            request_id = call("GET", "/v1/friends", token=bob)["items"][0]["request_id"]
            call("POST", f"/v1/friends/requests/{request_id}/accept", {}, bob)
            post = call("POST", "/v1/posts", {"text": "Synthetic shared moment"}, alice)
            assert call("GET", "/v1/feed", token=bob)["items"][0]["id"] == post["id"]
            call("POST", f'/v1/posts/{post["id"]}/comments', {"text": "Received"}, bob)
            assert call("GET", f'/v1/posts/{post["id"]}', token=alice)["replies"][0]["text"] == "Received"
            print("PASS actual HTTP: registration, friendship approval, feed and comments")
        finally:
            server.terminate()
            try: server.wait(timeout=10)
            except subprocess.TimeoutExpired: server.kill(); server.wait()


if __name__ == "__main__":
    main()
