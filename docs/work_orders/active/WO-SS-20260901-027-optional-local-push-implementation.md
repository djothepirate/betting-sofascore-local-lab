# WO-SS-20260901-027 — Implémentation fail-closed du push local optionnel

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Jalon :** après J9 — implémentation bornée de `OPTIONAL_LOCAL_PUSH`
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T08:02:45.4284643Z`
- **Ouverture Europe/Paris :** `2026-09-01T10:02:45.4284643+02:00`
- **Branche :** `codex/j9-optional-local-push-implementation`
- **Worktree :** `.tmp/j9-optional-local-push-implementation`
- **Base locale vérifiée :** `aedb5f424883c9e7a1839fd50aa2c2689fa65418`
- **Work Order parent :** `WO-SS-20260901-026-optional-integration-feasibility` — `VALIDATED`
- **ADR :** `ADR-SS-003 v0.1` — `ACCEPTED`
- **Draft ADR accepté :** `ca789a3a40ea5fc6c16312bd73f675bc9fd32650`
- **SHA-256 du draft ADR accepté :**
  `0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be`
- **Topologie sélectionnée :** `OPTIONAL_LOCAL_PUSH`
- **Permission officielle :** `NOT_EVIDENCED`
- **Type de lot :** contrat versionné, socle local désactivé, persistance séparée et qualifications
  synthétiques offline/loopback ; aucune cible réelle

## 1. Autorisation reçue et interprétation bornée

Le propriétaire autorise la réalisation d'un Work Order d'implémentation portant notamment sur :

- la permission officielle toujours `NOT_EVIDENCED` ;
- le contrat du receiver du Betting Project ;
- mTLS ;
- l'idempotence ;
- les accusés synchrones minimisés ;
- des états de livraison distincts de la validation J7 ;
- des qualifications offline et loopback avant tout réseau réel.

Cette autorisation remplace, pour le présent lot local et synthétique, le champ historique
`INTEGRATION_IMPLEMENTATION_AUTHORIZED=NO` enregistré lors de l'acceptation d'ADR-SS-003. Elle ne
transforme toutefois aucune décision interne en permission d'usage et ne satisfait pas la porte
explicite d'ADR-SS-003 §9.2. En conséquence, le lot peut rendre exécutoires et testables des
invariants fail-closed sans rendre l'option A utilisable contre une cible réelle.

```text
WORK_ORDER=WO-SS-20260901-027-optional-local-push-implementation
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
REAL_USE_STATUS=BLOCKED_BY_PERMISSION_GATE
OWNER_IMPLEMENTATION_AUTHORIZATION=RECEIVED
IMPLEMENTATION_AUTHORIZATION=LOCAL_FAIL_CLOSED_OFFLINE_AND_LOOPBACK_ONLY
ADR_SS_003_STATUS=ACCEPTED
ADR_SS_003_SELECTED_TOPOLOGY=OPTIONAL_LOCAL_PUSH

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
OFFICIAL_PERMISSION_GATE_SATISFIED=NO
REAL_RUNTIME_ACTIVATION_ALLOWED=NO

BETTING_PROJECT_RECEIVER_CONTRACT_DEFINITION=IN_SCOPE
BETTING_PROJECT_RECEIVER_IMPLEMENTATION_AUTHORIZED=NO
BETTING_PROJECT_REPOSITORY_MUTATION_AUTHORIZED=NO
LOCAL_SYNTHETIC_RECEIVER_HARNESS_AUTHORIZED=YES

OPTIONAL_LOCAL_PUSH_FAIL_CLOSED_SENDER_IMPLEMENTATION_AUTHORIZED=YES
SEPARATE_DELIVERY_LEDGER_IMPLEMENTATION_AUTHORIZED=YES
APPEND_ONLY_DELIVERY_LEDGER_MIGRATION=AUTHORIZED
J7_EXPORT_SCHEMA_CHANGE=NO

