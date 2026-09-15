# WO-062 — Candidats Java module et Windows runtime

- **Démarrage :** 15 septembre 2026.
- **État de rédaction du rapport :** `REQUALIFICATION_IN_PROGRESS` ; bilan final à compléter après les dix sessions supplémentaires autorisées.
- **Périmètre :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.
- **Décisions humaines :** création et évaluations autorisées ; validation humaine du contenu et installation personnelle C non réalisées.

## Livrables et état courant

| Skill | Version courante | Contenu | Évaluation |
| --- | --- | --- | --- |
| ss-java-module | `0.1.0-candidate.2` | [Procédure](../skills/local-lab/ss-java-module/SKILL.md), [métadonnées](../skills/local-lab/ss-java-module/agents/openai.yaml), [qualification courante](../skills/evaluations/WO-062/ss-java-module/qualification.json) | Run-02 : 7 PASS / 1 FAIL ; qualification incomplète sur JM-N01. |
| ss-windows-runtime | `0.1.0-candidate.1` | [Procédure](../skills/local-lab/ss-windows-runtime/SKILL.md), [métadonnées](../skills/local-lab/ss-windows-runtime/agents/openai.yaml), [qualification courante](../skills/evaluations/WO-062/ss-windows-runtime/qualification.json) | Première recette : 6 PASS / 2 BLOCKED ; reprise des deux sondes autorisée. |

Java décrit les responsabilités et dépendances réellement observées, le monomodule,
la composition Spring, les transactions, l'activation et la séparation du worker.
Windows décrit le runtime effectif, les arguments, codes natifs, encodages, délais,
propriété des processus et preuves de nettoyage. Les deux procédures relaient les
travaux spécialisés sans les déclencher implicitement.

## Préparation et identité

Les [48 sources Java](../skills/evaluations/WO-062/ss-java-module/source-inventory.md)
et [23 sources Windows](../skills/evaluations/WO-062/ss-windows-runtime/source-inventory.md)
ont pour référence `6648dd423e556b5248b8793a539ae85a7680f9bc`. Les préparations ont
été gelées aux commits `21e16cc` et `050ec84`, avant rédaction de chaque candidat.
Elles conservent sources, OID des blobs Git, tailles et SHA-256 de matérialisation,
prompts, entrées et oracle séparé. Les cinq pièces Java et sept pièces Windows hors
manifeste restent inchangées ; leurs mentions `NOT_RUN` sont historiques.

La préparation Windows comprend un protocole opératoire et une petite fixture Java.
Ce protocole définit les actions autorisées et leurs bornes ; les scripts de conduite,
mesures et conclusions doivent être produits par la session évaluée. Il ne fournit
pas de réponse attendue. Le WO de cadrage est exclu des entrées des essais : actualiser
sa synthèse ne modifie pas leurs sources figées.

| Pièce | SHA-256 |
| --- | --- |
| Préparation Java | `a0907baba5a15606b6a735873a3e8ab9cc4043dfe8d852f3b843e1ec61b68290` |
| Préparation Windows | `d62271db051e64edc261f4812f4969511993e314fb07cb8f6d3115ae446e29f7` |
| Java `.1` historique | `d20c8b838a9c4b9713e27c576e22272427d3f64fef50a670f5a09364cf849eb4` |
| Java `.2` courant | `255655544771ae6cf2a71b967aba8efeb7e53e64acc41ca31dc784e16a719118` |
| Métadonnées Java, inchangées | `ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae` |
| Windows `.1` courant | `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6` |
| Métadonnées Windows | `3038015980952bc18b21f45f0d4af514b35a8841394e4b80824986b7e30ccd9f` |

Java `.1` a été écrit à `d89f9f2`, sa recette a démarré, puis Windows `.1` a été
écrit à `9e7b777`. Les revues indépendantes se sont poursuivies en parallèle comme
en B. Java `.2` a ensuite été versionné à `2d62f12`, après conservation de la première
recette à `57ebe86`. Aucun résultat `.1` n'est attribué à `.2`.

## Première recette : seize sessions conservées

Le propriétaire a autorisé explicitement seize sessions éphémères, recevant le candidat,
le catalogue local et les seules entrées internes prévues, sans oracle. Les deux cas
Windows pratiques disposent en outre des permissions nécessaires à leurs sondes bornées.
Le contrôle automatique avait refusé le premier lancement Java avant exécution ;
l'autorisation complémentaire C a été recueillie avant toute session.

