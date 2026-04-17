#!/bin/bash
# =============================================================================
# Brent Oil Monitor - macOS App 打包脚本
# 使用 jpackage（Java 17 内置）生成 .app + 签名修复
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/brent-monitor"
JAR_FILE="$PROJECT_DIR/target/brent-monitor-shaded.jar"
OUTPUT_DIR="$SCRIPT_DIR/dist"
CLEAN_DIR="/tmp/BrentMonitor-Clean"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  macOS App 打包脚本${NC}"
echo -e "${BLUE}========================================${NC}"

# 检测 Java 版本
JAVA_FULL_VERSION=$(java -version 2>&1 | head -1)
JAVA_MAJOR=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo -e "${GREEN}✓ Java: $JAVA_FULL_VERSION${NC}"

if [ "$JAVA_MAJOR" -lt 17 ]; then
    echo -e "${RED}❌ 错误：需要 Java 17+，当前版本不支持 jpackage${NC}"
    exit 1
fi

if ! command -v jpackage &> /dev/null; then
    echo -e "${RED}❌ jpackage 未找到${NC}"
    exit 1
fi
echo -e "${GREEN}✓ jpackage: $(which jpackage)${NC}"

# 检查 JAR
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}⚠ shaded JAR 不存在，先执行构建...${NC}"
    "$SCRIPT_DIR/build.sh"
fi
echo -e "${GREEN}✓ shaded JAR: $JAR_FILE${NC}"

# 创建输出目录
mkdir -p "$OUTPUT_DIR"
rm -rf "$OUTPUT_DIR/BrentMonitor.app" "$OUTPUT_DIR/BrentMonitor.dmg" 2>/dev/null || true

# ========== 构建 JavaFX 模块路径 ==========
REPO=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2>/dev/null)
JAVAFX_VERSION="17.0.13"

ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    SUFFIX="-mac-aarch64.jar"
    echo -e "${GREEN}✓ Apple Silicon 检测到${NC}"
else
    SUFFIX="-mac.jar"
    echo -e "${GREEN}✓ Intel Mac 检测到${NC}"
fi

MODULE_PATH="$REPO/org/openjfx/javafx-controls/$JAVAFX_VERSION/javafx-controls-${JAVAFX_VERSION}${SUFFIX}"
MODULE_PATH="$MODULE_PATH:$REPO/org/openjfx/javafx-graphics/$JAVAFX_VERSION/javafx-graphics-${JAVAFX_VERSION}${SUFFIX}"
MODULE_PATH="$MODULE_PATH:$REPO/org/openjfx/javafx-base/$JAVAFX_VERSION/javafx-base-${JAVAFX_VERSION}${SUFFIX}"

# ========== jpackage（会失败于 codesign，但 app 目录已创建）==========
echo -e "${YELLOW}📦 生成 macOS App...${NC}"
jpackage \
    --type app-image \
    --input "$PROJECT_DIR/target" \
    --main-jar "brent-monitor-shaded.jar" \
    --dest "$OUTPUT_DIR" \
    --name "BrentMonitor" \
    --app-version "1.0.0" \
    --vendor "BrentMonitor" \
    --description "Brent Crude Oil News Monitor - High Concurrency Demo" \
    --mac-package-identifier "com.brentmonitor.app" \
    --mac-package-name "Brent Monitor" \
    --module-path "$MODULE_PATH" \
    --add-modules "javafx.controls,javafx.graphics" \
    --java-options "-Djpackage.app-version=1.0.0" \
    2>&1 || true

APP_FILE="$OUTPUT_DIR/BrentMonitor.app"
if [ ! -d "$APP_FILE" ]; then
    echo -e "${RED}❌ App 目录不存在，打包失败${NC}"
    exit 1
fi

# ========== 签名修复（macOS 13+ 的 codesign 问题）==========
echo -e "${YELLOW}🔏 签名修复（绕过 macOS 13+ Finder 属性限制）...${NC}"

# 剥离损坏签名
codesign --remove-signature "$APP_FILE" 2>/dev/null || true
rm -rf "$APP_FILE/Contents/_CodeSignature" "$APP_FILE/Contents/runtime/Contents/_CodeSignature" 2>/dev/null || true

# 复制到干净目录（rsync 不会传递扩展属性）
rm -rf "$CLEAN_DIR" 2>/dev/null
export COPYFILE_DISABLE=1
export COPY_EXTENDED_ATTRIBUTES_DISABLE=1
rsync -a --no-links "$APP_FILE/" "$CLEAN_DIR/"

# 签名干净副本
codesign -s - --deep -vvvv "$CLEAN_DIR/" 2>/dev/null

# 复制回 dist
rm -rf "$APP_FILE"
cp -pRX "$CLEAN_DIR" "$APP_FILE"

echo -e "${GREEN}✓ 签名完成！${NC}"
echo "  路径: $APP_FILE"
echo "  大小: $(du -sh "$APP_FILE" | cut -f1)"

# ========== 生成 DMG ==========
echo -e "\n${YELLOW}🗜️  生成 DMG 安装包...${NC}"
if command -v hdiutil &> /dev/null; then
    rm -f "$OUTPUT_DIR/BrentMonitor.dmg" 2>/dev/null || true
    hdiutil create -volname "BrentMonitor" \
        -srcfolder "$APP_FILE" \
        -ov -format UDZO \
        "$OUTPUT_DIR/BrentMonitor.dmg" 2>/dev/null
    if [ -f "$OUTPUT_DIR/BrentMonitor.dmg" ]; then
        echo -e "${GREEN}✓ DMG: $OUTPUT_DIR/BrentMonitor.dmg${NC}"
        echo "  大小: $(du -h "$OUTPUT_DIR/BrentMonitor.dmg" | cut -f1)"
    fi
fi

# ========== 启动测试 ==========
echo -e "\n${YELLOW}🧪 启动测试...${NC}"
"$APP_FILE/Contents/MacOS/BrentMonitor" &
APP_PID=$!
sleep 5
if ps -p $APP_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 应用运行正常（PID: $APP_PID）${NC}"
    kill $APP_PID 2>/dev/null
else
    echo -e "${RED}❌ 启动失败${NC}"
fi

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  打包完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "运行 App："
echo "  open $APP_FILE"
echo ""
echo "安装包："
echo "  open $OUTPUT_DIR/BrentMonitor.dmg"
