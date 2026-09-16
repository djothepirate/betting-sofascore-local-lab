# WO-062 — correction Java et requalification C4 du 16 septembre 2026

- **État :** qualification C encore incomplète ; dix nouvelles sessions terminées et revues.
- **Java :** `0.1.0-candidate.3`, correction ciblée puis huit cas réexécutés.
- **Windows :** `0.1.0-candidate.1` inchangé, WR-H01 puis WR-N01 dans deux sessions nouvelles.
- **Statuts :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.
- **Worktree :** `.tmp/wo062-skills-lot2`, branche `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`.

## Résultat courant

| Demande | Résultat C4 | Qualification |
| --- | --- | --- |
| Corriger Java avant recette | `.3` versionné avant les essais ; les deux omissions ciblées sont corrigées dans JM-N01 | **FAIL** : autre omission obligatoire, maintien du blocage de `sofascore-live-test` |
| WR-H01 : exécution, preuves, postflight, conclusion et réponse dans une même session | Chaîne complète réussie en **428,250 s**, harnais en **2 686,1568 ms**, omissions historiques traitées | **PASS substantiel et natif** ; **FAIL de conformité C4** pour lectures intégrales et répétées interdites par son complément |
| WR-N01 : failure et sleep avec propriété et nettoyage | Une tentative du conducteur ; erreur `Get-FileHash` avant toute JVM | **BLOCKED** : ni failure ni sleep exécuté ; aucun code ou nettoyage Java qualifié |

Les qualifications courantes sont **Java : 7 PASS / 1 FAIL** et
**Windows : 6 PASS / 1 FAIL / 1 BLOCKED**. Le FAIL global WR-H01 ne signifie pas que
le harnais a échoué : sa [revue distingue les dimensions](../skills/evaluations/WO-062/ss-windows-runtime/run-04/review-WR-H01.json).
Le critère d'organisation provient du complément opératoire C4. Les huit critères
substantiels de l'oracle original et la chaîne expressément demandée sont satisfaits.
La cible de six minutes était formulée « viser » et reste souple.

## Java .3 — correction réussie, omission distincte restante

Le [candidat .3](../skills/local-lab/ss-java-module/SKILL.md) change uniquement sa version
et deux passages : restituer explicitement l'arrêt terminal après perte du navigateur
ou contexte live, puis nommer la cible du garde textuel `com.microsoft.playwright`
dans `src/main` en distinguant les interfaces internes. La description de sélection
et les métadonnées d'interface restent identiques. Le [dossier de révision](../skills/evaluations/WO-062/ss-java-module/revision-03/revision.json)
conserve le précédent état et les empreintes ; son état `NOT_RUN` décrit la préparation.

Les huit cas figés sont exécutés sur les nouveaux octets ; aucun PASS de `.2` n'est
transféré. La [revue JM-N01](../skills/evaluations/WO-062/ss-java-module/run-04/review-JM-N01.md)
confirme les deux corrections, la distinction date civile/instant et **26 critères PASS**.
La [réponse figée](../skills/evaluations/WO-062/ss-java-module/run-04/cases/JM-N01/response.md)
omet toutefois le maintien du profil `sofascore-live-test` bloqué. Le mécanisme
`alwaysFail` du POM a été lu dans `item_19` ; le manque concerne sa restitution.
Cette omission ne prouve ni défaut de l'application, ni activation du profil, ni
recommandation contraire. Elle suffit au FAIL selon l'oracle original inchangé.

Le cas historique, les deux contre-épreuves et les quatre sélections sont PASS après
revue indépendante. Une correction ultérieure devra porter sur la restitution des
profils effectivement bloqués, sans réduire les autres exigences. La qualification
reste limitée à ces réponses et ne mesure pas un gain général de fiabilité.

## WR-H01 — la chaîne complète est observée

La [réponse nouvelle](../skills/evaluations/WO-062/ss-windows-runtime/run-04/cases/WR-H01/response.md),
les [observations](../skills/evaluations/WO-062/ss-windows-runtime/run-04/cases/WR-H01/runtime-artifacts/execution.json)
et la [revue indépendante](../skills/evaluations/WO-062/ss-windows-runtime/run-04/review-WR-H01.md)
appartiennent à cette seule session. Le harnais WO-044 exact est lancé une fois,
avec deux itérations, sous PowerShell 7.6.6 Core x64 Windows. Le code natif est **0**,
recopié immédiatement après sortie. Le conducteur dure **2 872,3827 ms**, sous sa borne
de 60 secondes. Objet Process, handle 2132, PID 8500, création et image sont conservés.

