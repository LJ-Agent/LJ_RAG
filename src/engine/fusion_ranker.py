"""FusionRanker — re-ranks retrieval results with time decay and importance weighting."""
import math
import time
from typing import Any

from common.config_loader import get_config


def _get_lambda() -> float:
    return float(get_config()["engine"]["time_decay_lambda"])


def time_decay(created_at_ms: int, lambda_val: float | None = None) -> float:
    """Exponential time decay: exp(-lambda * hours_since_creation).

    Recent facts get higher weight.
    """
    if lambda_val is None:
        lambda_val = _get_lambda()
    now_ms = int(time.time() * 1000)
    hours = (now_ms - created_at_ms) / (1000 * 3600)
    if hours < 0:
        hours = 0
    return math.exp(-lambda_val * hours)


def rank(
    items: list[dict[str, Any]],
    lambda_val: float | None = None,
) -> list[dict[str, Any]]:
    """Re-rank items using the fusion ranking formula.

    final_score = normalized_rrf_score × time_decay × importance_weight

    Items are expected to have:
      - _rrf_score (from RRF fusion) or defaults to 0.5
      - importance (float 0-1)
      - created_at_ms (int, epoch millis)
    """
    if lambda_val is None:
        lambda_val = _get_lambda()

    if not items:
        return items

    scored: list[tuple[dict[str, Any], float]] = []
    for item in items:
        rrf = item.get("_rrf_score", 0.5)
        importance = float(item.get("importance", 0.5))
        created = int(item.get("created_at_ms", 0))

        decay = time_decay(created, lambda_val)
        final = rrf * decay * importance
        scored.append((item, final))

    scored.sort(key=lambda x: x[1], reverse=True)

    result: list[dict[str, Any]] = []
    for item, final_score in scored:
        entry = dict(item)
        entry["_final_score"] = final_score
        result.append(entry)

    return result


def top_k(
    items: list[dict[str, Any]],
    k: int,
    lambda_val: float | None = None,
) -> list[dict[str, Any]]:
    """Rank then return top-k items."""
    ranked = rank(items, lambda_val)
    return ranked[:k]
