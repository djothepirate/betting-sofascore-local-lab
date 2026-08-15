# J4 — Qualification humaine réelle `EVENT_DETAILS` sous-étape 2

- **Date :** 2026-08-15
- **Work Order :** `WO-SS-20260815-004`
- **Branche :** `codex/j4-real-event-details-phase1`
- **Pull Request :** `#8` — `DRAFT` au moment de la consolidation des preuves
- **Qualification technique hors ligne :** `PASS`
- **Qualification humaine réelle :** `PASS`
- **État terminal de la campagne :** `STOPPED_LOCKED`
- **Configuration locale finale :** `LOCKED`
- **Statut :** `PASS_READY_FOR_PR8_MERGE`

## 1. Périmètre humain exécuté

La sous-étape 2 a été exécutée depuis l'interface locale avec des identifiants numériques choisis
par l'opérateur. Chaque cycle a exigé une préparation sans réseau, une phrase exacte liée à
l'identifiant, un acquittement et une action finale distincte. La preuve terminale de chaque cycle
indique exactement un appel fournisseur. Aucun cache, polling, planification ou retry automatique
n'a été utilisé.

Les captures et inspections JSON fournies à la tâche Codex restent des pièces temporaires. Elles
ne sont pas ajoutées au dépôt et aucun octet de payload brut n'est reproduit dans ce rapport.

## 2. Résultat minimisé des événements paramétrables

| Événement | Rencontre normalisée | Observation qualifiée | Snapshot | Taille | Résultat |
|---|---|---|---:|---:|---|
| `16483632` | Gençlerbirliği — Fenerbahçe | premier ID paramétrable, `notstarted` | 18 | 8981 octets | `NOUVELLE_VERSION` |
| `16412917` | FC Kryvbas Kryvyi Rih — FC Livyi Bereh Kyiv | état initial `notstarted` | 19 | 8069 octets | `NOUVELLE_VERSION` |
| `16412917` | même rencontre avant le coup d'envoi | contenu fournisseur inchangé | 19 | 8069 octets | `DÉDUPLIQUÉE` |
| `16412917` | même rencontre après le coup d'envoi | nouvel état `inprogress` | 23 | 8484 octets | `NOUVELLE_VERSION` |

Les snapshots 18, 19 et 23 sont des réponses HTTP `200`, classées `PARSED` et traitées par
`event-details-v2`. L'écart entre 19 et 23 est normal : l'identifiant de snapshot est global au
laboratoire et ne constitue pas un numéro de version propre à la rencontre.

```text
PARAMETERIZED_EVENT_16483632=PASS
PARAMETERIZED_EVENT_16412917=PASS
CONFIRMED_PROVIDER_CALLS_PER_CYCLE=1
RAW_BEFORE_PARSE=PASS
RAW_PAYLOAD_INCLUDED_IN_EVIDENCE=NO
```

## 3. Rappel manuel et changement en cours de match

Un premier rappel de `16412917`, exécuté avant le coup d'envoi avec une nouvelle confirmation, a
réellement effectué un transport fournisseur. La réponse étant identique au snapshot 19, le
résultat a été correctement dédupliqué : aucun snapshot ni observation métier superflus n'ont été
créés et la rencontre est restée à une version.

Après le coup d'envoi de `12:00` dans `Europe/Paris`, un nouveau cycle a persisté le snapshot 23 à
`2026-08-15T10:07:37.517008Z`. Sa taille et son SHA-256 diffèrent du snapshot 19. La même identité
canonique locale `9b9e7909-62e7-3985-bc34-759ad8684224` et le même identifiant fournisseur
`16412917` portent désormais une version courante `inprogress`, tandis que l'observation
`notstarted` reste intacte dans l'historique.

```text
MANUAL_RECALL_WITH_NEW_CONFIRMATION=PASS
PRE_KICKOFF_PROVIDER_TRANSPORT=1
PRE_KICKOFF_DEDUPLICATION=PASS
IN_MATCH_PROVIDER_TRANSPORT=1
IN_MATCH_NEW_SNAPSHOT_ID=23
IN_MATCH_STATUS=inprogress
IN_MATCH_NEW_VERSION=PASS
CANONICAL_IDENTITY_STABLE=PASS
```

## 4. Historique append-only et inspection locale

La fiche locale de `16412917` indique deux versions. L'historique expose séparément le snapshot
19 reçu à `2026-08-15T09:19:08.441876Z` avec le statut `notstarted` et le snapshot 23 reçu à
`2026-08-15T10:07:37.517008Z` avec le statut `inprogress`. Les deux états ont été ouverts et
consultés humainement. Le catalogue local conserve également les deux snapshots bruts sous la
même clé `EVENT_DETAILS|eventId=16412917`, chacun avec sa taille, son SHA-256, son classement et
son parseur propres.

L'inspection locale formatée vérifie la taille et le SHA-256 avant lecture et ne réécrit ni les
octets, ni la classification, ni l'historique. Les fichiers JSON transmis pour cette validation
sont traités comme données de preuve uniquement ; ils ne sont ni copiés dans le dépôt, ni cités
dans les logs ou la Pull Request.

