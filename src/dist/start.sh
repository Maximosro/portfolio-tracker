#!/usr/bin/env bash
set -e

echo "========================================"
echo "  Portfolio Tracker - Iniciando..."
echo "========================================"
echo

# Crear carpeta data si no existe
mkdir -p data

# Buscar el JAR
JAR=$(ls -1 *.jar 2>/dev/null | head -n1)

if [ -z "$JAR" ]; then
    echo "ERROR: No se encontró el fichero .jar"
    echo "Asegúrate de que el .jar está en la misma carpeta que este script."
    exit 1
fi

# Sugerir alias local
if ! grep -q "portfolio.tracker" /etc/hosts 2>/dev/null; then
    echo ""
    echo "[INFO] Para acceder vía http://portfolio.tracker:19480"
    echo "       añade esta línea a /etc/hosts (requiere sudo):"
    echo "       127.0.0.1  portfolio.tracker"
    echo ""
fi

echo "Ejecutando: $JAR"
echo "Base de datos: data/portfolio.db"
echo ""
echo " URL: http://localhost:19480/portfoliotracker/"
echo ""
echo "Pulsa Ctrl+C para detener."
echo "========================================"

# Abrir navegador automáticamente tras un pequeño delay
(sleep 5 && open "http://localhost:19480/portfoliotracker/" 2>/dev/null || xdg-open "http://localhost:19480/portfoliotracker/" 2>/dev/null) &

java -jar "$JAR"
