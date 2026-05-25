"""MySQL client using aiomysql for async connection pooling.

Provides CRUD operations for all memory domain entities.
"""
import json
import asyncio
from dataclasses import dataclass, field
from typing import Any, Optional

import aiomysql
from loguru import logger

from common.config_loader import get_config
from common.exceptions import StorageException


@dataclass
class MySQLConfig:
    host: str = "localhost"
    port: int = 3307
    user: str = "root"
    password: str = "root123"
    database: str = "rag_db"
    pool_size: int = 10
    charset: str = "utf8mb4"


_pool: aiomysql.Pool | None = None


def _get_cfg() -> MySQLConfig:
    cfg = get_config()
    return MySQLConfig(
        host=cfg["mysql"]["host"],  # pyright: ignore[reportArgumentType]
        port=int(cfg["mysql"]["port"]),  # pyright: ignore[reportArgumentType]
        user=cfg["mysql"]["user"],  # pyright: ignore[reportArgumentType]
        password=cfg["mysql"]["password"],  # pyright: ignore[reportArgumentType]
        database=cfg["mysql"]["database"],  # pyright: ignore[reportArgumentType]
        pool_size=int(cfg["mysql"]["pool_size"]),  # pyright: ignore[reportArgumentType]
        charset=cfg["mysql"].get("charset", "utf8mb4"),  # pyright: ignore[reportArgumentType]
    )


async def get_pool() -> aiomysql.Pool:
    """Get or create the MySQL connection pool."""
    global _pool
    if _pool is not None:
        return _pool
    c = _get_cfg()
    _pool = await aiomysql.create_pool(
        host=c.host,
        port=c.port,
        user=c.user,
        password=c.password,
        db=c.database,
        charset=c.charset,
        minsize=2,
        maxsize=c.pool_size,
        autocommit=True,
    )
    logger.info(f"MySQL pool created: {c.host}:{c.port}/{c.database}")
    return _pool


async def init_schema() -> None:
    """Run schema initialization from sql/init.sql."""
    import os
    from pathlib import Path

    sql_path = Path(__file__).parent.parent.parent.parent / "sql" / "init.sql"
    if not sql_path.exists():
        logger.warning(f"Schema file not found: {sql_path}")
        return

    pool = await get_pool()
    with open(sql_path, "r", encoding="utf-8") as f:
        sql = f.read()

    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            for stmt in sql.split(";"):
                stmt = stmt.strip()
                if stmt and not stmt.startswith("--"):
                    try:
                        await cur.execute(stmt)
                    except Exception as e:
                        logger.warning(f"Schema statement skipped: {e}")
    logger.info("MySQL schema initialized")


async def close_pool() -> None:
    """Close the connection pool."""
    global _pool
    if _pool:
        _pool.close()
        await _pool.wait_closed()
        _pool = None
        logger.info("MySQL pool closed")


# ==================== Atomic Facts ====================

async def insert_fact(fact: dict[str, Any]) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """INSERT INTO atomic_facts
                   (fact_id, user_id, session_id, content, category, importance,
                    tags, source_docs, access_count, created_at_ms, accessed_at_ms)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    fact["fact_id"], fact["user_id"], fact.get("session_id", ""),
                    fact["content"], fact.get("category", "factual"),
                    fact.get("importance", 0.5),
                    json.dumps(fact.get("tags", [])) if fact.get("tags") else None,
                    json.dumps(fact.get("source_docs", [])) if fact.get("source_docs") else None,
                    fact.get("access_count", 0),
                    fact["created_at_ms"], fact.get("accessed_at_ms", fact["created_at_ms"]),
                ),
            )


async def get_facts_by_user(
    user_id: int,
    categories: list[str] | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[dict[str, Any]]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            if categories:
                placeholders = ",".join(["%s"] * len(categories))
                await cur.execute(
                    f"""SELECT * FROM atomic_facts
                        WHERE user_id = %s AND category IN ({placeholders})
                        ORDER BY importance DESC, created_at_ms DESC
                        LIMIT %s OFFSET %s""",
                    (user_id, *categories, limit, offset),
                )
            else:
                await cur.execute(
                    """SELECT * FROM atomic_facts
                       WHERE user_id = %s
                       ORDER BY importance DESC, created_at_ms DESC
                       LIMIT %s OFFSET %s""",
                    (user_id, limit, offset),
                )
            return list(await cur.fetchall())


async def search_facts_by_keyword(
    user_id: int,
    keyword: str,
    limit: int = 20,
) -> list[dict[str, Any]]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(
                """SELECT * FROM atomic_facts
                   WHERE user_id = %s AND content LIKE %s
                   ORDER BY importance DESC
                   LIMIT %s""",
                (user_id, f"%{keyword}%", limit),
            )
            return list(await cur.fetchall())


async def update_fact(fact_id: str, updates: dict[str, Any]) -> None:
    set_clauses = []
    values: list[Any] = []
    for k, v in updates.items():
        if k in ("fact_id", "id"):
            continue
        db_col = _to_snake(k)
        set_clauses.append(f"{db_col} = %s")
        values.append(v if not isinstance(v, (list, dict)) else json.dumps(v))

    if not set_clauses:
        return

    values.append(fact_id)
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                f"UPDATE atomic_facts SET {', '.join(set_clauses)} WHERE fact_id = %s",
                tuple(values),
            )


async def delete_facts_by_user(user_id: int) -> int:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            result = await cur.execute(
                "DELETE FROM atomic_facts WHERE user_id = %s", (user_id,)
            )
            return result


async def count_facts(user_id: int) -> int:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                "SELECT COUNT(*) FROM atomic_facts WHERE user_id = %s", (user_id,)
            )
            row = await cur.fetchone()
            return row[0] if row else 0


# ==================== Episodic Summaries ====================

async def insert_episode(episode: dict[str, Any]) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """INSERT INTO episodic_summaries
                   (episode_id, user_id, session_id, summary, period,
                    key_fact_ids, start_time_ms, end_time_ms, created_at_ms)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    episode["episode_id"], episode["user_id"],
                    episode.get("session_id", ""), episode["summary"],
                    episode.get("period", "session"),
                    json.dumps(episode.get("key_fact_ids", [])) if episode.get("key_fact_ids") else None,
                    episode["start_time_ms"], episode["end_time_ms"],
                    episode["created_at_ms"],
                ),
            )


