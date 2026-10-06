# TD1 — Réponses

## Partie A — Premiers conteneurs

### A1

Commandes : `docker run hello-world` (deux fois), puis `docker ps -a`.

- La première exécution n'a pas l'image en local : Docker affiche `Unable to find image 'hello-world:latest' locally` et la télécharge depuis Docker Hub (`Pulling from library/hello-world`), puis lance le conteneur. La seconde réutilise l'image déjà présente : pas de téléchargement, elle démarre immédiatement.
- Chaque `docker run` crée un **nouveau** conteneur : `docker ps -a` en montre deux, avec des noms aléatoires différents (ex. `romantic_fermi`, `boring_saha`), tous `Exited (0)`. L'image est partagée, pas les conteneurs.

### A2

```bash
docker run -d --name web1 -p 8080:80 nginx:1.29-alpine
docker run -d --name web2 -p 8081:80 nginx:1.29-alpine
```

- Pas de conflit car chaque conteneur a son propre espace réseau : les deux nginx écoutent sur le port 80 de **leur** conteneur. Seuls les ports publiés sur l'hôte (8080 et 8081) doivent être distincts.
- Publier `web2` aussi sur 8080 échoue : `Bind for 0.0.0.0:8080 failed: port is already allocated`. Un port de l'hôte ne peut être lié qu'à un seul conteneur.

### A3

```bash
docker logs web1
docker logs -f web1
```

Ce sont les logs d'accès nginx (`GET / HTTP/1.1" 200 ...`) : un par requête. Nginx écrit sur stdout/stderr dans le conteneur, et Docker capture ces flux ; `docker logs` les relit, `-f` les suit en continu.

### A4

```bash
docker exec web1 sh -c 'echo Jules > /usr/share/nginx/html/index.html'
docker rm -f web1
docker run -d --name web1 -p 8080:80 nginx:1.29-alpine
```

- Après la modification, le navigateur affiche « Jules ». Après suppression et relance, on retrouve la page « Welcome to nginx! » : la modification a disparu.
- Elle avait été écrite dans la couche d'écriture du conteneur, supprimée avec lui ; l'image n'a jamais changé. `docker exec` ne convient donc pas pour modifier une application durablement : c'est un changement manuel, non reproductible. Il faut passer par l'image (Dockerfile) ou un volume / bind mount.

## Partie B — Variables d'environnement et mode interactif

### B1

Commandes :

```bash
docker run --rm -e PRENOM=Jules alpine printenv PRENOM
docker run --rm alpine printenv PRENOM
echo $PRENOM
```

- Avec `-e` : affiche `Jules` (code de sortie 0).
- Sans `-e` : n'affiche rien, `printenv` renvoie le code 1 car la variable n'existe pas.
- `echo $PRENOM` sur ma machine : ligne vide, la variable n'existe pas sur l'hôte.
- Une variable passée avec `-e` vit dans l'environnement du processus du conteneur (config du conteneur) : elle n'est ni dans l'image, ni sur l'hôte, et disparaît avec le conteneur.

### B2

Commandes :

```bash
docker run --rm -it alpine sh
# dans le conteneur
apk add curl
curl --version
exit

docker run --rm -it alpine sh
# dans le conteneur
curl --version
exit
```

- Après `apk add curl`, `curl --version` fonctionne dans le premier conteneur.
- Après avoir quitté et relancé la même commande, `curl` n'est plus là (`curl: not found`).
- L'installation avait été faite dans le conteneur (sa couche d'écriture), pas dans l'image `alpine`, qui reste en lecture seule et inchangée. Avec `--rm`, le conteneur est supprimé à la sortie et son installation disparaît avec lui ; le second conteneur repart de l'image d'origine.
- Pour l'installer durablement, il faudra l'inclure dans l'image avec un Dockerfile (`RUN apk add curl`).

## Partie C — Images et couches

### C1

Commandes :

```bash
docker pull node:24 && docker pull node:24-slim && docker pull node:24-alpine
docker image ls node
docker run --rm <image> sh -c 'ls /usr/bin | wc -l'
docker run --rm <image> which gcc git curl
```

| Image | Taille | Nb de commandes (`/usr/bin`) | gcc / git / curl |
|---|---|---|---|
| `node:24` | 1,14 GB | 664 | tous présents |
| `node:24-slim` | 249 MB | 273 | aucun |
| `node:24-alpine` | 166 MB | 143 | aucun |

- Les trois font tourner le même Node 24. La grosse image (`node:24`) contient en plus une chaîne de compilation et des outils système : gcc, git, curl et des centaines d'autres commandes.
- Ces outils ne sont pas utiles pour **faire tourner** une API : Node seul suffit. Ils servent à construire l'application (compiler des modules natifs, cloner, télécharger). En production, ils alourdissent l'image et élargissent la surface d'attaque, donc on préfère `slim` ou `alpine`.

### C2

