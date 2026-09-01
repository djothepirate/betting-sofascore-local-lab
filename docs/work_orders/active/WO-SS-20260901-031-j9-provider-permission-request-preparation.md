# WO-SS-20260901-031 — Préparation de la demande de permission fournisseur et du receiver concurrent

- **Statut :** `ACTIVE_PREPARATION_ONLY`
- **Jalon :** après J9 — levée documentaire de la porte de permission et préparation end-to-end
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T15:41:29.1426445Z`
- **Ouverture Europe/Paris :** `2026-09-01T17:41:29.1426445+02:00`
- **Branche :** `codex/j9-wo031-provider-permission-request`
- **Worktree :** `.tmp/j9-wo031-provider-permission-request`
- **Base exacte :** `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089`
- **Type de lot :** documentation et handoff inter-dépôts uniquement ; aucun changement runtime,
  aucun envoi externe et aucun réseau réel

## 1. Autorisation reçue et interprétation bornée

Le propriétaire autorise la préparation d'une demande destinée au canal public SofaScore
`Product -> API`. Il établit également qu'un test end-to-end de l'envoi réel exigera la
disponibilité du receiver dans le dépôt `betting-project`, avec exécution simultanée du receiver et
de `betting-sofascore-local-lab`.

Cette autorisation permet de produire un brouillon exact, une liste de questions et un handoff de
readiness inter-dépôts. Elle n'autorise pas l'envoi de la demande, l'utilisation d'une identité ou
d'une adresse de réponse non fournies, un appel fournisseur, une connexion au receiver réel, une
livraison, un déploiement VPS ou un usage de production.

```text
J9_WO031_OWNER_DECISION=AUTHORIZE_PREPARATION
J9_WO031_SCOPE=PREPARE_SOFASCORE_PRODUCT_API_PERMISSION_REQUEST_AND_REAL_RECEIVER_CONCURRENCY_HANDOFF
SOFASCORE_PRODUCT_API_REQUEST_PREPARATION_AUTHORIZED=YES
SOFASCORE_PRODUCT_API_REQUEST_SEND_AUTHORIZED=NO
CONCURRENT_REAL_RECEIVER_REQUIREMENT=YES
BETTING_PROJECT_RECEIVER_REPOSITORY_MUTATION_AUTHORIZED=NO
PROVIDER_ENDPOINT_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 2. Situation de départ factuelle

La revue officielle versionnée sous
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

WO-031 doit :

1. produire un brouillon adressable à SofaScore décrivant honnêtement l'usage envisagé, les
   limites techniques et la topologie optionnelle ;
2. demander une réponse écrite sur la permission applicable, le canal d'accès officiel, les
   familles autorisées, les quotas, la rétention, la transformation et le transfert privé ;
3. distinguer une documentation publique, un accusé de réception et une permission applicable ;
4. documenter les champs d'identité et de contexte que seul le propriétaire peut compléter avant
   un éventuel envoi ;
5. définir le handoff nécessaire pour implémenter et exécuter le vrai receiver `betting-project`
   en même temps que le laboratoire lors d'une future qualification end-to-end ;
6. préserver toutes les barrières réseau et de production jusqu'à des décisions distinctes.

## 4. Livrables

| Livrable | Chemin cible | État initial |
|---|---|---|
| Brouillon fournisseur | `docs/validation/J9-WO031-SOFASCORE-PRODUCT-API-PERMISSION-REQUEST-DRAFT-20260901.md` | `NOT_CREATED` |
| Handoff receiver concurrent | `docs/validation/J9-WO031-BETTING-PROJECT-RECEIVER-CONCURRENT-READINESS-HANDOFF-20260901.md` | `NOT_CREATED` |
| Traçabilité projet | `README.md`, `CHANGELOG.md` | `NOT_UPDATED` |

Le brouillon est une preuve versionnée de préparation, pas une preuve d'envoi ni une permission.
Son empreinte SHA-256 sera calculée une fois son contenu stabilisé et enregistrée dans ce Work
Order sans créer d'auto-référence dans le fichier haché.

## 5. Exigence de receiver réel concurrent

La qualification future ne pourra pas se limiter au harness synthétique de WO-027. Elle devra
exécuter deux applications réelles dans des processus distincts :

