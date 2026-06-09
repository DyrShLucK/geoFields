from fastapi import FastAPI, Response, status
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from sqlalchemy.orm import Session
from fastapi import Depends

from .api.v1.ndvi import router as ndvi_router
from .api.v1.elevation import router as elevation_router
from .utils import tiler
from .utils import s3_storage
from .core.config import FIELDS_DIR
from .db import get_db, get_field_geometry


app = FastAPI(
    title="Agro Service Pro"
)

app.include_router(ndvi_router)
app.include_router(elevation_router)

EMPTY_TILE_BYTES = tiler.get_empty_bytes()

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:8080",
        "http://127.0.0.1:8080"
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/tiles/{field_id}/{date}/{scene_id}/{z}/{x}/{y}.png")
async def get_tile(
    field_id: str,
    date: str,
    scene_id: str,
    z: int,
    x: int,
    y: int,
    db: Session = Depends(get_db)
):
    path = (
        FIELDS_DIR /
        field_id /
        f"{date}_{scene_id}_ndvi.tif"
    )

    try:
        geom = get_field_geometry(int(field_id))
    except Exception:
        geom = None

    if not path.exists() and s3_storage.s3_enabled():
        s3_key = s3_storage.ndvi_tif_key(field_id, date, scene_id)
        s3_storage.get_file(
            s3_storage.s3_bucket_ndvi(),
            s3_key,
            str(path),
        )

    if not path.exists():
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    tile_bytes = tiler.render_tile(
        path,
        z,
        x,
        y,
        field_geometry=geom
    )

    if tile_bytes == EMPTY_TILE_BYTES:
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    return Response(content=tile_bytes, media_type="image/png")


@app.get("/tiles/elevation/{field_id}/{z}/{x}/{y}.png")
async def get_elevation_tile(
    field_id: str,
    z: int,
    x: int,
    y: int,
):

    path = FIELDS_DIR / field_id / "elevation.tif"

    if not path.exists():
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    try:
        geom = get_field_geometry(int(field_id))
    except Exception:
        geom = None

    vmin, vmax = tiler.get_elevation_range(path)

    tile_bytes = tiler.render_tile(
        path,
        z,
        x,
        y,
        field_geometry=geom,
        layer_type="elevation",
        vmin=vmin,
        vmax=vmax,
    )

    if tile_bytes == EMPTY_TILE_BYTES:
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    return Response(content=tile_bytes, media_type="image/png")


@app.get("/tiles/slope/{field_id}/{z}/{x}/{y}.png")
async def get_slope_tile(
    field_id: str,
    z: int,
    x: int,
    y: int,
):

    path = FIELDS_DIR / field_id / "slope.tif"

    if not path.exists():
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    try:
        geom = get_field_geometry(int(field_id))
    except Exception:
        geom = None

    tile_bytes = tiler.render_tile(
        path,
        z,
        x,
        y,
        field_geometry=geom,
        layer_type="slope",
        vmin=0.0,
        vmax=15.0,
    )

    if tile_bytes == EMPTY_TILE_BYTES:
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    return Response(content=tile_bytes, media_type="image/png")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8001
    )