# API4 Front-end - GeodataHub

> **🔗 Acesse a plataforma em produção:** [https://niuansouza.duckdns.org/geodatahub/](https://niuansouza.duckdns.org/geodatahub/)

Este é o repositório Front-end do sistema **API4 / GeodataHub**, uma plataforma de acompanhamento, ingestão e processamento de cargas de dados espaciais e analíticos.

O sistema permite que operadores façam uploads de dados (como conjuntos geográficos ou safras), acompanhem a saúde do processamento automático do back-end através de um pipeline estruturado (Ingestão -> Validação -> Tratamento -> Publicação) e atuem como auditores em registros retidos (Quarentena).

---

## 🛠️ Tecnologias Utilizadas

- **[Vue 3](https://vuejs.org/)** (Composition API via `<script setup>`)
- **[Vite](https://vitejs.dev/)** (Build tool e Dev server ultra-rápido)
- **[TypeScript](https://www.typescriptlang.org/)** (Tipagem estática)
- **[Vue Router](https://router.vuejs.org/)** (Roteamento da SPA)
- **[Vitest](https://vitest.dev/)** (Testes unitários)
- **[pnpm](https://pnpm.io/)** (Gerenciador de pacotes rápido e eficiente)

---



## 🚀 Instalação e Execução

Este projeto utiliza estritamente o **pnpm**. Instale-o caso não possua (`npm install -g pnpm`).

### 1. Instalar Dependências
```bash
pnpm install
```

### 2. Rodar o Servidor de Desenvolvimento (Hot-Reload)
```bash
pnpm dev
```
O projeto estará rodando localmente (normalmente em `http://localhost:5173/`).

### 3. Fazer o Build para Produção
```bash
pnpm build
```

---

## 🧪 Testes

### Testes Unitários (Vitest)
Executam as baterias de testes da pasta `services/__tests__/` garantindo a saúde dos adaptadores e transformadores de dados.

```bash
pnpm test:unit
```
*(Para rodar apenas uma vez sem o modo "watch", use `pnpm test:unit --run`)*

### Testes End-to-End (Playwright)
```bash
pnpm test:e2e
```

---

## 🛠️ Configuração Recomendada de IDE

- **VS Code** com a extensão **Vue (Official) / Volar**
- Desabilite a extensão Vetur caso a possua.
