from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base
from sqlalchemy import text
import json


from .core.config import DATABASE_URL


engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    pool_size=10,
    max_overflow=20,
)


SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine
)

Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def get_field_geometry(field_id: int):

    query = text("SELECT ST_AsGeoJSON(ST_Transform(geom, 4326)) FROM fields WHERE id = :id")

    with engine.connect() as conn:
        result = conn.execute(query, {"id": field_id}).fetchone()
        if result and result[0]:
            return json.loads(result[0])
    return None