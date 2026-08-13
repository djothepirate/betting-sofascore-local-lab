# J3 — Rapport Windows de qualification de la reprise fournisseur à la page 2

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date de qualification | 2026-08-13 |
| Environnement | Windows local |
| Jalon | J3 — Appel manuel borné |
| Work Order | `WO-SS-20260812-003` |
| Branche | `feat/j3-five-page-provider-call` |
| Commit de base de l’unité qualifiée | `092d4e3c2985d41b5cd08d724285506f6a5e1f23` |
| Libellé du commit | `feat: resume J3 qualification from page two` |
| Famille fournisseur | `SCHEDULED_EVENTS` |
| Date fournisseur | `2026-08-13` |
| Checkpoint local conservé | Page `1` |
| Première page fournisseur de la reprise | Page `2` |
| Dernière page autorisée | Page `5` |
| Page réellement appelée pendant la reprise | Page `2` |
| Pages non appelées après l’incident | Pages `3`, `4` et `5` |
| Cookies, jetons, compte ou session | `NONE` |
| Retry automatique | `NO` |
| Résultat du transport page 2 | `PASS — HTTP 200` |
| Résultat de persistance page 2 | `PASS — INSERTED` |
| Résultat de compatibilité du parseur | `FAIL — SCHEMA_INCOMPATIBLE` |
| Résultat des garde-fous terminaux | `PASS` |
| Résultat global | `FAILED_SAFELY` |
| Décision documentaire | `J3_REMAINS_IN_DEVELOPMENT` |

## 2. Objet du rapport

Ce rapport consigne la qualification humaine de la reprise fournisseur J3 à partir de la page 2.
Cette reprise prolonge la qualification initiale du même jour, dont la page 1 avait été transportée,
persistée puis arrêtée de façon sûre sur une incompatibilité de schéma.

La présente qualification devait vérifier :

- la relecture locale et la validation du checkpoint de page 1 par le parseur corrigé ;
- l’absence de nouvelle requête pour la page 1 ;
- la préparation et la confirmation explicites d’une reprise limitée aux pages 2 à 5 ;
- le déclenchement de la reprise par une action finale séparée ;
- la persistance des octets bruts de chaque page avant parsing ;
- l’arrêt au premier incident, sans retry ni tentative d’une page suivante ;
- la réapplication de l’arrêt global et du verrou terminal ;
- le blocage persistant de toute seconde reprise dès qu’une page 2 existe ;
- la production d’une preuve terminale minimisée de version 2.

La future interrogation complète et répétable depuis l’interface graphique reste hors du périmètre de
cette qualification.

## 3. Gouvernance et limites maintenues

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Les limites suivantes sont restées actives pendant les tests et la qualification :

- application exposée uniquement sur `127.0.0.1:8087` ;
- PostgreSQL local disponible ;
- connecteur général toujours verrouillé ;
- seul le chemin J3 spécialisé `SCHEDULED_EVENTS` utilisable ;
- date strictement limitée à `2026-08-13` ;
- reprise strictement limitée aux pages 2 à 5 ;
- concurrence maximale `1` ;
- délai minimal de trois secondes entre deux départs de page ;
- aucune découverte dynamique de pagination ;
- aucun cookie, jeton, compte ou état de session fournisseur ;
- aucun proxy, aucune redirection, aucun polling et aucun live ;
- aucun retry automatique ;
- aucune répétition autorisée après la présence d’un snapshot de page 2.

## 4. Sources de preuve et intégrité

### 4.1 Captures opérateur

Les captures fournies couvrent les états suivants :

1. démarrage sûr avec arrêt global actif et `STARTUP_LOCK` ;
2. disponibilité du checkpoint local de page 1 ;
3. levée distincte de l’arrêt global ;
4. activation distincte du circuit ;
5. préparation de la reprise pour la date qualifiée ;
6. saisie de la phrase éphémère et acquittement explicite ;
7. état `CONFIRMED_READY` avant le bouton final ;
8. déclenchement humain de la reprise unique ;
9. arrêt terminal sur la page 2 ;
10. affichage de la preuve minimisée et disponibilité de son téléchargement.

Les captures restent hors Git. Le présent rapport n’en reproduit que les observations nécessaires à
la qualification.

