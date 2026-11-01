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

## 部署（打包上線前置設定）

正式環境除了上面的 SMTP／`CMS_APP_BASE_URL` 之外，還需要設定：

```powershell
$env:CMS_SEED_ENABLED="false"
$env:CMS_CORS_ALLOWED_ORIGINS="https://cms.example.com"
$env:CMS_UPLOAD_DIR="/var/cms/uploads/contracts"
```

- `CMS_CORS_ALLOWED_ORIGINS`：正式前端網域（可用逗號分隔多個），會加進允許清單，不會取代本機開發用的 `localhost:4200/4301`。
- `CMS_UPLOAD_DIR`：合約影像的實際存放目錄（建議用絕對路徑，重啟/換機器不會跟著程式的工作目錄跑掉）。沒設定時維持舊行為（相對路徑 `uploads/contracts`）。目前後端沒有提供下載這些檔案的 API，實際對外服務（例如 nginx）要指向同一個目錄。
- `CMS_SEED_ENABLED=false` 會讓 demo 假資料（manager/supervisor/staff 等帳號、demo 客戶/租約）完全不寫入，但 `role_permissions`（角色）與第一個管理員帳號也不會自動建立，必須先手動建好角色與一個管理員帳號，否則正式環境會沒有人能登入。複製 `scripts/bootstrap_admin.sql.example` 為 `scripts/bootstrap_admin.sql`（已被 .gitignore 排除），依檔案內說明填入真實帳密後在正式資料庫執行一次。

前端打包正式版之前，記得把 `frontend/src/environments/environment.prod.ts` 的 `apiBaseUrl` 改成正式後端網址，再執行：

```powershell
cd frontend
npm run build -- --configuration production
```

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
