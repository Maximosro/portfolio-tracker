@echo off
:: =====================================================================
::  install-service.bat — Registra Portfolio Tracker como servicio
::  en segundo plano que se inicia automáticamente con Windows 11.
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

echo ========================================
echo  Portfolio Tracker — Instalador de Servicio
echo ========================================
echo.

:: Determinar ruta absoluta de esta carpeta
set "APP_DIR=%~dp0"
set "APP_DIR=%APP_DIR:~0,-1%"

:: Buscar el JAR
set JAR=
for %%f in ("%APP_DIR%\*.jar") do set "JAR=%%f"

if "%JAR%"=="" (
    echo ERROR: No se encontro el fichero .jar en %APP_DIR%
    pause
    exit /b 1
)

:: Buscar Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java no encontrado en PATH.
    echo Instala Java 21+ y asegurate de que esta en el PATH.
    pause
    exit /b 1
)

:: Obtener ruta completa de javaw.exe (ejecucion sin ventana de consola)
for /f "tokens=*" %%i in ('where javaw 2^>nul') do set "JAVAW=%%i"
if "%JAVAW%"=="" (
    :: Fallback a java.exe si javaw no esta disponible
    for /f "tokens=*" %%i in ('where java') do set "JAVAW=%%i"
)

:: Crear carpeta data si no existe
if not exist "%APP_DIR%\data" mkdir "%APP_DIR%\data"

:: Nombre de la tarea programada
set TASK_NAME=PortfolioTracker

:: Eliminar tarea anterior si existe
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %errorlevel% equ 0 (
    echo Eliminando servicio anterior...
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
)

:: Crear la tarea programada
echo Registrando servicio "%TASK_NAME%"...
echo   JAR:    %JAR%
echo   Java:   %JAVAW%
echo   Dir:    %APP_DIR%
echo.

schtasks /create ^
    /tn "%TASK_NAME%" ^
    /tr "\"%JAVAW%\" -jar \"%JAR%\"" ^
    /sc onlogon ^
    /rl highest ^
    /f

if %errorlevel% neq 0 (
    echo.
    echo ERROR: No se pudo crear la tarea programada.
    pause
    exit /b 1
)

:: Configurar directorio de trabajo via XML (schtasks /create no lo soporta directamente)
:: Exportar, modificar e importar
set "TEMP_XML=%TEMP%\pt_task.xml"
schtasks /query /tn "%TASK_NAME%" /xml > "%TEMP_XML%" 2>nul

:: Inyectar WorkingDirectory en el XML
powershell -Command ^
    "$xml = [xml](Get-Content '%TEMP_XML%'); " ^
    "$ns = New-Object Xml.XmlNamespaceManager($xml.NameTable); " ^
    "$ns.AddNamespace('t','http://schemas.microsoft.com/windows/2004/02/mit/task'); " ^
    "$actions = $xml.SelectSingleNode('//t:Actions/t:Exec', $ns); " ^
    "$wd = $xml.CreateElement('WorkingDirectory','http://schemas.microsoft.com/windows/2004/02/mit/task'); " ^
    "$wd.InnerText = '%APP_DIR%'; " ^
    "$actions.AppendChild($wd) | Out-Null; " ^
    "$xml.Save('%TEMP_XML%')"

:: Reimportar la tarea con el directorio de trabajo
schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
schtasks /create /tn "%TASK_NAME%" /xml "%TEMP_XML%" /f >nul 2>&1
del "%TEMP_XML%" >nul 2>&1

echo.
echo ========================================
echo  Servicio instalado correctamente.
echo ========================================
echo.
echo  - Se iniciara automaticamente al iniciar sesion en Windows.
echo  - Se ejecuta en segundo plano (sin ventana).
echo  - URL: http://localhost:19480/portfoliotracker/
echo.
echo  Comandos utiles:
echo    Iniciar ahora:     schtasks /run /tn "%TASK_NAME%"
echo    Detener:            schtasks /end /tn "%TASK_NAME%"
echo    Desinstalar:        ejecuta uninstall-service.bat
echo.

:: Preguntar si iniciar ahora
set /p START_NOW="Iniciar el servicio ahora? (S/N): "
if /i "%START_NOW%"=="S" (
    echo Iniciando Portfolio Tracker...
    schtasks /run /tn "%TASK_NAME%" >nul 2>&1
    timeout /t 5 /nobreak >nul
    start http://localhost:19480/portfoliotracker/
    echo Servicio iniciado. Navegador abierto.
)

echo.
pause

