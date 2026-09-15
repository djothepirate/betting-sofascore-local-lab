# Revue indépendante — WR-H01 — run-02

## Verdict : BLOCKED — session et collecte incomplètes

La session a atteint la limite de **900 s** sans événement de fin de tour ni `response.md`. Le collecteur termine avec **1**, `turn_completed=false` et `exit_code=null`. Aucun SHA de réponse n'est inventé.

**Le résultat technique disponible est positif et doit être conservé :** le harnais figé a été invoqué une fois, sous PS7 Windows, avec deux itérations corrigées. Il a rendu **0** en **2 726,6811 ms**, tous les marqueurs requis sont présents, puis les postflights ont établi le nettoyage des ressources de cette reproduction. Le README enregistré explique correctement sa portée et l'histoire WO-053. Il reste un artefact du tour interrompu, pas une réponse finale ni une preuve de complétude de collecte.

## Identité

- Candidat `0.1.0-candidate.1`, commit `9e7b777e3be75f582f2198dfa8cca386b43426c2`.
- SHA-256 candidat : `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`.
- SHA-256 réponse : **absent / null**.
- SHA-256 événements : `2895f515191aa47d889dbf436ac17ec74dc94f8018087fb16a41897c0f35c0ae`.
- SHA-256 conduite réellement exécutée : `89fc081c70036da058e261d2e37b2a10ad98394e69c1dbe5df97d593536a0655`.
- SHA-256 README : `62730e1b1371a1c39ea7f5555258e4f0ff78fbacc682ad79b3ab856ca4b55707`.
- Oracle inchangé : `213dc98b419c2c6dad5104347a3e422ecb1516d9149967a15b0a8e06c6c6d437`.
- Gel observé avant lecture : `2026-09-15T22:08:58.806430+00:00`. **29 pièces conformes, dont22 artefacts ;12 entrées intactes.**
- `item_1` prouve la lecture native exacte de tout le candidat. Les22 commandes et les cinq scripts produits ont été examinés.

## Résultats techniques et incidents distincts

| Couche | Résultat | Limite |
|---|---|---|
| Premier conducteur | Code1, CIM `0x80041003`, 584,6146 ms extérieur | Arrêt avant tout lancement du harnais. |
| Unique harnais effectivement lancé | Code0, deux itérations, tous marqueurs | 2 726,6811 ms ; capture PowerShell, aucun Java applicatif. |
| Conducteur de cette exécution | Code0, 3 421,6377 ms au rapport | Retour extérieur 3 609,1594 ms ; budget60s respecté. |
| Commande suivante | `WR_PROMPT_RETURNED`, `item_37`, code0 | Après retour effectif d'`item_36`. |
| Postflight indépendant | Code0, 133,8408 ms | Runtime vide, racineUUID absente, PID36948 absent. |
| Session CLI | Timeout900, durée900,594 s, code non obtenu | La session s'est prolongée après la fin du harnais. |
| Collecteur | Code1 | Tour non terminé et réponse absente ; collecte incomplète. |

Le premier conducteur avait ajouté une lecture CIM du seul processus courant. Son refus survient avant la création d'un enfant. Le message secondaire « Allowed source hashes changed » vient de la comparaison d'une liste avant vide à la liste après ; il ne prouve aucune mutation. Les empreintes initiales et finales restent identiques. Le conducteur suivant emploie le handle possédé et lance pour la première fois le harnais intact ; aucune relance d'un harnais échoué ni modification de délai/source/runtime n'est constatée.

## Critères

- **I01 — PASS — Intégrité du gel et des entrées.** 29 pièces figées dont22 artefacts runtime conformes en taille/SHA ;12 entrées intactes ; manifeste21 artefacts vérifié hors lui-même. Limite : response.md et catalogue.txt absents : aucune empreinte inventée pour une pièce inexistante.

