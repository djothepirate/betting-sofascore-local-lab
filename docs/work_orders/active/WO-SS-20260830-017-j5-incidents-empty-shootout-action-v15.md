# WO-SS-20260830-017 — Incidents J5 V15, tableau d'action vide pendant une séance terminale

- **Statut :** `IN_PROGRESS`
- **Date :** 2026-08-30
- **Date de démarrage :** 2026-08-30
- **Qualification propriétaire :** `NOT_REQUESTED`
- **Clôture :** `ACTIVE`
- **Prérequis :** campagne J8 partielle et analyse bornée au commit `cfd536e`
- **Base locale :** `cfd536e`
- **Jalon :** correction J5 issue de la qualification J8
- **Branche :** `codex/j8-incidents-v15`
- **Demande propriétaire :** accepter une propriété absente ou un tableau vide uniquement dans
  une séance terminale de tirs au but déjà cohérente, tout en rejetant les listes non vides
  incohérentes, les types erronés et les séances temporellement mixtes
- **Preuve locale analysée :** snapshot incidents J8 `717`, sans copie du payload dans Git
- **Payload fournisseur ajouté à Git :** `NO`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Appel fournisseur pendant les tests automatisés :** `NOT_AUTHORIZED`
- **Nouvel endpoint, URI, allowlist ou transport :** `NO`
- **Polling, planification, live réseau, fallback ou retry :** `NOT_AUTHORIZED`
- **Modification de l'ADR-SS-001 :** `NOT_REQUIRED_AFTER_EXPLICIT_REVIEW`
- **Migration prévue :** `V28_J5_INCIDENTS_EMPTY_SHOOTOUT_ACTION_V15`

## 1. Objectif

Créer le parseur versionné `event-incidents-v15`, héritier de V14, afin de reconnaître la variante
JSON observée pendant la campagne J8 : pour une tentative de séance de tirs au but sans source de
minute, `footballPassingNetworkAction` peut être omis ou présent sous la forme exacte d'un tableau
vide.

Cette équivalence ne s'applique que si la réponse complète satisfait déjà toutes les protections
du contexte terminal V12 : marqueur `PEN` unique et inactif, période de jeu `FT` ou `ET` terminée,
séquences contiguës et uniques, tentatives exclusivement non minutées, classes fermées, scores et
identités de côté typés, puis score terminal égal à celui de la dernière tentative.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Preuve et diagnostic faisant autorité

La preuve antérieure, snapshot `189`, omet la propriété sur les tentatives non minutées et reste
parseable par V14. La réponse J8, snapshot `717`, utilise un tableau vide sur quatorze tentatives
cohérentes et échoue sous V14. Une sonde locale en mémoire a montré que traiter exclusivement ces
tableaux vides comme absents suffit à obtenir `PARSED`, sans appel fournisseur ni persistance.

```text
OLD_REPRESENTATION=PROPERTY_ABSENT
NEW_OBSERVED_REPRESENTATION=EMPTY_ARRAY
V14_NEW_REPRESENTATION=SCHEMA_INCOMPATIBLE
BOUNDED_COUNTERFACTUAL=PARSED
J8_INSTRUMENTATION_CAUSALITY=NOT_SUPPORTED_BY_LOCAL_EVIDENCE
GLOBAL_PROVIDER_SCHEMA_CHANGE=NOT_CLAIMED
```

Le payload exact reste dans la base locale append-only. Les tests utiliseront uniquement une
fixture minimale synthétique, sans équipes, joueurs, URI, header, cookie, jeton ou donnée de
session provenant de la réponse réelle.

## 3. Revue ADR-SS-001 v1.4

Le correctif intervient après la réception du brut et ne modifie aucune capacité réseau. Les
endpoints J5, l'allowlist, le protocole worker Playwright v5, les requêtes, le transport, la
concurrence unitaire, la temporisation, l'arrêt terminal et l'absence de retry restent inchangés.

```text
ADR_SS_001_WO_017_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
NEW_ENDPOINT=NO
ALLOWLIST_CHANGE=NO
PLAYWRIGHT_PROTOCOL_CHANGE=NO
PROVIDER_CAMPAIGN_AUTHORIZED=NO
```

