from rio_tiler.io import Reader
from rio_tiler.errors import TileOutsideBounds
from rio_tiler.models import ImageData
from rio_tiler.colormap import cmap
from rasterio.features import geometry_mask

import numpy as np
from PIL import Image
import io

NODATA = -9999.0


def render_tile(tif_path, z, x, y, field_geometry=None):

    if tif_path is None:
        return get_empty_bytes()

    try:
        with Reader(str(tif_path)) as src:

            tile = src.tile(
                x,
                y,
                z,
                resampling_method="nearest"
            )

            ndvi = tile.data[0].astype(np.float32)

            data_mask = np.where(
                ndvi == NODATA,
                0,
                255
            ).astype(np.uint8)

            if field_geometry:
                try:
                    geo_mask = geometry_mask(
                        [field_geometry],
                        out_shape=ndvi.shape,
                        transform=tile.transform,
                        invert=True
                    )

                    final_mask = np.where(geo_mask & (data_mask == 255), 255, 0).astype(np.uint8)
                except Exception as e:
                    print(f"Geometry mask error: {e}")
                    final_mask = data_mask
            else:
                final_mask = data_mask


            ndvi = np.where(
                ndvi == NODATA,
                np.nan,
                ndvi
            )

            vmin = 0.0
            vmax = 0.8

            normalized = (
                (ndvi - vmin) /
                (vmax - vmin)
            ) * 255.0

            normalized = np.clip(
                normalized,
                0,
                255
            )

            normalized = np.nan_to_num(
                normalized,
                nan=0
            ).astype(np.uint8)

            img = ImageData(
                normalized[np.newaxis, :, :],
                final_mask
            )

            return img.render(
                colormap=cmap.get("rdylgn"),
                img_format="PNG"
            )

    except TileOutsideBounds:
        return get_empty_bytes()

    except Exception as e:
        print(f"🔴 Tile error: {e}", flush=True)
        return get_empty_bytes()


def get_empty_bytes():
    img = Image.new(
        "RGBA",
        (256, 256),
        (0, 0, 0, 0)
    )
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()