# LifeMemory Circle / 好友朋友圈测试后台

This is a separate, self-hosted service. **No public server is provided by the APK or GitHub Pages.** The private journal, transcripts and AI keys are never auto-published to Circle. Only explicitly posted text/photos are sent here. This is an early small-group beta, not a WeChat-compatible client.

这是独立的自部署后台。**安装 APK 不等于已有公网朋友圈；GitHub Pages 只能托管宣传页。** 所有好友需连接同一个后台。私人日记、录音转写和 AI 密钥不会自动上传；只有主动发布的图文进入这里。

## Local verification / 本机联调

Use Python 3.12 and a virtual environment in this directory:

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements-test.txt
$env:SOCIAL_SIGNUP_CODE='choose-a-private-group-registration-code'
.\.venv\Scripts\python -m uvicorn app:app --host 127.0.0.1 --port 8787 --no-proxy-headers
```

For macOS/Linux set the same environment variable with `export` and run `.venv/bin/python`. `GET /health` should return `lifememory-social-v1`. Without `SOCIAL_SIGNUP_CODE`, registration is disabled. This invitation code is a group signup code, **not a DeepSeek key**.

For a USB-connected Android test phone run `adb -s DEVICE_SERIAL reverse tcp:8787 tcp:8787`, then enter `http://127.0.0.1:8787` in the app's Circle tab. Android Emulator can use `http://10.0.2.2:8787`. These are loopback-only development exceptions; remote servers require HTTPS. Register two accounts, send a username-based request, accept it on the other account, post a photo and check comments. No demo friends are fabricated in the app.

手机本机联调可使用 USB 反向端口，或模拟器的 `10.0.2.2`。远程访问必须使用 HTTPS；不支持把任意局域网 HTTP 地址作为生产服务。账号至少 3 位、密码至少 10 位。邀请码由后台部署者设置后分发。

## HTTPS deployment / 公网部署

Requires a server with Docker Compose and a domain whose DNS points at it, with inbound TCP 80/443 available. Copy `config.example` to `.env`, set the real `CIRCLE_DOMAIN`, and generate a signup code with `python -c "import secrets; print(secrets.token_urlsafe(24))"`. Keep `.env` out of Git.

```sh
docker compose up -d --build
docker compose logs --tail=50
```

Caddy terminates HTTPS and limits request bodies; only Caddy publishes ports. Do not expose Uvicorn directly with the container's trusted-proxy settings. Keep one API worker for the included in-memory login/signup/action rate limits. Multi-instance rate limiting, monitoring and production load testing are not included. Docker configuration is provided for deployment; a Docker/public-domain deployment has not been performed in this workspace.

App setup: Circle → server origin `https://YOUR_DOMAIN` → username/password → register with the server invitation code. Share your server address and exact username with friends. Both users must confirm a friendship before friends-only posts can be read.

## Visibility, data and limits / 可见范围与边界

- Friends-only posts are visible to their author and accepted friends of that author; pending requests and friends-of-friends confer no access. Private posts are visible only to the author but are **still stored on the server**.
- All people allowed to view a post can read its comments; this beta does not implement WeChat's mutual-friend-only comment filtering. UI labels this distinction.
- Photos use the same authorization as their post. The service re-encodes photos as JPEG without EXIF metadata. Up to nine photos; each submitted image must fit the size/dimension limits. App compression changes image quality; retain originals in your journal.
- Removing a friend revokes future server reads, including photos, but cannot retract a screenshot or a photo someone already saved.
- Passwords use salted PBKDF2; opaque session tokens are stored hashed on the server, and encrypted via Android Keystore in the app. Sessions expire after 30 days. Sign-out revokes the current session when online; local sign-out can still complete during an outage.
- The server owner can access server data: this is **not end-to-end encrypted**. SQLite data and backups contain social content. Use trusted hosting and retain backups of the `circle_data` volume.
- Delete-post removes its photos/comments/likes. Account deletion removes server posts, interactions and friendships; local private journal data stays on the phone. No SMS/email verification, password recovery, push notifications, video, or WeChat login/import is implemented in this test version.
- Shared content has its own server database; existing journal ZIP backups do not include it. Back up SQLite with its backup API or stop the API before copying the data volume. Do not copy only the live `.db` while ignoring WAL files.
- The feed loads 20 posts per page; detail shows the latest 100 comments. Drafts are held only for the current app session, not an offline publishing queue.

## Tests

```sh
python -m pytest -q
```

Tests use temporary databases and synthetic accounts/photos. They cover authorization, pending requests, non-transitive friendships, private posts, comment/like permissions, media access after unfriend/delete, pagination, session revocation, account deletion, input masking and login limits. The test client currently emits a Starlette/httpx deprecation warning; passing API tests are not a real-phone or public-hosting result.