| Cas | Java `.1` | Windows `.1` |
| --- | --- | --- |
| H01 — historique | **FAIL** — incident d'environnement avant tests omis | **BLOCKED** — outil local indisponible avant lecture du candidat |
| N01 — tâche nouvelle | **FAIL** — distinctions métier, exception d'adaptateur et relais omis | **BLOCKED** — outil local indisponible avant lecture du candidat |
| C01 — contre-épreuve | PASS | PASS |
| C02 — contre-épreuve | **FAIL** — relais SQL omis | PASS |
| S01 — sélection explicite | PASS | PASS |
| S02 — sélection implicite | PASS | PASS |
| S03 — routage lot 1 | PASS — ss-postgres-change | PASS — ss-work-order |
| S04 — autre projet | PASS — aucun ss-* imposé | PASS — aucun ss-* imposé |
| **Bilan run-01** | **5 PASS / 3 FAIL** | **6 PASS / 2 BLOCKED** |

Les [résultats Java](../skills/evaluations/WO-062/ss-java-module/run-01/results.json)
et [résultats Windows](../skills/evaluations/WO-062/ss-windows-runtime/run-01/results.json)
relient chaque réponse figée, revue et trace. Les échecs Java sont des omissions
obligatoires ; les relecteurs n'établissent ni décision technique incorrecte, ni
test réussi inventé, ni action hors périmètre. Ils restent des échecs de qualification.

### Correction Java ciblée

La [révision `.2`](../skills/evaluations/WO-062/ss-java-module/revision-02/revision.json)
exige une restitution explicite des distinctions déclencheur/provenance et date métier/UTC,
des erreurs propagées et dépendances aux exceptions d'adaptateur, des incidents historiques
avant tests et des relais spécialisés pour les prochaines actions. La description et
les métadonnées sont identiques. Aucun oracle, prompt, entrée ou première réponse n'est
réécrit. La [revue statique](../skills/evaluations/WO-062/ss-java-module/revision-02/review-static.md)
est favorable ; elle ne remplace pas la nouvelle recette.

### Blocage Windows et préparation de reprise

WR-H01 et WR-N01 n'ont pas pu lire le candidat ni lancer leurs outils :
`helper_unknown_error: setup refresh had errors`. Le code zéro du CLI signifie que
la session a rendu son constat. Le collecteur retourne 1 et `MissingRuntimeArtifacts`.
Aucun code du harnais ou de Java, aucune mesure de timeout ni preuve de nettoyage
des sondes n'a été produit. La durée de session n'est pas une durée de reproduction.

Le [diagnostic local](../skills/evaluations/WO-062/java-windows-runtime/runtime-diagnostic.json)
conserve les refus `SetNamedSecurityInfoW` sur les dossiers du cas et leur propriétaire
`CodexSandboxOffline`, différent du compte du lanceur. Les nouveaux dossiers sont créés
par le compte du lanceur. Un contrôle **sans modèle** prouve lecture, écriture et
nettoyage d'un marqueur dans chacun des deux contextes préparés ; candidat intact,
aucun résidu du marqueur. Il utilise le profil intégré `:workspace` décrit par la
[documentation officielle](https://learn.chatgpt.com/docs/permissions).

Le premier appel de diagnostic avait une syntaxe CLI incomplète, conservée séparément ;
après usage du paramètre de profil exigé, les contrôles locaux réussissent. Le sandbox,
la configuration persistante, les premières entrées et leurs ACL ne sont pas assouplis.
Les futures sessions conservent `--sandbox workspace-write`. Ce préflight ne qualifie
ni le skill, ni les sondes, ni une réparation générale du sandbox.

## Dix sessions supplémentaires autorisées

Le propriétaire répond **« J’autorise ces dix sessions »** : huit cas Java avec `.2`,
puis WR-H01 et WR-N01 avec le Windows `.1` identique. L'[autorisation exacte et son
périmètre](../skills/evaluations/WO-062/java-windows-runtime/requalification-authorization.json)
conservent cette décision. Les contextes sont neufs, les critères et bornes inchangés.
Les seize premières réponses et leurs verdicts restent versionnés.

