from fastapi import FastAPI, Response
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from sqlalchemy.orm import Session
from fastapi import Depends

from .api.v1.ndvi import router as ndvi_router
from .utils import tiler
from .core.config import FIELDS_DIR
from .db import get_db, get_field_geometry


app = FastAPI(
    title="Agro Service Pro"
)

app.include_router(ndvi_router)

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

    if not path.exists():
        return Response(
            content=tiler.render_tile(None, z, x, y, field_geometry=geom),
            media_type="image/png"
        )

    tile_bytes = tiler.render_tile(
        path,
        z,
        x,
        y,
        field_geometry=geom
    )

    return Response(
        content=tile_bytes,
        media_type="image/png"
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8001
    )