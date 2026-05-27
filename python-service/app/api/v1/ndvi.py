import json
import traceback
from datetime import datetime, date, timedelta
from pathlib import Path
from typing import List, Optional
import rasterio
from fastapi import APIRouter, HTTPException, Depends, Query
from sqlalchemy.orm import Session
from sqlalchemy import text, func, and_
import numpy as np

from ...db import get_db, get_field_geometry
from ...models.scene import Scene
from ...models.scene_index import SceneIndex
from ...models.field_analytic import FieldAnalytic
from ...services import processor
from ...core.config import FIELDS_DIR, INDICES_DIR
from ...utils import s3_storage

router = APIRouter()


def _tiles_url(field_id: str, actual_date_str: str, scene_name: str) -> dict:
    return {
        "url": f"http://localhost:8001/tiles/{field_id}/{actual_date_str}/{scene_name}/{{z}}/{{x}}/{{y}}.png",
        "actual_date": actual_date_str,
    }


def _sync_ndvi_tif_to_s3(field_id: str, actual_date_str: str, scene_name: str, local_path: str) -> None:
    """Синхронизация GeoTIFF в MinIO: upload только если объекта ещё нет в бакете."""
    if not s3_storage.s3_enabled():
        return
    bucket = s3_storage.s3_bucket_ndvi()
    key = s3_storage.ndvi_tif_key(field_id, actual_date_str, scene_name)
    if s3_storage.exists(bucket, key):
        return
    if s3_storage.put_file(bucket, key, local_path, content_type="image/tiff"):
        print(f"☁️ S3 uploaded: {key}", flush=True)
    else:
        print(f"⚠️ S3 upload failed: {key}", flush=True)


def _mean_ndvi_from_tif(local_path: str) -> float:
    # Для cache HIT считаем среднее прямо из существующего TIFF, чтобы не делать clip_index повторно.
    with rasterio.open(local_path) as src:
        data = src.read(1).astype(np.float32)
        nodata = src.nodata if src.nodata is not None else -9999.0
        valid = data[data != nodata]
        if valid.size == 0:
            return 0.0
        return float(np.mean(valid))


def _ensure_field_analytic(
        db: Session,
        field_id_int: int,
        scene_index_id: int,
        analytics_date: date,
        local_path: str,
        mean_value: Optional[float] = None) -> None:
    existing = db.query(FieldAnalytic).filter(
        FieldAnalytic.field_id == field_id_int,
        FieldAnalytic.scene_index_id == scene_index_id
    ).first()
    if existing:
        return
    if mean_value is None:
        mean_value = _mean_ndvi_from_tif(local_path)
    db.add(FieldAnalytic(
        field_id=field_id_int,
        scene_index_id=scene_index_id,
        date=analytics_date,
        local_path=local_path,
        mean_value=mean_value
    ))
    db.commit()


