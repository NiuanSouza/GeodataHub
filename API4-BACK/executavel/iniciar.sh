#!/usr/bin/env bash
# ==============================================================================
# Executável do Back-End API GEO com suporte a .env
# ==============================================================================
set -e

# Diretório base do executável
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Identificação e carregamento do arquivo .env
ENV_FILE=""
if [ -n "$1" ] && [ -f "$1" ] && [[ "$1" == *".env"* ]]; then
    ENV_FILE="$1"
    shift
elif [ -f "$SCRIPT_DIR/.env" ]; then
    ENV_FILE="$SCRIPT_DIR/.env"
elif [ -f "$SCRIPT_DIR/../.env" ]; then
    ENV_FILE="$SCRIPT_DIR/../.env"
elif [ -f "./.env" ]; then
    ENV_FILE="./.env"
fi

if [ -n "$ENV_FILE" ]; then
    echo "=========================================================="
    echo " [API GEO] Carregando configurações do .env: $ENV_FILE"
    echo "=========================================================="
    # Exporta variáveis do .env ignorando comentários e linhas em branco
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
else
    echo "=========================================================="
    echo " [API GEO] Nenhum arquivo .env detectado."
    echo " Utilizando configurações padrão ou variáveis de ambiente."
    echo "=========================================================="
fi

# 2. Resolução do comando Java (necessário Java 17+)
JAVA_CMD=""

# Prioridade 1: JAVA_HOME definido
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# Prioridade 2: java do PATH se for versão >= 17
if [ -z "$JAVA_CMD" ] && command -v java &> /dev/null; then
    SYS_JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
    if [ "$SYS_JAVA_VER" = "1" ]; then
        SYS_JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f2)
    fi
    if [ -n "$SYS_JAVA_VER" ] && [ "$SYS_JAVA_VER" -ge 17 ]; then
        JAVA_CMD="java"
    fi
fi

# Prioridade 3: Procurar instalações conhecidas do JDK 17
if [ -z "$JAVA_CMD" ]; then
    for candidate in \
        "/home/niuan/.jdks/jdk-17.0.21+9/bin/java" \
        "/usr/lib/jvm/java-17-openjdk-amd64/bin/java" \
        "/usr/lib/jvm/jdk-17/bin/java" \
        "$HOME/.jdks/"*/bin/java; do
        if [ -x "$candidate" ]; then
            JAVA_CMD="$candidate"
            export JAVA_HOME="$(dirname "$(dirname "$candidate")")"
            break
        fi
    done
fi

# Fallback se não encontrou Java 17+ específico
if [ -z "$JAVA_CMD" ]; then
    if command -v java &> /dev/null; then
        JAVA_CMD="java"
    else
        echo "[ERRO] Java não foi encontrado no PATH nem no sistema."
        echo "Por favor, instale o Java 17 ou configure JAVA_HOME."
        exit 1
    fi
fi

JAR_FILE="$SCRIPT_DIR/api-geo.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "[ERRO] Arquivo jar não encontrado: $JAR_FILE"
    exit 1
fi

echo "[API GEO] Usando JVM: $("$JAVA_CMD" -version 2>&1 | head -n 1)"
echo "[API GEO] Iniciando backend na porta: ${PORT:-${SERVER_PORT:-8080}}..."
echo "=========================================================="

exec "$JAVA_CMD" -jar "$JAR_FILE" "$@"
