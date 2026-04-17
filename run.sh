#!/bin/bash
# =============================================================================
# Brent Oil Monitor - 快速运行脚本
# 自动查找 JavaFX 模块路径并启动应用
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/brent-monitor"
JAR_FILE="$PROJECT_DIR/target/brent-monitor.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR 文件不存在，先执行构建..."
    ./build.sh
fi

# 检测 CPU 架构
ARCH=$(uname -m)
echo "检测到 CPU 架构: $ARCH"

# 获取 Maven 本地仓库路径
REPO=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2>/dev/null)
JAVAFX_VERSION="17.0.13"

if [ "$ARCH" = "arm64" ]; then
    # Apple Silicon M1/M2/M3
    SUFFIX="-mac-aarch64.jar"
    echo "使用 Apple Silicon JavaFX 模块"
else
    # Intel Mac
    SUFFIX="-mac.jar"
    echo "使用 Intel Mac JavaFX 模块"
fi

MODULE_PATH="$REPO/org/openjfx/javafx-controls/$JAVAFX_VERSION/javafx-controls-${JAVAFX_VERSION}${SUFFIX}"
MODULE_PATH="$MODULE_PATH:$REPO/org/openjfx/javafx-graphics/$JAVAFX_VERSION/javafx-graphics-${JAVAFX_VERSION}${SUFFIX}"
MODULE_PATH="$MODULE_PATH:$REPO/org/openjfx/javafx-base/$JAVAFX_VERSION/javafx-base-${JAVAFX_VERSION}${SUFFIX}"

echo "启动 Brent Oil Monitor..."
java \
    --module-path "$MODULE_PATH" \
    --add-modules javafx.controls,javafx.graphics \
    -jar "$JAR_FILE"
