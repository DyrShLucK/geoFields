import os, json, shutil, uuid, traceback
from pathlib import Path
from typing import Dict, Optional
from .models import JobStatus

# Простое in-memory хранилище статусов (для продакшена заменить на Redis)
jobs: Dict[str, Dict] = {}

def process_job(job_id: str, upload_dir: Path, output_dir: Path, ollama_url: str, model: str):
    """Фоновая обработка: вызывает основную логику из main.py"""
    input_path = upload_dir / job_id
    try:
        jobs[job_id]["status"] = JobStatus.PROCESSING
        jobs[job_id]["progress"] = "Извлечение файлов..."

        # 1. Распаковка ZIP (api/main.py сохраняет в {job_id}/upload.zip)
        zip_path = input_path / "upload.zip"
        if zip_path.exists():
            import zipfile
            with zipfile.ZipFile(zip_path, "r") as zip_ref:
                zip_ref.extractall(input_path)
            zip_path.unlink()

        shp_files = sorted(input_path.rglob("*.shp"))
        if not shp_files:
            raise ValueError("Файл .shp не найден в архиве")
        shp_file = shp_files[0]

        jobs[job_id]["progress"] = "Запуск обработки..."

        # 2. Вызов основной логики (импортируем из корневого main.py)
        import sys
        sys.path.insert(0, str(Path(__file__).parent.parent))
        from main import process_shapefile_logic  # см. шаг 5

        result_path = output_dir / f"{job_id}.geojson"
        process_shapefile_logic(
            shp_path=shp_file,
            out_path=result_path,
            ollama_url=ollama_url,
            model=model
        )

        jobs[job_id]["status"] = JobStatus.COMPLETED
        jobs[job_id]["result_url"] = f"/api/download/{job_id}"
        jobs[job_id]["progress"] = "Готово"

    except Exception as e:
        jobs[job_id]["status"] = JobStatus.FAILED
        jobs[job_id]["error"] = str(e)
        jobs[job_id]["progress"] = "Ошибка"
        traceback.print_exc()
    finally:
        # Очистка временных файлов
        if input_path.exists():
            shutil.rmtree(input_path, ignore_errors=True)