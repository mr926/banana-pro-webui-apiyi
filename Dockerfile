FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV BANANA_PRO_PORT=8787
ENV BANANA_PRO_HOST=0.0.0.0

RUN pip install --no-cache-dir Pillow pillow-heif

COPY server.py /app/server.py
COPY public /app/public
COPY data /app/data
COPY .env.example /app/.env.example

EXPOSE 8787

CMD ["python", "server.py"]
