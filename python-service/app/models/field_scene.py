from sqlalchemy import (
    Column,
    Integer,
    Float,
    Boolean,
    ForeignKey,
    TIMESTAMP
)

from datetime import datetime

from ..db import Base


class FieldScene(Base):
    __tablename__ = "field_scenes"

    id = Column(Integer, primary_key=True)

    field_id = Column(Integer, index=True)

    scene_id = Column(
        Integer,
        ForeignKey("scenes.id", ondelete="CASCADE"),
        index=True
    )

    # optional metadata
    intersect_area = Column(Float, default=0.0)

    processed = Column(Boolean, default=False)

    created_at = Column(
        TIMESTAMP,
        default=datetime.utcnow
    )