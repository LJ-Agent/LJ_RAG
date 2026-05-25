"""Milvus vector database client for user memory embeddings.

Collection: user_memories with partition_key=user_id for hard user-level isolation.
"""
from typing import Any

from loguru import logger
from pymilvus import (
    Collection,
    CollectionSchema,
    DataType,
    FieldSchema,
    MilvusClient,
    connections,
    utility,
)

from common.config_loader import get_config
from common.exceptions import StorageException


def _get_cfg() -> dict[str, Any]:
    return get_config()["milvus"]


# ==================== Connection ====================

def connect_milvus() -> None:
    cfg = _get_cfg()
    connections.connect(
        alias="default",
        host=cfg["host"],
        port=str(cfg["port"]),
    )
    logger.info(f"Milvus connected: {cfg['host']}:{cfg['port']}")


def disconnect_milvus() -> None:
    connections.disconnect("default")
    logger.info("Milvus disconnected")


# ==================== Collection Management ====================

def _get_schema() -> CollectionSchema:
    cfg = _get_cfg()
    dim = int(cfg.get("dim", 1536))

    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
        FieldSchema(name="fact_id", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="user_id", dtype=DataType.VARCHAR, max_length=32, is_partition_key=True),
        FieldSchema(name="category", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="importance", dtype=DataType.FLOAT),
        FieldSchema(name="created_at_ms", dtype=DataType.INT64),
        FieldSchema(name="fact_vector", dtype=DataType.FLOAT_VECTOR, dim=dim),
    ]
    return CollectionSchema(
        fields,
        description="User memory atomic facts with partition key isolation",
        enable_dynamic_field=True,
    )


def ensure_collection() -> Collection:
    cfg = _get_cfg()
    collection_name = cfg["collection_name"]

    if utility.has_collection(collection_name):
        col = Collection(collection_name)
        logger.info(f"Collection '{collection_name}' already exists, loaded: {col.num_entities} entities")
        return col

    schema = _get_schema()
    col = Collection(collection_name, schema)
    logger.info(f"Collection '{collection_name}' created")

    # Create HNSW index
    index_params = {
        "index_type": cfg.get("index_type", "HNSW"),
        "metric_type": cfg.get("metric_type", "COSINE"),
        "params": {
            "M": int(cfg.get("index_params", {}).get("M", 16)),
            "efConstruction": int(cfg.get("index_params", {}).get("efConstruction", 200)),
        },
    }
    col.create_index("fact_vector", index_params)
    logger.info(f"HNSW index created on fact_vector")
    return col


def get_collection() -> Collection:
    cfg = _get_cfg()
    return Collection(cfg["collection_name"])


# ==================== Vector Operations ====================

def insert_fact_vectors(facts: list[dict[str, Any]]) -> list[int]:
    """Insert fact vectors into Milvus. Each fact dict must have:
    fact_id, user_id, category, importance, created_at_ms, fact_vector.
    Returns list of inserted primary key ids.
    """
    if not facts:
        return []

    col = get_collection()
    data: list[list[Any]] = [[], [], [], [], [], []]
    for f in facts:
        data[0].append(f["fact_id"])
        data[1].append(str(f["user_id"]))
        data[2].append(f.get("category", "factual"))
        data[3].append(float(f.get("importance", 0.5)))
        data[4].append(int(f.get("created_at_ms", 0)))
        data[5].append(f["fact_vector"])

    result = col.insert(data)
    col.flush()
    return result.primary_keys


def search_by_user(
    user_id: int,
    query_vector: list[float],
    top_k: int = 20,
    score_threshold: float = 0.0,
    category_filter: str | None = None,
) -> list[dict[str, Any]]:
    """ANN search within a specific user's partition.

    Uses partition key filtering (Milvus enforces user isolation).
    """
    col = get_collection()
    col.load()

    search_params = {
        "metric_type": _get_cfg().get("metric_type", "COSINE"),
        "params": {"ef": int(_get_cfg().get("search_params", {}).get("ef", 128))},
    }

    expr = f'user_id == "{user_id}"'
    if category_filter:
        expr += f' and category == "{category_filter}"'

    results = col.search(
        data=[query_vector],
        anns_field="fact_vector",
        param=search_params,
        limit=top_k,
        expr=expr,
        output_fields=["fact_id", "user_id", "category", "importance", "created_at_ms"],
    )

    hits: list[dict[str, Any]] = []
    for hit in results[0]:
        if hit.distance >= score_threshold:
            hits.append({
                "fact_id": hit.entity.get("fact_id"),
                "user_id": hit.entity.get("user_id"),
                "category": hit.entity.get("category"),
                "importance": hit.entity.get("importance"),
                "created_at_ms": hit.entity.get("created_at_ms"),
                "score": float(hit.distance),
            })
    return hits


def delete_facts_by_user(user_id: int) -> int:
    """Delete all vectors for a user. Returns delete count."""
    col = get_collection()
    expr = f'user_id == "{user_id}"'
    result = col.delete(expr)
    col.flush()
    return result.delete_count if hasattr(result, 'delete_count') else 0


def delete_fact_by_id(fact_id: str) -> None:
    col = get_collection()
    col.delete(f'fact_id == "{fact_id}"')
    col.flush()


def count_vectors() -> int:
    col = get_collection()
    col.flush()
    return col.num_entities
