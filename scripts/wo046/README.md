# Préparation du lanceur WO-046 — WO-049

`WO046-LaunchPreparation.psm1` remplace les deux fragments fautifs du lanceur ad hoc R1 par des
fonctions versionnées et qualifiables hors ligne. **Il ne démarre rien, n'écrit aucun fichier,
n'ouvre aucune connexion et n'enregistre ni grant ni révocation.** Les outils et justificatifs
historiques R1 ne doivent pas être modifiés ou rejoués pour adopter ce module.

## API de préparation

| Fonction | Entrée / résultat | Limite |
|---|---|---|
| `New-WO046LocalLabLaunchEnvironment` | Références déjà sélectionnées, paramètres DB/export ; mapping de chaînes complet | Ne prouve pas la validité réelle d'un go, d'une PKI ou d'une qualification |
| `Assert-WO046LocalLabLaunchEnvironment` | Mapping complet ; succès silencieux ou refus expurgé | Refuse clés manquantes/supplémentaires, aliases, types et valeurs non conformes |
| `New-WO046LocalLabStartInfo` | Chemins et instance exacts, mapping validé, environnement hôte explicitement allowlisté ; `ProcessStartInfo` | Ne fait jamais `Process.Start` ; aucun test de fichier, de hash ou de disponibilité d'interface |
| `New-WO046TechnicalStopBytes` | UUID de go existant, hash du bloc existant, instant UTC exact à la microseconde ; `byte[]` | Justificatif technique, pas un owner-go et pas une décision propriétaire |
| `Test-WO046TechnicalStopBytes` | Octets et références attendues ; taille, SHA-256 et indicateur canonique | Vérifie la syntaxe/corrélation, pas la révocation effective dans la base |

Les retours de préparation contiennent des données privées fournies par l'appelant (mot de passe
DB et référence de certificat). Les conserver uniquement en mémoire : ne pas les afficher,
sérialiser, journaliser, ajouter à Git ou envoyer dans une exception. Le module ne lit pas les
fichiers privés, le magasin Windows ou les variables du processus pour trouver ces valeurs.

Le mapping fixe le listener Local Lab `127.0.0.1:8087`, PostgreSQL `5432`, receiver exact
`https://127.0.0.1:8444`, fournisseur désactivé, mTLS Windows-MY obligatoire, concurrence et
protocole laissés aux constantes qualifiées, timeouts 5/10 secondes et retry désactivé. Les
qualifications PASS sont des paramètres attendus de la campagne future, pas des preuves créées
par le mapping. `NOT_EVIDENCED` et `EVIDENCED_COMPATIBLE` sont acceptés pour le seul transfert
local ; `EVIDENCED_INCOMPATIBLE` est refusé. Le nom historique de propriété
`REMOTE_DELIVERY_AUTHORIZED` n'élargit pas la cible, qui reste le loopback exact.

L'environnement hôte doit être fourni explicitement, avec `SystemRoot` obligatoire et seulement
les clés autorisées : `SystemRoot`, `WINDIR`, `TEMP`, `TMP`, `USERPROFILE`, `APPDATA`, `LOCALAPPDATA`,
`PATH`, `ComSpec`, `PATHEXT`, `PROCESSOR_ARCHITECTURE`, `NUMBER_OF_PROCESSORS`, `OS`. Les autres
variables ne sont pas héritées silencieusement. Les variables Spring, provider, proxy, TLS debug
et JVM sont définies par le mapping contrôlé. L'environnement parent n'est jamais modifié.

Les six arguments Java sont préparés via `ProcessStartInfo.ArgumentList`, sans concaténation
shell : marqueur d'instance, trois garde-fous JDK, `-jar`, puis chemin JAR unique non préquoté.
Les chemins relatifs, contenant guillemets/contrôles ou segments non canoniques sont refusés.
`UseShellExecute=false`, `CreateNoWindow=true`, stdout/stderr redirigés. Le futur appelant doit
drainer ces flux de manière bornée, sous confidentialité, et établir l'identité exacte du
processus avant toute action sur celui-ci. Ce module ne remplace pas ces responsabilités.

