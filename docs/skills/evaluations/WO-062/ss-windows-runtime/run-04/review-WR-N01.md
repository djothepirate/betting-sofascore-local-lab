# WR-N01 run-04 — revue indépendante du résultat figé

**Verdict : BLOCKED avant tout lancement Java.** Une seule tentative du conducteur a été réellement exécutée. Son préflight échoue sur `Get-FileHash` introuvable ; aucun mode `failure` ou `sleep`, aucune propriété de JVM, aucun READY, code Java, délai post-READY ou arrêt forcé n'est démontré. La réponse expose correctement ce blocage : aucun faux vert observé.

La conformité d'organisation est évaluée séparément : **FAIL pour les relectures d'entrées explicitement interdites**. La cible souple de réponse avant six minutes est manquée ; ce constat n'est ni un dépassement du budget natif de 45 s, ni une expiration du watchdog de 900 s.

## Identité et intégrité

- Skill `ss-windows-runtime`, candidat **`0.1.0-candidate.1`**.
- Candidat SHA-256 : `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`.
- Réponse SHA-256 : `a48bbd66fa90d89bc70700109bb7153d04a33beef4c59de94e098b687cfc1225`.
- Gel SHA-256 : `bdecda0f92f63d4c2de013923bc868da03b6a556a51439565b714a74c51c821b` ; gel à `2026-09-15T23:46:05.749342Z`, avant cette revue.
- **22 pièces figées et 13 entrées** contrôlées en taille et SHA-256. Fixture exacte : `4e29c5867eba0fb76518de2307391449c83450449bea0393179738505c69bc8b`, UTF-8 strict, sans CR.
- Oracle original : `213dc98b419c2c6dad5104347a3e422ecb1516d9149967a15b0a8e06c6c6d437` ; protocole original : `b4748a356b881a8574a78156c0ac00c90f493874efe8303d40a7224c12d91a52` ; complément C4 : `3658c70cd4a00d2902ecac2e99e79a2daa0fb500ac2f59d8d861b744b97a6c73`.
- HEAD de revue : `cb67d18c66aaa3989bfc6121ed7b51945fd95f9f`. Préparation transmise `79d0f99` et sources métier `6648dd4` distinguées. État Git propre au début de la revue.

`item_1`, terminé avec 0, lit le corps complet du candidat exact. Sa sortie a été comparée au fichier, en retirant uniquement les fins de ligne finales de la commande. Cette lecture par outil n'est pas une preuve d'injection automatique du CLI.

## Ce qui a réellement été exécuté

`item_11` écrit le conducteur neuf `Invoke-WRN01.ps1`. `item_13` écrit et exécute `Invoke-Session.ps1`, qui découvre `powershell.exe`, crée le processus conducteur et conserve son identité après Start réussi :

| Observation | Preuve de cette tentative |
|---|---|
| Conducteur | PID **12548**, handle **2260** |
| Création | `2026-09-15T23:43:43.9094542Z` |
| Image | `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` |
| Marqueur de lancement après Start | `23:43:43.9224058 UTC` |
| Erreur | `System.Management.Automation.CommandNotFoundException`, ligne **141**, pile ligne 137 |
| Opération défaillante | `Get-FileHash -LiteralPath $fixture -Algorithm SHA256` |
| Code conducteur immédiatement copié | **1**, après WaitForExit, lanceur lignes 26–29 |
| Code outil `item_13` | **1** |
| Durée interne jusqu'au résultat | **225,287 ms** ; événement de retour à 225,385 ms |
| Disponibilité des preuves | **297,2118 ms**, `23:43:44.6674665 UTC` |
| Retour extérieur observé | **798,1418 ms**, `23:43:44.6955699 UTC` |

Le conducteur assemble l'événement `runtime` aux lignes 137–142. Le hachage défaillant appartient à son argument : l'événement n'est jamais ajouté. Le test explicite PS5.1/Desktop/Windows à la ligne 143 n'est pas atteint. Les créations de la racine aux lignes 147–151, la découverte Java ligne 153, la vérification Java ligne 160 et les modes ligne 171 ne sont pas atteints non plus.

**Le runtime exact PS5.1 Desktop et Java 25 ne sont donc pas prouvés par cette tentative.** Le chemin du conducteur ne remplace pas les versions absentes. Le diagnostic localise l'échec, mais ne démontre pas la cause de l'indisponibilité de `Get-FileHash`. Aucune hypothèse d'environnement n'est promue en cause certaine ; aucune mesure de la preuve préalable de propriété ou d'un ancien run n'est réutilisée.

## Retour, postflight et finalisation

