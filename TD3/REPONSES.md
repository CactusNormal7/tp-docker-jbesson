# TD3 - Réponses (B et C)

Prérequis : `cp .env.example .env` puis définir un mot de passe dans .env.

Partie B : commandes lancées avec `docker compose -f compose.yaml` pour ignorer compose.override.yaml.

## B1

```bash
docker compose -f compose.yaml config
```

Extrait (mot de passe masqué) :

```
      DB_NAME: visites
      DB_PASSWORD: XXXX
      DB_USER: visites
...
      POSTGRES_DB: visites
      POSTGRES_PASSWORD: XXXX
      POSTGRES_USER: visites
```

Les variables du .env sont bien interpolées.

## B2

Lancement sans restart (désactivé via un fichier norestart.yaml contenant `restart: "no"` pour api) :

```bash
docker compose -f compose.yaml -f norestart.yaml up -d --build
docker compose -f compose.yaml ps -a
docker compose -f compose.yaml logs api
```

```
api       Exited (1) 4 seconds ago
cache     Up 5 seconds
db        Up 5 seconds

api-1  | Connecting to Postgres at db:5432…
api-1  | Error: connect ECONNREFUSED 192.168.97.2:5432
```

Le nom db est résolu mais la connexion est refusée : au premier lancement Postgres initialise la base pendant quelques secondes, l'API tente de se connecter avant que Postgres soit prêt.

depends_on ne suffit pas : il attend que le conteneur db soit démarré, pas que Postgres soit prêt à accepter des connexions.

Avec restart: on-failure :

```bash
docker compose -f compose.yaml down -v
docker compose -f compose.yaml up -d
docker inspect td3-api-1 --format 'RestartCount={{.RestartCount}}'
docker compose -f compose.yaml logs api
```

```
RestartCount=3

api-1  | Connecting to Postgres at db:5432…
api-1  | Error: connect ECONNREFUSED 192.168.97.3:5432
api-1  | Connecting to Postgres at db:5432…
api-1  | Error: connect ECONNREFUSED 192.168.97.3:5432
api-1  | Connecting to Postgres at db:5432…
api-1  | Error: connect ECONNREFUSED 192.168.97.3:5432
api-1  | Connecting to Postgres at db:5432…
api-1  | Connecting to Redis at redis://cache:6379…
api-1  | Visites API listening on 3000
```

L'API plante 3 fois puis démarre quand Postgres est prêt.

C'est un contournement : l'application plante en boucle jusqu'à ce que la base soit prête, et une vraie erreur (mauvais mot de passe par exemple) provoquerait aussi des redémarrages en boucle.

## B3

1. Les données survivent à down puis up :

```
$ curl localhost:3000/
{"hitsInRedis":3,"visitsInPostgres":3,"servedBy":"8de1dac7d6ab"}

$ docker compose -f compose.yaml down
 Container td3-api-1 Removed
 Container td3-cache-1 Removed
 Container td3-db-1 Removed
 Network td3_default Removed

$ docker volume ls --filter name=td3
local     td3_pgdata
local     td3_redisdata

$ docker compose -f compose.yaml up -d
$ curl localhost:3000/
{"hitsInRedis":4,"visitsInPostgres":4,"servedBy":"bf1ee3943fbb"}
```

down supprime les conteneurs et le réseau, pas les volumes.

2. Les données disparaissent avec down -v :

```
$ docker compose -f compose.yaml down -v
 Volume td3_pgdata Removed
 Volume td3_redisdata Removed

$ docker compose -f compose.yaml up -d
$ curl localhost:3000/
{"hitsInRedis":1,"visitsInPostgres":1,"servedBy":"2c07f2dae763"}
```

Redis :

```
$ docker compose -f compose.yaml exec cache redis-cli CONFIG GET dir
dir
/data
```

Il faut monter un volume nommé sur /data, avec `--appendonly yes` pour que chaque écriture soit enregistrée sur disque. C'est en place dans compose.yaml, d'où le compteur Redis à 4 dans le test 1.

## B4

```
$ docker compose -f compose.yaml exec api getent hosts db
192.168.97.2      db  db

$ docker compose -f compose.yaml up -d --force-recreate db
$ docker compose -f compose.yaml exec api getent hosts db
192.168.97.2      db  db
```

L'IP n'a pas changé ici, mais ce n'est pas garanti : l'IP est attribuée à chaque création de conteneur et peut changer. Il faut utiliser le nom du service, que le DNS de Docker résout toujours vers l'IP actuelle.

## C1

compose.override.yaml :
- api construite avec la cible dev
- develop.watch : sync de ./app/src vers /app/src, rebuild si package.json ou package-lock.json change
- port de la base publié sur `${DB_HOST_PORT:-5432}`, configurable dans .env

```bash
docker compose up -d --build
docker compose ps
docker compose exec api ps -o args
```

```
api       Up 10 seconds   0.0.0.0:3000->3000/tcp
cache     Up 10 seconds   6379/tcp
db        Up 10 seconds   0.0.0.0:5432->5432/tcp

node --watch src/server.js
```

Base accessible depuis l'hôte :

```
$ nc -z localhost 5432
Connection to localhost port 5432 [tcp/postgresql] succeeded!
```

Lancement de `docker compose watch`, puis modification de /health dans app/src/server.js :

```js
app.get('/health', (req, res) => res.json({ status: 'UP', mode: 'dev' }));
```

```
Watch enabled
Syncing service "api" after 2 changes were detected

api-1  | Change detected in '/app/src/server.js'
api-1  | Restarting 'src/server.js'
api-1  | Visites API listening on 3000
```

```
$ curl localhost:3000/health
{"status":"UP","mode":"dev"}
```

Avant la modification : `{"status":"UP"}`. L'image du conteneur est identique avant et après (`docker inspect td3-api-1 --format '{{.Image}}'` renvoie sha256:d8efdc668eed... dans les deux cas) : pas de rebuild.

## C2

Sans --build :

```bash
docker compose -f compose.yaml up -d
docker compose -f compose.yaml exec api ps -o args
docker compose -f compose.yaml exec api id
curl localhost:3000/health
```

```
node --watch src/server.js
uid=0(root) gid=0(root)
{"status":"UP"}
```

L'image de dev est toujours utilisée (node --watch, root, ancien code).

Avec --build :

```bash
docker compose -f compose.yaml up -d --build
```

```
node src/server.js
uid=1000(node) gid=1000(node)
{"status":"UP","mode":"dev"}
```

Sans --build, Compose réutilise l'image td3-api existante. Dev et prod produisent une image du même nom, Compose ne détecte pas le changement de cible. Il faut ajouter --build à chaque changement de configuration.
