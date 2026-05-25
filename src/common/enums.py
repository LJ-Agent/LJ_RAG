"""Enumerations for memory management domain."""
from enum import Enum


class FactCategory(str, Enum):
    FACTUAL = "factual"
    PREFERENCE = "preference"
    PLAN = "plan"
    KNOWLEDGE = "knowledge"
    RELATIONSHIP = "relationship"


class EpisodicPeriod(str, Enum):
    SESSION = "session"
    HOURLY = "hourly"
    DAILY = "daily"
    WEEKLY = "weekly"


class RuleCategory(str, Enum):
    PREFERENCE = "preference"
    HABIT = "habit"
    CONSTRAINT = "constraint"
    PROCEDURE = "procedure"


class ArchivePolicy(str, Enum):
    STANDARD = "STANDARD"
    IMMEDIATE = "IMMEDIATE"
    COMPACTED = "COMPACTED"


class TaskType(str, Enum):
    DISTILL = "distill"
    FORGET = "forget"
    ARCHIVE = "archive"


class TaskStatus(str, Enum):
    COMPLETED = "completed"
    FAILED = "failed"
    ACCEPTED = "accepted"


class CaptureStatus(str, Enum):
    ACCEPTED = "accepted"
    DISTILLED = "distilled"
    MERGED = "merged"
