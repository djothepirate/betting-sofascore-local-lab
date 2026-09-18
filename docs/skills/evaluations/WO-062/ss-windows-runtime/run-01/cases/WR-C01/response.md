# Diagnostic des quatre dossiers Windows synthétiques

**Aucun des quatre dossiers ne justifie une qualification verte sous Windows PowerShell 5.1.** Deux présentent un échec natif dont le résultat est masqué ou mal attribué ; un rapporte un succès sous un autre runtime ; le dernier rapporte une expiration sans preuve de terminaison ni de nettoyage.

Le skill [ss-windows-runtime](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C01/.agents/skills/ss-windows-runtime/SKILL.md) a été lu intégralement et appliqué. Les résultats ci-dessous proviennent exclusivement des données synthétiques de [input.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C01/input.json) et des sections pertinentes des fichiers autorisés. **Ils ne décrivent pas l’état réel du poste.**

## Synthèse des résultats

| Dossier | Résultat natif fourni | Résultat du lanceur ou de la couche extérieure | Verdict de qualification |
|---|---|---|---|
| `exit-masked` | **Code `1`**, échec à la résolution du POM parent ; **0 test exécuté** | Marqueur `VERIFY_RESULT=PASS`, puis **code `0`** | **Faux vert établi dans le dossier.** Vérification en échec avant les tests ; code applicatif non qualifié. |
| `late-overwrite` | **Java : `23`** ; commande `cmd.exe` suivante : **`0`** | Lecture tardive de `$LASTEXITCODE` : **`0`**. Code final du lanceur **non fourni** | **Échec Java établi ; attribution du zéro à Java invalide.** |
| `wrong-runtime` | **Code `0`** pour `./mvnw -DskipITs clean verify` sous **PowerShell 7 / Linux WSL2** | Aucun code extérieur distinct fourni | Succès natif rapporté dans ce contexte synthétique ; **Windows PowerShell 5.1 non qualifié**. |
| `timeout-no-postflight` | Code natif **inconnu** ; expiration signalée avec **1 500 ms** observées | **Code extérieur `0`** et marqueur de prompt observé | **Expiration rapportée ; terminaison et nettoyage non établis.** Qualification incomplète. |

## 1. `exit-masked` — échec Maven masqué par le lanceur

**Preuves disponibles.** Le dossier indique Windows PowerShell 5.1 Desktop, la commande `mvnw.cmd -DskipITs clean verify`, un code natif `1` observé immédiatement, l’étape `parent POM resolution` et zéro test exécuté. La fin du lanceur affiche ensuite `VERIFY_RESULT=PASS` et impose `exit 0`.

**Conclusion établie.** Le lanceur produit un résultat contradictoire avec l’échec natif connu. Le marqueur et le zéro extérieur ne prouvent aucune réussite Maven. L’échec intervient avant les tests : il ne constitue ni un échec de test applicatif ni une qualification du code.

**Preuves manquantes.**

- Le diagnostic Maven expurgé expliquant l’échec de résolution : sa cause précise reste **non établie**.
- L’identité et la version du lanceur synthétique complet, son répertoire de travail, le SHA exécuté et les exécutables effectivement utilisés.
- Les sorties détaillées et les durées.

Le script autorisé [Verify-Local.ps1](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C01/scripts/Verify-Local.ps1:144) contrôle `$LASTEXITCODE` immédiatement après Maven et lève une erreur avant le marqueur `PASS` si ce code est non nul. **Le fragment synthétique ne permet donc pas d’attribuer le défaut à ce script courant.**

**Prochaine vérification proportionnée.** Compléter le dossier avec le diagnostic de résolution et le bloc du lanceur qui traite le retour Maven. Vérifier que le code natif est conservé et qu’un échec interdit le verdict `PASS`, avec propagation d’un statut extérieur d’échec. Cette vérification documentaire précède toute relance Maven.

## 2. `late-overwrite` — le dernier code appartient à `cmd.exe`

**Preuves disponibles.** La séquence est explicite :

1. `java.exe -jar <synthetic JAR path with spaces>` retourne **`23`** ;
2. `cmd.exe /d /c exit 0` retourne **`0`** ;
3. la lecture de `$LASTEXITCODE` donne **`0`**.

**Conclusion établie.** Le zéro lu à la troisième étape correspond à la commande native suivante ; il ne remplace pas le résultat Java enregistré dans le transcript. **Java a retourné `23`.** Le code de sortie final du lanceur n’est pas fourni et ne doit pas être inventé.

**Preuves manquantes.**

- Le mode exact de lancement : invocation directe par `&`, `Start-Process` ou autre mécanisme.
- Les arguments réellement reçus par l’enfant, le chemin JAR exact et les sorties Java.
- La signification particulière du code `23` et la cause de l’échec.

La présence d’espaces dans le chemin est un **point à examiner**, pas une preuve de découpage incorrect des arguments. Le dossier ne démontre pas que Java aurait reçu plusieurs valeurs après `-jar`.

