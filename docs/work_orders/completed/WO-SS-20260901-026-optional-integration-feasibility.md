# WO-SS-20260901-026 — Étude de faisabilité de l’intégration optionnelle et proposition ADR-SS-003

- **Statut :** `VALIDATED`
- **Jalon :** après J9 — préparation de l’intégration optionnelle
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T06:37:59.009Z`
- **Ouverture Europe/Paris :** `2026-09-01T08:37:59.009+02:00`
- **Branche :** `codex/j9-optional-integration-study`
- **Worktree :** `.tmp/j9-optional-integration-study`
- **Base locale et distante vérifiée :**
  `1a58a3bd7673f5946d5c48ae573c191e52d223a2`
- **Branche publiée de référence :** `origin/codex/j9-decision`
- **Work Order parent :** `WO-SS-20260831-018-decision-j9` — `VALIDATED`
- **Preuve de robustesse retenue :** `WO-SS-20260831-023-j9-provider-robustness-v11` —
  `PASS`, validée par le propriétaire
- **Rapport de preuve :** `J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901`
- **SHA-256 du rapport de preuve :**
  `1c6a97f872d5621efcaffa505d16a7724f81816891aa0a9a990a0abec56e87c1`
- **Décision J9 finale :** `PREPARE_OPTIONAL_INTEGRATION`
- **Permission officielle :** `NOT_EVIDENCED`
- **ADR accepté :** `ADR-SS-003 v0.1` — `ACCEPTED`
- **Commit local de proposition :** `ca789a3a40ea5fc6c16312bd73f675bc9fd32650`
- **SHA-256 du draft ADR-SS-003 v0.1 accepté :**
  `0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be`
- **Décision propriétaire ADR :** `ACCEPT`, reçue à `2026-09-01T07:25:25.4809414Z`
  (`2026-09-01T09:25:25.4809414+02:00` en Europe/Paris)
- **Topologie sélectionnée :** `OPTIONAL_LOCAL_PUSH`
- **Fondement de clôture :** l’acceptation d’ADR-SS-003 v0.1 satisfait l’objet documentaire du WO
- **Type de lot :** documentation et décision d’architecture seulement

## 1. Décision d’ouverture et interprétation bornée

Le propriétaire a autorisé le push des travaux finalisés de la plage J9, puis demandé l’ouverture
d’un Work Order afin :

1. d’étudier l’intégration optionnelle du SofaScore Local Lab au Betting Project ;
2. de comparer un push local d’export validé à une topologie Playwright exécutée sur VPS ;
3. de proposer ADR-SS-003.

La branche J9 finalisée a été publiée sans force sur le dépôt privé de référence. `main` est resté
au commit `40323faa7dca3341da6ef980b5762f1ff5a32a79`. Le présent Work Order part du commit J9 publié
exact `1a58a3bd7673f5946d5c48ae573c191e52d223a2`, dans une branche et un worktree distincts.

Cette instruction d’ouverture autorisait une étude et une proposition d’ADR. La décision
propriétaire reçue le 1er septembre accepte désormais ADR-SS-003 v0.1 et sélectionne
`OPTIONAL_LOCAL_PUSH`, sans autoriser aucune implémentation, aucun endpoint, aucun déploiement,
aucun appel fournisseur et aucune exploitation de production.

```text
WORK_ORDER_STATUS=VALIDATED
J9_FINAL_DECISION=PREPARE_OPTIONAL_INTEGRATION
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
ADR_SS_003_STATUS=ACCEPTED
ADR_SS_003_COMMIT=ca789a3a40ea5fc6c16312bd73f675bc9fd32650
ADR_SS_003_FILE_SHA256=0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be
ADR_SS_003_OWNER_DECISION=ACCEPT
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
OWNER_ACCEPTANCE_RECORDED=YES
CLOSURE_BASIS=ADR_SS_003_V0_1_OWNER_ACCEPTANCE_SATISFIES_WO026_OBJECTIVE
MOVE_TO_COMPLETED_PERFORMED=YES
CODE_CHANGE_AUTHORIZED=NO
INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
BETTING_PROJECT_NETWORK_CHANGE_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
LIVE_OR_SCHEDULED_OPERATION_AUTHORIZED=NO
PRIMARY_DATABASE_PURGE=NO
```

## 2. Objectif

Produire une décision d’architecture vérifiable, réversible et compatible avec l’indépendance du
Betting Project. La comparaison doit établir ce qui est déjà prouvé, ce qui n’est qu’un contrat à
concevoir et ce qui est actuellement interdit par la gouvernance.

Le livrable doit permettre au propriétaire de décider séparément s’il accepte ADR-SS-003 v0.1.
Même acceptée, cette ADR ne pourra ouvrir qu’un futur Work Order d’implémentation borné ; elle ne
vaudra ni autorisation réseau fournisseur, ni autorisation de production.

## 3. Référentiel factuel versionné à l’ouverture

| Entrée | Fait retenu | Portée et limite |
|---|---|---|
| Décision J9 | `PREPARE_OPTIONAL_INTEGRATION`, décidée à `2026-09-01T05:49:58.398Z` (`07:49:58.398+02:00` à Paris) | Autorise l’étude et la proposition d’ADR-SS-003, pas l’implémentation |
| Preuve WO-023 | `PASS` ; 28 réponses HTTP 200 et 28 parsings sur 38 tentatives autorisées ; aucun incident bloquant | Preuve Windows résidentielle, locale, manuelle et bornée ; aucune extrapolation à une adresse VPS |
| Export J7 | Export canonique v1, enveloppe `{manifest,data}`, taille maximale 5 Mio et téléchargement réservé à `HUMAN_VALIDATED` | Base locale exploitable ; aucun contrat de livraison distante n’existe |
| ADR-SS-001 v1.4 | Playwright local, manuel et opt-in ; export JSON versionné puis éventuel push HTTPS sortant depuis Windows ; payloads bruts locaux | Le VPS et le cœur du Betting Project ne contactent pas directement SofaScore |
| ADR-SS-002 v1.1 | `ACCEPTED` ; portée de campagne achevée et go WO-023 consommé | N’autorise ni intégration, ni nouveau réseau, ni nouvelle campagne |
| Gouvernance du dépôt | `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY` | Doit rester inchangée sous WO-026 |
| Conditions d’utilisation | Revue officielle versionnée du 2026-08-31 : restrictions factuellement relevées ; aucune permission ou licence applicable extraite | `NOT_EVIDENCED` à l’ouverture ; état à revérifier, sans conclusion juridique favorable ou défavorable |
| Déploiement VPS | Option non exclue par le propriétaire, mais aucune qualification VPS n’a été menée | `NOT_MEASURED`, `NOT_AUTHORIZED` et, sous la gouvernance courante, `BLOCKED_BY_CURRENT_GOVERNANCE` |

Les rapports J8/J9, les Work Orders clôturés, ADR-SS-001, ADR-SS-002 et `docs/reference` ne sont pas
réécrits par ce lot.

## 4. Périmètre

### 4.1 Inclus

- décrire le contrat d’architecture d’un push local optionnel d’un export J7 minimisé et déjà
  `HUMAN_VALIDATED` ;
- décrire les frontières de confiance, d’exploitation et de données d’une éventuelle topologie
  Playwright VPS ;
- comparer les deux options et le maintien local sans intégration, sans score arbitraire ;
- qualifier chaque constat par une preuve, un niveau de confiance et la lacune restante ;
- proposer ADR-SS-003 v0.1 avec une recommandation réversible ;
- définir les portes qui devront précéder toute acceptation, implémentation ou production future.

### 4.2 Exclus

```text
APPLICATION_CODE_CHANGE=NO
NEW_HTTP_ENDPOINT=NO
NEW_PROVIDER_ENDPOINT=NO
NEW_URI_OR_ALLOWLIST=NO
SQL_SCHEMA_OR_MIGRATION_CHANGE=NO
EXPORT_J7_FORMAT_CHANGE=NO
NETWORK_CONFIGURATION_CHANGE=NO
CERTIFICATE_OR_SECRET_PROVISIONING=NO
BETTING_PROJECT_CHANGE=NO
VPS_DEPLOYMENT=NO
PROVIDER_CALLS_UNDER_WO026=0
POLLING=0
SCHEDULER=0
LIVE_MODE=0
AUTOMATIC_RETRY=0
FALLBACK=0
RAW_PAYLOAD_TRANSFER=NO
```

Une exigence de code ou de runtime découverte pendant l’étude ouvre un futur Work Order distinct ;
elle n’est pas absorbée par WO-026.

## 5. Options fermées

### 5.1 Option A — `OPTIONAL_LOCAL_PUSH`

Le laboratoire conserve l’acquisition Playwright sur Windows, manuelle, à la demande, explicite et
désactivée par défaut. Après validation humaine et commit local, un export J7 minimisé serait livré
par un push HTTPS sortant vers un point d’import dédié du Betting Project.

Le contrat candidat doit préserver :

- aucune connexion entrante vers Windows ;
- aucun déclenchement d’acquisition SofaScore par une livraison ;
- aucun payload brut, cookie, jeton, état Playwright ou paramètre technique SofaScore transféré ;
- un identifiant de livraison et un état de livraison distincts de `HUMAN_VALIDATED` ;
- une livraison v1 manuelle, distincte et postérieure à `HUMAN_VALIDATED` ;
- un `POST` HTTPS, une clé d’idempotence stable `exportId + fileSha256` et un accusé synchrone
  minimisé ;
- un ledger de livraison séparé, six états déterministes et zéro retry automatique ;
- une reprise manuelle éventuelle du même artefact avec la même clé, après vérification du receiver ;
- mTLS obligatoire, avec clé privée non exportable dans le magasin de certificats de l’utilisateur
  Windows ;
- rotation par chevauchement contrôlé, révocation, récupération et audit sans secret ;
- timeout fini, concurrence `1`, supervision minimisée et absence de polling ou scheduler ;
- l’absence d’effet sur le Betting Project lorsque le poste Windows ou le laboratoire est arrêté.

Ces choix sont fixés par ADR-SS-003 v0.1 acceptée, mais restent `NOT_IMPLEMENTED`. L’URI, les valeurs
numériques de timeout, le profil de certificat et les noms exacts du schéma d’accusé sont
`NOT_DEFINED` ou `NOT_MEASURED` et devront respecter ces choix dans les futurs Work Orders.

### 5.2 Option B — `VPS_PLAYWRIGHT`

Le laboratoire ou un composant équivalent exécuterait Playwright sur une infrastructure VPS et
contacterait le fournisseur depuis cette adresse. L’option doit être étudiée, car le propriétaire
ne l’exclut plus comme architecture future, mais elle n’est pas recevable comme simple variante
d’implémentation sous les règles actuelles.

Elle est aujourd’hui `BLOCKED_BY_CURRENT_GOVERNANCE` pour quatre raisons cumulatives :

1. `AGENTS.md` impose `LOCAL_ONLY` et Playwright local, manuel et opt-in ;
2. ADR-SS-001 §4 maintient que le VPS et le cœur du Betting Project ne contactent pas directement
   les endpoints SofaScore ;
3. ADR-SS-001 §6.2 rejette l’intégration directe SofaScore sur le VPS ;
4. les octets fournisseur bruts devraient être reçus et traités sur le VPS, alors que la frontière
   actuelle exige que les payloads bruts restent locaux et interdit leur envoi au VPS.

Une future recevabilité exigerait au minimum une décision propriétaire distincte sur la révision
d’ADR-SS-001 et de la gouvernance, ainsi qu’une résolution explicite de la frontière des octets
bruts. ADR-SS-003 ne modifie pas ces textes à elle seule.

Les dimensions suivantes sont en outre `NOT_MEASURED` : accessibilité fournisseur depuis une IP
VPS, egress, restrictions de l’hébergeur, exposition réseau, sandbox Chromium, privilèges système,
isolation des processus, stockage éphémère, secrets et certificats, limites CPU/mémoire/disque,
supervision, nettoyage fail-closed, rotation, sauvegarde, disponibilité, coût et astreinte.

### 5.3 Option C — `KEEP_LOCAL_NO_INTEGRATION`

Cette option témoin conserve les exports et décisions dans le laboratoire sans livraison au Betting
Project. Elle est compatible avec la gouvernance et immédiatement réversible, mais n’apporte aucune
automatisation d’intégration. Elle reste le repli obligatoire si une porte matérielle échoue.

## 6. Matrice de décision factuelle initiale

Les qualifications ne sont pas des notes et ne sont pas additionnées. Elles décrivent uniquement
l’état de preuve : `PASS_LOCAL`, `COMPATIBLE_FOR_FUTURE_WORK_ORDER_UNDER_CURRENT_GOVERNANCE`, `PARTIAL_DESIGN`,
`NOT_MEASURED`, `NOT_EVIDENCED`, `BLOCKED_BY_CURRENT_GOVERNANCE` ou `FALLBACK_SAFE`.

| Critère | `OPTIONAL_LOCAL_PUSH` | `VPS_PLAYWRIGHT` | Fait, confiance et lacune restante |
|---|---|---|---|
| Compatibilité de gouvernance | `COMPATIBLE_FOR_FUTURE_WORK_ORDER_UNDER_CURRENT_GOVERNANCE` | `BLOCKED_BY_CURRENT_GOVERNANCE` | **Élevée** : ADR-SS-001 §3.9 prévoit l’export puis un éventuel push sortant Windows ; §§4 et 6.2 excluent l’appel direct depuis le VPS. Cette qualification ne vaut pas compatibilité d’une implémentation absente. Toute évolution VPS exige une décision/amendement distinct. |
| Permission officielle | `NOT_EVIDENCED` | `NOT_EVIDENCED` | **Élevée sur l’absence de preuve** : aucune permission ou licence applicable n’a été extraite. Une source officielle ou une autorisation appropriée reste une porte dure ; ce document n’est pas un avis juridique. |
| Accessibilité fournisseur | `PASS_LOCAL` dans les bornes WO-023 | `NOT_MEASURED` | **Élevée pour Windows seulement** : WO-023 est `PASS`. Une adresse résidentielle Windows ne prédit ni l’accessibilité, ni le blocage, ni la stabilité d’une IP VPS. |
| Frontière des données | `PARTIAL_DESIGN` | `BLOCKED_BY_CURRENT_GOVERNANCE` | **Élevée** : J7 fournit un export canonique normalisé ; ADR-SS-001 maintient les payloads bruts locaux. Le push doit prouver la minimisation. Sur VPS, les octets bruts y seraient nécessairement reçus ; la gouvernance doit être résolue avant étude exécutoire. |
| Déclenchement fournisseur | Acquisition locale séparée de la livraison | Acquisition et traitement co-localisés sur le VPS | **Élevée pour l’invariant cible** : une livraison ne doit jamais déclencher une acquisition. Aucun orchestrateur de livraison n’existe. Le modèle VPS devrait prouver la même séparation sans scheduler implicite. |
| Surface réseau | Push HTTPS sortant seulement ; aucune entrée Windows | Egress fournisseur, administration VPS et éventuels services locaux à isoler | **Moyenne pour A, nulle pour B** : la direction de flux A est définie, pas son endpoint. Aucun modèle de menace ni inventaire de ports VPS n’existe. |
| Navigateur et isolation | Contexte non persistant et nettoyage déjà qualifiés localement | `NOT_MEASURED` | **Élevée pour le runtime Windows borné** : WO-020/021 et WO-023 qualifient fermeture, délai et artefacts. Aucune preuve équivalente Linux/VPS, sandbox ou cgroups n’existe. |
| Secrets et certificats | mTLS et clé Windows non exportable imposés par le draft | Stockage, injection et rotation VPS `NOT_MEASURED` | **Moyenne pour le choix, nulle pour l’exécution** : aucun certificat, secret, profil, rotation ou révocation n’a été provisionné ou testé. |
| Contrat de livraison | Choix d’architecture fixés, runtime `NOT_IMPLEMENTED` | Non applicable avant levée du blocage | **Élevée sur les choix, nulle sur l’exécution** : `POST` HTTPS, déclencheur manuel, idempotence, accusé, états, zéro retry automatique, mTLS et supervision minimisée sont proposés ; URI, profils et valeurs restent à qualifier. |
| Audit et reproductibilité | Export J7 et hashes déjà reproductibles localement | `NOT_MEASURED` | **Élevée pour l’export** : WO-023 a produit deux exports byte-identiques. La preuve de livraison et l’audit VPS restent à concevoir. |
| Rétention, purge et restauration | Le brut reste sous J6/V28 local ; cycle de vie de la copie J7 chez le receiver `NOT_DEFINED` | Portage J6, rétention, purge, chiffrement et restauration VPS `NOT_MEASURED` | **Élevée pour le local, nulle pour les cibles** : la sauvegarde/restauration V28 est qualifiée localement. Aucune politique receiver ou VPS n’est définie. |
| Exploitation et supervision | Geste manuel à la demande ; poste arrêté sans effet sur le Betting Project | Supervision, restart, nettoyage, alerting et astreinte `NOT_MEASURED` | **Élevée pour la non-dépendance exigée**, faible pour les mécanismes futurs. Aucun SLO, runbook ou exploitant VPS n’est désigné. |
| Fraîcheur et disponibilité | Dépendent d’un geste manuel ; aucune promesse continue | Potentiel théorique d’exécution distante, mais `NOT_MEASURED` et non autorisé | **Élevée sur la limite locale**, nulle sur un bénéfice VPS. Aucun polling, live, scheduler ou SLA ne peut être déduit. |
| Coût et ressources | Sender, receiver, mTLS et exploitation `NOT_MEASURED` | Hébergement, navigateur, supervision et stockage `NOT_MEASURED` | **Nulle comparativement** : aucune mesure TCO, volumétrie de production ou coût receiver n’existe. |
| Maintenabilité | Réutilise les contrats J7/J9 ; livraison à construire | Nouveau runtime, packaging et exploitation à construire | **Moyenne pour A, faible pour B** : la réutilisation J7 est versionnée ; aucun prototype ou cycle de maintenance distant n’est qualifié. |
| Exactitude et valeur analytique | Non améliorées par le transport | Non améliorées par le lieu d’exécution | **Élevée** : une topologie de livraison ne prouve ni exactitude, ni valeur métier. Ces critères restent des portes indépendantes. |
| Indépendance du Betting Project | Compatible si ingestion facultative, désactivable et sans attente du poste | Risque accru de couplage si service permanent ; à démontrer | **Élevée sur l’invariant**, non mesurée sur l’implémentation. `NO_CRITICAL_DEPENDENCY` reste obligatoire. |
| Arrêt, rollback et désactivation | Arrêt du push sans effet sur acquisition ou cœur ; contrat à tester | Retrait du service VPS et purge contrôlée à concevoir | **Moyenne pour A, faible pour B** : l’architecture A est directionnellement réversible ; aucune procédure de rollback distante n’est qualifiée. |
| Conclusion factuelle | Seule direction compatible pour un futur Work Order sous la gouvernance actuelle, sous portes | Alternative différée, bloquée avant expérimentation distante | **Élevée** : cette conclusion découle des textes actuels, pas d’un score et pas d’une compatibilité runtime déjà démontrée. `KEEP_LOCAL_NO_INTEGRATION` reste le repli si une porte échoue. |

## 7. Recommandation du draft et décision propriétaire

Le draft v0.1 recommandait :

1. `OPTIONAL_LOCAL_PUSH` comme seule direction proposée pour un futur Work Order d’implémentation
   sous la gouvernance actuelle ;
2. `VPS_PLAYWRIGHT` comme option différée, non autorisée et bloquée tant que la gouvernance, la
   frontière des données, la permission officielle et une preuve VPS dédiée ne sont pas résolues ;
3. `KEEP_LOCAL_NO_INTEGRATION` comme repli sûr et réversible ;
4. aucun changement effectif avant acceptation propriétaire explicite d’ADR-SS-003 et ouverture
   d’un autre Work Order.

Le propriétaire a explicitement accepté cette recommandation et sélectionné
`OPTIONAL_LOCAL_PUSH`. Ce choix ne vaut ni autorisation d’implémentation, ni ouverture automatique
d’un Work Order d’implémentation.

## 8. Travaux de faisabilité restant à instruire

### 8.1 Droits d’usage et permission

- identifier une permission officielle ou une licence applicable à l’acquisition, la normalisation,
  la conservation et la transmission des données ;
- consigner URL, version, date/heure UTC, accessibilité et extrait factuel minimal ;
- maintenir `NOT_EVIDENCED` en l’absence de texte clair ;
- obtenir une revue compétente si nécessaire, sans transformer l’étude technique en avis juridique.

### 8.2 Contrat du push local

- définir un point d’import dédié sans publier d’URI avant autorisation ;
- figer version de schéma, taille maximale, compression éventuelle et minimisation ;
- définir identité de livraison, idempotence, accusé, état de livraison, erreurs et timeout ;
- décider si une reprise est manuelle ou bornée, sans retry implicite ;
- définir mTLS, identité serveur/client, clé non exportable, rotation, révocation et récupération ;
- prouver qu’une livraison ne peut appeler le fournisseur et qu’un échec n’altère jamais la
  décision locale `HUMAN_VALIDATED`.

### 8.3 Qualification de la topologie VPS

Avant tout prototype connecté :

- décision propriétaire séparée autorisant l’étude exécutoire et indiquant les textes de
  gouvernance à réviser ;
- résolution explicite de la présence d’octets fournisseur bruts sur le VPS ;
- permission officielle établie ;
- modèle de menace, frontières de confiance, egress et exposition ;
- image/runtime, sandbox Chromium, utilisateur non privilégié, systèmes de fichiers éphémères,
  limites CPU/mémoire/disque/PID et politique de patch ;
- secrets, certificats, rotation, sauvegarde et récupération ;
- supervision, arrêt fail-closed, nettoyage, disponibilité, astreinte et coûts ;
- campagne de preuve VPS distincte, bornée, non furtive et explicitement autorisée.

### 8.4 Indépendance et réversibilité

- ingestion entièrement facultative et désactivée par défaut ;
- aucune analyse standard ne dépend d’une donnée SofaScore fraîche ;
- absence de file bloquante, appel synchrone critique ou retry infini ;
- conservation d’un comportement fonctionnel lorsque le laboratoire, le réseau ou le VPS est
  indisponible ;
- procédure de désactivation et de rollback démontrable.

## 9. Portes de décision

### 9.1 Acceptation d’ADR-SS-003 — portes franchies

- matrice factuelle revue par le propriétaire ;
- statut `NOT_EVIDENCED` de la permission explicitement reconnu ;
- portée nulle de l’ADR sur le runtime, le réseau et la production explicitement reconnue ;
- statut `BLOCKED_BY_CURRENT_GOVERNANCE` de la topologie VPS explicitement reconnu ;
- choix propriétaire explicite `ACCEPT`, reçu et lié au commit/hash du draft.

### 9.2 Avant tout Work Order d’implémentation du push local

- ADR-SS-003 explicitement acceptée ;
- permission, licence ou base d’usage applicable suffisamment établie après une revue qualifiée ;
  une décision propriétaire interne ne crée aucun droit et ne se substitue jamais à cette porte ;
- contrat de données et de livraison approuvé ;
- modèle de menace, mTLS, rotation, idempotence et états approuvés ;
- Work Order et branche dédiés ;
- aucune autorisation fournisseur déduite de l’autorisation d’intégration.

### 9.3 Avant toute expérimentation Playwright VPS

- décision propriétaire distincte autorisant la révision de la gouvernance ;
- ADR-SS-001 et règles du dépôt réexaminées explicitement ;
- frontière des données brutes résolue ;
- permission officielle établie ;
- Work Order de preuve VPS, manifeste, limites et go explicites ;
- aucun mécanisme furtif, proxy rotatif, challenge bypass, réutilisation de cookie ou profil
  persistant.

## 10. Critères d’acceptation du lot documentaire

- [x] branche et worktree dédiés créés depuis le tip J9 publié exact et propre ;
- [x] décision J9, preuve WO-023, hash et non-autorisations consignés ;
- [x] `OPTIONAL_LOCAL_PUSH`, `VPS_PLAYWRIGHT` et le repli local définis ;
- [x] matrice factuelle sans score arbitraire, avec confiance et lacunes ;
- [x] incompatibilités VPS avec `LOCAL_ONLY`, ADR-SS-001 et la frontière brute explicitées ;
- [x] proposition ADR-SS-003 v0.1 créée sans acceptation implicite, puis acceptation propriétaire
  explicite liée au commit et au SHA-256 du draft ;
- [x] aucune modification d’ADR-SS-001, ADR-SS-002, `AGENTS.md` ou `docs/reference` ;
- [x] aucun code, endpoint, URI, schéma, migration, configuration, appel fournisseur ou déploiement ;
- [x] `mvnw.cmd clean verify` réussi ;
- [x] `git diff --check` réussi ;
- [x] liens Markdown locaux contrôlés ;
- [x] scan de secrets, cookies, jetons, payloads et chemins sensibles réussi ;
- [x] `server.address=127.0.0.1` et flags réseau bloquants confirmés ;
- [x] commit documentaire local dédié créé.

Les cases de validation ne seront cochées qu’avec les sorties réellement observées.

## 11. Vérifications exécutées

```powershell
.\mvnw.cmd clean verify
git diff --check
git status --short --branch
```

Contrôles supplémentaires :

- inventaire exact des fichiers modifiés ;
- liens Markdown locaux vers WO-018, WO-023, ADR-SS-001, ADR-SS-002 et ADR-SS-003 ;
- recherche de mots de passe, clés, jetons, cookies, payloads bruts et identifiants de session ;
- recherche de `server.address` et des valeurs par défaut des flags fournisseur/Playwright ;
- confirmation qu’aucun test standard n’effectue d’appel réel ;
- confirmation qu’aucun push, PR ou merge de la branche WO-026 n’est implicitement autorisé.

Résultats du 2026-09-01 :

| Contrôle | Résultat factuel |
|---|---|
| `mvnw.cmd --offline -Dmaven.repo.local=... clean verify` | `PASS` hors sandbox à `2026-09-01T06:54:03Z` (`08:54:03+02:00` Europe/Paris) ; 946 tests, 0 échec, 0 erreur, 5 skips prévus |
| Première tentative en sandbox | arrêt avant tests pendant la fermeture ZipFS de `spring-orm-7.0.8.jar`, avec `AccessDeniedException` sur le cache Maven utilisateur ; la même commande hors sandbox a réussi, sans modification de source ou de dépendance |
| Rerun post-acceptation du `clean verify` | `BLOCKED_BY_PREEXISTING_LOCAL_APPLICATION` à `2026-09-01T07:36:13Z` : 946 tests rapportés, 1 échec, 0 erreur, 5 skips ; seul `J6NativeBinaryPipelineQualificationTest` a refusé le listener `127.0.0.1:8087` déjà occupé |
| Contre-vérification J6 ciblée | même refus fail-closed à `2026-09-01T07:38:01Z` ; `netstat`, l’API .NET et la lecture de processus ont attribué le port à une instance locale préexistante de `SofascoreLocalApplication`, démarrée à `2026-09-01T07:14:32Z` ; aucun processus n’a été arrêté |
| `git diff --check` | `PASS` |
| Liens Markdown locaux | `PASS`, 136 cibles résolues dans README, WO-026 et ADR-SS-003 |
| Hygiène documentaire | `PASS`, aucun secret, valeur de credential, clé privée, cookie, jeton, payload brut, chemin utilisateur ou URI nouvelle dans le lot |
| Adresse et flags | `PASS` : `server.address=127.0.0.1` ; `sofascore.enabled`, Playwright et toutes les qualifications restent à `false` par défaut ; base URL et allowlist restent vides |
| Portée Git | quatre fichiers documentaires seulement ; ADR-SS-001, ADR-SS-002, `AGENTS.md`, code, configuration, migrations et `docs/reference` inchangés |
| Réseau | aucun appel fournisseur ou transfert sous WO-026 |
| Commit de proposition | `ca789a3a40ea5fc6c16312bd73f675bc9fd32650` ; ADR-SS-003 v0.1 SHA-256 `0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be` |

Le `PASS` complet de `06:54:03Z` reste la vérification du lot documentaire de proposition. Le
rerun post-acceptation n’a révélé aucune régression de source : il a confirmé le comportement
fail-closed attendu lorsqu’une application locale utilisateur occupe déjà le port réservé. Le
présent lot ne possède aucune autorisation pour arrêter cette application.

## 12. Décision propriétaire ADR-SS-003 enregistrée

Le bloc suivant est reproduit tel que reçu :

```text
ADR_SS_003_OWNER_DECISION=ACCEPT
ADR_SS_003_VERSION=0.1
ADR_SS_003_COMMIT=ca789a3a40ea5fc6c16312bd73f675bc9fd32650
ADR_SS_003_FILE_SHA256=0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be
WORK_ORDER=WO-SS-20260901-026-optional-integration-feasibility
PROPOSED_PRIMARY_TOPOLOGY=OPTIONAL_LOCAL_PUSH
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH
VPS_PLAYWRIGHT_STATUS=DEFERRED_BLOCKED_BY_CURRENT_GOVERNANCE
ADR_SS_001_EFFECT=UNCHANGED_UNDER_PROPOSAL
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
ADR_ACCEPTANCE_AUTHORIZES_IMPLEMENTATION=NO
INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
BETTING_PROJECT_RECEIVER_IMPLEMENTATION_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
FUTURE_IMPLEMENTATION_WORK_ORDER_REQUIRED=YES
FUTURE_VPS_GOVERNANCE_DECISION_REQUIRED=YES
OWNER_CONFIRMATION_REQUIRED=YES
```

La dernière ligne est conservée verbatim. Le message qui contient ce bloc est la confirmation du
propriétaire pour l’ADR. Conformément au précédent WO-022, cette acceptation satisfait le critère
terminal du présent Work Order documentaire sans fabriquer de champ propriétaire supplémentaire :

```text
OWNER_CONFIRMATION_RECEIVED=YES
ADR_OWNER_CONFIRMATION_OUTSTANDING=NO
WORK_ORDER_STATUS=VALIDATED
CLOSURE_BASIS=ADR_SS_003_V0_1_OWNER_ACCEPTANCE_SATISFIES_WO026_OBJECTIVE
MOVE_TO_COMPLETED_PERFORMED=YES
PROVIDER_NETWORK_AUTHORIZED=NO
INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO
BETTING_PROJECT_RECEIVER_IMPLEMENTATION_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 13. Références

- [Décision J9 WO-018](WO-SS-20260831-018-decision-j9.md)
- [Preuve J9 WO-023](WO-SS-20260831-023-j9-provider-robustness-v11.md)
- [Rapport PASS WO-023](../../validation/J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901.md)
- [ADR-SS-001](../../../ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md)
- [ADR-SS-002](../../../ADR-SS-002-bounded-multi-dossier-provider-robustness.md)
- [ADR-SS-003 v0.1 accepté](../../../ADR-SS-003-optional-integration-topology.md)
- [Architecture J7](../../architecture/J7-CANONICAL-EVENT-EXPORT.md)
- [Transport Playwright J3](../../architecture/J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md)
- [Architecture générale](../../architecture/ARCHITECTURE.md)
- [Règles du dépôt](../../../AGENTS.md)

## 14. Clôture

WO-026 est validé et déplacé vers les Work Orders terminés parce que l’acceptation propriétaire
d’ADR-SS-003 v0.1 satisfait intégralement son objectif documentaire. Aucun lot d’implémentation
n’est ouvert implicitement et toutes les portes réseau, livraison, VPS et production demeurent
fermées.
