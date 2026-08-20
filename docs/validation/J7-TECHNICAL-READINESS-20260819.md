# J7 — Readiness technique de l'export canonique local — 2026-08-19

## 1. Statut

```text
J7_TECHNICAL_IMPLEMENTATION=HUMAN_QUALIFIED_AWAITING_PR_REVIEW
J7_STANDARD_SUITE=PASS
J7_INTEGRATION_SUITE=PASS
J7_DIFF_REVIEW=PASS
J7_HUMAN_QUALIFICATION=PASS
J7_WORK_ORDER_STATUS=READY_FOR_PR_REVIEW
J7_APPLICATION_PHASE=J6-HISTORY-AND-GUARDED-RETENTION-VALIDATED
```

Ce rapport consigne les portes techniques et la recette humaine du Work Order
`WO-SS-20260819-008`. Il ne constitue aucune autorisation de transfert. Le seul résultat encore
`PENDING` est la publication de la branche avec une PR revue `CLEAN/MERGEABLE`.
La phase exposée par l'application reste volontairement celle de J6, dernier jalon humainement
validé ; elle ne doit passer à J7 qu'après la recette, la clôture du Work Order et la publication
revue.

## 2. Base et frontière

```text
J7_BASE_COMMIT=d24e812a35467297eebf254b36ec5913b29c71cf
J7_BRANCH=codex/j7-canonical-export
J7_SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
J7_SCHEMA_VERSION=1.0.0
J7_FLYWAY_TARGET=23
J7_PROVIDER_CALLS_AUTHORIZED=0
J7_PRIMARY_DATABASE_PURGE_AUTHORIZED=NO
```

J7 exporte un événement à la fois et ses seules observations courantes. L'historique J6, les lots,
l'import Betting Project, le VPS, le polling et toute collecte sont hors périmètre.

## 3. Éléments implémentés à contrôler

- schéma JSON Draft 2020-12 fermé et chargé depuis le classpath sans résolution réseau ;
- enveloppe `{manifest,data}` déterministe, cinq sources ordonnées et avertissements structurés ;
- `dataSha256`, `sourceSetSha256`, hash candidat initial et hash courant du fichier ;
- sélection J4/J5/J6 en lecture seule `REPEATABLE_READ`, sans lecture de `payload_raw`, et verrou
  transactionnel partagé avec les écritures d'observation, les quatre tables métier filles J5 et
  les mutations autorisées des snapshots référencés pendant le contrôle de fraîcheur ; une
  écriture fille dont le parent n'est pas encore visible est refusée en mode fail-closed ;
- états `PRESENT`, `UNAVAILABLE`, `MISSING`, `EMPTY_VALID` et complétude J5 ;
- identifiants numériques strictement positifs bornés à `Long.MAX_VALUE`, scanner renforcé sur les
  octets, textes et clés JSON, UTF-8 strict et limite de 5 Mio ;
- cycle `COHERENCE_CHECKED` vers `HUMAN_VALIDATED` ou `REJECTED` ;
- relecture du jeu de sources avant validation sous verrou d'événement ;
- intention de décision write-ahead persistée avec statut, heure, motif, chemin, hash et taille
  pour authentifier les reprises terminales ;
- stockage local par temporaire synchronisé puis lien physique atomique create-new sur le même
  système de fichiers, chemins générés et refus de traversée/lien/écrasement/altération ;
- reprise d'un candidat non référencé uniquement après vérification de l'état courant et
  reconstruction octet par octet avec le générateur courant ;
- migration append-only V23 et compatibilité des anciennes lignes `export_manifest` ;
- pages HTML locales protégées par jeton unique et téléchargement validé seul ;
- en-têtes anti-cache/noindex et aperçu échappé ;
- absence de nouveau client réseau, endpoint fournisseur ou ordonnanceur.

## 4. Matrice de tests attendue

### Suite standard

```powershell
.\mvnw.cmd clean verify
```

Résultat final obtenu le 2026-08-20 après la clarification d'interface et la fin de la session
opérateur :

```text
J7_STANDARD_SUITE_RESULT=PASS
J7_STANDARD_TESTS_TOTAL=457
J7_STANDARD_FAILURES=0
J7_STANDARD_ERRORS=0
J7_STANDARD_SKIPPED=2_WINDOWS_SYMLINK_CREATION_NOT_PERMITTED
J7_STANDARD_PROVIDER_CALLS=0
```

La suite couvre le schéma candidat/validé/rejeté, mutations invalides, limites `long`,
déterminisme, mapping complet, provenances et complétudes, changements de sources, réutilisation
d'un export validé strictement identique, scanner renforcé, publication create-new,
intentions/reprises terminales, tokens, confirmations, statuts HTTP, échappement, en-têtes sur les
erreurs pré-contrôleur et téléchargement. Elle couvre également la configuration combinée
J3/J4 phase 2/J5 et la sérialisation des départs fournisseur J3 par le coordinateur commun.

