from sqlalchemy import Column, Integer, String, ForeignKey, Date, Float, DateTime, Index
from sqlalchemy.sql import func
from ..db import Base


class FieldAnalytic(Base):
    __tablename__ = "field_analytic"

    id = Column(Integer, primary_key=True, index=True)

    field_id = Column(Integer, nullable=False, index=True)

    scene_index_id = Column(Integer, ForeignKey("scene_indices.id"), nullable=True)

    date = Column(Date, nullable=False, index=True)

    local_path = Column(String, nullable=False)

    mean_value = Column(Float)

    created_at = Column(DateTime, server_default=func.now())

    __table_args__ = (
        Index('idx_field_res', 'field_id', 'date'),
    )