import rasterio
import rasterio.mask
import numpy as np


# ФУНКЦИЯ 1: Расчет индекса для всего снимка
def calculate_full_index(source_path, output_path, red_band=3, nir_band=4):
    with rasterio.open(source_path) as src:
        # Читаем каналы (в Rasterio нумерация с 1)
        red = src.read(3)  # B4
        nir = src.read(4)  # B8

        # Защита от деления на 0
        denom = (nir + red)
        ndvi = np.where(denom == 0, np.nan, (nir - red) / (denom + 1e-10))
        ndvi = np.clip(ndvi, -1, 1)

        # Для хранения на диске заменяем NaN на NoData значение
        ndvi_out = np.nan_to_num(ndvi, nan=-9999.0)

        # Копируем метаданные источника
        meta = src.meta.copy()

        # ВАЖНО: Обновляем только то, что изменилось
        meta.update({
            "driver": "GTiff",
            "count": 1,
            "dtype": "float32",
            "nodata": -9999.0,
            "crs": src.crs if src.crs else "EPSG:4326",  # Гарантируем наличие CRS
            "transform": src.transform,
            "compress": "lzw"  # Сжатие, чтобы файл весил меньше
        })

        with rasterio.open(output_path, "w", **meta) as dst:
            dst.write(ndvi_out, 1)
    print(f"✅ Полный индекс сохранен: {output_path}")


NODATA = -9999.0


def clip_index(source_index_path, geometry_dict, output_path):

    with rasterio.open(source_index_path) as src:

        try:

            # ОБРЕЗКА ПО ПОЛИГОНУ
            out_image, out_transform = rasterio.mask.mask(
                src,
                [geometry_dict],
                crop=True,
                filled=True,
                nodata=NODATA,
                all_touched=False,
            )

            data = out_image[0].astype(np.float32)

            # СРЕДНИЙ NDVI
            valid_data = data[data != NODATA]

            if valid_data.size > 0:
                mean_val = float(np.mean(valid_data))
            else:
                mean_val = 0.0

            # МЕТАДАННЫЕ
            meta = src.meta.copy()

            meta.update({
                "driver": "GTiff",
                "height": out_image.shape[1],
                "width": out_image.shape[2],
                "transform": out_transform,
                "count": 1,
                "dtype": "float32",
                "nodata": NODATA,
                "compress": "lzw"
            })

            # СОХРАНЕНИЕ
            with rasterio.open(output_path, "w", **meta) as dst:

                dst.write(data, 1)

                # ALPHA MASK
                alpha_mask = np.where(
                    data == NODATA,
                    0,
                    255
                ).astype(np.uint8)

                dst.write_mask(alpha_mask)

            return mean_val

        except Exception as e:

            print(f"⚠️ clip_index error: {e}")

            return 0.0


def generate_slope_raster(elevation_path, output_slope_path):
    """
    Расчёт карты уклонов (градусы) из GeoTIFF высот.
    NoData (-9999) не участвует в градиенте и сохраняется в выходном файле.
    """
    with rasterio.open(elevation_path) as src:
        elev = src.read(1).astype(np.float32)
        nodata = src.nodata if src.nodata is not None else NODATA
        transform = src.transform

        # Размер пикселя в единицах CRS; для WGS84 переводим градусы в метры.
        px_x = abs(transform.a)
        px_y = abs(transform.e)
        if src.crs and src.crs.is_geographic:
            px_x *= 111320.0
            px_y *= 111320.0

        valid = np.isfinite(elev) & (elev != nodata)
        elev_work = np.where(valid, elev, np.nan)

        # numpy.gradient: (d/dy, d/dx) с учётом шага сетки в метрах.
        dy, dx = np.gradient(elev_work, px_y, px_x)
        slope_deg = np.degrees(np.arctan(np.sqrt(dx ** 2 + dy ** 2)))

        slope_out = np.where(valid, slope_deg.astype(np.float32), NODATA)

        meta = src.meta.copy()
        meta.update({
            "driver": "GTiff",
            "count": 1,
            "dtype": "float32",
            "nodata": NODATA,
            "compress": "lzw",
        })

        with rasterio.open(output_slope_path, "w", **meta) as dst:
            dst.write(slope_out, 1)
            alpha_mask = np.where(slope_out == NODATA, 0, 255).astype(np.uint8)
            dst.write_mask(alpha_mask)

    print(f"✅ Карта уклонов сохранена: {output_slope_path}", flush=True)