### Suite PostgreSQL/Testcontainers

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Résultat final obtenu le 2026-08-20 :

```text
J7_INTEGRATION_SUITE_RESULT=PASS
J7_INTEGRATION_TESTS_TOTAL=43
J7_INTEGRATION_FAILURES=0
J7_INTEGRATION_ERRORS=0
J7_INTEGRATION_SKIPPED=0
J7_INTEGRATION_PROVIDER_CALLS=0
```

La suite couvre une installation V1→V23 et un upgrade V22→V23 réellement prérempli avant migration
avec quatre snapshots/occurrences J6, l'état et le détail J4, trois observations J5 `COMPLETE` et
leurs lignes métier, tous relus à l'identique après V23. Elle couvre aussi les contraintes et
triggers, les verrous concurrents de l'état, du détail, du parent J5, de ses quatre tables filles et
du snapshot fournisseur, ainsi que le refus fermé d'une fille dont le parent n'est pas visible,
l'intention write-ahead, le cycle candidat/décision/téléchargement et un export fournisseur complet
`PRESENT`/`COMPLETE` alimenté par les parseurs statistiques, incidents et compositions.

## 5. Contrôles statiques attendus

```text
J7_GIT_DIFF_CHECK=PASS
J7_SECRET_SCAN=PASS_0_HIGH_RISK_FINDINGS
J7_ENV_IGNORED=PASS
J7_SERVER_ADDRESS_127_0_0_1=PASS
J7_MIGRATIONS_V1_TO_V22_UNCHANGED=PASS
J7_ONLY_V23_ADDED=PASS
J7_EXPORT_RUNTIME_FILES_UNTRACKED=PASS_ONLY_GITKEEP_TRACKED_RUNTIME_JSON_IGNORED
J7_NETWORK_GUARDS_UNCHANGED=PASS_DISABLED_BY_DEFAULT
J7_J6_BACKUP_SCRIPT_EXPECTS_V23=PASS
J7_UNUSED_YAML_MODULE_ABSENT=PASS
J7_FINAL_OPERATOR_LISTENER_127_0_0_1_8087=ABSENT
J7_VERSIONED_RUNTIME_EXPORTS=0
```

Le contrôle final du 2026-08-20 a porté sur le diff complet, y compris les fichiers non suivis.
`git diff --check`
termine avec le code 0 ; son seul message est l'avis Git de conversion LF vers CRLF du script
PowerShell J6. La dépendance de validation JSON n'introduit pas le module YAML exclu. Après la
session opérateur, aucun processus n'écoute sur `127.0.0.1:8087` ; les JSON runtime restent ignorés
et aucun nom de fichier candidat, validé, rejeté ou de preuve J3 n'est suivi par Git.

### 5.1 Amendement multi-campagnes dans une même instance

L'amendement demandé pendant la recette a été exercé sans appel fournisseur. Cinquante-cinq tests
ciblés valident l'union exacte des endpoints, le refus de J4 phase 1 dans une session combinée, les
politiques J3/J4 phase 2/J5 et le coordinateur partagé. L'application a ensuite été démarrée avec la
configuration locale combinée réellement présente : Spring a accepté le binding, Flyway a constaté
V23 à jour et le serveur a écouté uniquement sur `127.0.0.1:8087`. Aucune action manuelle n'a été
déclenchée pendant ce contrôle de démarrage.

```text
J7_COMBINED_SESSION_AMENDMENT=PASS
J7_COMBINED_SESSION_TARGETED_TESTS=PASS_55_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J7_COMBINED_SESSION_ACTUAL_CONFIG_STARTUP=PASS_127_0_0_1_8087
J7_COMBINED_SESSION_BINDING_FAILURE=RESOLVED
J7_COMBINED_SESSION_ENDPOINTS=SCHEDULED_EVENTS,EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
J7_COMBINED_SESSION_J4_MODE=PHASE_2_ONLY
J7_COMBINED_SESSION_MANUAL_COORDINATOR=PASS_J3_J4_J5
J7_COMBINED_SESSION_AUTOMATIC_PROVIDER_CALLS=0
J7_COMBINED_SESSION_NEW_PROVIDER_ENDPOINTS=0
```

### 5.2 Preuve humaine du mode combiné et clarification du contrat J3

Après le contrôle technique, l'opérateur a exercé les campagnes explicitement autorisées. Une
première instance a terminé J3 sur six pages (`308` à `313`), puis J4 phase 2 sur le snapshot
`314`. La première requête J5 a rencontré `TRANSPORT_TIMEOUT` avant toute réponse persistée : la
campagne s'est verrouillée immédiatement, sans retry et sans tenter les familles suivantes. Ce
résultat est conservé comme preuve d'arrêt sûr, pas comme succès J5.

