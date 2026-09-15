# Revue indépendante WR-H01 — run-03

## Verdict : BLOCKED avant lancement du harnais

Conduite bloquée avant lancement du harnais : Accès refusé, zéro lancement, code natif absent. Le runtime vide et le retour du prompt sont réellement observés ; une réponse finale explicite est produite dans une session normale de 281.875 s. Aucune reproduction de frontière d'argument n'est démontrée. Omissions secondaires de comparaison historique et écart de stratégie de lecture consignés séparément.

La réponse finale décrit correctement le blocage. Ce verdict ne concerne pas un échec démontré du harnais lui-même. Il n'est pas causé par un timeout ou une panne du collecteur.

## Résultat technique et finalisation

| Couche | Observation |
|---|---|
| Harnais WO044 | Aucun lancement ; code, transcript, identité et durée absents |
| Conduite | `Accès refusé`, `BLOCKED`, 194.609 ms, code outil 0 |
| Retour | `WR_PROMPT_RETURNED`, événement item_17, 22:44:24.4114165Z |
| Postflight | Événement item_22 ; runtime existant, zéro entrée, 85.8657 ms ; aucune suppression/aucun arrêt |
| Session CLI | Code 0, turn.completed, 281.875 s |
| Collecteur | Code 0, gel complet de 18 pièces |

Le script n'a enregistré ni ligne fautive ni pile d'appel. La requête CIM sur le conducteur précède l'affectation de `result.runtime` et pourrait produire ce symptôme ; la trace ne suffit pas à l'attribuer précisément. Le diagnostic postérieur dans un autre contexte annoncé par la coordination reste séparé et n'améliore pas ce verdict.

## Critères

### I01 — PASS — Gel, candidat et corpus exacts

18 pièces figées et 13 entrées du préflight conformes en SHA-256 et taille. Candidat .1, oracle et complément opératoire inchangés ; trois sources exécutables dans leur arborescence. Entrées décodables en UTF-8 strict, sans NUL.

Limite : Le commit source métier 6648dd4, le candidat 9e7b777 et la préparation 4a83e12 restent distincts.

### I02 — PASS — Lecture intégrale réelle du candidat

item_1 : Get-Content -Raw du SKILL.md, code 0 ; aggregated_output contient exactement le corps complet du candidat.

Limite : Lecture par outil prouvée, pas injection automatique du CLI ; catalogue reconstruit après exécution.

### I03 — PASS — Actions autorisées et absence de mutation hors portée

12 commandes natives examinées, toutes code outil 0. Lectures autorisées, création des seules racines output, deux scripts ajoutés sous runtime-artifacts et leurs preuves. Aucun appel Java, Maven, Docker, réseau, application, base, oracle ou ancien résultat ; aucune source modifiée.

Limite : L'inventaire de processus auxiliaire présent dans le script n'a pas été atteint ; aucun arrêt ni suppression effectué.

### H01 — BLOCKED — Exécution neuve du harnais exact sous PS7 Windows avec deux itérations

item_10 observe PS7.6.6 Core / Windows 10.0.26200 et résout pwsh7.6.6.0. Le conducteur prévoit les trois fichiers exacts et -Iterations 2, mais item_16 produit BLOCKED avant result.runtime, result.command et EXECUTION_START. Aucun processus natif du harnais créé.

Limite : Le PS observé est celui de l'outil ; version/architecture/PSHOME et PID du futur enfant sont absents. La disponibilité de PS7 ne suffit pas à reproduire.

### H02 — BLOCKED — Transcript actuel, arguments, code immédiat, temps et retour

Aucun marqueur actuel WO044_PRE_FIX_SPLIT_ARGUMENT_REPRODUCED, WO044_QUALIFIED_ITERATIONS=2, capture avec/sans espace, identité, trois garde-fous ou quatre refus. Aucun code ni durée du harnais. Le conducteur enregistre 194.609 ms, erreur Accès refusé, natif null et outil 0. item_17 observe ensuite WR_PROMPT_RETURNED à 22:44:24.4114165Z.

