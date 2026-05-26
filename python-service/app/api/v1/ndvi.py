import json
import traceback
from datetime import datetime, date, timedelta
from typing import List, Optional
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

router = APIRouter()


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
            return {
                "url": f"http://localhost:8001/tiles/{field_id}/{str(ready_analytic.date)}/{ready_analytic.scene_index.scene.scene_id}/{{z}}/{{x}}/{{y}}.png",
                "actual_date": str(ready_analytic.date)}

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