"""Deduplicator — merges duplicate/similar facts using vector cosine similarity."""
import math
from typing import Any

from common.config_loader import get_config
from infrastructure.llm.adapter import embed
from infrastructure.milvus.client import search_by_user
from loguru import logger


def _cosine(a: list[float], b: list[float]) -> float:
    """Compute cosine similarity between two vectors."""
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(x * x for x in b))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


def deduplicate(
    user_id: int,
    new_facts: list[dict[str, Any]],
    threshold: float | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Deduplicate new facts against existing facts in Milvus.

    Strategy:
      - similarity > threshold  → merge (keep old fact_id, update content, take max importance)
      - threshold * 0.6 ~ threshold → keep both, link as related
      - similarity < threshold * 0.6 → insert as new

    Args:
        user_id: Owner user ID.
        new_facts: List of new fact dicts (must have 'content' field).
        threshold: Cosine similarity threshold (default from config: 0.85).

    Returns:
        (facts_to_insert, facts_to_merge) — insert list and merge list.
    """
    if threshold is None:
        threshold = float(get_config()["engine"]["dedup_threshold"])
    related_threshold = threshold * 0.6

    if not new_facts:
        return [], []

    # Embed all new facts
    texts = [f["content"] for f in new_facts]
    try:
        vectors = [embed(t) for t in texts]
    except Exception as e:
        logger.warning(f"Embedding failed during dedup, keeping all as new: {e}")
        return new_facts, []

    to_insert: list[dict[str, Any]] = []
    to_merge: list[dict[str, Any]] = []

    for i, fact in enumerate(new_facts):
        # Search for similar existing facts in user's partition
        try:
            hits = search_by_user(
                user_id=user_id,
                query_vector=vectors[i],
                top_k=5,
                score_threshold=related_threshold,
            )
        except Exception as e:
            logger.warning(f"Milvus search failed during dedup: {e}")
            to_insert.append(fact)
            continue

        if not hits:
            to_insert.append(fact)
            continue

        best = hits[0]
        similarity = best["score"]

        if similarity > threshold:
            # Merge: keep old fact_id, update content, take max importance
            merged = dict(fact)
            merged["fact_id"] = best["fact_id"]
            merged["importance"] = max(fact.get("importance", 0.5), best.get("importance", 0.5))
            merged["_merge_action"] = "update"
            to_merge.append(merged)
            logger.debug(f"Merged fact (sim={similarity:.3f}): {fact['content'][:80]}")
        else:
            to_insert.append(fact)

    logger.info(f"Dedup result: {len(to_insert)} insert, {len(to_merge)} merge out of {len(new_facts)}")
    return to_insert, to_merge
