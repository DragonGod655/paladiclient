@echo off
setlocal enabledelayedexpansion

echo ====================================================
echo   Auto Sell Pro Vip (Standalone) - Trinh Build ^& Ma Hoa
echo ====================================================
echo.

echo [1/4] Dang thiet lap fabric.mod.json cho AutoSell Mod...
copy /y src\main\resources\fabric.mod.json.autosell src\main\resources\fabric.mod.json

echo [2/4] Dang bien dich client (Gradle Build - AutoSell)...
call gradlew.bat build -PbuildType=autosell --console=plain
if %ERRORLEVEL% neq 0 (
    echo.
    echo [LOI] Bien dich that bai! Vui long kiem tra lai code.
    echo.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/4] Kiem tra Allatori Obfuscator...
set JAR_ORIGINAL=build\libs\1.21.11-1.21.11-local.jar
set OBF_DIR=obfuscator
set OBF_JAR=%OBF_DIR%\allatori.jar
set CONFIG_FILE=%OBF_DIR%\config.xml
set OUTPUT_JAR=dist\autosellprovip.jar

if not exist "%OBF_DIR%" mkdir "%OBF_DIR%"
if not exist dist mkdir dist

if not exist "%OBF_JAR%" (
    echo.
    echo [CANH BAO] Khong tim thay file allatori.jar tai duong dan: %OBF_JAR%
    echo.
    echo Hướng dẫn:
    echo 1. Tai Allatori tu trang chu va giai nen.
    echo 2. Chep file 'allatori.jar' vao thu muc '%OBF_DIR%' vua duoc tao.
    echo 3. Chay lai file build nay de tu dung ma hoa code.
    echo.
    echo - Da sao chep file MOD GOC CHUA MA HOA vao: %OUTPUT_JAR%
    echo.
    copy /y "%JAR_ORIGINAL%" "%OUTPUT_JAR%"
    pause
    exit /b 0
)

echo.
echo [4/4] Tu dong tao file cau hinh config.xml...

(
echo ^<config^>
echo     ^<input^>
echo         ^<jar in="..\%JAR_ORIGINAL%" out="..\%OUTPUT_JAR%"/^>
echo     ^</input^>
echo.
echo     ^<keep-names^>
echo         ^<class access="protected+"/^>
echo         ^<class template="class * implements net.fabricmc.api.ClientModInitializer" /^>
echo         ^<class template="class * implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint" /^>
echo         ^<class template="class autosell.mixin.**" /^>
echo         ^<class template="class autosell.AutoSellMod" /^>
echo     ^</keep-names^>
echo.
echo     ^<property name="packages-naming" value="collapse" /^>
echo     ^<property name="classes-naming" value="compact" /^>
echo     ^<property name="methods-naming" value="compact" /^>
echo     ^<property name="fields-naming" value="compact" /^>
echo     ^<property name="local-variables-naming" value="remove" /^>
echo.
echo     ^<property name="string-encryption" value="enable" /^>
echo     ^<property name="string-encryption-type" value="maximum" /^>
echo     ^<property name="string-encryption-version" value="v4" /^>
echo.
echo     ^<property name="line-numbers" value="remove" /^>
echo     ^<property name="generics" value="remove" /^>
echo     ^<property name="inner-classes" value="remove" /^>
echo     ^<property name="remove-dbg" value="enable" /^>
echo ^</config^>
) > "%CONFIG_FILE%"

echo.
echo Dang tien hanh ma hoa file jar bang Allatori...
cd %OBF_DIR%
java -jar allatori.jar config.xml
cd ..

if %ERRORLEVEL% neq 0 (
    echo.
    echo [LOI] Ma hoa that bai! Kiem tra lai JRE va file allatori.jar.
    echo.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ====================================================
echo [THANH CONG] File jar cua ban da duoc ma hoa an toan!
echo File dau ra: %OUTPUT_JAR%
echo ====================================================
echo.
pause