| Phase | Heure UTC du 15 septembre | Portée |
| --- | --- | --- |
| Lancement effectivement confirmé | 23:33:07.554 | Après `Process.Start` réussi |
| Sortie native | 23:33:10.225 | Harnais : 2 686,1568 ms, code 0 |
| Retour de commande distinct | 23:33:15.380 | Nouvelle commande après le retour de l'outil |
| Postflight indépendant | 23:33:48.145 | Runtime vide, PID 8500 absent, 83,3556 ms |
| Conclusion écrite | 23:35:55.809 | Diagnostic conservé par la session |
| Réponse finale reçue | 23:37:09.097 | 426,031 s depuis le début de mesure CLI |
| Session terminée | 23:37:11 environ | Trace de coordination : 428,250 s, CLI et collecteur 0 |

Le transcript contient le split initial, deux itérations corrigées, le cas sans espace,
l'instance, les trois flags, l'ordre du JAR et les quatre refus. Le harnais établit
le nettoyage de ses ressources détenues ; le postflight extérieur contrôle séparément
le dossier runtime et le PID surveillé. Aucun arrêt forcé n'est nécessaire. Cette
reproduction d'arguments n'est pas un lancement de l'application Java.

Les omissions historiques sont traitées : quatre classes de chemins refusés, identifiants
et SHA exacts des essais WO-053, échec Linux avant tests, différence entre instrumentation
et cause établie, absence accidentelle de `-DskipITs` et nettoyage ciblé des conteneurs
dans WO-044. Les preuves anciennes ne sont pas réécrites.

F01 reste en échec : lecture intégrale du module de **176 724 octets**, puis relectures
du complément, du protocole et des scripts, malgré les lignes 10–12 du
[complément C4](../skills/evaluations/WO-062/c-requalification-04/windows-session-protocol.md).
La cible de six minutes est dépassée ; le watchdog de quinze minutes ne l'est pas.
Les événements situent environ trois minutes avant le harnais et quatre minutes après
sa sortie, sans attribuer ces intervalles à une cause interne unique. Les heures de
réception ne mesurent pas le temps de raisonnement du service.

Cette session démontre qu'une opération native de moins de trois secondes peut aboutir
à une réponse finale normale. Le succès natif et la conclusion ne sont plus assemblés
depuis deux essais différents. L'écart d'organisation reste visible séparément.

## WR-N01 — blocage du conducteur avant les deux modes

La [réponse figée](../skills/evaluations/WO-062/ss-windows-runtime/run-04/cases/WR-N01/response.md)
et les [observations](../skills/evaluations/WO-062/ss-windows-runtime/run-04/cases/WR-N01/runtime-artifacts/observations.json)
localisent l'échec au préflight, ligne 141 du conducteur :
`System.Management.Automation.CommandNotFoundException`, `Get-FileHash` introuvable.
La pile et le texte exact sont conservés. L'erreur survient dans la construction de
l'événement destiné à enregistrer le runtime, avant la découverte Java et la création
de la racine temporaire.

| Observation | Résultat |
| --- | --- |
| Processus réellement lancé | Conducteur `powershell.exe`, PID 12548, handle 2260, image et création conservées |
| Code conducteur / outil | 1 / 1 ; aucun code natif Java |
| Durée interne / observée extérieurement | 225,287 ms / 798,1418 ms, sous 45 000 ms |
| Retour et postflight | Commande distincte ; PID conducteur absent et racine jamais créée |
| Modes failure / sleep | Non lancés |
| Session de modèle | 531,828 s, réponse finale, CLI 0 et collecteur 0, sans expiration |

La [revue WR-N01](../skills/evaluations/WO-062/ss-windows-runtime/run-04/review-WR-N01.md)
maintient **BLOCKED**. La provenance du conducteur n'est pas celle d'une JVM. La version
5.1, l'édition Desktop, le CLR et l'architecture n'ont pas été enregistrés dans l'événement
runtime défaillant ; le nom `powershell.exe` ne les certifie pas. L'absence de ressources
créées ne vaut pas preuve de nettoyage après expiration. La revue conserve aussi les
relectures contraires au complément et le dépassement de la cible souple de six minutes.

