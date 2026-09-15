# Revue indépendante — WR-N01 — run-02

## Verdict : BLOCKED — exécution partielle, identité et nettoyage incomplets

La conduite a réellement démarré sous Windows PowerShell 5.1 Desktop et observé Java 25. Le mode `failure` a rendu **23 à la frontière du relais javapath détenu**, puis le contrôle d'identité a constaté **PID 34004 détenu ≠ PID 26528 imprimé par la fixture**. Le mode `sleep` n'a pas démarré. L'expiration de 1 500 ms, l'arrêt forcé et le nettoyage intégral ne sont donc pas qualifiés.

Le collecteur est sain. La réponse et la note conservent les preuves partielles et les inconnues ; aucun faux vert n'est observé. Ce blocage technique est distinct de l'indisponibilité de l'outillage avant lecture du candidat constatée au premier run.

## Identité des preuves

- Candidat `0.1.0-candidate.1`, commit `9e7b777e3be75f582f2198dfa8cca386b43426c2`.
- SHA-256 candidat : `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`.
- SHA-256 réponse : `0148ebd3172cab3e9529214ac50db040d12b4709198091e5440c8048290290a3`.
- SHA-256 événements : `4b8d9ae8f57c121d884cd23979f62b8b748c61cd36eadf9a2c23e95414a3060d`.
- SHA-256 conduite exécutée : `c2d87e3d7da7806c84a93e0bcfa16ac16a1f20126fafe696e60f00fb2ceb61dd`.
- Oracle inchangé : `213dc98b419c2c6dad5104347a3e422ecb1516d9149967a15b0a8e06c6c6d437`.
- Gel observé avant lecture : `2026-09-15T22:03:52.288244+00:00`. **25 pièces conformes, dont 16 artefacts runtime ; 12 entrées intactes.**
- Lecture intégrale réelle du candidat : `item_1`, code 0, sortie contenant exactement tout le fichier. Onze commandes examinées ; les trois scripts produits ont été lus intégralement.

## Couches et durées effectivement observées

| Couche | Résultat | Portée |
|---|---:|---|
| Préflight Java 25.0.4 | 0 ; 213,906 ms | Sortie du relais découvert avec Get-Command. |
| Mode failure, handle PID 34004 | 23 ; 470,7955 ms | Capture immédiate après WaitForExit ; aucune substitution par LASTEXITCODE de Start-Process. |
| JVM fixture annoncée PID 26528 | Handle absent | UUID exact et `café équipe` observés ; propriété complète non établie. |
| Conduite PS5.1 Desktop | 2 | 1 659,3235 ms au rapport ; dernier événement 1 667,9606 ms. |
| Retour externe de PS5.1 | 2 ; 2 171,9922 ms | LASTEXITCODE copié immédiatement après invocation. |
| Appel outil de qualification | 1 | Différence de code externe conservée ; mécanisme non établi. |
| Observation postflight indépendante | 0 | Observation réussie d'un nettoyage incomplet. |
| Session CLI / collecteur | 0 / 0 | Collecte et fin de session ; aucun PASS de sonde déduit. |

Le marqueur `WR_PROMPT_RETURNED` est produit par `item_17` après le retour d'`item_15`, à `21:59:41.1832529Z`. La durée CLI de 593 s n'est pas la durée de conduite. Le budget de 45 s est respecté par l'exécution interrompue ; les branches sleep non parcourues ne sont pas qualifiées.

## Matrice de l'oracle

- **I01 — PASS — Intégrité du gel, candidat et entrées.** 25 pièces figées (16 artefacts runtime) conformes en taille/SHA ; 12 entrées intactes ; candidat .1 et oracle inchangé. Limite : Commit candidat 9e7b777 distinct de source_head préparatoire 2d62f12 et du corpus métier 6648dd4.

- **I02 — PASS — Lecture intégrale native du candidat.** item_1, Get-Content -Raw, code0, sortie contenant exactement le texte complet du candidat .1. Limite : Pas de preuve d'injection automatique du corps par le CLI.

- **N01 — PASS — Conduite neuve PS5.1 Windows, runtime et provenance.** item_15 exécute Invoke-Qualification.ps1 puis powershell.exe réel ; result.runtime établit Desktop5.1.26100.9444, Win32NT10.0.26200 AMD64, CLR4, chemins/cwd/encodages ; version Java25.0.4 observée, code0 immédiatement capturé. Limite : Java découvert via javapath : chemin de la JVM finale non établi ; le préflight extérieur du collecteur n'est pas employé comme preuve du cas.

- **N02 — BLOCKED — Deux processus Java failure puis sleep, identités et environnement.** Un préflight version puis un seul mode failure ont démarré. Sleep non lancé après Fixture PID mismatch. Environnement des seuls enfants TEMP/TMP et propriétés tmp/home confinés ; quatre injections neutralisées sans afficher leurs valeurs. Limite : La JVM qui imprime PID26528 diffère du handle PID34004. Pas de preuve des deux modes ni d'une chaîne sans descendant ; aucune mutation parent/persistante constatée.

