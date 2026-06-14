FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html

# ALTERADO: Copia para a pasta de templates para o Nginx injetar a porta do Railway
COPY nginx.conf /etc/nginx/templates/default.conf.template

# REMOVIDO: O "EXPOSE 80" foi retirado porque a porta agora é dinâmica
