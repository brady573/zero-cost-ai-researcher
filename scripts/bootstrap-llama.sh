#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/third_party/llama.cpp"
TAG="b10516"

if [[ -d "$TARGET/.git" ]]; then
  echo "llama.cpp already present at $TARGET"
else
  mkdir -p "$(dirname "$TARGET")"
  git clone --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp.git "$TARGET"
fi

AI_CHAT="$TARGET/examples/llama.android/lib/src/main/cpp/ai_chat.cpp"
python3 - "$AI_CHAT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = "constexpr int DEFAULT_CONTEXT_SIZE = 8192;"
new = "constexpr int DEFAULT_CONTEXT_SIZE = 4096;"
if old in text:
    path.write_text(text.replace(old, new, 1))
elif new not in text:
    raise SystemExit("Could not patch context size; upstream Android binding changed.")
print("llama.cpp Android context size set to 4096.")
PY

echo "Bootstrap complete."
echo "Build real inference flavor with: ./gradlew :app:assembleLlamaDebug"
