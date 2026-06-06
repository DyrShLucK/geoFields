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


def _apply_colormap(values_u8, alpha_mask, colormap_name="rdylgn"):
    """RGBA PNG: выбранная палитра + явная прозрачность вне поля."""
    cm = cmap.get(colormap_name)
    lut = np.zeros((256, 4), dtype=np.uint8)
    for idx, color in cm.items():
        lut[int(idx)] = color
    rgba = lut[values_u8]
    rgba[..., 3] = np.where(alpha_mask > 0, rgba[..., 3], 0)
    return rgba


# Агрономическая классификация уклонов (4 группы, градусы → RGB).
SLOPE_CLASS_COLORS = (
    (26, 150, 65),    # 0–3°  Неэродированные / слабоэродированные
    (255, 255, 191),  # 3–5°  Средне- и слабоэродированные
    (253, 174, 97),   # 5–8°  Средне- и сильносмытые почвы
    (215, 25, 28),    # >8°   Сильноэродированные
)


def _apply_slope_classes(slope_deg, alpha_mask):
    """Дискретная раскраска уклона по агрономическим классам."""
    rgba = np.zeros((*slope_deg.shape, 4), dtype=np.uint8)
    visible = alpha_mask > 0

    class_masks = [
        visible & (slope_deg >= 0.0) & (slope_deg < 3.0),
        visible & (slope_deg >= 3.0) & (slope_deg < 5.0),
        visible & (slope_deg >= 5.0) & (slope_deg < 8.0),
        visible & (slope_deg >= 8.0),
    ]
    for mask, rgb in zip(class_masks, SLOPE_CLASS_COLORS):
        rgba[mask, 0] = rgb[0]
        rgba[mask, 1] = rgb[1]
        rgba[mask, 2] = rgb[2]
        rgba[mask, 3] = 255

    return rgba


_elevation_range_cache = {}


def get_elevation_range(tif_path):
    """Мин/макс высоты по полю (без NoData -9999). Кэш по mtime файла."""
    import rasterio
    from pathlib import Path

    path = Path(tif_path)
    mtime = path.stat().st_mtime
    cache_key = str(path.resolve())
    cached = _elevation_range_cache.get(cache_key)
    if cached and cached[0] == mtime:
        return cached[1], cached[2]

    with rasterio.open(str(tif_path)) as src:
        data = src.read(1).astype(np.float32)
        nodata = src.nodata if src.nodata is not None else NODATA
        valid = data[np.isfinite(data) & (data != nodata)]
        if valid.size == 0:
            return 0.0, 1.0
        vmin = float(valid.min())
        vmax = float(valid.max())
        if vmax <= vmin:
            vmax = vmin + 1.0
        _elevation_range_cache[cache_key] = (mtime, vmin, vmax)
        return vmin, vmax


def render_tile(
    tif_path,
    z,
    x,
    y,
    field_geometry=None,
    layer_type="ndvi",
    vmin=None,
    vmax=None,
):

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

            values = tile.data[0].astype(np.float32)

            valid = np.isfinite(values) & (values != NODATA)
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
                            out_shape=values.shape,
                            transform=tile.transform,
                            invert=True,
                            all_touched=False,
                        )
                        valid &= inside
                    except Exception as e:
                        print(f"Geometry mask error: {e}", flush=True)

            alpha_mask = np.where(valid, 255, 0).astype(np.uint8)

            if layer_type == "slope":
                slope_arr = np.where(valid, values, 0.0)
                rgba = _apply_slope_classes(slope_arr, alpha_mask)
            else:
                values = np.where(valid, values, np.nan)
                if layer_type == "elevation":
                    v_min = float(vmin) if vmin is not None else 0.0
                    v_max = float(vmax) if vmax is not None else 1.0
                    if v_max <= v_min:
                        v_max = v_min + 1.0
                    colormap_name = "terrain"
                else:
                    v_min, v_max = 0.0, 0.8
                    colormap_name = "rdylgn"

                normalized = np.clip((values - v_min) / (v_max - v_min) * 255.0, 0, 255)
                normalized = np.nan_to_num(normalized, nan=0).astype(np.uint8)
                rgba = _apply_colormap(normalized, alpha_mask, colormap_name)

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
