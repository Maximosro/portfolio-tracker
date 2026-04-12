@echo off
title Portfolio Tracker
echo ========================================
echo   Portfolio Tracker - Iniciando...
echo ========================================
echo.

:: Crear carpeta data si no existe
if not exist "data" mkdir data

:: Buscar el JAR
set JAR=
for %%f in (*.jar) do set JAR=%%f

if "%JAR%"=="" (
    echo ERROR: No se encontro el fichero .jar
    echo Asegurate de que el .jar esta en la misma carpeta que este script.
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
