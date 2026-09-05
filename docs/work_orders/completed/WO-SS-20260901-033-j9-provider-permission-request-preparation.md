# WO-SS-20260901-033 — Préparation de la demande de permission fournisseur et du receiver concurrent

- **Statut courant :** `ABANDONED_BY_OWNER` — décision du 2026-09-05.

> Le propriétaire déclare obsolètes les conditions de poursuite et abandonne WO-032,
> WO-033 et WO-034. Les validations, préflights et autorisations de reprise ci-dessous
> sont historiques : aucune saisie complémentaire, production de rendu ou fusion de
> clôture n'est désormais attendue. Aucun résultat technique antérieur n'est effacé.
> Classement administratif completed ; aucun envoi ni permission fournisseur créé.
> Pour WO-034, le nettoyage privé prévu historiquement reste distinct : non exécuté,
> aucune absence de données privées attestée ; suppression à décider explicitement.
- **Jalon :** après J9 — levée documentaire de la porte de permission et préparation end-to-end
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T15:41:29.1426445Z`
- **Ouverture Europe/Paris :** `2026-09-01T17:41:29.1426445+02:00`
- **Branche :** `codex/j9-wo033-provider-permission-request`
- **Worktree :** `.tmp/j9-wo033-provider-permission-request`
- **Base exacte :** `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089`
- **Commit d'ouverture historique avant renumérotation :**
  `35d8588c4cfb1214c3f8ea26f064f91672fa706b`
- **Identifiant initial désormais remplacé :**
  `WO-SS-20260901-031-j9-provider-permission-request-preparation`
- **Décision de renumérotation enregistrée UTC :** `2026-09-01T18:07:20.2600389Z`
- **Décision de renumérotation enregistrée Europe/Paris :**
  `2026-09-01T20:07:20.2600389+02:00`
- **Requalification terminée UTC :** `2026-09-01T18:21:47Z`
- **Requalification terminée Europe/Paris :** `2026-09-01T20:21:47+02:00`
- **Postflight UTC :** `2026-09-01T18:22:46.3756532Z`
- **Validation propriétaire UTC :** `2026-09-01T18:28:18.8023401Z`
- **Validation propriétaire Europe/Paris :** `2026-09-01T20:28:18.8023401+02:00`
- **Clôture locale UTC :** `2026-09-01T18:28:18.8023401Z`
- **Type de lot :** documentation et handoff inter-dépôts uniquement ; aucun changement runtime,
  aucun envoi externe, appel d'acquisition fournisseur ou réseau receiver ; seules les pages
  officielles ont été consultées en lecture seule

## 1. Autorisation reçue et interprétation bornée

Le propriétaire autorise la préparation d'une demande destinée au canal public SofaScore
`Product -> API`. Il établit également qu'un test end-to-end de l'envoi réel exigera la
disponibilité du receiver dans le dépôt `betting-project`, avec exécution simultanée du receiver et
de `betting-sofascore-local-lab`.

Le propriétaire précise ensuite le déclencheur et la trajectoire de qualification. L'activation
explicite d'un export J7 éligible doit soumettre une requête au receiver `betting-project`. Sous la
gouvernance v0.1 courante, « activation » est interprété comme une action opérateur distincte sur un
export déjà `HUMAN_VALIDATED`, et non comme un envoi automatique lors du changement d'état de
validation. Les trois paliers demandés sont :

1. les deux dépôts exécutés sur le poste Windows ;
2. le Local Lab sur Windows et `betting-project` sur le VPS de production déclaré par le
   propriétaire ;
3. éventuellement, les deux dépôts sur ce VPS de production.

Le VPS déclaré porte l'IPv4 `51.255.167.32`. Le propriétaire le décrit comme disponible, mais
aucune configuration ni installation manuelle n'y a encore été réalisée. WO-033 enregistre ce fait
sans contacter, sonder, configurer ou qualifier l'hôte. L'adresse n'est ni une URI receiver
acceptée, ni une preuve de contrôle, de durcissement, de TLS, d'accessibilité ou de readiness.

Cette autorisation permet de produire un brouillon exact, une liste de questions et un handoff de
readiness inter-dépôts. Elle n'autorise pas l'envoi de la demande, l'utilisation d'une identité ou
d'une adresse de réponse non fournies, un appel fournisseur, une connexion au receiver réel, une
livraison, un déploiement VPS ou un usage de production.

```text
J9_WO033_OWNER_DECISION=AUTHORIZE_PREPARATION
J9_WO033_SCOPE=PREPARE_SOFASCORE_PRODUCT_API_PERMISSION_REQUEST_AND_REAL_RECEIVER_CONCURRENCY_HANDOFF
SOFASCORE_PRODUCT_API_REQUEST_PREPARATION_AUTHORIZED=YES
SOFASCORE_PRODUCT_API_REQUEST_SEND_AUTHORIZED=NO
CONCURRENT_REAL_RECEIVER_REQUIREMENT=YES
J7_EXPORT_DELIVERY_TRIGGER=EXPLICIT_OPERATOR_ACTIVATION_AFTER_HUMAN_VALIDATED
J7_EXPORT_ACTIVATION_SUBMITS_RECEIVER_REQUEST=REQUIRED_FUTURE_BEHAVIOR
J7_EXPORT_ACTIVATION_TRIGGERS_PROVIDER_ACQUISITION=NO
E2E_STAGE_1=WINDOWS_LOCAL_LAB_TO_WINDOWS_BETTING_PROJECT
E2E_STAGE_2=WINDOWS_LOCAL_LAB_TO_PRODUCTION_VPS_BETTING_PROJECT
E2E_STAGE_3=POTENTIAL_PRODUCTION_VPS_LOCAL_LAB_TO_SAME_VPS_BETTING_PROJECT
OWNER_DECLARED_PRODUCTION_VPS_IPV4=51.255.167.32
OWNER_DECLARED_PRODUCTION_VPS_STATE=AVAILABLE_UNCONFIGURED_NOT_QUALIFIED
VPS_CONNECTION_OR_PROBE_AUTHORIZED_UNDER_WO033=NO
BETTING_PROJECT_RECEIVER_REPOSITORY_MUTATION_AUTHORIZED=NO
PROVIDER_ENDPOINT_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
RECEIVER_VPS_DEPLOYMENT_AUTHORIZED=NO
LOCAL_LAB_VPS_DEPLOYMENT_AUTHORIZED=NO
PROVIDER_VPS_ACQUISITION_AUTHORIZED=NO
PRODUCTION_INGESTION_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

