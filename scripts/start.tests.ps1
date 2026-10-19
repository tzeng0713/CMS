$scriptPath = Join-Path $PSScriptRoot 'start.ps1'

Describe 'Windows startup script' {
  It 'shows usage without starting services' {
    $output = & $scriptPath -Help 2>&1 | Out-String

    $output | Should Match 'Usage:.*start\.ps1'
    $output | Should Match '-Local'
    $output | Should Match '-MySql'
  }
}
