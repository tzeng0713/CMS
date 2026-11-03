#!/usr/bin/env bash
# 自動壓一個 <環境>-<今天日期>.<序號> 格式的部署 tag 並 push，觸發 .github/workflows/build-push.yml。
# 序號會自動抓今天已經壓過的最大序號 +1，不用自己算、也不會撞號。
#
# 用法：
#   ./scripts/deploy_tag.sh dev
#   ./scripts/deploy_tag.sh main
set -euo pipefail

ENVIRONMENT="${1:-}"
if [[ "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "main" ]]; then
  echo "用法：$0 dev|main" >&2
  exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "$ENVIRONMENT" ]]; then
  echo "目前在 '$CURRENT_BRANCH' 分支，但你要壓 '$ENVIRONMENT' 的部署 tag，分支對不起來。" >&2
  echo "先執行：git checkout $ENVIRONMENT" >&2
  exit 1
fi

echo "==> 同步遠端 tag，避免跟別人壓過的序號撞號..."
git fetch origin --tags --quiet

DATE_PART="$(date +%Y%m%d)"
PREFIX="${ENVIRONMENT}-${DATE_PART}."

LAST_SEQ=$(git tag -l "${PREFIX}*" | sed "s/^${PREFIX}//" | sort -n | tail -1)
if [[ -z "$LAST_SEQ" ]]; then
  NEXT_SEQ=1
else
  # 10# 強制當十進位解讀，避免 "08"/"09" 這種有前導零的數字被誤判成無效的八進位數字
  NEXT_SEQ=$((10#$LAST_SEQ + 1))
fi

TAG_NAME="${PREFIX}$(printf '%02d' "$NEXT_SEQ")"

echo ""
echo "即將：先 push '$ENVIRONMENT' 分支，再壓 tag：$TAG_NAME"
echo "對應 commit：$(git rev-parse --short HEAD)  $(git log -1 --pretty=%s)"
echo ""
read -r -p "確定要繼續嗎？(y/N) " CONFIRM
if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
  echo "已取消，沒有 push 分支也沒有建立 tag。"
  exit 0
fi

echo "==> push 分支 '$ENVIRONMENT'，確保 tag 跟分支指向同一個狀態..."
git push origin "$ENVIRONMENT"

git tag "$TAG_NAME"
git push origin "$TAG_NAME"

echo ""
echo "完成，已推送分支 '$ENVIRONMENT' 跟 tag $TAG_NAME。"
echo "到 GitHub 的 Actions 分頁確認建置狀態：https://github.com/tzeng0713/CMS/actions"
