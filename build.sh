#!/bin/bash
# =============================================================================
# Brent Oil Monitor - 构建脚本
# 支持 macOS/Linux，依赖 Maven
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/brent-monitor"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Brent Oil Monitor 构建脚本${NC}"
echo -e "${BLUE}========================================${NC}"

# 检测 Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ 错误：未找到 Maven，请先安装：${NC}"
    echo "  macOS: brew install maven"
    echo "  Ubuntu: sudo apt install maven"
    exit 1
fi

echo -e "${GREEN}✓ Maven: $(mvn -version | head -1)${NC}"
echo -e "${GREEN}✓ Java:  $(java -version 2>&1 | head -1)${NC}"
echo -e "${GREEN}✓ CPU:   $(sysctl -n hw.ncpu) 核心${NC}"

cd "$PROJECT_DIR"

echo -e "\n${YELLOW}🧹 清理项目...${NC}"
mvn clean -q

echo -e "\n${YELLOW}🔨 编译项目...${NC}"
mvn compile -q

echo -e "\n${YELLOW}📦 打包（shade 插件生成 uber-jar）...${NC}"
mvn package -q -DskipTests

JAR_FILE="$PROJECT_DIR/target/brent-monitor.jar"
if [ -f "$JAR_FILE" ]; then
    echo -e "\n${GREEN}✓ 打包成功！${NC}"
    echo "  JAR: $JAR_FILE"
    echo "  大小: $(du -h "$JAR_FILE" | cut -f1)"
else
    echo -e "${RED}❌ 打包失败${NC}"
    exit 1
fi

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  构建完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "运行应用："
echo "  ./run.sh"
echo ""
echo "打包 macOS App："
echo "  ./build-macos-app.sh"
echo ""