- **I02 — PASS — Lecture intégrale native du candidat exact.** item_1 Get-Content -Raw code0, sortie contenant exactement tout SKILL.md .1 ; candidat et YAML conformes au preflight. Limite : Lecture réelle prouvée, injection automatique CLI non établie.

- **C01 — BLOCKED — Tour terminé, réponse finale et collecte complète.** trace : TimeoutExpired900s, MissingTurnCompleted, MissingOrEmptyResponse ; turn_completed=false, exit_code=null, collector_exit_code=1 ; response.md absent. Limite : README.md existe comme artefact finalisé avant timeout mais ne remplace pas l'événement de fin ou la réponse manquante. Résultat technique ci-dessous conservé.

- **H01 — PASS — Exécution neuve du harnais exact sous PS7 Windows, deux itérations.** item_36 lance Run-WRH01Harness puis Invoke-WRH01Harness ; ProcessStartInfo cible pwsh.exe -NoLogo -NoProfile -NonInteractive -File script autorisé -Iterations2. PS7.6.6 Core/Windows10.0.26200 X64, image enfant7.6.6.500 et handle1496/PID36948 observés ; trois sources exécutables intactes et arborescence conservée. Limite : La table PSVersionTable est celle du conducteur ; image/chemin/version enfant réellement observés, table propre du harnais non imprimée. Premier conducteur bloqué avant lancement, pas de seconde invocation du harnais.

- **H02 — PASS — Marqueurs actuels, code0 immédiat, temps et prompt.** execution.stdout intégral concorde avec item_36 : baseline split, chemin entier, ordre-jar, identité,3/3flags,4/4rejets,2itérations et cas sans espace. WaitForExit puis ExitCode immédiat0 ;2726.6811ms harnais,3609.1594ms extérieur ;item_37 WR_PROMPT_RETURNED après retour. Limite : Code0 technique distinct du code CLI inconnu ; aucune itération historique copiée, aucune fin de session déduite du prompt de commande.

- **H03 — PASS — TEMP/TMP enfant, nettoyage et postflight indépendant.** Conduite définit TEMP/TMP dans le seul ProcessStartInfo à output/runtime vide ; parent inchangé. Racine UUID e0bcc5c7-8317-4591-8221-ef76740ceafe observée puis absente. Marqueurs zéro résidu du harnais ; item_43 postflight séparé observe runtime vide et PID36948 absent. Limite : Les PID internes/codes détaillés des quatre captures ne sont pas imprimés ; preuve via assertions du harnais exact. Douze métadonnées sur13 fichiers vues, aucun JSON brut retenu. Pas d'inventaire mondial ni nettoyage CLI prouvé par ce postflight.

- **H04 — PASS — Défaut de sérialisation et helper exact.** README sections Commande/Capture : Start-Process recompose ArgumentList, chemin canonique avec espaces protégé comme seul argument immédiatement après -jar ; relatif/nonJAR/guillemet/contrôle refusés. Concorde avec helper et captures du harnais. Limite : Transmission des trois flags prouvée, pas comportement du retry JDK ; aucune commande shell libre.

- **H05 — PASS — Capture PowerShell sans Java/application/base/réseau.** README distingue captureur PowerShell, fauxJAR4octets, absenceJava25/Spring/J6/PS5.1 ; scripts exacts et22commandes ne lancent niJava/Maven/Pester/Docker/HTTP/app/base. Limite : Pas de qualification de WR-N01, CIWindows2022, Eclipse ou stabilité générale.

- **H06 — PASS — WO053 : cause non établie et portée des deux essais.** README confronte run33972681012 NOT_ESTABLISHED à deux Windows du run33973934317/5f3dea2b72c0751174ea389891397e11bd9e08ae ; instrumentation et workflow_dispatch de branche distincts du merge PR29 ; aucun diagnostic rétroactif readiness/signal/cleanup. Limite : Synthèse fondée sur les trois rapports autorisés, pas nouvelle consultation CI distante.

