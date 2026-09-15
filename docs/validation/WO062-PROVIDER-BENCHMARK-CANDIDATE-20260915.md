# WO-062 — Candidat ss-provider-benchmark et évaluation A2

**Complément ultérieur :** [PB-S01 qualifié dans run-02 et bilan cumulé](WO062-PROVIDER-BENCHMARK-PBS01-QUALIFICATION-20260915.md).
Le présent rapport conserve les résultats et limites du premier run.

- **Date :** 15 septembre 2026.
- **État :** `EVALUATED_WITH_OPEN_FINDINGS_PENDING_OWNER_REVIEW` ; **12 cas exécutés, 11 PASS**, cas restant ouvert : **PB-S01**.
- **Périmètre :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.
- **Candidat :** [SKILL.md](../skills/local-lab/ss-provider-benchmark/SKILL.md), version `0.1.0-candidate.1`, et [métadonnées](../skills/local-lab/ss-provider-benchmark/agents/openai.yaml).
- **Source des verdicts :** [results.json](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/results.json), [revue métier](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/review-tasks.md), [revue des contre-épreuves](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/review-counterexamples.md), [revue de sélection](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/review-selection.md).

## Autorité et provenance

Après acceptation de WO-062 et réalisation A1, le propriétaire demande de rédiger le candidat
depuis cet inventaire puis de l'évaluer avec les cas figés dans un contexte indépendant de
l'oracle. Cette autorisation couvre la rédaction et les essais de cet incrément. La validation
humaine du contenu et son installation personnelle restent des étapes distinctes.

La base des 38 sources est `74d3f38afd64ce587353fbd645cad7a388e9756c`. La préparation acceptée
a été figée dans le commit local `cced29a`, puis le candidat dans
`06f7bf8aa76c124246ea661fea5c4afe95b66354` avant les essais. Aucun octet des six fichiers A1 n'a été modifié.
Leur état historique `PREPARED_NOT_RUN` décrit la préparation ; le fichier de résultats A2
ci-dessus porte l'état courant des essais.

SHA-256 du candidat : `4bcecee4dbe75ec74530a7bda63acc8cbc84cc15dcd7c07e2ab9e6f80cc7be94`.
SHA-256 du manifeste A1 : `4fd13d59e33665b11b48767dbb75ace879c626ed450cc15596c625320e3ac6eb`.
Le [manifeste des entrées](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/inputs-manifest.json) associe les prompts exacts, les
sources et les copies synthétiques à leurs empreintes ; [source-map.json](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/source-map.json)
résout les liens absolus historiques des réponses vers les fichiers versionnés du corpus.

## Résultats des douze cas

| Cas | Objet | Verdict de revue | Résultat principal |
|---|---|---|---|
| [PB-H01](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-H01/response.md) | Relecture historique J8 | **PASS** | 20 tentatives ; découverte 16 ; marginal 4 ; un dossier exploitable, aucun strict. |
| [PB-N01](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-N01/response.md) | Tâche nouvelle prematch/live | **PASS** | Protocole et première scorecard produits ; cohortes, checkpoints, coûts et mesures absentes. |
| [PB-C01](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-C01/response.md) | Strates | **PASS** | Ledger, réponses historiques et baseline séparés ; note source non exécutée. |
| [PB-C02](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-C02/response.md) | Taux et latences | **PASS** | 4/6 réponses ; 1/2 compatibilité ; erreur 4/6 ; P50=60 ms. |
| [PB-C03](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-C03/response.md) | Complétude | **PASS** | 10/12=83,33 % ; vide valide et indisponibilité conservés. |
| [PB-C04](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-C04/response.md) | Dossiers et coûts | **PASS** | Deux cibles, un exploitable ; ratios 3 et 5 ; variante à dénominateur nul non mesurée. |
| [PB-C05](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-C05/response.md) | Fenêtre et empreinte | **PASS** | Bornes HTTP/export distinctes ; longueur brute et ordre TreeSet confrontés au code. |
| [PB-C06](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-C06/response.md) | Corrections tardives | **PASS** | Délais de 60 000 et 120 000 ms ; rejet sans état direct antérieur. |
| [PB-S01](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-S01/response.md) | Sélection explicite | **BLOCKED** | Bon candidat choisi ; chargement du corps exact non attesté par la trace disponible. |
| [PB-S02](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-S02/response.md) | Sélection implicite | **PASS** | Candidat sélectionné puis lu par outil ; contenu normalisé identique. |
| [PB-S03](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-S03/response.md) | Non-sélection : build | **PASS** | ss-verify sélectionné et lu ; aucun build exécuté. |
| [PB-S04](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-S04/response.md) | Non-sélection : autre dépôt | **PASS** | Aucun skill Lab sélectionné ; aucune comparaison commerciale exécutée. |

