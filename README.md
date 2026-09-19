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
- Node.js 18+
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

新申請帳號必填工作 Email，系統會先寄送一次性驗證連結。帳號在完成驗證前無法登入；驗證連結預設 24 小時失效，登入頁也可重新寄送。既有職員不受影響。既有職員請由主管至「職員總覽」補上工作 Email，否則該職員無法收到重設連結。

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
