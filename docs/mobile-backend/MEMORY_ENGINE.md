# Claude Code-Style Memory Engine

Logy uses a memory and preference taxonomy inspired by Claude Code, implementing a two-tier persistence system with automatic deduplication, tier-based ranking, and budget-enforced summarization.

## Four-Type Taxonomy

1. **USER (`user`)**: Who the user is (name, role, persistent habits, general workspace settings).
2. **FEEDBACK (`feedback`)**: Stated corrections, likes, dislikes, or explicit guidance provided during sessions.
3. **PROJECT (`project`)**: Project-specific parameters, architecture decisions, coding styles, or session-level context.
4. **REFERENCE (`reference`)**: Reference material, documentation snippets, API templates, or usage patterns.

## Memory Processing Pipeline

Before injecting preferences into the model's context workspace, memories go through the following strict sequential pipeline:

```
Raw Memory Entries -> Deduplicate -> Categorize (Tiers) -> Rank -> Summarize -> Inject Prompt
```

1. **Deduplicate**: Groups memories by exact normalized titles and retains only the entry with the highest relevance score.
2. **Categorize (Tiers)**:
   - **CRITICAL**: Contains explicit "always", "never", "must", or "do not" rules.
   - **IMPORTANT**: Stated likes, preferred conventions, or records with usage counts >= 3.
   - **CONTEXTUAL**: General context or situational attributes.
3. **Rank**: Sorted primarily by priority tier, then relevance score, and finally usage frequency.
4. **Summarize**: Truncates and formats preferences to fit securely within a token/character budget limit (capped at ~500 characters).
