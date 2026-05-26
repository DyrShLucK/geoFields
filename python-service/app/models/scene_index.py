from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, UniqueConstraint
from sqlalchemy.sql import func
from ..db import Base


class SceneIndex(Base):
    __tablename__ = "scene_indices"

    id = Column(Integer, primary_key=True, index=True)

    scene_id = Column(Integer, ForeignKey("scenes.id", ondelete="CASCADE"), nullable=False)

    index_type = Column(String(10), nullable=False)

    local_path = Column(String, nullable=False)

    created_at = Column(DateTime, server_default=func.now())

    __table_args__ = (
        UniqueConstraint('scene_id', 'index_type', name='_scene_index_type_uc'),
    )