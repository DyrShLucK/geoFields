# Python NDVI Service

FastAPI-сервис расчёта NDVI и выдачи PNG-тайлов для GeoFields.

## Запуск

```bash
docker compose up -d python-processor
# локально: uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Порт в Docker: `8001` (host) → `8000` (контейнер).

## Что изменилось (кратко)

### S3 / MinIO (GeoTIFF)

- После расчёта NDVI по полю GeoTIFF загружается в бакет **`geofields-ndvi`**:
  - ключ: `fields/{field_id}/{date}_{scene_id}_ndvi.tif`
- При **cache HIT** (файл уже на диске / в БД) — **догрузка в S3**, если объекта ещё нет  
  (раньше при HIT upload не вызывался — поэтому `.tif` мог не появляться в MinIO).
- Эндпоинт `/tiles/...` при отсутствии локального `.tif` **скачивает его из S3**.

Переменные окружения (`docker-compose.yml`):

| Переменная | Пример |
|------------|--------|
| `S3_ENABLED` | `true` |
| `S3_ENDPOINT` | `http://minio:9000` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `minioadmin` |
| `S3_BUCKET_NDVI` | `geofields-ndvi` |

Проверка в MinIO Console: `http://localhost:9001` → бакет `geofields-ndvi` → `fields/`.

### Кэш расчёта NDVI (`ndvi.py`)

- Запрос с датой `2026-01-01`, фактическая дата снимка `2023-05-14` — кэш ищется по **полю + сцене** и по **файлу на диске**, а не только по точной дате запроса.
- Повторный запрос не пересчитывает GeoTIFF, если файл уже есть.

### PNG-тайлы (`main.py`)

- Пустые тайлы (нет данных / вне поля) → **`204 No Content`**, без PNG 334 bytes.
- Кэш PNG — на стороне **Java** (MinIO `geofields-tiles`), не в Python.

## Основные эндпоинты

| Метод | Путь | Назначение |
|-------|------|------------|
| GET | `/get_ndvi_tiles_for_field` | URL шаблона тайлов + расчёт GeoTIFF при необходимости |
| GET | `/get_ndvi_trend` | Тренд NDVI для графика |
| GET | `/tiles/{field_id}/{date}/{scene_id}/{z}/{x}/{y}.png` | Один PNG-тайл |

## Хранилище на диске

```
storage/
  fields/{field_id}/{date}_{scene_id}_ndvi.tif   # NDVI по полю
  indices/NDVI/{date}/{scene_id}.tif               # региональный индекс
```

В Docker том: `./shared-data` → `/app/storage`.

## S3/MinIO: как работает сейчас

### Что хранится

- **GeoTIFF NDVI по полям** в бакете `geofields-ndvi`
  - ключ: `fields/{field_id}/{actual_date}_{scene_id}_ndvi.tif`
  - Когда просят PNG-тайл:
  Python сначала ищет field TIFF локально,
  если нет — скачивает из S3,
рендерит PNG-тайл.
- **PNG тайлы** хранятся и кэшируются на стороне Java (бакет `geofields-tiles`), Python только рендерит тайл по запросу.

### Поток записи/чтения

1. `GET /get_ndvi_tiles_for_field` находит сцену и рассчитывает/берёт готовый TIFF.
2. После расчёта (и при cache-hit) Python синхронизирует TIFF в S3, если объекта ещё нет.
3. `GET /tiles/...png` при отсутствии локального TIFF пробует скачать его из `geofields-ndvi`.
4. Если данных для тайла нет — возвращает `204 No Content`.

### Функции в `app/utils/s3_storage.py`

- `s3_enabled()` — включен ли S3-режим (`S3_ENABLED`)
- `s3_endpoint()` — endpoint MinIO/S3
- `s3_bucket_ndvi()` / `s3_bucket_tiles()` — имена бакетов
- `ensure_bucket(bucket)` — best-effort создание/проверка бакета
- `exists(bucket, key)` — проверка, существует ли объект
- `ndvi_tif_key(field_id, actual_date_str, scene_name)` — стабильный ключ TIFF
- `put_file(bucket, key, local_path, content_type)` — upload локального файла
- `get_file(bucket, key, local_path)` — download объекта на диск
- `ensure_ndvi_tif_uploaded(...)` — idempotent upload TIFF (если ещё нет в бакете)

## Как всё идёт через сцены (scene workflow)

Python работает не “по произвольной дате”, а по **ближайшей доступной сцене**:

1. Для `field_id` и запрошенной даты ищется ближайшая сцена (`scenes`) с пересечением контура поля.
2. Для этой сцены берётся/создаётся региональный индекс (`scene_indices`, файл `indices/NDVI/{date}/{scene}.tif`).
3. Из регионального индекса вырезается поле (`clip_index`) -> `fields/{field_id}/{actual_date}_{scene}_ndvi.tif`.
4. В `field_analytic` пишется `mean_value` (для тренда), привязанный к сцене.
5. Во внешнем ответе возвращается `actual_date` и URL тайлов с `scene_id`.

Из-за этого `actual_date` может отличаться от даты запроса — это нормально и ожидаемо.
