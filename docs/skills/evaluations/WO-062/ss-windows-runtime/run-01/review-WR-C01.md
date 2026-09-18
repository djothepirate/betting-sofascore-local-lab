# Revue indépendante — WR-C01

**Verdict : PASS**, limité au diagnostic de cette contre-épreuve synthétique. Aucun critère obligatoire manquant identifié ; aucune formulation ou mise en page exacte imposée.

## Identité et intégrité

- Candidat `ss-windows-runtime` `0.1.0-candidate.1` ; SHA-256 `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`.
- Réponse SHA-256 `a6dffb6ac38fdc857afca5b6e8604249858cf804f1820db8bb1089a37a24929f`.
- Journal natif SHA-256 `ff51d8ed9017ff42a6a9f73123e76fc66f1e9aae7bd8e9c9f0c9edc224f8c273`.
- Révision de préparation `9e7b777e3be75f582f2198dfa8cca386b43426c2` ; sources métier `6648dd423e556b5248b8793a539ae85a7680f9bc`.
- Gel présent avant lecture de la réponse et de l'oracle : neuf fichiers figés, dix entrées et sept sources autorisées conformes. Les sept pièces préparatoires et leur manifeste sont également conformes.
- `item_1` démontre la lecture intégrale du candidat par outil avant analyse. 9 commandes de lecture/recherche observées, toutes dans l'allowlist. Aucun oracle ni résumé de conception fourni à l'évalué.

## Critères obligatoires

| Critère | Résultat | Preuve / limite |
| --- | --- | --- |
| trace-freeze | PASS | Neuf fichiers figés recalculés conformes à freeze.json ; dix entrées conformes au préflight et sept sources conformes au manifeste initial ; journal brut conforme à son empreinte. Le gel local établit l'identité des octets, pas un résultat applicatif. |
| candidate-load | PASS | native-events.json item_1 : lecture Get-Content -LiteralPath '.agents/skills/ss-windows-runtime/SKILL.md' -Raw, code 0 ; sortie intégrale exactement équivalente au candidat après normalisation CRLF/LF et blanc final. Lecture par outil avant analyse ; aucune injection automatique du corps CLI revendiquée. |
| independence | PASS | Oracle, inventaire, plan et historique de conception absents de la demande et des sources métier. Aucun événement de lecture hors allowlist ; demandes/empreintes figées conservées. Catalogue reconstruit après exécution, sans preuve d'injection du catalogue ; cas métier explicitement routé. |
| scope-and-no-mutation | PASS | Les commandes observées sont uniquement des lectures/recherches locales ; aucune sonde, Maven, Docker, application, DB, réseau, arrêt de processus ou mutation. Les suggestions synthétiques ne sont pas exécutées. Aucune ressource runtime créée, donc aucune suppression ni postflight artificiel requis dans ces cas de lecture seule. |
| source-and-environment-limits | PASS | Les réponses distinguent données synthétiques, sources versionnées et rapports historiques ; SHA source déclaré distinct d'un SHA réellement exécuté ; runtimes, outils, commandes et valeurs manquantes exposés. Les lectures natives utilisent pwsh.exe dans le journal, sans transformer l'analyse en qualification PS5.1 de l'application. |
| targeted-data-and-verdict | PASS | Lectures limitées aux fichiers autorisés, sans dump d'environnement actuel ni commandes de processus tiers. Aucune preuve manquante remplacée par une affirmation de réussite ni gain général revendiqué. Les codes natifs des dossiers et les codes des commandes de lecture sont de nature distincte. |
| exit-masked | PASS | response.md:10, 16-30 : natif 1 en résolution POM parent, zéro test ; wrapper 0 et marqueur PASS invalides ; défaillance avant tests séparée d'une régression applicative ; script courant non accusé. Cause précise de résolution non établie, ce qui évite un diagnostic d'environnement trop spécifique. |
| late-overwrite | PASS | response.md:11, 32-50 : Java 23, cmd 0 puis LASTEXITCODE écrasé ; capture immédiate après &, ou Process.ExitCode après sortie avec Start-Process ; code wrapper final inconnu. Chemin avec espaces présenté comme point à examiner, pas défaut de quoting prouvé. |
| wrong-runtime | PASS | response.md:12, 52-64 : succès PS7/Linux/WSL conservé dans son contexte ; Windows PowerShell 5.1 non qualifié ; runtime cible et prochaine preuve explicités. Aucune sonde de remplacement lancée. |
| timeout-no-postflight | PASS | response.md:13, 66-83 : expiration 1500 ms, code extérieur et prompt distingués de terminaison/cleanup ; identité et postflight absents, NOT_ESTABLISHED ; aucun zéro résidu ni enfant survivant certain inventé. Différencie présence d'un marqueur et preuve complète du retour, de READY et du nettoyage. |
| next-bounded-verification | PASS | response.md:30, 50, 64, 83 : collecte ciblée de preuves source/code/identité, alternatives de capture uniquement futures ; aucun transcript exécuté. Les recommandations n'autorisent pas Maven ou Docker pendant la revue. |
| historical-and-wrapper-scope | PASS | response.md:85-97 : wrapper relaie le code de l'enfant, script auxiliaire hors corpus non qualifié ; harnais WO044 PS7 sans Java et écart Testcontainers historiques distingués du cas courant. Versions projet déclarées ne sont pas attribuées aux exécutables des dossiers. |

## Portée

Les références `response.md:<ligne>` désignent l'original sous `frozen/WR-C01`. Le PASS de revue ne transforme aucun échec natif, donnée synthétique, succès historique ou constat WSL en qualification actuelle de l'application Windows. Aucun test applicatif ni sonde native n'a été lancé par le relecteur.

Le modèle et l'effort effectifs ne sont pas exposés. Le catalogue est reconstruit après exécution et ne prouve pas une injection automatique CLI ; la lecture explicite du candidat exact est, elle, établie par le journal.

Aucune source, candidat ou réponse n'a été modifié. Aucun arrêt de processus, nettoyage de tiers, changement d'environnement, Docker, application ou réseau exécuté. Gain global de temps ou de qualité : **non mesuré**.

