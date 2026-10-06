# TD2 — Mesures et réponses

Stack choisie : **Node 24 + TypeScript + Express** (port 3000).

## Tableau de mesures

| Version | Taille de l'image | Build à froid | Rebuild après modif d'**une ligne** de code | `.env` dans l'image ? | Utilisateur |
|---|---|---|---|---|---|
| v1 — naïve | 1.24 GB | 2.4 s | 2.1 s | **oui** | root (uid 0) |
| v2 — cache | 1.24 GB | 2.6 s | 1.2 s | **oui** | root (uid 0) |
| v3 — `.dockerignore` | 1.18 GB | 2.3 s | 0.9 s | non | root (uid 0) |
| v4 — multi-stage | **170 MB** | 2.9 s | 0.9 s | non | **node (uid 1000)** |

Conditions : Docker 29.4, images de base déjà téléchargées, connexion rapide (le `npm ci` ne prend que ~2 s,
d'où des temps de build courts). La modif d'une ligne = changer le message par défaut dans `src/server.ts`.

Commandes utilisées pour chaque version :

```bash
docker build --no-cache -f Dockerfile.v1 -t td2:v1 .   # build à froid
# modifier une ligne de src/server.ts, puis :
docker build -f Dockerfile.v1 -t td2:v1 .              # rebuild
docker image ls td2                                    # tailles
docker run --rm td2:v1 ls -la                          # fichiers présents dans /app (.env ?)
docker run --rm td2:v1 id                              # utilisateur
docker run --rm -p 3000:3000 td2:v1                    # puis : curl localhost:3000/
```

(v2 et v3 ont été construites avec des Dockerfiles intermédiaires ; seuls `Dockerfile.v1` et `Dockerfile` (v4) sont
rendus.)

---

## Étape 1 — Version naïve (`Dockerfile.v1`)

```dockerfile
FROM node:24
WORKDIR /app
COPY . .
RUN npm ci && npm run build
EXPOSE 3000
CMD ["node", "dist/server.js"]
```

```bash
$ curl localhost:3000/
{"message":"Hello Docker","version":"dev","hostname":"adc45249886a"}
```

**Q1. Le `.env` est-il dans l'image ? Pourquoi est-ce grave ?**

Oui : `COPY . .` copie tout le dossier, y compris `.env` (visible avec `docker run --rm td2:v1 ls -la`).
C'est grave même en local car :
- une image est faite de **couches lisibles par n'importe qui qui possède l'image** (`docker save`, `docker history`,
  `docker run … cat .env`) : le secret est en clair dedans ;
- l'image est faite pour **circuler** : un `docker push` vers un registre (Docker Hub, GitHub, CI…) et la clé fuite,
  potentiellement publiquement ;
- même supprimé dans une couche suivante (`RUN rm .env`), le fichier **reste dans la couche précédente** ;
- la clé doit alors être considérée comme compromise et **révoquée**.

Les secrets se passent à l'exécution (`-e`, `--env-file`, secrets Docker/Kubernetes), jamais au build.

## Étape 2 — Le cache

```dockerfile
FROM node:24
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
EXPOSE 3000
CMD ["node", "dist/server.js"]
```

**Q2. Pourquoi le rebuild est-il plus rapide ? Que se passe-t-il si on modifie `package.json` ?**

Docker réutilise une couche du cache tant que l'instruction **et les fichiers qu'elle copie** n'ont pas changé ;
dès qu'une couche est invalidée, **toutes les suivantes** sont reconstruites.
- En v1, `COPY . .` est avant `npm ci` : modifier une ligne de code invalide la copie, donc `npm ci` est refait.
- En v2, on copie d'abord **uniquement** `package.json` / `package-lock.json` : tant qu'ils ne changent pas, la couche
  `npm ci` vient du cache (`CACHED`), seuls `COPY . .` et `npm run build` sont rejoués (2.1 s → 1.2 s ; l'écart serait
  bien plus grand avec beaucoup de dépendances ou un réseau lent).

Si on modifie `package.json` (ou le lock), la couche `COPY package*.json` est invalidée, donc `npm ci` et tout ce qui
suit sont refaits — ce qui est voulu, puisque les dépendances ont changé.

## Étape 3 — `.dockerignore`

```
.env
.env.*
node_modules
dist
.git
.gitignore
.DS_Store
*.md
Dockerfile*
.dockerignore
java
npm-debug.log*
```

**Q3. Qu'avez-vous exclu, et pourquoi ? Qu'a changé `transferring context` ?**

| Ligne | Pourquoi |
|---|---|
| `.env`, `.env.*` | secrets : ne doivent jamais entrer dans une image (cf. Q1) |
| `node_modules` | réinstallé par `npm ci` dans l'image ; la copie locale (59 Mo, compilée pour macOS) écrasait celle de l'image au `COPY . .` |
| `dist` | artefact de build, recompilé dans l'image ; une version locale périmée pourrait s'y glisser |
| `.git`, `.gitignore` | historique et config git, inutiles et parfois sensibles |
| `.DS_Store`, `npm-debug.log*` | fichiers parasites du système / logs |
| `*.md`, `Dockerfile*`, `.dockerignore` | docs et fichiers de build, inutiles à l'exécution ; modifier le README ne casse plus le cache |
| `java` | l'autre version de l'app, non utilisée |

`transferring context` : **57.66 MB → 161 B**. Le client Docker n'envoie plus `node_modules`, `dist`, `.env`… au
démon : le build démarre plus vite, l'image ne contient plus que le nécessaire (1.24 GB → 1.18 GB) et le `.env` n'y est
plus.

## Étape 4 — Multi-stage (`Dockerfile`, image `td2:v4`)

```dockerfile
FROM node:24.21.0-alpine3.24 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:24.21.0-alpine3.24
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist
USER node
EXPOSE 3000
CMD ["node", "dist/server.js"]
```

Résultat : **170 MB** (contre 1.24 GB), utilisateur `node` (uid 1000), versions de base épinglées.

**Q4. Qu'est-ce qui est dans l'image de build et n'est plus dans l'image finale ?**

- le **compilateur TypeScript** (`typescript`) et les **devDependencies** (`@types/express`, `@types/node`) ;
- les **sources TypeScript** (`src/`) et `tsconfig.json` — seul `dist/` (le JS compilé) est copié ;
- le **cache npm** ;
- la base **Debian complète** de `node:24` (compilateurs gcc/make, python, git, curl… ~1 Go) remplacée par Alpine ;
- et l'app ne tourne plus en **root** mais en `node`.

## Étape 5 — Une image, plusieurs configurations

```bash
docker run -d --name td2-a -p 3001:3000 -e MESSAGE="Bonjour depuis A" -e APP_VERSION=1.0.0 td2:v4
docker run -d --name td2-b -p 3002:3000 -e MESSAGE="Bonjour depuis B" -e APP_VERSION=1.0.0 td2:v4

$ curl localhost:3001/
{"message":"Bonjour depuis A","version":"1.0.0","hostname":"4543dd744be1"}
$ curl localhost:3002/
{"message":"Bonjour depuis B","version":"1.0.0","hostname":"071b1df371ec"}
```

**Q5. Pourquoi ne pas reconstruire l'image pour changer le message ?**

- **Build once, run anywhere** : c'est la même image (même digest) qui est testée puis déployée en recette et en
  prod. Reconstruire produirait une image différente, non testée (dépendances qui bougent, etc.).
- La **configuration dépend de l'environnement**, pas du code (principe *12-factor* : config dans l'environnement).
  Une seule image sert N environnements / N instances.
- C'est **instantané** : changer une variable = redémarrer un conteneur, pas relancer un build et un push.
- Les **secrets** restent hors de l'image (cf. Q1).