LOOPBACK_AND_OFFLINE_QUALIFICATION_AUTHORIZED=YES
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
POLLING_OR_SCHEDULER=NO
AUTOMATIC_RETRY=NO
FALLBACK=NO
PRIMARY_DATABASE_PURGE=NO
PUSH_AUTHORIZED=NO
```

Le receiver réel appartient à un autre dépôt et exige, conformément à ADR-SS-003 §13, son propre
Work Order. Le présent dépôt ne versionne que le contrat consommable et un harness de qualification
synthétique. Aucun endpoint du Betting Project et aucune URI réelle ne sont créés ici.

## 2. Porte de permission officielle

La revue qualifiée d'ouverture est versionnée dans
[`J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md`](../../validation/J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md).
Elle maintient déterministement `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` : une documentation
technique publique et une rubrique de contact API ne constituent pas une licence ou un consentement,
et aucune autorisation explicite applicable au corpus et à son transfert n'est disponible dans le
dépôt.

Conséquences exécutoires obligatoires :

1. le kill switch est désactivé par défaut ;
2. une configuration `NOT_EVIDENCED` refuse toute tentative avant ouverture de socket ;
3. aucune URI réelle n'est fournie par défaut ou par les tests ;
4. seul un receiver synthétique lié à `127.0.0.1` sur port éphémère peut être utilisé par les
   qualifications dédiées ;
5. toute activation future exige une preuve versionnée, une revue compétente si nécessaire, un
   réexamen d'ADR-SS-003 et une décision propriétaire distincte ;
6. ce Work Order ne produit aucun avis juridique.

## 3. Objectif

Produire un socle local vérifiable qui ferme les risques techniques identifiés par ADR-SS-003 sans
autoriser son usage réel :

- contrat d'import versionné pour un export J7 canonique v1 immuable et `HUMAN_VALIDATED` ;
- identité et clé d'idempotence stables fondées sur `exportId + fileSha256` ;
- accusé synchrone minimisé et validation stricte de ses corrélations ;
- ledger distinct portant exactement les six états ADR ;
- zéro retry automatique et concurrence `1` ;
- politique JDK HTTP attestée dès le démarrage JVM avec
  `-Djdk.httpclient.disableRetryConnect=true`,
  `-Djdk.httpclient.redirects.retrylimit=1` et
  `-Djdk.httpclient.enableAllMethodRetry=false` ;
- politique mTLS fail-closed avec clé privée destinée au magasin utilisateur Windows et dont le
  provider Java n'expose aucun encodage ; la politique native de non-exportabilité reste une porte
  non qualifiée avant toute cible réelle ;
- classification déterministe des succès, doublons, refus et résultats ambigus ;
- preuve offline/loopback uniquement synthétique, sans SofaScore, Betting Project réel ou VPS.

## 4. Contrat receiver v1 à figer

| Dimension | Valeur cible WO-027 |
|---|---|
| Direction | `POST` HTTPS sortant du lab vers un endpoint d'import dédié |
| Corps | octets exacts de l'enveloppe J7 canonique v1 déjà `HUMAN_VALIDATED` |
| Taille | au plus `5 242 880` octets |
| Version | identifiant de protocole explicite et versionné |
| Idempotence | clé stable dérivée de `exportId` et du SHA-256 du fichier |
| Accusé | identité d'import distante, résultat, `exportId`, SHA-256 reçu, `receivedAt` |
| Résultats positifs | `IMPORTED` ou doublon exact explicitement confirmé |
| Conflit | `exportId` connu avec hash divergent : refus local avant claim ; HTTP `409` distant : refus terminal |
| Sécurité | mTLS obligatoire pour toute cible réelle ; validation du serveur et identité client |
| Reprise | aucune automatique ; action manuelle avec même artefact et même clé après réconciliation |

L'accusé ne contient jamais le document complet. Le receiver ne peut jamais appeler SofaScore et
son indisponibilité ne bloque aucune fonction critique du Betting Project.

## 5. États de livraison séparés

Le ledger est indépendant de la table et du statut J7. Il expose exactement :

```text
NOT_ATTEMPTED
IN_FLIGHT
DELIVERED
DUPLICATE_CONFIRMED
REJECTED_TERMINAL
UNKNOWN_RECONCILIATION_REQUIRED
```

- `HUMAN_VALIDATED` est une condition d'éligibilité et n'est jamais modifié par une livraison ;
- timeout, rupture TLS, erreur de transport, `5xx`, accusé absent, malformé ou non corrélé donnent
  `UNKNOWN_RECONCILIATION_REQUIRED` ;
- une réponse `4xx` normalement reçue, aux métadonnées valides et au corps borné, donne
  `REJECTED_TERMINAL` ; une réponse hostile ou surdimensionnée reste ambiguë sans HTTP fiable ;
- seuls un accusé valide et corrélé ou un doublon exact confirmé donnent respectivement
  `DELIVERED` ou `DUPLICATE_CONFIRMED` ;
- aucune transition ne déclenche J3, J4, J5 ou Playwright.

## 6. mTLS et secrets

Le profil cible impose la validation du certificat serveur, une identité client exacte et une clé
privée destinée à `Windows-MY`. Le code refuse une clé dont le provider Java expose un encodage ;
ce contrôle ne démontre pas à lui seul que la clé a été provisionnée avec une politique Windows
native non exportable. Cette preuve reste `NOT_QUALIFIED_FOR_REAL_TARGET` et devra être produite
avec le profil PKI réel. Aucun alias sensible, certificat, clé, mot de passe, payload ou chemin
utilisateur ne peut être conservé dans Git ou les logs. WO-027 qualifie la politique, les erreurs
et un échange mTLS synthétique ; il ne provisionne ni certificat de production ni receiver réel.

## 7. Lots d'implémentation

1. **Ouverture, permission et contrat :** versionner la revue officielle, le contrat receiver et le
   modèle de menace minimal.
2. **Domaine et persistance locale :** identité immuable, clé, accusé, machine d'états, journal de
   tentatives/résultats append-only et projection strictement gardée dans un ledger indépendant,
   sans changer J7.
3. **Sender fail-closed et mTLS :** relire l'export J7, imposer taille/hash/timeout/concurrence, zéro
   retry et refuser avant socket lorsque la permission est `NOT_EVIDENCED`. Les trois propriétés
   JDK HTTP exactes doivent être présentes au démarrage JVM et sont revérifiées avant construction
   du client puis avant envoi ; une valeur absente, tardive ou divergente ferme localement le
   transport.
4. **Qualification synthétique :** receiver test-only sur `127.0.0.1` et port éphémère ; aucun
   fournisseur ou listener persistant.

## 8. Cas de qualification obligatoires

- configuration par défaut bloquée et absence de socket ;
- permission `NOT_EVIDENCED` refusée avant résolution DNS ou connexion ;
- export non `HUMAN_VALIDATED`, hash divergent ou taille supérieure à 5 Mio refusé localement ;
- livraison nominale synthétique et accusé corrélé ;
- effet appliqué puis ACK perdu : reprise manuelle avec la même clé et le même artefact,
  `DUPLICATE_CONFIRMED` sans second effet receiver ;
- `exportId` connu avec hash divergent : refus local avant claim, sans ligne ni socket ; un HTTP
  `409` distant normalement reçu est `REJECTED_TERMINAL` ;
- accusé incohérent, `4xx`, `5xx`, timeout, arrêt receiver et rupture mTLS classifiés ;
- aucune reprise automatique, concurrence supérieure à `1` ou transition illégale ;
- arguments JVM anti-retry exacts observés depuis le processus de test, et refus avant
  `sendAsync` si l'une des trois valeurs diverge ;
- aucune modification du statut J7, invocation Playwright ou acquisition fournisseur ;
- aucun secret, payload ou certificat privé dans les logs, preuves ou Git ;
- nettoyage du receiver synthétique et absence de listener résiduel.

## 9. Vérifications et définition de fini

1. `mvnw.cmd clean verify` ;
2. `mvnw.cmd -Pintegration-tests verify` puisque la persistance évolue ;
3. `docker compose --env-file .env config` ;
4. `git diff --check` et scan de secrets ;
5. contrôle de `server.address=127.0.0.1`, des flags fournisseur bloqués et du profil de livraison
   désactivé ;
6. contrôle de zéro appel fournisseur et zéro livraison réelle ;
7. rapport de qualification, architecture, runbook, README et CHANGELOG à jour.

Le statut maximal atteint automatiquement est `READY_FOR_OWNER_REVIEW`. Le déplacement vers
`completed`, un push, l'activation runtime, le receiver Betting Project réel, une livraison réelle,
un déploiement VPS ou une production exigent chacun leur porte explicite applicable.

## 10. Registre d'exécution

| Étape | État | Preuve |
|---|---|---|
| Baseline, branche et worktree | `PASS` | base `aedb5f424883c9e7a1839fd50aa2c2689fa65418`, branche/worktree dédiés |
| Revue officielle | `PASS_REVIEW_NOT_PERMISSION` | rapport versionné ; résultat `NOT_EVIDENCED` |
| Contrat receiver | `IMPLEMENTED_LOCAL_CONTRACT_ONLY` | contrat v1.0 et ACK strict ; aucune implémentation externe autorisée |
| Ledger et sender fail-closed | `IMPLEMENTED_DISABLED` | V29, six états séparés, zéro cible réelle, barrières JDK anti-retry |
| Qualification offline/loopback | `PASS` | [rapport WO-027](../../validation/J9-WO027-OPTIONAL-LOCAL-PUSH-QUALIFICATION-20260901.md), zéro réseau fournisseur/receiver réel |
| Vérification finale | `PASS_READY_FOR_OWNER_REVIEW` | Surefire `1036/0/0/5`, Failsafe `84/0/0/0`, permission réelle toujours bloquante |

Le Work Order reste actif. Seule une validation propriétaire explicite peut autoriser son
déplacement vers `completed`; elle ne pourra pas, à elle seule, autoriser une cible ou une livraison
réelle tant que la porte de permission officielle et les autres prérequis d'ADR-SS-003 ne sont pas
satisfaits.