La disponibilité de `Get-FileHash` n'a pas été contrôlée avant cette dépendance du
conducteur. La cause de son indisponibilité — module, environnement hérité ou autre —
**reste non établie**. Aucun changement de PATH, PSModulePath, installation ou
assouplissement du sandbox n'est effectué.

Avant une prochaine recette, il faut séparer l'enregistrement initial du runtime de
toute collecte d'empreinte et vérifier les dépendances du conducteur dans le processus
concerné. Une collecte SHA-256 via les API .NET disponibles est une correction possible,
à vérifier d'abord sans Java ni ressources runtime. Il faut aussi revoir le nettoyage
si l'expiration précède la publication de l'identité Java : la possession du conducteur
ne se substitue pas à celle du processus à arrêter. Ces actions préparatoires ne sont
pas des branches qualifiées par la présente tentative interrompue.

La [preuve antérieure de propriété](../skills/evaluations/WO-062/c-targeted-reprise/ownership-proof.json)
reste valide pour son propre essai. Elle n'est pas transmise comme résultat au contexte
C4, ne qualifie aucun mode manquant et n'attribue pas la propriété du résidu
`JavaLauncher.log` de run-02. Ce fichier historique reste conservé.

## Recette, indépendance et provenance

L'[autorisation consignée](../skills/evaluations/WO-062/c-requalification-04/authorization.json)
applique la demande actuelle au service configuré et au périmètre interne déjà autorisé.
La recette est bornée à dix sessions, une tentative par cas, sans relance automatique.
Java utilise au plus deux sessions simultanées ; les deux sessions Windows se succèdent.
Oracles, anciennes réponses et résultats de propriété restent exclus des contextes
évalués. Les compléments Windows ne donnent ni valeurs attendues ni sorties à recopier.

| Cas | Version candidate | Verdict de revue | Durée de session | Commandes terminées |
| --- | --- | --- | ---: | ---: |
| JM-H01 | .3 | PASS | 301,156 s | 14 |
| JM-N01 | .3 | FAIL | 550,469 s | 26 |
| JM-C01 | .3 | PASS | 328,016 s | 19 |
| JM-C02 | .3 | PASS | 526,625 s | 28 |
| JM-S01 | .3 | PASS | 25,062 s | 1 |
| JM-S02 | .3 | PASS | 52,828 s | 2 |
| JM-S03 | .3 | PASS | 25,875 s | 1 |
| JM-S04 | .3 | PASS | 29,578 s | 0 |
| WR-H01 | .1 | FAIL de conformité C4 | 428,250 s | 15 |
| WR-N01 | .1 | BLOCKED | 531,828 s | 10 |

Les dix sessions disposent d'une réponse finale, de `turn.completed` et d'une collecte
complète, sans expiration. Les zéros CLI/collecteur ne remplacent pas les verdicts
substantiels ou les codes des commandes internes. Les réponses sont figées avant revue.
La sélection et la lecture du candidat sont établies par outil lorsque pertinentes ;
S04 peut réussir en excluant le skill sans lire son corps. Le catalogue reconstruit
après exécution n'est pas une capture de la requête transmise.

| Référence | Contenu et antériorité |
| --- | --- |
| `cf36a0cea4788402a5da391867c58ef1a479c942` | Candidat Java .3 et correction, avant tout essai .3 |
| `79d0f99c393a06d4d2e81749a55fb3bfbd222cc3` | Complément Windows ; HEAD déclaré dans les préflights |
| `2691f32` | Dix contextes, revue statique Java, tests de chronométrie et accès Windows, avant tous les essais C4 |
| `cb67d18` | Revue du protocole Windows, avant ses deux sessions |

Le [dossier préparatoire](../skills/evaluations/WO-062/c-requalification-04/preparation/artifact-manifest.json)
et les manifestes des runs relient les octets exacts. Modèle et effort utilisent les
valeurs configurées, sans override ; leurs valeurs effectives ne sont pas exposées.
Les candidats ne sont pas réécrits après leurs évaluations.

