# WO-049 — Qualification hors ligne du lanceur et du justificatif d'arrêt

- Work Order : `WO-SS-20260905-049-j9-wo046-launcher-stop-proof`.
- Branche : `codex/ss-20260905-049-j9-wo046-launcher-stop-proof`.
- Base : `a4dabd8023e957bee23c84c00fba0088146a5d49`.
- Ouverture documentaire : `abd5e9be541a8f1d30ea0426e674477959ea3893`.
- Implémentation : `22130d16781cce41a370cbcdbf6cd1b7b91546c2`.
- Résultat du correctif : `PASS_OFFLINE_FAIL_CLOSED`.
- État : `READY_FOR_OWNER_REVIEW`, avec déviation d'exécution à reconnaître explicitement.
- Ce résultat ne vaut ni lancement réel qualifié, ni validation propriétaire, ni reprise WO-046.

## 1. Correction et portée

Le propriétaire autorise une correction et une qualification hors ligne distinctes après l'arrêt
WO-046 R1. Le worktree neuf `.tmp/w49` part de l'état propre R1. Le numéro 049 et la branche sont
vérifiés disponibles ; les instances Eclipse préexistantes ne travaillent pas sur ce nouveau
worktree. Ni les scripts ad hoc R1 ni les preuves historiques ne sont réécrits.

Le module [WO046-LaunchPreparation.psm1](../../scripts/wo046/WO046-LaunchPreparation.psm1)
fournit cinq fonctions sans lancement, écriture de fichier, accès SQL, socket ou magasin PKI :

- génération du mapping complet d'environnement, avec les noms séparés attendus par Spring ;
- refus des clés manquantes, inconnues, mal nommées ou altérées et des références incomplètes ;
- préparation de `ProcessStartInfo.ArgumentList` : marqueur, trois gardes JDK, `-jar`, chemin
  unique ; aucun shell, aucune mutation de l'environnement parent, aucun `Process.Start` ;
- sérialisation du justificatif technique par clé/valeur/LF, sans concaténation ambiguë dans
  les éléments d'un tableau PowerShell ;
- relecture stricte et corrélée des octets, puis taille et SHA-256 reproductibles.

La règle EOL du seul nouveau module est fixée à LF dans `.gitattributes`. Les deux nouveaux
scripts `.ps1` respectent CRLF ; les documents et le test Java sont en LF. Tous sont UTF-8 sans
BOM. Aucun fichier Java applicatif, POM, endpoint, protocole, migration, ADR ou PDF n'est changé.

