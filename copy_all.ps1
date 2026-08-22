$src = "C:\Users\Admin\Documents\GitHub\meteor-client\build\libs\1.21.11-1.21.11-local.jar"

$explicitTargets = @(
    "C:\Users\Admin\Documents\GitHub\meteor-client\dist\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\aha\private-game-content\mods\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\aha\auto-sync\groups\default\mods\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\aha\.minecraft\mods\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\paladi\private-game-content\mods\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\paladi\.minecraft\mods\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\feather-3\.minecraft\mods\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.feather\user-mods\1.21.11-fabric\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.minecraft\feather-mods\paladiclient.jar",
    "C:\Users\Admin\AppData\Roaming\.minecraft\mods\paladiclient.jar",
    "C:\Users\Admin\Downloads\mmc-develop-win32\MultiMC\mods\paladiclient.jar",
    "C:\Users\Admin\Downloads\mmc-develop-win32\MultiMC\instances\1.21.11\.minecraft\mods\paladiclient.jar",
    "C:\Users\Admin\Downloads\mmc-develop-win32\MultiMC\instances\1.21.111\.minecraft\mods\paladiclient.jar"
)

foreach ($target in $explicitTargets) {
    $parent = Split-Path -Path $target -Parent
    if (Test-Path $parent) {
        Copy-Item -Path $src -Destination $target -Force
        Write-Host "Updated: $target"
    }
}

$oldDawn = @(
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\aha\private-game-content\mods\dawn-client.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\aha\auto-sync\groups\default\mods\dawn-client.jar",
    "C:\Users\Admin\AppData\Roaming\.dawn\profiles\aha\.minecraft\mods\dawn-client.jar"
)
foreach ($old in $oldDawn) {
    if (Test-Path $old) {
        Remove-Item -Path $old -Force
        Write-Host "Removed old: $old"
    }
}

Get-ChildItem -Path "C:\Users\Admin\AppData\Roaming\.dawn\profiles\aha" -Recurse -Filter "paladiclient.jar" | Select-Object FullName, Length, LastWriteTime
