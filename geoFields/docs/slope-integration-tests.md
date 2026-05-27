# Slope Integration Tests

Проверки для связки фронт -> Java -> Python по модулю уклонов.

## 1) Smoke: Java собран и endpoints зарегистрированы

```powershell
cd geoFields
.\gradlew.bat test
```

Ожидание: `BUILD SUCCESSFUL`.

## 2) Python contract (должны существовать endpoints)

```powershell
curl.exe -i -sS "http://localhost:8001/get_slope_tiles_for_field?field_id=1&date=2023-01-01"
curl.exe -i -sS "http://localhost:8001/get_slope_trend?field_id=1&start_date=2023-01-01&end_date=2023-03-01"
```

Ожидание при готовом Python:
- HTTP `200`
- JSON структуры:
  - `{"url":".../slope/tiles/.../{z}/{x}/{y}.png","actual_date":"YYYY-MM-DD"}`
  - `{"labels":[...],"data":[...]}`

Текущее состояние в проекте (на момент написания): Python возвращает `404 Not Found`, потому что slope endpoints пока не реализованы на стороне Python.

## 3) Java proxy error handling (когда Python недоступен)

Предусловие: остановить python-processor.

```powershell
docker compose stop python-processor
```

Далее из браузера (авторизованная сессия) вызвать slope-кнопку в аналитике.

Ожидание:
- Java возвращает `502`
- Текст ошибки: `Python slope service unavailable`
- На фронте в блоке Slope показывается сообщение из ответа.

## 4) Java proxy success (когда Python доступен и endpoints реализованы)

Предусловие: python-processor запущен и реализует slope endpoints.

Проверка через UI:
1. Выбрать поля в NDVI-чеклисте.
2. Задать период.
3. Нажать `Рассчитать уклоны`.

Ожидание:
- Запросы:
  - `GET /get_slope_tiles_for_field`
  - `GET /get_slope_trend`
- Рисуется линия и точки на slope-графике.
- При пустых данных отображается статус `Нет данных за период`.

