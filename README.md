<div align="center">
  <img src="docs/img/banner.png" alt="GeodataHub Banner" width="100%">
</div>

# GeodataHub - GeoRural DataHub

> Plataforma corporativa de governança, ingestão, validação topológica e análise territorial de dados geoespaciais em larga escala.

**🔗 Acesse o projeto em produção:** [https://niuansouza.duckdns.org/geodatahub/](https://niuansouza.duckdns.org/geodatahub/)

**Aviso de Origem:** Este projeto é um *fork* e uma evolução independente do repositório original desenvolvido para o desafio proposto pela Visiona Tecnologia Espacial na FATEC. O projeto original pode ser consultado aqui: [Bifrost-Connect/GeoRural-DataHub](https://github.com/Bifrost-Connect/GeoRural-DataHub).

---

## 🚀 Visão Geral

O **GeodataHub** é uma solução completa para padronização e auditoria de dados geográficos e tabulares do agronegócio e meio ambiente (imóveis rurais do CAR, APPs, Reservas Legais, assentamentos do INCRA e alertas do MapBiomas/IBAMA).

A plataforma orquestra todo o fluxo operacional através de uma arquitetura orientada a pipelines em **4 camadas**:

1. **Ingestão:** Recebimento multi-formato de arquivos (Shapefile ZIP, GeoJSON, CSV, GPKG e KML) com checagem preliminar de integridade estrutural.
2. **Validação:** Verificação automática de regras de negócio e validações topológicas rigorosas (geometrias fechadas, auto-intersecções e projeções espaciais). Inconformidades são desviadas automaticamente para a área de **Quarentena**.
3. **Tratamento & Análise Territorial:** Reprojeção e normalização cartográfica para o padrão SIRGAS 2000 (EPSG:4674). Execução de cruzamentos espaciais avançados diretamente no PostgreSQL com PostGIS para cálculo de áreas de sobreposição, índices de conformidade ambiental e geração de versões consolidadas.
4. **Publicação:** Disponibilização dos conjuntos de dados validados em mapas interativos, catálogo de bases oficiais e exportação de relatórios auditáveis.

### ✨ Principais Funcionalidades

- **Pipeline Automatizado em 4 Etapas:** Acompanhamento visual em tempo real de cada fase do processamento (Ingestão ➔ Validação ➔ Tratamento ➔ Publicação).
- **Área de Quarentena:** Isolamento inteligente de registros que falharam nas regras topológicas, permitindo auditoria visual das inconsistências e reprocessamento controlado.
- **Painel do Gestor (Gate de Decisão):** Fluxo de aprovação ou rejeição técnica fundamentada com trilha completa de auditoria (*Linhagem do Dado*).
- **Mapas Interativos de Alta Performance:** Renderização de feições geográficas, camadas de restrição ambiental e polígonos rurais com MapLibre GL.
- **Histórico e Versionamento Fechado:** Rastreabilidade estrita de cada cálculo analítico, garantindo reprodução futura para fins de fiscalização e crédito rural.

---

## 📂 Estrutura do Repositório

O projeto é estruturado em um monorepo que separa a interface cliente do motor analítico de processamento:

- **/API4-FRONT**: Interface web responsiva e interativa desenvolvida com Vue 3 (Composition API), Vite, TypeScript, Pinia, Vue Router e MapLibre GL. [Veja o README do Front-End](./API4-FRONT/README.md)
- **/API4-BACK**: Motor de inteligência geoespacial e API RESTful construído em Java 17+, Spring Boot 3, Spring Data JPA, Flyway e PostgreSQL com PostGIS. [Veja o README do Back-End](./API4-BACK/README.md)
- **/deploy_config**: Metadados e configurações para integração automática no ecossistema e portfólio.
- **/docs**: Documentações técnicas adicionais, manuais e ativos visuais do projeto.

---

## 🛠 Como executar o projeto todo

### Opção 1: Via Docker Compose (Recomendado)

A forma mais rápida de subir o ecossistema completo (PostGIS + Back-End + Front-End):

1. Certifique-se de ter o **Docker** e o **Docker Compose** instalados na sua máquina.
2. Clone o repositório:
   ```bash
   git clone https://github.com/NiuanSouza/GeodataHub.git
   cd GeodataHub
   ```
3. Suba todos os serviços em segundo plano:
   ```bash
   docker compose up -d --build
   ```
4. As aplicações estarão disponíveis nos seguintes endereços:
   - **Front-End:** `http://localhost:5173` (ou porta configurada)
   - **Back-End API:** `http://localhost:8080/api`
   - **PostgreSQL / PostGIS:** `localhost:5432`

---

### Opção 2: Execução Manual / Desenvolvimento

Caso deseje executar as partes individualmente em ambiente de desenvolvimento:

#### 1. Banco de Dados (PostGIS)
Suba a instância do PostgreSQL com a extensão PostGIS ativa:
```bash
docker compose up -d database
```

#### 2. Back-End (Spring Boot)
1. Certifique-se de ter o **JDK 17+** e o **Maven** configurados.
2. Acesse o diretório do backend:
   ```bash
   cd API4-BACK
   ./mvnw clean compile
   ./mvnw spring-boot:run
   ```
3. A API inicializará em `http://localhost:8080/api` aplicando as migrações Flyway automaticamente.

#### 3. Front-End (Vue 3 + Vite)
1. Certifique-se de ter o **Node.js** e o gerenciador **pnpm** instalados.
2. Acesse o diretório do frontend:
   ```bash
   cd API4-FRONT
   pnpm install
   pnpm dev
   ```
3. Acesse `http://localhost:5173` no seu navegador.
