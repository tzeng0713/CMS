[CmdletBinding()]
param(
  [switch]$Local,
  [switch]$MySql,
  [switch]$BackendOnly,
  [switch]$FrontendOnly,
  [switch]$DemoMail,
  [ValidateRange(1, 65535)]
  [int]$Port = 8080,
  [string]$DbUrl = "jdbc:mysql://localhost:3306/cms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Taipei&allowPublicKeyRetrieval=true&useSSL=false",
  [string]$DbUsername = "root",
  [string]$DbPassword = "123456",
  [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Show-Usage {
  @"
Usage: .\scripts\start.ps1 [options]

Starts the CMS backend and frontend from Windows PowerShell.

Options:
  -Local             Use the seeded H2 in-memory database instead of MySQL.
  -MySql             Use MySQL (default).
  -BackendOnly       Start only the Spring Boot backend.
  -FrontendOnly      Start only the Angular frontend.
  -DemoMail          Prompt for the Gmail app password and enable demo email delivery.
  -Port <number>     Backend port (default: 8080).
  -DbUrl <url>       MySQL JDBC URL.
  -DbUsername <name> MySQL username (default: root).
  -DbPassword <text> MySQL password (default: 123456).
  -Help              Show this message without starting services.

Examples:
  .\scripts\start.ps1
  .\scripts\start.ps1 -Local
  .\scripts\start.ps1 -BackendOnly -Local
  .\scripts\start.ps1 -BackendOnly -DemoMail
  .\scripts\start.ps1 -DbPassword 'your-password'
"@
}

function Escape-PowerShellLiteral([string]$Value) {
  return $Value.Replace("'", "''")
}

function Assert-PortAvailable([int]$RequestedPort, [string]$ServiceName) {
  $listener = Get-NetTCPConnection -State Listen -LocalPort $RequestedPort -ErrorAction SilentlyContinue |
    Select-Object -First 1

  if ($null -ne $listener) {
    throw "$ServiceName cannot start because port $RequestedPort is already in use."
  }
}

function Start-ManagedPowerShell([string]$Command) {
  $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
  return Start-Process -FilePath 'powershell.exe' -NoNewWindow -PassThru -ArgumentList @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-EncodedCommand', $encodedCommand
  )
}

function Stop-ProcessTree([int]$ProcessId) {
  $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
  foreach ($child in $children) {
    Stop-ProcessTree -ProcessId $child.ProcessId
  }

  Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

if ($Help) {
  Show-Usage
  return
}

if ($Local -and $MySql) {
  throw 'Choose either -Local or -MySql, not both.'
}

if ($BackendOnly -and $FrontendOnly) {
  throw 'Choose either -BackendOnly or -FrontendOnly, not both.'
}

if ($DemoMail -and $FrontendOnly) {
  throw '-DemoMail requires the backend.'
}

$rootDirectory = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $rootDirectory 'backend'
$frontendDirectory = Join-Path $rootDirectory 'frontend'
$mavenPath = Join-Path $rootDirectory 'tools\apache-maven-3.9.9\bin\mvn.cmd'
$runBackend = -not $FrontendOnly
$runFrontend = -not $BackendOnly
$processes = @()
$originalMailEnvironment = @{}

if ($runBackend) {
  if (-not (Test-Path -LiteralPath $mavenPath)) {
    $mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -eq $mavenCommand) {
      throw 'Maven was not found. Restore tools\\apache-maven-3.9.9 or install Maven.'
    }
    $mavenPath = $mavenCommand.Source
  }

  Assert-PortAvailable -RequestedPort $Port -ServiceName 'Backend'
}

if ($runFrontend) {
  if ($null -eq (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw 'Node.js and npm were not found. Install Node.js 18 or later.'
  }

  Assert-PortAvailable -RequestedPort 4200 -ServiceName 'Frontend'

  if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory 'node_modules'))) {
    Write-Host '==> Installing frontend dependencies...'
    Push-Location $frontendDirectory
    try {
      npm install
      if ($LASTEXITCODE -ne 0) {
        throw "npm install failed with exit code $LASTEXITCODE."
      }
    }
    finally {
      Pop-Location
    }
  }
}

