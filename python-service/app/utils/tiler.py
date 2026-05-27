from rio_tiler.io import Reader
from rio_tiler.errors import TileOutsideBounds
from rio_tiler.colormap import cmap
from rasterio.features import geometry_mask
from rasterio.warp import transform_geom

import numpy as np
from PIL import Image
import io

NODATA = -9999.0
FIELD_GEOM_CRS = "EPSG:4326"


def _geometry_in_crs(geometry, target_crs):
    """Контур поля (WGS84) → CRS тайла (обычно EPSG:3857 для {z}/{x}/{y})."""
    if not geometry or target_crs is None:
        return None
    try:
        dst = target_crs.to_string() if hasattr(target_crs, "to_string") else str(target_crs)
        if dst.upper() in ("EPSG:4326", "OGC:CRS84"):
            return geometry
        return transform_geom(FIELD_GEOM_CRS, dst, geometry)
    except Exception as e:
        print(f"Geometry CRS transform error: {e}", flush=True)
        return None


def _apply_rdylgn(values_u8, alpha_mask):
    """RGBA PNG: палитра rdylgn + явная прозрачность вне поля."""
    cm = cmap.get("rdylgn")
    lut = np.zeros((256, 4), dtype=np.uint8)
    for idx, color in cm.items():
        lut[int(idx)] = color
    rgba = lut[values_u8]
    rgba[..., 3] = np.where(alpha_mask > 0, rgba[..., 3], 0)
    return rgba


def render_tile(tif_path, z, x, y, field_geometry=None):

    if tif_path is None:
        return get_empty_bytes()

    try:
        with Reader(str(tif_path)) as src:
            tile = src.tile(
                x,
                y,
                z,
                resampling_method="nearest",
            )

            ndvi = tile.data[0].astype(np.float32)

            valid = np.isfinite(ndvi) & (ndvi != NODATA)
            if getattr(tile, "mask", None) is not None:
                valid &= tile.mask.astype(bool)

            # Маска по контуру поля в CRS тайла (Web Mercator), не CRS исходного GeoTIFF
            if field_geometry:
                tile_crs = tile.crs or "EPSG:3857"
                geom = _geometry_in_crs(field_geometry, tile_crs)
                if geom is not None:
                    try:
                        inside = geometry_mask(
                            [geom],
                            out_shape=ndvi.shape,
                            transform=tile.transform,
                            invert=True,
                            all_touched=False,
                        )
                        valid &= inside
                    except Exception as e:
                        print(f"Geometry mask error: {e}", flush=True)

            alpha_mask = np.where(valid, 255, 0).astype(np.uint8)

            ndvi = np.where(valid, ndvi, np.nan)
            vmin, vmax = 0.0, 0.8
            normalized = np.clip((ndvi - vmin) / (vmax - vmin) * 255.0, 0, 255)
            normalized = np.nan_to_num(normalized, nan=0).astype(np.uint8)

            rgba = _apply_rdylgn(normalized, alpha_mask)

            img = Image.fromarray(rgba, mode="RGBA")
            buf = io.BytesIO()
            img.save(buf, format="PNG")
            return buf.getvalue()

    except TileOutsideBounds:
        return get_empty_bytes()

    except Exception as e:
        print(f"🔴 Tile error: {e}", flush=True)
        return get_empty_bytes()


def get_empty_bytes():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()