Le `BLOCKED` de PB-S01 qualifie une **preuve de chargement insuffisante**, pas une erreur
de routage. La réponse désigne le chemin du bon candidat et décrit sa procédure. Elle ne
contient cependant aucune commande de lecture ; la reconstruction du contexte ne capture
pas la requête effectivement transmise. Le chargement automatique exact n'est donc pas
promu en réussite sur la seule déclaration du modèle. Les quatre sessions de sélection
se sont terminées avec un code de sortie nul.

## Résultat nouveau prematch/live

La [réponse PB-N01](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-N01/response.md) fournit le protocole et la première
scorecard qui restaient à produire. Elle distingue les observations du 7 septembre sous V2,
la modification source V3, les trois arrêts réels V8 et les qualifications locales V11.

- Elche `NOT_REQUESTED` avant match traduit la politique historique ; cela ne mesure pas
  une indisponibilité fournisseur.
- Aux checkpoints Nantes 19:46Z/19:47Z, un J4 récent coexiste avec des familles J5 anciennes.
  La scorecard sépare âge depuis réception, dispersion des familles et retard source inconnu.
- Les intervalles médians clos ne prouvent pas une continuité après la dernière réception.
  Les queues encore ouvertes sont conservées dans l'analyse.
- Les départs réels V8 et les capacités rejouées V11 ne prouvent ni quota fournisseur,
  ni cause des refus, ni disponibilité réelle soutenue avec la politique actuelle.
- L'exactitude sportive, le comparateur externe, le temps source et le coût monétaire restent
  non établis. La construction de dossiers est étayée ; l'utilité prematch représentative et
  la continuité live V11 restent à démontrer.

La revue retient notamment une réserve de présentation : les quatre derniers instants sont
distingués dans plusieurs sections ; un tableau unique faciliterait leur lecture. Cette
réserve ne modifie pas la réponse figée et ne masque aucun écart matériel de calcul.

## Méthode d'évaluation et limites

Les huit cas métier ont utilisé huit agents neufs, chacun lancé avec `fork_turns=none`.
Chaque agent a reçu le prompt figé, le candidat exact, les seules sources autorisées du cas
et, pour les contre-épreuves, un seul objet synthétique. Ni inventaire commenté, ni oracle,
ni historique de conception n'ont été fournis. Les fichiers de sortie ont été figés par taille
et SHA-256 avant transmission aux relecteurs indépendants. Le candidat n'a pas été retouché
après observation des réponses. Il n'y a eu ni répétition guidée, ni correction des sorties.

Les quatre cas de sélection ont utilisé le CLI Codex `0.154.0-alpha.6.2`, dans des sessions
neuves éphémères en lecture seule, avec le prompt de routage uniquement. Une tentative
initiale sans réponse a été interrompue. Le lancement externe a ensuite été refusé par la
revue automatique d'approbation pour transmission de contexte interne sans accord explicite.
Le propriétaire a alors autorisé les quatre sessions ; elles ont toutes produit une réponse.

Les catalogues réels contiennent un seul candidat et deux copies identiques des cinq skills
du lot 1 : la copie temporaire et l'installation utilisateur préexistante. Les dix fichiers
personnels ont été comparés au paquet en lecture seule ; ils n'ont pas été réinstallés.
La [découverte locale](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/discovery.json) et chaque catalogue de cas conservent cette
provenance. S02 lit le candidat exact par outil ; S03 lit `ss-verify`. La commande fournie
au CLI n'ajoute manuellement aucun corps de skill ni fichier joint.

