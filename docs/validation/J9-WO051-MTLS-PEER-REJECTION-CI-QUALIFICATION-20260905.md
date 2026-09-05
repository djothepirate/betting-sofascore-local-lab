# J9 — WO-051 : qualification du correctif d'assertion mTLS post-fusion

```text
WORK_ORDER=WO-SS-20260905-051-j9-mtls-peer-rejection-ci
RESULT=PASS_LOCAL_SYNTHETIC_MTLS_FAIL_CLOSED
OWNER_REVIEW=PENDING
BASE_COMMIT=49d4308794411d41ece97cbd22fdce7fef377616
OPENING_COMMIT=f67caf4d951bc4bd71466f8156e053653d2dcd8c
IMPLEMENTATION_COMMIT=992423055091276914555104bb299ef3ce76d2c9
BRANCH=codex/wo-051-mtls-peer-rejection-ci
```

## Échec observé et cause

[Run 33961252984](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/33961252984),
[job Linux 101293407768](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/33961252984/job/101293407768),
événement push sur le commit de fusion ci-dessus. Windows SUCCESS, Linux FAILURE.

Failsafe : 106 tests d'intégration, 1 échec, 0 erreur ; l'unique assertion en échec est
`J7DeliveryMutualTlsLoopbackIT.rejectsASignedClientIdentityNotApprovedByTheServer`,
ancien helper ligne 283 : TLS_FAILURE attendu, IO_FAILURE observé.
La classe mTLS totalisait 4 tests dont 1 échec. Fin Maven historique : 2026-09-05T10:44:19Z.

Le test exigeait une classification TLS précise pour un refus d'identité par le serveur.
Le classificateur runtime retourne TLS_FAILURE lorsqu'une SSLException figure dans les
causes, sinon IO_FAILURE (hors timeout). L'erreur était donc une hypothèse trop stricte
du test sur ce que le client HTTP peut observer lors du rejet par le pair.

Limite du diagnostic : le log expurgé ne fournit pas la chaîne native complète. Une fermeture
du pair remontée comme IOException sans SSLException est compatible avec ce résultat, mais
la séquence native exacte (alerte TLS, fermeture/reset, ordonnancement) n'est pas démontrée.
On ne présente pas une course native précise ni une régression de sécurité comme établie.
La classe IT n'a pas changé entre 400900410dfa751521ce387fbadcc4b5ca95a94a et le commit de
fusion ; sa dernière modification antérieure est f5a2788. Le défaut n'est pas attribué
au correctif d'environnement WO-050.

## Correctif limité aux tests

1. Confiance serveur et SAN contrôlés localement par le client : TLS_FAILURE reste obligatoire.
2. Identité cliente absente ou non approuvée par le serveur : seuls TLS_FAILURE et IO_FAILURE
   sont acceptés comme échec. Timeout, interruption, réponse/ACK positif ou autre classification
   ne sont pas acceptés.
3. Pour l'identité non approuvée, un import synthétique authentifié est d'abord effectué sur
   le même listener et doit retourner 201 avec un effet. L'essai refusé doit ensuite conserver
   les compteurs de requêtes et d'effets inchangés. Le test sans identité dispose déjà de ses
   contrôles positifs préalables 201/200/409.
4. Le helper compare les compteurs à leur valeur préalable (zéro pour les refus de confiance
   locale, un pour le test avec contrôle positif) ; absence de nouvelle requête applicative,
   message limité au nom de classification et fermeture transport restent vérifiés.
5. Test unitaire supplémentaire : texte « TLS » dans IOException et SocketException imbriquée
   sans preuve SSL restent IO_FAILURE ; SSLHandshakeException imbriquée reste TLS_FAILURE.
   Le helper existant vérifie une seule invocation sendAsync, sans retry.

Fichiers de test modifiés :

