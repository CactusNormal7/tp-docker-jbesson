# TD3 - Partie A (à la main)

## A1

### 1. Image de l'API

```bash
cd TD3/app
docker build --target prod -t td3-api:prod .
docker images td3-api
```

```
IMAGE          ID             DISK USAGE
td3-api:prod   b667ec7abe00        179MB
```

### 2. Postgres

```bash
docker volume create td3-pgdata
docker run -d --name db \
  -e POSTGRES_DB=visites -e POSTGRES_USER=visites -e POSTGRES_PASSWORD=motdepasse \
  -v td3-pgdata:/var/lib/postgresql \
  postgres:18-alpine
docker logs db
```

```
LOG:  database system is ready to accept connections
```

### 3. Redis

```bash
docker run -d --name cache redis:8-alpine
```

### 4. API, premier essai sans réseau

```bash
docker run -d --name api -p 3000:3000 \
  -e DB_HOST=db -e DB_NAME=visites -e DB_USER=visites -e DB_PASSWORD=motdepasse \
  -e REDIS_URL=redis://cache:6379 \
  td3-api:prod
docker ps -a
docker logs api
```

```
api       Exited (1) 2 seconds ago

Error: getaddrinfo ENOTFOUND db
  code: 'ENOTFOUND',
  hostname: 'db'
```

L'API ne démarre pas : le nom "db" n'est pas résolu. Sur le réseau bridge par défaut, les conteneurs ne se joignent pas par leur nom.

Correction : créer un réseau avec `docker network create` et ajouter `--network td3-net` aux trois docker run.

### 5. Avec le réseau

```bash
docker rm -f api db cache
docker network create td3-net

docker run -d --name db --network td3-net \
  -e POSTGRES_DB=visites -e POSTGRES_USER=visites -e POSTGRES_PASSWORD=motdepasse \
  -v td3-pgdata:/var/lib/postgresql \
  postgres:18-alpine

docker run -d --name cache --network td3-net redis:8-alpine

docker run -d --name api --network td3-net -p 3000:3000 \
  -e DB_HOST=db -e DB_NAME=visites -e DB_USER=visites -e DB_PASSWORD=motdepasse \
  -e REDIS_URL=redis://cache:6379 \
  td3-api:prod

docker logs api
```

```
Connecting to Postgres at db:5432…
Connecting to Redis at redis://cache:6379…
Visites API listening on 3000
```

```
$ curl localhost:3000/
{"hitsInRedis":1,"visitsInPostgres":1,"servedBy":"379e85b7dc32"}
{"hitsInRedis":2,"visitsInPostgres":2,"servedBy":"379e85b7dc32"}
{"hitsInRedis":3,"visitsInPostgres":3,"servedBy":"379e85b7dc32"}
```

## A2

```bash
docker rm -f api db cache
```

Puis relance des trois docker run de l'étape 5.

```
$ curl localhost:3000/
{"hitsInRedis":1,"visitsInPostgres":4,"servedBy":"46d314eba0a1"}
```

Postgres : le compteur a survécu (4). Les données sont dans le volume td3-pgdata, qui n'est pas supprimé avec le conteneur. Les logs de db l'indiquent : `Database directory appears to contain a database; Skipping initialization`.

Redis : le compteur repart à 1. Aucun volume n'est monté, les données étaient dans le conteneur supprimé.
