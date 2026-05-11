# geoFields: быстрый запуск

## Запуск через Docker
Если есть изменения в коде

Из корня проекта:

```bash
cd "/geoFields"
./gradlew clean bootJar
cd "../"
```
Если нет изменений

```
docker compose down -v
docker compose up -d
```

Примечания:
- `docker compose down -v` удаляет volume БД и при следующем старте заново импортирует бэкап.
- Бэкап поднимается из `LAST_BCUP_BD.sql` во время инициализации Postgres.
- Приложение доступно по адресу `http://localhost:8080` (или другой порт из `docker-compose.yml`).
- Режим `html/css/js` выбирается сверху в `docker-compose.yml` через `SPRING_PROFILES_ACTIVE`.
- `SPRING_PROFILES_ACTIVE: live-ui` - `html`, `css` и `js` берутся из `geoFields/src/main/resources/templates` и `geoFields/src/main/resources/static`, изменения применяются после `docker compose restart app`.
- `SPRING_PROFILES_ACTIVE: ""` - `html`, `css` и `js` берутся только из `app.jar`.
- Если менялся Java-код, как и раньше нужно сначала выполнить `./gradlew clean bootJar`, потому что приложение запускается из `jar`.

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
cd "./geoFields"
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

## Кратко по эндпоинтам

- `GET /api/session/context` - возвращает контекст текущей сессии, роль пользователя и CSRF-данные для UI.
- `GET /get_fields` и `GET /api/fields/{fieldId}/intersections` - отдают поля организации в формате GeoJSON и позволяют получить пересечения по выбранному полю.
- `GET /get_ndvi_value`, `GET /get_ndvi_by_id`, `GET /get_all_ndvi_tile` - NDVI-запросы: значение в точке, слой по одному полю и слой по набору полей/по всей организации.
- `/api/org/manager/*` - сводка менеджера организации, обработка заявок на регистрацию и управление инвайт-ссылками.
- `/api/org/admin/*` - просмотр участников организации, смена ролей, удаление пользователей и удаление полей.
- `/api/org/agronomist/*` - сводка агронома, CRUD по истории культур, создание нового поля и смена статуса поля (`ACTIVE` / `OBSOLETE`).

Для большинства JSON-эндпоинтов нужна авторизованная сессия (`JSESSIONID`), а для изменяющих запросов дополнительно нужен CSRF-токен (`X-XSRF-TOKEN`). Полные схемы запросов и ответов описаны в `OpenAPIspec.yaml`.

## Где находятся HTML/CSS/JS

- HTML-шаблоны: `geoFields/src/main/resources/templates/`
- CSS: `geoFields/src/main/resources/static/css/`
- JavaScript: `geoFields/src/main/resources/static/js/`

## Для запросов через postman или api

- В cookie указать JSESSIONID и XSRF-TOKEN
- Для post запросов получить csrf токен по http://localhost:8080/api/session/context и указать его в загаловках как X-XSRF-TOKEN
- JSESSIONID и XSRF-TOKEN можно получить при логине, или одноразово скопировать из браузера, если там авторизироваться В F12 APPLICATION (Cookie)
