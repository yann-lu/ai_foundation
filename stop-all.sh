#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# AI Foundation 一键停止脚本
# 停止：后端 + 管理后台 + Playground
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}🛑 正在停止 AI Foundation 所有服务...${NC}"
echo ""

# 查找并停止后端 Java 进程
stop_by_port() {
  local port=$1
  local name=$2
  local pids

  if command -v lsof >/dev/null 2>&1; then
    pids=$(lsof -ti tcp:"$port" 2>/dev/null || true)
  else
    pids=$(fuser "$port/tcp" 2>/dev/null || true)
  fi

  if [ -n "$pids" ]; then
    echo -e "${YELLOW}停止 ${name} (端口 ${port}, PID: ${pids//$'\n'/, })...${NC}"
    # 先发送 SIGTERM
    echo "$pids" | xargs kill 2>/dev/null || true
    # 等待最多 10 秒
    local waited=0
    while [ $waited -lt 10 ]; do
      local still_alive=false
      for pid in $pids; do
        if kill -0 "$pid" 2>/dev/null; then
          still_alive=true
          break
        fi
      done
      if [ "$still_alive" = false ]; then
        break
      fi
      sleep 1
      waited=$((waited + 1))
    done
    # 强制杀死还活着的
    for pid in $pids; do
      if kill -0 "$pid" 2>/dev/null; then
        echo -e "${RED}  强制杀死 PID $pid${NC}"
        kill -9 "$pid" 2>/dev/null || true
      fi
    done
    echo -e "${GREEN}  ✅ ${name} 已停止${NC}"
  else
    echo -e "${GREEN}  ✅ ${name} (端口 ${port}) 未运行${NC}"
  fi
}

# 按端口停止服务
stop_by_port 8080 "后端服务"
stop_by_port 5173 "管理后台前端"
stop_by_port 5174 "Playground 前端"

# 额外清理：查找 vite / mvn 进程（以防端口检测遗漏）
echo ""
echo -e "${CYAN}清理残留进程...${NC}"

VITE_PIDS=$(pgrep -f "vite" 2>/dev/null || true)
if [ -n "$VITE_PIDS" ]; then
  echo -e "${YELLOW}  停止残留 vite 进程: ${VITE_PIDS//$'\n'/, }${NC}"
  echo "$VITE_PIDS" | xargs kill 2>/dev/null || true
  sleep 2
  for pid in $VITE_PIDS; do
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null || true
  done
fi

MVN_PIDS=$(pgrep -f "spring-boot:run" 2>/dev/null || true)
if [ -n "$MVN_PIDS" ]; then
  echo -e "${YELLOW}  停止残留 Maven 进程: ${MVN_PIDS//$'\n'/, }${NC}"
  echo "$MVN_PIDS" | xargs kill 2>/dev/null || true
fi

echo ""
echo -e "${GREEN}✅ 所有服务已停止${NC}"