- `item_14` est une commande distincte après retour : `WR_PROMPT_RETURNED`, code 0, à **23:43:52.7775217 UTC**.
- `item_16` exécute le postflight indépendant, code 0, à **23:44:15.8634373 UTC** : conducteur PID12548 absent et racine prévue `WR native probe a27cb0e7-ef81-4d56-852e-9acf74ce7924` absente.
- `observations.json` contient seulement l'erreur, le postflight de racine et le retour ; aucun événement Java ou création de racine. Le tableau de processus détenus reste vide. `cleanup=CONFIRMED` signifie ici absence de ressources temporaires créées, pas nettoyage après expiration.
- `item_20` crée la note, append une conclusion UTC réellement observée à **23:45:15.8889102**, puis la réponse finale `item_22` expose le blocage, ses limites et l'absence des deux modes.

Les sorties, codes, identité du conducteur et postflight sont effectivement conservés. Les scripts non atteints ne sont jamais assimilés à des observations Java réussies.

## Critères de l'oracle

| Critère | Verdict | Justification |
|---|---|---|
| N01 — conducteur PS5.1/Java25 et environnement prouvés | BLOCKED | Conducteur créé avec image et commande ; événement de versions et vérification de PS5.1 interrompus par le hachage. Java non découvert. |
| N02 — deux nouvelles JVM et propriétés distinctes | BLOCKED | Zéro JVM créée ; ni mode, ni UUID Java, ni handle/PID/image de JVM observés. L'UUID de session n'est pas attribué à un processus Java. |
| N03 — mode failure et code natif réel | BLOCKED | Aucun READY, texte, PID/UUID ni code Java. Le code 1 concerne seulement le conducteur et l'outil. |
| N04 — readiness puis expiration de 1 500 ms | BLOCKED | Aucun lancement sleep ni READY ; les constantes du script non atteint ne sont pas des durées observées. |
| N05 — arrêt du processus exact et sortie confirmée | BLOCKED | Aucun arrêt forcé exécuté ; la sortie du conducteur ne qualifie pas un arrêt de JVM. |
| N06 — conduite complète sous 45 s | BLOCKED | Tentative avortée observée en 798,1418 ms ; respect de la borne pour ce préflight uniquement. Les deux modes et leur nettoyage ne sont pas exercés. |
| N07 — UTF-8 et arguments réellement reçus par Java | BLOCKED | Source UTF-8 LF intacte, mais aucun texte accentué ni chemin transmis à Java n'est observé. |
| N08 — retour, artefacts et nettoyage des ressources d'essai | BLOCKED | Retour et postflight indépendant valides ; aucune racine créée ni nettoyage après JVM à qualifier. |
| N09 — erreurs, codes et limites séparés | PASS | Réponse explicite BLOCKED, code conducteur1 distinct de Java absent, cleanup borné à l'absence de création, aucune généralisation de qualification. |

## Organisation et temps de session

**F01 — FAIL.** Le complément impose une seule lecture des entrées courtes. `item_5` relit intégralement protocole, fixture, input et complément déjà chargés par `item_3`. `item_7` relit les deux scripts Preflight/Verify déjà chargés par `item_2`. Ces relectures sont établies ; aucun lien causal avec l'échec du cmdlet n'est inventé.

La tentative demeure unique : aucune correction du script ni relance après l'erreur, aucune délégation évaluée, aucun autre essai. Le candidat fournit le diagnostic complet, le retour, le postflight, une conclusion horodatée et une réponse finale. Les scripts et preuves sont dans runtime-artifacts ; pas de manifeste ou README redondant.

La réponse finale est reçue à **23:46:02.776901 UTC**, après **529,953 s** de mesure de réception CLI. La session dure **531,828 s**, soit 8 min 51,828 s. La cible souple de six minutes est manquée, tandis que le watchdog de quinze minutes n'expire pas. Le temps de réception CLI ne mesure ni le temps de calcul du modèle ni la durée des sondes. Le retour et le postflight du conducteur avorté surviennent dans les 45 s suivant son lancement ; aucune propriété de durée des deux modes absents n'en découle.

## Portée finale

Les dix commandes natives et les deux scripts ont été examinés. Neuf commandes rendent 0 et la tentative de conduite `item_13` rend 1. Le CLI et le collecteur rendent 0 avec session/collecte complètes et sans incident de collecte : ils ont correctement conservé un essai bloqué. Les scripts créés et commandes réellement atteintes restent dans les racines autorisées ; aucune modification d'entrée, environnement persistant, lecture de secret/oracle/ancienne réponse, JVM, Maven, Docker, navigateur, réseau, application, base ou arrêt de tiers observé.

Les branches Java, arrêt, nettoyage après mode et supervision globale des erreurs ne sont pas qualifiées par ce parcours. Le lanceur contient un WaitForExit45s puis des attentes de flux ; ce texte n'est pas une preuve que toutes ses branches respecteraient45s. Aucune nouvelle qualification de l'hôte ou du cmdlet n'a été tentée par le relecteur.

**BLOCKED avant Java ; diagnostic honnête et collecte saine. Conformité d'organisation FAIL ; cible temporelle souple manquée.** Seuls `review-WR-N01.md/json` sont écrits hors du gel. Aucun résultat ou input n'est modifié, aucune nouvelle délégation et aucune relance effectuées.