1. `betting-sofascore-local-lab`, lié à `127.0.0.1`, produisant puis envoyant une enveloppe J7
   canonique déjà `HUMAN_VALIDATED` ;
2. le receiver implémenté dans le dépôt `betting-project`, lié à une adresse et un port explicitement
   qualifiés, validant le contrat, l'idempotence, l'effet unique et l'accusé borné.

Le receiver ne doit jamais déclencher une acquisition SofaScore. Les deux applications ne doivent
partager ni base, ni volume, ni secret implicite. Leur coexistence exige un futur Work Order dédié
dans `betting-project`, puis une qualification loopback/offline inter-processus avant toute cible
distante. Le worktree `betting-project` actuellement occupé par d'autres travaux ne sera pas
modifié sous WO-031.

## 6. Hors périmètre

- envoi du formulaire, d'un courriel ou de toute autre correspondance externe ;
- appel des endpoints SofaScore, relance de campagne J3/J4/J5 ou réutilisation d'un ancien go ;
- modification d'ADR-SS-001 ou d'ADR-SS-003 ;
- changement Java, Spring, Maven, SQL, Docker, endpoint, transport, certificat ou configuration ;
- implémentation ou démarrage du receiver `betting-project` ;
- connexion à une cible réelle, livraison d'un export, VPS, production, live, polling ou scheduler ;
- collecte ou versionnement d'une identité, d'un secret, d'un certificat ou d'un payload brut.

## 7. Portes ultérieures distinctes

### 7.1 Envoi de la demande

Un éventuel envoi exigera un bloc propriétaire séparé identifiant au minimum le brouillon exact et
son SHA-256, l'identité d'expédition, le statut du projet ou de l'organisation, une adresse de
réponse, la description d'usage approuvée et le canal cible. Sans ce bloc, le brouillon reste
`PREPARED_NOT_SENT`.

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

## 8. Vérifications et définition de fini

1. brouillon fournisseur complet, factuel et marqué `NOT_SENT` ;
2. aucun nom, statut commercial, organisation ou adresse de réponse inventés ;
3. questions explicites sur accès, familles, quota, automatisation, rétention, dérivation,
   transfert privé et contexte betting ;
4. handoff receiver décrivant la coexistence de deux processus réels, l'ordre de démarrage, les
   barrières et les critères de qualification ;
5. aucun changement dans le dépôt `betting-project` ;
6. `README.md` et `CHANGELOG.md` mis à jour ;
7. `mvnw.cmd --offline clean verify` ;
8. `git diff --check`, scan des secrets, contrôle de `server.address=127.0.0.1` et des flags
   réseau bloqués ;
9. empreinte du brouillon calculée et état passé à `READY_FOR_OWNER_REVIEW` ;
10. aucun envoi ou réseau réel observé.

Les tests d'intégration ne sont pas requis pour ce lot documentaire qui ne modifie ni code,
migration, schéma, persistance ou configuration.

## 9. Registre d'exécution

| Date/heure UTC | Action | Résultat |
|---|---|---|
| `2026-09-01T15:41:29.1426445Z` | `origin/main` et commit de base contrôlés | `PASS` — `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089` |
| `2026-09-01T15:41:29.1426445Z` | numéro WO-031, branche et worktree dédiés contrôlés | `PASS` |
| `2026-09-01T15:41:29.1426445Z` | portée de préparation et interdictions réseau enregistrées | `PASS` |

## 10. État courant

```text
WORK_ORDER=WO-SS-20260901-031-j9-provider-permission-request-preparation
WORK_ORDER_STATUS=ACTIVE_PREPARATION_ONLY
PREPARATION_AUTHORIZED=YES
PERMISSION_REQUEST_DRAFT_STATUS=NOT_CREATED
PERMISSION_REQUEST_SENT=NO
OWNER_SEND_DECISION=NOT_RECEIVED
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
OFFICIAL_PERMISSION_GATE_SATISFIED=NO
BETTING_PROJECT_RECEIVER_CONCURRENT_READINESS=NOT_PREPARED
BETTING_PROJECT_RECEIVER_IMPLEMENTED=NO
BETTING_PROJECT_RECEIVER_RUNNING=NO
PROVIDER_ENDPOINT_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
OWNER_REVIEW_REQUIRED=YES
```