Après redémarrage explicite, une seconde instance a terminé successivement :

1. J4 phase 2, réponse `EVENT_DETAILS` persistée et parsée dans le snapshot `315` ;
2. J5, réponses `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` persistées et parsées dans
   les snapshots `316`, `317` et `318` ;
3. J3, six pages `SCHEDULED_EVENTS` persistées et parsées dans l'ordre sous les snapshots `319` à
   `324`, avec `hasNextPage=false` sur la sixième page.

Ces gestes représentent dix-huit tentatives fournisseur humaines sur les deux instances :
dix-sept réponses persistées et une tentative J5 terminée par timeout. Aucun appel n'a été
automatique et aucun de ces transports n'a été déclenché par une création, une décision ou un
téléchargement J7. Les captures et les preuves minimisées téléchargées restent hors Git.

L'inspection opérateur des réponses J3 a aussi confirmé le sens de la forme fournisseur
`scheduled` : l'endpoint met en avant les compétitions disponibles pour la date, mais ne retourne
pas de rencontres programmées. Le parseur avait déjà le bon comportement
`SCHEDULED_TOURNAMENT_LIST` et n'inventait aucun `ScheduledEvent`; seul le retour J4 « 0 événement »
était ambigu. Il expose désormais `scheduledTournamentCount` et affiche que le nombre correspond
aux compétitions, qu'aucun match n'est fourni ou inventé et qu'aucune observation J4 n'est créée.

La correction d'affichage et son compteur sont couverts par treize tests ciblés locaux. Les suites
complètes consignées plus haut précèdent cette clarification sans effet sur le réseau, la base ou
les migrations ; leur relance finale reste obligatoire après la fin de la session opérateur.

```text
J7_COMBINED_HUMAN_FIRST_INSTANCE=SAFE_STOP_J3_PASS_J4_PASS_J5_TRANSPORT_TIMEOUT_NO_RETRY
J7_COMBINED_HUMAN_SECOND_INSTANCE=PASS_J4_PHASE2_J5_THREE_FAMILIES_J3_SIX_PAGES
J7_COMBINED_HUMAN_FIRST_TWO_INSTANCES_MANUAL_PROVIDER_ATTEMPTS=18
J7_COMBINED_HUMAN_FIRST_TWO_INSTANCES_PERSISTED_PROVIDER_RESPONSES=17
J7_COMBINED_HUMAN_FIRST_TWO_INSTANCES_TIMEOUTS=1
J7_COMBINED_HUMAN_AUTOMATIC_PROVIDER_CALLS=0
J7_COMBINED_HUMAN_J7_TRIGGERED_PROVIDER_CALLS=0
J7_J3_SCHEDULED_SHAPE=COMPETITIONS_WITHOUT_SCHEDULED_MATCHES
J7_J3_SCHEDULED_J4_OBSERVATIONS_CREATED=0_BY_CONTRACT
J7_J3_OPERATOR_MESSAGE=PASS_EXPLICIT_COMPETITION_COUNT_AND_NO_MATCH
J7_POST_CLARIFICATION_TARGETED_TESTS=PASS_13_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J7_POST_CLARIFICATION_FULL_STANDARD_RERUN=PASS_457_TESTS_0_FAILURES_0_ERRORS_2_SKIPPED_WINDOWS_SYMLINK
J7_COMBINED_HUMAN_J6_J7_SINGLE_RUN_CLOSURE_BEFORE_THIRD_INSTANCE=HISTORICAL_OPEN_CLOSED_BY_THIRD_INSTANCE
```

### 5.3 Preuve humaine bout en bout dans une seule instance

Une troisième séquence opérateur, commencée le 2026-08-19 avant minuit et terminée le
2026-08-20 après minuit en zone `Europe/Paris`, ferme le besoin de coexistence dans une même
instance. Le passage de la date courante du formulaire au 20 août n'est pas un changement de
campagne : les clés des six snapshots J3 restent explicitement liées à `date=2026-08-19`.

Dans cette instance unique, l'opérateur a successivement vérifié :

1. J3, six pages fournisseur `325` à `330`, toutes `HTTP 200` et `PARSED`, sans cache hit ni retry,
   avec terminaison sur `hasNextPage=false`, arrêt global réappliqué et circuit `LOCKED` ; le
   reparsage local confirme notamment 100 compétitions sur les snapshots `325` et `329`, puis 61
   sur `330`, sans rencontre inventée ni observation J4 créée ;
2. J5 sur Atlético Madrid — Málaga CF (`16421055`), avec les trois appels ordonnés : statistiques
   snapshot `331` / observation `207` à `COMPLETE 250/250`, incidents snapshot `332` / observation
   `208` à `COMPLETE 79/79`, compositions snapshot `333` / observation `209` à
   `COMPLETE 95/95`, puis statut `COMPLETED_LOCKED` ;