### 1.1 Réconciliation du numéro de Work Order

Le propriétaire a confirmé que
`WO-SS-20260901-031-ci-gitlab-local-only` appartient aux travaux d'environnement d'intégration
continue, sans lien avec J9, et conserve le numéro `031`. Le présent lot fournisseur, ouvert
ultérieurement avec le même numéro sur une branche locale non publiée, est donc renuméroté `033`.

Cette correction change les chemins et les octets des deux livrables. Les hashes et le commit de
préparation précédemment présentés sous `031` sont conservés comme preuves historiques, mais sont
remplacés pour toute future revue par les nouvelles références `033`. Aucun commit de la PR CI
#23, aucune branche CI et aucun document de ce Work Order CI ne sont modifiés.

```text
WORK_ORDER_NUMBER_RECONCILIATION_OWNER_DECISION=AUTHORIZE
WORK_ORDER_RENUMBER_AUTHORIZED=YES
WORK_ORDER_RENUMBER_DECISION=AUTHORIZE_RENUMBER_FROM_WO031_TO_WO033
WORK_ORDER_RENUMBER_REASON=RESOLVE_COLLISION_WITH_DISTINCT_CI_WO031
PRESERVED_WORK_ORDER=WO-SS-20260901-031-ci-gitlab-local-only
PRESERVED_WORK_ORDER_SCOPE=CONTINUOUS_INTEGRATION_ENVIRONMENT_NOT_J9
DISTINCT_CI_WO031_PRESERVED=YES
DISTINCT_CI_WO031_REF=origin/codex/ss-20260901-031-ci-bootstrap
DISTINCT_CI_WO031_COMMIT=01eea4ba60e966ca48a5824575a6b895d6a48a1b
DISTINCT_CI_WO031_PATH=docs/work_orders/active/WO-SS-20260901-031-ci-gitlab-local-only.md
RENUMBERED_FROM=WO-SS-20260901-031-j9-provider-permission-request-preparation
RENUMBERED_TO=WO-SS-20260901-033-j9-provider-permission-request-preparation
PRE_RENUMBER_BRANCH=codex/j9-wo031-provider-permission-request
CURRENT_BRANCH=codex/j9-wo033-provider-permission-request
PRE_RENUMBER_WORKTREE=.tmp/j9-wo031-provider-permission-request
CURRENT_WORKTREE=.tmp/j9-wo033-provider-permission-request
BRANCH_RENAME_RECORDED_AT_UTC=2026-09-01T18:06:32Z
HISTORICAL_OPENING_COMMIT=35d8588c4cfb1214c3f8ea26f064f91672fa706b
HISTORICAL_OPENING_IDENTIFIER=WO-SS-20260901-031-j9-provider-permission-request-preparation
PRE_RENUMBER_HEAD=e659ff8fc05ce99913d87ae554c9356581e852f8
HISTORY_REWRITE=NO
COMMIT_STRATEGY=APPEND_ONLY
OLD_DOCUMENTATION_COMMIT=e659ff8fc05ce99913d87ae554c9356581e852f8
OLD_PERMISSION_REQUEST_DRAFT_SHA256=27daa5859ce52d6bf2e4459a4ba7ab4048bab21e5b23e12fddb226e6ce228b3e
OLD_RECEIVER_HANDOFF_SHA256=63b5f5f9968d4eea4190e2702e3589e7f20669b91e7f84622f33cc458cfb5ca6
OLD_REFERENCES_STATUS=SUPERSEDED_BY_AUTHORIZED_RENUMBERING
CI_PULL_REQUEST_23_MUTATED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 2. Situation de départ factuelle

La revue des sources officielles versionnée sous
[`J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md`](../../validation/J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md)
conclut :

```text
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
OFFICIAL_PERMISSION_GATE_SATISFIED=NO
EXPLICIT_APPLICABLE_PERMISSION_FOUND=NO
PUBLIC_API_DOCUMENTATION_EQUALS_PERMISSION=NO
```

Les sources publiques déjà identifiées établissent seulement :

- l'existence de conditions publiques comportant des restrictions sur certaines requêtes
  automatisées, le scraping, l'agrégation et l'extraction substantielle sans consentement
  explicite ;
- l'existence d'une documentation technique publique d'API externe ;
- l'existence d'un formulaire de contact officiel proposant la catégorie `Product -> API`.

Ces constats ne fournissent ni licence applicable aux six familles J3/J4/J5 du laboratoire, ni
quota autorisé, ni permission de conserver, normaliser ou transférer les données. Le Work Order ne
transforme aucune interprétation interne en avis juridique.

## 3. Objectifs

WO-033 doit :

1. produire un brouillon adressable à SofaScore décrivant honnêtement l'usage envisagé, les
   limites techniques et la topologie optionnelle ;
2. demander une réponse écrite sur la permission applicable, le canal d'accès officiel, les
   familles autorisées, les quotas, la rétention, la transformation et le transfert privé ;
3. distinguer une documentation publique, un accusé de réception et une permission applicable ;
4. documenter les champs d'identité et de contexte que seul le propriétaire peut compléter avant
   un éventuel envoi ;
5. définir le handoff nécessaire pour implémenter et exécuter le vrai receiver `betting-project`
   en même temps que le laboratoire lors d'une future qualification end-to-end ;
6. ordonner les qualifications Windows/Windows, Windows/VPS puis, sous révision de gouvernance,
   l'option VPS/VPS ;
7. préserver toutes les barrières réseau et de production jusqu'à des décisions distinctes.

## 4. Livrables

| Livrable | Chemin | État final de préparation |
|---|---|---|
| Brouillon fournisseur | `docs/validation/J9-WO033-SOFASCORE-PRODUCT-API-PERMISSION-REQUEST-DRAFT-20260901.md` | v1.2 `PREPARED_NOT_SENT` — SHA-256 `0934f6c68bf7b9c6d072c9fc616c809d75967c090f81402e5bc8cfdeb8bc150d` |
| Handoff receiver concurrent | `docs/validation/J9-WO033-BETTING-PROJECT-RECEIVER-CONCURRENT-READINESS-HANDOFF-20260901.md` | v1.2 `PREPARED_NOT_IMPLEMENTED` — SHA-256 `94c8b643bb6d20386f1ab9ca3694f7724222c9ff3a5a0898b32e2d2bbb43b18a` |
| Traçabilité projet | `README.md`, `CHANGELOG.md` | `UPDATED` |

Le brouillon est une preuve versionnée de préparation, pas une preuve d'envoi ni une permission.
Son hash atteste le modèle avec placeholders. Une future autorisation d'envoi devra porter sur le
SHA-256 d'un rendu final distinct, sans placeholder et byte-identique au texte effectivement
présenté dans le formulaire ; le hash du modèle ne peut pas autoriser ce rendu.

## 5. Exigence de receiver réel concurrent et trajectoire

La qualification future ne pourra pas se limiter au harness synthétique de WO-027. Elle devra
exécuter deux applications réelles dans des processus distincts :

1. `betting-sofascore-local-lab`, lié à `127.0.0.1`, envoyant une enveloppe J7 canonique
   `HUMAN_VALIDATED` entièrement synthétique tant que la permission reste `NOT_EVIDENCED` ;
2. le receiver implémenté dans le dépôt `betting-project`, lié à une adresse et un port explicitement
   qualifiés, validant le contrat, l'idempotence, l'effet unique et l'accusé borné.

Le receiver ne doit jamais déclencher une acquisition SofaScore. Les deux applications ne doivent
partager ni base, ni volume, ni secret implicite. Leur coexistence exige un futur Work Order dédié
dans `betting-project`, puis une qualification loopback/offline inter-processus avant toute cible
distante. Le worktree `betting-project` actuellement occupé par d'autres travaux ne sera pas
modifié sous WO-033.

Le palier 1 peut qualifier le vrai code avec une enveloppe synthétique avant la permission
fournisseur. Un test d'applications et de réseau réels avec J7 synthétique
(`REAL_APPLICATION_SYNTHETIC_E2E`) ne vaut pas livraison réelle de données dérivées fournisseur
(`PROVIDER_DERIVED_REAL_DELIVERY`) ; seule cette dernière exige la permission officielle
applicable.

Le palier 2 reste architecturalement un push sortant depuis Windows, mais touche un
hôte de production et exige donc une qualification VPS, un ingress explicitement choisi, mTLS, une
URI ou un nom DNS figé, un déploiement receiver autorisé et un go réseau séparé. Le palier 3 déplace
également le Local Lab et potentiellement Playwright sur le VPS : il est
`BLOCKED_BY_CURRENT_GOVERNANCE` tant que `LOCAL_ONLY`, ADR-SS-001, ADR-SS-003 et la frontière des
données brutes n'ont pas été explicitement révisés.

## 6. Hors périmètre

- envoi du formulaire, d'un courriel ou de toute autre correspondance externe ;
- appel des endpoints SofaScore, relance de campagne J3/J4/J5 ou réutilisation d'un ancien go ;
- modification d'ADR-SS-001 ou d'ADR-SS-003 ;
- changement Java, Spring, Maven, SQL, Docker, endpoint, transport, certificat ou configuration ;
- implémentation ou démarrage du receiver `betting-project` ;
- connexion à une cible réelle, livraison d'un export, VPS, production, live, polling ou scheduler ;
- collecte ou versionnement d'une identité, d'un secret, d'un certificat ou d'un payload brut.

L'index `docs/reference/REFERENCES.md` reflète encore une hiérarchie antérieure à ADR-SS-003/WO-026.
Cette dette documentaire a été traitée séparément par WO-032, sans modifier les PDF de référence
immuables. WO-032 est validé et poussé sur sa branche dédiée, mais n'est pas incorporé silencieusement
à la base du présent Work Order.

## 7. Portes ultérieures distinctes

### 7.1 Envoi de la demande

Un éventuel envoi exigera d'abord un rendu final UTF-8 sans placeholder, préparé hors Git pour ne
pas versionner les données personnelles. Un bloc propriétaire séparé devra identifier son SHA-256
exact, la destination `https://corporate.sofascore.com/contact`, la catégorie `Product -> API`, le
mode `COMPACT_ONLY`, `PRIMARY_ONLY` ou `PRIMARY_PLUS_APPENDIX`, le compteur de placeholders égal à
zéro, l'identité
d'expédition, le statut du projet, l'usage betting, la fréquence attendue, l'adresse de réponse et
tous les choix de rétention, revente, redistribution, entraînement et hébergement. Sans concordance
des octets au moment de l'envoi, le brouillon reste `PREPARED_NOT_SENT`.

