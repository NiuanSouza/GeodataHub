# =========================================================================
# SCRIPT AUTOMATIZADO DE CARGA GEOESPACIAL (GDAL / OGR2OGR)
# Objetivo: Ler Shapefiles/GeoJSONs e injetar na tabela camada_referencia
# =========================================================================

# 1. Configurações de Conexão com o PostgreSQL
# (Ajuste o user e password se a sua máquina local usar valores diferentes)
$DB_CONN = "PG:""host=localhost port=5432 dbname=sistema_geo user=postgres password=postgres"""

# 2. Variáveis de Entrada (Onde você vai alterar a cada arquivo novo)
# Troque os valores abaixo conforme a camada que você quer carregar
$ARQUIVO_FISICO   = "C:\Caminho\Para\O\Seu\Arquivo\malha_ibge_pr.geojson"
$NOME_DA_LAYER    = "malha_ibge_pr"          # O nome interno do layer no GeoJSON/Shapefile
$FONTE            = "IBGE"                   # Órgão de origem (ex: IBGE, INCRA)
$NOME_CAMADA      = "Malha Municipal PR"     # Nome amigável da camada
$COLUNA_ID_ORIGEM = "cd_mun"                 # Nome da coluna no arquivo que identifica o polígono

Write-Host "Iniciando a carga do arquivo: $ARQUIVO_FISICO" -ForegroundColor Cyan

# 3. Execução do GDAL com Dialeto PostgreSQL para empacotar o JSONB
# -nln "camada_referencia" : Tabela de destino
# -append                  : Adiciona os dados sem dropar a tabela
# -t_srs EPSG:4674         : Força a conversão para SIRGAS 2000 (SRID 4674)
# -nlt PROMOTE_TO_MULTI    : Converte Polygons simples em MultiPolygons (evita erros de tipagem)

ogr2ogr -f "PostgreSQL" $DB_CONN "$ARQUIVO_FISICO" `
  -nln "public.camada_referencia" -append -t_srs "EPSG:4674" -nlt PROMOTE_TO_MULTI `
  -dialect PostgreSQL `
  -sql "SELECT '$FONTE' AS fonte, '$NOME_CAMADA' AS nome_camada, CAST($COLUNA_ID_ORIGEM AS VARCHAR) AS identificador_origem, geometry AS geom, row_to_json(t)::jsonb AS atributos FROM ""$NOME_DA_LAYER"" t"

Write-Host "Carga concluída com sucesso!" -ForegroundColor Green