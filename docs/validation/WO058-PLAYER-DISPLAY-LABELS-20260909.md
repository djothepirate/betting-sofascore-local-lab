# WO-058 — Catégories statistiques et libellés des indisponibles

## Demande et réalisation

Base : `2afa48496a47b99f0161c7e6b0c38c9c07e776e8`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`, worktree `.tmp/wo058-live-j4-j5`.

Le propriétaire précise la catégorie et le libellé français de deux statistiques encore
affichées sous leur clé dans « Autres données ». Le message nomme `savedFromInsideTheBox`,
tandis que la capture montre `savedShotsFromInsideTheBox` : les deux formes sont prises en charge.

| Clé reçue | Catégorie | Libellé |
|---|---|---|
| `savedFromInsideTheBox` | Gardien | Arrêts dans la surface |
| `savedShotsFromInsideTheBox` | Gardien | Arrêts dans la surface |
| `bigChanceMissed` | Attaque | Grosses occasions manquées |

`PlayerStatisticsPresentation` porte ces catégories. La projection est commune
au J5 manuel, au rendu initial live et à ses actualisations. Les métriques conservent leurs
clés et leurs valeurs : les variantes d'arrêts ne sont ni additionnées ni fusionnées si elles
coexistent. Les zéros, absences, arrondis et autres catégories gardent leur comportement.

V3 conservait déjà ces clés numériques sous leur nom exact. Le parseur, ses avertissements
historiques, les hashes et les données stockées sont inchangés. Les observations existantes
bénéficient donc de la présentation sans nouvelle collecte ni rejeu.

Pendant ce même travail, le propriétaire ajoute trois traductions d'indisponibilité,
prises en charge dans `LineupsPresentation` :

| Description reçue | Affichage français |
|---|---|
| `Back Injury` | Blessure au dos |
| `Broken Ankle` / `Broken ankle` | Fracture de la cheville |
| `Knee Injury` | Blessure au genou |

La capture porte `Broken Ankle`, avec une majuscule, et le message `Broken ankle` : les deux
formes sont reconnues. La blessure au genou reste distincte de l'entorse explicitement reçue
(`Sprained Knee Injury`). Les descriptions stockées, les identités et les dates restent inchangées.

## Vérification

Relecture indépendante : la conservation des clés numériques par V3 et le chemin commun
SSR/live sont confirmés. Aucun changement fonctionnel supplémentaire n'est nécessaire.
Le contrôle côté hôte retrouve le Lab Java PID 15284 sur `127.0.0.1:8087`, démarré à 09:08:44
heure locale. Conformément à l'autorisation propriétaire antérieure concernant le port 8087,
son identité et son écoute sont revalidées, puis le processus est arrêté. Une nouvelle
lecture des sockets côté hôte avec `-ErrorAction Stop` confirme le port libre avant les tests.

Java 25.0.4 et wrapper Maven, avec cache local sous Windows :

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' clean verify
```

Résultat : `BUILD SUCCESS` le 9 septembre 2026 à 07:34:11 UTC (09:34:11 Europe/Paris),
en 6 min 51 s. Surefire recense 1 944 cas, sans échec ni erreur, dont cinq ignorés :
quatre cas de liens symboliques indisponibles sous Windows et un contrôle Docker J6 opt-in.
Failsafe exécute 171 tests d'intégration, sans échec, erreur ni cas ignoré. Le contrôle natif
exigeant le port 8087 libre passe également. Le profil `integration-tests` n'est pas relancé
séparément pour cette retouche de présentation sans modification de persistance ni de migration.
Aucun test de simple correspondance littérale n'est ajouté : les tests existants sont
exécutés par la vérification complète obligatoire.

Le diff des cinq fichiers est relu : encodage UTF-8 valide, `git diff --check` réussi et
aucun secret détecté dans les modifications. Aucun appel fournisseur ni changement de
configuration réseau n'est introduit ; l'application reste liée à `127.0.0.1`.
Le lot est préparé dans le worktree WO-058 ; le checkout Eclipse n'est pas modifié par l'agent.

Journal local ignoré : `.tmp/player-metric-categories-clean-verify.log`.
Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Fichiers modifiés

- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/PlayerStatisticsPresentation.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LineupsPresentation.java`
- `CHANGELOG.md`
- `docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `docs/validation/WO058-PLAYER-DISPLAY-LABELS-20260909.md`
