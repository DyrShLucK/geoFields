import os
import threading
import traceback
from pathlib import Path

from fastapi import APIRouter, HTTPException

from ...core.config import ELEVATION_RAW_PATH, FIELDS_DIR
from ...db import get_field_geometry
from ...services import processor
from ...utils import tiler

router = APIRouter()

_prepare_locks: dict[int, threading.Lock] = {}
_prepare_locks_guard = threading.Lock()


def _get_prepare_lock(field_id: int) -> threading.Lock:
    with _prepare_locks_guard:
        if field_id not in _prepare_locks:
            _prepare_locks[field_id] = threading.Lock()
        return _prepare_locks[field_id]


def _elevation_tif_path(field_id: int) -> Path:
    return FIELDS_DIR / str(field_id) / "elevation.tif"


def _slope_tif_path(field_id: int) -> Path:
    return FIELDS_DIR / str(field_id) / "slope.tif"


def _public_base_url() -> str:
    return os.getenv("PYTHON_PUBLIC_URL", "http://localhost:8001").rstrip("/")


def _elevation_tile_url_template(field_id: int) -> str:
    return f"{_public_base_url()}/tiles/elevation/{field_id}/{{z}}/{{x}}/{{y}}.png"


def _slope_tile_url_template(field_id: int) -> str:
    return f"{_public_base_url()}/tiles/slope/{field_id}/{{z}}/{{x}}/{{y}}.png"


def _build_prepare_response(field_id: int) -> dict:
    elevation_path = _elevation_tif_path(field_id)
    vmin, vmax = tiler.get_elevation_range(elevation_path)
    return {
        "status": "ready",
        "tile_url": _elevation_tile_url_template(field_id),
        "slope_tile_url": _slope_tile_url_template(field_id),
        "elevation_min": vmin,
        "elevation_max": vmax,
    }


def _ensure_slope_raster(elevation_path: Path, slope_path: Path) -> None:
    if slope_path.exists() and slope_path.stat().st_size > 0:
        return
    processor.generate_slope_raster(str(elevation_path), str(slope_path))


@router.get("/prepare_elevation/{field_id}")
async def prepare_elevation(field_id: int):
    """
    Подготовка рельефа и уклона для поля: elevation.tif + slope.tif (кэш на диске).
    """
    output_path = _elevation_tif_path(field_id)
    slope_path = _slope_tif_path(field_id)

    if output_path.exists() and output_path.stat().st_size > 0:
        try:
            _ensure_slope_raster(output_path, slope_path)
        except Exception as e:
            print(f"🚨 generate_slope_raster error: {e}", flush=True)
            traceback.print_exc()
            raise HTTPException(status_code=500, detail="Не удалось подготовить карту уклонов") from e
        return _build_prepare_response(field_id)

    if not ELEVATION_RAW_PATH.exists():
        raise HTTPException(
            status_code=503,
            detail=f"Исходный SRTM не найден: {ELEVATION_RAW_PATH}",
        )

    geometry = get_field_geometry(field_id)
    if geometry is None:
        raise HTTPException(status_code=404, detail=f"Поле {field_id} не найдено")

    lock = _get_prepare_lock(field_id)
    with lock:
        if output_path.exists() and output_path.stat().st_size > 0:
            _ensure_slope_raster(output_path, slope_path)
            return _build_prepare_response(field_id)

        field_dir = FIELDS_DIR / str(field_id)
        field_dir.mkdir(parents=True, exist_ok=True)
        try:
            processor.clip_index(
                source_index_path=str(ELEVATION_RAW_PATH),
                geometry_dict=geometry,
                output_path=str(output_path),
            )
            _ensure_slope_raster(output_path, slope_path)
        except Exception as e:
            print(f"🚨 prepare_elevation error: {e}", flush=True)
            traceback.print_exc()
            raise HTTPException(status_code=500, detail="Не удалось подготовить рельеф для поля") from e

        if not output_path.exists() or output_path.stat().st_size == 0:
            raise HTTPException(status_code=500, detail="Файл рельефа не был создан")
        if not slope_path.exists() or slope_path.stat().st_size == 0:
            raise HTTPException(status_code=500, detail="Файл уклона не был создан")

    return _build_prepare_response(field_id)
