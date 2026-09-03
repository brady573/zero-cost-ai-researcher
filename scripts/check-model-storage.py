#!/usr/bin/env python3
"""Static checks for safe private GGUF model import/replacement."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

storage = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/storage/ModelStorage.kt"
)
if not storage.is_file():
    problems.append("ModelStorage.kt is missing.")
else:
    text = storage.read_text()
    for needle in [
        "DigestOutputStream",
        "validateGguf",
        "GGUF_MAGIC",
        "sha256.take(FILE_HASH_CHARS)",
        "renameTo(finalFile)",
        "deletePreviousPrivateModel",
    ]:
        if needle not in text:
            problems.append(f"Model import invariant missing: {needle}")

view_model = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/ui/ResearchViewModel.kt"
).read_text()
if "container.modelStorage.importGguf" not in view_model:
    problems.append("UI still bypasses ModelStorage for model import.")

if problems:
    print("Model-storage static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Model-storage static check passed.")
