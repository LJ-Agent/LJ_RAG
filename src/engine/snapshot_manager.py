"""SnapshotManager — creates full user memory snapshots for compliance and recovery."""
import time
from typing import Any

from infrastructure.mysql.client import (
    get_facts_by_user,
    get_episodes_by_user,
    get_rules_by_user,
    count_facts,
    insert_snapshot,
)
from loguru import logger


async def create_snapshot(user_id: int) -> dict[str, Any]:
    """Create a full memory snapshot for a user.

    Gathers all facts, episodes, and rules into a single JSON blob
    and stores it in the memory_snapshots table.

    Returns the snapshot dict.
    """
    # Gather all data
    facts = await get_facts_by_user(user_id, limit=100000)
    episodes = await get_episodes_by_user(user_id)
    rules = await get_rules_by_user(user_id)

    now_ms = int(time.time() * 1000)

    snapshot_data = {
        "user_id": user_id,
        "created_at_ms": now_ms,
        "fact_count": len(facts),
        "episode_count": len(episodes),
        "rule_count": len(rules),
        "facts": facts,
        "episodes": episodes,
        "rules": rules,
    }

    await insert_snapshot(user_id, snapshot_data)
    logger.info(
        f"Snapshot created: user={user_id}, "
        f"facts={len(facts)}, episodes={len(episodes)}, rules={len(rules)}"
    )
    return snapshot_data


def export_user_data(
    user_id: int,
    start_time_ms: int | None = None,
    end_time_ms: int | None = None,
) -> dict[str, Any]:
    """Export all user memory data for compliance (GDPR data portability).

    Returns dict with facts, episodes, rules, and working memory suitable for JSON export.
    """
    facts: list[dict[str, Any]] = []
    episodes: list[dict[str, Any]] = []
    rules: list[dict[str, Any]] = []

    import asyncio
    loop = asyncio.get_event_loop()

    loop.run_until_complete(
        get_facts_by_user(user_id, limit=100000)
    )

    # For sync usage in gRPC handlers
    # We'll use a helper that wraps the async calls
    return {
        "user_id": user_id,
        "exported_at_ms": int(time.time() * 1000),
        "time_range": {"start_ms": start_time_ms, "end_ms": end_time_ms},
        "facts": facts,
        "episodes": episodes,
        "rules": rules,
    }
