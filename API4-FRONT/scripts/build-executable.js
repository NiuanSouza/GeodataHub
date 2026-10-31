import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(__dirname, '..')
const outputDir = path.join(rootDir, 'executavel')

console.log('--- [1/5] Compilando frontend Vue (Vite) ---')
execSync('npm run build-only', { cwd: rootDir, stdio: 'inherit' })

console.log('\n--- [2/5] Gerando estrutura de assets embutidos ---')
execSync('node scripts/generate-assets.js', { cwd: rootDir, stdio: 'inherit' })

if (!fs.existsSync(outputDir)) {
  fs.mkdirSync(outputDir, { recursive: true })
}

const exePath = path.join(outputDir, 'frontend')
const serverCpp = path.join(__dirname, 'server.cpp')
const embeddedCpp = path.join(__dirname, 'dist_embedded.cpp')

console.log('\n--- [3/5] Compilando executavel nativo Linux (g++) ---')
const compileCmd = `g++ -O2 -std=c++17 -pthread "${serverCpp}" "${embeddedCpp}" -o "${exePath}"`
console.log(`Executando: ${compileCmd}`)
execSync(compileCmd, { cwd: rootDir, stdio: 'inherit' })

fs.chmodSync(exePath, 0o755)
console.log(`Executável gerado com sucesso em: ${exePath}`)

console.log('\n--- [4/5] Criando arquivo .env.example e documentação em /executavel ---')
const envExampleContent = `# =======================================================
# Configuracao do Servidor Frontend GeodataHub
# =======================================================

# Porta onde o servidor frontend ira rodar (padrao: 3000)
PORT=3000

# Host de escuta (padrao: 0.0.0.0 para aceitar conexoes externas, ou 127.0.0.1 para local)
HOST=0.0.0.0

# URL base da API REST Backend (injetada no axios e na aplicacao)
VITE_API_BASE_URL=http://localhost:8080
`
fs.writeFileSync(path.join(outputDir, '.env.example'), envExampleContent, 'utf-8')

// Cria tambem um .env padrao se nao existir no executavel
const envDefaultPath = path.join(outputDir, '.env')
if (!fs.existsSync(envDefaultPath)) {
  fs.writeFileSync(envDefaultPath, envExampleContent, 'utf-8')
}

const readmeContent = `# Executável do Frontend GeodataHub

Este diretório contém o executável autossuficiente (standalone) do Frontend do GeodataHub para Linux.

## Características
- **100% Autossuficiente**: Todos os arquivos do frontend (HTML, JS, CSS, imagens) estão embutidos no binário. Não necessita de Node.js, npm, nginx ou arquivos externos para rodar.
- **Suporte a .env em Tempo de Execução**: Lê automaticamente as variáveis de ambiente do arquivo \`.env\` sem precisar recompilar a aplicação.
- **Roteamento SPA Completo**: Suporta o HTML5 History Mode do Vue Router (todas as rotas carregam normalmente sem erro 404).

---

## Como Usar

### 1. Configurar o arquivo .env
Edite ou crie o arquivo \`.env\` na mesma pasta do executável:

\`\`\`env
PORT=3000
HOST=0.0.0.0
VITE_API_BASE_URL=http://localhost:8080
\`\`\`

### 2. Iniciar o Executável
Basta executar o binário no terminal:

\`\`\`bash
./frontend
\`\`\`

Acesse no navegador: **http://localhost:3000**

---

## Opções de Linha de Comando

| Opção | Descrição | Exemplo |
|---|---|---|
| \`-p, --port <porta>\` | Define a porta HTTP (sobrescreve o .env) | \`./frontend -p 8080\` |
| \`-h, --host <ip>\` | Define a interface de rede | \`./frontend -h 127.0.0.1\` |
| \`-e, --env <caminho>\` | Especifica um arquivo .env personalizado | \`./frontend --env /etc/app.env\` |
| \`-d, --dist <pasta>\` | Permite sobrescrever os arquivos estáticos por uma pasta em disco | \`./frontend -d ./minha-dist\` |
| \`-v, --version\` | Exibe a versão | \`./frontend -v\` |
| \`--help\` | Exibe a ajuda detalhada | \`./frontend --help\` |

---

## Ordem de Prioridade do .env
1. Caminho passado explicitamente via flag \`--env <arquivo>\`
2. Arquivo \`.env\` no diretório onde o comando foi chamado (\`./.env\`)
3. Arquivo \`.env\` na mesma pasta do executável (\`<pasta_do_binario>/.env\`)
4. Variáveis de ambiente do sistema operacional (\`export VITE_API_BASE_URL=...\`)
`
fs.writeFileSync(path.join(outputDir, 'README.md'), readmeContent, 'utf-8')

console.log('\n--- [5/5] Validando executável gerado ---')
const helpOutput = execSync(`"${exePath}" --help`).toString()
console.log(helpOutput)

console.log('\n======================================================')
console.log(' Build concluído com sucesso!')
console.log(` Executável pronto em: ${exePath}`)
console.log('======================================================')
