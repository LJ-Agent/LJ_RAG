"""Classifier — PII detection, fact categorization, and importance scoring."""
import re
from typing import Any

from common.enums import FactCategory
from common.exceptions import PIIViolationException
from loguru import logger

# PII detection patterns (Chinese + international)
_PII_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("身份证号", re.compile(r"[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx]")),
    ("手机号", re.compile(r"1[3-9]\d{9}")),
    ("邮箱", re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")),
    ("银行卡", re.compile(r"\d{16,19}")),
    ("地址", re.compile(r"(?:省|市|区|县|镇|村|路|街|巷|号|栋|单元|室).{2,20}(?:省|市|区|县|镇|村|路|街|巷|号|栋|单元|室)")),
    ("IP地址", re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")),
]


def detect_pii(text: str) -> list[str]:
    """Scan text for PII patterns. Returns list of detected PII type names."""
    found: list[str] = []
    for pii_type, pattern in _PII_PATTERNS:
        if pattern.search(text):
            found.append(pii_type)
    return found


def sanitize_pii(text: str) -> tuple[str, bool]:
    """Replace detected PII with placeholders. Returns (sanitized_text, had_pii)."""
    had_pii = False
    result = text
    for _pii_type, pattern in _PII_PATTERNS:
        if pattern.search(result):
            had_pii = True
            result = pattern.sub("[REDACTED]", result)
    return result, had_pii


def validate_no_pii(fact: dict[str, Any], enable_detection: bool = True) -> None:
    """Raise PIIViolationException if PII is detected and detection is enabled."""
    if not enable_detection:
        return
    content = fact.get("content", "")
    detected = detect_pii(content)
    if detected:
        raise PIIViolationException(f"PII types {detected} found in fact: {content[:100]}")


def classify_category(content: str, suggested: str = "") -> str:
    """Validate and normalize fact category."""
    valid = {e.value for e in FactCategory}
    if suggested in valid:
        return suggested
    return "factual"


def evaluate_importance(content: str, category: str) -> float:
    """Heuristic importance scoring as a fallback when LLM scoring is unavailable.

    This is a lightweight regex+keyword based scorer, less accurate than LLM.
    """
    score = 0.3  # baseline

    # Preference indicators
    if category == "preference":
        score = 0.5
    elif category == "plan":
        score = 0.6
    elif category == "relationship":
        score = 0.4

    # Keyword boosts
    importance_indicators = [
        (r"必须|一定|绝对|永远|从不|决定|承诺|约定|目标|计划", 0.25),
        (r"喜欢|讨厌|希望|想要|需要|偏好|习惯|经常|每次", 0.15),
        (r"应该|可能|大概|觉得|认为|也许|或许", 0.05),
    ]
    for pattern, boost in importance_indicators:
        if re.search(pattern, content):
            score += boost

    return min(score, 1.0)
