import os, json, shutil, uuid, traceback
from pathlib import Path
from typing import Dict, Optional
from .models import JobStatus

# Простое in-memory хранилище статусов (для продакшена заменить на Redis)
jobs: Dict[str, Dict] = {}

def process_job(job_id: str, upload_dir: Path, output_dir: Path, ollama_url: str, model: str):
    """Фоновая обработка: вызывает основную логику из main.py"""
    try:
        jobs[job_id]["status"] = JobStatus.PROCESSING
        jobs[job_id]["progress"] = "Извлечение файлов..."

        # 1. Распаковка если был ZIP
        input_path = upload_dir / f"{job_id}"
        input_path.mkdir(exist_ok=True)
        
        uploaded_file = upload_dir / f"{job_id}.zip"
        if uploaded_file.exists():
            import zipfile
            with zipfile.ZipFile(uploaded_file, "r") as zip_ref:
                zip_ref.extractall(input_path)
            uploaded_file.unlink()
        
        # Поиск .shp файла
        shp_file = next(input_path.glob("*.shp"), None)
        if not shp_file:
            raise ValueError("Файл .shp не найден в архиве")

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