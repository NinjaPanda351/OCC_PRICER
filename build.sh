#!/usr/bin/env bash
# build.sh — One-click build for macOS: compile → jar → jpackage → dmg
# Usage: ./build.sh [version]
#   e.g. ./build.sh 4.0.4
set -euo pipefail

VERSION="${1:-2.0.7}"
JPACKAGE_VERSION="${VERSION%%-*}"   # strip pre-release suffix (e.g. "4.0.4-1" → "4.0.4")

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src"
LIB_DIR="$PROJECT_DIR/lib"
ASSETS_DIR="$PROJECT_DIR/assets"
OUT_DIR="$PROJECT_DIR/out/production/OCC_Trade_Pricer"
DIST_DIR="$PROJECT_DIR/dist"
JAR_NAME="OCC_Trade_Pricer.jar"
JAR_PATH="$PROJECT_DIR/$JAR_NAME"
APP_NAME="OCC Card Pricer"
MAIN_CLASS="com.cardpricer.gui.MainSwingApplication"
MANIFEST_SRC="$SRC_DIR/META-INF/MANIFEST.MF"
ICON_PNG="$ASSETS_DIR/OCC_Icon_400x400.png"
ICON_ICNS="$PROJECT_DIR/out/OCC_Icon.icns"

echo "=== OCC Card Pricer Build Script (macOS) ==="
echo "Version : $VERSION"
echo "Project : $PROJECT_DIR"

# ── Step 1: Compile ───────────────────────────────────────────────────────────
echo ""
echo "[1/5] Compiling sources..."
mkdir -p "$OUT_DIR"

# Collect all .java source files
SOURCES=$(find "$SRC_DIR" -name "*.java" | tr '\n' ' ')

# Build classpath from lib/ (colon-separated on macOS)
CP=$(find "$LIB_DIR" -name "*.jar" | tr '\n' ':')
CP="${CP%:}"  # strip trailing colon

javac -encoding UTF-8 -cp "$CP" -d "$OUT_DIR" $SOURCES
echo "  Compiled $(find "$SRC_DIR" -name "*.java" | wc -l | tr -d ' ') source files."

# ── Step 2: Copy resources (non-.java files under src/) ───────────────────────
echo ""
echo "[2/5] Copying resources..."
find "$SRC_DIR" -type f ! -name "*.java" | while read -r f; do
    rel="${f#$SRC_DIR/}"
    dest="$OUT_DIR/$rel"
    mkdir -p "$(dirname "$dest")"
    cp "$f" "$dest"
done

# ── Step 3: Package as fat JAR ────────────────────────────────────────────────
echo ""
echo "[3/5] Creating fat JAR..."

TMP_EXTRACT="$PROJECT_DIR/out/extracted_deps"
rm -rf "$TMP_EXTRACT"
mkdir -p "$TMP_EXTRACT"

# Extract all dependency JARs
find "$LIB_DIR" -name "*.jar" | while read -r jar; do
    unzip -qo "$jar" -d "$TMP_EXTRACT"
done

# Merge extracted deps, then overlay compiled classes (our classes win)
cp -r "$TMP_EXTRACT/." "$OUT_DIR/"

# Build the fat JAR
rm -f "$JAR_PATH"
(cd "$OUT_DIR" && jar cfm "$JAR_PATH" "$MANIFEST_SRC" .)
echo "  Created: $JAR_PATH"

# ── Step 4: Convert icon PNG → icns ───────────────────────────────────────────
echo ""
echo "[4/5] Converting icon..."
mkdir -p "$(dirname "$ICON_ICNS")"

ICONSET="$PROJECT_DIR/out/OCC_Icon.iconset"
rm -rf "$ICONSET"
mkdir -p "$ICONSET"

sips -z 16   16   "$ICON_PNG" --out "$ICONSET/icon_16x16.png"    > /dev/null
sips -z 32   32   "$ICON_PNG" --out "$ICONSET/icon_16x16@2x.png" > /dev/null
sips -z 32   32   "$ICON_PNG" --out "$ICONSET/icon_32x32.png"    > /dev/null
sips -z 64   64   "$ICON_PNG" --out "$ICONSET/icon_32x32@2x.png" > /dev/null
sips -z 128  128  "$ICON_PNG" --out "$ICONSET/icon_128x128.png"  > /dev/null
sips -z 256  256  "$ICON_PNG" --out "$ICONSET/icon_128x128@2x.png" > /dev/null
sips -z 256  256  "$ICON_PNG" --out "$ICONSET/icon_256x256.png"  > /dev/null
sips -z 400  400  "$ICON_PNG" --out "$ICONSET/icon_256x256@2x.png" > /dev/null
sips -z 400  400  "$ICON_PNG" --out "$ICONSET/icon_512x512.png"  > /dev/null

iconutil -c icns "$ICONSET" -o "$ICON_ICNS"
echo "  Icon: $ICON_ICNS"

# ── Step 5: jpackage (macOS dmg) ──────────────────────────────────────────────
echo ""
echo "[5/5] Running jpackage..."

APP_IMAGE_DIR="$DIST_DIR/$APP_NAME"
rm -rf "$APP_IMAGE_DIR"
mkdir -p "$DIST_DIR"

jpackage \
    --type dmg \
    --name "$APP_NAME" \
    --app-version "$JPACKAGE_VERSION" \
    --input "$PROJECT_DIR" \
    --main-jar "$JAR_NAME" \
    --main-class "$MAIN_CLASS" \
    --dest "$DIST_DIR" \
    --icon "$ICON_ICNS" \
    --java-options "-Xmx512m"

DMG_PATH="$DIST_DIR/${APP_NAME}-${JPACKAGE_VERSION}.dmg"
echo ""
echo "Done! Release: $DMG_PATH"