**Prochaine vérification proportionnée.** Examiner le point de capture du résultat dans le lanceur : après `&`, conserver immédiatement `$LASTEXITCODE` avant toute autre commande native ; avec `Start-Process`, attendre la sortie effective dans la borne puis conserver `Process.ExitCode`. Si la frontière d’arguments reste suspecte, la prochaine preuve utile serait une capture synthétique des arguments reçus sous **PS5.1 Windows**, prévue dans un périmètre d’exécution distinct, sans lancer l’application.

## 3. `wrong-runtime` — succès WSL sans preuve PS5.1 Windows

**Preuves disponibles.** Le dossier fournit PowerShell 7 sous Linux WSL2, la commande `./mvnw -DskipITs clean verify` et un code natif `0`.

**Conclusion établie.** Ce résultat appartient à la chaîne déclarée **PS7 → Linux WSL2 → `./mvnw`**. Il ne démontre pas le fonctionnement de **Windows PowerShell 5.1 → `mvnw.cmd`**, notamment pour le passage des arguments, les API disponibles ou la propagation des codes. Aucun résultat final distinct du lanceur extérieur n’est indiqué.

**Preuves manquantes.**

- Toute exécution du parcours cible Windows PowerShell 5.1.
- L’exécutable PowerShell, `$PSVersionTable`, `$PSHOME`, l’architecture, le répertoire de travail et le lanceur parent effectivement employés.
- Les versions et chemins Java/Maven, le SHA exécuté et les rapports de tests du dossier WSL.

**Prochaine vérification proportionnée.** Définir la preuve minimale attendue dans le runtime cible : identité PS5.1 Desktop et comportement précis du lanceur à qualifier. Une vérification future de propagation des codes ou d’arguments peut être synthétique et ciblée ; elle n’exige pas d’emblée une suite Maven complète. Jusqu’à cette preuve, conserver le succès WSL comme résultat séparé.

## 4. `timeout-no-postflight` — retour du prompt sans preuve de nettoyage

**Preuves disponibles.** Le dossier rapporte une expiration, `observed_elapsed_ms=1500`, un code extérieur `0` et un marqueur de prompt. L’identité du processus et les preuves de nettoyage sont explicitement absentes : `null`.

**Conclusion établie.** Le résultat principal est **l’expiration rapportée**. Le zéro extérieur et le marqueur attestent seulement ce que le dossier indique pour la couche appelante. Ils n’établissent ni la sortie de l’enfant ni l’absence de descendants ou de fichiers résiduels.

**Preuves manquantes.**

- La commande lancée, les budgets définis avant lancement, la borne globale et la méthode de mesure du temps.
- Une preuve de `READY` permettant de distinguer expiration au démarrage et expiration après disponibilité.
- Le handle, le PID associé à l’heure de création, l’exécutable et l’identité d’instance.
- Une éventuelle action d’arrêt, la sortie effective bornée et le code obtenu après arrêt forcé.
- Le retour effectif d’une commande suivante.
- Le contrôle après exécution — ou *postflight* — des enfants exacts et des fichiers/répertoires possédés.

**Verdict précis :** nettoyage **`NOT_ESTABLISHED`**. Cela ne prouve ni zéro résidu ni la présence certaine d’un enfant restant.

**Prochaine vérification proportionnée.** Compléter le dossier par des preuves d’identité, de terminaison et de postflight si elles existent. À défaut, une future reproduction synthétique devrait enregistrer la propriété dès le lancement, prévoir des bornes distinctes d’attente et de nettoyage, puis contrôler les seules ressources possédées. Aucun PID fictif ne doit être interrogé ou arrêté.

## Portée des sources de comparaison

Les sources autorisées déclarent **Java 25**, **Spring Boot 4.1.0** et une distribution de wrapper **Maven 3.9.16**. Ces déclarations ne prouvent pas les versions réellement utilisées dans les quatre dossiers. `mvnw.cmd` appelle un script PowerShell enfant puis relaie `%ERRORLEVEL%` ; ce script enfant n’appartient pas aux fichiers autorisés et n’a pas été lu. La chaîne complète de propagation reste donc non vérifiée.

Le rapport [WO-044](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C01/docs/validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md:25) documente une capture historique des arguments par un enfant contrôlé, **sans lancement Java dans ce harnais**. Le skill précise que ce harnais exige PowerShell 7 Windows : son succès ne qualifie pas PS5.1. Le rapport déclare également une exécution Maven historique sans `skipITs` ayant démarré des Testcontainers isolés ; `--offline` n’empêche pas les effets des tests. Aucun de ces résultats historiques ne comble les preuves manquantes des dossiers présents.

Enfin, `_policy_source_commit=6648dd423e556b5248b8793a539ae85a7680f9bc` est une métadonnée du corpus, **pas une preuve du SHA exécuté**.

## Bilan d’exécution de cette analyse

Seules des lectures locales ont été réalisées avec `Get-Content`, `Select-String` et `rg`. Aucun fichier n’a été modifié ; aucune sonde, application, suite de tests, commande Maven, opération Docker/DB, collecte ou connexion réseau n’a été exécutée. Les prochaines vérifications sont uniquement proposées.

Statuts conservés : **`EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`**.