### 7.2 Qualification d'une réponse

Un accusé automatique ou une réponse ambiguë ne satisfait pas la porte. Toute réponse devra être
archivée sans secret, datée, reliée au message effectivement envoyé et évaluée quant à son auteur,
son applicabilité, son périmètre, sa durée et ses conditions. Une revue compétente restera requise
si le texte exige une interprétation juridique.

### 7.3 Receiver et test end-to-end

Même avec une permission compatible, le test réel exigera au minimum :

- un Work Order distinct dans `betting-project` et un worktree propre dédié ;
- le contrat/version J7 accepté, un endpoint dédié, mTLS, idempotence, effet unique et accusé borné ;
- des ports, profils, bases et secrets explicitement séparés pour l'exécution concurrente ;
- une qualification offline/loopback des deux vraies applications, sans fournisseur ;
- une revue d'ADR-SS-003 et une autorisation propriétaire distincte pour la première livraison
  réelle ;
- une autorisation fournisseur indépendante si cette qualification comprend une nouvelle
  acquisition.

La progression entre les paliers n'est pas automatique :

| Palier | Portée | Porte supplémentaire minimale |
|---|---|---|
| 1 — Windows/Windows | deux vrais processus, réseau loopback | receiver et sender qualifiés, go local dédié ; export synthétique autorisable avant permission |
| 2 — Windows/VPS | Local Lab Windows vers receiver du VPS de production | inventaire et durcissement VPS, choix d'ingress, mTLS distant, DNS/SAN, sauvegarde/restauration, déploiement et réseau explicitement autorisés |
| 3 — VPS/VPS éventuel | Local Lab et Betting Project sur le VPS de production | permission couvrant l'acquisition hébergée, révision ADR-SS-001/003 et règles `LOCAL_ONLY`, isolation Playwright, données brutes et production |

