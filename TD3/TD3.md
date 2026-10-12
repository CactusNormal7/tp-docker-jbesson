# TD3 — Une application multi-services (≈ 1 h 30)

**Individuel.** Rendu : dossier `td3/` de votre dépôt `docker-td`, avant la **date donnée en cours**.

> Commandes écrites pour un terminal **bash / zsh** (macOS, Linux, ou Ubuntu dans WSL sous Windows — recommandé).
> Sous PowerShell, remplacez `time <commande>` par `Measure-Command { <commande> }`.

L'application `TD3/app/` compte les visites : à chaque `GET /`, elle incrémente un compteur dans **Redis** et
enregistre une ligne dans **Postgres**, puis renvoie les deux totaux et le nom de l'instance qui a répondu.
Son `Dockerfile` (fourni) a deux cibles : `dev` et `prod`.

| Variable | Rôle |
|---|---|
| `PORT` | port d'écoute (défaut 3000) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | connexion Postgres |
| `DB_PASSWORD_FILE` | alternative à `DB_PASSWORD` : chemin d'un fichier contenant le mot de passe (utile au TD4) |
| `REDIS_URL` | ex. `redis://hote:6379` |

Images : `postgres:18-alpine`, `redis:8-alpine`.

---

## Partie A — À la main (sans Compose)

**A1.** Lancez l'application **à la main**, un conteneur à la fois, jusqu'à ce que http://localhost:3000 réponde.
Notez **toutes** les commandes dans `td3/A-manuel.md`. Avancez par étapes, en vérifiant chacune :

1. **L'image de l'API** : `docker build` avec la cible `prod` (`--target`), depuis `TD3/app/`.
   Vérification : elle apparaît dans `docker images`.
2. **Postgres**, avec ses données dans un **volume nommé** (à monter sur `/var/lib/postgresql`).
   L'image `postgres` se configure par trois variables : `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
   — choisissez les valeurs, vous les redonnerez à l'API.
   Vérification : `docker logs` finit par `database system is ready to accept connections`.
3. **Redis** : aucune option particulière.
4. **L'API** : port publié, et les variables du tableau ci-dessus (`DB_HOST` et `REDIS_URL` désignent les deux
   autres conteneurs **par leur nom**).

> **Premier essai** : lancez les trois conteneurs sans option de réseau. L'API démarre-t-elle ? Lisez ses logs.
> Quelle est l'erreur, et quelle correction (vue en cours) faut-il apporter aux **trois** `docker run` ?

Si un conteneur s'arrête tout de suite : `docker ps -a`, puis `docker logs <nom>`. Pour recommencer : `docker rm -f <nom>`.

**A2.** Appelez `GET /` plusieurs fois. Supprimez **les trois** conteneurs, puis recréez-les.

> Le compteur Postgres a-t-il survécu ? Et celui de Redis ? Expliquez.

## Partie B — Avec Compose

**B1.** Écrivez `compose.yaml` qui décrit la même application : `api` (construite depuis `./app`, cible `prod`),
`db`, `cache`. Aucun mot de passe en clair dans `compose.yaml` : il vient d'un fichier `.env`.
Commitez un `.env.example`, **pas** le `.env`.

> Vérifiez le résultat de l'interpolation avec `docker compose config`.

**B2.** Lancez `docker compose up -d`, puis regardez les logs de l'API.

> Il est probable que l'API plante au premier démarrage. Lisez l'erreur et expliquez-la.
> `depends_on` ne suffisait-il pas ? Ajoutez `restart: on-failure` à l'API (Docker relance le conteneur
> s'il s'arrête en erreur) : que se passe-t-il ?
> Pourquoi est-ce un contournement et pas une vraie solution ? (La vraie solution arrive plus tard dans le module.)

**B3.** Prouvez, commandes et sorties à l'appui, que :
1. les données **survivent** à `docker compose down` puis `up` ;
2. elles **disparaissent** avec `docker compose down -v`.

> Pour que le compteur **Redis** survive lui aussi, que faut-il ajouter ?
> (Indice : `docker compose exec cache redis-cli CONFIG GET dir` indique où Redis écrit ses données.)

**B4.** Depuis le conteneur `api`, retrouvez l'adresse IP résolue pour `db` : `docker compose exec api getent hosts db`.
Recréez `db` (`docker compose up -d --force-recreate db`) et recommencez.

> L'IP a-t-elle changé ? Pourquoi ne faut-il jamais écrire une IP de conteneur dans une configuration ?

## Partie C — L'environnement de dev

**C1.** Écrivez `compose.override.yaml` pour le dev :
- l'API est construite avec la cible **`dev`** (rechargement automatique avec `node --watch`) ;
- chaque modification de `app/src/` est **synchronisée** dans le conteneur avec `develop.watch` ;
- une modification de `package.json` **reconstruit** l'image ;
- la base est accessible depuis votre machine (pour un client SQL), sur un port **configurable** dans `.env`.

```bash
docker compose up -d --build
docker compose watch
```

Modifiez la réponse de `/health` : prouvez que le changement est pris **sans reconstruire à la main**.

**C2.** Passez de la configuration dev à la configuration « prod seule » :
```bash
docker compose -f compose.yaml up -d
```

> Que se passe-t-il si vous oubliez `--build` en passant de l'une à l'autre ? Pourquoi ?

---

### Rendu

```
td3/
├── compose.yaml
├── compose.override.yaml
├── .env.example
├── A-manuel.md       ← commandes de la partie A + réponses A1, A2
└── REPONSES.md       ← réponses B2 à C2, avec commandes et sorties
```

### Barème (/10)

| Critère | Points |
|---|---|
| Partie A : commandes justes, volume nommé, réseau créé | 2 |
| `compose.yaml` : services, volumes, interpolation, aucun secret, `.env.example` | 3 |
| `compose.override.yaml` : cible dev, `develop.watch`, port configurable | 2 |
| Réponses B2 à C2 (explications, preuves) | 3 |
