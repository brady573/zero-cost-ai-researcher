#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

LLAMA_TAG="${LLAMA_TAG:-b10516}"
LLAMA_DIR="${LLAMA_DIR:-$HOME/zero-cost-ai-researcher/third_party/llama.cpp}"
BUILD_DIR="$LLAMA_DIR/build-termux"

if ! command -v pkg >/dev/null 2>&1; then
  echo "This script is intended for Termux." >&2
  exit 1
fi

pkg update -y
pkg install -y git cmake ninja clang make python

if [[ ! -d "$LLAMA_DIR/.git" ]]; then
  mkdir -p "$(dirname "$LLAMA_DIR")"
  git clone --depth 1 --branch "$LLAMA_TAG" \
    https://github.com/ggml-org/llama.cpp.git \
    "$LLAMA_DIR"
fi

git -C "$LLAMA_DIR" fetch --depth 1 origin "refs/tags/$LLAMA_TAG:refs/tags/$LLAMA_TAG"
git -C "$LLAMA_DIR" checkout --detach "$LLAMA_TAG"

cmake \
  -S "$LLAMA_DIR" \
  -B "$BUILD_DIR" \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_SERVER=OFF

cmake --build "$BUILD_DIR" --target llama-simple-chat -j "$(nproc)"

echo
echo "llama.cpp CLI built successfully:"
echo "$BUILD_DIR/bin/llama-simple-chat"
echo
echo "Example:"
echo "$BUILD_DIR/bin/llama-simple-chat -m ~/model.gguf -c 4096"