La réussite d'un palier ne vaut ni autorisation ni preuve du suivant.

## 8. Vérifications et définition de fini

1. brouillon fournisseur complet, factuel et marqué `NOT_SENT` ;
2. aucun nom, statut commercial, organisation ou adresse de réponse inventés ;
3. questions explicites sur accès, familles, quota, automatisation, rétention, dérivation,
   transfert privé et contexte betting ;
4. handoff receiver décrivant la coexistence de deux processus réels, l'ordre de démarrage, les
   trois placements successifs, leurs barrières et les critères de qualification ;
5. aucun changement dans le dépôt `betting-project` ;
6. `README.md` et `CHANGELOG.md` mis à jour ;
7. `mvnw.cmd --offline clean verify` ;
8. `git diff --check`, scan des secrets, contrôle de `server.address=127.0.0.1` et des flags
   réseau bloqués ;
9. empreintes des deux livrables calculées et état passé à `READY_FOR_OWNER_REVIEW` ;
10. aucun envoi, appel d'acquisition fournisseur, réseau receiver ou livraison observé ; seules
    les trois pages officielles sont consultées en lecture seule.

Les tests d'intégration ne sont pas requis pour ce lot documentaire qui ne modifie ni code,
migration, schéma, persistance ou configuration.

## 9. Registre d'exécution

