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
import re
import sys

path = Path(sys.argv[1])
text = path.read_text()
pattern = re.compile(
    r"(constexpr\s+int\s+DEFAULT_CONTEXT_SIZE\s*=\s*)8192(\s*;)"
)
patched, count = pattern.subn(r"\g<1>4096\g<2>", text, count=1)

if count == 1:
    path.write_text(patched)
elif not re.search(
    r"constexpr\s+int\s+DEFAULT_CONTEXT_SIZE\s*=\s*4096\s*;",
    text,
):
    raise SystemExit("Could not patch context size; upstream Android binding changed.")

print("llama.cpp Android context size set to 4096.")
PY

echo "Bootstrap complete."
echo "llama.cpp Android binding is ready for the llamaDebug Gradle build."
