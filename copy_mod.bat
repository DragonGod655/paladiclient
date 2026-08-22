@echo off
set SRC=build\libs\1.21.11-1.21.11-local.jar

if not exist dist mkdir dist
copy /y "%SRC%" "dist\paladiclient.jar" >nul
copy /y "%SRC%" "paladi-client-1.21.11.jar" >nul

echo [COPIED] dist\paladiclient.jar
echo [COPIED] paladi-client-1.21.11.jar

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$src = 'build\libs\1.21.11-1.21.11-local.jar';" ^
  "$targets = @();" ^
  "Get-ChildItem -Path 'C:\Users\Admin\AppData\Roaming\.dawn\profiles' -Recurse -Directory -Filter 'mods' -ErrorAction SilentlyContinue | ForEach-Object { $targets += $_.FullName };" ^
  "Get-ChildItem -Path 'C:\Users\Admin\AppData\Roaming\.feather' -Recurse -Directory -Filter 'user-mods' -ErrorAction SilentlyContinue | ForEach-Object { $targets += $_.FullName };" ^
  "Get-ChildItem -Path 'C:\Users\Admin\AppData\Roaming\.minecraft' -Directory -Filter '*mods*' -ErrorAction SilentlyContinue | ForEach-Object { $targets += $_.FullName };" ^
  "Get-ChildItem -Path 'C:\Users\Admin\AppData\Roaming\.tlauncher' -Recurse -Directory -Filter 'mods' -ErrorAction SilentlyContinue | ForEach-Object { $targets += $_.FullName };" ^
  "Get-ChildItem -Path 'C:\Users\Admin\Downloads\mmc-develop-win32\MultiMC' -Recurse -Directory -Filter 'mods' -ErrorAction SilentlyContinue | ForEach-Object { $targets += $_.FullName };" ^
  "$unique = $targets | Select-Object -Unique;" ^
  "foreach ($dir in $unique) {" ^
  "  if (Test-Path $dir) {" ^
  "    Copy-Item -Path $src -Destination (Join-Path $dir 'paladiclient.jar') -Force;" ^
  "    Write-Host ('[COPIED] ' + (Join-Path $dir 'paladiclient.jar'));" ^
  "  }" ^
  "}"

echo.
echo [DONE] Tat ca cac profile bao gom aha da duoc cap nhat day du!