try {
  if ($runBackend) {
    if ($DemoMail) {
      $secureMailPassword = Read-Host 'Gmail app password for tzeng0713@gmail.com' -AsSecureString
      $mailPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureMailPassword)
      try {
        $mailPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($mailPasswordPointer) -replace '\s', ''
        if ([string]::IsNullOrWhiteSpace($mailPassword)) {
          throw 'A Gmail app password is required for -DemoMail.'
        }
        foreach ($name in @('CMS_SMTP_HOST', 'CMS_SMTP_USERNAME', 'CMS_SMTP_PASSWORD', 'CMS_PASSWORD_RESET_FROM', 'CMS_APP_BASE_URL')) {
          $originalMailEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        }
        $env:CMS_SMTP_HOST = 'smtp.gmail.com'
        $env:CMS_SMTP_USERNAME = 'tzeng0713@gmail.com'
        $env:CMS_SMTP_PASSWORD = $mailPassword
        $env:CMS_PASSWORD_RESET_FROM = 'tzeng0713@gmail.com'
        $env:CMS_APP_BASE_URL = 'http://localhost:4200'
      }
      finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($mailPasswordPointer)
        $mailPassword = $null
        $secureMailPassword.Dispose()
      }
    }

    $escapedBackendDirectory = Escape-PowerShellLiteral $backendDirectory
    $escapedMavenPath = Escape-PowerShellLiteral $mavenPath

    if ($Local) {
      Write-Host "==> Starting backend with the local H2 database on http://localhost:$Port"
      $backendCommand = @"
Set-Location -LiteralPath '$escapedBackendDirectory'
& '$escapedMavenPath' spring-boot:run '-Dspring-boot.run.profiles=local' '-Dspring-boot.run.arguments=--server.port=$Port'
exit `$LASTEXITCODE
"@
    }
    else {
      $escapedDbUrl = Escape-PowerShellLiteral $DbUrl
      $escapedDbUsername = Escape-PowerShellLiteral $DbUsername
      $escapedDbPassword = Escape-PowerShellLiteral $DbPassword
      Write-Host "==> Starting backend with MySQL on http://localhost:$Port"
      $backendCommand = @"
Set-Location -LiteralPath '$escapedBackendDirectory'
`$env:CMS_DB_URL = '$escapedDbUrl'
`$env:CMS_DB_USERNAME = '$escapedDbUsername'
`$env:CMS_DB_PASSWORD = '$escapedDbPassword'
`$env:SERVER_PORT = '$Port'
& '$escapedMavenPath' spring-boot:run
exit `$LASTEXITCODE
"@
    }

    $processes += Start-ManagedPowerShell $backendCommand
    foreach ($name in $originalMailEnvironment.Keys) {
      [Environment]::SetEnvironmentVariable($name, $originalMailEnvironment[$name], 'Process')
    }
    $originalMailEnvironment.Clear()
  }

  if ($runFrontend) {
    $escapedFrontendDirectory = Escape-PowerShellLiteral $frontendDirectory
    Write-Host '==> Starting frontend on http://localhost:4200'
    $frontendCommand = @"
Set-Location -LiteralPath '$escapedFrontendDirectory'
`$env:NG_CLI_ANALYTICS = 'false'
npm start
exit `$LASTEXITCODE
"@
    $processes += Start-ManagedPowerShell $frontendCommand
  }

  Write-Host ''
  if ($runBackend) {
    Write-Host "Backend API: http://localhost:$Port"
  }
  if ($runFrontend) {
    Write-Host 'Frontend:    http://localhost:4200'
  }
  Write-Host 'Press Ctrl+C to stop services started by this script.'

  while ($true) {
    foreach ($process in $processes) {
      $process.Refresh()
      if ($process.HasExited) {
        throw "A startup process exited unexpectedly with code $($process.ExitCode)."
      }
    }
    Start-Sleep -Seconds 1
  }
}
finally {
  foreach ($name in $originalMailEnvironment.Keys) {
    [Environment]::SetEnvironmentVariable($name, $originalMailEnvironment[$name], 'Process')
  }
  foreach ($process in $processes) {
    if (-not $process.HasExited) {
      Stop-ProcessTree -ProcessId $process.Id
    }
  }
}
