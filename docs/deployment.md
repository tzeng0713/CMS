# 部署（Docker Compose，測試階段用 GCP e2-micro 永久免費方案）

`docker-compose.yml` 會啟動三個容器：`mysql`（資料庫）、`backend`（Spring Boot，連到 `mysql`）、
`frontend`（nginx，服務 Angular 靜態檔案，並把 `/api` 反向代理到 `backend`、`/uploads` 服務合約影像檔）。
前後端走同一個 nginx 對外，瀏覽器看到的是同源請求，不需要處理 CORS。

```bash
cp .env.example .env
# 編輯 .env：填入 MySQL 密碼、CMS_APP_BASE_URL（你的 VM 外部 IP 或網域）、SMTP 設定
docker compose up -d --build
```

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