### 4.2 Fichier de preuve minimisée

| Élément | Valeur |
|---|---|
| Nom local reçu | `J3-MINIMIZED-EVIDENCE-2026-08-13 (1).txt` |
| Version du format | `2` |
| Taille du fichier | `1069` octets |
| SHA-256 du fichier | `9D2762ED7095266495D3A206350150CC8E9DADE629ADEA1E0F2EA9A04879AC87` |
| Horodatage de génération | `2026-08-13T21:28:05.659152900Z` |
| Payload brut inclus | `NO` |
| URI fournisseur incluse | `NO` |
| En-têtes inclus | `NO` |
| Identifiant de confirmation inclus | `NO` |

Le fichier téléchargé reste hors Git. Son empreinte est consignée afin de permettre un contrôle
d’intégrité ultérieur sans versionner la preuve locale.

## 5. Validation technique hors ligne de l’unité

### 5.1 Correction du câblage Spring

Le premier démarrage de l’unité a révélé une sélection ambiguë entre les constructeurs de
`J3QualificationResumePolicy`. Le constructeur de production à deux dépendances a été sélectionné
explicitement par injection Spring.

La correction a été validée par :

- une compilation complète ;
- l’exécution réussie de la suite standard ;
- le démarrage de l’application locale ;
- la résolution du checkpoint PostgreSQL ;
- l’affichage de l’état `REPRISE J3 PAGE 2 PRÊTE` dans le tableau de bord.

### 5.2 Tests automatisés et contrôles statiques

| Contrôle | Commande ou source | Résultat |
|---|---|---|
| Préflight Windows | `scripts/Preflight-Local.ps1` via `scripts/Verify-Local.ps1` | `PASS` |
| Cible Java | contrôle du préflight | `PASS — Java 25` |
| Scan des garde-fous source | `scripts/Verify-Local.ps1` | `PASS` |
| Suite Maven standard | `mvnw.cmd clean verify` via `scripts/Verify-Local.ps1` | `PASS` |
| Rapports Surefire | `target/surefire-reports/TEST-*.xml` | `33` suites, `142` tests |
| Échecs standards | rapports Surefire | `0` |
| Erreurs standards | rapports Surefire | `0` |
| Tests ignorés | rapports Surefire | `0` |
| Vérification du diff | validation technique de l’unité | `PASS` |
| Recherche de secret | validation technique de l’unité | `PASS` |
| Adresse serveur | configuration et affichage local | `PASS — 127.0.0.1` |
| Appel SofaScore pendant les tests standards | garde-fous de test | `NO` |

Les rapports Surefire contrôlés après la correction du câblage Spring ont été générés entre
`2026-08-13T23:09:13+02:00` et `2026-08-13T23:09:18+02:00`.

### 5.3 Profil d’intégration

La commande suivante a été tentée pendant la validation technique de l’unité :

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Testcontainers n’a pas pu créer la base éphémère, le compte d’exécution ne disposant pas de l’accès au
tube Windows `docker_engine`. L’échec est intervenu avant l’exécution de `FlywayMigrationIT` :

```text
INTEGRATION_PROFILE_STARTED=YES
FLYWAY_MIGRATION_IT_EXECUTED=NO
FUNCTIONAL_INTEGRATION_RESULT=NOT_ESTABLISHED
BLOCKER=WINDOWS_DOCKER_ENGINE_PIPE_ACCESS
SOFASCORE_NETWORK_CALLS_EXECUTED=NO
```

Ce résultat environnemental n’est pas comptabilisé comme un échec fonctionnel du lecteur de
checkpoint. La qualification humaine a toutefois exercé PostgreSQL local avec succès, sans se
substituer au profil d’intégration automatisé.

## 6. État préalable observé dans l’interface

### 6.1 Tableau de bord et données locales

L’interface a confirmé :

```text
CONNECTOR_STATUS=REPRISE_J3_PAGE_2_READY
BASE_URL=CONFIGURED_NOT_DISPLAYED
SERVER_ADDRESS=127.0.0.1:8087
POSTGRESQL=AVAILABLE
FLYWAY_MIGRATIONS=2
SNAPSHOTS_BEFORE_RESUME=1
INCIDENTS_BEFORE_RESUME=1
PAGE_1_HTTP_STATUS=200
PAGE_1_LATENCY_MS=640
```

