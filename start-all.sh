#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# AI Foundation 一键启动脚本
# 同时启动：后端 (8080) + 管理后台前端 (5173) + Playground 前端 (5174)
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_ADMIN_DIR="$SCRIPT_DIR/frontend"
FRONTEND_PLAYGROUND_DIR="$SCRIPT_DIR/frontend-playground"

# Java 17 配置（优先使用 Corretto 17）
JAVA_HOME_CANDIDATES=(
  "/Users/luyan/Library/Java/JavaVirtualMachines/corretto-17.0.19/Contents/Home"
  "/Users/luyan/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
  "/Applications/ServBay/package/openjdk/17/17.0.15/zulu-17.jdk/Contents/Home"
)

for candidate in "${JAVA_HOME_CANDIDATES[@]}"; do
  if [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    break
  fi
done

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "❌ 未找到 Java 17，请手动设置 JAVA_HOME"
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"
echo "✅ JAVA_HOME = $JAVA_HOME"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 子进程 PID 管理
BACKEND_PID=""
ADMIN_PID=""
PLAYGROUND_PID=""

cleanup() {
  echo ""
  echo -e "${YELLOW}🛑 正在停止所有服务...${NC}"
  for pid in $BACKEND_PID $ADMIN_PID $PLAYGROUND_PID; do
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  # 等待子进程退出
  for pid in $BACKEND_PID $ADMIN_PID $PLAYGROUND_PID; do
    if [ -n "$pid" ]; then
      wait "$pid" 2>/dev/null || true
    fi
  done
  echo -e "${GREEN}✅ 所有服务已停止${NC}"
  exit 0
}

trap cleanup SIGINT SIGTERM

# 带前缀的日志输出
prefix_log() {
  local prefix="$1"
  local color="$2"
  while IFS= read -r line; do
    echo -e "${color}[${prefix}]${NC} $line"
  done
}

# ------------------------------------------------------------
# 1. 启动后端
# ------------------------------------------------------------
echo -e "${BLUE}🧹 执行 mvn clean (跳过测试)...${NC}"
cd "$BACKEND_DIR"
mvn clean install -DskipTests -q
echo -e "${BLUE}   mvn clean 完成${NC}"

echo -e "${BLUE}🚀 启动后端服务 (端口 8080)...${NC}"
cd "$BACKEND_DIR"
mvn -Pqa -pl ai-foundation-gateway spring-boot:run -q \
  > >(prefix_log "BACKEND" "$BLUE") \
  2> >(prefix_log "BACKEND-ERR" "$RED" >&2) &
BACKEND_PID=$!
echo -e "${BLUE}   后端 PID: $BACKEND_PID${NC}"

# ------------------------------------------------------------
# 2. 启动管理后台前端
# ------------------------------------------------------------
echo -e "${GREEN}🚀 启动管理后台前端 (端口 5173)...${NC}"
cd "$FRONTEND_ADMIN_DIR"
npm run dev \
  > >(prefix_log "ADMIN" "$GREEN") \
  2> >(prefix_log "ADMIN-ERR" "$RED" >&2) &
ADMIN_PID=$!
echo -e "${GREEN}   管理后台 PID: $ADMIN_PID${NC}"

# ------------------------------------------------------------
# 3. 启动 Playground 前端
# ------------------------------------------------------------
echo -e "${MAGENTA}🚀 启动 Playground 前端 (端口 5174)...${NC}"
cd "$FRONTEND_PLAYGROUND_DIR"
npm run dev \
  > >(prefix_log "PLAYGROUND" "$MAGENTA") \
  2> >(prefix_log "PLAYGROUND-ERR" "$RED" >&2) &
PLAYGROUND_PID=$!
echo -e "${MAGENTA}   Playground PID: $PLAYGROUND_PID${NC}"

# ------------------------------------------------------------
# 等待服务就绪并打印访问地址
# ------------------------------------------------------------
echo ""
echo -e "${CYAN}⏳ 正在等待服务启动...${NC}"
echo ""

# 等待后端就绪（最多 120 秒）
wait_for_backend() {
  local max_wait=120
  local waited=0
  while [ $waited -lt $max_wait ]; do
    if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

# 等待 Vite 就绪（简单检测端口）
wait_for_port() {
  local port=$1
  local max_wait=60
  local waited=0
  while [ $waited -lt $max_wait ]; do
    if curl -s "http://localhost:$port" 2>/dev/null | head -1 | grep -q "HTTP\|DOCTYPE\|html" || \
       nc -z localhost "$port" 2>/dev/null; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

# 并行等待
wait_for_backend &
BACKEND_WAIT_PID=$!
wait_for_port 5173 &
ADMIN_WAIT_PID=$!
wait_for_port 5174 &
PLAYGROUND_WAIT_PID=$!

wait $BACKEND_WAIT_PID && echo -e "${GREEN}✅ 后端已就绪  http://localhost:8080${NC}" || echo -e "${RED}⚠️  后端启动超时，请检查日志${NC}"
wait $ADMIN_WAIT_PID && echo -e "${GREEN}✅ 管理后台已就绪  http://localhost:5173${NC}" || echo -e "${RED}⚠️  管理后台启动超时，请检查日志${NC}"
wait $PLAYGROUND_WAIT_PID && echo -e "${GREEN}✅ Playground 已就绪  http://localhost:5174${NC}" || echo -e "${RED}⚠️  Playground 启动超时，请检查日志${NC}"

echo ""
echo -e "${CYAN}══════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  🎉 所有服务启动完成！${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════${NC}"
echo -e "  后端 API:       ${BLUE}http://localhost:8080${NC}"
echo -e "  健康检查:       ${BLUE}http://localhost:8080/actuator/health${NC}"
echo -e "  管理后台:       ${GREEN}http://localhost:5173${NC}  (admin / admin123)"
echo -e "  Playground:     ${MAGENTA}http://localhost:5174${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════${NC}"
echo -e "  按 ${RED}Ctrl+C${NC} 停止所有服务"
echo -e "  停止脚本: ${YELLOW}./stop-all.sh${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════${NC}"
echo ""

# 保持前台运行，等待子进程
wait
