# J9 — readiness locale du durcissement de preuve de nettoyage WO-025

## 1. Résultat

La qualification locale de `WO-SS-20260831-025-j9-backup-cleanup-proof-hardening` est
`PASS_LOCAL`. Le correctif est prêt pour revue propriétaire, mais le Work Order reste actif et aucune
nouvelle sauvegarde/restauration WO-023 n'est autorisée par ce résultat.

```text
WORK_ORDER=WO-SS-20260831-025-j9-backup-cleanup-proof-hardening
BRANCH=codex/j9-backup-cleanup-proof-hardening
OPENING_BASE_COMMIT=501490233df5d29719e19d70dabbfc1e37ac4ce7
QUALIFICATION_RESULT=PASS_LOCAL
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
OPENING_COMMIT=5a93911d42b84755d461781dbdb45e890f008789
IMPLEMENTATION_COMMIT=d019be274f200aaa33124b041e3773cf15ae2723
IMPLEMENTATION_STATUS=IMPLEMENTED_AND_LOCALLY_QUALIFIED
OWNER_VALIDATION=PENDING
MOVE_TO_COMPLETED_AUTHORIZED=NO
PROVIDER_ACCESS_PERFORMED=NO
PRIMARY_DATABASE_PURGE=NO
WO023_BACKUP_RETRY_AUTHORIZED=NO_PENDING_SEPARATE_OWNER_DECISION
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_PROVIDER_GLOBAL_GO_GRANTED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

La qualification s'est achevée le 2026-09-01 en heure locale Europe/Paris. Les portes finales
Maven et Verify-Local se sont terminées entre `2026-08-31T22:14:16Z` et
`2026-08-31T22:21:11Z`.

## 2. Contre-preuves et corrections

| Contre-preuve | Correction qualifiée | Résultat |
|---|---|---|
| Le défaut runtime PostgreSQL était de 5 s alors que la preuve WO-024 avait employé 10 s | budgets effectifs séparés : nettoyage natif 5 s et observation PostgreSQL 10 s, tous deux publiés par le script et exercés sans override dans le parcours Docker | `PASS_5000_10000` |
| Une terminaison pouvait consommer la borne puis laisser un ancien compte non nul produire un faux `SESSION_REMAINING` | toute observation devient non fraîche après une tentative de terminaison ; sans nouvelle observation postérieure, la borne produit `POSTGRES_OBSERVATION_TIMEOUT` | `PASS` |
| Les comptes de sessions ciblées et de signaux de terminaison pouvaient être incohérents | un même CTE matérialisé retourne le nombre exact de cibles et le nombre de `pg_terminate_backend(...) = true`; les deux scalaires sont parsés et cumulés avec `successful <= targeted` | `PASS` |
| Le message final masquait la cause interne et une erreur fichier pouvait remplacer l'erreur PostgreSQL | catégories internes bornées et sanitées, agrégation déterministe de l'erreur primaire, du nettoyage PostgreSQL, des fichiers et du temp root | `PASS` |
| La cible native pouvait ne pas créer son fichier PID avant l'ancien timeout global de 1 500 ms | handshake en mémoire distinct du budget d'exécution, identité cible exacte `PID + StartTime`, puis preuve de confinement et de nettoyage | `PASS` |
| Des API Windows pouvaient diverger sur un ancien état synthétique | corroboration bornée Job Object/.NET/Toolhelp/CIM/`tasklist`; divergence non ouvrable classée ambiguë et jamais traitée comme autorisation de terminaison | `PASS_FAIL_CLOSED` |
| Un ancien répertoire synthétique hors dépôt existait sans preuve de propriété suffisante | temp root neuf, enfant direct du temp système, nom canonique GUID, marqueur atomique lié au chemin et nonce, refus des reparse points et preuve d'absence après suppression littérale | `PASS` |

La contre-validation post-incident de WO-023 demeure une preuve historique : WO-025 corrige et
qualifie le mécanisme, mais ne transforme pas rétroactivement la tentative interrompue en
sauvegarde qualifiée.

## 3. Preuve PostgreSQL fail-closed

### 3.1 Propriété et observation

- le nom d'application est validé de manière sensible à la casse par
  `^j6_(backup|restore)_[a-f0-9]{32}$` ;
- chaque commande `psql` emploie `ON_ERROR_STOP=1` ;
- la terminaison vise uniquement l'égalité exacte du `PGAPPNAME` de l'exécution ;
- les cibles et les signaux acceptés proviennent du même snapshot SQL matérialisé ;
- une réussite exige trois observations fraîches et successives exactement égales à zéro ;
- la seconde confirmation du même nom exact réutilise uniquement une preuve déjà stabilisée ;
- un refus de terminaison, une sortie mal formée, une erreur SQL, une erreur Docker, un timeout
  d'observation ou une preuve de nettoyage de processus manquante reste terminal et fail-closed.

### 3.2 Parsing et classification

Le parseur accepte uniquement un entier canonique non négatif représentable par `Int64`, avec au
plus la fin de ligne attendue. Les sorties vide, blanche, signée, décimale, bruitée, multiligne ou
hors plage sont refusées. La paire de terminaison exige exactement deux scalaires et interdit que
le nombre de signaux acceptés dépasse le nombre de cibles.

Les catégories publiables sont bornées, notamment :

```text
POSTGRES_DOCKER_COMMAND_FAILED
POSTGRES_DOCKER_COMMAND_NONZERO_EXIT
POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED
POSTGRES_SQL_COMMAND_NONZERO_EXIT
POSTGRES_OBSERVATION_TIMEOUT
POSTGRES_SCALAR_OUTPUT_INVALID
POSTGRES_SESSION_REMAINING
POSTGRES_SESSION_STABILITY_NOT_PROVEN
POSTGRES_SESSION_CLEANUP_UNCONFIRMED
```

Ni stderr brut, ni chaîne de connexion, ni `.env`, ni phrase `age`, ni payload n'est placé dans
l'enveloppe documentaire ou l'erreur sanitée.

### 3.3 Exécutable Docker

Le chemin Docker utilisé par ce mécanisme doit être absolu, résoudre un fichier ordinaire exact et
non un reparse point. Sous Windows, la preuve exige en plus les métadonnées produit Docker et une
signature Authenticode valide attribuée à Docker Inc. Une identité non confirmée ferme le parcours.

## 4. Preuve native Windows bornée

Le superviseur crée le processus hôte suspendu, l'assigne d'abord à un Job Object Windows
`KILL_ON_JOB_CLOSE`, puis seulement ouvre sa porte. La cible réelle publie ensuite, sur un named
pipe `CurrentUserOnly`, un message `J6_NATIVE_TARGET_START_V1` lié à un nonce aléatoire. Le parent
valide l'ensemble exact des champs avant de commencer le budget d'exécution.

Les résultats et les erreurs qualifiées conservent séparément :

```text
TargetProcessId
TargetStartedAtUtcTicks
StartupDurationMilliseconds
ExecutionDurationMilliseconds
Confinement
ActiveProcessesAfterCleanup
```

La preuve d'absence croise le Job Object, l'identité exacte ouvrable par .NET, Toolhelp en lecture
seule, CIM et une invocation `tasklist` elle-même bornée et confinée. Un état secondaire visible
alors que .NET ne peut pas ouvrir l'identité est classé
`AMBIGUOUS_CROSS_API_GHOST_VISIBILITY`. Seul un état transitoire sans vue active ni erreur peut
être réobservé, au maximum trois fois pendant cinq secondes. Aucune de ces observations n'autorise
une terminaison : le nettoyage repose exclusivement sur le Job Object possédé ou une identité
exacte déjà acquise, jamais sur un PID seul, un nom ou un préfixe.

Le contrôle final du listener utilise les tables TCP de l'API .NET et confirme l'absence d'écoute
sur le port local 8087.

## 5. Preuve du temp root synthétique

Le harness crée un enfant direct du répertoire temporaire système avec un nom canonique incluant
un GUID. Un marqueur de propriété atomique lie le protocole, le chemin canonique et un nonce. Avant
toute suppression, le harness revérifie :

- le parent temporaire exact et le nom canonique ;
- l'existence du répertoire possédé ;
- le marqueur et le nonce attendus ;
- l'absence de reparse point sur la racine et tous ses descendants ;
- l'absence de glob et l'impossibilité de viser le parent, un frère ou un chemin extérieur.

La suppression emploie uniquement le chemin littéral exact. Le scénario positif exige son absence
finale ; les scénarios négatifs préservent le frère sentinelle et classent l'échec sans masquer une
éventuelle erreur primaire.

## 6. Qualifications exécutées

### 6.1 Parcours ciblés

| Qualification | Exécution | Résultat |
|---|---:|---|
| script hors ligne direct | 1 finale | `PASS` |
| classe Maven ciblée hors ligne | 5 répétitions successives | `PASS` à chaque répétition |
| script Docker local direct | 1 finale | `PASS` |
| classe Maven ciblée avec Docker | 3 répétitions successives | `PASS` à chaque répétition |

Commande hors ligne :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6BackupRestoreLoopbackQualification.ps1
```