## Justificatif d'arrêt

Le format conserve `WO046_FAIL_CLOSED_TECHNICAL_STOP_V1` avec dix champs ordonnés, une ligne par
champ, UTF-8 sans BOM et LF final. Les valeurs ne sont pas concaténées dans les éléments d'un
tableau PowerShell : un `StringBuilder` ajoute séparément clé, `=`, valeur et LF.

L'instant doit avoir un offset UTC nul et une précision représentable sans perte en six décimales.
Le parseur refuse BOM, CRLF, contrôles, taille supérieure à 4096 octets, absence de LF final,
champ vide/inconnu/dupliqué, ordre différent, références divergentes, date invalide ou octets
non canoniques. Le résultat est reproduit exactement avant calcul du SHA-256.

La raison est volontairement limitée à `LOCAL_LAB_START_CONFIGURATION_BINDING_REFUSED`, comme
le justificatif R1 corrigé. Étendre les raisons ou affirmer un arrêt après consommation ne fait
pas partie de WO-049. Les constantes `PROVIDER_DERIVED_IMPORT_POSTS=0` et
`NEW_GO_OR_RETRY_AUTHORIZED=NO` exigent une preuve indépendante avant tout usage opérationnel.

## Qualification sans campagne

Le build borné doit explicitement désactiver Failsafe : le parent Spring Boot déclare ses
exécutions même sans `-Pintegration-tests`. La commande de qualification WO-049 est :

```text
mvnw.cmd clean verify --offline
  -Dmaven.repo.local=<cache Maven local déjà disponible>
  -DskipITs=true
  -Dsurefire.excludes=**/J6NativeBinaryPipelineQualificationTest.java
  -P!integration-tests,!provider-playwright-local-qualification,!j7-browser-origin-loopback-qualification,!provider-playwright-runtime
```

L'exclusion J6 évite de rejouer sa qualification native sans rapport avec le correctif. Les
tests d'intégration et le receiver ne doivent pas être démarrés pour vérifier ces fonctions.
Le flag abrégé `-o` n'est pas utilisé : il est ambigu pour le wrapper PowerShell de ce dépôt.

```powershell
Import-Module Pester -RequiredVersion 3.4.0
Invoke-Pester -Script scripts/Tests/WO049LaunchPreparation.Tests.ps1
```

`Wo049LauncherOfflineQualificationTest` invoque uniquement le script de fixtures
`scripts/wo049/Export-WO049SyntheticLaunchPreparation.ps1` dans un PowerShell 7 enfant borné à
30 secondes. Ce script n'accepte aucune entrée privée et n'écrit aucun artefact. Le test Java
injecte son mapping dans `SystemEnvironmentPropertySource`, puis dans le Binder Spring avec
le véritable `application.yml`. Il vérifie les contraintes Java sans démarrer de contexte
applicatif. Le golden du justificatif est vérifié indépendamment par SHA-256 Java.

## Porte avant adoption par une future campagne

La qualification hors ligne ne valide pas le lancement réel. WO-046 devra, sous nouvelle
autorité, adopter explicitement ce module dans un lanceur successeur et vérifier les octets
du module, les JAR, les références qualifiées, les autorisations et le nouveau manifeste gelé.
Le grant R1 révoqué et son manifeste sont non réutilisables. Le volume receiver conservé doit
faire l'objet d'une décision explicite de réutilisation ou de remplacement.

Avant tout lancement futur : rendre le mapping, vérifier son intégralité, contrôler son binding
avec les classes du JAR exact, puis revérifier le go et sa fenêtre. Le succès de ces opérations
ne dispense pas de l'enregistrement durable, de la confirmation UI à usage unique, du claim
atomique, des contrôles de readiness et du cleanup exact. Aucune de ces actions n'est exécutée
par WO-049 et aucun défaut ne permet une relance automatique.
