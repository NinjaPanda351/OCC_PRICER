#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
VERSION=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -1)
[[ -z "${1:-}" || "$1" == "$VERSION" ]] || { echo 'Release tag and pom.xml differ' >&2; exit 1; }
bash ./mvnw -B verify
RUN=$(mktemp -d "$PWD/target/package-XXXXXX")
mkdir -p "$RUN/input" "$RUN/output" "$RUN/icon.iconset" dist
cp target/OCC_Trade_Pricer.jar THIRD_PARTY_NOTICES.md "$RUN/input/"
for size in 16 32 128 256 512; do
    sips -z "$size" "$size" assets/OCC_Icon_400x400.png --out "$RUN/icon.iconset/icon_${size}x${size}.png" >/dev/null
    doubled=$((size * 2))
    sips -z "$doubled" "$doubled" assets/OCC_Icon_400x400.png --out "$RUN/icon.iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$RUN/icon.iconset" -o "$RUN/icon.icns"
"$JAVA_HOME/bin/jpackage" --type app-image --name 'OCC Card Pricer' --app-version "${VERSION%%-*}" \
    --input "$RUN/input" --main-jar OCC_Trade_Pricer.jar --main-class com.cardpricer.gui.MainSwingApplication \
    --dest "$RUN/output" --icon "$RUN/icon.icns" --java-options '-Xmx512m' --java-options '--enable-native-access=ALL-UNNAMED'
while IFS= read -r -d '' file; do
    case "$(basename "$file")" in OCC_Trade_Pricer.jar|'OCC Card Pricer.cfg'|THIRD_PARTY_NOTICES.md|.jpackage.xml) ;; *) echo "Unexpected package payload: $file" >&2; exit 1 ;; esac
done < <(find "$RUN/output/OCC Card Pricer.app/Contents/app" -type f -print0)
"$JAVA_HOME/bin/jpackage" --type dmg --name 'OCC Card Pricer' --app-version "${VERSION%%-*}" \
    --app-image "$RUN/output/OCC Card Pricer.app" --dest "$RUN/output"
cp "$RUN/output/OCC Card Pricer-${VERSION%%-*}.dmg" "dist/OCC_Card_Pricer_V${VERSION}.dmg"