Le corpus synthétique hors ligne était également disponible :

```text
FIXTURES_AVAILABLE=10/10
PARSED=6
EXPECTED_SCHEMA_BREAKS=3
EXPECTED_UNEXPECTED_CONTENT=1
LOAD_FAILURES=0
PARSER=scheduled-events-v1
PROVIDER_SCHEMA=VALIDATED
```

### 6.2 Checkpoint de page 1

La politique de reprise a relu le snapshot local de page 1 et a confirmé :

- l’unicité du snapshot ;
- le statut HTTP `2xx` ;
- la cohérence entre sa taille, ses octets et son empreinte SHA-256 ;
- le parsing local `PARSED` avec `scheduled-events-v1` ;
- la présence de `hasNextPage=true` ;
- l’absence de snapshot préalable pour les pages 2 à 5.

La page 1 n’a pas été redemandée au fournisseur pendant la reprise.

## 7. Tests opérateur du parcours de confirmation

| ID | Test effectué | Résultat observé | Statut |
|---|---|---|---|
| `J3-RESUME-WIN-01` | Vérifier le démarrage sûr | Arrêt global `ACTIF`, circuit `LOCKED`, motif `STARTUP_LOCK` | `PASS` |
| `J3-RESUME-WIN-02` | Vérifier le checkpoint local | Page 1 validée, confirmation exigée avant transport | `PASS` |
| `J3-RESUME-WIN-03` | Lever l’arrêt global | Arrêt global `LEVÉ`, circuit toujours `LOCKED` | `PASS` |
| `J3-RESUME-WIN-04` | Activer le circuit séparément | Circuit `CLOSED`, activation opérateur `OUI` | `PASS` |
| `J3-RESUME-WIN-05` | Préparer la date qualifiée | Date `2026-08-13`, portée pages `2-5` | `PASS` |
| `J3-RESUME-WIN-06` | Contrôler la clé d’intention | `SCHEDULED_EVENTS|date=2026-08-13|pages=2-5` | `PASS` |
| `J3-RESUME-WIN-07` | Recopier la phrase éphémère | Phrase exacte acceptée avant expiration | `PASS` |
| `J3-RESUME-WIN-08` | Acquitter les limites de la reprise | Date, checkpoint, pages 2-5 et absence de session explicitement confirmés | `PASS` |
| `J3-RESUME-WIN-09` | Confirmer sans transporter | État `CONFIRMED_READY`, aucun transport exécuté par la confirmation seule | `PASS` |
| `J3-RESUME-WIN-10` | Contrôler l’action finale distincte | Bouton final limité à une reprise unique pages 2 à 5 | `PASS` |
| `J3-RESUME-WIN-11` | Contrôler le catalogue | Seul `SCHEDULED_EVENTS` est appelable ; autres familles absentes/non appelables | `PASS` |

## 8. Qualification réelle de la reprise

### 8.1 Déclenchement

L’opérateur a utilisé le bouton final après les étapes de réarmement, d’activation, de préparation et
de confirmation. La séquence a commencé directement à la page 2.

```text
QUALIFICATION_SCOPE=PAGES_1_2_3_4_5
VERIFIED_LOCAL_CHECKPOINT_PAGES=1
PROVIDER_RESUME_FIRST_PAGE=2
PAGE_1_REPEATED=NO
FINAL_ACTION=HUMAN_TRIGGERED
```

### 8.2 Transport et persistance de la page 2

La page 2 a été reçue avec succès, puis persistée avant parsing :

```text
PAGE_2_REQUESTED_AT=2026-08-13T21:28:05.074699100Z
PAGE_2_RECEIVED_AT=2026-08-13T21:28:05.477000800Z
PAGE_2_HTTP_STATUS=200
PAGE_2_LATENCY_MS=402
PAGE_2_SNAPSHOT_RECORDED=YES
PAGE_2_SNAPSHOT_ID=2
PAGE_2_PERSISTENCE_OUTCOME=INSERTED
PAGE_2_PAYLOAD_SIZE_BYTES=189621
PAGE_2_PAYLOAD_SHA256=c4b23f6f77e0798eade031a0ed0eec9c0d49e63d76b85c8e762679c04cf32a04
```

