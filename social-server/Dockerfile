FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home circle && mkdir /data && chown circle:circle /data
COPY app.py .
USER circle
ENV SOCIAL_DB=/data/social.db
EXPOSE 8787
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8787", "--workers", "1", "--proxy-headers", "--forwarded-allow-ips", "*"]
