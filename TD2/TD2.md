# TD2 — Écrire ses Dockerfiles (≈ 1 h 30)

**Individuel.** Rendu : dossier `td2/` de votre dépôt `docker-td`, avant la **date donnée en cours**.

> Commandes écrites pour un terminal **bash / zsh** (macOS, Linux, ou Ubuntu dans WSL sous Windows — recommandé).
> Sous PowerShell, remplacez `time <commande>` par `Measure-Command { <commande> }`.

Une mini-API vous est fournie en deux versions — prenez **la stack que vous connaissez le mieux** :

| Dossier | Stack | Build | Lancement | Port |
|---|---|---|---|---|
| `TD2/node/` | Node 24 + TypeScript + Express | `npm run build` (→ `dist/`) | `node dist/server.js` | 3000 |
| `TD2/java/` | Java 21 + Spring Boot | `mvn package` (→ `target/*.jar`) | `java -jar target/td2-api-1.0.0.jar` | 8080 |

Routes : `GET /` (message, version, hostname) et `GET /health`.
Variables lues : `PORT`, `MESSAGE`, `APP_VERSION`.

> Vous n'avez **pas** besoin de Node ni de Maven sur votre machine : tout se construit dans Docker.

Pour chaque version de Dockerfile, relevez dans `td2/MESURES.md` :

| Version | Taille de l'image | Build à froid | Rebuild après modif d'**une ligne** de code | `.env` dans l'image ? | Utilisateur |
|---|---|---|---|---|---|

Commandes utiles :
```bash
docker build --no-cache -t td2:v1 .   # build à froid, sans cache — la durée s'affiche : « Building 42.1s »
docker build -t td2:v1 .              # rebuild, après avoir modifié une ligne d'un fichier source
docker image ls td2                   # tailles
docker run --rm td2:v1 ls -la         # remplace le CMD : liste les fichiers du dossier de travail de l'image
docker run --rm td2:v1 id             # avec quel utilisateur l'app tourne-t-elle ?
```

---

## Étape 1 — La version naïve

Écrivez un Dockerfile **le plus simple possible** qui marche : une image de base **complète**, on copie **tout**
le dossier, on construit, on lance. Partez de ce squelette (fichier `Dockerfile`, à côté du `package.json` ou du
`pom.xml`) et remplacez les `____` :

```dockerfile
FROM ____                 # node:24   ou   maven:3-eclipse-temurin-21
WORKDIR /app
COPY . .
RUN ____                  # ce qui prépare l'app : voir ci-dessous
EXPOSE ____               # le port de l'app (tableau du haut)
CMD ["____", "____"]      # la commande « Lancement » du tableau, un mot par élément
```

| | Ce que fait le `RUN` | Le `CMD` lance |
|---|---|---|
| **Node** | installe les dépendances (`npm ci`) **puis** compile (`npm run build`) — deux `RUN`, ou un seul avec `&&` | `node dist/server.js` |
| **Java** | `mvn package -DskipTests` (télécharge les dépendances et produit le jar) | `java -jar target/td2-api-1.0.0.jar` |

C'est le même schéma que le Dockerfile vu en cours, avec une étape de **construction** en plus.
Ne cherchez pas à l'optimiser : c'est l'objet des étapes suivantes.

Construisez `td2:v1`, lancez-le avec le port publié, vérifiez `GET /`. Remplissez la ligne `v1` du tableau.

> **Q1.** Le fichier `.env` du dossier contient une clé d'API. Est-il dans votre image ? Pourquoi est-ce grave, même
> si l'image n'est « que » sur votre machine pour l'instant ?

## Étape 2 — Le cache

Réordonnez le Dockerfile pour que la modification d'un fichier **source** ne relance **pas** le téléchargement
des dépendances. (Java : `mvn dependency:go-offline` après avoir copié le seul `pom.xml`.)

Image `td2:v2`. Mesurez le rebuild.

> **Q2.** Pourquoi le rebuild est-il plus rapide ? Que se passe-t-il si vous modifiez `package.json` / `pom.xml` ?

## Étape 3 — `.dockerignore`

Ajoutez un `.dockerignore`. Image `td2:v3`.

> **Q3.** Qu'avez-vous exclu, et pourquoi chaque ligne ? Regardez la ligne `transferring context` du build
> avant/après : qu'est-ce qui a changé ?

## Étape 4 — Multi-stage

Séparez **construction** et **exécution** :
- une étape de build avec les outils (compilateur TypeScript, Maven, JDK) ;
- une étape finale qui ne contient **que** ce qu'il faut pour exécuter (`node:24-alpine` + dépendances de prod + `dist/`,
  ou `eclipse-temurin:21-jre-alpine` + le jar) ;
- l'app tourne avec un utilisateur **non-root** (`USER`). Pas besoin de le créer : les images `node` ont déjà un
  utilisateur `node`, et les images Alpine un utilisateur `nobody`.

Image `td2:v4`.

> **Q4.** Qu'est-ce qui est dans l'image de build et **n'est plus** dans l'image finale ? Citez-en au moins trois.

## Étape 5 — Une image, plusieurs configurations

Sans reconstruire, lancez **deux** conteneurs de `td2:v4` sur deux ports différents, avec deux valeurs de `MESSAGE`
et `APP_VERSION=1.0.0`.

> **Q5.** Pourquoi est-il important de ne **pas** reconstruire l'image pour changer le message ?

---

### Rendu

```
td2/
├── src/, package.json… ← l'app fournie (sans node_modules/target/dist), nécessaire à la CI du TD4
├── Dockerfile          ← la version finale (v4)
├── .dockerignore
├── Dockerfile.v1       ← la version naïve, pour comparaison
└── MESURES.md          ← le tableau + réponses Q1 à Q5
```

### Barème (/10)

| Critère | Points |
|---|---|
| Tableau de mesures complet et cohérent | 2 |
| Dockerfile final : multi-stage, cache bien ordonné, `CMD` entre crochets, non-root, versions explicites | 4 |
| `.dockerignore` pertinent (`.env`, dépendances, artefacts de build) | 1 |
| Réponses Q1 à Q5 | 3 |
