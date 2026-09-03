#!/usr/bin/env python3
"""Fail before publishing if large models/secrets/build products could be committed."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ignore = (ROOT / ".gitignore").read_text(encoding="utf-8").splitlines()
required = {"third_party/llama.cpp/", "*.gguf", "*.apk", ".env", "*.keystore", "*.jks"}
missing = sorted(required - set(ignore))

workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
workflow_requirements = [
    "assembleLlamaDebug",
    "testLlamaDebugUnitTest",
    "check-thinking-suppression.py",
    "check-phone-development.py",
    "upload-artifact@v4",
]
missing_workflow = [item for item in workflow_requirements if item not in workflow]

if missing or missing_workflow:
    print("Phone pre-push check FAILED.")
    for item in missing:
        print(f"- .gitignore missing: {item}")
    for item in missing_workflow:
        print(f"- workflow missing: {item}")
    sys.exit(1)

print("Phone pre-push check passed.")
