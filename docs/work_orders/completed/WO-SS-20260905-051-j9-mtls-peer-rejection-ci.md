# WO-SS-20260905-051 — Refus mTLS distant et assertion CI portable

- Statut : `COMPLETED_OWNER_VALIDATED`.
- Base : `49d4308794411d41ece97cbd22fdce7fef377616`.
- Branche : `codex/wo-051-mtls-peer-rejection-ci` ; worktree neuf `.tmp/w51`.
- Autorité : demande propriétaire de diagnostiquer le job Linux 101293407768 du run
  33961252984 et de réaliser un correctif après fusion de la PR #27.

## Périmètre

Diagnostic et correction du test mTLS synthétique, tests unitaires de classification et
qualification locale synthétique 127.0.0.1 seulement. Aucun receiver réel, export fournisseur,
base primaire, certificat utilisateur, appel SofaScore, déploiement ou production.
Pas de push, PR ou fusion sans instruction distincte. WO-050 et rapports qualifiés gelés.

## Constat

Le run main a 106 tests d'intégration, 1 échec, 0 erreur. L'assertion du test
`J7DeliveryMutualTlsLoopbackIT.rejectsASignedClientIdentityNotApprovedByTheServer`
attend TLS_FAILURE mais observe IO_FAILURE. Le classificateur runtime n'émet TLS_FAILURE
que s'il trouve SSLException dans les causes ; aucune heuristique textuelle ne doit remplacer
cette preuve. Le log ne donne pas la chaîne native et ne prouve pas son détail exact.

## Correction et critères

- Distinguer validation du serveur par le client (TLS_FAILURE strict) et refus de l'identité
  cliente par le pair (TLS_FAILURE ou IO_FAILURE, jamais succès, timeout ou interruption).
- Pour le refus client, prouver préalablement que le même serveur fonctionne avec l'identité
  autorisée ; comparer ensuite compteurs de requêtes/effets inchangés et fermeture transport.
- Garder le mode needClientAuth, le loopback, les délais, absence de retry et le runtime intacts.
- Tests unitaires déterministes pour SSLException imbriquée et IOException sans preuve TLS.
- Tests mTLS avec clés éphémères synthétiques et nettoyage existant, sans Docker nécessaire.
- Build hors ligne avec exclusions explicites des autres suites natives/infrastructure ;
  aucun PASS Linux ou PostgreSQL inféré d'une qualification Windows ciblée.
- Rapport, UTF-8, secrets, diff, commits locaux et revue propriétaire avant clôture.

## Résultat local

- Aucun changement runtime, workflow CI, pom, protocole ou migration.
- 21 tests ciblés réussis (17 unitaires transport, 4 mTLS synthétiques).
- Build clean verify hors ligne borné : 1149 tests standards, 0 échec/erreur, 4 ignorés ;
  Failsafe limité à J7DeliveryMutualTlsLoopbackIT : 4 tests, 0 échec/erreur/ignoré.
- Fin 2026-09-05T10:57:32Z, BUILD SUCCESS, durée affichée 01:55 min.
- Politique locale, branche, UTF-8, diff et neuf règles de secrets : PASS.
- Pas de qualification Linux distante revendiquée ; le run GitHub historique reste en échec.
- Correctif local prêt à revue, aucun push, PR, fusion ou nouvelle campagne réelle.

Implémentation : `992423055091276914555104bb299ef3ce76d2c9`.
[Rapport de qualification](../../validation/J9-WO051-MTLS-PEER-REJECTION-CI-QUALIFICATION-20260905.md),
SHA-256 `83468174d27cb5339690fcd3e947e495a228aa603e82f69816be4cf2be83d41f`.
Résultat soumis : `PASS_LOCAL_SYNTHETIC_MTLS_FAIL_CLOSED`. Revue propriétaire requise.

## Validation propriétaire et clôture — 2026-09-05

Cette section remplace l'attente historique de revue ci-dessus. Le propriétaire valide
explicitement le correctif et autorise sa publication ainsi que la création d'une PR vers
main, sans autoriser la fusion. Le rapport qualifié et son SHA-256 restent inchangés.

```text
J9_WO051_OWNER_REVIEW_DECISION=VALIDATE
J9_WO051_WORK_ORDER=WO-SS-20260905-051-j9-mtls-peer-rejection-ci
J9_WO051_IMPLEMENTATION_COMMIT=992423055091276914555104bb299ef3ce76d2c9
J9_WO051_DOCUMENTATION_COMMIT=4daa7984d0c3f524d2b8af25b4c23a27524fee52
J9_WO051_QUALIFICATION_RESULT=PASS_LOCAL_SYNTHETIC_MTLS_FAIL_CLOSED
J9_WO051_QUALIFICATION_REPORT_SHA256=83468174d27cb5339690fcd3e947e495a228aa603e82f69816be4cf2be83d41f
J9_WO051_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO051_WORK_ORDER_MOVE_TO_COMPLETED=YES
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_WO046_NEW_POST_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Push de la branche WO-051 et PR vers main autorisés par instruction complémentaire.
Historique qualifié conservé, aucun squash/rebase, aucune nouvelle autorisation métier.
Les checks de la nouvelle PR doivent être lus sur son HEAD ; aucun PASS Linux n'est anticipé.

Vérification de clôture : clean verify hors ligne, skipITs=true et exclusions pipeline
natif J6/superviseur JVM-sockets ; 1149 tests, 0 échec/erreur, 4 ignorés, BUILD SUCCESS,
fin 2026-09-05T11:04:05Z, durée affichée 01:02 min. Aucun nouveau test réseau.
Politique locale, diff, UTF-8 et neuf règles de secrets sur le lot publié : PASS.
