#!/usr/bin/env bash
# Idempotent setup for running toon3D in a headless cloud container. See SKILL.md.
#   1. a desktop-only "mirror" Gradle build (the repo root build cannot resolve the Android Gradle plugin here)
#   2. the dev-only capture driver module inside that mirror
#   3. a virtual display (Xvfb :77) with a window manager (openbox; without one GLFW never renders/updates)
# Safe to re-run; it only (re)creates what is missing. Never writes inside the repo except via the symlinks.
set -euo pipefail

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SKILL_DIR="$REPO_ROOT/.claude/skills/run-game"
RUNTIME_DIR="${TOON3D_RUNTIME_DIR:-/tmp/toon3d-runtime}"
MIRROR="$RUNTIME_DIR/mirror"
MAVEN_MIRROR='maven { url = "https://maven-central.storage-download.googleapis.com/maven2/" }'
export DISPLAY="${DISPLAY_NUMBER:-:77}"

mkdir -p "$MIRROR"
for entry in core lwjgl3 assets gradle gradlew gradle.properties docs story; do
  ln -sfn "$REPO_ROOT/$entry" "$MIRROR/$entry"
done

# settings.gradle: desktop modules + capture; plugin repo through the googleapis Maven Central mirror
# (repo.maven.apache.org answers 429 from this container).
cat > "$MIRROR/settings.gradle" <<EOF
pluginManagement { repositories { $MAVEN_MIRROR
  gradlePluginPortal() } }
include 'lwjgl3', 'core', 'capture'
EOF

# build.gradle: the repo's, minus the buildscript{} block (Android Gradle plugin), configuring every subproject,
# with the Maven mirror first in the subprojects repositories.
sed -e '/^buildscript {/,/^}/d' \
    -e "s/configure(subprojects - project(':android'))/configure(subprojects)/" \
    -e "s#^  repositories {\$#  repositories {\n    $MAVEN_MIRROR#" \
    "$REPO_ROOT/build.gradle" > "$MIRROR/build.gradle"

# Capture module: COPIED (not symlinked) so Gradle's build/ output never lands inside the repo.
rm -rf "$MIRROR/capture"
mkdir -p "$MIRROR/capture"
cp -r "$SKILL_DIR/capture/." "$MIRROR/capture/"

# Virtual display + window manager.
if ! command -v openbox >/dev/null 2>&1; then
  apt-get update -q >/dev/null 2>&1 || true
  apt-get install -y -q openbox >/dev/null 2>&1
fi
if ! pgrep -x Xvfb >/dev/null; then
  (Xvfb "$DISPLAY" -screen 0 1280x720x24 >/dev/null 2>&1 &)
  sleep 2
fi
if ! pgrep -x openbox >/dev/null; then
  (nohup openbox >/dev/null 2>&1 &)
  sleep 1
fi

echo "toon3d runtime ready: mirror=$MIRROR display=$DISPLAY"
echo "build/test:  cd $MIRROR && ./gradlew -q :core:build :lwjgl3:compileJava"
echo "capture:     $SKILL_DIR/run.sh --out <dir> '<script>'"