3. J6 en lecture locale, avec comparaison append-only incidents `190 → 208`, de
   `EMPTY_VALID 0/0` à `COMPLETE 79/79`, sans appel fournisseur ;
4. J4 phase 2 sur le même événement, avec une nouvelle version `finished` issue du snapshot `334`,
   observation d'état courante `114`, détail local `107`, puis statut `COMPLETED_LOCKED` ;
5. J7, avec création du candidat `696c1965-183e-43ba-ba47-4ee4d2ad5481`, inspection des cinq
   sources `PRESENT`, zéro avertissement, validation humaine, puis téléchargement réussi du fichier
   `.validated.json`, sans transport fournisseur.

Le téléchargement J7 a été vérifié indépendamment hors de l'application. Le fichier mesure
37 239 octets et son SHA-256 vaut
`37784bb4187fb80de06b765d8a9cb54b36f8035ed24eee82f9d6791c5a388880`, identique à l'empreinte
affichée par le manifeste persisté. Il contient uniquement les racines `manifest` et `data`, porte
le statut `HUMAN_VALIDATED`, conserve le `dataSha256`
`655f01a9e468e171cc6cef2a6c58977929ec8d24c1da4ac7a0f374aaccdc6ac1` et le
`sourceSetSha256` `0b81ff01a186e95cec8d311ddd215fdddc6983b34853356b8b1d2d251c830d15`.
Les cinq sources sont ordonnées `EVENT_STATE`, `EVENT_DETAILS`, `EVENT_STATISTICS`,
`EVENT_INCIDENTS`, `EVENT_LINEUPS` et référencent `334`, `334`, `331`, `332`, `333`. Le fichier est
UTF-8 sans BOM, utilise uniquement LF, se termine par LF et ne contient aucune clé interdite ni
motif sensible détecté.

Cette séquence ajoute dix appels fournisseur humains : six J3, trois J5 et un J4 phase 2. Avec les
deux séquences précédentes, le total observé est de vingt-huit tentatives manuelles, dont
vingt-sept réponses persistées et un timeout sûr sans retry. J6 et J7 restent purement locaux ; le
nombre d'appels automatiques et le nombre d'appels déclenchés par J7 restent tous deux nuls.

```text
J7_COMBINED_HUMAN_THIRD_INSTANCE=PASS_J3_SIX_PAGES_J5_COMPLETE_J6_LOCAL_J4_PHASE2_J7_VALIDATED_DOWNLOADED
J7_COMBINED_HUMAN_SINGLE_INSTANCE_J3_J4_J5_J6_J7=PASS
J7_COMBINED_HUMAN_THIRD_INSTANCE_MANUAL_PROVIDER_ATTEMPTS=10
J7_COMBINED_HUMAN_MANUAL_PROVIDER_ATTEMPTS=28
J7_COMBINED_HUMAN_PERSISTED_PROVIDER_RESPONSES=27
J7_COMBINED_HUMAN_TIMEOUTS=1
J7_COMBINED_HUMAN_AUTOMATIC_PROVIDER_CALLS=0
J7_COMBINED_HUMAN_J7_TRIGGERED_PROVIDER_CALLS=0
J7_COMBINED_HUMAN_J6_J7_SINGLE_RUN_CLOSURE=PASS
J7_J3_J4_J5_LOCKED_HUMAN_REVIEW=PASS
J7_PROVIDER_16421055_EXPORT_ID=696c1965-183e-43ba-ba47-4ee4d2ad5481
J7_PROVIDER_16421055_EXPORT_STATUS=HUMAN_VALIDATED
J7_PROVIDER_16421055_SOURCE_SNAPSHOTS=331,332,333,334
J7_PROVIDER_16421055_WARNINGS=NONE
J7_PROVIDER_16421055_VALIDATED_SIZE_BYTES=37239
J7_PROVIDER_16421055_DATA_SHA256=655f01a9e468e171cc6cef2a6c58977929ec8d24c1da4ac7a0f374aaccdc6ac1
J7_PROVIDER_16421055_SOURCE_SET_SHA256=0b81ff01a186e95cec8d311ddd215fdddc6983b34853356b8b1d2d251c830d15
J7_PROVIDER_16421055_FILE_SHA256=37784bb4187fb80de06b765d8a9cb54b36f8035ed24eee82f9d6791c5a388880
J7_PROVIDER_16421055_DOWNLOAD_SHA256_MATCH=PASS
J7_PROVIDER_16421055_UTF8_LF=PASS
J7_PROVIDER_16421055_SENSITIVE_FINDINGS=0
J7_PROVIDER_16421055_J7_TRANSPORT=NONE
```

