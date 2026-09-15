# WO-062 — reprise C ciblée du 16 septembre 2026

- **État :** qualification C toujours incomplète ; deux nouvelles sessions terminées et une preuve préalable Java distincte.
- **Périmètre :** JM-N01 frais ; diagnostic et reprise WR-H01 ; amélioration de la filiation/propriété avant toute répétition complète WR-N01.
- **Candidats inchangés :** Java `0.1.0-candidate.2`, Windows `0.1.0-candidate.1`.
- **Statuts :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Résultat courant

| Cas | Travail accompli | Verdict et portée |
| --- | --- | --- |
| JM-N01 | Session fraîche, corpus et oracle inchangés, réponse finale après 604,047 s | **FAIL** : deux omissions obligatoires subsistent. La distinction date civile/instant est désormais restituée. |
| WR-H01 | Nouvelle session instrumentée, réponse finale après 281,875 s | **BLOCKED** : un accès refusé empêche le lancement du harnais. La fin normale en moins de cinq minutes ne qualifie pas la reproduction. |
| WR-N01 | Sonde préalable de filiation/propriété, sortie naturelle et revue indépendante | **PASS sur la propriété seulement**. Le cas complet reste **BLOCKED** sur sa dernière tentative ; failure et sleep ne sont pas relancés. |

Les qualifications courantes restent **Java : 7 PASS / 1 FAIL** et
**Windows : 6 PASS / 2 BLOCKED**. Les [résultats Java courants](../skills/evaluations/WO-062/ss-java-module/run-03/current-results.json)
et [résultats Windows courants](../skills/evaluations/WO-062/ss-windows-runtime/run-03/current-results.json)
précisent la provenance de chaque cas. Les huit cas de chaque skill ne sont pas tous
réexécutés : seuls JM-N01 et WR-H01 le sont dans cette reprise.

## JM-N01 — session fraîche et revue

La [réponse nouvelle](../skills/evaluations/WO-062/ss-java-module/run-03/cases/JM-N01/response.md)
est produite avec les mêmes 39 entrées de préflight, dont les sources autorisées et
le candidat exact. Aucun oracle, résultat précédent ni correction attendue n'est
fourni. La [revue indépendante](../skills/evaluations/WO-062/ss-java-module/run-03/review-JM-N01.md)
confirme la lecture intégrale du candidat par outil et la conformité des empreintes.

Deux exigences restent absentes de la restitution :

1. La perte du navigateur ou du contexte live termine la session sans reprise ni recréation automatique.
2. Le garde textuel de Verify-Local vise précisément `com.microsoft.playwright` dans `src/main`.

La distinction entre `LocalDate` et `Instant` satisfait cette fois le critère temporel ;
le mot « UTC » n'est pas imposé littéralement. Les deux omissions restantes n'établissent
ni recommandation contraire ni résultat de test inventé. Le verdict reste néanmoins FAIL.

La session termine avec codes natif et collecteur 0, 30 commandes de lecture et une
réponse finale. Deux tentatives de sous-agents CLI refusées et une erreur 206 du hook
`legacy_notify` sont conservées dans les traces ; aucun sous-agent n'a effectivement
travaillé dans cette session et ces incidents ne fondent pas son verdict métier.

## WR-H01 — comprendre les quinze minutes

La [chronologie de run-02](../skills/evaluations/WO-062/c-targeted-reprise/wrh-run02-timeline.json)
est reconstruite depuis les heures des artefacts et la trace figée, sans réexécuter
le harnais historique ni modifier ses preuves.

| Point observé dans run-02 | Depuis le début de session |
| --- | ---: |
| Premier préflight du conducteur | 376,406 s |
| Début du conducteur qui exécute réellement le harnais | 600,423 s |
| Retour de ce conducteur | 604,033 s |
| Postflight indépendant | 671,625 s |
| Écriture du manifeste d'artefacts par la session | 866,937 s |
| Expiration de la session, sans réponse finale | Environ 900,6 s |

Le harnais lui-même dure **2 726,6811 ms**. Environ **600 s précèdent son conducteur**
et **297 s suivent le retour**. Les commandes montrent des lectures étendues puis
des relectures, la préparation de preuves, un postflight et une documentation
additionnelle. Elles produisent 336 104 caractères de sortie au total, dont 193 799
pour la plus volumineuse. Toutes les opérations publiques démarrées ont une fin
enregistrée ; aucune attente d'outil encore ouverte n'est établie à l'expiration.

Le collecteur extérieur analyse et fige les événements **après** la fin ou l'expiration
du sous-processus évalué. Son travail de collecte postérieur ne peut donc pas expliquer
l'expiration de ce sous-processus. La collecte d'artefacts réalisée par la session,
elle, est bien comprise dans ses quinze minutes.