Ces valeurs démontrent la réussite du transport et de la persistance. Elles ne démontrent pas la
compatibilité du document avec le parseur.

### 8.3 Parsing et arrêt de séquence

Le parseur a classé le snapshot de page 2 comme incompatible :

```text
PAGE_2_SCHEMA_STATUS=SCHEMA_INCOMPATIBLE
PAGE_2_TERMINAL_CODE=SCHEMA_INCOMPATIBLE
TERMINAL_STATE=FAILED
FAILED_PAGE=2
AUTOMATIC_RETRY_EXECUTED=NO
```

L’orchestrateur s’est arrêté sur ce premier incident. Les pages 3, 4 et 5 n’ont pas été demandées.
Le compteur affiché est resté à `1 / 5`, la page 1 étant le seul checkpoint qualifié et complété.

## 9. Validation des politiques terminales

| ID | Politique contrôlée | Résultat observé | Statut |
|---|---|---|---|
| `J3-RESUME-TERM-01` | Arrêt au premier incident | Arrêt sur la page 2 | `PASS` |
| `J3-RESUME-TERM-02` | Absence de retry | `AUTOMATIC_RETRY_EXECUTED=NO` | `PASS` |
| `J3-RESUME-TERM-03` | Absence de page suivante | Pages 3, 4 et 5 non tentées | `PASS` |
| `J3-RESUME-TERM-04` | Réapplication de l’arrêt global | `FINAL_GLOBAL_STOP=ACTIVE` | `PASS` |
| `J3-RESUME-TERM-05` | Verrouillage du circuit | `FINAL_CIRCUIT_STATE=LOCKED` | `PASS` |
| `J3-RESUME-TERM-06` | Motif terminal | `QUALIFICATION_TERMINAL_LOCK` | `PASS` |
| `J3-RESUME-TERM-07` | Blocage d’une seconde reprise | `J3_RESUME_ALREADY_ATTEMPTED` | `PASS` |
| `J3-RESUME-TERM-08` | Conservation du snapshot avant parsing | Snapshot `2`, résultat `INSERTED` | `PASS` |
| `J3-RESUME-TERM-09` | Minimisation de la preuve | Aucun payload, URI, en-tête ou identifiant de confirmation | `PASS` |

Après la séquence, le tableau de bord a confirmé :

```text
CONNECTOR_STATUS=CONFIG_ENABLED_BUT_QUALIFICATION_BLOCKED
POSTGRESQL=AVAILABLE
SNAPSHOTS_AFTER_RESUME=2
INCIDENTS_AFTER_RESUME=2
LAST_RECORDED_ENDPOINT=SCHEDULED_EVENTS
LAST_RECORDED_HTTP_STATUS=200
LAST_RECORDED_LATENCY_MS=402
FINAL_GLOBAL_STOP=ACTIVE
FINAL_CIRCUIT_STATE=LOCKED
FINAL_CIRCUIT_REASON=QUALIFICATION_TERMINAL_LOCK
PERSISTENT_BLOCKER=J3_RESUME_ALREADY_ATTEMPTED
```

## 10. Matrice de résultat consolidée

| Domaine | Attendu | Observé | Résultat |
|---|---|---|---|
| Démarrage local | application disponible sur loopback | `127.0.0.1:8087` | `PASS` |
| Câblage Spring | politique de reprise injectable | application démarrée après sélection explicite du constructeur | `PASS` |
| PostgreSQL | stockage local disponible | `AVAILABLE` | `PASS` |
| Checkpoint page 1 | relu et validé localement | page 1 qualifiée | `PASS` |
| Répétition page 1 | interdite | aucune requête page 1 | `PASS` |
| Confirmation | explicite et séparée | intention `CONFIRMED_READY` avant transport | `PASS` |
| Transport page 2 | requête unique | HTTP `200`, `402 ms` | `PASS` |
| Persistance page 2 | avant parsing | snapshot `2`, `INSERTED` | `PASS` |
| Parsing page 2 | compatible | `SCHEMA_INCOMPATIBLE` | `FAIL` |
| Page 3 | non appelée après incident page 2 | non appelée | `PASS` |
| Page 4 | non appelée après incident page 2 | non appelée | `PASS` |
| Page 5 | non appelée après incident page 2 | non appelée | `PASS` |
| Retry | aucun | aucun | `PASS` |
| Arrêt global terminal | actif | actif | `PASS` |
| Circuit terminal | verrouillé | verrouillé | `PASS` |
| Répétition de la reprise | bloquée | `J3_RESUME_ALREADY_ATTEMPTED` | `PASS` |
| Preuve minimisée | disponible sans contenu sensible | version `2`, 1069 octets | `PASS` |
| Lot pages 1 à 5 complet | cinq pages qualifiées | seule la page 1 est complète ; arrêt sur page 2 | `FAIL` |