**Java run-02 : 7 PASS / 1 FAIL**, après huit sessions terminées et revues.
Les [résultats](../skills/evaluations/WO-062/ss-java-module/run-02/results.json)
conservent la nouvelle recette complète ; aucune réussite `.1` n'est transférée à `.2`.
La [revue JM-N01](../skills/evaluations/WO-062/ss-java-module/run-02/review-JM-N01.md)
relève trois précisions obligatoires absentes de la réponse : instants UTC, terminaison
sans reprise après perte du contexte live, cible `com.microsoft.playwright` dans
`src/main` du garde textuel. Les distinctions et le fonctionnement nominal sont
décrits, mais ne remplacent pas ces précisions. Aucune décision inverse ni preuve
inventée n'est établie ; le verdict reste FAIL pour restitution incomplète.

La [revue nouvelle produite](../skills/evaluations/WO-062/ss-java-module/run-02/cases/JM-N01/response.md)
identifie aussi des pistes fondées : une resoumission conflictuelle peut retirer les
octets de l'import original ; le sort du terminal J8 après échec de publication ou
perte du propriétaire reste à qualifier ; le test natif J3 requiert une sélection
explicite. Ces constats restent destinés à des travaux applicatifs distincts.

**Windows run-02 : en cours.** Les deux sondes doivent produire leurs propres
commandes, codes, durées et postflights. Les préflights d'accès ne valent pas résultat.

## Indépendance et portée

Chaque essai utilise un répertoire neuf et Codex CLI `0.154.0-alpha.6.2`, en mode
éphémère. Modèle et effort suivent les réglages configurés ; leurs valeurs ne sont
pas exposées dans les traces observées et ne sont pas inventées. L'oracle, l'inventaire
commenté, le plan et les résultats antérieurs sont exclus du contexte évalué. Les
sources historiques expressément allowlistées demeurent des entrées métier légitimes.

La sélection exige génériquement de lire le skill choisi et de s'arrêter au routage.
Elle établit sélection et lecture intégrale **par outil** ; aucune injection automatique
du corps par le CLI n'est attestée. Le catalogue est reconstruit après exécution,
sans être une capture de la requête transmise. Chaque candidat rencontre les huit
skills personnels existants ; la coexistence des dix ensemble reste à qualifier en D.

Les sorties sont figées avant revue. Les relecteurs disposent ensuite de l'oracle,
sans intervenir dans les sessions. Les exports conservent commandes et sorties réelles,
avec retrait des seuls événements de raisonnement interne. Les empreintes des journaux
bruts restent dans les traces. Le lanceur a passé une revue indépendante et 17 tests
simulés de copie, bornes, diagnostics et gel, sans modèle ni exécution des sondes.

WR-H01 concerne une capture native d'arguments sous PowerShell 7, sans lancement de
l'application Java. WR-N01 concerne une petite sonde Java 25 sous Windows PowerShell
5.1, sans Maven ni application Spring. Aucune stabilité générale de Windows, Docker,
Eclipse, WSL, aucune collecte fournisseur ni CI distante n'en découle.
Le gain général de temps ou de qualité est **non mesuré**.

## Contrôles de livraison et suite

La génération officielle des métadonnées et `quick_validate.py` réussissent pour les
candidats ; UTF-8/LF sans BOM vérifié. Ces contrôles attestent le format, pas le métier.
Sources, préparations, premières réponses et revues sont contrôlées par empreinte.
Les seize fichiers personnels existants conservent contenu et dates de modification.
Les deux répertoires personnels C sont absents.

Code, tests applicatifs, POM, migrations, ADR, workflows, scripts, installateur,
lot 1, SKL-002 et preuves A/B restent inchangés. La suite applicative déjà exécutée
dans ce WO sur ce code inchangé est réutilisée avec cette portée explicite :
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1`,
soit `mvnw.cmd -DskipITs clean verify`, puis `java ci/VerifyTestReports.java standard`.
Fin `2026-09-15T15:58:40Z` : **2 440 déclarés, 2 435 exécutés, cinq ignorés,
zéro échec/erreur**. Aucun nouveau lancement Maven n'est revendiqué ; aucune persistance
n'est modifiée. Les vérifications nouvelles portent sur les skills et leurs preuves.

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md) reste actif,
sur `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`, cible `feature/V0.1.0-RC01`.
Validation humaine du contenu et des limites, installation personnelle, consolidation D
et livraison Git sont des étapes distinctes. Aucun push, PR, fusion, promotion, tag
ou clôture n'est réalisé dans cette recette.