## 6. Qualification humaine exécutée

Après passage de toutes les portes techniques, l'opérateur a suivi
`docs/runbooks/J7-CANONICAL-EVENT-EXPORT.md` :

1. créer et rejeter un candidat synthétique ;
2. recréer, valider et télécharger un export synthétique ;
3. créer, inspecter, valider et télécharger l'événement fournisseur local `16691018` sans nouvel
   appel ;
4. confirmer avertissements, provenances, hashes, parseurs, absence de brut/secrets/sessions,
   verrous J3/J4/J5 et zéro appel SofaScore.

```text
J7_SYNTHETIC_REJECTION=PASS_REJECTED_RETAINED_NOT_DOWNLOADABLE
J7_SYNTHETIC_VALIDATION=PASS_HUMAN_VALIDATED_DOWNLOADED
J7_PROVIDER_16691018_SELECTION_AND_WARNINGS=PASS
J7_PROVIDER_16691018_REJECTION=PASS_REJECTED_RETAINED_NOT_DOWNLOADABLE
J7_PROVIDER_16691018_VALIDATION=PASS_HUMAN_VALIDATED_DOWNLOADED
J7_SENSITIVE_CONTENT_HUMAN_REVIEW=PASS_PROVIDER_AND_SYNTHETIC
J7_J3_J4_J5_LOCKED_HUMAN_REVIEW=PASS
J7_EXPORT_GESTURE_PROVIDER_CALLS=0
J7_MANUAL_CAMPAIGN_PROVIDER_ATTEMPTS=28
J7_AUTOMATIC_PROVIDER_CALLS=0
J7_HUMAN_QUALIFICATION=PASS
```

La recette humaine est fermée. Le Work Order demeure dans `active` uniquement jusqu'à la branche
poussée, la PR revue et son état `CLEAN/MERGEABLE`.

## 7. Première preuve humaine partielle — fournisseur complet `16707704`

Le 2026-08-19, l'opérateur a ouvert depuis la fiche locale Fenerbahçe — Olympique lyonnais le
parcours J7, créé le candidat, inspecté ses preuves, saisi la confirmation exacte, obtenu le statut
`HUMAN_VALIDATED`, puis téléchargé le JSON sans erreur constatée. Les dix captures fournies restent
hors dépôt. Le JSON téléchargé et le terminal conservé sous `exports/` restent eux aussi hors Git.

Une relecture indépendante du fichier téléchargé confirme sa stricte identité octet par octet avec
le terminal local. La taille et le SHA-256 correspondent à l'aperçu après décision. Le JSON est en
UTF-8, se termine par LF sans CR, ne possède que les racines `manifest` et `data`, et conserve les
cinq sources dans l'ordre contractuel. Les hashes du seul objet `data` et du tableau `sources` ont
été recalculés depuis les octets téléchargés et correspondent au manifeste. Le scan des clés et du
texte avec les règles J7 ne relève aucun contenu sensible. Le téléchargement ayant relu exactement
ce terminal, le contrôle de schéma exécuté par la route porte sur les mêmes octets.