Les traces métier sont déclaratives : elles ne constituent pas un journal système exhaustif
ni un confinement des lectures par le système d'exploitation. Les traces natives conservent
les commandes et les réponses, avec empreintes des corps lus ; les éléments de raisonnement
interne et les journaux complets de configuration ne sont pas publiés. Le contexte local a
été reconstruit après les essais ; il ne remplace pas une capture de la requête transmise.
Le modèle et l'effort exacts ne sont pas exposés dans les métadonnées observées : ils restent
non renseignés, sans valeur inventée. Un essai par cas et aucun témoin A/B ne permettent de
revendiquer un gain de productivité ou une généralisation au-delà de ces cas.

## Contrôles

Les [résultats détaillés du contrôle documentaire](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/checks.json)
et le [manifeste des artefacts](../skills/evaluations/WO-062/ss-provider-benchmark/run-01/artifact-manifest.json)
conservent les comptes et empreintes de cette livraison. Leur PASS ne modifie pas le verdict
BLOCKED de PB-S01.

| Contrôle | Résultat et portée |
|---|---|
| Validateur officiel `skill-creator/scripts/quick_validate.py` | PASS sur le candidat exact ; contrôle de format seulement. PyYAML a été placé dans un dossier temporaire, sans modifier l'environnement personnel. |
| 38 sources : blobs Git à la base et SHA-256 des fichiers | PASS ; corpus A1 inchangé. |
| Cinq fichiers verrouillés par le manifeste A1 et manifeste lui-même | PASS ; inventaire, plan, oracle, cas et entrées intacts. |
| Dix fichiers approuvés du lot 1 | PASS ; paquet et copies personnelles identiques, lecture seule. |
| Douze réponses/traces et entrées des cas | Empreintes vérifiées avant les revues et contrôlées lors de l'assemblage. |
| Découverte locale puis sélection native | Candidat unique découvert ; verdicts distincts par cas, PB-S01 reste ouvert. |
| UTF-8, JSON, liens, contenu sensible, diff et conservation des octets | Contrôle documentaire sur les fichiers livrés ; les preuves du run sont protégées de la normalisation Git par une règle `.gitattributes` ciblée. |

Le contrôle applicatif déjà exécuté dans ce WO porte sur ce même code :
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1`, soit
`mvnw.cmd -DskipITs clean verify`, puis `java ci/VerifyTestReports.java standard` :
**2 440 cas recensés, 2 435 exécutés, zéro échec, zéro erreur, cinq ignorés**, fin
`2026-09-15T15:58:40Z`. Voir la [preuve de cadrage](WO062-SKILLS-LOT2-SCOPING-20260915.md).
Cette suite n'est pas relancée pour des instructions et des preuves documentaires ; aucun
Java, test applicatif, profil, configuration runtime, migration ou workflow CI ne change.
Ce résultat antérieur ne qualifie pas le comportement du skill. L'intégration PostgreSQL
n'est pas requise pour ce delta ; aucune CI distante actuelle n'est revendiquée.

## Livraison de l'incrément et suite

Fichiers ajoutés : les deux fichiers du candidat ; le dossier `run-01` (réponses, entrées,
traces, empreintes, trois revues, catalogues et résultats) ; la présente validation.
Fichiers actualisés : WO-062, les deux README, CHANGELOG et la règle d'octets de `.gitattributes`.
Les sources métier, A1, les preuves SKL-002 et l'installateur restent inchangés.

WO-062 reste actif sur `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`, dans son worktree
distinct ; la cible de PR reste `feature/V0.1.0-RC01`. Les commits de cet incrément sont locaux.
Aucune publication, PR, fusion, installation personnelle, validation humaine du skill ou
clôture du WO n'est déduite de ces essais.

La prochaine action de qualification est de compléter la preuve du chargement explicite
PB-S01, puis de soumettre le contenu et ses limites à la validation propriétaire. Les quatre
autres skills du lot 2 restent dans l'ordre et le périmètre acceptés du WO.
