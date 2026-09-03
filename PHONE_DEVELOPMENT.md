# Phone-only development

This project can be edited and tested from an Android phone.

## Recommended split

```text
Galaxy S25+
  ├─ Termux
  │   ├─ edit/git
  │   ├─ run static checks
  │   └─ build/test llama.cpp CLI natively
  │
  └─ GitHub web/app
      └─ trigger .github/workflows/android-build.yml
          └─ produces installable llamaDebug APK
```

The application itself still has no required backend or cloud inference. GitHub Actions is only
an optional development/build machine.

## Why not AndroidIDE for this project?

The project uses Kotlin, Compose, Gradle, CMake, and the llama.cpp Android NDK module. AndroidIDE's
published limitation says it has no official NDK support. That makes it unsuitable as the
primary full-build environment for this repository.

AIDE advertises Android NDK support, but this repository is a modern Kotlin/Compose/AGP project,
so the supported path in this repository is Termux for source/native work and the included
GitHub Actions workflow for full APK compilation.

## 1. Install Termux

Use one Termux distribution only. Do not mix app/plugins from different signing sources.

After opening Termux:

```bash
termux-setup-storage
pkg update -y
pkg upgrade -y
pkg install -y git gh unzip zip python cmake ninja clang make openssh
```

Keep the working repository under `$HOME`, not `/sdcard` or shared Downloads storage.

## 2. Extract this project

Assuming the ZIP is in Android Downloads:

```bash
mkdir -p ~/zero-cost-ai-researcher
cd ~/zero-cost-ai-researcher
unzip ~/storage/downloads/zero-cost-ai-researcher-mvp.zip
```

The archive contains a top-level `zero-cost-ai-researcher/` directory. If so:

```bash
cd ~/zero-cost-ai-researcher/zero-cost-ai-researcher
```

For a simpler final location:

```bash
cd ~
mv ~/zero-cost-ai-researcher/zero-cost-ai-researcher ~/zcai
rm -rf ~/zero-cost-ai-researcher
cd ~/zcai
```

## 3. Run repository checks directly on the phone

```bash
python scripts/check-zero-cost.py
python scripts/check-m8.py
python scripts/check-m1.py
python scripts/check-citation-audit.py
python scripts/check-resilience.py
python scripts/check-context-budget.py
python scripts/check-source-provenance.py
python scripts/check-model-storage.py
python scripts/check-independent-verification.py
python scripts/check-mutable-plan.py
python scripts/check-network-cancellation.py
```

These checks do not require Android Studio.

## 4. Build llama.cpp natively in Termux

The repository contains:

```bash
bash scripts/termux-build-llama.sh
```

That installs the required Termux packages, checks out pinned llama.cpp `b10516`, and builds
`llama-simple-chat` directly for the phone.

The result is:

```text
third_party/llama.cpp/build-termux/bin/llama-simple-chat
```

Test a GGUF model, then type your prompt at the green `>` prompt:

```bash
third_party/llama.cpp/build-termux/bin/llama-simple-chat \
  -m ~/model.gguf \
  -c 4096 \
  -n 128 \
  -p "Explain why citations should entail a factual claim."
```

Keep large GGUF files in Termux `$HOME` for native CLI testing. The Android app later imports its
own private copy through the system file picker.

## 5. Put the project on GitHub from the phone

Create an empty GitHub repository in the browser, then in Termux:

```bash
cd ~/zcai
git init
git add .
git commit -m "Initial zero-cost AI researcher"
git branch -M main
```

Authenticate:

```bash
gh auth login
```

Then either create/push directly:

```bash
gh repo create zero-cost-ai-researcher \
  --public \
  --source=. \
  --remote=origin \
  --push
```

or point `origin` at a repository you already created.

Do not commit GGUF model files or API keys.

## 6. Build the APK from the phone

The repository contains:

```text
.github/workflows/android-build.yml
```

In the GitHub website on the phone:

```text
repository
-> Actions
-> Build Android APK
-> Run workflow
-> Run workflow
```

The workflow:

```text
checks out source
-> installs JDK 17
-> installs Android SDK 36
-> installs NDK 29.0.13113456
-> installs CMake 3.31.6
-> installs Gradle 8.13
-> bootstraps pinned llama.cpp b10516
-> runs all static release checks
-> builds llamaDebug
-> runs JVM unit tests
-> uploads the APK artifact
```

After the run finishes:

```text
Actions
-> completed Build Android APK run
-> Artifacts
-> zero-cost-ai-researcher-llama-debug
```

Download the artifact ZIP, extract the APK, tap it, and allow installation from the browser/file
manager if Android asks.

## 7. Install a model in the app

Download a Qwen3-4B Q4_K_M GGUF file using the browser.

Open:

```text
Zero Cost AI Researcher
-> Local settings
-> Import GGUF model
```

Select the downloaded `.gguf`.

The app copies it into private storage, validates the GGUF header, and hashes it with SHA-256.

## 8. First validation

Run:

```text
M1 on-device validation
Duration: 1 minute
Run M1
```

Do not start the 10-minute M1 acceptance run or the full 64-question M8 benchmark until the
1-minute result is healthy.

## Development loop entirely from the phone

```text
edit in Termux/editor
-> run static checks
-> git commit
-> git push
-> GitHub Actions builds APK
-> download APK artifact
-> install APK
-> test on the same Galaxy S25+
```

This avoids requiring a desktop Android Studio installation.
