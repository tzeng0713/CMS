# CMS 客戶管理資訊系統

Angular + Spring Boot + MySQL 的客戶、租約、租金管理系統。

## 專案結構

- `backend`：Spring Boot API
- `frontend`：Angular 前端
- `scripts`：本機啟動與建 DB 輔助腳本
- `table.txt`：完整 MySQL 建表語法與 demo 假資料
- `backend/src/main/resources/schema.sql`：Spring Boot 啟動時使用的 schema
- `backend/src/main/resources/data/seed-data.json`：Spring Boot 第一次啟動時匯入的 demo 假資料

## 需求

- Java 17
- Maven 3.9+
- Node.js 22+（18、20 已停止支援）
- MySQL 8+

## 建立資料庫

方式一：直接執行完整 SQL。

```powershell
mysql -u root -p < table.txt
```

方式二：只建立 DB，讓 Spring Boot 第一次啟動時自動建表與匯入 seed。

```powershell
mysql -u root -p < scripts\create_mysql_database.sql
```

## 啟動後端

預設連線到 `jdbc:mysql://localhost:3306/cms`。

```powershell
.\scripts\start_backend_mysql.ps1 -DbUsername root -DbPassword 你的密碼
```

也可以手動啟動：

```powershell
cd backend
$env:CMS_DB_USERNAME="root"
$env:CMS_DB_PASSWORD="你的密碼"
mvn spring-boot:run
```

後端 API：

- `http://localhost:8080/api/dashboard`

## 啟動前端

```powershell
cd frontend
npm install
npm start
```

前端網址：

- `http://localhost:4200`

## 忘記密碼與 Email 設定

登入頁可用帳號或工作 Email 申請重設密碼。系統一律回覆相同結果，避免洩漏帳號是否存在；寄出的連結只能使用一次，預設 30 分鐘後失效。

新申請帳號必填工作 Email，系統會先寄送一次性驗證連結。帳號在完成驗證前無法登入；驗證後可登入，但只會看到「帳號審核中」頁面，無法載入客戶與租約資料。主管須在「職員總覽」核准開通，申請人重新登入後才能使用系統。自助申請一律先建立為一般秘書，角色調整由主管完成。驗證連結預設 24 小時失效，登入頁也可重新寄送。既有職員會保留為已開通。職員可在「我的資料」申請姓名或信箱異動，主管在「資料異動待審」核准或退回；信箱異動核准後必須重新完成 Email 驗證，才可再次登入與使用忘記密碼。

部署時請設定 SMTP 與前端公開網址；未設定 `CMS_SMTP_HOST` 時，系統不會傳送郵件。

```powershell
$env:CMS_SMTP_HOST="smtp.example.com"
$env:CMS_SMTP_PORT="587"
$env:CMS_SMTP_USERNAME="smtp-user"
$env:CMS_SMTP_PASSWORD="smtp-password"
$env:CMS_PASSWORD_RESET_FROM="no-reply@example.com"
$env:CMS_APP_BASE_URL="https://cms.example.com"
```

可選設定：`CMS_SMTP_AUTH`（預設 `true`）、`CMS_SMTP_STARTTLS`（預設 `true`）、`CMS_PASSWORD_RESET_TOKEN_TTL_MINUTES`（預設 `30`）、`CMS_EMAIL_VERIFICATION_TOKEN_TTL_MINUTES`（預設 `1440`）。

若需讓隔離分支的 4301 前端連到本機 8081 測試後端，可使用：

```powershell
cd frontend
npm exec ng serve -- --configuration test-backend --host 127.0.0.1 --port 4301
```

## 部署（Docker Compose，測試階段用 GCP e2-micro 永久免費方案）

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

**GCP e2-micro（1GB RAM）注意事項：**
- 三個容器的記憶體上限（`docker-compose.yml` 裡的 `mem_limit`）已經收緊過（mysql 256m、backend 420m、frontend 48m），實測正常運作時加總約 480MB，但建議額外幫 VM 加一個 swapfile 當安全網，避免瞬間尖峰被 OOM kill：
  ```bash
  sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```
- GCP 的 e2 系列是 x86_64（amd64），跟 Apple Silicon Mac 本機不同架構；本機測試建置/執行要加 `DOCKER_DEFAULT_PLATFORM=linux/amd64`，VM 上直接跑不需要這個設定。
- 記得到 GCP 防火牆規則開放 80（跟 443，如果之後加 HTTPS）port。

資料庫資料存在 named volume `mysql-data`，合約影像存在 `uploads-data`，`docker compose down`（不加 `-v`）不會清掉這兩個 volume；只有 `docker compose down -v` 會整個砍掉，使用前要注意。

## Demo 帳號

- 主管：`manager / password`
- 督導秘書：`supervisor / password`
- 一般秘書：`staff / password`

## 驗證

```powershell
cd backend
mvn test

cd ..\frontend
npm run build
```

如果本機沒有安裝全域 Maven，但專案資料夾有 `tools/apache-maven-3.9.9`，也可以使用：

```powershell
cd backend
..\tools\apache-maven-3.9.9\bin\mvn.cmd test
```
