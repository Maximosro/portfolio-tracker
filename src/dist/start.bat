@echo off
title Portfolio Tracker
echo ========================================
echo   Portfolio Tracker - Iniciando...
echo ========================================
echo.

:: Ir al directorio donde esta este script (donde esta el JAR)
cd /d "%~dp0"

:: Crear carpeta data si no existe
if not exist "data" mkdir data

:: Buscar Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java no encontrado en PATH.
    echo Instala Java 21+ y asegurate de que esta en el PATH del sistema.
    echo.
    echo Descarga: https://adoptium.net/
    pause
    exit /b 1
)

:: Buscar el JAR
set JAR=
for %%f in ("*.jar") do set "JAR=%%f"

if "%JAR%"=="" (
    echo ERROR: No se encontro el fichero .jar
    echo Asegurate de que el .jar esta en la misma carpeta que este script.
    echo Directorio actual: %cd%
    pause
    exit /b 1
)

echo Ejecutando: %JAR%
echo Base de datos: data\portfolio.db
echo.
echo  URL: http://localhost:19480/portfoliotracker/
echo.
echo Pulsa Ctrl+C para detener.
echo ========================================

:: Abrir navegador automaticamente tras un pequeno delay
start "" /B cmd /c "timeout /t 5 /nobreak >nul && start http://localhost:19480/portfoliotracker/"

java -jar "%JAR%"
pause
