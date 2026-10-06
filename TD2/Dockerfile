# v4 — multi-stage, cache ordonné, non-root

# ---- Étape 1 : build (toutes les dépendances + compilateur TypeScript) ----
FROM node:24.21.0-alpine3.24 AS build
WORKDIR /app
# Les manifestes d'abord : la couche npm ci reste en cache tant qu'ils ne changent pas
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# ---- Étape 2 : exécution (dépendances de prod + dist/ uniquement) ----
FROM node:24.21.0-alpine3.24
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist
USER node
EXPOSE 3000
CMD ["node", "dist/server.js"]
