# WO-SS-20260905-051 — Refus mTLS distant et assertion CI portable

- Statut : `IMPLEMENTED_PENDING_OWNER_REVIEW`.
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