## 4. Contrat fermé V15

Pour `footballPassingNetworkAction` sur une tentative candidate non minutée :

- propriété absente : admissible dans le contexte terminal cohérent ;
- tableau JSON vide : équivalent à l'absence dans ce même contexte ;
- `null`, objet, chaîne, nombre ou booléen : type erroné, non admissible ;
- tableau non vide : jamais assimilé à une absence ; les règles historiques de minute imbriquée
  restent applicables, et toute combinaison terminale incohérente reste refusée ;
- mélange de tentatives effectivement minutées et non minutées : séance mixte refusée ;
- les formes absente et tableau vide peuvent coexister entre tentatives, puisqu'elles portent la
  même sémantique d'absence de source auxiliaire.

V15 ne déduit aucune minute. Il conserve les warnings et la mesure de complétude V12 : la minute
reste absente, son rendu reste `—` et les chemins manquants restent mesurés.

## 5. Périmètre inclus

- point d'extension protégé dans V12 dont le comportement historique par défaut reste
  « propriété absente seulement » ;
- `EventIncidentsV15Parser` surchargeant uniquement cette politique avec
  « absente ou tableau vide » ;
- câblage V15 dans la campagne J5 directe, l'import JSON local et l'import multi-match partagé ;
- compatibilité de lecture des observations incidents V1 à V15 ;
- migration Flyway append-only V28 ajoutant V15 à la contrainte fermée ;
- fixture synthétique et tests positifs/négatifs ciblés ;
- tests de migration propre et upgrade V27 vers V28 ;
- mise à jour des exigences, de l'architecture, des runbooks, du README, du changelog et des
  preuves J8 concernées ;
- validation standard et PostgreSQL sans réseau.

## 6. Hors périmètre

- modification ou suppression des migrations V1 à V27 ;
- réécriture automatique d'une observation historique ;
- reparse persistant automatique du snapshot `717` ;
- nouvelle campagne J5, import opérateur ou appel fournisseur ;
- relance de la campagne bornée J8 ;
- assouplissement d'une autre structure incidents ;
- adoption J9, qualification humaine ou clôture automatique de J8 ;
- nouvel endpoint, URI, transport, retry, polling, scheduler ou mécanisme navigateur.

## 7. Critères d'acceptation

- [ ] V14 continue de refuser la variante tableau vide ;
- [ ] V15 accepte la forme historique avec propriété absente ;
- [ ] V15 accepte le tableau vide dans une séance terminale cohérente ;
- [ ] V15 accepte une séance cohérente combinant absence et tableau vide ;
- [ ] V15 ne crée ni minute ni signal de complétude artificiel ;
- [ ] une liste non vide incohérente reste refusée ;
- [ ] `null`, objet, chaîne, nombre et booléen restent refusés ;
- [ ] une séance mêlant tentative minutée et tentative non minutée reste refusée ;
- [ ] les gaps, doublons, scores terminaux divergents et marqueurs invalides restent refusés ;
- [ ] les règles V14 `Extra time` et V13 restent inchangées ;
- [ ] les voies directe et import utilisent V15 ;
- [ ] la lecture des versions historiques reste compatible ;
- [ ] V28 est append-only et l'upgrade V27 vers V28 ne réécrit aucune donnée ;
- [ ] `mvnw clean verify` est vert ;
- [ ] `mvnw -Pintegration-tests verify` est vert ;
- [ ] `Verify-Local.ps1 -WithIntegrationTests` est vert sans appel fournisseur ;
- [ ] `docker compose --env-file .env config` et `git diff --check` sont verts ;
- [ ] le Work Order reste actif jusqu'à une décision propriétaire séparée de qualification.

## 8. Livraison Git prévue

Les commits locaux seront découpés en :

1. ouverture du Work Order correctif ;
2. parseur V15 et migration V28 ;
3. câblage, tests et documentation ;
4. readiness technique.

Aucun push, aucune Pull Request, aucune fusion dans `main` et aucune nouvelle campagne fournisseur
ne sont autorisés par ce Work Order.
