#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/third_party/llama.cpp"
TAG="b10516"

if [[ -d "$TARGET/.git" ]]; then
    echo "llama.cpp already present at $TARGET"
else
    mkdir -p "$(dirname "$TARGET")"
    git clone --depth 1 --branch "$TAG" \
        https://github.com/ggml-org/llama.cpp.git \
        "$TARGET"
fi

AI_CHAT="$TARGET/examples/llama.android/lib/src/main/cpp/ai_chat.cpp"

python3 -c '
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

patched, count = re.subn(
    r"(DEFAULT_CONTEXT_SIZE\s*=\s*)8192(\s*;)",
    r"\g<1>4096\g<2>",
    text,
    count=1,
)

if count == 1:
    path.write_text(patched, encoding="utf-8")
elif not re.search(
    r"DEFAULT_CONTEXT_SIZE\s*=\s*4096\s*;",
    text,
):
    raise SystemExit(
        "Could not patch DEFAULT_CONTEXT_SIZE"
    )

print("llama.cpp Android context size set to 4096.")
' "$AI_CHAT"

grep -n "DEFAULT_CONTEXT_SIZE" "$AI_CHAT" | head -n 1

echo "llama.cpp Android binding ready."