```text
J7_FIRST_HUMAN_EXPORT_RESULT=PASS_DOWNLOADED_WITHOUT_ERROR
J7_FIRST_HUMAN_EXPORT_SCOPE=SUPPLEMENTAL_COMPLETE_PROVIDER_EVENT
J7_FIRST_HUMAN_EXPORT_CANONICAL_EVENT_ID=5c497a2c-9963-3de6-908c-1c076531b235
J7_FIRST_HUMAN_EXPORT_PROVIDER_EVENT_ID=16707704
J7_FIRST_HUMAN_EXPORT_ID=b99b7062-4056-480a-8865-1655b851f9ed
J7_FIRST_HUMAN_EXPORT_STATUS=HUMAN_VALIDATED
J7_FIRST_HUMAN_EXPORT_GENERATED_AT=2026-08-19T13:37:05.703559Z
J7_FIRST_HUMAN_EXPORT_DECIDED_AT=2026-08-19T13:38:08.434399Z
J7_FIRST_HUMAN_EXPORT_CANDIDATE_SIZE_BYTES=34716
J7_FIRST_HUMAN_EXPORT_VALIDATED_SIZE_BYTES=34739
J7_FIRST_HUMAN_EXPORT_DATA_SHA256=ce7c5fdc8be8cb7b9638e082f8388f12a51c5cb69030b778530377616d20b0d6
J7_FIRST_HUMAN_EXPORT_SOURCE_SET_SHA256=2638a66f70f1462dbd45eecc44bfd05b01f784d7bafe92f583145814b69b9caa
J7_FIRST_HUMAN_EXPORT_CANDIDATE_SHA256=5d55a1a27a0c8c04ed1fbfab1389778bde0ecbe821338cf777c7acaf3db6e6e1c
J7_FIRST_HUMAN_EXPORT_FILE_SHA256=2f60b37151e81625a96ab9426f5474f4600191d102c398a4be8cfd96c5a230a9
J7_FIRST_HUMAN_EXPORT_DOWNLOAD_SHA256_MATCH=PASS
J7_FIRST_HUMAN_EXPORT_HISTORY=PASS_ONE_HUMAN_VALIDATED
J7_FIRST_HUMAN_EXPORT_DATA_SHA256_MATCH=PASS
J7_FIRST_HUMAN_EXPORT_SOURCE_SET_SHA256_MATCH=PASS
J7_FIRST_HUMAN_EXPORT_SCHEMA_REVALIDATED_ON_DOWNLOAD=PASS
J7_FIRST_HUMAN_EXPORT_UTF8_LF=PASS
J7_FIRST_HUMAN_EXPORT_SOURCES=EVENT_STATE,EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
J7_FIRST_HUMAN_EXPORT_SOURCE_KIND=PROVIDER_SNAPSHOT
J7_FIRST_HUMAN_EXPORT_SOURCE_SNAPSHOTS=261,262,263,270
J7_FIRST_HUMAN_EXPORT_COMPLETENESS=STATISTICS_COMPLETE,INCIDENTS_COMPLETE,LINEUPS_COMPLETE
J7_FIRST_HUMAN_EXPORT_RAW_PAYLOAD_STATE=RETAINED_METADATA_ONLY
J7_FIRST_HUMAN_EXPORT_WARNINGS=NONE
J7_FIRST_HUMAN_EXPORT_SENSITIVE_FINDINGS=0
J7_FIRST_HUMAN_EXPORT_J7_TRANSPORT=NONE
J7_FIRST_HUMAN_EXPORT_ARTIFACTS_VERSIONED=NO
J7_FIRST_HUMAN_EXPORT_RUNBOOK_CLOSURE=SUPPLEMENTAL_PROOF_ONLY
```

Cette preuve qualifie un événement fournisseur complet supplémentaire. Elle ne remplace pas le
rejet synthétique, la validation synthétique ni la validation suivie du téléchargement de
`16691018`. L'inspection des avertissements et le rejet de ce dernier sont consignés séparément en
section 8 ; les gestes terminaux complémentaires qui ont depuis fermé la recette sont consignés en
sections 9 et 10.

## 8. Preuve humaine partielle — rejet fournisseur ciblé `16691018`

Le 2026-08-19, l'opérateur a ouvert depuis la fiche locale Cittadella — Atalanta U23 le parcours
J7, créé et inspecté le candidat, vérifié les trois avertissements attendus, saisi la confirmation
exacte de rejet et un motif local borné, puis obtenu le statut terminal `REJECTED`. Les six captures
fournies restent hors dépôt et le terminal runtime reste ignoré par Git. Le motif est uniquement
qualifié par sa présence et sa longueur ; son texte n'est pas recopié dans ce rapport.

La relecture indépendante confirme un fichier UTF-8 strict terminé par LF sans CR, avec les seules
racines `manifest` et `data`. Les cinq sources `PROVIDER_SNAPSHOT` sont dans l'ordre contractuel et
leur état brut déclaré est `RETAINED` sans octets bruts. Les statistiques sont `UNAVAILABLE`, les
incidents et compositions sont `PRESENT` avec complétude `PARTIAL`. Les hashes de `data` et du
tableau `sources` ont été recalculés à l'identique ; le scan des octets, du texte et des clés avec
les règles J7 ne relève rien de sensible.

Après décision, un seul terminal `.rejected.json` subsiste : ni candidat ni fichier validé ne sont
présents. Une requête explicite vers sa route locale de téléchargement renvoie `404`, un corps vide,
aucun `Content-Disposition`, et conserve les en-têtes `no-store`, `no-cache`, expiration immédiate
et `X-Robots-Tag: noindex, nofollow, noarchive`.

