#!/usr/bin/env python3
"""Static checks for the phone-only development workflow."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

workflow = ROOT / ".github/workflows/android-build.yml"
termux = ROOT / "scripts/termux-build-llama.sh"
docs = ROOT / "PHONE_DEVELOPMENT.md"

for path in (workflow, termux, docs):
    if not path.is_file():
        problems.append(f"Missing {path.relative_to(ROOT)}")

if workflow.is_file():
    text = workflow.read_text()
    for needle in [
        'ANDROID_NDK_VERSION: "29.0.13113456"',
        'ANDROID_CMAKE_VERSION: "3.31.6"',
        'GRADLE_VERSION: "8.13"',
        "./scripts/bootstrap-llama.sh",
        ":app:assembleLlamaDebug",
        "actions/upload-artifact@v4",
    ]:
        if needle not in text:
            problems.append(f"Phone build workflow missing {needle}")

if termux.is_file():
    text = termux.read_text()
    for needle in [
        'LLAMA_TAG="${LLAMA_TAG:-b10516}"',
        "pkg install -y git cmake ninja clang make python",
        "-DGGML_OPENMP=OFF",
        "--target llama-simple-chat",
    ]:
        if needle not in text:
            problems.append(f"Termux llama build missing {needle}")

if problems:
    print("Phone-development static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Phone-development static check passed.")
