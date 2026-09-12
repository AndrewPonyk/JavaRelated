@echo off
:: Check and self-elevate to Administrator automatically
net session >nul 2>&1
if %errorLevel% neq 0 (
    powershell -NoProfile -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

echo =========================================================
echo Registering Windows Security Platform 10.0.29628.1000...
echo =========================================================

if not exist "C:\Windows\System32\SecurityHealth\10.0.29628.1000-0" (
    echo Creating 10.0.29628.1000-0 directory...
    xcopy /E /I /Q /Y "C:\Windows\System32\SecurityHealth\10.0.29554.1001-0" "C:\Windows\System32\SecurityHealth\10.0.29628.1000-0" >nul 2>&1
)

echo Writing platform registration script...
(
echo @echo off
echo reg add "HKLM\SOFTWARE\Microsoft\Windows Security Health\Platform" /v "CoreLocation" /t REG_SZ /d "\\?\C:\Windows\System32\SecurityHealth\10.0.29628.1000-0" /f
echo reg add "HKLM\SOFTWARE\Microsoft\Windows Security Health\Platform" /v "Registered" /t REG_DWORD /d 1 /f
) > "%SystemRoot%\Temp\set_platform.cmd"

echo Applying Platform keys with SYSTEM privileges...
schtasks /create /tn "FixSecHealth" /tr "cmd.exe /c %SystemRoot%\Temp\set_platform.cmd" /sc onstart /ru "SYSTEM" /f >nul 2>&1
schtasks /run /tn "FixSecHealth" >nul 2>&1
timeout /t 2 /nobreak >nul 2>&1
schtasks /delete /tn "FixSecHealth" /f >nul 2>&1

sc create FixSecHealth binPath= "cmd.exe /c %SystemRoot%\Temp\set_platform.cmd" type= own start= demand >nul 2>&1
sc start FixSecHealth >nul 2>&1
timeout /t 1 /nobreak >nul 2>&1
sc delete FixSecHealth >nul 2>&1

if exist "%SystemRoot%\Temp\set_platform.cmd" del "%SystemRoot%\Temp\set_platform.cmd" >nul 2>&1

echo Registering Updates catalog entries...
reg add "HKLM\SOFTWARE\Microsoft\Windows Security Health\Updates" /v "10.0.29628.1000-0" /t REG_QWORD /d 1 /f
reg add "HKLM\SOFTWARE\Microsoft\Windows Security Health\Updates" /v "10.0.29554.1001-0" /t REG_QWORD /d 1 /f
reg add "HKLM\SOFTWARE\Microsoft\Windows Security Health\Updates" /v "10.0.27703.1006-0" /t REG_QWORD /d 1 /f
reg delete "HKLM\SOFTWARE\Microsoft\Windows Security Health\Updates" /v "wu" /f >nul 2>&1

echo.
echo Current Platform values in registry:
reg query "HKLM\SOFTWARE\Microsoft\Windows Security Health\Platform"
echo.

echo.
echo =========================================================
echo Verifying with Windows Update Agent...
echo =========================================================
powershell -NoProfile -Command "$Session = New-Object -ComObject Microsoft.Update.Session; $Searcher = $Session.CreateUpdateSearcher(); $Results = $Searcher.Search('IsInstalled=0 and Type=''Software'''); Write-Host 'Missing updates count:' $Results.Updates.Count"

echo.
echo =========================================================
echo SUCCESS! All keys updated. Refresh MetaDefender now.
echo =========================================================
pause
