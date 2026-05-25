"""Archiver — moves expired working memory from Redis to MySQL long-term storage."""
import time
from typing import Any

from common.config_loader import get_config
from infrastructure.mysql.client import insert_archive_entry
from infrastructure.redis.client import get_working_memory, evict_working_memory
from loguru import logger


def archive_session(
    user_id: int,
    session_id: str,
    keep_top: int | None = None,
) -> int:
    """Archive a session's working memory to MySQL.

    1. Read all working memory entries from Redis.
    2. Write them to working_memory_archive table.
    3. Keep the top-N most important entries in Redis, evict the rest.

    Args:
        user_id: Owner user ID.
        session_id: Session identifier.
        keep_top: Number of entries to keep in Redis (default from config).

    Returns:
        Number of entries archived.
    """
    if keep_top is None:
        keep_top = int(get_config()["engine"]["max_persistent_working_entries"])

    entries = get_working_memory(user_id, session_id)
    if not entries:
        logger.info(f"No working memory entries to archive for session {session_id}")
        return 0

    # Sort by creation time (most recent first) for keep_top selection
    entries.sort(key=lambda e: e.get("created_at_ms", 0), reverse=True)

    # Write all to archive
    archived_count = 0
    for entry in entries:
        try:
            insert_archive_entry({
                "entry_id": entry["entry_id"],
                "user_id": user_id,
                "session_id": session_id,
                "key": entry.get("key", ""),
                "value": entry.get("value", ""),
                "created_at_ms": entry.get("created_at_ms", 0),
            })
            archived_count += 1
        except Exception as e:
            logger.error(f"Failed to archive entry {entry.get('entry_id')}: {e}")

    # Evict from Redis (all entries removed from working set)
    evicted = evict_working_memory(user_id, session_id)

    # Re-add top N as persistent working entries
    for entry in entries[:keep_top]:
        try:
            from infrastructure.redis.client import set_working_memory
            set_working_memory(
                user_id=user_id,
                session_id=f"persistent:{session_id}",
                key=entry.get("key", ""),
                value=entry.get("value", ""),
                ttl=86400 * 7,  # 7 days for persistent entries
            )
        except Exception as e:
            logger.warning(f"Failed to re-add persistent entry: {e}")

    logger.info(
        f"Archived {archived_count} entries, evicted {evicted} from Redis, "
        f"kept {keep_top} persistent (user={user_id}, session={session_id})"
    )
    return archived_count
