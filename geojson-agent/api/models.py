from pydantic import BaseModel, Field
from typing import Optional, List
from enum import Enum

class JobStatus(str, Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"

class UploadResponse(BaseModel):
    job_id: str
    message: str = "Файл принят в обработку"
    status: JobStatus = JobStatus.PENDING

class JobStatusResponse(BaseModel):
    job_id: str
    status: JobStatus
    progress: Optional[str] = None
    error: Optional[str] = None
    result_url: Optional[str] = None

class HealthResponse(BaseModel):
    status: str = "ok"
    ollama: str = "connected"
    model: str = Field(..., description="Активная модель")