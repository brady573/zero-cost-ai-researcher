#!/usr/bin/env python3
"""Static guardrail checks for accidental billable/cloud inference paths."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
THIS_FILE = Path(__file__).resolve()
TEXT_EXTENSIONS = {".kt", ".kts", ".toml", ".xml", ".md", ".py", ".sh"}
FORBIDDEN = {
    "api." + "openai.com": "OpenAI cloud inference endpoint",
    "api." + "anthropic.com": "Anthropic cloud inference endpoint",
    "generativelanguage." + "googleapis.com": "Google cloud inference endpoint",
    "api." + "groq.com": "Groq cloud inference endpoint",
    "api." + "mistral.ai": "Mistral cloud inference endpoint",
}

problems = []
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix not in TEXT_EXTENSIONS:
        continue
    if path.resolve() == THIS_FILE or "third_party" in path.parts:
        continue
    text = path.read_text(errors="ignore")
    for needle, description in FORBIDDEN.items():
        if needle in text:
            problems.append(f"{path.relative_to(ROOT)}: {description}")

preferences = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/config/AppPreferences.kt"
).read_text()
if "coerceIn(1, 1000)" not in preferences:
    problems.append("Tavily hard limit is not capped at 1000.")

tavily = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/search/TavilySearchProvider.kt"
).read_text()
if 'put("search_depth", "basic")' not in tavily:
    problems.append("Tavily is not locked to basic search.")
if "budgetController.reserve" not in tavily:
    problems.append("Tavily request does not reserve local provider budget.")


searx = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/search/SearXngSearchProvider.kt"
).read_text()
if "budgetController.reserve" not in searx:
    problems.append("SearXNG request does not reserve local provider budget.")
if "MONTHLY_REQUEST_LIMIT = 5_000" not in searx:
    problems.append("SearXNG courtesy hard limit is missing.")

if problems:
    print("Zero-cost guardrail check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Zero-cost guardrail check passed.")
