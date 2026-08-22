@echo off
echo === Paladi Client Builder ===
cd /d C:\Users\Admin\Documents\GitHub\meteor-client
echo Building...
call gradlew.bat build
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED!
    pause
    exit /b 1
)
echo.

set MINECRAFT_MODS=C:\Users\Admin\AppData\Roaming\.minecraft\mods
set FEATHER_MODS=C:\Users\Admin\AppData\Roaming\.feather\user-mods\1.21.11-fabric
set FEATHER_MC_MODS=C:\Users\Admin\AppData\Roaming\.minecraft\feather-mods
set DAWN_MODS=C:\Users\Admin\AppData\Roaming\.dawn\profiles\paladi\.minecraft\mods
set TLAUNCHER_MODS=C:\Users\Admin\AppData\Roaming\.tlauncher\legacy\Minecraft\game\mods

echo Copying to mods folders...
for %%f in (build\libs\*.jar) do (
    echo %%f | findstr /v "dev sources" >nul
    if not errorlevel 1 (
        copy /Y "%%f" "%FEATHER_MODS%\" >nul 2>&1
        copy /Y "%%f" "%FEATHER_MC_MODS%\" >nul 2>&1
        copy /Y "%%f" "%DAWN_MODS%\" >nul 2>&1
        copy /Y "%%f" "%TLAUNCHER_MODS%\" >nul 2>&1
        echo [OK] Copied: %%f
        echo      -> %FEATHER_MODS%
        echo      -> %FEATHER_MC_MODS%
        echo      -> %DAWN_MODS%
        echo      -> %TLAUNCHER_MODS%
    )
)

echo.
echo === DONE! ===
pause