async def get_episodes_by_user(user_id: int, period: str | None = None) -> list[dict[str, Any]]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            if period:
                await cur.execute(
                    """SELECT * FROM episodic_summaries
                       WHERE user_id = %s AND period = %s
                       ORDER BY end_time_ms DESC LIMIT 20""",
                    (user_id, period),
                )
            else:
                await cur.execute(
                    """SELECT * FROM episodic_summaries
                       WHERE user_id = %s
                       ORDER BY end_time_ms DESC LIMIT 20""",
                    (user_id,),
                )
            return list(await cur.fetchall())


async def delete_episodes_by_user(user_id: int) -> int:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            result = await cur.execute(
                "DELETE FROM episodic_summaries WHERE user_id = %s", (user_id,)
            )
            return result


# ==================== Procedural Rules ====================

async def insert_rule(rule: dict[str, Any]) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """INSERT INTO procedural_rules
                   (rule_id, user_id, rule_content, category, supporting_fact_ids,
                    confidence, activation_count, created_at_ms, last_activated_ms)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    rule["rule_id"], rule["user_id"], rule["rule_content"],
                    rule.get("category", "preference"),
                    json.dumps(rule.get("supporting_fact_ids", [])) if rule.get("supporting_fact_ids") else None,
                    rule.get("confidence", 0.5),
                    rule.get("activation_count", 0),
                    rule["created_at_ms"],
                    rule.get("last_activated_ms", rule["created_at_ms"]),
                ),
            )


async def get_rules_by_user(user_id: int) -> list[dict[str, Any]]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(
                """SELECT * FROM procedural_rules
                   WHERE user_id = %s
                   ORDER BY confidence DESC, activation_count DESC""",
                (user_id,),
            )
            return list(await cur.fetchall())


async def update_rule_activation(rule_id: str) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """UPDATE procedural_rules
                   SET activation_count = activation_count + 1,
                       last_activated_ms = UNIX_TIMESTAMP(NOW()) * 1000
                   WHERE rule_id = %s""",
                (rule_id,),
            )


async def delete_rules_by_user(user_id: int) -> int:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            result = await cur.execute(
                "DELETE FROM procedural_rules WHERE user_id = %s", (user_id,)
            )
            return result


# ==================== Working Memory Archive ====================

async def insert_archive_entry(entry: dict[str, Any]) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """INSERT INTO working_memory_archive
                   (entry_id, user_id, session_id, entry_key, entry_value, created_at_ms)
                   VALUES (%s,%s,%s,%s,%s,%s)""",
                (
                    entry["entry_id"], entry["user_id"], entry["session_id"],
                    entry["key"], entry.get("value", ""),
                    entry.get("created_at_ms", 0),
                ),
            )


async def get_archive_by_session(user_id: int, session_id: str) -> list[dict[str, Any]]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(
                """SELECT * FROM working_memory_archive
                   WHERE user_id = %s AND session_id = %s
                   ORDER BY archived_at DESC""",
                (user_id, session_id),
            )
            return list(await cur.fetchall())


# ==================== User Profiles ====================

async def upsert_user_profile(user_id: int) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """INSERT INTO user_profiles (user_id, last_active_at)
                   VALUES (%s, NOW())
                   ON DUPLICATE KEY UPDATE last_active_at = NOW()""",
                (user_id,),
            )


async def update_profile_counts(
    user_id: int, *, fact_delta: int = 0, episode_delta: int = 0, rule_delta: int = 0
) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """UPDATE user_profiles
                   SET total_facts = total_facts + %s,
                       total_episodes = total_episodes + %s,
                       total_rules = total_rules + %s,
                       last_active_at = NOW()
                   WHERE user_id = %s""",
                (fact_delta, episode_delta, rule_delta, user_id),
            )


# ==================== Audit Logs ====================

async def insert_audit_log(
    user_id: int,
    operation: str,
    target_type: str,
    target_id: str | None = None,
    detail: dict[str, Any] | None = None,
) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """INSERT INTO audit_logs
                   (user_id, operation, target_type, target_id, detail)
                   VALUES (%s,%s,%s,%s,%s)""",
                (
                    user_id, operation, target_type, target_id,
                    json.dumps(detail) if detail else None,
                ),
            )


# ==================== Memory Snapshots ====================

async def insert_snapshot(user_id: int, snapshot_data: dict[str, Any]) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """INSERT INTO memory_snapshots
                   (user_id, snapshot_data, fact_count, episode_count, rule_count)
                   VALUES (%s,%s,%s,%s,%s)""",
                (
                    user_id,
                    json.dumps(snapshot_data),
                    snapshot_data.get("fact_count", 0),
                    snapshot_data.get("episode_count", 0),
                    snapshot_data.get("rule_count", 0),
                ),
            )


# ==================== Helpers ====================

def _to_snake(name: str) -> str:
    import re
    s1 = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", name)
    s2 = re.sub(r"([a-z\d])([A-Z])", r"\1_\2", s1)
    return s2.lower()
