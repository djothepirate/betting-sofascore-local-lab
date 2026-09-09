# WO-058 — Motifs en français après le retour visuel

## Observation et correction

Base : `51d78e0fd522ddceaac345d44ce3fbf62abab20d`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`, worktree `.tmp/wo058-live-j4-j5`.

Les captures fournies par le propriétaire montrent le capitaine Hugo Rodallega,
ses statistiques individuelles ouvertes et les joueurs indisponibles. Les retours suivants
ajoutent le carton d'Alan Saldivia et d'autres indisponibles. Cinq descriptions restaient
en anglais, car elles n'étaient pas encore reconnues dans les dictionnaires de présentation.

| Valeur reçue | Présentation française |
|---|---|
| `Cruciate Ligament Injury` | Blessure au ligament croisé |
| `Toe Injury` | Blessure à un orteil |
| `Muscle Injury` | Blessure musculaire |
| `Unknown` | Motif inconnu |
| `Other reason` (carton) | Autre motif |

`LineupsPresentation` traite les indisponibles ; `IncidentPresentation.graphicMotifLabel`
traite le motif de carton. Les libellés n'infèrent ni rupture ni ligament antérieur/postérieur.
Les descriptions fournisseur stockées restent inchangées. La page J5, le rendu initial live
et les mises à jour live partagent ces projections ; aucun changement de JavaScript,
de parseur ou de persistance n'est requis. Le tableau technique des incidents conserve
le vocabulaire source. Les descriptions non reconnues continuent d'afficher leur texte reçu,
avec échappement ; `Unknown` explicitement reçu reste distinct d'un motif absent.

Cette observation visuelle ne constitue pas une clôture formelle du WO.
Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Vérification

Java 25.0.4, wrapper Maven et cache local, sous Windows. Le contrôle initial du port depuis
le contexte restreint, avec erreurs masquées, avait indiqué à tort une disponibilité ; il
ne constitue pas une preuve de libération côté hôte.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' clean verify
```

Une première passe commencée pour le seul ligament croisé a été interrompue à réception
des captures supplémentaires ; elle ne constitue pas une preuve de réussite. La commande
ci-dessus est relancée sur les cinq traductions regroupées.

La première passe complète sur les cinq libellés échoue à 06:55:01 UTC : 1 944 cas Surefire,
un échec, zéro erreur et cinq ignorés. Le seul échec est le contrôle natif J6, avec le marqueur
`LOOPBACK_APPLICATION_LISTENER_RESIDUAL`. La lecture côté hôte identifie le Lab Java PID 27956,
sur `127.0.0.1:8087`, démarré à 08:43:21 heure locale. Conformément à l'autorisation propriétaire
antérieure d'arrêter le processus occupant ce port, son identité et son écoute sont revalidées,
puis le processus est arrêté. La libération est confirmée côté hôte par une lecture des sockets
avec `-ErrorAction Stop`, sans assimiler une erreur de lecture à un port libre.

La commande complète est relancée sans changement du code ou des tests. Elle se termine par
`BUILD SUCCESS` le 9 septembre à 07:04:31 UTC (09:04:31 à Paris), en 6 min 58 s :
1 944 cas Surefire, zéro échec, zéro erreur et cinq ignorés ; puis 171 intégrations Failsafe,
toutes réussies sans cas ignoré. Le scénario natif précédemment bloqué est exécuté et réussi.
Les cinq ignorés sont quatre scénarios de liens symboliques indisponibles sous Windows
et le scénario Docker J6 soumis à l'opt-in `j6.docker.qualification=true`.

Le profil explicite `integration-tests` n'est pas relancé séparément :
la retouche ne modifie ni migration ni persistance. Les éventuelles intégrations héritées
de `verify` sont recensées dans le résultat réel de la commande.

Journaux locaux ignorés : `.tmp/french-reasons-clean-verify.log` (échec conservé) et
`.tmp/french-reasons-clean-verify-final.log` (relance après arrêt autorisé).
Relecture indépendante : projections communes SSR/live, aucun autre défaut certain
identifié sur ce chemin. Aucun nouveau test de correspondance littérale n'est ajouté pour
cette retouche d'affichage ; les tests existants sont exécutés par la commande obligatoire.
La revue du diff, l'inventaire des cinq fichiers, l'encodage UTF-8 strict et la recherche de
motifs de secrets sont réussis. `git diff --check` est propre. Les migrations, la configuration
sur `127.0.0.1` et les opt-ins fournisseur sont inchangés. Le correctif reste local au worktree
WO-058 ; la qualification n'effectue ni mise à jour du checkout Eclipse ni publication/fusion.

## Fichiers du correctif

- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LineupsPresentation.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/IncidentPresentation.java`
- `CHANGELOG.md`
- `docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `docs/validation/WO058-FRENCH-REASONS-20260909.md`