Limite : Les marqueurs figurant dans les sources et rapports lus ne sont pas des résultats actuels. Les codes CLI/collecteur 0 et la fin normale ne qualifient pas le harnais.

### H03 — BLOCKED — Confinement enfant, nettoyage et postflight indépendant

Le script prépare TEMP/TMP exclusivement dans ProcessStartInfo, sous output/runtime, UseShellExecute=false/CreateNoWindow=true, chemins canoniques avec séparateur et contrôle des ancêtres sans reparse. Le lancement n'est pas atteint. item_22 exécute le postflight séparé : runtime existe, zéro entrée, 85.8657 ms, aucune suppression.

Limite : Runtime vide réellement observé. ownedNativeProcessCount=0 découle de l'absence de lancement et n'est pas un inventaire indépendant/global. Aucun cleanup effectif d'un harnais lancé ni capture supprimée n'est qualifié ; la limite des captures jetables est consignée.

### H04 — FAIL — Explication complète de la frontière d'arguments

Réponse 29 explique la recomposition Start-Process ArgumentList, les guillemets du chemin canonique, sa position après -jar, identité et trois arguments JDK. Les quatre classes refusées (relatif, extension non-JAR, guillemet, contrôle) ne sont pas restituées.

Limite : Omission de contenu ; aucune commande shell libre proposée. Le helper exact lu par item_10 contient ces contrôles, mais sa présence dans une source n'est pas une explication par la réponse.

### H05 — PASS — Portée de la capture PowerShell

Réponse 3, 23 et 31 distingue absence de reproduction et portée conditionnelle du harnais PowerShell, sans exécution Java ou JAR applicatif. Aucun Maven/Docker observé.

Limite : Aucune qualification Java 25/Spring, de la sonde WR-N01 ou de l'application déduite.

### H06 — FAIL — Histoire WO053 et provenance exacte

Réponse 33–37 conserve NOT_ESTABLISHED, deux essais Windows sans reproduction, différences instrumentation/workflow_dispatch de branche/merge synthétique PR et absence de stabilité générale. Elle omet les identifiants 33972681012 et 33973934317 ainsi que le SHA 5f3dea2b72c0751174ea389891397e11bd9e08ae.

Limite : Omission de provenance obligatoire, pas diagnostic causal inverse. Aucun lien causal inventé avec le refus d'accès actuel.

### H07 — PASS — Linux et portée propre du durcissement WR-H03

Réponse 33 : Linux échoué au scan avant tests, run global non vert ; aucune alerte déclarée fausse. Réponse 35 : contre-épreuve C# sans API Win32, pas reproduction CI, anciens succès ne qualifiant pas le diff ultérieur.

Limite : Les preuves historiques restent historiques, aucune CI rejouée.

### H08 — FAIL — Portée exacte de l'écart WO044

Réponse 31 mentionne Testcontainers et une commande Maven historique distincte. Elle omet que l'absence accidentelle de -DskipITs a déclenché les conteneurs isolés et que leur nettoyage exact a été prouvé.

Limite : Omission de contenu. Aucune exécution Docker actuelle ni masquage en succès d'un écart actuel observé.

### F01 — FAIL — Lecture ciblée demandée par le complément opératoire

item_4 lit intégralement le module de 176724 octets avec les scripts ; item_6/7/8 répètent des entrées et les trois rapports déjà lus par item_2/3, avant une recherche ciblée item_10.

Limite : Écart à l'organisation demandée (éviter intégrales et relectures répétées), sans incidence causale prouvée sur le refus d'accès. Aucun nouveau runtime ni sous-agent.

### F02 — PASS — Finalisation autonome après impossibilité

Un conducteur, zéro lancement du harnais, aucune relance. JSON et scripts conservés ; EXECUTION_END, EVIDENCE_READY, POSTFLIGHT_COMPLETE et CONCLUSION_READY présents, EXECUTION_START explicitement absent. Réponse finale autonome présente, sans README ni manifeste doublon ; session 281.875 s sous la cible six minutes.

