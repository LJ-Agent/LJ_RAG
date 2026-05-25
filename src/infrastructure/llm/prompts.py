"""Prompt templates for memory distillation, classification, and summarization."""

DISTILL_PROMPT = """You are a memory extraction system. Extract atomic, self-contained facts from the conversation below.

Rules:
- Each fact must be a single, self-contained sentence that stands alone without context.
- Capture explicit statements AND implicit information (preferences, plans, relationships, knowledge).
- Do NOT repeat facts already stated in the conversation history — only extract new or updated facts.
- Categorize each fact as one of: factual, preference, plan, knowledge, relationship.
- Rate importance from 0.0 (trivial small talk) to 1.0 (critical personal info, long-term plan, key preference).
- Generate 1-3 tags per fact for search indexing.
- Skip greeting-only turns, pure acknowledgments ("ok", "thanks"), and purely functional assistant messages.

Return a JSON object with a "facts" array:
{
  "facts": [
    {
      "content": "<one self-contained sentence>",
      "category": "factual|preference|plan|knowledge|relationship",
      "importance": 0.0-1.0,
      "tags": ["tag1", "tag2"]
    }
  ]
}

CONVERSATION:
{messages}

Return JSON only, no other text."""


CLASSIFY_PROMPT = """Analyze the following fact extracted from a conversation and classify its importance.

Fact: {fact}

Evaluate:
1. Is this trivial small talk (greetings, pleasantries) → importance 0.0-0.2
2. Is this a casual remark or observation → importance 0.2-0.5
3. Is this a stated preference, near-term plan, or useful knowledge → importance 0.5-0.7
4. Is this a strong personal preference, long-term plan, or critical knowledge → importance 0.7-0.9
5. Is this identity-defining, safety-critical, or legally relevant → importance 0.9-1.0

Also classify into: factual, preference, plan, knowledge, relationship.

Return JSON:
{
  "importance": 0.0-1.0,
  "category": "factual|preference|plan|knowledge|relationship",
  "reasoning": "<one short sentence why>"
}

Return JSON only."""


SUMMARIZE_PROMPT = """Summarize the following set of conversation facts into a concise episodic paragraph.

User ID: {user_id}
Session ID: {session_id}
Time period: {period}
Facts collected: {fact_count}

Facts:
{facts_list}

Write a 3-5 sentence paragraph summarizing what the user discussed, decided, and revealed in this period.
Focus on the most important and actionable information.

Return JSON:
{
  "summary": "<3-5 sentence paragraph>",
  "key_fact_ids": ["fact_id_1", "fact_id_2"],
  "period": "session|hourly|daily|weekly"
}

Return JSON only."""
