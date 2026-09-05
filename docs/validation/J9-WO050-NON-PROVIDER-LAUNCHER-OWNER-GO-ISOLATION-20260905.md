# J9 — WO-050 : qualification hors ligne de l'isolation owner-go des lanceurs

## Résultat et identité

```text
WORK_ORDER=WO-SS-20260905-050-j9-non-provider-launcher-owner-go-isolation
RESULT=PASS_OFFLINE_FAIL_CLOSED
OWNER_REVIEW=PENDING
BASE_COMMIT=facb4b51f23b09b07e46653ca8a40dac6f543f59
OPENING_COMMIT=30b8cfd89e010af9d5e5a917ecfc907fdf9d5224
IMPLEMENTATION_COMMIT=87deb475007c853ed5f06752756183078f2e404f
BRANCH=codex/wo-050-non-provider-launcher-owner-go-isolation
PR27_UPDATE=NOT_PERFORMED_PENDING_OWNER_VALIDATION
```

Qualification du 2026-09-05, Windows, PowerShell 7, Java 25.0.4, Maven wrapper et Spring Boot
4.1.0. Aucun appel GitHub n'est nécessaire à cette qualification. Les résultats CI de la
PR #27 concernent son ancien HEAD, pas ce correctif encore exclusivement local.

## Défaut et correction

La P2 relève l'héritage des variables
`OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID` et
`OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256` malgré un mode DISABLED.
La validation Java refuse à juste titre cette référence résiduelle ; le correctif ne change
pas cette garde. Les deux noms rejoignent les listes de sauvegarde existantes et leurs
valeurs sont neutralisées pendant le parcours. Les finally existants restaurent ensuite
l'environnement initial, même si l'appel simulé lève une exception.

Six fichiers de lancement sont modifiés :

- `scripts/Start-Local.ps1` ;
- `scripts/Start-J3PlaywrightLocal.ps1` ;
- `scripts/Start-J4PlaywrightLocal.ps1` ;
- `scripts/Start-J5PlaywrightLocal.ps1` ;
- `scripts/Export-J8Benchmark.ps1` ;
- `scripts/Invoke-J6Retention.ps1`.

Les flags fournisseur des parcours J3/J4/J5 ne sont pas modifiés : « sans livraison
fournisseur » désigne ici l'intégration J7 désactivée, pas une suppression du parcours
Playwright manuel existant. Aucun de ces parcours métier n'est lancé pendant la qualification.

## Preuve comportementale PowerShell

`scripts/wo050/Test-WO050LauncherIsolation.ps1` lit l'AST des scripts versionnés et extrait
leurs sections originales de sauvegarde, neutralisation et restauration d'environnement.
Il remplace seulement les appels métier Maven et Start-Local par des doublures déterministes.
Les commandes de localisation et affichage sont simulées. Il n'exécute ni le préflight,
ni Docker, ni Maven métier, ni les vérifications du cache Playwright des vrais lanceurs.

Le parcours J6 est Preview uniquement ; la branche Execute est remplacée par un refus
explicite dans la copie de test en mémoire. Il ne lit donc pas de manifeste privé, de
sauvegarde ou de configuration locale. Les commandes inattendues dans la section extraite
sont refusées. Le test ne modifie pas les fichiers des lanceurs pour les exécuter.

| Matrice | Valeurs |
|---|---|
| Lanceurs | Les six scripts ci-dessus |
| Héritage synthétique | Aucun, GO seul, hash seul, paire complète |
| Issue simulée | Succès, code natif 19, exception injectée |
| Appel métier réel | Aucun |
| Nombre de scénarios | 6 x 4 x 3 = 72 |

Avant le correctif, le même harness a échoué de manière attendue sur
`WO050_AMBIENT_GO_LEAK:Start-Local.ps1:1`, code 1 : le GO hérité subsistait.
Après le correctif, les 72 cas passent, douze pour chaque lanceur. Chaque cas observe les
valeurs au moment de l'appel simulé, vérifie DISABLED/false/false, l'absence des deux références,
et la restauration exacte de leur état initial. Aucun contenu privé n'est produit.

Limite explicite : cette preuve qualifie l'isolation de l'environnement dans les scopes
originaux, pas le préflight complet, une exécution métier de rétention, Docker ou une campagne.

## Binding Java et non-régression

`Wo050LauncherOwnerGoIsolationTest` couvre :

1. La reproduction Java, via Binder Spring et Bean Validation réels, des quatre états hérités :
   le mode désactivé n'accepte toujours que l'absence des références.
