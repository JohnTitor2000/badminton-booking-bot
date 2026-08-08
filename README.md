# Badminton Booking Bot

Telegram-бот для записи на бадминтон (Spring Boot + PostgreSQL).

## Railway

1. Создай проект на [railway.app](https://railway.app) и подключи этот GitHub-репозиторий.
2. Добавь плагин **PostgreSQL** в тот же проект.
3. В Variables сервиса бота задай:

| Variable | Пример |
|---|---|
| `BOT_TOKEN` | токен от BotFather |
| `BOT_USERNAME` | `tbilisi_badminton_booking_bot` |
| `ADMIN_IDS` | `1082249767` |
| `CHANNEL_ID` | `-1003826191502` |
| `BADMINTON_TIMEZONE` | `Asia/Tbilisi` |

`DATABASE_URL` Railway подставит сам из Postgres. Порт `PORT` — тоже.

4. Deploy. Healthcheck: `GET /health`.
5. В личке боту: `/start` → меню админа → «Опубликовать».

Важно: одновременно должен крутиться только один инстанс бота (long polling). Локальный `docker compose` перед продом останови.

## Локально

```bash
cp .env.example .env
# заполни BOT_TOKEN / ADMIN_IDS / CHANNEL_ID
docker compose --env-file .env up --build
```
