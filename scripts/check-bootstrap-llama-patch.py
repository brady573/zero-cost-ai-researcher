#!/usr/bin/env python3
"""Execute the bootstrap context patch against pinned-upstream formatting."""

from pathlib import Path
import re
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
bootstrap = (root / "scripts/bootstrap-llama.sh").read_text(encoding="utf-8")

marker = "python3 - \"$AI_CHAT\" <<'PY'\n"
if marker not in bootstrap or "\nPY\n" not in bootstrap:
    raise SystemExit("Could not locate bootstrap Python heredoc.")

body = bootstrap.split(marker, 1)[1].split("\nPY\n", 1)[0]
upstream_line = "constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;\n"

with tempfile.TemporaryDirectory() as directory:
    target = Path(directory) / "ai_chat.cpp"
    target.write_text(upstream_line, encoding="utf-8")

    completed = subprocess.run(
        [sys.executable, "-", str(target)],
        input=body,
        text=True,
        capture_output=True,
        check=False,
    )

    if completed.returncode != 0:
        print(completed.stdout)
        print(completed.stderr)
        raise SystemExit("Bootstrap Python patch failed to execute.")

    patched = target.read_text(encoding="utf-8")
    if "DEFAULT_CONTEXT_SIZE    = 4096;" not in patched:
        raise SystemExit(f"Unexpected patched content: {patched!r}")

print("llama.cpp bootstrap patch execution check passed.")
