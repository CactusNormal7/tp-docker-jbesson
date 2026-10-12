# TD2 - Mesures et réponses

Stack : Node (TypeScript + Express, port 3000).

| Version | Taille | Build à froid | Rebuild (1 ligne modifiée) | .env dans l'image ? | Utilisateur |
|---|---|---|---|---|---|
| v1 | 1.24GB | 2.7s | 1.7s | oui | root |
| v2 | 1.24GB | 1.7s | 1.0s | oui | root |
| v3 | 1.18GB | 1.5s | 0.7s | non | root |
| v4 | 170MB | 2.8s | 0.9s | non | node |

Commandes :

```bash
time docker build --no-cache -t td2:v1 .
time docker build -t td2:v1 .
docker image ls td2
docker run --rm td2:v1 ls -la
docker run --rm td2:v1 id
```

Le rebuild est mesuré après modification du message par défaut dans src/server.ts.

## Étape 1

```bash
docker run -d -p 3000:3000 td2:v1
curl localhost:3000/
```

```
{"message":"Hello Docker","version":"dev","hostname":"2607465443ac"}
```

### Q1

Le .env est dans l'image :

```
$ docker run --rm td2:v1 cat .env
API_KEY=sk-live-ne-doit-jamais-finir-dans-une-image
```

`COPY . .` copie tout le dossier. Toute personne ayant l'image peut lire la clé, et un push sur un registry la rend accessible.

## Étape 2

### Q2

package.json et package-lock.json sont copiés en premier, puis `npm ci`, puis le reste du code. Une modification du code ne touche pas la couche npm ci, qui reste en cache :

```
#8 [4/6] RUN npm ci
#8 CACHED
```

En v1, `RUN npm ci && npm run build` est refait à chaque modification.
Si package.json change, le cache est invalidé à partir de cette ligne et npm ci est refait.

## Étape 3

### Q3

Exclus :
- .env, .env.* : secrets
- node_modules : réinstallé dans l'image par npm ci
- dist : recompilé dans l'image
- .git, .gitignore : historique et config git, inutiles dans l'image
- *.md, Dockerfile*, .dockerignore : documentation et fichiers de build, inutiles à l'exécution
- .DS_Store, npm-debug.log* : fichiers système et logs
- java : autre version de l'API

transferring context :
- sans .dockerignore (v1) : 57.66MB
- avec .dockerignore (v3) : 814B

node_modules et les autres fichiers exclus ne sont plus envoyés au build.

## Étape 4

### Q4

Absents de l'image finale :
- le compilateur TypeScript
- les devDependencies (@types/node, @types/express)
- les sources src/ et tsconfig.json (seul dist/ est copié)
- le cache npm

## Étape 5

```bash
docker run -d --name td2-a -p 3001:3000 -e MESSAGE="Bonjour depuis A" -e APP_VERSION=1.0.0 td2:v4
docker run -d --name td2-b -p 3002:3000 -e MESSAGE="Bonjour depuis B" -e APP_VERSION=1.0.0 td2:v4
```

```
$ curl localhost:3001/
{"message":"Bonjour depuis A","version":"1.0.0","hostname":"bbab8dfaed35"}
$ curl localhost:3002/
{"message":"Bonjour depuis B","version":"1.0.0","hostname":"580ac65637ec"}
```

### Q5

La même image doit tourner dans tous les environnements (test, prod). Reconstruire pour changer la config produit une nouvelle image non testée. La config dépend de l'environnement, elle se passe au lancement.
