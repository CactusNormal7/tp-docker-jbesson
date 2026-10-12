# TD1 - Réponses

Jules Besson

## Partie A

### A1

```bash
docker run hello-world
docker run hello-world
docker ps -a
```

1ere exécution : l'image n'est pas présente en local, elle est téléchargée avant le lancement.

```
Unable to find image 'hello-world:latest' locally
latest: Pulling from library/hello-world
58dee6a49ef1: Pull complete
Status: Downloaded newer image for hello-world:latest

Hello from Docker!
```

2eme exécution : l'image est déjà en local, le message s'affiche directement.

`docker ps -a` liste 2 conteneurs (jovial_turing, fervent_napier) en Exited (0) : chaque docker run crée un nouveau conteneur.

### A2

```bash
docker run -d --name web1 -p 8080:80 nginx:1.29-alpine
docker run -d --name web2 -p 8081:80 nginx:1.29-alpine
```

Pas de conflit : chaque conteneur a son propre réseau isolé, donc son propre port 80. Seul le port de l'hôte (8080, 8081) doit être unique.

Publication de web2 sur 8080 :

```
Bind for 0.0.0.0:8080 failed: port is already allocated
```

Le port 8080 de l'hôte est déjà utilisé par web1.

### A3

```bash
docker logs web1
docker logs -f web1
```

```
192.168.215.1 - - [12/Oct/2026:20:39:53 +0000] "GET / HTTP/1.1" 200 896 "-" "curl/8.7.1" "-"
```

Ce sont les logs d'accès de nginx, une ligne par requête. Nginx écrit sur sa sortie standard, Docker la capture. -f suit les logs en continu.

### A4

```bash
docker exec web1 sh -c 'echo Jules > /usr/share/nginx/html/index.html'
docker rm -f web1
docker run -d --name web1 -p 8080:80 nginx:1.29-alpine
```

Après le exec, la page affiche "Jules". Après suppression et relance, la page affiche "Welcome to nginx!".

La modification était dans la couche d'écriture du conteneur, supprimée avec lui. L'image n'a pas changé. docker exec sert à déboguer, pas à modifier une application.

## Partie B

### B1

```bash
docker run --rm -e PRENOM=Jules alpine printenv PRENOM
docker run --rm alpine printenv PRENOM
echo $PRENOM
```

- avec -e : Jules
- sans -e : rien, code de retour 1
- echo $PRENOM sur l'hôte : vide

La variable existe uniquement dans l'environnement du conteneur, ni dans l'image ni sur l'hôte.

### B2

```bash
docker run --rm -it alpine sh
apk add curl
curl --version
exit
docker run --rm -it alpine sh
curl --version
```

1er conteneur : `OK: 13.5 MiB in 25 packages` puis `curl 8.22.0`.
2eme conteneur : `sh: curl: not found`.

curl a été installé dans le conteneur, pas dans l'image. Avec --rm le conteneur est supprimé à la sortie, le suivant repart de l'image alpine d'origine.

## Partie C

### C1

```bash
docker pull node:24 && docker pull node:24-slim && docker pull node:24-alpine
docker image ls node
docker run --rm node:24 sh -c 'ls /usr/bin | wc -l'
docker run --rm node:24 which gcc git curl
```

| Image | Taille | Commandes | gcc / git / curl |
|---|---|---|---|
| node:24 | 1.14GB | 664 | oui |
| node:24-slim | 249MB | 273 | non |
| node:24-alpine | 166MB | 143 | non |

Les 3 images ont le même Node (v24.21.0). node:24 contient en plus des outils de compilation et système (gcc, git, curl...). Ils servent à construire, pas à faire tourner une API.

### C2

```bash
docker history node:24-alpine
```

9 couches. La plus lourde (152MB) : `RUN /bin/sh -c addgroup -g 1000 node && ...`, qui installe Node.

### C3

```bash
docker image inspect nginx:1.29-alpine | grep -A4 Cmd
```

```
"Cmd": [
    "nginx",
    "-g",
    "daemon off;"
],
```

Commande au démarrage : `nginx -g "daemon off;"`. ExposedPorts : 80/tcp. Cohérent avec le -p 8080:80 de A2.

## Partie D

### D1

Observation : `docker run -d alpine` rend la main, `docker ps` est vide, `docker ps -a` affiche le conteneur en Exited (0).

Explication : le Cmd de alpine est `["/bin/sh"]`. Sans terminal, le shell se termine immédiatement, donc le conteneur s'arrête.

Correction : `docker run -d --name d1fix alpine sleep 1000`. Le conteneur reste en Up.

### D2

Observation : le conteneur tourne, `curl localhost:9082` renvoie `curl: (52) Empty reply from server`.

Explication : -p 9082:8080 redirige vers le port 8080 du conteneur, nginx écoute sur le 80.

Correction : publier vers le port 80 du conteneur (`-p 9083:80`, testé sur 9083) : réponse HTTP 200.

### D3

```bash
docker run -d --name dormeur alpine sleep 1000
time docker stop dormeur
docker ps -a --filter name=dormeur
```

```
docker stop dormeur  0.01s user 0.01s system 0% cpu 10.146 total
Exited (137)
```

Durée : 10 secondes. Code de sortie : 137.

Docker envoie SIGTERM au processus, sleep ne réagit pas. Après 10 secondes Docker envoie SIGKILL. 137 = 128 + 9 (SIGKILL).

Bonus avec --init : arrêt en 0.097s, code 143 (128 + 15, SIGTERM). Le processus init transmet le SIGTERM à sleep, qui s'arrête immédiatement.

### D4

```bash
docker run --name gourmand --memory 50m node:24-alpine \
  node -e "const a=[]; while(true) a.push(new Array(1e6).fill(1))"
docker inspect gourmand
```

Le conteneur est tué, code de sortie 137.

```
"OOMKilled": true,
"ExitCode": 137,
```

Le conteneur dépasse la limite de 50 Mo. Mécanisme : les cgroups limitent la mémoire, l'OOM killer du noyau tue le processus.

## Partie E

```bash
docker system df
docker rm -f web1 web2 d1fix f56b4c2f37b4 ce3b143b6d15
docker container prune -f
docker system df
```

Avant :

```
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          31        4         3.222GB   1.715GB (53%)
Containers      11        5         1.832MB   745.6kB (40%)
```

`docker container prune -f` : `Total reclaimed space: 745.6kB`

Après :

```
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          31        0         3.222GB   1.776GB (55%)
Containers      0         0         0B        0B
```
