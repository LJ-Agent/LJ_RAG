"""Unified result wrapper for gRPC service methods."""
from dataclasses import dataclass, field
from typing import Any, Generic, TypeVar

T = TypeVar("T")


@dataclass
class MemoryResult(Generic[T]):
    """Standard result container for memory service operations."""
    success: bool = True
    data: T | None = None
    message: str = ""
    error_code: int = 0

    @staticmethod
    def ok(data: T = None, message: str = "ok") -> "MemoryResult[T]":
        return MemoryResult(success=True, data=data, message=message)

    @staticmethod
    def fail(message: str, error_code: int = 1) -> "MemoryResult":
        return MemoryResult(success=False, data=None, message=message, error_code=error_code)
