FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# As VITE_* sao lidas na COMPILACAO, nao em tempo de execucao: o Vite troca
# import.meta.env.VITE_X pelo literal dentro do bundle. Sem estes ARG/ENV, uma
# variavel definida no painel do Railway nao chega ate o `npm run build` e o
# valor sai `undefined` no bundle — o Sentry nunca inicia e o Turnstile fica no
# escape hatch, os dois em silencio e parecendo configuracao errada do painel.
# Vazias por padrao: e o que mantem o build local funcionando sem nada setado.
ARG VITE_SENTRY_DSN=""
ARG VITE_SENTRY_ENV=""
ARG VITE_TURNSTILE_SITE_KEY=""
ARG VITE_META_PIXEL_ID=""
ENV VITE_SENTRY_DSN=$VITE_SENTRY_DSN \
    VITE_SENTRY_ENV=$VITE_SENTRY_ENV \
    VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY \
    VITE_META_PIXEL_ID=$VITE_META_PIXEL_ID

RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html

# ALTERADO: Copia para a pasta de templates para o Nginx injetar a porta do Railway
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Fora de templates/ de proposito: nao precisa de envsubst e o caminho fica fixo
# para os `include` do default.conf.
COPY security-headers.conf /etc/nginx/security-headers.conf

# REMOVIDO: O "EXPOSE 80" foi retirado porque a porta agora é dinâmica
