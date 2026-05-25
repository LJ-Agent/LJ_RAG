"""Retriever — hybrid memory retrieval combining vector + keyword + working memory."""
import time
import hashlib
import json
from typing import Any

from common.config_loader import get_config
from infrastructure.llm.adapter import embed
from infrastructure.milvus.client import search_by_user
from infrastructure.mysql.client import search_facts_by_keyword, get_facts_by_user, get_rules_by_user, get_episodes_by_user
from infrastructure.redis.client import get_working_memory, get_search_cache, set_search_cache
from loguru import logger


def _rrf_fuse(
    vector_results: list[dict[str, Any]],
    keyword_results: list[dict[str, Any]],
    k: int = 60,
) -> list[dict[str, Any]]:
    """Reciprocal Rank Fusion combining two ranked result lists."""
    scores: dict[str, float] = {}
    items: dict[str, dict[str, Any]] = {}

    for rank, item in enumerate(vector_results):
        fid = item.get("fact_id", "")
        scores[fid] = scores.get(fid, 0) + 1.0 / (k + rank + 1)
        items[fid] = item

    for rank, item in enumerate(keyword_results):
        fid = item.get("fact_id", "")
        scores[fid] = scores.get(fid, 0) + 1.0 / (k + rank + 1)
        items[fid] = item

    fused = sorted(scores.keys(), key=lambda x: scores[x], reverse=True)
    result: list[dict[str, Any]] = []
    for fid in fused:
        entry = dict(items[fid])
        entry["_rrf_score"] = scores[fid]
        result.append(entry)
    return result


def _query_hash(user_id: int, query: str, categories: list[str] | None) -> str:
    raw = f"{user_id}:{query}:{sorted(categories) if categories else 'all'}"
    return hashlib.md5(raw.encode()).hexdigest()


def retrieve(
    user_id: int,
    query: str,
    top_k: int = 20,
    score_threshold: float = 0.0,
    categories: list[str] | None = None,
    time_range_start_ms: int | None = None,
    time_range_end_ms: int | None = None,
    session_id: str | None = None,
) -> dict[str, Any]:
    """Hybrid memory retrieval.

    Searches: Milvus (vector) + MySQL (keyword) + Redis (working memory).

    Returns: {"facts": [...], "episodes": [...], "rules": [...], "working": [...], "latency_ms": ...}
    """
    start = time.time()

    # Check cache for non-personalized queries
    cache_h = _query_hash(user_id, query, categories)
    if not session_id:  # Don't cache session-specific retrievals
        cached = get_search_cache(cache_h)
        if cached:
            cached["latency_ms"] = (time.time() - start) * 1000
            return cached

    # 1. Vector retrieval (Milvus)
    try:
        query_vec = embed(query)
        category_filter = categories[0] if categories and len(categories) == 1 else None
        vector_hits = search_by_user(
            user_id=user_id,
            query_vector=query_vec,
            top_k=top_k * 2,
            score_threshold=score_threshold,
            category_filter=category_filter,
        )
    except Exception as e:
        logger.warning(f"Vector retrieval failed, falling back to keyword only: {e}")
        vector_hits = []

    # 2. Keyword retrieval (MySQL LIKE)
    try:
        keyword_hits = search_facts_by_keyword(user_id, query, limit=top_k)
        for kw in keyword_hits:
            kw["_source"] = "keyword"
    except Exception as e:
        logger.warning(f"Keyword retrieval failed: {e}")
        keyword_hits = []

    # 3. RRF fusion for facts
    fused_facts = _rrf_fuse(vector_hits, keyword_hits, k=60)

    # Apply time range filter
    if time_range_start_ms is not None or time_range_end_ms is not None:
        filtered: list[dict[str, Any]] = []
        for f in fused_facts:
            created = f.get("created_at_ms", 0)
            if time_range_start_ms is not None and created < time_range_start_ms:
                continue
            if time_range_end_ms is not None and created > time_range_end_ms:
                continue
            filtered.append(f)
        fused_facts = filtered

    # Apply category filter if multiple specified
    if categories and len(categories) > 1:
        fused_facts = [f for f in fused_facts if f.get("category") in categories]

    fused_facts = fused_facts[:top_k]

    # 4. Get episodes and rules (augment context)
    episodes: list[dict[str, Any]] = []
    rules: list[dict[str, Any]] = []
    try:
        episodes = get_episodes_by_user(user_id)
    except Exception as e:
        logger.warning(f"Episode retrieval failed: {e}")
    try:
        rules = get_rules_by_user(user_id)
    except Exception as e:
        logger.warning(f"Rule retrieval failed: {e}")

    # 5. Working memory (Redis)
    working: list[dict[str, Any]] = []
    if session_id:
        try:
            working = get_working_memory(user_id, session_id)
        except Exception as e:
            logger.warning(f"Working memory retrieval failed: {e}")

    latency = (time.time() - start) * 1000
    result = {
        "facts": fused_facts,
        "episodes": episodes,
        "rules": rules,
        "working": working if session_id else [],
        "latency_ms": latency,
    }

    # Cache non-session results
    if not session_id:
        try:
            set_search_cache(cache_h, result, ttl=300)
        except Exception:
            pass

    return result