@router.get("/get_ndvi_by_id")
async def get_ndvi_by_id(field_id: str, date: str, db: Session = Depends(get_db)):
    print(f"\n--- [START] Запрос NDVI: Поле {field_id}, Дата {date} ---", flush=True)

    try:
        field_id_int = int(field_id)
        target_dt = datetime.strptime(date, "%Y-%m-%d").date()
        index_type = "NDVI"

        ready_analytic = db.query(FieldAnalytic).filter(
            FieldAnalytic.field_id == field_id_int,
            FieldAnalytic.date == target_dt
        ).first()

        if ready_analytic:
            print(f"📦 Результат найден в базе (field_analytics). Отдаю URL.", flush=True)
            return {"url": f"http://localhost:8001/tiles/{field_id}/{date}/{{z}}/{{x}}/{{y}}.png"}

        print(f"🔍 В кэше нет. Ищу ближайший снимок в таблице scenes...", flush=True)

        scene_query = text("""
            SELECT s.id, s.scene_id, s.acquisition_date, s.local_path, ST_AsGeoJSON(f.geom) as field_geom
            FROM scenes s, fields f
            WHERE f.id = :f_id
              AND ST_Intersects(f.geom, s.footprint)
            ORDER BY ABS(s.acquisition_date - CAST(:t_date AS DATE)) ASC
            LIMIT 1
        """)

        res = db.execute(scene_query, {"f_id": field_id_int, "t_date": date}).fetchone()

        if not res:
            print(f"❌ Ошибка: Для поля {field_id} не найдено подходящих снимков в базе.", flush=True)
            raise HTTPException(status_code=404, detail="Нет доступных спутниковых данных")

        db_scene_id, scene_name, actual_date, raw_path, field_geom_str = res
        actual_date_str = str(actual_date)
        geometry_dict = json.loads(field_geom_str)

        scene_idx = db.query(SceneIndex).filter(
            SceneIndex.scene_id == db_scene_id,
            SceneIndex.index_type == index_type
        ).first()

        if not scene_idx:
            print(f"⚙️ Рассчитываю полный {index_type} для всего региона (сцена {scene_name})...", flush=True)
            full_idx_dir = INDICES_DIR / index_type / actual_date_str
            full_idx_dir.mkdir(parents=True, exist_ok=True)
            full_idx_path = full_idx_dir / f"{scene_name}.tif"

            processor.calculate_full_index(str(raw_path), str(full_idx_path))

            scene_idx = SceneIndex(
                scene_id=db_scene_id,
                index_type=index_type,
                local_path=str(full_idx_path)
            )
            db.add(scene_idx)
            db.commit()
            db.refresh(scene_idx)
        else:
            print(f"✅ Региональный индекс уже готов: {scene_idx.local_path}", flush=True)

        print(f"✂️ Вырезаю поле {field_id} из регионального индекса...", flush=True)
        field_res_dir = FIELDS_DIR / field_id
        field_res_dir.mkdir(parents=True, exist_ok=True)
        field_final_path = field_res_dir / f"{actual_date_str}_{scene_name}_ndvi.tif"

        mean_val = processor.clip_index(
            source_index_path=scene_idx.local_path,
            geometry_dict=geometry_dict,
            output_path=str(field_final_path)
        )

        # Upload NDVI GeoTIFF to object storage (optional)
        _sync_ndvi_tif_to_s3(field_id, actual_date_str, scene_name, str(field_final_path))

        new_analytic = FieldAnalytic(
            field_id=field_id_int,
            scene_index_id=scene_idx.id,
            date=actual_date,
            local_path=str(field_final_path),
            mean_value=mean_val
        )
        db.add(new_analytic)
        db.commit()

        print(f"✨ ВСЁ ГОТОВО! NDVI за {actual_date_str} сохранен и отправлен.", flush=True)

        return {
            "url": f"http://localhost:8001/tiles/{field_id}/{actual_date_str}/{scene_name}/{{z}}/{{x}}/{{y}}.png",
            "actual_date": actual_date_str
        }

    except Exception as e:
        print(f"🚨 КРИТИЧЕСКАЯ ОШИБКА В NDVI.PY: {str(e)}", flush=True)
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/get_ndvi_tiles_for_field")
async def get_ndvi_tiles_for_field(field_id: str, date: str, db: Session = Depends(get_db)):
    """
    Эндпоинт для получения URL тайлов NDVI для одного поля на конкретную дату.
    Если данных нет, инициирует расчет.
    """
    print(f"\n--- [START] Запрос NDVI-тайлов: Поле {field_id}, Дата {date} ---", flush=True)

    try:
        field_id_int = int(field_id)
        target_dt = datetime.strptime(date, "%Y-%m-%d").date()
        index_type = "NDVI"

        ready_analytic = db.query(FieldAnalytic).filter(
            FieldAnalytic.field_id == field_id_int,
            FieldAnalytic.date == target_dt
        ).first()

        if ready_analytic:
            print(f"📦 Результат найден в базе (field_analytics). Отдаю URL.", flush=True)
            ad = str(ready_analytic.date)
            sid = ready_analytic.scene_index.scene.scene_id
            if Path(ready_analytic.local_path).exists():
                _sync_ndvi_tif_to_s3(field_id, ad, sid, ready_analytic.local_path)
            return _tiles_url(field_id, ad, sid)

        print(f"🔍 В кэше нет. Ищу ближайший снимок в таблице scenes...", flush=True)

        scene_query = text("""
            SELECT s.id, s.scene_id, s.acquisition_date, s.local_path, ST_AsGeoJSON(f.geom) as field_geom
            FROM scenes s, fields f
            WHERE f.id = :f_id
              AND ST_Intersects(f.geom, s.footprint)
            ORDER BY ABS(s.acquisition_date - CAST(:t_date AS DATE)) ASC
            LIMIT 1
        """)

        res = db.execute(scene_query, {"f_id": field_id_int, "t_date": date}).fetchone()

        if not res:
            print(f"❌ Ошибка: Для поля {field_id} не найдено подходящих снимков в базе.", flush=True)
            raise HTTPException(status_code=404, detail="Нет доступных спутниковых данных")

        db_scene_id, scene_name, actual_date, raw_path, field_geom_str = res
        actual_date_str = str(actual_date)
        geometry_dict = json.loads(field_geom_str)

        scene_idx = db.query(SceneIndex).filter(
            SceneIndex.scene_id == db_scene_id,
            SceneIndex.index_type == index_type
        ).first()

        if not scene_idx:
            print(f"⚙️ Рассчитываю полный {index_type} для всего региона (сцена {scene_name})...", flush=True)
            full_idx_dir = INDICES_DIR / index_type / actual_date_str
            full_idx_dir.mkdir(parents=True, exist_ok=True)
            full_idx_path = full_idx_dir / f"{scene_name}.tif"

            processor.calculate_full_index(str(raw_path), str(full_idx_path))

            scene_idx = SceneIndex(
                scene_id=db_scene_id,
                index_type=index_type,
                local_path=str(full_idx_path)
            )
            db.add(scene_idx)
            db.commit()
            db.refresh(scene_idx)
        else:
            print(f"✅ Региональный индекс уже готов: {scene_idx.local_path}", flush=True)

        field_res_dir = FIELDS_DIR / field_id
        field_res_dir.mkdir(parents=True, exist_ok=True)
        field_final_path = field_res_dir / f"{actual_date_str}_{scene_name}_ndvi.tif"

        ready_for_scene = db.query(FieldAnalytic).filter(
            FieldAnalytic.field_id == field_id_int,
            FieldAnalytic.scene_index_id == scene_idx.id,
        ).first()

        if ready_for_scene and Path(ready_for_scene.local_path).exists():
            ad = str(ready_for_scene.date)
            scene_id = ready_for_scene.scene_index.scene.scene_id
            print(f"📦 Кэш HIT (field_analytics + файл): поле {field_id}, сцена {scene_name}, дата {ad}", flush=True)
            # Файл уже есть локально — догружаем в S3, если раньше не попал (cache HIT без clip_index)
            _sync_ndvi_tif_to_s3(field_id, ad, scene_id, ready_for_scene.local_path)
            return _tiles_url(field_id, ad, scene_id)

        if field_final_path.exists():
            print(f"📦 Кэш HIT (файл на диске): {field_final_path}", flush=True)
            # Если строка аналитики отсутствует, создаём её из готового TIFF (идемпотентно).
            _ensure_field_analytic(
                db=db,
                field_id_int=field_id_int,
                scene_index_id=scene_idx.id,
                analytics_date=actual_date,
                local_path=str(field_final_path),
                mean_value=None
            )
            _sync_ndvi_tif_to_s3(field_id, actual_date_str, scene_name, str(field_final_path))
            return _tiles_url(field_id, actual_date_str, scene_name)

        print(f"✂️ Вырезаю поле {field_id} из регионального индекса...", flush=True)

        mean_val = processor.clip_index(
            source_index_path=scene_idx.local_path,
            geometry_dict=geometry_dict,
            output_path=str(field_final_path)
        )

        _sync_ndvi_tif_to_s3(field_id, actual_date_str, scene_name, str(field_final_path))

        _ensure_field_analytic(
            db=db,
            field_id_int=field_id_int,
            scene_index_id=scene_idx.id,
            analytics_date=actual_date,
            local_path=str(field_final_path),
            mean_value=mean_val
        )

        print(f"✨ ВСЁ ГОТОВО! NDVI за {actual_date_str} сохранен и отправлен.", flush=True)

        return _tiles_url(field_id, actual_date_str, scene_name)

    except Exception as e:
        print(f"🚨 КРИТИЧЕСКАЯ ОШИБКА В NDVI.PY: {str(e)}", flush=True)
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/get_ndvi_trend")
async def get_ndvi_trend(
        field_ids: List[int] = Query(..., alias="field_id"),
        start_date: date = Query(...),
        end_date: date = Query(...),
        db: Session = Depends(get_db)
):
    """
    Эндпоинт для получения средних значений NDVI для группы полей за выбранный период
    для построения графика тренда.
    """
    print(f"\n--- [START] Запрос тренда NDVI: Поля {field_ids}, с {start_date} по {end_date} ---", flush=True)

    try:
        results = {}
        current_date = start_date

        while current_date <= end_date:

            month_start = current_date.replace(day=1)
            next_month_start = (month_start + timedelta(days=32)).replace(day=1)

            analytics = db.query(FieldAnalytic).filter(
                FieldAnalytic.field_id.in_(field_ids),
                and_(
                    FieldAnalytic.date >= month_start,
                    FieldAnalytic.date < next_month_start
                )
            ).all()

            if analytics:

                daily_max_ndvi = {}
                for analytic in analytics:
                    analytic_date_str = str(analytic.date)
                    if analytic_date_str not in daily_max_ndvi:
                        daily_max_ndvi[analytic_date_str] = []
                    daily_max_ndvi[analytic_date_str].append(analytic.mean_value)

                mean_for_month = np.mean([a.mean_value for a in analytics]) if analytics else None

                results[month_start.strftime("%Y-%m-%d")] = float(
                    mean_for_month) if mean_for_month is not None else None
            else:
                results[month_start.strftime("%Y-%m-%d")] = None  # Нет данных за этот месяц

            current_date = next_month_start

        filtered_results = {k: v for k, v in results.items() if v is not None}

        sorted_results = sorted(filtered_results.items())

        chart_data = []
        chart_labels = []
        for dt_str, value in sorted_results:
            dt_obj = datetime.strptime(dt_str, "%Y-%m-%d")
            chart_labels.append(dt_obj.strftime("%b"))  # e.g., "Jan", "Feb"
            chart_data.append(value)

        print(f"✅ Тренд NDVI готов. Точек: {len(chart_data)}", flush=True)
        return {"labels": chart_labels, "data": chart_data}

    except Exception as e:
        print(f"🚨 КРИТИЧЕСКАЯ ОШИБКА В GET_NDVI_TREND: {str(e)}", flush=True)
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))