## 11. Observation d’interface non bloquante

Le message d’interface indique :

```text
Reprise arrêtée avant la page 2 (SCHEMA_INCOMPATIBLE)
```

La preuve démontre toutefois que la page 2 a été appelée, reçue et persistée avant le parsing. Une
formulation plus exacte serait :

```text
Reprise arrêtée sur la page 2 après persistance (SCHEMA_INCOMPATIBLE)
```

Cette imprécision de libellé n’altère ni l’état terminal, ni les métadonnées de preuve, ni les
garde-fous observés.

## 12. Interprétation et décision

La qualification confirme séparément trois résultats :

1. **transport réussi** : la page 2 a répondu en HTTP `200` ;
2. **persistance réussie** : le snapshot brut a été enregistré avant parsing ;
3. **parsing incompatible** : `scheduled-events-v1` n’accepte pas encore le schéma qualifié de la
   page 2.

Le résultat global est donc `FAILED_SAFELY`, et non un échec de transport. Les politiques de sécurité
ont fonctionné conformément au Work Order : arrêt immédiat, aucun retry, aucune tentative des pages 3
à 5, arrêt global réappliqué, circuit verrouillé, preuve minimisée disponible et seconde reprise
interdite.

La qualification complète des cinq pages ne peut pas être déclarée terminée. J3 reste au statut
`IN_DEVELOPMENT`.

La prochaine unité technique devra :

- analyser hors ligne le snapshot local de page 2 déjà persisté ;
- adapter `scheduled-events-v1` au schéma observé sans relancer d’appel fournisseur ;
- ajouter des fixtures synthétiques minimisées et des tests de rupture correspondants ;
- réexécuter la validation standard et, dans un terminal Windows disposant de Docker, le profil
  d’intégration ;
- soumettre toute éventuelle reprise à partir de la page 3 à une décision explicite distincte ;
- ne pas ouvrir à ce stade l’interrogation graphique répétable.

## 13. Bilan final

```text
J3_PAGE_TWO_RESUME_QUALIFICATION=FAILED_SAFELY
J3_PAGE_ONE_LOCAL_CHECKPOINT=PASS
J3_PAGE_ONE_PROVIDER_CALL_REPEATED=NO
J3_PAGE_TWO_TRANSPORT=PASS
J3_PAGE_TWO_HTTP_STATUS=200
J3_PAGE_TWO_PERSISTENCE=PASS
J3_PAGE_TWO_SCHEMA=SCHEMA_INCOMPATIBLE
J3_PAGES_THREE_TO_FIVE_REQUESTED=NO
J3_AUTOMATIC_RETRY_EXECUTED=NO
J3_FINAL_GLOBAL_STOP=ACTIVE
J3_FINAL_CIRCUIT_STATE=LOCKED
J3_FINAL_CIRCUIT_REASON=QUALIFICATION_TERMINAL_LOCK
J3_REPEAT_RESUME=BLOCKED
J3_MINIMIZED_EVIDENCE=PASS
J3_STANDARD_TESTS=142
J3_STANDARD_FAILURES=0
J3_STANDARD_ERRORS=0
J3_INTEGRATION_PROFILE=BLOCKED_BEFORE_IT_BY_DOCKER_PIPE_ACCESS
J3_PROVIDER_PAYLOAD_IN_GIT=NO
J3_PROVIDER_URI_IN_GIT=NO
J3_COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO
J3_WORK_ORDER=IN_DEVELOPMENT
NEXT_TECHNICAL_UNIT=ADAPT_PAGE_TWO_SCHEMA_OFFLINE
REPEATABLE_GUI_QUERY=OUT_OF_SCOPE_FUTURE_UNIT
```