Commande Docker :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6BackupRestoreLoopbackQualification.ps1 -WithDocker
```

Commande Maven ciblée Docker, propriété passée comme un argument PowerShell unique :

```powershell
.\mvnw.cmd -q '-Dj6.docker.qualification=true' `
  '-Dtest=J6NativeBinaryPipelineQualificationTest' test
```

Le parcours Docker final a qualifié le succès nominal `pg_dump -> age` synthétique puis
`age -> pg_restore`, l'échec anticipé du consommateur, l'échec de déchiffrement, une erreur de
nettoyage injectée, les classifications PostgreSQL, les trois zéros, l'idempotence et l'absence
finale de session possédée, base temporaire, fichier partiel, processus et listener. Le double
`age` n'emploie aucun secret et ne qualifie ni la cryptographie réelle ni le dialogue interactif.

### 6.2 Portes de dépôt

| Commande | Résultat |
|---|---|
| `.\mvnw.cmd clean verify` | `946` tests, `0` échec, `0` erreur, `5` ignorés |
| `.\mvnw.cmd -Pintegration-tests verify` | standards `946/0/0/5`, intégrations `67/0/0/0` |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1 -WithIntegrationTests` | `PASS`, Java 25, Docker contrôlé, intégrations exécutées, réseau SofaScore `NO` |
| `docker compose --env-file .env config --quiet` | `PASS` |
| `git diff --check` | `PASS` |

Les portes finales ont aussi confirmé :

```text
SERVER_ADDRESS=127.0.0.1_PRESERVED
DEFAULT_PROVIDER_NETWORK_FLAGS=BLOCKING_PRESERVED
HIGH_CONFIDENCE_SECRET_MATCH_COUNT=0
FORBIDDEN_NETWORK_OR_BINDING_ADD_COUNT=0
BROAD_PURGE_ADD_COUNT=0
PROVIDER_ACCESS_PERFORMED=NO
PRIMARY_DATABASE_PURGE=NO
```

## 7. Essais exploratoires exclus de la preuve favorable

Trois essais antérieurs ne sont pas comptés parmi les répétitions qualifiées :

1. un lancement dans le bac à sable restreint où CIM/`tasklist` n'étaient pas observables ; le
   harness a correctement échoué fermé ;
2. un lancement où l'absence d'objet de `Get-NetTCPConnection` a été traitée comme une erreur
   d'observation ; le contrôle du listener a ensuite été remplacé par l'API .NET et rejoué ;
3. une commande Maven où la propriété Docker n'était pas protégée comme argument PowerShell
   unique ; Maven l'a refusée avant tout test et la commande corrigée a été rejouée.

Ces essais n'ont exécuté aucun appel fournisseur et ne sont pas utilisés pour augmenter le nombre
de succès.

## 8. Limites et prochaine porte

WO-025 prouve le mécanisme local, le confinement et les classifications fail-closed. Il ne prouve
pas une nouvelle sauvegarde réelle WO-023, n'utilise pas la base primaire pour une purge et
n'autorise aucune acquisition SofaScore.

La prochaine action possible est exclusivement la revue propriétaire de WO-025. Même si le
propriétaire le valide et autorise son déplacement vers les Work Orders terminés, une nouvelle
décision distincte restera requise avant de retenter la sauvegarde/restauration WO-023. Une reprise
de la campagne fournisseur exigera encore ses propres portes et un nouveau go global valide.
