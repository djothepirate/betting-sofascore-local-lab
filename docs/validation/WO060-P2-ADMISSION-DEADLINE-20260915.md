# WO-060 — Motif d’annulation à expiration de l’admission réseau

Date : 15 septembre 2026, Europe/Paris.

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Finding et correction

Le [P2 de la PR #36](https://github.com/djothepirate/betting-sofascore-local-lab/pull/36#discussion_r4012991874)
concerne `J3RuntimeService.reserveNetwork`. La base du correctif est
`0f9040f01cbf28b08747a40fb89adda038986928`, dont les quatre checks GitHub Windows/Linux
ont réussi avant cette nouvelle demande propriétaire.

L’expiration de la fenêtre avant réservation réseau était convertie en interruption générique.
Une exception dédiée transporte maintenant `ADMISSION_DEADLINE_EXPIRED` jusqu’au traitement
de l’ordre : terminal durable `CANCELLED`, motif conservé, aucune collection démarrée ni
ressource fournisseur ouverte. La même décision couvre le motif transmis par le watchdog.
Le délai et sa marge de 130 secondes restent inchangés. Les autres arrêts ou erreurs ne sont
pas reclassés comme expiration.

Trois cas ont été ajoutés à `J3RuntimeIT` sur PostgreSQL Testcontainers :

- ordre admis dans le passé avec 129 secondes restantes, refusé dès la réservation ;
- ordre avec 135 secondes restantes, coordinateur occupé, plusieurs tentatives de réservation
  locale puis annulation à la limite ;
- erreur technique à la réservation, conservée en `INTERRUPTED / EXECUTION_INTERRUPTED`.

Les deux premiers cas relisent le terminal par un nouveau store, vérifient le libellé
**Annulée**, le motif d’échéance, zéro appel, zéro ouverture de fournisseur, aucune collection
pour l’ordre annulé et le succès antérieur inchangé. Les ordres respectent les contraintes SQL
d’admission immuable ; aucun trigger n’est désactivé. Les horodatages d’admission sont fournis
au store dans le passé, puis la file et le consommateur ordinaires exécutent le scénario.

## Qualification

Environnement : Windows, Java 25.0.4, Maven Wrapper, Spring Boot 4.1.0, PostgreSQL 18.4-alpine
jetable. Aucun appel SofaScore ni navigateur fournisseur n’est lancé. La base opérateur reste
inchangée ; les migrations de test s’exécutent uniquement dans les bases jetables.

Commande ciblée avant puis après correction :

```powershell
.\mvnw.cmd -Pintegration-tests '-Dtest=J3AutomationDataTest' '-Dit.test=J3RuntimeIT' verify
```

- Avant correction : 6 tests Failsafe, 2 échecs attendus, 0 erreur/exclusion ; les deux cas
  d’expiration observent `INTERRUPTED` au lieu de `CANCELLED`.
- Après correction : BUILD SUCCESS, 3 tests Surefire et 6 Failsafe, zéro échec/erreur/exclusion,
  terminé le 15 septembre à 07:34:48 UTC en 1 min 1 s.
- Vérification complète `mvnw.cmd clean verify` : BUILD SUCCESS, terminé le 15 septembre
  à 07:51:59 UTC en 16 min 18 s. Les rapports XML confirment 2 440 tests Surefire dans
  238 suites (5 exclusions), 286 Failsafe dans 17 suites (0 exclusion), zéro échec/erreur.
  Les six cas de `J3RuntimeIT` passent aussi dans ce build complet.

Un premier lancement dans le bac à sable s’est arrêté avant les tests sur l’accès Maven
(`Permission denied: getsockopt`). Une première compilation du correctif a signalé une
collision de nom de variable avec une lambda ; elle a été corrigée avant les qualifications
ci-dessus. Ces tentatives ne sont pas comptées comme des tests réussis.

Empreintes SHA-256 des sources qualifiées et des journaux locaux :

| Élément | SHA-256 |
|---|---|
| `J3RuntimeService.java` | `6541e21a443baea843695e13219508f8c441f4470db2ea4569cd236675ed8b46` |
| `J3RuntimeIT.java` | `ab7510105d50acdf5df1c4257cdd1532599b4eadfc5eff4f903375b0c58acec8` |
| `.tmp/wo060-p2-red.log` | `508874495ffc7e990824398a045d3ef2a20cc3bd9fb92bdfbce0783ff5667104` |
| `.tmp/wo060-p2-targeted.log` | `e8620cab1efa2c20b67f2ceb738de610f7bc98ff3794f6fd53eb56234c96f33d` |
| `.tmp/wo060-p2-clean-verify.log` | `8aaf7ad6b6fd7de31721b47089a02e3d9394a2e98b248650bbfa475fb2d123a7` |

Les empreintes des deux fichiers Java ont été relues après le build : elles sont identiques
à celles de la qualification ciblée. Le fichier de configuration conserve
`server.address=127.0.0.1`. Les derniers ajustements du rapport portent uniquement sur les
résultats de validation.

## Fichiers modifiés

- `src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/J3RuntimeIT.java`
- `CHANGELOG.md`
- `docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md`
- `docs/work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md`
- ce rapport.

Le correctif est destiné à la même branche de PR, vers `feature/V0.1.0-RC01`. Le WO reste
actif jusqu’à fusion ; les checks du commit initial ne qualifient pas le nouveau candidat.