```text
J7_PROVIDER_16691018_LOCAL_SELECTION=PASS
J7_PROVIDER_16691018_WARNINGS_REVIEW=PASS
J7_PROVIDER_16691018_REJECTION=PASS_SUPPLEMENTAL
J7_PROVIDER_16691018_CANONICAL_EVENT_ID=f4713f80-4769-3656-ba51-61d8ac1aa814
J7_PROVIDER_16691018_EXPORT_ID=b6b58dce-a739-4495-b3e5-1e3f3f091b5a
J7_PROVIDER_16691018_STATUS=REJECTED
J7_PROVIDER_16691018_GENERATED_AT=2026-08-19T13:53:56.941990Z
J7_PROVIDER_16691018_DECIDED_AT=2026-08-19T13:55:13.857431Z
J7_PROVIDER_16691018_SOURCE_SNAPSHOTS=187,188,189,192
J7_PROVIDER_16691018_CANDIDATE_SIZE_BYTES=28678
J7_PROVIDER_16691018_REJECTED_SIZE_BYTES=28767
J7_PROVIDER_16691018_DATA_SHA256=345244db14600e2ff7ab5b53ccd40296db53c3cc363e7f3b0622de5774485b6d
J7_PROVIDER_16691018_SOURCE_SET_SHA256=48357cc401d92b51468dd32357f645908b0262b7b33185fc2f9b74f623e036d6
J7_PROVIDER_16691018_CANDIDATE_SHA256=f5776c8d88fb14ef1eb97e3da8aa33a9093aa43825bfa71635f8aed671531fbf
J7_PROVIDER_16691018_REJECTED_SHA256=cb17f359e685aafa7dd24f9a076e7e608575066126ac832b94a72d2089362e8a
J7_PROVIDER_16691018_WARNINGS=UNAVAILABLE_COMPONENT:EVENT_STATISTICS,PARTIAL_COMPLETENESS:EVENT_INCIDENTS,PARTIAL_COMPLETENESS:EVENT_LINEUPS
J7_PROVIDER_16691018_REJECTION_REASON=SAFE_PRESENT_75_CHARACTERS
J7_PROVIDER_16691018_CANDIDATE_CLEANUP=PASS
J7_PROVIDER_16691018_REJECTED_DOWNLOAD=PASS_404_EMPTY_BODY
J7_PROVIDER_16691018_UTF8_LF=PASS
J7_PROVIDER_16691018_SENSITIVE_FINDINGS=0
J7_PROVIDER_16691018_J7_TRANSPORT=NONE
J7_PROVIDER_16691018_ARTIFACTS_VERSIONED=NO
J7_PROVIDER_16691018_HUMAN_VALIDATION=PASS_SEPARATE_RECREATED_EXPORT
```

Cette première preuve ferme l'inspection des avertissements et le rejet du cas fournisseur ciblé.
Sa recréation, sa validation puis son téléchargement sont consignés ci-dessous.

## 9. Preuve humaine finale — validation fournisseur ciblée `16691018`

Dans une nouvelle décision explicite, l'opérateur a recréé le candidat de Cittadella — Atalanta
U23, revu les mêmes trois avertissements, saisi la confirmation exacte, obtenu
`HUMAN_VALIDATED`, puis téléchargé le JSON sans erreur. Le terminal local et le téléchargement sont
identiques octet par octet. Le fichier est UTF-8 sans BOM, terminé par un unique LF, et ses seules
racines sont `manifest` et `data`.

Le recalcul indépendant de `dataSha256` correspond au manifeste. Les cinq sources sont ordonnées et
conservent les snapshots 187, 188, 189 et 192, avec statistiques `UNAVAILABLE` et incidents puis
compositions `PARTIAL`. Le scan ciblé ne relève ni clé interdite ni valeur sensible. Cette décision
ne déclenche aucun transport fournisseur.

```text
J7_PROVIDER_16691018_VALIDATED_EXPORT_ID=af9735bc-ccfa-419e-bfea-fb6e500bc5bb
J7_PROVIDER_16691018_VALIDATED_STATUS=HUMAN_VALIDATED
J7_PROVIDER_16691018_VALIDATED_GENERATED_AT=2026-08-19T23:01:28.313303Z
J7_PROVIDER_16691018_VALIDATED_DECIDED_AT=2026-08-19T23:02:03.081454Z
J7_PROVIDER_16691018_VALIDATED_SIZE_BYTES=28701
J7_PROVIDER_16691018_VALIDATED_SOURCE_SNAPSHOTS=187,188,189,192
J7_PROVIDER_16691018_VALIDATED_DATA_SHA256=345244db14600e2ff7ab5b53ccd40296db53c3cc363e7f3b0622de5774485b6d
J7_PROVIDER_16691018_VALIDATED_SOURCE_SET_SHA256=48357cc401d92b51468dd32357f645908b0262b7b33185fc2f9b74f623e036d6
J7_PROVIDER_16691018_VALIDATED_CANDIDATE_SHA256=a2ab71499eaede38b7c2d5da8be33c350b0f6af7aa1ecf020dcb358d16ea63d2
J7_PROVIDER_16691018_VALIDATED_FILE_SHA256=87ed3d0e17a3150bf3a7aee1834da35e40a8dbcafecb037032cdf893ec543063
J7_PROVIDER_16691018_VALIDATED_DOWNLOAD_MATCH=PASS_BYTE_FOR_BYTE
J7_PROVIDER_16691018_VALIDATED_DATA_SHA256_RECALCULATION=PASS
J7_PROVIDER_16691018_VALIDATED_UTF8_LF=PASS
J7_PROVIDER_16691018_VALIDATED_SENSITIVE_FINDINGS=0
J7_PROVIDER_16691018_VALIDATED_J7_TRANSPORT=NONE
J7_PROVIDER_16691018_VALIDATED_ARTIFACTS_VERSIONED=NO
```

