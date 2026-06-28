param(
  [string]$DbUrl = "jdbc:mysql://localhost:3306/cms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Taipei&allowPublicKeyRetrieval=true&useSSL=false",
  [string]$DbUsername = "root",
  [string]$DbPassword = "123456"
)

$env:CMS_DB_URL = $DbUrl
$env:CMS_DB_USERNAME = $DbUsername
$env:CMS_DB_PASSWORD = $DbPassword

Push-Location "$PSScriptRoot\..\backend"
try {
  $localMaven = Join-Path $PSScriptRoot "..\tools\apache-maven-3.9.9\bin\mvn.cmd"
  if (Test-Path $localMaven) {
    & $localMaven spring-boot:run
  }
  else {
    mvn spring-boot:run
  }
}
finally {
  Pop-Location
}
