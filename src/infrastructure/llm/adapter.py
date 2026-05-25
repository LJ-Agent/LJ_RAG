"""LLM adapter — OpenAI-compatible API for embedding and chat completion.

Supports both sync usage and async invocation via run_in_executor for gRPC contexts.
"""
import asyncio
import json
import time
from typing import Any

import httpx
from loguru import logger
from openai import OpenAI

from common.config_loader import get_config
from common.exceptions import DistillationException, StorageException


_client: OpenAI | None = None


def _get_cfg() -> dict[str, Any]:
    return get_config()["llm"]


def get_client() -> OpenAI:
    global _client
    if _client is not None:
        return _client
    c = _get_cfg()
    _client = OpenAI(
        api_key=c["api_key"],
        base_url=c["base_url"] or None,
        timeout=c.get("timeout", 60),
    )
    logger.info(f"LLM client initialized: base_url={c.get('base_url', 'default')}")
    return _client


# ==================== Embedding ====================

def embed(text: str) -> list[float]:
    """Generate embedding vector for a single text."""
    c = _get_cfg()
    client = get_client()
    resp = client.embeddings.create(
        model=c.get("embedding_model", "text-embedding-3-small"),
        input=text,
    )
    return resp.data[0].embedding


def embed_batch(texts: list[str]) -> list[list[float]]:
    """Generate embedding vectors for multiple texts."""
    c = _get_cfg()
    client = get_client()
    resp = client.embeddings.create(
        model=c.get("embedding_model", "text-embedding-3-small"),
        input=texts,
    )
    return [d.embedding for d in resp.data]


async def embed_async(text: str) -> list[float]:
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(None, embed, text)


# ==================== Chat Completion ====================

def chat(
    messages: list[dict[str, str]],
    temperature: float = 0.2,
    max_tokens: int = 2048,
    response_format: str | None = None,
) -> str:
    """Synchronous chat completion. Returns content string."""
    c = _get_cfg()
    client = get_client()

    kwargs: dict[str, Any] = {
        "model": c.get("chat_model", "gpt-4o-mini"),
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    if response_format == "json_object":
        kwargs["response_format"] = {"type": "json_object"}

    resp = client.chat.completions.create(**kwargs)
    return resp.choices[0].message.content or ""


async def chat_async(
    messages: list[dict[str, str]],
    temperature: float = 0.2,
    max_tokens: int = 2048,
    response_format: str | None = None,
) -> str:
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(
        None, lambda: chat(messages, temperature, max_tokens, response_format)
    )


def chat_structured(
    messages: list[dict[str, str]],
    temperature: float = 0.2,
    max_tokens: int = 2048,
) -> dict[str, Any]:
    """Chat completion returning parsed JSON dict."""
    raw = chat(messages, temperature, max_tokens, response_format="json_object")
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # Try to extract JSON block from the response
        start = raw.find("{")
        end = raw.rfind("}")
        if start >= 0 and end > start:
            return json.loads(raw[start:end + 1])
        raise DistillationException(f"LLM response is not valid JSON: {raw[:200]}")


# ==================== Health Check ====================

def health_check() -> bool:
    """Check whether the LLM API is reachable."""
    try:
        client = get_client()
        client.models.list()
        return True
    except Exception as e:
        logger.warning(f"LLM health check failed: {e}")
        return False
