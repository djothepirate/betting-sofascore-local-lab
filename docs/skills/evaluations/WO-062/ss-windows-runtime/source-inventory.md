# WO-062 — Inventaire préparatoire de ss-windows-runtime

- Statut : `PREPARED_NOT_RUN` ; préparation C2, avant rédaction et évaluation.
- Date : 15 septembre 2026.
- Racine lue : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- Commit de référence : `6648dd423e556b5248b8793a539ae85a7680f9bc`.
- Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`.
- POM : `0.1.0-rc.1-SNAPSHOT`, Java 25, Spring Boot 4.1.0.
- 23 sources ciblées, plus deux nouvelles entrées opératoires figées.
- Cet inventaire est réservé à la conception/revue ; ne pas le transmettre aux essais.

## Provenance

Le [manifeste](manifest.json) fige les sources par chemin, taille et SHA-256 des
octets du worktree lus, ainsi que l'OID du blob Git au commit exact ci-dessus.
Le blob Git et le SHA-256 de matérialisation Windows ne sont pas interchangeables :
les fins de ligne peuvent différer. Le lanceur matérialise le blob figé ou vérifie
la copie exacte, puis conserve ses propres empreintes. WR-N02, source de cadrage,
n'appartient à aucune allowlist métier ; une actualisation du WO n'actualise pas les cas.

Les sept autres fichiers de préparation sont identifiés dans le manifeste, qui ne
se hache pas lui-même. Les nouvelles entrées [runtime-protocol.md](runtime-protocol.md)
et [NativeProbe.java](fixtures/NativeProbe.java) sont des données d'essai autorisées,
pas des sources historiques. Leurs hashes appartiennent à `files` et
`evaluation_files` ; elles n'ont pas de blob au HEAD source antérieur à leur création.

| ID | Classe | Source et localisation | Utilité |
| --- | --- | --- | --- |
| WR-N01 | normative | [AGENTS.md](../../../../../AGENTS.md) — Invariants ; socle ; tests | Statuts locaux, Java 25, absence de fournisseur, propriété et portée des changements. |
| WR-N02 | scoping | [docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md](../../../../../docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md) — Sections 4.C2, 5 et 6 | Contrat du skill et obligation de deux exécutions utiles ; cadrage exclu des entrées évaluées. |
| WR-P01 | procedure | [docs/runbooks/RUNBOOK-LOCAL.md](../../../../../docs/runbooks/RUNBOOK-LOCAL.md) — Sections 1, 4 et 6 | Eclipse, UTF-8, contrôles locaux et incidents ports ; procédures soumises à la propriété des ressources. |
| WR-P02 | procedure_implementation | [scripts/Preflight-Local.ps1](../../../../../scripts/Preflight-Local.ps1) — Assert-Command ; java --version ; docker info | Préflight réel : commande présente, code natif et moteur accessible sont distincts. |
| WR-P03 | procedure_implementation | [scripts/Verify-Local.ps1](../../../../../scripts/Verify-Local.ps1) — Préflight ; Push-Location ; commandes Maven | Commande Windows effective avec skipITs et ajout explicite intégration. |
| WR-I01 | implementation | [pom.xml](../../../../../pom.xml) — parent ; version ; java.version ; profils | Java 25, Spring Boot 4.1.0, monomodule et version du train. |
| WR-I02 | implementation | [mvnw.cmd](../../../../../mvnw.cmd) — JAVA_HOME ; MAVEN_OPTS ; exécution Java | Frontière Windows du wrapper ; lecture uniquement, aucun Maven dans les cas runtime. |
| WR-I03 | implementation | [.mvn/wrapper/maven-wrapper.properties](../../../../../.mvn/wrapper/maven-wrapper.properties) — distributionUrl ; wrapperVersion | Version Maven résolue par le wrapper, sans installation ni téléchargement. |
| WR-I04 | implementation | [src/main/resources/application.yml](../../../../../src/main/resources/application.yml) — server.address ; flags fournisseur | Adresse loopback et configuration courante ; aucune application lancée. |
| WR-I05 | implementation | [src/main/resources/application-local.yml](../../../../../src/main/resources/application-local.yml) — configuration locale ; JDBC | Surcharges locales versionnées, sans lire .env ni démarrer une base. |
| WR-H01 | historical | [docs/validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md](../../../../../docs/validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md) — Sections 2 à 6 | Split natif reproduit, correction bornée et écart Testcontainers déclaré. |
| WR-H02 | historical | [docs/validation/J9-WO053-CTRL-BREAK-DIAGNOSTIC-20260905.md](../../../../../docs/validation/J9-WO053-CTRL-BREAK-DIAGNOSTIC-20260905.md) — Contrat expurgé ; exécutions locales ; essais GitHub | Instrumentation, différences hôte/runner et cause originale NOT_ESTABLISHED. |
| WR-H03 | historical | [docs/validation/J9-WO053-REVIEW-HARDENING-20260905.md](../../../../../docs/validation/J9-WO053-REVIEW-HARDENING-20260905.md) — Portée ; matrice ; vérifications | Contre-épreuves du relais et cleanup ; nouveau diff et preuves anciennes distingués. |
| WR-H04 | historical | [docs/validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md](../../../../../docs/validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md) — Incident initial et portée des preuves | Incident PSModulePath observé dans B, correction uniquement enfant. |
| WR-H05 | historical | [docs/skills/evaluations/WO-062/football-quality-ci-security/installation/initial-runtime-failure.json](../../../../../docs/skills/evaluations/WO-062/football-quality-ci-security/installation/initial-runtime-failure.json) — diagnostic ; resolution | Échec Get-FileHash et comparaison héritage/defaults dans un contexte exact. |
| WR-I06 | implementation | [scripts/wo036/WO036-CampaignTools.psm1](../../../../../scripts/wo036/WO036-CampaignTools.psm1) — ConvertTo-WO036JavaJarStartProcessArgument ; Start-WO036ComponentCore | Protection de la valeur JAR, flags et frontière native ; module lu/importé par le seul harnais autorisé. |
| WR-P04 | controlled_qualification | [scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1](../../../../../scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1) — Préflight ; Invoke-WO044ArgumentCapture ; finally | Reproduction contrôlée existante PS7, capture par enfant et nettoyage exact. |
| WR-P05 | controlled_fixture | [scripts/wo044/Capture-WO044NativeArguments.ps1](../../../../../scripts/wo044/Capture-WO044NativeArguments.ps1) — args ; sortie JSON UTF-8 | Enfant de capture réel sans Java, réseau ni application. |
| WR-T01 | test | [scripts/Tests/WO044JavaJarArgumentBoundary.Tests.ps1](../../../../../scripts/Tests/WO044JavaJarArgumentBoundary.Tests.ps1) — Describe WO-044 Java JAR native argument boundary | Assertions du contrat ; lecture seulement, aucune suite Pester exécutée par ce cas. |
| WR-I07 | implementation | [scripts/J6-NativeBinaryPipeline.psm1](../../../../../scripts/J6-NativeBinaryPipeline.psm1) — J6WindowsKillOnCloseJob ; New-J6OwnedProcessIdentity ; cleanup | Preuve de propriété, délais et nettoyage séparé du résultat ; ne pas importer pour la petite sonde. |
| WR-T02 | test | [scripts/Test-J6CtrlBreakDiagnosticContract.ps1](../../../../../scripts/Test-J6CtrlBreakDiagnosticContract.ps1) — allowlist ; diagnostic ; failure isolation | Contrat de diagnostic synthétique, ne reproduit pas la cause CI. |
| WR-R01 | routing | [docs/skills/local-lab/ss-verify/SKILL.md](../../../../../docs/skills/local-lab/ss-verify/SKILL.md) — Choisir l'exécution ; diagnostic et résultats | Frontière lot 1 : vérification standard sans incident Windows à diagnostiquer. |
| WR-R02 | routing | [docs/skills/local-lab/ss-work-order/SKILL.md](../../../../../docs/skills/local-lab/ss-work-order/SKILL.md) — Reprise ; cadrage | Frontière lot 1 : reprise et périmètre du WO. |

## Distinctions issues des sources

1. **Une frontière native n'est pas une simple liste PowerShell.** WO-044 reproduit
   la recomposition de `Start-Process -ArgumentList` : le chemin non protégé se
   fractionne ; le helper valide l'unique chemin canonique `.jar` et le protège
   explicitement. Les trois flags JDK, l'identité et l'ordre immédiatement après
   `-jar` font partie du contrat. Sources WR-H01, WR-I06, WR-P04, WR-P05, WR-T01.
2. **La reproduction historique possède un runtime précis.** Le harnais appelle
   `pwsh.exe`, emploie `$IsWindows`, `IsPathFullyQualified` et
   `ConvertFrom-Json -Depth` ; il n'est pas directement compatible PS5.1.
   WR-H01 le rejoue en PS7 Windows avec deux itérations, sans démarrer Java.
   WR-N01 fournit séparément une exécution réellement PS5.1 avec Java 25. Ne pas
   confondre cette sonde neuve, la capture simulant Java et un lancement Spring réel.
3. **Une instrumentation verte ne prouve pas la cause d'une intermittence.**
   WR-H02 conserve la cause originale `NOT_ESTABLISHED`, même après deux essais
   Windows Server 2022 réussis, sur le commit diagnostique
   `5f3dea2b72c0751174ea389891397e11bd9e08ae`. Le dispatch de branche et
   l'instrumentation diffèrent du merge PR original ; un autre job Linux échoue
   avant tests. WR-H03 ajoute ensuite des contre-épreuves de relais/cleanup,
   pas un correctif causal. Les verts anciens ne qualifient pas les futurs octets.
4. **Les résultats appartiennent à leur couche.** Le code Java/Maven immédiatement
   observé ne se déduit pas du code extérieur d'un script ni d'un `Write-Output`.
   Un code lu après une autre commande native a pu être écrasé. `Start-Process`
   expose le code de son objet Process après sortie, pas via `$LASTEXITCODE`.
   Sources WR-P02, WR-P03, WR-I02 et WR-H03.
5. **Les délais et le nettoyage apportent des preuves différentes.** Un retour du
   prompt ne démontre pas zéro enfant restant ; un cleanup sans exception ne suffit
   pas. Le harnais WO-044 conserve identité/PID/heure/commande avant arrêt. Le module
   J6 sépare résultat primaire et preuve du nettoyage avec confinement Windows.
   La nouvelle sonde n'importe pas ce gros module ni ses parcours ; elle possède
   ses deux processus sans descendants et conserve leurs handles. Sources WR-P04,
   WR-I07, WR-H02, WR-H03.
6. **Le wrapper et les tests réellement exécutés font autorité.** Le standard Windows
   courant appelle `mvnw.cmd -DskipITs clean verify` ; le wrapper Java 25 et les
   profils doivent être lus au bon worktree. `--offline` ne désactive pas Docker
   ou les tests de réseau. L'écart déclaré de WO-044 a démarré des Testcontainers
   isolés avec une commande sans skipITs ; il ne doit ni être masqué ni reproduit.
   Aucun Maven/Pester/Docker n'est nécessaire aux deux cas préparés.
7. **CLI Docker présent et moteur accessible sont distincts.** Le préflight appelle
   `docker info` et vérifie son code, puis Compose. Il ne suffit pas d'identifier
   `docker.exe`. Sonde indisponible, moteur arrêté et contexte incorrect restent
   des hypothèses distinctes tant que les observations manquent. WR-P02.
8. **Un conseil opérateur n'autorise pas l'arrêt d'un tiers.** Le runbook décrit
   notamment le port 8087 cadré ; l'inventaire des propriétaires doit précéder
   toute action. Le cas C2 donne explicitement un Java tiers non possédé : ne pas
   transposer en arrêt global, mutation de .env ou ouverture de 0.0.0.0.
   WR-P01 reste soumis à AGENTS et aux bornes actuelles du cas.
9. **PSModulePath : expérience B bornée.** Le lancement Python → Windows PowerShell
   5.1 du 15 septembre a hérité de chemins PS7 et a échoué sur Get-FileHash avant
   copie. Le diagnostic avec defaults natifs a réussi ; la correction a omis
   PSModulePath uniquement dans la copie d'environnement enfant, sans distinction
   de casse du nom. WR-H04/H05 ne prouvent ni une panne universelle de PS5.1, ni
   que toutes les valeurs PS7 sont incompatibles, ni qu'une suppression globale
   soit nécessaire. Une nouvelle panne exige une observation comparable.
10. **Encodage et affichage doivent être séparés.** Les textes du dépôt restent
    UTF-8 ; la sonde neuve émet explicitement UTF-8. Sous PS5.1, `Out-File` et
    une lecture implicite n'assurent pas le même encodage que PS7. Vérifier les
    octets, BOM et lecture effective avant de conclure à une corruption ; ne pas
    réencoder globalement le dépôt ni changer les fins de ligne pour corriger
    l'affichage d'une console.

## Préparation exécutable, pas qualification déclarée

Les cas historiques et neufs contiennent un protocole opératoire transmis à la
session indépendante : copies figées, runtime natif précis, identité des enfants,
deadlines, espaces, répertoire temporaire et preuves persistantes distincts.
WR-H01 réutilise le harnais existant intact. WR-N01 utilise une fixture Java
minimale dont les deux modes produisent de vraies sorties natives différentes.
Aucun tableau de résultats ni script de conduite déjà évalué n'est livré.

Tous les cas restent `NOT_RUN`. Aucun candidat `ss-windows-runtime`,
validation humaine, installation personnelle, amélioration applicative ou gain
général n'est établi. La vérification de liens/JSON/empreintes de cette préparation
ne peut pas être comptée comme reproduction du harnais ou qualification PS5.1.
