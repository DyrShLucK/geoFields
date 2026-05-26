from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, Date
from sqlalchemy.sql import func
from geoalchemy2 import Geometry

from app.db import Base


class Scene(Base):
    __tablename__ = "scenes"

    id = Column(Integer, primary_key=True)

    scene_id = Column(String, unique=True, nullable=False)

    platform = Column(String)

    acquisition_date = Column(Date)

    cloud_cover = Column(Float)

    footprint = Column(Geometry("POLYGON"))

    epsg = Column(Integer)

    local_path = Column(String)

    is_downloaded = Column(Boolean, default=False)

    created_at = Column(DateTime, server_default=func.now())