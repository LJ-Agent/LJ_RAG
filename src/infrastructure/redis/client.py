"""Redis client for working memory, caching, task queues, and distributed locks."""
import json
import time
from typing import Any, Optional
from uuid import uuid4

from redis import Redis

from common.config_loader import get_config
from common.exceptions import StorageException
from loguru import logger


_redis: Redis | None = None


def _get_cfg() -> dict[str, Any]:
    return get_config()["redis"]


def get_client() -> Redis:
    global _redis
    if _redis is not None:
        return _redis
    c = _get_cfg()
    _redis = Redis(
        host=c["host"],
        port=int(c["port"]),
        password=c["password"] or None,
        db=int(c.get("db", 1)),
        decode_responses=True,
        socket_connect_timeout=5,
        socket_keepalive=True,
    )
    _redis.ping()
    logger.info(f"Redis connected: {c['host']}:{c['port']} db={c.get('db', 1)}")
    return _redis


def close() -> None:
    global _redis
    if _redis:
        _redis.close()
        _redis = None


# ==================== Working Memory ====================

def set_working_memory(
    user_id: int, session_id: str, key: str, value: str, ttl: int | None = None
) -> str:
    """Store a single working memory entry. Returns the entry_id."""
    r = get_client()
    cfg = _get_cfg()
    if ttl is None:
        ttl = int(cfg.get("working_memory_ttl_seconds", 3600))

    entry_id = str(uuid4())
    entry_key = f"wm:{user_id}:{session_id}:{entry_id}"

    data = json.dumps({
        "entry_id": entry_id,
        "user_id": user_id,
        "session_id": session_id,
        "key": key,
        "value": value,
        "created_at_ms": int(time.time() * 1000),
    }, ensure_ascii=False)

    r.setex(entry_key, ttl, data)
    r.sadd(f"wm:index:{user_id}:{session_id}", entry_id)
    r.expire(f"wm:index:{user_id}:{session_id}", ttl + 300)
    return entry_id


def get_working_memory(user_id: int, session_id: str) -> list[dict[str, Any]]:
    """Retrieve all working memory entries for a session."""
    r = get_client()
    entry_ids = r.smembers(f"wm:index:{user_id}:{session_id}")
    if not entry_ids:
        return []

    keys = [f"wm:{user_id}:{session_id}:{eid}" for eid in entry_ids]
    values = r.mget(keys)
    entries: list[dict[str, Any]] = []
    for v in values:
        if v:
            try:
                entries.append(json.loads(v))
            except json.JSONDecodeError:
                pass
    return entries


def evict_working_memory(user_id: int, session_id: str) -> int:
    """Remove all working memory entries for a session. Returns evicted count."""
    r = get_client()
    entry_ids = r.smembers(f"wm:index:{user_id}:{session_id}")
    count = len(entry_ids)
    if entry_ids:
        keys = [f"wm:{user_id}:{session_id}:{eid}" for eid in entry_ids]
        r.delete(*keys)
        r.delete(f"wm:index:{user_id}:{session_id}")
    return count


def get_working_memory_keys(user_id: int, session_id: str, pattern: str = "*") -> list[str]:
    """Search working memory keys matching a pattern."""
    entries = get_working_memory(user_id, session_id)
    import fnmatch
    return [e["key"] for e in entries if fnmatch.fnmatch(e["key"], pattern)]


# ==================== Search Cache ====================

def get_search_cache(query_hash: str) -> dict[str, Any] | None:
    r = get_client()
    data = r.get(f"cache:search:{query_hash}")
    if data:
        return json.loads(data)
    return None


def set_search_cache(query_hash: str, result: dict[str, Any], ttl: int = 300) -> None:
    r = get_client()
    r.setex(f"cache:search:{query_hash}", ttl, json.dumps(result, ensure_ascii=False))


# ==================== Distributed Lock ====================

def acquire_lock(user_id: int, ttl: int = 30) -> bool:
    """Try to acquire a per-user distributed lock. Returns True on success."""
    r = get_client()
    return bool(r.set(f"lock:{user_id}", "1", nx=True, ex=ttl))


def release_lock(user_id: int) -> None:
    r = get_client()
    r.delete(f"lock:{user_id}")


# ==================== Streams (Task Queues) ====================

def push_to_stream(stream_name: str, message: dict[str, Any], max_len: int = 10000) -> str:
    """Push a message to a Redis Stream."""
    r = get_client()
    return r.xadd(stream_name, message, maxlen=max_len)