Les quatre runs conservent **38 tentatives C** : 25 Java toutes versions et 13 Windows,
avec **37 réponses finales et une expiration historique**. Le cumul historique est
**25 PASS / 7 FAIL / 6 BLOCKED** ; les seize cas courants sont **13 PASS / 2 FAIL / 1 BLOCKED**.
Les runs 01 à 03, oracles, cas et fixtures restent intacts. Les
[résultats Java courants](../skills/evaluations/WO-062/ss-java-module/run-04/current-results.json)
reprennent seulement les huit cas .3 de run-04 ; les
[résultats Windows courants](../skills/evaluations/WO-062/ss-windows-runtime/run-04/current-results.json)
conservent six PASS C/S de run-01 et les nouvelles tentatives H/N de run-04.

## Contrôles de livraison et limites

- Validation structurelle du candidat Java : PASS. Le premier appel échoue faute de module
  Python YAML ; une dépendance temporaire déjà disponible est ensuite utilisée et le contrôle
  passe, sans installation ou changement persistant.
- Revue statique Java et revue indépendante du complément Windows : PASS avant les essais concernés.
- Cinq tests du collecteur chronométré : PASS ; quatre tests synthétiques de publication : PASS,
  réexécutés après la correction de compatibilité du publieur. Aucun modèle, harnais Windows
  ou JVM de qualification n'est lancé par ces tests.
- [Publieur et revues initiales](../skills/evaluations/WO-062/c-requalification-04/publication-support/README.md) :
  trois défauts corrigés puis revue favorable. Les gardes vérifient run/skill/version/gel,
  reprise exacte des six résultats Windows historiques et totaux. La publication prépare tous
  les fichiers et remplace atomiquement la qualification par skill ; elle ne promet pas une
  transaction entre les deux skills ni un retour arrière après arrêt brutal.
- Le premier contrôle réel détecte ensuite le champ de version absent du schéma run-01 ;
  le publieur utilise désormais son préflight authentifié et compare les identités. Une
  [revue ciblée](../skills/evaluations/WO-062/c-requalification-04/publication-schema-fix/review.md)
  valide cette correction. L'auteur de la revue WR-H01 complète aussi son champ `skill`.
  Ces refus ont lieu avant publication, sans modifier réponse ou verdict. Le contrôle complet
  puis la publication réussissent ; leurs [preuves](../skills/evaluations/WO-062/c-requalification-04/publication-schema-fix/artifact-manifest.json)
  conservent la version effectivement utilisée.
- Les [contrôles de livraison](WO062-C4-CHECKS-20260916.json) vérifient manifestes, empreintes,
  revues, réponses, provenance, liens, recherche de secrets et conservation. La vérification
  des octets Git est exécutée séparément après commit. Les [vérificateurs et la revue
  documentaire](../skills/evaluations/WO-062/c-requalification-04/delivery-audit/README.md)
  sont conservés séparément des entrées des sessions.
- Code applicatif, ADR, scripts applicatifs, CI, POM, preuves A/B, huit skills personnels
  antérieurs et leurs seize fichiers/dates restent identiques. La preuve Maven du WO terminée
  le 15 septembre à **15:58:40 UTC** est réutilisée explicitement : 2 440 tests déclarés,
  2 435 exécutés, cinq ignorés, zéro échec/erreur. Aucun nouveau build ou test d'intégration
  n'est revendiqué ; `Verify-Local` avait exécuté `mvnw.cmd -DskipITs clean verify`, puis le
  vérificateur des rapports standards.

## Ce qui reste ouvert

1. **Java :** restituer le profil live bloqué dans JM-N01 ; les deux corrections demandées
   sont acquises, mais ne dispensent pas du dernier critère omis.
2. **WR-H01 :** réussite native et historique acquise pour cette tentative ; l'écart des
   lectures reste documenté, ainsi que le dépassement de la cible souple.
3. **WR-N01 :** rendre le préflight compatible et observable, puis obtenir réellement les
   deux modes avec nouvelles identités, délais et nettoyage prouvés.

La recette C4 s'arrête sur ses dix tentatives conservées. Aucun essai supplémentaire,
validation humaine C, installation personnelle C, push, PR, fusion ou clôture n'est effectué.
Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md) reste actif.
