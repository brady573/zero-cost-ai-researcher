#!/usr/bin/env python3
"""Regression check for the pinned llama.cpp Android context patch."""

import re
import sys

upstream = "constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;"
expected = "constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;"

pattern = re.compile(
    r"(constexpr\s+int\s+DEFAULT_CONTEXT_SIZE\s*=\s*)8192(\s*;)"
)
patched, count = pattern.subn(r"\g<1>4096\g<2>", upstream, count=1)

if count != 1 or patched != expected:
    print("llama.cpp bootstrap patch regression check FAILED.")
    sys.exit(1)

print("llama.cpp bootstrap patch regression check passed.")
