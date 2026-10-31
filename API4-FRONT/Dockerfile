# ==============================================================================
# Dockerfile Multi-Stage para API4-FRONT (Vue 3 + Vite + Nginx)
# ==============================================================================
FROM node:20-alpine AS builder
WORKDIR /app

# Copia arquivos de dependência
COPY package*.json ./
RUN npm install --legacy-peer-deps

# Copia arquivos do código e compila o frontend
COPY . .
RUN npm run build-only

# ------------------------------------------------------------------------------
# Servidor Nginx de Alta Performance para servir os estáticos
# ------------------------------------------------------------------------------
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Remove arquivos default do nginx
RUN rm -rf ./*

# Copia arquivos estáticos compilados
COPY --from=builder /app/dist .

# Copia configuração customizada do Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

