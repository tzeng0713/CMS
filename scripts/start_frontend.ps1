Push-Location "$PSScriptRoot\..\frontend"
try {
  npm start
}
finally {
  Pop-Location
}