2. La présence des sauvegardes et neutralisations dans les six scripts, test portable.
3. Sur Windows, l'exécution bornée du harness PowerShell (30 secondes, sortie au plus
   128 Kio), puis la validation Java des 72 environnements observés, sans application/socket.

Les sept tests WO-049 vérifient en outre que le lanceur dédié PROVIDER_DERIVED, sa matrice
complète et ses refus restent qualifiés. Les neuf tests OptionalLocalPushProperties passent.
Au total : **19 tests ciblés, zéro échec, zéro erreur, zéro ignoré**.

Commande ciblée, JAVA_HOME positionné pour ce processus sur Java 25.0.4 :

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-Dtest=Wo050LauncherOwnerGoIsolationTest,Wo049LauncherOfflineQualificationTest,OptionalLocalPushPropertiesTest' test
```

Fin de l'exécution ciblée réussie : `2026-09-05T10:18:47Z` (12:18:47 Europe/Paris), 21,491 s.
La première tentative sandbox a échoué à la compilation sur l'accès au JAR spring-orm du
cache Maven. Elle n'est pas comptée comme une qualification réussie. La relance hôte avec
le même cache, toujours `--offline`, a réussi ; aucun téléchargement ni service métier.

## Vérification élargie bornée

```powershell
.\mvnw.cmd clean verify --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' '-DskipITs=true' '-Dsurefire.excludes=**/J6NativeBinaryPipelineQualificationTest.java,**/ChildJvmPlaywrightProviderSupervisorTest.java' '-P!integration-tests,!provider-playwright-local-qualification,!j7-browser-origin-loopback-qualification,!provider-playwright-runtime'
```

**BUILD SUCCESS — 1148 tests, 0 échec, 0 erreur, 4 ignorés** ; durée affichée 01:10 min,
fin `2026-09-05T10:20:34Z` (12:20:34 Europe/Paris). Les tests de contrôleurs utilisent le
contexte servlet simulé de Spring, pas un listener de l'application.

Le build exclut explicitement le pipeline natif J6 et la suite superviseur JVM/sockets.
Failsafe affiche « Tests are skipped » pour integration-test et verify. Les profils
Playwright et navigateur de qualification sont désactivés. Cela n'est pas un PASS de
l'ensemble des tests natifs/loopback/PostgreSQL du dépôt, hors autorité de ce WO.
La compilation des sources IT n'est pas leur exécution. Aucun schéma/persistance ne change.

## Contrôles et intégrité

- `ci/assert-local-only.sh` : `LOCAL_ONLY_POLICY=PASS` et flags réseau/VPS/production NO.
- `ci/check-branch-name.sh codex/wo-050-non-provider-launcher-owner-go-isolation` : PASS.
- `git diff --check` et contrôle indexé : PASS.
- Texte UTF-8 strict, sans BOM ni NUL dans les fichiers modifiés : PASS.
- Neuf expressions exactes de `ci/check-no-secrets.sh` appliquées par Git grep au lot indexé :
  aucune détection. Ce contrôle porte sur le lot WO-050, pas sur une nouvelle revue de tout
  l'historique. Aucune donnée privée n'a été lue pour construire les fixtures.
- Aucun changement sous `src/main`, `docs/reference`, ni des outils WO-049 et preuves gelées.
- Rapport R2 WO-046 inchangé : SHA-256
  `3b128cab84c384f5901215042d554e935730ce146565687677ddb1c2a39eddf1`.

## État de sortie et revue demandée

```text
WO050_QUALIFICATION=PASS_OFFLINE_FAIL_CLOSED
WO050_OWNER_REVIEW=REQUIRED
WO050_MOVE_TO_COMPLETED=NOT_AUTHORIZED_YET
PUSH_OR_PR27_UPDATE=NOT_PERFORMED
PR27_DISCUSSION_RESOLUTION=NOT_PERFORMED
PR27_MERGE=NO
APPLICATION_DATABASE_PLAYWRIGHT_RECEIVER_START=NO
PROVIDER_OR_RECEIVER_HTTP=NO
PRIVATE_CONFIGURATION_OR_CERTIFICATE_ACCESS=NO
WO046_NEW_MANIFEST_OR_GO_OR_POST=NO
WO046_OWNER_GO=CONSUMED_ONCE_NOT_REUSABLE
OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REMOTE_RECEIVER_VPS_PRODUCTION=NO
```

Le propriétaire doit valider ce correctif avant toute mise à jour de la PR #27. WO-050 reste
actif jusqu'à sa décision ; aucune résolution GitHub n'est anticipée à partir du PASS local.
