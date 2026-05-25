"""Distiller — extracts atomic facts from conversations via LLM distillation."""
import time
from typing import Any
from uuid import uuid4

from common.config_loader import get_config
from common.enums import FactCategory
from common.exceptions import DistillationException
from infrastructure.llm.adapter import chat_structured
from infrastructure.llm.prompts import DISTILL_PROMPT
from loguru import logger


def _format_conversation(messages: list[dict[str, Any]]) -> str:
    """Format conversation messages into a readable text block for the LLM prompt."""
    lines: list[str] = []
    for msg in messages:
        role = msg.get("role", "unknown")
        content = msg.get("content", "")
        ts = msg.get("timestamp_ms", 0)
        ts_str = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts / 1000)) if ts else ""
        prefix = f"[{ts_str}] {role.upper()}" if ts_str else f"[{role.upper()}]"
        lines.append(f"{prefix}: {content}")
    return "\n".join(lines)


def distill(
    user_id: int,
    session_id: str,
    messages: list[dict[str, Any]],
    config: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    """Distill a session conversation into atomic facts.

    Args:
        user_id: Owner user ID.
        session_id: Session identifier.
        messages: List of {"role", "content", "timestamp_ms", "metadata"} dicts.
        config: Optional overrides (model, importance_threshold, max_facts, etc.).

    Returns:
        List of atomic fact dicts ready for storage.
    """
    if not messages:
        return []

    importance_threshold = (config or {}).get("importance_threshold", 0.3)
    max_facts = (config or {}).get("max_facts_per_batch", 50)

    formatted = _format_conversation(messages)
    prompt = DISTILL_PROMPT.format(messages=formatted)

    try:
        result = chat_structured(
            messages=[{"role": "user", "content": prompt}],
            temperature=0.2,
            max_tokens=2048,
        )
    except Exception as e:
        raise DistillationException(f"LLM distillation failed: {e}") from e

    raw_facts: list[dict[str, Any]] = result.get("facts", [])
    if not raw_facts:
        logger.info(f"No facts extracted from conversation (user={user_id}, session={session_id})")
        return []

    now_ms = int(time.time() * 1000)
    facts: list[dict[str, Any]] = []
    for f in raw_facts:
        imp = float(f.get("importance", 0.5))
        if imp < importance_threshold:
            continue

        category = f.get("category", "factual")
        if category not in [e.value for e in FactCategory]:
            category = "factual"

        facts.append({
            "fact_id": str(uuid4()),
            "user_id": user_id,
            "session_id": session_id,
            "content": f["content"],
            "category": category,
            "importance": imp,
            "tags": f.get("tags", []),
            "sources": [],
            "access_count": 0,
            "created_at_ms": now_ms,
            "accessed_at_ms": now_ms,
        })

    # Limit
    facts.sort(key=lambda x: x["importance"], reverse=True)
    facts = facts[:max_facts]

    logger.info(
        f"Distilled {len(facts)} facts from {len(messages)} messages "
        f"(user={user_id}, session={session_id})"
    )
    return facts