## 10. Preuve humaine finale — rejet puis validation synthétiques

L'opérateur a chargé l'événement de démonstration `900001`, créé et inspecté un premier candidat,
puis l'a rejeté avec une confirmation exacte et un motif local présent et borné. Le terminal
`.rejected.json` est conservé par l'application, l'écran le déclare non téléchargeable et aucun
fichier rejeté n'est présent dans le dossier de téléchargements. Un second candidat a ensuite été
créé depuis le même état, validé humainement et téléchargé sans erreur.

Les deux terminaux ont exactement le même objet `data`, le même `dataSha256`, les mêmes cinq
provenances `SYNTHETIC_FIXTURE` dans l'ordre contractuel et le même `sourceSetSha256`. Ils portent
cinq avertissements `SYNTHETIC_SOURCE`, un par composant, et `rawPayloadState=NOT_APPLICABLE`.
Chaque fichier est UTF-8 sans BOM, terminé par un unique LF ; les recalculs de `dataSha256`
correspondent et le scan ciblé ne trouve ni clé interdite ni valeur sensible. Le téléchargement
validé est identique octet par octet au terminal local.

```text
J7_SYNTHETIC_CANONICAL_EVENT_ID=9740bb59-0207-31a3-a6ae-5c8463255887
J7_SYNTHETIC_PROVIDER_EVENT_ID=900001
J7_SYNTHETIC_REJECTED_EXPORT_ID=e6bd61ab-80e2-4a9d-854c-015ea37521a1
J7_SYNTHETIC_REJECTED_STATUS=REJECTED
J7_SYNTHETIC_REJECTED_SIZE_BYTES=8702
J7_SYNTHETIC_REJECTED_FILE_SHA256=b83e0cc6f05b0d934ab4f48d6bbc959e9624935cd3bbd4095701b03ccc44ca09
J7_SYNTHETIC_REJECTION_REASON=SAFE_PRESENT_4_CHARACTERS
J7_SYNTHETIC_REJECTED_DOWNLOAD=PASS_UI_NON_DOWNLOADABLE_AND_NO_EXTERNAL_FILE
J7_SYNTHETIC_VALIDATED_EXPORT_ID=563dbeb8-8660-422b-b214-e3b1b306aa6d
J7_SYNTHETIC_VALIDATED_STATUS=HUMAN_VALIDATED
J7_SYNTHETIC_VALIDATED_SIZE_BYTES=8707
J7_SYNTHETIC_VALIDATED_FILE_SHA256=fb637b4c87fc6a8cba230c6d4ad0928af3d52c1a793d379ae6ac9b9d1888b1f4
J7_SYNTHETIC_VALIDATED_DOWNLOAD_MATCH=PASS_BYTE_FOR_BYTE
J7_SYNTHETIC_DATA_SHA256=1a30148f03efce117bbe1cb8b30308eeab338741bbec392a4b5c58de488984a5
J7_SYNTHETIC_SOURCE_SET_SHA256=0021ac5425c1fed41c489f81d7346cbcfbaa45fa667dc581aad0d832b123edc5
J7_SYNTHETIC_REJECTED_VALIDATED_DATA_EQUIVALENCE=PASS
J7_SYNTHETIC_REJECTED_VALIDATED_SOURCE_EQUIVALENCE=PASS
J7_SYNTHETIC_WARNINGS=FIVE_SYNTHETIC_SOURCE
J7_SYNTHETIC_DATA_SHA256_RECALCULATION=PASS_BOTH_TERMINALS
J7_SYNTHETIC_UTF8_LF=PASS_BOTH_TERMINALS
J7_SYNTHETIC_SENSITIVE_FINDINGS=0
J7_SYNTHETIC_J7_TRANSPORT=NONE
J7_SYNTHETIC_ARTIFACTS_VERSIONED=NO
```

## 11. Conclusion de la recette humaine

Les gestes obligatoires du runbook sont tous acquis : rejet et validation synthétiques, validation
du cas fournisseur ciblé avec ses avertissements, vérification des téléchargements, invariance des
données, absence de contenu sensible, parcours combiné J3/J4/J5/J6/J7 et absence de transport J7.
La qualification humaine passe à `PASS`. La branche et la PR restent la seule porte ouverte avant
le passage du Work Order à `VALIDATED`.

```text
J7_HUMAN_QUALIFICATION=PASS
J7_WORK_ORDER_STATUS=READY_FOR_PR_REVIEW
J7_PR_CLEAN_MERGEABLE=PENDING
```
