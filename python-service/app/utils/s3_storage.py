# Модуль работы с MinIO/S3 (GeoTIFF NDVI). Включение: S3_ENABLED=true в docker-compose.
import os
from pathlib import Path
from typing import Optional

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError


def _env(name: str, default: Optional[str] = None) -> Optional[str]:
    v = os.getenv(name)
    if v is None or v == "":
        return default
    return v


def s3_enabled() -> bool:
    v = (_env("S3_ENABLED", "false") or "").strip().lower()
    return v in ("1", "true", "yes", "on")


def s3_endpoint() -> str:
    return (_env("S3_ENDPOINT", "") or "").strip()


def s3_bucket_ndvi() -> str:
    return (_env("S3_BUCKET_NDVI", "geofields-ndvi") or "geofields-ndvi").strip()


def s3_bucket_tiles() -> str:
    return (_env("S3_BUCKET_TILES", "geofields-tiles") or "geofields-tiles").strip()


def _client():
    endpoint = s3_endpoint()
    access_key = _env("S3_ACCESS_KEY")
    secret_key = _env("S3_SECRET_KEY")

    session = boto3.session.Session()
    return session.client(
        "s3",
        endpoint_url=endpoint if endpoint else None,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        config=Config(signature_version="s3v4"),
        region_name=_env("S3_REGION", "us-east-1"),
    )

def ensure_bucket(bucket: str) -> None:
    if not s3_enabled():
        return
    try:
        _client().head_bucket(Bucket=bucket)
        return
    except ClientError:
        pass
    try:
        _client().create_bucket(Bucket=bucket)
    except ClientError:
        # best-effort
        return


def exists(bucket: str, key: str) -> bool:
    if not s3_enabled():
        return False
    try:
        _client().head_object(Bucket=bucket, Key=key)
        return True
    except ClientError as e:
        code = str(e.response.get("Error", {}).get("Code", "")).lower()
        if code in ("404", "notfound", "nosuchkey"):
            return False
        return False


def ndvi_tif_key(field_id: str, actual_date_str: str, scene_name: str) -> str:
    """Стабильный ключ GeoTIFF NDVI по полю и сцене."""
    return f"fields/{field_id}/{actual_date_str}_{scene_name}_ndvi.tif"


def put_file(bucket: str, key: str, local_path: str, content_type: Optional[str] = None) -> bool:
    """Загружает локальный файл в S3. Возвращает True при успехе."""
    if not s3_enabled():
        return False
    src = Path(local_path)
    if not src.is_file():
        return False
    ensure_bucket(bucket)
    extra = {}
    if content_type:
        extra["ContentType"] = content_type
    try:
        _client().upload_file(str(src), bucket, key, ExtraArgs=extra or None)
        return True
    except ClientError:
        return False


def ensure_ndvi_tif_uploaded(field_id: str, actual_date_str: str, scene_name: str, local_path: str) -> bool:
    """
    Кладёт GeoTIFF в бакет geofields-ndvi, если объекта ещё нет.
    Вызывается и после расчёта, и при cache HIT (файл уже на диске).
    """
    if not s3_enabled():
        return False
    bucket = s3_bucket_ndvi()
    key = ndvi_tif_key(field_id, actual_date_str, scene_name)
    if exists(bucket, key):
        return True
    return put_file(bucket, key, local_path, content_type="image/tiff")


def get_file(bucket: str, key: str, local_path: str) -> bool:
    """Download object to local_path. Returns True if downloaded."""
    if not s3_enabled():
        return False
    dst = Path(local_path)
    dst.parent.mkdir(parents=True, exist_ok=True)
    try:
        _client().download_file(bucket, key, str(dst))
        return True
    except ClientError:
        return False