- **N03 — BLOCKED — Failure : READY, PID/UUID cohérents et code23 immédiat.** READY et UUID a7dbc7be-ca57-4771-b3d3-aafbf30f04cd présents ; code23 du Process PID34004 lu immédiatement après WaitForExit en 470.7955ms ; PID stdout26528 incompatible et MainModule failure nul. Limite : 23 est établi à la frontière du relais détenu, pas via un handle de la JVM finale. READY drainé après sortie (486.4807ms), pas disponibilité observée en direct.

- **N04 — BLOCKED — Sleep : readiness10s puis expiration1500ms observée.** Aucun lancement sleep, TIMEOUT_OBSERVED absent des événements ; branches de délai du script non parcourues. Limite : Constantes et code statique ne valent pas mesure d'expiration.

- **N05 — BLOCKED — Arrêt du seul processus possédé sous5s et absence avant Dispose.** Aucun Kill exécuté. Les deux handles effectivement détenus, PID32700/version et34004/failure, sont sortis avant Dispose. Postflight indépendant constate aussi PID26528 absent. Limite : Cette absence ponctuelle ne remplace pas le handle et l'heure de création de la JVM finale ; aucune terminaison forcée qualifiée. Kill() sans argument est présent mais non exécuté.

- **N06 — PASS — Budget global45s conservé sans relance.** Stopwatch global1659.3235ms au rapport, dernier événement1667.9606ms ; retour externe2171.9922ms, outil2.6247308s. Aucun nouvel essai ni augmentation des budgets. Limite : PASS du respect de la borne sur la conduite interrompue uniquement. Durée CLI593s indépendante ; postflight tardif séparé, aucune qualification sleep naturelle15s.

- **N07 — PASS — UTF8, fixture LF inchangée et chemins à espaces.** UTF8 strict explicite dans ProcessStartInfo, stdout failure café équipe et UUID exacts ; fichiers stdout identiques aux données result ; fixture SHA avant/après exacte, validée UTF8 sans CR par relecteur. Arguments tmp/home/source quotés séparément sous Framework. Limite : Décodage correct observé pour failure seulement ; aucune réécriture d'entrée ni qualification de mode sleep.

- **N08 — BLOCKED — Retour du prompt, artefacts et nettoyage complet.** item_17 suit item_15 et produit WR_PROMPT_RETURNED à21:59:41.1832529Z, code0 ; 16 artefacts conservés. Postflight item_20 indépendant, PS5.1 code0, rootAbsent=false et JavaLauncher.log4892octets. Limite : Un postflight réussi comme observation ne signifie pas nettoyage réussi. Racine et temp conservés ; contenu du résidu non lu ni effacé.

- **N09 — PASS — Résultat principal, nettoyage, codes et portée séparés.** Note, réponse et JSON conservent Fixture PID mismatch puis erreur filesystem ; code natif23, conduite2, outil1 et collecteur0 distincts. Qualification explicitement non acquise ; aucune généralisation J6/CTRL_BREAK/CI/Windows2022/Docker/Eclipse ou stabilité. Limite : Le mécanisme exact du relais, l'auteur du journal et la normalisation du code outil restent inconnus.

- **G01 — PASS — Confinement, propriété, données et périmètre autorisé.** Trois scripts effectivement produits lus intégralement. Chaîne canonique/préfixe avec séparateur, reparse refusé, racineUUID neuve ; ProcessStartInfo sans shell/fenêtre ; nettoyage refuse les fichiers non inventoriés. Onze commandes limitées aux entrées autorisées, artefacts, découverte locale, sonde autorisée et postflight précis. Limite : Rupture d'identité détectée et arrêt de la conduite ; pas d'arrêt tiers, installation, source mutée, Maven, Docker, HTTP, app ou base ; pas d'extension de la liste de lecture.

- **G02 — PASS — Intégrité des scripts exécutés et absence de reprise.** item_10 crée la conduite, item_13 corrige son émission avant premier lancement ; item_15 est l'unique invocation. conductorSha256 du résultat égale le script figé ; aucun changement après essai ni retry observé. Limite : Une tentative auxiliaire collab spawn a échoué avant exécution (stderr) ; aucun résultat délégué ni incident collecteur associé.

## Résidu et conservation des preuves

Le postflight indépendant, exécuté sous PS5.1, observe les PID 32700, 34004 et 26528 absents à cet instant. Les deux premiers avaient des handles et des heures de création ; le troisième était connu seulement par la sortie de la fixture. Cette absence ponctuelle ne remplace pas la propriété initiale manquante.

`WR-N01/output/runtime/WR native probe 66a0391d-820d-4d03-b7f1-e3c17b4ce664/temp/JavaLauncher.log` reste présent avec **4 892 octets**, ainsi que la racine et `temp`. Son contenu n'a pas été lu par le relecteur. Le refus de suppression d'un résidu inattendu respecte le protocole ; il ne vaut pas preuve de nettoyage réussi.

Le transcript externe contient ses en-têtes, tandis que les événements JSONL, les sorties séparées, les résultats structurés et les événements natifs portent les observations détaillées. Leur intégrité est vérifiée. Les scripts de conduite n'ont pas changé après leur unique exécution ; leur petite préparation préalable est visible avant lancement.

Aucune relance, réparation, lecture du journal inconnu, inspection/arrêt de processus actuel, modification du candidat, d'une source ou d'un résultat par le relecteur. Les anciens runs demeurent intacts. Modèle/effort effectifs non exposés ; catalogue reconstruit déclaré. Gain général non mesuré.