**Limite causale :** les anciens événements ne portent pas d'heure de réception
individuelle. Les intervalles ne permettent pas de séparer exactement raisonnement,
attente du service, transport et construction des appels. Aucune durée précise de
raisonnement interne ni cause unique n'est affirmée.

### Run-03 — fin normale, exécution bloquée

L'[entrée de conduite additionnelle](../skills/evaluations/WO-062/c-targeted-reprise/wrh-flow-addendum.md)
borne les lectures, la documentation et la finalisation, et demande des marqueurs
de phases. Elle vise moins de six minutes, avec le watchdog de quinze minutes
inchangé. Le lanceur horodate désormais la réception des événements JSONL ; ces
horodatages sont locaux et ne mesurent pas le temps interne du serveur.
L'usage des événements et de la sortie finale correspond au
[mode non interactif documenté par OpenAI](https://learn.chatgpt.com/docs/non-interactive-mode).

La [trace nouvelle](../skills/evaluations/WO-062/ss-windows-runtime/run-03/cases/WR-H01/trace.json)
et sa [chronométrie](../skills/evaluations/WO-062/ss-windows-runtime/run-03/cases/WR-H01/native-timing-summary.json)
établissent une réponse finale et `turn.completed`, sans expiration. La durée de la
trace de coordination est **281,875 s** ; celle du sous-processus instrumenté est
**281,594 s**. Ce sont deux périmètres de mesure distincts.

| Phase | Observation de run-03 |
| --- | --- |
| Préflight du conducteur | 22:44:17 UTC ; accès refusé en 194,609 ms |
| Lancement du harnais | **Non atteint** : aucun PID enfant ni code natif |
| Preuves écrites | Marqueur EVIDENCE_READY à 22:44:17 UTC |
| Retour du prompt | Marqueur après retour effectif à 22:44:24 UTC |
| Postflight et conclusion prêts | 22:45:28 UTC ; runtime vide, zéro ressource temporaire créée |
| Réponse finale reçue | 22:46:11 UTC |

Le marqueur `EXECUTION_END` est écrit dans le finally même lorsque le préflight
échoue ; **il ne prouve pas un lancement**. `EXECUTION_START` est absent. Le postflight
de 85,866 ms constate le dossier vide et ne revendique aucun nettoyage d'un harnais
exécuté. La [revue indépendante WR-H01](../skills/evaluations/WO-062/ss-windows-runtime/run-03/review-WR-H01.md)
maintient donc BLOCKED. Les preuves de réussite native de run-02 et cette nouvelle
réponse finale ne sont pas assemblées pour fabriquer une exécution complète réussie.

La revue relève aussi trois omissions de restitution historique : les quatre classes
de chemins refusés, les identifiants/SHA exacts des deux essais WO-053 et l'absence
accidentelle de `-DskipITs` avec nettoyage des conteneurs dans WO-044. Elle observe
également des lectures intégrales et répétées malgré la consigne de lecture ciblée.
Ces écarts sont conservés ; ils ne démontrent pas la cause du refus d'accès et
devront être traités dans une prochaine qualification complète.

### Piste précise pour l'accès refusé

Le conducteur ne conserve que le message d'exception, sans ligne ni pile. Un
[diagnostic complémentaire en lecture seule](../skills/evaluations/WO-062/c-targeted-results/wrh-preflight-diagnostic.json)
réalisé dans le sandbox de la tâche de coordination fait réussir le parcours des
ancêtres, la lecture du runtime et l'observation de son propre exécutable. La lecture
CIM de son propre parent échoue en 46,898 ms, avec `HRESULT 0x80041003`.

Ce résultat est compatible avec l'appel `Get-CimInstance Win32_Process` placé avant
le lancement dans le conducteur. **Le contexte est distinct de la session évaluée :
la ligne fautive de celle-ci n'est pas prouvée rétroactivement.** La prochaine
conduite doit conserver étape, exception, ligne et pile ; la lecture d'un parent
du conducteur ne doit pas masquer les preuves nécessaires sur l'enfant réellement
créé. Aucun assouplissement du sandbox ni nouvelle invocation du harnais n'est effectué ici.

## WR-N01 — propriété améliorée avant répétition

La [preuve préalable](../skills/evaluations/WO-062/c-targeted-reprise/ownership-proof.json)
est produite par une fixture distincte, sans modes failure/sleep. La découverte locale
identifie un relais javapath (`OriginalFilename=shimconsole.exe`) et l'image Java 25
directe (`OriginalFilename=java.exe`). Le chemin direct est découvert, sans modification
du PATH ni installation. Oracle documente les emplacements javapath et JDK/bin dans son
[guide Windows Java 25](https://docs.oracle.com/en/java/javase/25/install/installation-jdk-microsoft-windows-platforms.html) ;
les propriétés du processus ci-dessous proviennent des mesures locales.

| Preuve nouvelle | Valeur observée |
| --- | --- |
| Runtime | Windows PowerShell 5.1 Desktop ; Java 25.0.4+7-LTS-189 |
| Exécutable direct | `C:\Program Files\Java\jdk-25.0.4\bin\java.exe` |
| PID détenu / publié par Java | 4808 / 4808 |
| Parent publié / PID conducteur | 35688 / 35688 |
| Handle conservé | 2520 |
| Identifiant d'essai | Même UUID publié et détenu pendant la vie du processus |
| Image | Chemin demandé, MainModule et ProcessHandle.info.command concordants |
| Fin | RELEASE, sortie naturelle 0, racine temporaire absente |
| Durée totale rapportée | 959,1857 ms |

La [revue indépendante de propriété](../skills/evaluations/WO-062/c-targeted-results/ownership-review.md)
conclut **PASS_OWNERSHIP_ONLY**. Les heures de création sont enregistrées avec une
différence de précision de 0,8359 ms ; le script ne les compare pas. La limite de
20 s dans sa finalisation n'est pas un watchdog extérieur universel. La preuve et
son postflight sont produits par ce conducteur, hors session de modèle ; aucun
succès failure/sleep, timeout, arrêt forcé ou traitement d'accent n'en découle.

Les [conditions de reprise préparées](../skills/evaluations/WO-062/c-targeted-results/wrn-resumption-conditions.md)
et leur [revue](../skills/evaluations/WO-062/c-targeted-results/wrn-resumption-review.md)
précisent les preuves à recueillir de nouveau pour chaque mode. Elles distinguent
le parent connu par la création du processus d'une observation du parent par la
JVM ; la fixture originale n'imprime pas ce dernier. Aucun résultat préalable ne
doit être donné à la prochaine session comme preuve du nouvel essai.

**Le cas complet WR-N01 n'est pas relancé dans cette étape.** Son protocole et sa
fixture restent intacts. Le journal `JavaLauncher.log` de run-02 est conservé ;
la nouvelle sonde n'en établit pas la propriété et ne le supprime pas. La preuve de
filiation est désormais disponible pour préparer un nouvel essai complet borné.

## Conservation, indépendance et contrôles

L'[autorisation ciblée](../skills/evaluations/WO-062/c-targeted-reprise/authorization.json)
reflète la demande actuelle. Les deux nouvelles sessions utilisent des répertoires
frais, le service configuré et les candidats exacts. L'oracle et les résultats
antérieurs restent exclus. Les lectures intégrales des candidats sont prouvées par
outil ; aucune injection automatique n'est affirmée. Les réponses sont figées avant revue.

Les candidats et sources d'origine étaient déjà versionnés. Les nouveaux octets
opératoires et leurs empreintes ont été fixés avant lancement, puis le lot de
préparation a été commité à `380d9ed` pendant l'exécution des sessions. Cela n'est
pas présenté comme un commit de toute la nouvelle préparation antérieur au lancement.

Le dossier C conserve désormais **28 tentatives de modèle** : 17 Java et 11 Windows,
avec **27 réponses finales et une session historique expirée**. Les verdicts historiques
totalisent 18 PASS / 5 FAIL / 5 BLOCKED ; les 16 cas courants restent à 13 PASS /
1 FAIL / 2 BLOCKED. La sonde de propriété et les diagnostics locaux ne sont pas des
tentatives supplémentaires de qualification des skills.

Les [contrôles de cette livraison](WO062-C-TARGETED-CHECKS-20260916.json) relient
empreintes, résultats, revues, liens, recherche de secrets et conservation des fichiers
personnels. Les cinq tests locaux du collecteur chronométré vérifient fin normale,
code non nul, expiration d'un enfant détenu, JSON invalide et refus d'un cas hors
périmètre ; ils n'appellent aucun modèle et ne lancent pas les sondes Windows.

Le code applicatif étant inchangé, la preuve Maven du WO terminée le
15 septembre à 15:58:40 UTC reste réutilisée avec cette limite : 2 440 tests déclarés,
2 435 exécutés, cinq ignorés, zéro échec/erreur. Aucun nouveau Maven ou test
d'intégration n'est revendiqué. Les huit skills personnels précédents conservent
leurs seize fichiers et leurs dates ; les deux skills C ne sont pas installés.

## Suite concrète

1. **Java :** traiter les deux omissions persistantes, puis qualifier la restitution complète dans un contexte indépendant.
2. **WR-H01 :** préparer une conduite qui diagnostique précisément le préflight, puis obtenir dans la même session un harnais exécuté, ses preuves, le postflight, la conclusion et la réponse finale.
3. **WR-N01 :** utiliser la preuve de propriété revue pour préparer et geler la conduite complète des deux modes, avec les bornes originales et des identités fraîches.

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md) reste actif.
La validation humaine des candidats C, leur installation, la consolidation D et la
livraison Git restent distinctes. Aucun push, PR, fusion, promotion, tag ou clôture
n'est réalisé dans cette reprise.
