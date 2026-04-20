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
set PORT=19480

echo ========================================
echo  Portfolio Tracker — Desinstalador de Servicio
echo ========================================
echo.

:: 1. Detener tarea programada si esta corriendo
echo [1/3] Deteniendo el servicio si esta activo...
schtasks /end /tn "%TASK_NAME%" >nul 2>&1

:: 2. Matar todos los procesos en el puerto 19480
echo [2/3] Matando procesos en puerto %PORT%...
set FOUND=0
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT% " ^| findstr "LISTENING"') do (
    if %%p neq 0 (
        echo       Matando PID %%p ...
        taskkill /PID %%p /F >nul 2>&1
        set FOUND=1
    )
)
if %FOUND% equ 0 (
    echo       [INFO] No hay procesos escuchando en el puerto %PORT%.
) else (
    echo       [OK] Procesos eliminados.
)

:: 3. Eliminar tarea programada
echo [3/3] Eliminando tarea programada...
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %errorlevel% equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f
    echo       [OK] Servicio "%TASK_NAME%" eliminado correctamente.
) else (
    echo       [INFO] No se encontro el servicio "%TASK_NAME%". Ya estaba desinstalado.
)

echo.
echo ========================================
echo  La aplicacion ya no se iniciara con Windows.
echo  Tus datos en data\portfolio.db NO se han eliminado.
echo ========================================
echo.
pause