Limite : Les noms de phases ne prouvent pas une exécution native. Les temps monotones de conduite/postflight et la réception des événements appartiennent à des horloges/portées distinctes.

### F03 — PASS — Codes et incidents correctement séparés

Réponse 21–25 : code outil 0 dû au catch ne remplace pas le résultat BLOCKED. Collecteur 0, CLI 0, turn_completed=true, collection_completed=true, timed_out=false ; aucun incident de collecte observé.

Limite : Le catch conserve seulement Exception.Message : ligne et pile absentes. Les avertissements icônes/snapshot PowerShell du stderr n'empêchent pas la fin normale et ne sont pas la preuve de la cause native.

## Empreintes

- Candidat : `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6` ; commit : `9e7b777e3be75f582f2198dfa8cca386b43426c2`.
- YAML : `3038015980952bc18b21f45f0d4af514b35a8841394e4b80824986b7e30ccd9f`.
- Réponse : `14c84a8fbfb11d13b090b4e17070615e2b876b6bd6df6174594589e813f80771`.
- Événements : `98764b1e5016e61c596c19d65458f3b3ebdaa46d5a624697481ed531a3a4320e`.
- Trace : `2a188856a6c4be753cdeef17f785ca8a265bddae4a0c6e5831770ad54ec5873f`.
- Oracle : `213dc98b419c2c6dad5104347a3e422ecb1516d9149967a15b0a8e06c6c6d437`.
- Protocole : `b4748a356b881a8574a78156c0ac00c90f493874efe8303d40a7224c12d91a52`.
- Complément opératoire : `59bae0779270ea670730b3c60425623ba81bee52d899edcb4dd2c2ddab52f4a6`.
- Timeline : `f3b6423b65c16e4ff33accf5c5d4f90c0b3aba420fa231ecc9df3aaf1e406d9a`.
- Conducteur exécuté : `bd86fbc9b765c9a66c9ccc9d9d777445f0aa6b2a3db5ae64124e2db38fd680e6`.
- Résultat : `292b281c2d7e59149690048b48722e6f38caf5778f4384b36729da7ebb418628`.
- Postflight : `537f5b882de6467cab57db692a9e8276d002f3eb30a2719df580140f6c26c701`.

Gel `2026-09-15T22:46:15.269938+00:00` : 18/18 fichiers conformes. Entrées du préflight : 13/13 conformes. Lecture initiale des résultats après gel. Les 41 événements horodatés et les 12 commandes terminées ont été examinés ; aucune mesure de raisonnement du modèle n'est déduite des intervalles.

## Limites complémentaires

- La requête CIM ciblant le conducteur est située avant result.runtime ; son refus serait compatible avec les absences observées. Le message seul ne localise pas la ligne. Aucun diagnostic hôte ultérieur n'est intégré rétroactivement au gel ni utilisé pour affirmer la cause exacte.
- Les valeurs de contexte et de nombre de lancements du script Complete sont assemblées après observation ; les événements item_10/16/17 les corroborent. Le zéro de ressources n'est pas un inventaire général du poste.
- Le conducteur contient un seuil 55 s puis des attentes de cleanup ; préflight, CIM, collecte asynchrone et plusieurs cleanup n'ont pas de superviseur global propre garantissant toute branche sous 60 s. Aucune de ces branches n'a été exercée et aucune preuve de respect général des délais n'en découle.
- Les chemins de liens de la réponse sont relatifs à la copie du cas ; les artefacts existent et sont figés. Aucun gain général de temps/qualité mesuré.
- Model et effort effectifs non exposés ; catalogue reconstruit, pas capture de transmission.

Aucun faux vert ni décision technique inverse observé. H04, H06 et H08 sont des omissions de contenu distinctes du blocage technique. F01 est un écart d'organisation, sans causalité établie avec ce blocage. Aucun modèle, probe, test applicatif, correction ou modification de source/candidat/oracle/ancienne revue par le relecteur.

