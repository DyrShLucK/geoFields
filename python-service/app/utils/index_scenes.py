import os
import rasterio
from sqlalchemy import create_engine, text
from pathlib import Path
from datetime import datetime


DATABASE_URL = os.getenv("DATABASE_URL")

RAW_DIR = Path("/app/storage/raw")

engine = create_engine(DATABASE_URL)


def get_tif_metadata(tif_path):

    with rasterio.open(tif_path) as src:
        b = src.bounds
        wkt = f"POLYGON(({b.left} {b.bottom}, {b.right} {b.bottom}, {b.right} {b.top}, {b.left} {b.top}, {b.left} {b.bottom}))"
        epsg = src.crs.to_epsg() if src.crs else 4326
        return wkt, epsg


def run_index():
    print(f"🚀 Начинаю индексацию снимков в контейнере...")
    print(f"📂 Папка поиска: {RAW_DIR}")

    if not RAW_DIR.exists():
        print(f"❌ Ошибка: Папка {RAW_DIR} не найдена!")
        return

    tif_files = list(RAW_DIR.rglob("*.tif"))
    print(f"🔎 Найдено файлов: {len(tif_files)}")

    count = 0
    for tif_path in tif_files:
        scene_id = tif_path.stem
        date_str = tif_path.parent.name

        try:

            datetime.strptime(date_str, "%Y-%m-%d")
        except ValueError:
            print(f"⚠️ Пропуск {tif_path.name}: папка {date_str} не является датой.")
            continue


        db_path = str(tif_path)

        try:
            wkt, epsg = get_tif_metadata(tif_path)

            with engine.begin() as conn:

                check = conn.execute(
                    text("SELECT id FROM scenes WHERE scene_id = :sid"),
                    {"sid": scene_id}
                ).fetchone()

                if not check:
                    conn.execute(text("""
                        INSERT INTO scenes (
                            scene_id, platform, acquisition_date, 
                            footprint, epsg, local_path, is_downloaded
                        ) VALUES (
                            :sid, 'Sentinel-2', :date, 
                            ST_GeomFromText(:wkt, 4326), :epsg, :path, True
                        )
                    """), {
                        "sid": scene_id,
                        "date": date_str,
                        "wkt": wkt,
                        "epsg": epsg,
                        "path": db_path
                    })
                    print(f"✅ Индексирован: {scene_id}")
                    count += 1
                else:
                    print(f"ℹ️ Пропущен (уже в базе): {scene_id}")

        except Exception as e:
            print(f"🔴 Ошибка файла {scene_id}: {e}")

    print(f"🏁 Завершено. Добавлено новых сцен: {count}")


if __name__ == "__main__":
    run_index()