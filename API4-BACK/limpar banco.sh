#!/bin/bash
set -e

# Garante a execução a partir do diretório onde está o docker-compose.yml
cd "$(dirname "$0")"

echo "================================================="
echo "  Limpando e recriando o banco de dados Docker  "
echo "================================================="

echo "1. Parando contêineres e excluindo volumes (pg_data)..."
if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    docker compose down -v
elif command -v docker-compose &> /dev/null; then
    docker-compose down -v
else
    echo "Erro: Docker Compose não encontrado."
    exit 1
fi

echo "2. Subindo novo contêiner com banco de dados limpo..."
if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    docker compose up -d
else
    docker-compose up -d
fi

echo "3. Aguardando o PostgreSQL inicializar e aceitar conexões..."
MAX_ATTEMPTS=30
ATTEMPT=0
until docker exec postgres_postgis_api_geo pg_isready -U root -d sistema_geo &> /dev/null || [ $ATTEMPT -ge $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    sleep 1
done

if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
    echo "Aviso: O contêiner subiu, mas o PostgreSQL ainda pode estar inicializando."
else
    echo "================================================="
    echo "  Banco de dados limpo e pronto na porta 5432!   "
    echo "================================================="
fi

