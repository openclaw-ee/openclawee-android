#!/usr/bin/env bash
#
# setup-aapt2-arm64.sh
#
# On ARM64 Linux hosts, the Android SDK ships x86_64 aapt2 binaries that can't
# run natively. This script sets up QEMU user-mode emulation so that the
# x86_64 aapt2 from the SDK can be used transparently.
#
# Run this once before building. It:
#   1. Downloads qemu-user-static (from the Ubuntu package)
#   2. Downloads the x86_64 glibc runtime libraries needed by aapt2
#   3. Creates a wrapper script at /tmp/aapt2
#   4. Sets the android.aapt2FromMavenOverride property in gradle.properties
#
# Prerequisites: curl, dpkg-deb, jar (JDK)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/pragone/android-sdk}}"

STAGING_DIR="/tmp/openclaw-arm64-build"
QEMU_DIR="$STAGING_DIR/qemu"
SYSROOT="$STAGING_DIR/x86_64-sysroot"
AAPT2_WRAPPER="$STAGING_DIR/aapt2"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[arm64-setup]${NC} $*"; }
warn() { echo -e "${YELLOW}[arm64-setup]${NC} $*"; }

# Only needed on ARM64
if [ "$(uname -m)" != "aarch64" ]; then
    log "Not on ARM64 — no QEMU wrapper needed."
    exit 0
fi

if [ -x "$AAPT2_WRAPPER" ]; then
    log "QEMU aapt2 wrapper already exists at $AAPT2_WRAPPER"
    # Ensure gradle.properties points to it
    grep -q "aapt2FromMavenOverride" "$PROJECT_ROOT/gradle.properties" || \
        echo "android.aapt2FromMavenOverride=$AAPT2_WRAPPER" >> "$PROJECT_ROOT/gradle.properties"
    exit 0
fi

mkdir -p "$QEMU_DIR" "$SYSROOT"

log "Downloading qemu-user-static (Ubuntu Noble arm64)..."
QEMU_DEB="$STAGING_DIR/qemu-user-static.deb"
curl -sL "http://ports.ubuntu.com/ubuntu-ports/pool/universe/q/qemu/qemu-user-static_8.2.2+ds-0ubuntu1.16_arm64.deb" \
    -o "$QEMU_DEB"
dpkg-deb -x "$QEMU_DEB" "$QEMU_DIR/"
QEMU_BIN="$QEMU_DIR/usr/bin/qemu-x86_64-static"
log "  ✓ qemu-x86_64-static"

log "Downloading x86_64 runtime libraries..."
declare -A LIBS=(
    ["libc6"]="http://archive.ubuntu.com/ubuntu/pool/main/g/glibc/libc6_2.39-0ubuntu8.7_amd64.deb"
    ["libgcc-s1"]="http://archive.ubuntu.com/ubuntu/pool/main/g/gcc-14/libgcc-s1_14.2.0-4ubuntu2~24.04.1_amd64.deb"
    ["libstdc++6"]="http://archive.ubuntu.com/ubuntu/pool/main/g/gcc-14/libstdc++6_14.2.0-4ubuntu2~24.04.1_amd64.deb"
)
for lib in "${!LIBS[@]}"; do
    deb="$STAGING_DIR/${lib}.deb"
    curl -sL "${LIBS[$lib]}" -o "$deb"
    dpkg-deb -x "$deb" "$SYSROOT/"
    log "  ✓ $lib (amd64)"
done

log "Finding aapt2 in SDK build-tools..."
AAPT2_BIN=$(find "$ANDROID_SDK/build-tools" -name "aapt2" -type f | sort -V | tail -1)
if [ -z "$AAPT2_BIN" ]; then
    echo "ERROR: aapt2 not found in $ANDROID_SDK/build-tools"
    exit 1
fi
log "  Using: $AAPT2_BIN"

log "Creating aapt2 wrapper script..."
cat > "$AAPT2_WRAPPER" << EOF
#!/bin/sh
exec "$QEMU_BIN" -L "$SYSROOT" "$AAPT2_BIN" "\$@"
EOF
chmod +x "$AAPT2_WRAPPER"

# Test it
if "$AAPT2_WRAPPER" version 2>/dev/null | grep -q "Android Asset Packaging"; then
    log "  ✓ aapt2 wrapper works!"
else
    echo "ERROR: aapt2 wrapper test failed"
    exit 1
fi

log "Updating gradle.properties..."
if grep -q "aapt2FromMavenOverride" "$PROJECT_ROOT/gradle.properties"; then
    sed -i "s|android.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=$AAPT2_WRAPPER|" \
        "$PROJECT_ROOT/gradle.properties"
else
    echo "android.aapt2FromMavenOverride=$AAPT2_WRAPPER" >> "$PROJECT_ROOT/gradle.properties"
fi

log ""
log "============================================"
log " ARM64 build setup complete!"
log " aapt2 wrapper: $AAPT2_WRAPPER"
log "============================================"
log ""
log "You can now run: ./gradlew assembleDebug"
