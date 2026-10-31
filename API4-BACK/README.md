# API4 Back-end (API Geo)

Este é o repositório Back-end do sistema **API4**, uma plataforma de ingestão, validação topológica, cálculo territorial analítico e publicação de cargas de dados geoespaciais e tabulares.

O back-end orquestra todo o ciclo de vida dos arquivos recebidos através de um pipeline estruturado em 4 etapas: **Ingestão ➔ Validação ➔ Tratamento ➔ Publicação**. Em caso de inconformidades técnicas ou topológicas, o sistema realiza o desvio lateral controlado para **Quarentena**, permitindo auditoria, exportação de relatórios de rejeição e reprocessamento histórico.

---

## 🛠️ Tecnologias Utilizadas

- **[Java 17+](https://adoptium.net/)** (Linguagem de programação base)
- **[Spring Boot 3](https://spring.io/projects/spring-boot)** (Framework corporativo para APIs REST)
  - Spring MVC (Endpoints REST)
  - Spring Data JPA & Hibernate (Mapeamento objeto-relacional)
  - Spring Security (Autenticação baseada em roles: Operador, Auditor, Administrador)
  - Spring Async (`@EnableAsync` para processamento geoespacial em background)
- **[PostgreSQL](https://www.postgresql.org/)** com extensão **[PostGIS](https://postgis.net/)** (Banco de dados relacional e funções de análise espacial)
- **[Flyway](https://flywaydb.org/)** (Versionamento e execução automatizada de migrações DDL/DML)
- **[Jackson](https://github.com/FasterXML/jackson)** (Serialização e leitura streaming de GeoJSON/JSON)
- **[Maven](https://maven.apache.org/)** com Wrapper (`./mvnw`) (Gerenciador de dependências e build)
- **[Docker & Docker Compose](https://www.docker.com/)** (Provisionamento do banco PostGIS conteinerizado)

---

## 🚀 Instalação e Execução

### 1. Pré-requisitos
- JDK 17 ou superior instalado e configurado no `JAVA_HOME`.
- Docker e Docker Compose instalados (para executar o PostgreSQL com PostGIS).

### 2. Iniciar o Banco de Dados PostGIS
Na raiz deste repositório, execute o Docker Compose para subir a instância do banco PostGIS:
```bash
docker compose up -d
```
O banco estará disponível em `localhost:5432` (database: `sistema_geo`, user: `root`, password: `root`).

### 3. Compilar o Projeto
Para baixar as dependências e compilar as classes Java:
```bash
./mvnw clean compile
```

### 4. Executar a Aplicação Spring Boot
```bash
./mvnw spring-boot:run
```
A API iniciará no endereço local:
- **Base URL:** `http://localhost:8080/api`
- O Flyway executará automaticamente todas as migrações pendentes na inicialização.

> **Dica:** Você também pode utilizar o script utilitário `./iniciar-backend.sh` localizado na raiz do projeto geral.

---

## 🧪 Testes

### Compilar Testes
```bash
./mvnw test-compile
```

### Executar Testes Unitários e de Integração
```bash
./mvnw test
```

Para rodar apenas uma suíte ou classe específica de teste:
```bash
./mvnw test -Dtest=ConversorMultiFormatoTest
```

---

## 📂 Estrutura do Projeto

```text
API4-BACK/
├── pom.xml                               # Configuração Maven e dependências do projeto
├── mvnw / mvnw.cmd                       # Maven Wrapper executável
├── docker-compose.yml                    # Definição do container PostgreSQL 16 + PostGIS
├── zona_bruta_storage/                   # Armazenamento em disco dos arquivos enviados (/upload)
├── zona_processada_storage/              # Cóppias de trabalho padronizadas em SIRGAS 2000 (EPSG:4674)
└── src/
    ├── main/
    │   ├── java/com/bifrostconnect/api_geo/
    │   │   ├── config/                   # Configurações de Segurança, CORS e Flyway
    │   │   ├── controller/               # Endpoints REST (Cargas, Processos, Análise, Catálogo)
    │   │   ├── dto/                      # Data Transfer Objects (Requisições e Respostas)
    │   │   ├── entity/                   # Entidades JPA (Processo, Feição, Arquivo, Auditoria)
    │   │   ├── repository/               # Repositórios JPA e consultas PostGIS via JdbcTemplate
    │   │   └── service/                  # Regras de negócio, conversores, cálculo analítico e quarentena
    │   │       └── conversor/            # Conversores para Shapefile ZIP, GeoJSON, CSV, GPKG e KML
    │   └── resources/
    │       ├── application.properties    # Propriedades da aplicação e conexão do banco
    │       └── db/migration/             # Migrações SQL versionadas (V1 a V29)
    └── test/                             # Baterias de testes unitários e de integração
```

---

## 🛠️ Configuração Recomendada de IDE

- **VS Code** com o pacote **Extension Pack for Java** e **Spring Boot Extension Pack**.
- **IntelliJ IDEA** (Community ou Ultimate) abrindo a raiz do diretório `API4-BACK` como projeto Maven.
