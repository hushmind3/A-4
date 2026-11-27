FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY gateway.py journal.py worker.py ./
ENV PYTHONUNBUFFERED=1
# The worker registers with LiveKit; the gateway is the authenticated HTTPS API.
CMD ["sh","-c","python worker.py start & exec uvicorn gateway:app --host 0.0.0.0 --port 8765"]
