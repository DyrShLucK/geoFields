# geoFields: быстрый запуск

## Запуск через Docker

Из корня проекта:

```bash
cd "C:/Users/adm/Desktop/diplom Main/geoFields"
./gradlew clean bootJar
cd "C:/Users/adm/Desktop/diplom Main"
docker compose down -v
docker compose up -d
```

Примечания:
- `docker compose down -v` удаляет volume БД и при следующем старте заново импортирует бэкап.
- Бэкап поднимается из `LAST_BCUP_BD.sql` во время инициализации Postgres.
- Приложение доступно по адресу `http://localhost:8080` (или другой порт из `docker-compose.yml`).

Полезные команды:

```bash
docker compose ps
docker compose logs db --tail=200
docker compose logs app --tail=200
```

## Локальный запуск через Gradle

Требования:
- Java 21
- Локально запущенные PostgreSQL и Redis

Настройки по умолчанию (`geoFields/src/main/resources/application.properties`):
- БД: `jdbc:postgresql://localhost:5432/geofields`
- Пользователь/пароль БД: `postgres` / `12345`
- Redis: `localhost:6379`

Запуск:

```bash
cd "C:/Users/adm/Desktop/diplom Main/geoFields"
./gradlew bootRun
```

## Тестовые пользователи

Пароль для всех пользователей: `user1234`

- `user123` — роль `ORG_MANAGER`
- `user12345` — роль `AGRONOMIST`
- `user1234` — роль `ORG_ADMIN`
- `2user1234` — роль `ORG_ADMIN`
- `2user12345` — роль `USER`

## Где находятся спецификации

- Основная OpenAPI спецификация бэкенда: `geoFields/src/main/resources/static/OpenAPIspec.yaml`
- Спецификация внешнего NDVI API: `geoFields/src/main/resources/static/ndvi-outbound-api.yaml`

## Где находятся HTML/CSS/JS

- HTML-шаблоны: `geoFields/src/main/resources/templates/`
- CSS: `geoFields/src/main/resources/static/css/`
- JavaScript: `geoFields/src/main/resources/static/js/`

## Для запросов через postmman или api

- В cookie указать JSESSIONID и XSRF-TOKEN
- Для post запросов получить csrf токен по http://localhost:8080/api/session/context и указать его в загаловках как X-XSRF-TOKEN
- JSESSIONID и XSRF-TOKEN можно получить при логине, или одноразово скопировать из браузера, если там авторизироваться В F12 APPLICATION (Cookie)