- **H07 — PASS — Linux en échec et durcissement distinct.** README conserve échecLinux avanttests/scan historique sans fauxpositif inventé ; run global nonvert ; WR-H03 durcit observation/propagation/erreur primaire avec ses preuves, sans établir causeCTRL_BREAK. Limite : Anciennes réussitesWindows ne qualifient pas le diff de durcissement ni les octets courants.

- **H08 — PASS — Écart historique WO044 Testcontainers.** README rappelle commande Maven historique sansskipITs, Testcontainers isolés démarrés par erreur puis nettoyage exact ; offline n'exclut pas bases. Limite : Aucun Docker ni Maven lancé dans cette reproduction.

- **G01 — PASS — Périmètre, environnement, UTF8 et sécurité de conduite.** Cinq scripts produits intégralement examinés ; chemins canoniques/préfixe avec séparateur/reparse contrôlés ; écritures runtime-artifacts et temporaire uniquement ; ProcessStartInfo sansshell/fenêtre, captures Hidden, UTF8strict ; aucune suppression externe ni variable persistante. Limite : Le chemin d'arrêt forcé nécessitant CIM n'a pas été sollicité et n'est pas qualifié ; aucun arrêt effectué par la conduite.

- **G02 — PASS — Incident initial et absence de faux vert.** item_25 : préflightCIM courant refusé0x80041003, commandeharnaisNOT_STARTED, code1 ; item_30 confirme ce seul accès. Erreur secondaire de comparaison sourceBefore=[]/sourceAfter n'est pas une mutation ; sources initiales et finales conformes. Incident conservé, nouveau conducteur emploie handle avant première invocation réelle. Limite : Adaptation d'une observation auxiliaire avant lancement, ni affaiblissement du harnais figé ni relance d'un harnais échoué. README honnête ; absence de réponse finale empêche le verdict complet.

- **G03 — PASS — Budgets natifs conservés et fin de session distincte.** Harnais2726.6811ms, conducteur3421.6377ms au rapport, retourextérieur3609.1594ms, outil3.796855s ; limites60s/10s/5s nonaugmentées. Postflight133.8408ms séparé. Limite : TimeoutCLI900.594s après résultats métier : ne constitue pas un dépassement de60s du harnais et ne réfute pas sa sortie déjà prouvée.

## Nettoyage et limites de l'observation

Le processus harnais détenu était **PID36948, handle1496**, créé à `22:03:58.8528744Z`, image réellement observée `C:\Program Files\PowerShell\7\pwsh.exe`, version7.6.6.500. Sa sortie est confirmée avant libération. La racine temporaire `WO044 Java JAR argument boundary e0bcc5c7-8317-4591-8221-ef76740ceafe` a été vue puis trouvée absente. Le postflight séparé à `22:05:09.5243423Z` retrouve `output/runtime/` vide et aucun PID36948.

Les quatre processus de capture sont vérifiés par le harnais exact ; leurs PID individuels ne sont pas imprimés. Leurs JSON temporaires ont été supprimés normalement. L'observateur n'a vu que12 des13 fichiers créés et le README le dit. Aucun inventaire CIM global, arrêt forcé, effacement par la conduite ou nettoyage tiers n'a été effectué. Les zéros du harnais ont cette portée précise.

La trace de collecte dit `process_cleanup=not_verified` après le timeoutCLI. Cela ne démontre pas un résidu du harnais déjà terminé ; cela ne démontre pas non plus le nettoyage de la session CLI. L'observation hôte post-session annoncée par coordination est une pièce séparée et n'est pas utilisée pour améliorer le verdict ni compléter rétroactivement le gel.

Aucune réponse reconstruite, relance, correction des scripts évalués, modification d'entrée/candidat/résultat ou test applicatif par le relecteur. Les anciens runs demeurent intacts. Modèle/effort effectifs non exposés ; gain général non mesuré.