```text
SNAPSHOT_19_LOCALLY_INSPECTABLE=PASS
SNAPSHOT_23_LOCALLY_INSPECTABLE=PASS
APPEND_ONLY_HISTORY_VERSION_COUNT=2
PREVIOUS_OBSERVATION_PRESERVED=PASS
RAW_AND_NORMALIZED_DATA_SEPARATED=PASS
```

## 5. Arrêt global, reverrouillage et arrêt des applications

Après la campagne, l'opérateur a appliqué **« Arrêt global J4 »**. L'interface a confirmé que toute
nouvelle préparation de sous-étape 2 exigeait un redémarrage. L'instance ayant effectué les appels
réels a ensuite été arrêtée.

Les six paramètres locaux ont été remis à leur état bloqué : connecteur, qualification J3,
qualification J4 et opt-in de sous-étape 2 désactivés, origine fournisseur vide et liste
d'endpoints autorisés vide. Le fichier local `.env` reste ignoré par Git et n'est pas intégré à la
preuve.

Après redémarrage, l'interface a affiché `LOCKED` ainsi que les cinq motifs de blocage attendus. Le
champ **« Identifiant fournisseur de l'événement »** et l'action **« Préparer un rafraîchissement »**
étaient désactivés, tandis que les données déjà persistées restaient consultables localement.

L'instance de vérification a enfin été arrêtée à `2026-08-15T12:20:47+02:00`. Le journal confirme
le début puis la fin de l'arrêt gracieux de Tomcat, la fermeture de l'EntityManagerFactory et
l'arrêt complet de `HikariPool-1`.

```text
GLOBAL_STOP=PASS
APPLICATION_STOPPED_AFTER_REAL_CAMPAIGN=PASS_HUMAN_CONFIRMATION
LOCAL_CONFIGURATION_FILE_RELOCKED_AFTER_PHASE2=PASS
LOCAL_CONFIGURATION_EFFECTIVE_RELOCK_AFTER_RESTART=PASS
NETWORK_CONTROLS_LOCKED_AFTER_RESTART=PASS
PARAMETER_FIELD_DISABLED_AFTER_RESTART=PASS
PREPARATION_ACTION_DISABLED_AFTER_RESTART=PASS
PERSISTED_DATA_LOCALLY_READABLE_WHILE_NETWORK_LOCKED=PASS
APPLICATION_STOPPED_AFTER_RELOCK_VERIFICATION=PASS_GRACEFUL_SHUTDOWN_LOG
```

## 6. Décision de qualification humaine

La sous-étape 2 démontre à la fois l'acquisition d'un ID paramétrable, le rappel manuel d'une
réponse inchangée, la création d'une nouvelle version lors d'un changement fournisseur et la
conservation de l'historique. Les deux sous-étapes réelles J4 sont humainement qualifiées. Cette
décision n'autorise ni polling, ni tâche planifiée, ni nouvelle famille d'endpoint, ni production,
ni déploiement VPS.

```text
REAL_PROVIDER_QUALIFICATION=PASS
HUMAN_PARAMETERIZED_EVENT_QUALIFICATION=PASS
HUMAN_MANUAL_RECALL_QUALIFICATION=PASS
PRE_KICKOFF_DEDUPLICATION_QUALIFICATION=PASS
IN_MATCH_REFRESH_QUALIFICATION=PASS
APPEND_ONLY_HISTORY_QUALIFICATION=PASS
RAW_SNAPSHOT_LOCAL_INSPECTION=PASS
LOCAL_CONFIGURATION_RELOCKED_AFTER_PHASE2=PASS
EFFECTIVE_RELOCK_AFTER_RESTART=PASS
APPLICATION_FINAL_GRACEFUL_SHUTDOWN=PASS
```

## 7. Vérifications Maven de clôture

Les deux suites ont été exécutées avec le connecteur, J3, J4 et la sous-étape 2 explicitement
désactivés dans le processus Maven. La suite standard a validé 212 tests. Le profil d'intégration a
rejoué les 212 tests standards puis validé 16 tests PostgreSQL/Testcontainers et Flyway V1 à V6.
Aucun scénario Maven n'est autorisé à contacter SofaScore et aucun appel fournisseur n'a été
exécuté.

```text
FINAL_STANDARD_TESTS=212
FINAL_STANDARD_FAILURES=0
FINAL_STANDARD_ERRORS=0
FINAL_STANDARD_SKIPPED=0
FINAL_INTEGRATION_TESTS=16
FINAL_INTEGRATION_FAILURES=0
FINAL_INTEGRATION_ERRORS=0
FINAL_INTEGRATION_SKIPPED=0
FINAL_FLYWAY_SCHEMA=V6
REAL_PROVIDER_CALLS_DURING_FINAL_MAVEN=0
```

## 8. État avant fusion de la Pull Request

La qualification humaine permet la clôture, mais le Work Order reste actif jusqu'à l'intégration
de la Pull Request `#8` dans `main`. Son archivage `VALIDATED` sera effectué après cette fusion afin
de consigner le véritable commit de merge.

```text
TECHNICAL_OFFLINE_QUALIFICATION=PASS
REAL_PROVIDER_QUALIFICATION=PASS
WORK_ORDER_STATUS=IN_DEVELOPMENT_PENDING_PR8_MERGE
J4_WORK_ORDER=ACTIVE_PENDING_PR8_MERGE
J4_CAN_BE_CLOSED=YES_AFTER_PR8_MERGE
```
