@echo off
setlocal
rem Baut Juiced und legt die fertige Mod in den Modrinth-Profilordner "Fabric 26.3".
cd /d "%~dp0"
set "MODS=%APPDATA%\ModrinthApp\profiles\Fabric 26.3\mods"

for /f "tokens=2 delims==" %%V in ('findstr /b "version=" gradle.properties') do set "VERSION=%%V"
set "JAR=build\libs\juiced-%VERSION%.jar"

echo Baue Juiced %VERSION% ...
call gradlew.bat build
if errorlevel 1 goto failed
if not exist "%JAR%" goto missing
if not exist "%MODS%" goto nomods

echo Ersetze alte Juiced-Versionen im Mods-Ordner ...
del /q "%MODS%\juiced-*.jar" 2>nul
copy /y "%JAR%" "%MODS%\" >nul
if errorlevel 1 goto copyfailed

echo.
echo Fertig: juiced-%VERSION%.jar liegt im Mods-Ordner. Minecraft kann gestartet werden.
pause
exit /b 0

:failed
echo.
echo Der Build ist fehlgeschlagen. Die Fehlermeldung steht weiter oben.
pause
exit /b 1

:missing
echo.
echo Build fertig, aber %JAR% wurde nicht gefunden.
pause
exit /b 1

:nomods
echo.
echo Mods-Ordner nicht gefunden: %MODS%
echo Die fertige Mod liegt hier: %JAR%
pause
exit /b 1

:copyfailed
echo.
echo Kopieren fehlgeschlagen. Ist Minecraft noch offen? Bitte schliessen und nochmal starten.
pause
exit /b 1
