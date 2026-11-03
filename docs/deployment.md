# 部署（Docker Compose，測試階段用 GCP e2-micro 永久免費方案）

`docker-compose.yml` 會啟動三個容器：`mysql`（資料庫）、`backend`（Spring Boot，連到 `mysql`）、
`frontend`（nginx，服務 Angular 靜態檔案，並把 `/api` 反向代理到 `backend`、`/uploads` 服務合約影像檔）。
前後端走同一個 nginx 對外，瀏覽器看到的是同源請求，不需要處理 CORS。

```bash
cp .env.example .env
# 編輯 .env：填入 MySQL 密碼、CMS_APP_BASE_URL（你的 VM 外部 IP 或網域）、SMTP 設定
docker compose up -d --build
```

**之後更新：壓 tag 才會建置＋上版（不是一 push 就自動上），VM 只要 pull（不用在 VM 上編譯）**

`.github/workflows/build-push.yml` 不是監聽 push，是監聽**符合格式的 tag**，平常 commit/push 不會觸發任何建置，要明確決定「現在要上版了」才壓 tag：

```bash
git checkout dev   # 或 main，看要上哪個環境
git tag dev-20261103.01   # 格式：<環境>-<YYYYMMDD>.<序號>，同一天重上版序號往上加
git push origin dev-20261103.01
```

推上去後，GitHub 的雲端機器會自動把 `backend`／`frontend` 兩個 image 建好，推到 GitHub Container Registry。不用在自己電腦或 VM 上編譯，GitHub Actions 對 public repo 完全免費、沒有額度限制；任何對這個 repo 有寫入權限的協作者都能壓 tag 觸發，不用個別申請 Token。

**image tag 跟著 tag 的環境前綴走**（`dev-*` 推出 `:dev`、`main-*` 推出 `:main`），兩個環境的 image 不會互相蓋掉，可以明確分清楚開發環境跟正式環境。同時也會推一份跟 tag 同名的快照（例如 `:dev-20261103.01`），之後要回滾到某個特定版本，把 VM 的 `.env` 的 `IMAGE_TAG` 改成那個快照標籤即可，不用重新建置。

`.env` 裡的 `IMAGE_TAG`（預設 `dev`）決定這台機器要 pull 哪一個：這台測試機維持預設值即可；之後如果拿同一份 `docker-compose.yml` 架設正式環境，只要把那台機器的 `.env` 改成 `IMAGE_TAG=main`，不用另外維護一份 compose 設定。

第一次設定好後，到 `github.com/tzeng0713` 的 Packages 分頁確認 `cms-backend`／`cms-frontend` 兩個 package 的可見度是 **Public**（GitHub Actions 用內建 token 推送時通常會自動跟著 repo 一樣是 public，保險起見手動確認一次，不是 public 的話 VM pull 會需要先登入）。

VM 上更新：

```bash
cd ~/CMS
git pull
docker compose pull
docker compose up -d
```

`git pull` 是為了同步 `docker-compose.yml`、`mysql/low-memory.cnf`、`scripts/*.sql` 這些沒有打包進 image、VM 直接讀檔案的設定；程式碼本身（Java／Angular）已經在 CI 建好的 image 裡，VM 不需要重新編譯。

第一次啟動後，`schema.sql` 會把資料表建出來，但因為 `CMS_SEED_ENABLED=false`（寫在 compose 裡），
不會有任何角色或帳號，必須手動建第一個管理員帳號：

```bash
cp scripts/bootstrap_admin.sql.example scripts/bootstrap_admin.sql
# 依檔案內說明產生密碼雜湊、填入真實姓名/帳號/Email 後：
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p你的root密碼 cms < scripts/bootstrap_admin.sql
```

務必加 `--default-character-set=utf8mb4`，否則中文姓名/分館名稱會被雙重編碼存成亂碼（已實測驗證）。

**限制誰可以連進來（nginx Basic Auth）：**

整站（包含 `/api`）會先要求瀏覽器彈出的帳號密碼才能進，沒有這組密碼的人連 CMS 自己的登入頁都看不到：

```bash
mkdir -p nginx
echo "帳號:$(openssl passwd -apr1 '你要設定的共用密碼')" > nginx/.htpasswd
docker compose up -d --build frontend
```

`nginx/.htpasswd` 已被 `.gitignore` 排除，不會進版控。這組帳密是所有人共用的一道閘門，跟 CMS 系統本身每個職員各自的帳號密碼是兩回事。

**GCP e2-micro（1GB RAM）注意事項：**
- 三個容器的記憶體上限（`docker-compose.yml` 裡的 `mem_limit`）已經收緊過（mysql 256m、backend 420m、frontend 48m），實測正常運作時加總約 480MB，但建議額外幫 VM 加一個 swapfile 當安全網，避免瞬間尖峰被 OOM kill：
  ```bash
  sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```
- GCP 的 e2 系列是 x86_64（amd64），跟 Apple Silicon Mac 本機不同架構；本機測試建置/執行要加 `DOCKER_DEFAULT_PLATFORM=linux/amd64`，VM 上直接跑不需要這個設定。
- 記得到 GCP 防火牆規則開放 80（跟 443，如果之後加 HTTPS）port。

資料庫資料存在 named volume `mysql-data`，合約影像存在 `uploads-data`，`docker compose down`（不加 `-v`）不會清掉這兩個 volume；只有 `docker compose down -v` 會整個砍掉，使用前要注意。
