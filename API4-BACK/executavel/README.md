# Executável do Backend API GEO

Este diretório contém o executável autônomo do backend Spring Boot com suporte nativo a arquivos de configuração `.env`.

---

## 📁 Arquivos Inclusos

- **`api-geo`** / **`iniciar.sh`**: Script executável para Linux / macOS que carrega o `.env` e inicia a aplicação com a JVM adequada (Java 17+).
- **`api-geo.jar`**: JAR totalmente executável (`Spring Boot fully executable jar`). Pode ser executado via `./api-geo` ou `java -jar api-geo.jar`.
- **`api-geo.conf`**: Configurações de inicialização do JAR nativo (reconhece JDK 17 e `.env`).
- **`api-geo.bat`**: Script executável para sistemas Windows.
- **`.env`**: Arquivo de configuração de ambiente ativo.
- **`.env.example`**: Modelo com todas as variáveis suportadas e documentadas.

---

## 🚀 Como Executar

### 1. No Linux / macOS

Basta abrir o terminal nesta pasta e executar:

```bash
./api-geo
```

Ou:

```bash
./iniciar.sh
```

Ou diretamente o arquivo JAR:

```bash
./api-geo.jar
# ou
java -jar api-geo.jar
```

Você também pode passar um arquivo `.env` específico como primeiro argumento:

```bash
./api-geo .env.producao
```

Ou passar parâmetros do Spring Boot diretamente na linha de comando:

```bash
./api-geo --server.port=9090
```

---

### 2. No Windows

Dê um duplo clique em `api-geo.bat` ou execute no terminal CMD / PowerShell:

```cmd
api-geo.bat
```

---

## ⚙️ Configurações Suportadas no `.env`

O executável aceita tanto o formato simples (`DB_HOST`, `PORT`, etc.) quanto as variáveis oficiais do Spring Boot (`SPRING_DATASOURCE_URL`, `SERVER_PORT`, etc.):

```env
# Porta HTTP
PORT=8080

# Banco de dados PostgreSQL / PostGIS
DB_HOST=localhost
DB_PORT=5432
DB_NAME=sistema_geo
DB_USER=root
DB_PASSWORD=root

# Ou URL JDBC completa:
# SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sistema_geo
# SPRING_DATASOURCE_USERNAME=root
# SPRING_DATASOURCE_PASSWORD=root

# Hibernate / Flyway
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_FLYWAY_ENABLED=true
SPRING_JPA_SHOW_SQL=true

# Upload de arquivos
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=5GB
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=5GB

# Prefixo da API
SPRING_MVC_SERVLET_PATH=/api
```

---

## 📋 Pré-requisitos

- Java 17 ou superior instalado no sistema (ou configurado na variável de ambiente `JAVA_HOME`).
- Banco de dados PostgreSQL com extensão PostGIS em execução (ex: via `docker compose up -d`).