- `src/test/java/com/bettingproject/sofascorelocal/integration/J7DeliveryMutualTlsLoopbackIT.java` ;
- `src/test/java/com/bettingproject/sofascorelocal/adapter/bettingproject/transport/BettingProjectJ7DeliveryHttpTransportTest.java`.

README, changelog et Work Order sont actualisés. Aucun fichier src/main, workflow CI, pom,
ADR, migration ou PDF n'est modifié. needClientAuth, TLS, délais, absence de retry et loopback
restent inchangés. Ce correctif ne supprime pas le test et ne convertit pas des erreurs I/O
en TLS_FAILURE dans le runtime.

## Qualification locale

Windows, Java 25.0.4, Maven wrapper hors ligne, Spring Boot 4.1.0.
Cache Maven hôte existant, aucune dépendance téléchargée.

### Lot ciblé

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Dtest=BettingProjectJ7DeliveryHttpTransportTest,J7DeliveryMutualTlsLoopbackIT' test
```

21 tests réussis : 17 unitaires transport, 4 mTLS, aucun échec/erreur/ignoré.
46,921 secondes ; fin 2026-09-05T10:54:32Z. Un avertissement de typage varargs dans
l'assertion a ensuite été éliminé avec Arrays.asList, puis requalifié dans le build final.

### Build final et Failsafe ciblé

```powershell
.\mvnw.cmd clean verify --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Pintegration-tests' '-Dit.test=J7DeliveryMutualTlsLoopbackIT' '-Dsurefire.excludes=**/J6NativeBinaryPipelineQualificationTest.java,**/ChildJvmPlaywrightProviderSupervisorTest.java'
```

- Surefire : 1149 tests, 0 échec, 0 erreur, 4 ignorés.
- Failsafe : seulement J7DeliveryMutualTlsLoopbackIT, 4 tests, 0 échec, 0 erreur, 0 ignoré,
  durée 34,88 secondes.
- BUILD SUCCESS, durée affichée 01:55 min, fin 2026-09-05T10:57:32Z (12:57:32 Europe/Paris).
- Les suites pipeline natif J6 et superviseur JVM/sockets sont exclues ; les IT PostgreSQL
  ne sont pas exécutés. Ce résultat n'est pas un PASS de l'ensemble des 106 IT Linux.

Le test mTLS génère ses propres clés PKCS12 temporaires, ouvre un listener HTTPS sur
127.0.0.1 avec port éphémère et envoie uniquement les octets synthétiques préexistants.
Ce serveur de fixture n'est pas le receiver Betting Project. Aucun magasin de certificats
utilisateur, base primaire, conteneur ou export réel n'est utilisé. Les assertions afterEach
arrêtent le serveur, attendent son executor et suppriment le sous-arbre TLS temporaire exact.
Les quatre tests ont passé ces contrôles de cleanup lors des deux lots.

## Contrôles et limites

- Politique locale et convention de branche : PASS.
- git diff --check, UTF-8 sans BOM/NUL : PASS.
- Les neuf regex du scanner CI appliquées par Git grep au lot indexé : aucune détection.
- Rapport qualifié WO-050 inchangé, SHA-256
  c6b1acd4f0b3866fe6db1b7fce6c68986c6007844a63a348c4f8dcacca5a7ccd.
- Aucun run Linux nouveau, aucune correction déjà publiée et aucun check GitHub vert nouveau
  ne sont revendiqués. La CI Linux doit requalifier la branche après publication autorisée.

```text
PRODUCTION_CODE_CHANGE=NO
MTLS_RELAXATION=NO
AUTOMATIC_RETRY=NO
PROVIDER_NETWORK=NO
PROVIDER_DERIVED_PAYLOAD=NO
REAL_RECEIVER=NO
PRIMARY_DATABASE_ACCESS=NO
VPS_PRODUCTION=NO
PUSH_PR_MERGE=NOT_PERFORMED
WORK_ORDER_CLOSURE=REQUIRES_OWNER_VALIDATION
```
