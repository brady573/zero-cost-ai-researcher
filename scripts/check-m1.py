#!/usr/bin/env python3
"""Static checks for the M1 on-device validation path."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    ROOT / "app/src/main/java/dev/zerocost/researcher/performance/DeviceTelemetry.kt",
    ROOT / "app/src/main/java/dev/zerocost/researcher/performance/ModelTelemetry.kt",
    ROOT / "app/src/main/java/dev/zerocost/researcher/performance/M1Validation.kt",
    ROOT / "app/src/llama/java/dev/zerocost/researcher/inference/LlamaCppResearchModel.kt",
]

problems = []
for path in required_files:
    if not path.is_file():
        problems.append(f"Missing {path.relative_to(ROOT)}")

device = required_files[0].read_text()
for needle in [
    "processPssKb",
    "processRssKb",
    "currentThermalStatus",
    "BATTERY_PROPERTY_CAPACITY",
    "BATTERY_PROPERTY_CHARGE_COUNTER",
    "systemLowMemory",
]:
    if needle not in device:
        problems.append(f"Device telemetry missing {needle}")

m1 = required_files[2].read_text()
for needle in [
    "ACCEPTANCE_DURATION_MS = 10L * 60 * 1000",
    "structuredSuccessRate",
    "severeOrWorseFraction",
    "deviceSamples",
    "nativeBenchmark",
]:
    if needle not in m1:
        problems.append(f"M1 report missing {needle}")

llama = required_files[3].read_text()
for needle in [
    "streamEmissions++",
    "ModelCallMetric",
    "SystemClock.elapsedRealtime",
    "engine.bench",
]:
    if needle not in llama:
        problems.append(f"llama telemetry missing {needle}")

if problems:
    print("M1 static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("M1 static check passed.")
