"""Domain exceptions for memory management service."""


class MemoryException(Exception):
    """Base exception for all memory service errors."""
    def __init__(self, message: str, code: int = 1):
        super().__init__(message)
        self.message = message
        self.code = code


class DistillationException(MemoryException):
    """Raised when LLM distillation fails."""
    def __init__(self, message: str = "Distillation failed"):
        super().__init__(message, code=1001)


class ClassificationException(MemoryException):
    """Raised when fact classification fails."""
    def __init__(self, message: str = "Classification failed"):
        super().__init__(message, code=1002)


class DeduplicationException(MemoryException):
    """Raised when deduplication processing fails."""
    def __init__(self, message: str = "Deduplication failed"):
        super().__init__(message, code=1003)


class RetrievalException(MemoryException):
    """Raised when memory retrieval fails."""
    def __init__(self, message: str = "Retrieval failed"):
        super().__init__(message, code=2001)


class StorageException(MemoryException):
    """Raised when a storage backend operation fails."""
    def __init__(self, message: str = "Storage operation failed"):
        super().__init__(message, code=3001)


class UserNotFoundException(MemoryException):
    """Raised when the requested user has no memory profile."""
    def __init__(self, user_id: int):
        super().__init__(f"User memory profile not found: {user_id}", code=4001)


class PIIViolationException(MemoryException):
    """Raised when PII is detected and policy prevents storage."""
    def __init__(self, detail: str = "PII detected"):
        super().__init__(f"PII violation: {detail}", code=5001)
