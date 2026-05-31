import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent.parent

STORAGE_DIR = BASE_DIR / "storage"
RAW_DIR = STORAGE_DIR / "raw"
INDICES_DIR = STORAGE_DIR / "indices"
FIELDS_DIR = STORAGE_DIR / "fields"
ELEVATION_RAW_PATH = RAW_DIR / "elevation" / "srtm.tif"

for p in [RAW_DIR, INDICES_DIR, FIELDS_DIR, ELEVATION_RAW_PATH.parent]:
    p.mkdir(parents=True, exist_ok=True)

DATABASE_URL = os.getenv("DATABASE_URL")

GEE_PROJECT_ID = os.getenv("GEE_PROJECT_ID")
GEE_SERVICE_ACCOUNT = os.getenv("GEE_SERVICE_ACCOUNT")
GEE_JSON_KEY = " "