| Date/heure UTC | Action | Résultat |
|---|---|---|
| `2026-09-01T15:41:29.1426445Z` | `origin/main` et commit de base contrôlés | `PASS` — `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089` |
| `2026-09-01T15:41:29.1426445Z` | numéro initial WO-031, branche et worktree dédiés contrôlés | `PASS` — état historique avant découverte de la collision |
| `2026-09-01T15:41:29.1426445Z` | portée de préparation et interdictions réseau enregistrées | `PASS` |
| `2026-09-01T15:44:51.0151176Z` | conditions, documentation API externe et canal `Product -> API` officiels rafraîchis en lecture seule | `PASS` — aucune permission applicable déduite |
| `2026-09-01T15:55:13Z` | `mvnw.cmd --offline clean verify` sous le compte propriétaire | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0` |
| `2026-09-01T15:57:40.9090014Z` | relecture indépendante, liens, cohérence inter-dépôts et corrections de gouvernance | `PASS` |
| `2026-09-01T15:57:40.9090014Z` | hashes des deux livrables, diff, flags et traçabilité préparés | `PASS` |
| `2026-09-01T16:51:36.7174622Z` | trajectoire propriétaire Windows/Windows, Windows/VPS et VPS/VPS enregistrée | `PASS` — aucune connexion au VPS ni autorisation réseau déduite |
| `2026-09-01T17:02:53Z` | `mvnw.cmd --offline clean verify` après révision v1.1 | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, aucun réseau fournisseur/receiver/VPS |
| `2026-09-01T17:04:10.6191936Z` | postflight Docker, ports et processus du worktree | `PASS` — seul PostgreSQL local préexistant sur `127.0.0.1:5432`, aucun processus ou conteneur de test résiduel |
| `2026-09-01T18:07:20.2600389Z` | décision propriétaire de réconciliation du numéro | `AUTHORIZE` — WO CI conserve `031`, lot fournisseur renuméroté `033` |
| `2026-09-01T18:07:20.2600389Z` | branche et worktree fournisseur renommés | `PASS` — aucune modification de la branche CI ni de la PR #23 |
| `2026-09-01T18:13:34.3075675Z` | livrables renommés figés en v1.2 et empreintes recalculées | `PASS` — identité `033`, anciennes références conservées comme historique remplacé |
| `2026-09-01T18:21:47Z` | `mvnw.cmd --offline clean verify` après renumérotation | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, aucun appel fournisseur/receiver/VPS |
| `2026-09-01T18:22:46.3756532Z` | postflight conteneurs, processus et listeners | `PASS` — aucun résidu Testcontainers, processus lié au worktree ou listener `8087`/`8444` ; seul PostgreSQL local préexistant sur `127.0.0.1:5432` reste actif |
| `2026-09-01T18:28:18.8023401Z` | bloc de revue propriétaire reçu et vérifié | `VALIDATE` — commit qualifié, commit de renumérotation, versions et hashes concordants ; readiness reconnue |
| `2026-09-01T18:28:18.8023401Z` | déplacement du Work Order de `active` vers `completed` | `PASS` — autorisé explicitement, sans envoi ni ouverture d'une barrière réseau |

## 10. État courant

```text
WORK_ORDER=WO-SS-20260901-033-j9-provider-permission-request-preparation
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260901-033-j9-provider-permission-request-preparation.md
WORK_ORDER_RENUMBER_AUTHORIZED=YES
WORK_ORDER_RENUMBER_DECISION=AUTHORIZE_RENUMBER_FROM_WO031_TO_WO033
WORK_ORDER_RENUMBER_REASON=RESOLVE_COLLISION_WITH_DISTINCT_CI_WO031
WORK_ORDER_PREVIOUS_ID=WO-SS-20260901-031-j9-provider-permission-request-preparation
WORK_ORDER_CURRENT_ID=WO-SS-20260901-033-j9-provider-permission-request-preparation
RENUMBERED_FROM=WO-SS-20260901-031-j9-provider-permission-request-preparation
NUMBER_COLLISION_WITH=WO-SS-20260901-031-ci-gitlab-local-only
NUMBER_COLLISION_SCOPE=CONTINUOUS_INTEGRATION_ENVIRONMENT_NOT_J9
RENUMBERING_OWNER_AUTHORIZED=YES
PRE_RENUMBER_BRANCH=codex/j9-wo031-provider-permission-request
CURRENT_BRANCH=codex/j9-wo033-provider-permission-request
PRE_RENUMBER_WORKTREE=.tmp/j9-wo031-provider-permission-request
CURRENT_WORKTREE=.tmp/j9-wo033-provider-permission-request
BRANCH_RENAME_RECORDED_AT_UTC=2026-09-01T18:06:32Z
DISTINCT_CI_WO031_PRESERVED=YES
DISTINCT_CI_WO031_REF=origin/codex/ss-20260901-031-ci-bootstrap
DISTINCT_CI_WO031_COMMIT=01eea4ba60e966ca48a5824575a6b895d6a48a1b
DISTINCT_CI_WO031_PATH=docs/work_orders/active/WO-SS-20260901-031-ci-gitlab-local-only.md
OLD_DOCUMENTATION_COMMIT=e659ff8fc05ce99913d87ae554c9356581e852f8
OLD_REFERENCES_STATUS=SUPERSEDED_BY_AUTHORIZED_RENUMBERING
PREPARATION_AUTHORIZED=YES
HISTORICAL_OPENING_COMMIT=35d8588c4cfb1214c3f8ea26f064f91672fa706b
HISTORICAL_OPENING_IDENTIFIER=WO-SS-20260901-031-j9-provider-permission-request-preparation
OPENING_COMMIT_SCOPE=PRE_RENUMBERING_WO031
PRE_RENUMBER_HEAD=e659ff8fc05ce99913d87ae554c9356581e852f8
HISTORY_REWRITE=NO
COMMIT_STRATEGY=APPEND_ONLY
RENUMBERING_COMMIT=cdb9c73fc26c6456d3e42020108fadb55769398d
QUALIFIED_COMMIT=9535478d54d26b35c95bef5a7e7615fe23c68de8
QUALIFIED_COMMIT_MATCH=YES
RENUMBERING_COMMIT_MATCH=YES
WO033_LOCAL_COLLISION_CHECK=PASS
WO033_REMOTE_COLLISION_CHECK=PASS
PRE_RENUMBER_DRAFT_PATH=docs/validation/J9-WO031-SOFASCORE-PRODUCT-API-PERMISSION-REQUEST-DRAFT-20260901.md
CURRENT_DRAFT_PATH=docs/validation/J9-WO033-SOFASCORE-PRODUCT-API-PERMISSION-REQUEST-DRAFT-20260901.md
PERMISSION_REQUEST_DRAFT_STATUS=PREPARED_NOT_SENT
PERMISSION_REQUEST_DRAFT_PRE_RENUMBER_VERSION=1.1
PERMISSION_REQUEST_DRAFT_VERSION=1.2
PERMISSION_REQUEST_DRAFT_SHA256=0934f6c68bf7b9c6d072c9fc616c809d75967c090f81402e5bc8cfdeb8bc150d
PERMISSION_REQUEST_DRAFT_OWNER_REVIEW_MATCH=YES
OLD_PERMISSION_REQUEST_DRAFT_SHA256=27daa5859ce52d6bf2e4459a4ba7ab4048bab21e5b23e12fddb226e6ce228b3e
FINAL_RENDERED_REQUEST_STATUS=NOT_CREATED_REQUIRES_OWNER_VALUES
FINAL_RENDERED_UNRESOLVED_PLACEHOLDER_COUNT=NOT_EVALUATED
PERMISSION_REQUEST_SENT=NO
OWNER_SEND_DECISION=NOT_RECEIVED
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
OFFICIAL_PERMISSION_GATE_SATISFIED=NO
PRE_RENUMBER_HANDOFF_PATH=docs/validation/J9-WO031-BETTING-PROJECT-RECEIVER-CONCURRENT-READINESS-HANDOFF-20260901.md
CURRENT_HANDOFF_PATH=docs/validation/J9-WO033-BETTING-PROJECT-RECEIVER-CONCURRENT-READINESS-HANDOFF-20260901.md
BETTING_PROJECT_RECEIVER_CONCURRENT_HANDOFF_STATUS=PREPARED_NOT_IMPLEMENTED
BETTING_PROJECT_RECEIVER_CONCURRENT_HANDOFF_PRE_RENUMBER_VERSION=1.1
BETTING_PROJECT_RECEIVER_CONCURRENT_HANDOFF_VERSION=1.2
BETTING_PROJECT_RECEIVER_CONCURRENT_HANDOFF_SHA256=94c8b643bb6d20386f1ab9ca3694f7724222c9ff3a5a0898b32e2d2bbb43b18a
BETTING_PROJECT_RECEIVER_CONCURRENT_HANDOFF_OWNER_REVIEW_MATCH=YES
OLD_BETTING_PROJECT_RECEIVER_CONCURRENT_HANDOFF_SHA256=63b5f5f9968d4eea4190e2702e3589e7f20669b91e7f84622f33cc458cfb5ca6
BETTING_PROJECT_RECEIVER_IMPLEMENTED=NO
BETTING_PROJECT_RECEIVER_RUNNING=NO
J7_EXPORT_DELIVERY_TRIGGER=EXPLICIT_OPERATOR_ACTIVATION_AFTER_HUMAN_VALIDATED
J7_EXPORT_ACTIVATION_SUBMITS_RECEIVER_REQUEST=REQUIRED_FUTURE_BEHAVIOR
E2E_STAGE_1_STATUS=OWNER_REQUIRED_NOT_IMPLEMENTED
E2E_STAGE_2_STATUS=OWNER_REQUIRED_NOT_AUTHORIZED
E2E_STAGE_3_STATUS=OWNER_FUTURE_OPTION_BLOCKED_BY_CURRENT_GOVERNANCE
OWNER_DECLARED_PRODUCTION_VPS_IPV4=51.255.167.32
OWNER_DECLARED_PRODUCTION_VPS_STATE=AVAILABLE_UNCONFIGURED_NOT_QUALIFIED
VPS_CONNECTION_OR_PROBE_PERFORMED=NO
KNOWN_REFERENCE_INDEX_GAP=ADDRESSED_BY_WO032_ON_SEPARATE_PUSHED_BRANCH_NOT_IN_CURRENT_BASE
STANDARD_VERIFY=PASS
STANDARD_VERIFY_COMMAND=mvnw.cmd --offline clean verify
STANDARD_VERIFY_LATEST_AT_UTC=2026-09-01T18:21:47Z
STANDARD_VERIFY_INITIAL_SANDBOX_ATTEMPT=STOPPED_PRE_BUILD_LOCAL_MAVEN_PARENT_NOT_VISIBLE
SUREFIRE_TESTS=1043
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
FAILSAFE_TESTS=84
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0
POSTFLIGHT=PASS_ONLY_PREEXISTING_LOCAL_POSTGRES
POSTFLIGHT_AT_UTC=2026-09-01T18:22:46.3756532Z
TESTCONTAINERS_RESIDUAL=NONE
RELATED_WORKTREE_PROCESS_RESIDUAL=NONE
LISTENER_8087=NONE
LISTENER_8444=NONE
OFFICIAL_SOURCE_READ_ONLY_HTTPS=YES
PROVIDER_ENDPOINT_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
RECEIVER_VPS_DEPLOYMENT_AUTHORIZED=NO
LOCAL_LAB_VPS_DEPLOYMENT_AUTHORIZED=NO
PROVIDER_VPS_ACQUISITION_AUTHORIZED=NO
PRODUCTION_INGESTION_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
OWNER_REVIEW_REQUIRED=NO
OWNER_REVIEW_DECISION=VALIDATE
OWNER_REVIEW_BLOCK_STATUS=COMPLETE
LOCAL_READINESS_ACKNOWLEDGED=YES
WORK_ORDER_MOVE_TO_COMPLETED=YES
MOVE_TO_COMPLETED_AUTHORIZED=YES
MOVE_TO_COMPLETED_PERFORMED=YES
```

## 11. Bloc de revue propriétaire reçu

Le propriétaire valide le lot documentaire et autorise son déplacement vers les Work Orders
terminés. La revue documentaire et l'autorisation d'envoyer la demande restent deux décisions
séparées : le bloc reçu refuse explicitement l'envoi, le réseau fournisseur, le receiver réel, la
livraison réelle, le VPS et la production.

```text
J9_WO033_OWNER_REVIEW_DECISION=VALIDATE
J9_WO033_WORK_ORDER=WO-SS-20260901-033-j9-provider-permission-request-preparation
J9_WO033_QUALIFIED_COMMIT=9535478d54d26b35c95bef5a7e7615fe23c68de8
J9_WO033_RENUMBERING_COMMIT=cdb9c73fc26c6456d3e42020108fadb55769398d
J9_WO033_PERMISSION_REQUEST_DRAFT_VERSION=1.2
J9_WO033_PERMISSION_REQUEST_DRAFT_SHA256=0934f6c68bf7b9c6d072c9fc616c809d75967c090f81402e5bc8cfdeb8bc150d
J9_WO033_RECEIVER_HANDOFF_VERSION=1.2
J9_WO033_RECEIVER_HANDOFF_SHA256=94c8b643bb6d20386f1ab9ca3694f7724222c9ff3a5a0898b32e2d2bbb43b18a
J9_WO033_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO033_WORK_ORDER_MOVE_TO_COMPLETED=YES
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PERMISSION_REQUEST_SEND_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REAL_RECEIVER_NETWORK_AUTHORIZED=NO
J9_REAL_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```
