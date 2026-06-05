import os, uuid, shutil
from pathlib import Path
from contextlib import asynccontextmanager
from fastapi import FastAPI, UploadFile, File, BackgroundTasks, HTTPException
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware

from .models import UploadResponse, JobStatusResponse, HealthResponse, JobStatus
from .worker import jobs, process_job

UPLOAD_DIR = Path("/app/temp")
OUTPUT_DIR = Path("/app/output")
UPLOAD_DIR.mkdir(exist_ok=True)
OUTPUT_DIR.mkdir(exist_ok=True)

OLLAMA_URL = os.getenv("OLLAMA_BASE_URL", "http://ollama:11434")
MODEL_NAME = os.getenv("MODEL_NAME", "qwen2.5:14b")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: проверка связи с Ollama
    import requests, time
    for _ in range(30):
        try:
            requests.get(f"{OLLAMA_URL}/api/version", timeout=2)
            break
        except:
            time.sleep(2)
    yield
    # Shutdown: можно добавить очистку старых задач

app = FastAPI(
    title="GeoJSON Agent API",
    description="Конвертация shapefile → GeoJSON с ИИ-маппингом полей",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/api/health", response_model=HealthResponse)
async def health():
    return HealthResponse(model=MODEL_NAME)

@app.post("/api/upload", response_model=UploadResponse, status_code=202)
async def upload_shapefile(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
):
    job_id = str(uuid.uuid4())
    
    # Сохраняем файл
    temp_path = UPLOAD_DIR / f"{job_id}"
    temp_path.mkdir(exist_ok=True)
    
    file_ext = Path(file.filename).suffix.lower()
    if file_ext == ".zip":
        # ZIP-архив с shapefile
        zip_path = temp_path / "upload.zip"
        with open(zip_path, "wb") as f:
            content = await file.read()
            f.write(content)
    elif file_ext in [".shp", ".dbf", ".shx", ".prj"]:
        # Одиночный файл — сохраняем с оригинальным именем
        file_path = temp_path / file.filename
        with open(file_path, "wb") as f:
            content = await file.read()
            f.write(content)
    else:
        raise HTTPException(400, "Поддерживаются .zip или файлы shapefile (.shp, .dbf, .shx, .prj)")

    # Инициализируем запись о задаче
    jobs[job_id] = {
        "status": JobStatus.PENDING,
        "progress": "Файл загружен",
        "created": True
    }

    # Запускаем обработку в фоне
    background_tasks.add_task(
        process_job,
        job_id=job_id,
        upload_dir=UPLOAD_DIR,
        output_dir=OUTPUT_DIR,
        ollama_url=OLLAMA_URL,
        model=MODEL_NAME
    )

    return UploadResponse(job_id=job_id)

@app.get("/api/status/{job_id}", response_model=JobStatusResponse)
async def get_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(404, "Задача не найдена")
    
    job = jobs[job_id]
    return JobStatusResponse(
        job_id=job_id,
        status=job["status"],
        progress=job.get("progress"),
        error=job.get("error"),
        result_url=job.get("result_url")
    )

@app.get("/api/download/{job_id}")
async def download_result(job_id: str):
    result_path = OUTPUT_DIR / f"{job_id}.geojson"
    if not result_path.exists():
        raise HTTPException(404, "Результат не найден")
    
    return FileResponse(
        path=result_path,
        filename=f"result_{job_id[:8]}.geojson",
        media_type="application/geo+json"
    )

@app.delete("/api/cleanup/{job_id}")
async def cleanup(job_id: str):
    """Удаление результата после скачивания"""
    result_path = OUTPUT_DIR / f"{job_id}.geojson"
    if result_path.exists():
        result_path.unlink()
    if job_id in jobs:
        del jobs[job_id]
    return {"message": "Очищено"}