La [documentation d'usage](../../scripts/wo046/README.md) distingue explicitement une préparation
syntaxiquement valide d'une autorisation réelle. Les données privées retournées doivent rester
en mémoire. L'appelant futur devra encore prouver l'autorité, les hashes des JAR, les identités,
la fenêtre et le grant, puis assurer le lancement, la readiness et le cleanup sous nouveau go.

## 2. Mapping et refus effectivement qualifiés

Le script de fixture sans entrée privée construit le mapping avec des valeurs exclusivement
synthétiques. Le test Java lit sa sortie dans un processus PowerShell 7 borné à 30 secondes,
puis utilise `SystemEnvironmentPropertySource`, le vrai `application.yml`, le Binder Spring et
les contraintes de `OptionalLocalPushProperties`. Aucun contexte applicatif n'est démarré par
les sept tests WO-049.

| Contrôle | Résultat |
|---|---|
| Noms R1 reproduits avec valeurs synthétiques | Sender activé mais mode DISABLED, références absentes, matrice refusée |
| Mapping corrigé produit par PowerShell | Mode PROVIDER_DERIVED, qualifications PASS, origine/certificat/go exacts, contraintes Java satisfaites |
| Suppression de chaque clé attendue | Refus du mapping PowerShell |
| Alias concaténés, casing, clé supplémentaire | Refus |
| Provider activé, adresse publique, timeout >10 s, retry ou mTLS désactivé | Refus |
| UUID/hash/identifiant invalide, injection de contrôle, chemin relatif/ambigu | Refus |
| Arguments et environnement du `ProcessStartInfo` | Six arguments distincts, JAR avec espaces conservé, environnement parent inchangé |
| Propriété officielle NOT_EVIDENCED / EVIDENCED_COMPATIBLE | Comportement local v0.2 inchangé |
| EVIDENCED_INCOMPATIBLE | Toujours bloquant |
| Provider J3/J4/J5 et Playwright, refresh/polling | Désactivés |

La liste d'environnement hôte est fermée, avec `SystemRoot` obligatoire. Aucune variable JVM,
Spring ou credential ne peut être héritée par un override hôte non prévu. Les trois gardes
JDK restent distinctes ; les timeouts préparés sont 5/10 secondes. La cible reste exactement
`https://127.0.0.1:8444`, le listener Local Lab `127.0.0.1:8087` et PostgreSQL primaire `5432`.

Cette preuve porte sur la préparation `ArgumentList` et le binding. Elle ne revendique pas un
nouveau test de démarrage natif de l'application, un handshake ou une preuve de réception J7.

## 3. Justificatif technique canonique

Le format `WO046_FAIL_CLOSED_TECHNICAL_STOP_V1` contient dix champs dans un ordre fixe, chacun
sur une ligne, et un LF final. Les valeurs `GO_ID` et `STOPPED_AT_UTC` ne sont plus séparées de
leur clé. L'instant doit être UTC et représentable exactement à la microseconde.

Le golden synthétique de 476 octets est comparé indépendamment à des octets attendus et au
SHA-256 Java :

```text
SYNTHETIC_TECHNICAL_STOP_BYTES=476
SYNTHETIC_TECHNICAL_STOP_SHA256=058022ed74ef13e2c9c51f69bd7b87bec612d33baf5f06cf5e8756a8e810d2c1
POWERSHELL_JAVA_GOLDEN_BYTE_EQUALITY=PASS
FRENCH_TURKISH_CULTURE_STABILITY=PASS
```

Le parseur refuse BOM, CRLF, LF manquant ou supplémentaire, NUL, UTF-8 invalide, entrée supérieure
à 4096 octets, suppression/duplication/réordonnancement d'un champ, clé inconnue, valeur vide,
références divergentes, timestamp non UTC ou sous-microseconde. Le défaut R1 sur lignes séparées
est reproduit et refusé. La raison et les constantes restent limitées au refus de configuration
avant import ; l'appelant doit corroborer cette situation avant tout usage réel du justificatif.

Ce golden n'est ni un nouveau owner-go, ni un manifeste, ni une révocation persistée. Aucun
fichier privé ou justificatif R1 réel n'est remplacé par le test.

## 4. Commandes et résultats

Environnement qualifié : Windows, Java 25.0.4, Spring Boot 4.1.0, PowerShell 7.6.5, Pester 3.4.0.
Le cache Maven local existant est explicitement sélectionné par `-Dmaven.repo.local` ; aucun
téléchargement n'est effectué. Son chemin privé n'est pas nécessaire à la preuve publiée.

### 4.1 Pester ciblé

```text
Invoke-Pester -Script scripts/Tests/WO049LaunchPreparation.Tests.ps1 -PassThru
PASSES=20
FAILURES=0
SKIPPED=0
FINAL_DURATION_SECONDS=1.65
```

Les premiers tests ont exposé deux défauts du harness : une assertion `Should Throw` avec une
variable de boucle ambiguë, et une construction de tableaux de bytes doublement imbriquée.
Ils sont corrigés en capture explicite de refus et `List[byte[]]`. Les tests corrigés passent
sur plusieurs exécutions. L'export NUnit Pester sous sandbox a signalé un refus CIM de lecture
de métadonnées OS ; le fichier XML est néanmoins bien formé et contient 20 tests, zéro erreur
et zéro échec. Ce signal ne constitue pas une qualification des métadonnées OS manquantes.

### 4.2 Maven borné, exécution finale

```text
mvnw.cmd clean verify --offline
  -Dmaven.repo.local=<cache local existant>
  -DskipITs=true
  -Dsurefire.excludes=**/J6NativeBinaryPipelineQualificationTest.java
  -P!integration-tests,!provider-playwright-local-qualification,!j7-browser-origin-loopback-qualification,!provider-playwright-runtime
RESULT=BUILD_SUCCESS
SUREFIRE_TESTS=1185
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=4
WO049_JAVA_TESTS=7
WO049_JAVA_FAILURES=0
WO049_JAVA_ERRORS=0
WO049_JAVA_SKIPPED=0
FAILSAFE_INTEGRATION_TEST_AND_VERIFY=SKIPPED
FINAL_BUILD_DURATION_SECONDS=51.855
FINAL_BUILD_FINISHED_AT_UTC=2026-09-05T00:53:32Z
FINAL_BUILD_FINISHED_AT_EUROPE_PARIS=2026-09-05T02:53:32+02:00
```

Une première exécution correctement bornée avait déjà réussi en 53.985 secondes avec les mêmes
compteurs ; l'exécution finale suit la normalisation CRLF des scripts. L'exclusion J6 évite de
rejouer sa qualification native, étrangère au correctif. Il s'agit donc d'un `clean verify`
explicitement borné, pas d'un PASS d'intégration. Le JAR construit dans w49 n'a pas été lancé
ou substitué à celui de la campagne WO-046.

## 5. Déviation d'exécution à reconnaître

Une première commande a échoué avant Maven parce que `-o` est ambigu pour le wrapper PowerShell.
Le passage à `--offline` a ensuite échoué faute de sélection du cache, puis la compilation sous
sandbox a refusé un JAR Spring du cache. La commande hôte utilisant le cache existant a démarré
le build, **sans `skipITs=true`**. Cette omission de Codex a permis à Failsafe d'exécuter des
tests PostgreSQL/Testcontainers non autorisés par le périmètre hors ligne.

La cause est vérifiée dans le POM parent `spring-boot-starter-parent:4.1.0` : son plugin Failsafe
déclare les goals `integration-test` et `verify`. Le plugin étant déclaré dans le POM enfant,
ces exécutions existent même sans `-Pintegration-tests`. Il ne s'agit pas d'une autorisation
propriétaire ou d'une activation réseau déduite du présent Work Order.

Les observations avant interruption montrent :

- le test natif J6 hors ligne avait terminé en 69.45 secondes, 4 tests dont un ignoré ;
- `FlywayMigrationIT` avait terminé 69 tests sur PostgreSQL Testcontainers isolé ;
- `J7DeliveryLedgerMigrationIT` avait démarré son conteneur isolé et ses migrations, sans
  rapport de fin constaté avant l'interruption ; aucun résultat global de cette série n'est
  revendiqué ;
- les adresses JDBC observées étaient celles des conteneurs de test à ports éphémères, pas la
  base primaire sur 5432 ; ces tests utilisent des fixtures, pas l'export J7 réel sélectionné.

Le build a été interrompu dès observation de la déviation, les rapports partiels conservés dans
le répertoire de travail ignoré, et la disparition des processus/ressources de test contrôlée.
La sélection finale avec `skipITs=true` et profils désactivés a été appliquée sans modifier le
POM, le wrapper, les tests historiques ou les autorisations. Les tests PostgreSQL involontaires
ne sont pas utilisés pour augmenter la portée de qualification de WO-049.

```text
EXECUTION_DEVIATION=UNEXPECTED_ISOLATED_TESTCONTAINERS_EXECUTION
EXECUTION_DEVIATION_OWNER_ACKNOWLEDGEMENT=REQUIRED
INTERRUPTED_INTEGRATION_SERIES_RESULT=NOT_CLAIMED
```

## 6. Postcontrôle et conservation

Le contrôle hôte final à `2026-09-05T00:56:13.1279342Z` constate :

```text
TESTCONTAINERS_CONTAINER_COUNT=0
WO049_JAVA_PROCESS_COUNT=0
CAMPAIGN_LISTENERS_8087_8444_5433=0
PRIMARY_CONTAINER_METADATA=RUNNING_HEALTHY
POSTFLIGHT_SQL_QUERIES=0
```

La santé primaire est lue uniquement via les métadonnées Docker du conteneur exact ; aucun
recontrôle SQL des données primaires n'est effectué sous WO-049. Aucun receiver WO-046 ou
Local Lab de campagne n'est lancé, aucun export réel transmis, aucun nouveau manifeste ou
go WO-046 créé. L'incident Testcontainers de la section 5 interdit de prétendre à « zéro base
démarrée pendant tout le tour » ; le postcontrôle zéro résidu et la qualification finale hors
ligne doivent être distingués de cet écart.

Le worktree WO-046 reste propre à `a4dabd8023e957bee23c84c00fba0088146a5d49`. Son rapport R1 et
son manifeste sont byte-identiques, avec les SHA-256 respectifs
`819572974a6f43c28da4e4191766932c46c43102895ad58e3e436d4b90a783bd` et
`6590603286c6c422588bbc3f6a46f502716fa2e2df18b6ad0df741cbd1cbf277`.
Les scripts historiques R1, son grant et son justificatif privé n'ont pas été modifiés.

## 7. Empreintes qualifiées

Les tailles/hashes des scripts sont ceux des fichiers de travail conformes à `.gitattributes`
(CRLF pour `.ps1`, LF pour le module et Java). Les rapports de test bruts restent ignorés et
ne sont pas ajoutés à Git ; seuls leurs engagements sont publiés.

| Artefact | Octets | SHA-256 |
|---|---:|---|
| `scripts/wo046/WO046-LaunchPreparation.psm1` | 12841 | `731565492e0f17a61b918019083ecbb7231a8ff64c7c2f807572b46196600bd6` |
| `scripts/wo049/Export-WO049SyntheticLaunchPreparation.ps1` | 1901 | `a8cf1644cf4b58f67306fa4840c4bc97467f2c1c1941fd7b0bdecc233e0b9213` |
| `scripts/Tests/WO049LaunchPreparation.Tests.ps1` | 17380 | `f62850ce5915b58281b3f83f0b98e70129048c82b1d6de528b47d16a1542da89` |
| `Wo049LauncherOfflineQualificationTest.java` | 12640 | `e9e33615bfa163b0e471ea57aa3653f9dfccc850958fc66108404cc7b84d051a` |
| Rapport texte Surefire WO-049 | 410 | `22bfc3fffed61927721f71575f202332acfc37a7c81ec25374bc9024c5df35aa` |
| Rapport XML Pester | 7309 | `4b46d1ecee586828354e1d1c8a27aabb43020a4d2b1a04af1f57768c4865ad65` |
| Journal Maven final borné | 82349 | `977cb127258e29da26442a21275d195c0d3c35c9ae47e4e48be1d305b78fc57c` |
| Rapport Flyway du build interrompu | 381 | `2491a0e26365c0668f644e2cead63e100067cc50c9b14111e651d092d223a9e5` |

Les contrôles de diff, UTF-8 et contenu synthétique passent. Aucun secret réel, empreinte privée
de certificat, donnée J7 réelle ou chemin privé n'est ajouté aux nouveaux outils et fixtures.
Les valeurs et UUID des tests sont volontairement artificiels, pas des données de campagne.

## 8. Revue et reprise séparées

WO-049 reste actif jusqu'à validation propriétaire et reconnaissance de la déviation. Aucun
push, fusion ou déplacement vers les Work Orders terminés n'est effectué par cette qualification.
Après validation, l'adoption de la préparation versionnée par un lanceur successeur WO-046,
les recontrôles frais, le sort explicite du volume receiver conservé, le manifeste successeur
gelé et le nouveau go restent soumis à leurs décisions distinctes.

```text
QUALIFICATION_RESULT=PASS_OFFLINE_FAIL_CLOSED
OWNER_REVIEW_REQUIRED=YES
EXECUTION_DEVIATION_ACKNOWLEDGEMENT_REQUIRED=YES
WO049_MOVE_TO_COMPLETED=NO
WO046_RESUME_AUTHORIZED=NO
WO046_NEW_MANIFEST_AUTHORIZED=NO
WO046_NEW_OWNER_GO_GRANTED=NO
WO046_REAL_POST_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```
