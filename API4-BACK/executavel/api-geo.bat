@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo ==========================================================
echo  [API GEO] Iniciando Back-End com suporte a .env
echo ==========================================================

REM 1. Carrega o .env se existir
if exist ".env" (
    echo [API GEO] Carregando variaveis do arquivo .env...
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        set "line=%%A"
        if not "!line:~0,1!"=="#" (
            if not "%%B"=="" (
                set "%%A=%%B"
            )
        )
    )
) else (
    echo [API GEO] Nenhum arquivo .env encontrado. Usando configuracoes padrao.
)

REM 2. Executa a aplicacao
if not exist "api-geo.jar" (
    echo [ERRO] api-geo.jar nao encontrado!
    pause
    exit /b 1
)

java -jar api-geo.jar %*