```bash
docker history node:24-alpine
```

- 9 couches listées (dont 5 à 0 B : `CMD`, `ENTRYPOINT`, `ENV` ×2, `CMD /bin/sh`, qui ne sont que des métadonnées). Celles qui ajoutent réellement des fichiers : le rootfs Alpine (8,66 MB), la création de l'utilisateur et l'installation de Node (152 MB), `apk add` des dépendances (5,37 MB) et le `COPY` de l'entrypoint (388 B).
- La plus lourde (152 MB) vient de l'instruction `RUN /bin/sh -c addgroup -g 1000 node && ...`, qui crée l'utilisateur `node` et installe Node.js lui-même.

### C3

```bash
docker image inspect nginx:1.29-alpine | grep -A4 -E '"Cmd"|ExposedPorts'
```

- `Cmd` : `["nginx", "-g", "daemon off;"]` : nginx démarre au premier plan (sans daemon), pour que le conteneur reste vivant.
- `ExposedPorts` : `80/tcp`.
- C'est cohérent avec A2 : j'ai publié `-p 8080:80`, donc le port 80 du conteneur vers 8080 sur l'hôte. `EXPOSE` est seulement une indication ; c'est `-p` qui publie réellement.

## Partie D — Énigmes

### D1

- **Observation** : `docker run -d alpine` rend la main, mais `docker ps` ne montre rien (une fois le conteneur terminé). `docker ps -a` le montre en `Exited (0)`.
- **Explication** : le `Cmd` de l'image `alpine` est `["/bin/sh"]` (vu via `docker image inspect alpine`). Sans terminal ni entrée standard, le shell lit une entrée vide et se termine aussitôt ; or un conteneur ne vit que tant que son processus principal tourne.
- **Correction** : donner au conteneur un processus qui dure, par exemple `docker run -d alpine sleep 1000`, ou garder un shell avec `docker run -it alpine`.

### D2

- **Observation** : `docker run -d -p 9082:8080 nginx:1.29-alpine` : le conteneur tourne, mais `curl localhost:9082` échoue (connexion réinitialisée / pas de réponse, code curl 52).
- **Explication** : le mapping redirige l'hôte 9082 vers le port 8080 **du conteneur**, or nginx écoute sur 80 (cf. `ExposedPorts` en C3). Rien n'écoute sur 8080 dans le conteneur.
- **Correction** : `docker run -d -p 9082:80 nginx:1.29-alpine` (testé : réponse HTTP 200).

### D3

```bash
docker run -d --name dormeur alpine sleep 1000
time docker stop dormeur
docker ps -a --filter name=dormeur
```

- L'arrêt a pris environ **10,2 s**, et le statut est `Exited (137)`.
- Cycle de vie : `docker stop` envoie SIGTERM au processus PID 1, puis attend 10 s (délai par défaut). `sleep` étant PID 1 sans gestionnaire de signal, il ignore SIGTERM (le noyau n'applique pas l'action par défaut au PID 1). Au bout des 10 s, Docker envoie SIGKILL et le processus est tué : 137 = 128 + 9 (SIGKILL).
- *Bonus* avec `--init` : l'arrêt est quasi instantané (~0,1 s) et le code est `Exited (143)` (128 + 15 = SIGTERM). Un petit processus init (tini) est PID 1, il transmet SIGTERM à `sleep` qui se termine proprement, sans attendre le SIGKILL.

### D4

```bash
docker run --name gourmand --memory 50m node:24-alpine \
  node -e "const a=[]; while(true) a.push(new Array(1e6).fill(1))"
docker inspect gourmand --format '{{.State.ExitCode}} {{.State.OOMKilled}}'
```

- Le programme alloue de la mémoire en boucle jusqu'à dépasser la limite de 50 Mo, puis le conteneur est tué brutalement. Code de sortie : **137** (128 + 9, SIGKILL).
- Dans `docker inspect gourmand`, le champ `State.OOMKilled` vaut `true` (avec `ExitCode: 137`), ce qui confirme une mort par manque de mémoire.
- Mécanisme du noyau : les **cgroups** limitent la mémoire du conteneur (`--memory 50m`) ; quand il dépasse, l'**OOM killer** du noyau tue le processus.

## Partie E — Ménage

### E1

```bash
docker system df
docker container prune -f
docker system df
```

- **Avant** : 12 conteneurs (4 actifs, 745,6 kB récupérables), images 1,618 GB, build cache 113,3 MB.
- J'ai d'abord supprimé les nginx encore actifs (`docker rm -f web1 web2 d2 d2ok`), puis `docker container prune -f` a supprimé les conteneurs arrêtés (745,6 kB récupérés).
- **Après** : 0 conteneur. Les conteneurs pesaient très peu (~1,6 MB) : l'essentiel de l'espace est dans les images (1,618 GB, dont 1,61 GB désormais récupérables avec `docker image prune -a`).
