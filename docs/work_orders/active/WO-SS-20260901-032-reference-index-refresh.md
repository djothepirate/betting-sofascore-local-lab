# WO-SS-20260901-032 — Actualisation de l’index des références de cadrage

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Jalon :** après J9 — cohérence documentaire du référentiel de décision
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T17:12:33.2592273Z`
- **Ouverture Europe/Paris :** `2026-09-01T19:12:33.2592273+02:00`
- **Branche :** `codex/j9-wo032-reference-index-refresh`
- **Worktree :** `.tmp/j9-wo032-reference-index-refresh`
- **Base exacte :** `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089` (référence locale
  `origin/main` ; aucune actualisation distante exécutée sous ce lot)
- **Commit d’ouverture :** `e5258386c1582a1cb8fa6b394264889293617aa6`
- **Qualification locale terminée UTC :** `2026-09-01T17:20:32Z`
- **Qualification locale terminée Europe/Paris :** `2026-09-01T19:20:32+02:00`
- **Type de lot :** documentation locale seulement ; aucun changement de code, configuration,
  schéma, réseau, ADR ou PDF

## 1. Autorisation reçue et interprétation bornée

Le propriétaire demande un Work Order documentaire distinct pour actualiser l’index
`docs/reference/REFERENCES.md`, sans modifier les PDF de référence immuables.

Cette instruction autorise uniquement :

1. l’ouverture du présent Work Order dans une branche et un worktree dédiés ;
2. l’actualisation factuelle de l’index textuel après l’acceptation d’ADR-SS-003 et la clôture de
   WO-026 ;
3. la traçabilité correspondante dans `README.md` et `CHANGELOG.md` ;
4. les vérifications locales et les commits documentaires nécessaires.

Elle n’autorise ni push, ni pull request, ni fusion dans `main`, ni réécriture d’une preuve ou d’un
Work Order terminé. Elle n’ouvre aucun réseau fournisseur ou receiver, aucun déploiement VPS et
aucun usage de production.

```text
WORK_ORDER=WO-SS-20260901-032-reference-index-refresh
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
DOCUMENTATION_ONLY=YES
REFERENCE_INDEX_UPDATE_AUTHORIZED=YES
REFERENCE_PDF_CHANGE_AUTHORIZED=NO
ADR_CHANGE_AUTHORIZED=NO
COMPLETED_WORK_ORDER_OR_REPORT_REWRITE_AUTHORIZED=NO
APPLICATION_CODE_OR_CONFIGURATION_CHANGE_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PUSH_OR_MERGE_AUTHORIZED=NO
```

## 2. Dette documentaire observée

L’index versionné sur `origin/main` s’arrête à ADR-SS-002 et présente encore la comparaison entre
push local optionnel et Playwright VPS comme une étude future. Or les faits versionnés établissent
désormais :

- WO-026 est `VALIDATED` et terminé ;
- ADR-SS-003 v0.1 est `ACCEPTED` ;
- la topologie sélectionnée est `OPTIONAL_LOCAL_PUSH` ;
- Playwright VPS reste `DEFERRED_BLOCKED_BY_CURRENT_GOVERNANCE` ;
- WO-027 a implémenté et qualifié localement un socle fail-closed, sans cible réelle ;
- la permission officielle reste `NOT_EVIDENCED` ;
- aucune livraison réelle, acquisition fournisseur, exécution VPS ou production ne découle de ces
  décisions.

Le texte obsolète doit être remplacé sans réinterpréter les ADR et sans attribuer au propriétaire
une autorisation qu’il n’a pas accordée.

## 3. État initial immuable et empreintes

| Élément | État à l’ouverture |
|---|---|
| `docs/reference/REFERENCES.md` | 1 886 octets ; SHA-256 `d2cff7ce0bea80eb903d66bc293e90b6d7bdd8b38e81524ab082a16e751bd446` |
| `docs/reference/Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf` | 259 004 octets ; SHA-256 `746106cfe5142ad7b7426443b9730b22f281d6d844d108a5c371c7e08c9f1fee` ; blob Git `cfbe41b57c235163eeb60036ba9186c305981307` |
| Nombre de PDF physiques sous `docs/reference` | `1` |
| Diff `origin/main -- docs/reference` à l’ouverture | aucun |

L’empreinte, la taille et le blob du PDF devront être identiques après le lot. Aucun outil de
réécriture, conversion, normalisation de métadonnées ou rendu du PDF ne sera lancé.

## 4. Référentiel factuel autorisé

| Source versionnée | Fait utilisable dans l’index | Limite à conserver |
|---|---|---|
| ADR-SS-001 v1.4 | exception fournisseur locale Windows, manuelle et bornée | aucune acquisition directe depuis le VPS sous la gouvernance courante |
| ADR-SS-002 v1.1 | campagne J9 unique acceptée puis achevée ; ancien go consommé | aucun nouveau go ou appel fournisseur |
| WO-026 et ADR-SS-003 v0.1 | `OPTIONAL_LOCAL_PUSH` sélectionné ; Playwright VPS différé et bloqué | l’acceptation de l’ADR n’est pas une autorisation runtime ou réseau |
| WO-027 | socle de push local optionnel qualifié `PASS_LOCAL_FAIL_CLOSED` | permission officielle, receiver réel et livraison réelle non qualifiés |
| WO-028 | identité/version canonique J7 clarifiées dans ADR-SS-003 | aucun changement de topologie, version de décision ou autorisation |
| WO-029 | bornes de port du delivery local durcies et qualifiées | aucun receiver réel, réseau réel, VPS ou production |
| WO-030 | bornes de port Playwright fournisseur durcies dans un lot fournisseur distinct | aucun appel fournisseur, réseau receiver, VPS ou production |
| PDF de cadrage v0.1.0 | référence complémentaire du 8 août 2026 | fichier immuable et contenu non réinterprété par ce lot |

## 5. Modifications incluses

- ajouter ADR-SS-003 et les liens Markdown locaux vers les décisions versionnées dans
  `docs/reference/REFERENCES.md` ;
- actualiser la hiérarchie de décision et remplacer la phrase devenue obsolète sur l’étude future ;
- distinguer explicitement décision d’architecture, implémentation locale fail-closed et
  autorisations d’usage réel ;
- préserver `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` et les blocages VPS/production ;
- ajouter une entrée bornée dans `README.md` et `CHANGELOG.md` ;
- consigner les contrôles et empreintes dans le présent Work Order.

## 6. Hors périmètre

- toute modification de `docs/reference/*.pdf` ;
- toute modification d’ADR-SS-001, ADR-SS-002 ou ADR-SS-003 ;
- toute modification d’un Work Order terminé, d’un rapport de validation ou de `docs/architecture` ;
- tout changement Java, Maven, Spring, SQL, Docker, endpoint, transport ou configuration ;
- tout envoi de demande fournisseur, appel SofaScore ou réseau receiver ;
- tout secret, certificat, identité personnelle, payload brut ou donnée de session ;
- tout déploiement ou accès au VPS ;
- tout push, PR, fusion, rebase ou squash.

## 7. Vérifications et définition de fini

1. inventaire Git limité au présent Work Order, à `REFERENCES.md`, `README.md` et `CHANGELOG.md` ;
2. PDF de référence byte-identique : taille, SHA-256 et blob Git inchangés ;
3. liens Markdown locaux ajoutés résolus depuis `docs/reference` ;
4. faits ADR-SS-003/WO-026/WO-027/WO-028 concordants avec les sources versionnées ;
5. aucune permission officielle, autorisation réseau, VPS ou production déduite ;
6. `mvnw.cmd --offline clean verify` réussi ;
7. `git diff --check` et scan de secrets réussis ;
8. `server.address=127.0.0.1` et les flags réseau bloquants inchangés ;
9. aucun fichier PDF, code, configuration, migration, ADR ou rapport dans le diff ;
10. commit documentaire local dédié, sans push ni changement de `main`.

Les tests d’intégration n’étaient pas requis par la portée documentaire. Ils ont néanmoins été
exécutés avec succès parce qu’ils sont attachés au cycle `verify` courant du projet.

## 8. Résultats de qualification

| Contrôle | Résultat factuel |
|---|---|
| `mvnw.cmd --offline clean verify` | `PASS` à `2026-09-01T17:20:32Z` ; Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, `BUILD SUCCESS` |
| Liens Markdown locaux | `PASS` — 11 cibles locales, aucune absente |
| `git diff --check` | `PASS` |
| Scan de secrets haute confiance | `PASS` — aucune clé privée, credential, jeton ou valeur de mot de passe dans le diff |
| Allowlist du diff | `PASS` — quatre documents seulement : Work Order, index, README et changelog |
| PDF de référence | `PASS` — un seul PDF, 259 004 octets, SHA-256 et blob Git inchangés |
| Code, configuration, ADR, rapports et Work Orders terminés | `PASS` — aucun diff |
| Invariants réseau | `PASS` — `server.address=127.0.0.1`, flags fournisseur et push optionnel bloqués, fichiers byte-identiques à la base |
| Postflight | `PASS` — aucun listener `8087`, processus attribuable au worktree ou conteneur Testcontainers résiduel |
| Réseau réel | zéro appel fournisseur, zéro appel receiver réel, zéro accès VPS |

L’index final mesure 4 924 octets et porte le SHA-256
`959adb8d120386ede0a0fbd2b46a2883b57a04582d5b21134d62107b14342c91`.

## 9. Registre d’exécution

| Date/heure UTC | Action | Résultat |
|---|---|---|
| `2026-09-01T17:12:33.2592273Z` | référence locale `origin/main`, numéro WO-032, branche et worktree contrôlés | `PASS` — base `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089` ; fraîcheur distante non sollicitée |
| `2026-09-01T17:12:33.2592273Z` | inventaire et empreinte du PDF immuable | `PASS` — 259 004 octets, SHA-256 `746106cfe5142ad7b7426443b9730b22f281d6d844d108a5c371c7e08c9f1fee` |
| `2026-09-01T17:12:33.2592273Z` | périmètre documentaire et interdictions enregistrés | `PASS` |
| `2026-09-01T17:13:28Z` | commit local d’ouverture | `PASS` — `e5258386c1582a1cb8fa6b394264889293617aa6` |
| `2026-09-01T17:15:00Z` | index actualisé et hiérarchie confrontée aux ADR/WO versionnés | `PASS` — ADR-SS-003 et WO-026 à WO-030 intégrés sans extrapolation |
| `2026-09-01T17:20:32Z` | `mvnw.cmd --offline clean verify` | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0` |
| `2026-09-01T17:21:10.2147379Z` | liens, diff, secrets, invariants, empreintes et postflight | `PASS` |

## 10. État courant

```text
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
REFERENCE_INDEX_STATUS=CURRENT_THROUGH_WO030_ON_BASE_F3D7D3F
REFERENCE_INDEX_UPDATE_STATUS=COMPLETED
REFERENCE_INDEX_SHA256_BEFORE=d2cff7ce0bea80eb903d66bc293e90b6d7bdd8b38e81524ab082a16e751bd446
REFERENCE_INDEX_SHA256_AFTER=959adb8d120386ede0a0fbd2b46a2883b57a04582d5b21134d62107b14342c91
REFERENCE_PDF_IMMUTABILITY_STATUS=PASS_BYTE_IDENTICAL
REFERENCE_PDF_SHA256_BEFORE=746106cfe5142ad7b7426443b9730b22f281d6d844d108a5c371c7e08c9f1fee
REFERENCE_PDF_SHA256_AFTER=746106cfe5142ad7b7426443b9730b22f281d6d844d108a5c371c7e08c9f1fee
REFERENCE_PDF_GIT_BLOB_BEFORE=cfbe41b57c235163eeb60036ba9186c305981307
REFERENCE_PDF_GIT_BLOB_AFTER=cfbe41b57c235163eeb60036ba9186c305981307
LOCAL_QUALIFICATION_STATUS=PASS
OWNER_REVIEW_REQUIRED=YES
OWNER_REVIEW_DECISION=NOT_RECEIVED
MOVE_TO_COMPLETED_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PUSH_OR_MERGE_AUTHORIZED=NO
```

## 11. Références

- [Index à actualiser](../../reference/REFERENCES.md)
- [WO-026 — étude de faisabilité](../completed/WO-SS-20260901-026-optional-integration-feasibility.md)
- [WO-027 — implémentation locale fail-closed](../completed/WO-SS-20260901-027-optional-local-push-implementation.md)
- [WO-028 — clarification de l’identité J7](../completed/WO-SS-20260901-028-adr-ss-003-j7-schema-identity-clarification.md)
- [WO-029 — durcissement du port de delivery](../completed/WO-SS-20260901-029-j9-optional-local-push-port-boundary-hardening.md)
- [WO-030 — durcissement du port Playwright fournisseur](../completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md)
- [ADR-SS-001](../../../ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md)
- [ADR-SS-002](../../../ADR-SS-002-bounded-multi-dossier-provider-robustness.md)
- [ADR-SS-003](../../../ADR-SS-003-optional-integration-topology.md)

## 12. Bloc de revue propriétaire attendu

La qualification locale rend le lot recevable à une revue propriétaire ; elle ne le valide pas et
n’autorise pas son déplacement vers `completed`. Le bloc attendu devra identifier le commit exact
qualifié communiqué après le commit documentaire final :

```text
J9_WO032_OWNER_REVIEW_DECISION=<VALIDATE|REJECT>
J9_WO032_WORK_ORDER=WO-SS-20260901-032-reference-index-refresh
J9_WO032_DOCUMENTATION_COMMIT=<qualified commit>
J9_WO032_REFERENCE_INDEX_SHA256=959adb8d120386ede0a0fbd2b46a2883b57a04582d5b21134d62107b14342c91
J9_WO032_REFERENCE_PDF_SHA256=746106cfe5142ad7b7426443b9730b22f281d6d844d108a5c371c7e08c9f1fee
J9_WO032_LOCAL_READINESS_ACKNOWLEDGED=<YES|NO>
J9_WO032_WORK_ORDER_MOVE_TO_COMPLETED=<YES|NO>
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REAL_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```
