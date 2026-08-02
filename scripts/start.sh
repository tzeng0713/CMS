#!/usr/bin/env bash
# CMS 一鍵啟動腳本（macOS / Linux）
# 用法：
#   ./scripts/start.sh                     # MySQL 模式（預設 root / 密碼 123456）
#   ./scripts/start.sh -u root -p 你的密碼  # 指定 MySQL 帳密
#   ./scripts/start.sh --local             # H2 記憶體資料庫，免裝 MySQL
#   ./scripts/start.sh --backend-only      # 只啟動後端
#   ./scripts/start.sh --frontend-only     # 只啟動前端
#   ./scripts/start.sh --port 8082         # 指定後端 port（預設 8080，需與前端 environment.ts 一致）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DB_USERNAME="root"
DB_PASSWORD="rootpassword"
DB_URL="jdbc:mysql://localhost:3306/cms?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Taipei&allowPublicKeyRetrieval=true&useSSL=false"
USE_LOCAL=false
RUN_BACKEND=true
RUN_FRONTEND=true
BACKEND_PORT=8080

while [[ $# -gt 0 ]]; do
  case "$1" in
    -u|--db-username) DB_USERNAME="$2"; shift 2 ;;
    -p|--db-password) DB_PASSWORD="$2"; shift 2 ;;
    --db-url)         DB_URL="$2"; shift 2 ;;
    --port)           BACKEND_PORT="$2"; shift 2 ;;
    --local)          USE_LOCAL=true; shift ;;
    --backend-only)   RUN_FRONTEND=false; shift ;;
    --frontend-only)  RUN_BACKEND=false; shift ;;
    -h|--help)        grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知參數：$1（用 -h 看用法）" >&2; exit 1 ;;
  esac
done

PIDS=()

# 連同子程序一起停掉（mvn 會 fork 出 java、npm 會 fork 出 ng），只殺父程序會留殘留
kill_tree() {
  local child
  for child in $(pgrep -P "$1" 2>/dev/null); do
    kill_tree "$child"
  done
  kill "$1" 2>/dev/null || true
}

cleanup() {
  echo ""
  echo "正在停止服務..."
  for pid in "${PIDS[@]:-}"; do
    kill_tree "$pid"
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

if $RUN_BACKEND; then
  command -v mvn >/dev/null || { echo "找不到 mvn，請先安裝 Maven（brew install maven）" >&2; exit 1; }

  # macOS 系統的 /usr/bin/java 只是替身，若沒有真正的 JDK 就改用 Homebrew 的 openjdk
  if ! /usr/libexec/java_home >/dev/null 2>&1 && [[ -z "${JAVA_HOME:-}" ]]; then
    for brew_jdk in /opt/homebrew/opt/openjdk@17 /opt/homebrew/opt/openjdk; do
      if [[ -x "$brew_jdk/bin/java" ]]; then
        export JAVA_HOME="$brew_jdk"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "==> 使用 Homebrew JDK：$JAVA_HOME"
        break
      fi
    done
  fi
  java -version >/dev/null 2>&1 || { echo "找不到可用的 JDK，請先安裝（brew install openjdk@17）" >&2; exit 1; }

  if lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port $BACKEND_PORT 已被占用：" >&2
    lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >&2
    echo "請先停掉該程序，或用 --port 換一個 port（記得同步改 frontend/src/environments/environment.ts）" >&2
    exit 1
  fi

  if $USE_LOCAL; then
    echo "==> 啟動後端（H2 記憶體資料庫，local profile，port ${BACKEND_PORT}）..."
    (cd "$ROOT_DIR/backend" && mvn spring-boot:run \
      -Dspring-boot.run.profiles=local \
      -Dspring-boot.run.arguments="--server.port=$BACKEND_PORT") &
  else
    echo "==> 啟動後端（MySQL：${DB_USERNAME}@localhost:3306/cms，port ${BACKEND_PORT}）..."
    (cd "$ROOT_DIR/backend" && \
      CMS_DB_URL="$DB_URL" \
      CMS_DB_USERNAME="$DB_USERNAME" \
      CMS_DB_PASSWORD="$DB_PASSWORD" \
      SERVER_PORT="$BACKEND_PORT" \
      mvn spring-boot:run) &
  fi
  PIDS+=($!)
fi

if $RUN_FRONTEND; then
  command -v npm >/dev/null || { echo "找不到 npm，請先安裝 Node.js 18+（brew install node）" >&2; exit 1; }

  if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
    echo "==> 前端 node_modules 不存在，先安裝依賴..."
    (cd "$ROOT_DIR/frontend" && npm install)
  fi

  echo "==> 啟動前端（http://localhost:4200）..."
  # NG_CLI_ANALYTICS=false：背景執行無法回答 Angular CLI 的統計詢問，直接關閉避免中斷
  (cd "$ROOT_DIR/frontend" && NG_CLI_ANALYTICS=false npm start) &
  PIDS+=($!)
fi

echo ""
echo "後端 API：http://localhost:$BACKEND_PORT"
echo "前端頁面：http://localhost:4200"
echo "按 Ctrl+C 可同時停止前後端。"
wait
