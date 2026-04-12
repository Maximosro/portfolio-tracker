@echo off
:: =====================================================================
::  uninstall-service.bat — Elimina el servicio Portfolio Tracker
::  REQUIERE ejecutarse como Administrador.
:: =====================================================================

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo  ERROR: Este script necesita ejecutarse como Administrador.
    echo  Haz clic derecho ^> "Ejecutar como administrador"
    echo.
    pause
    exit /b 1
)

set TASK_NAME=PortfolioTracker

echo ========================================
echo  Portfolio Tracker — Desinstalador de Servicio
echo ========================================
echo.

:: Detener si esta corriendo
echo Deteniendo el servicio si esta activo...
schtasks /end /tn "%TASK_NAME%" >nul 2>&1

:: Matar proceso java asociado (por si quedo colgado)
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq javaw.exe" /fo list ^| find "PID"') do (
    wmic process where "ProcessId=%%i" get CommandLine 2>nul | find "PortfolioTracker" >nul 2>&1
    if not errorlevel 1 taskkill /pid %%i /f >nul 2>&1
)

:: Eliminar tarea programada
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %errorlevel% equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f
    echo.
    echo Servicio "%TASK_NAME%" eliminado correctamente.
) else (
    echo.
    echo No se encontro el servicio "%TASK_NAME%". Ya estaba desinstalado.
)

echo.
echo ========================================
echo  La aplicacion ya no se iniciara con Windows.
echo  Tus datos en data\portfolio.db NO se han eliminado.
echo ========================================
echo.
pause

