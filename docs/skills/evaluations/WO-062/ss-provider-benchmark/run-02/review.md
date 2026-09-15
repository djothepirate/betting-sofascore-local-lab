# Revue indépendante — PB-S01 / run-02

- Relecteur : `/root/pb_s01_result_review`, indépendant de l'exécution évaluée.
- Revue UTC : `2026-09-15T18:15:03.6426268Z`.
- HEAD vérifié : `6e310623208902db994995caf61bc58d1d4a9040` ; état suivi propre avant rédaction.
- Verdict : **PASS**, limité au chargement explicite par outil dans cet essai instrumenté.
- `closes_finding=true` : la preuve ouverte **SEL-01** est levée dans la qualification courante par ce nouvel essai identifié. **PB-S01/run-01 reste BLOCKED** ; sa réponse, ses preuves et son verdict ne sont pas réécrits.

## Critère et portée de la décision

L'oracle A1 `docs/skills/evaluations/WO-062/ss-provider-benchmark/oracle.md:153–164` attend pour PB-S01 le chargement du candidat exact sur demande explicite, suivi d'un arrêt au routage. Le plan A1 `evaluation-plan.md:90–101` exclut le préchargement manuel du corps et une invitation supplémentaire à choisir le candidat ; il demande d'observer les skills chargés et les outils utilisés. Le prompt PB-S01 demeure celui de `cases.json:124–130`.

Le protocole de run-02 `frozen/protocol.md:9–18,31–49` annonce une différence réelle avec run-01 : une lecture outil intégrale du skill retenu devient obligatoire. L'enveloppe `frozen/routing-request.txt:1` impose cette lecture de façon générique, autorise « aucun » et ne fournit ni nom de candidat attendu, ni chemin, hash, corps ou extrait. Le nom du candidat est présent dans le seul prompt métier, qui l'invoquait déjà explicitement. Cette instrumentation facilite l'observation du comportement demandé, sans changer l'identité du candidat ni donner une réponse de sélection supplémentaire.

**Ce PASS complète le critère matériel de chargement ; il ne qualifie pas une exécution avec le seul prompt A1, sans l'enveloppe ajoutée.** Il ne démontre pas que le même chargement aurait eu lieu sans cette obligation. Il ne transforme pas l'essai en reproduction à entrée strictement identique de run-01 et ne certifie pas l'injection automatique du CLI.

## 1. Intégrité vérifiée avant jugement — PASS

Les huit fichiers de `frozen/freeze.json:5–45` ont été vérifiés par recalcul indépendant de leur taille et de leur SHA-256 avant l'analyse de leurs résultats. Tous concordent :

| Fichier gelé | Octets recalculés | SHA-256 recalculé |
|---|---:|---|
| catalogue.txt | 7670 | `e1e5d1b55875ed6785fa1ec7fc334ac5d9dae7558531f8096cf688886427fac5` |
| native-events.json | 9918 | `6a73146201b15f84174f79dfa4ff80bafecdbf44adc8fff99697b5020a43acd3` |
| preflight.json | 1263 | `5ff2604d9e3c1d5c07ae6ff764922e3058e709a1deed956b1fd527ce788bd04d` |
| prompt.txt | 157 | `ae3d76ca52d167c47bf81db65645d1f54d18695f4d3f804a1c1edca3832d5455` |
| protocol.md | 3307 | `8f9353ec392ce78e2cc7e86ff27e09dca225f8f2cbd4cd97cca07fc3372869a2` |
| response.md | 1123 | `f654de0e67e55de8f8472dd83681e5ca7486ec8323ea14a9522ab1106a2bd829` |
| routing-request.txt | 733 | `6df7eef7002338ccf54e6c7e540e53f97fe877dfdeae401724636194902f6c24` |
| trace.json | 3342 | `bda00c4cd4378c40630ffa4d7f3a2e905d3aa25b5ee4fa300a697854d0254311` |

Le manifeste A1 a le SHA-256 `4fd13d59e33665b11b48767dbb75ace879c626ed450cc15596c625320e3ac6eb`, identique au préflight. Ses cinq fichiers gelés ont aussi été recalculés : `source-inventory.md`, `evaluation-plan.md`, `oracle.md`, `cases.json`, `inputs.json` ; leurs tailles et empreintes concordent. Cela établit que les critères et le prompt A1 consultés n'ont pas été modifiés.

Le manifeste `run-01/artifact-manifest.json` a le SHA-256 `cb44371200f47e72aa00c3bd231a4fb4168f06fd6c91e9653aecdb7f1e788087`, identique au préflight. Les **68 fichiers** qu'il répertorie ont été vérifiés par taille et SHA-256, sans écart. Le constat ouvert demeure celui de `run-01/review-selection.md:72–88` : aucune commande, donc aucune preuve de corps chargé dans cet essai historique.

## 2. Identité du prompt et de l'enveloppe — PASS

La comparaison exacte de `frozen/prompt.txt` au champ `prompt` de PB-S01 dans `cases.json`, suivi du LF terminal du fichier, est positive. Le SHA-256 recalculé depuis ce champ et ce LF est `ae3d76ca52d167c47bf81db65645d1f54d18695f4d3f804a1c1edca3832d5455`. Le même texte figure intégralement en fin de `frozen/routing-request.txt:3`, après la séparation de deux LF. Les empreintes du prompt et de l'enveloppe concordent avec `frozen/preflight.json:12–13` et `frozen/trace.json:25–27`.

Le préflight enregistré à `18:07:11.408754Z` précède le démarrage enregistré à `18:08:24.8069963Z`. Il identifie déjà le hash de l'enveloppe et la lecture obligatoire (`preflight.json:3,13,18`). Le gel est enregistré à `18:10:21.985845Z`, après la fin d'exécution enregistrée à `18:08:58.0901855Z` et avant cette revue. Ces horodatages forment une chronologie locale cohérente ; ils ne constituent pas une attestation temporelle externe signée.

L'enveloppe ne contient que le cadrage de sélection, les limites de lecture/action et le prompt exact. L'absence d'autre ajout manuel et l'exécution éphémère sont déclarées par `protocol.md:22–27` et `trace.json:8,13–29` ; la revue ne les transforme pas en capture de la requête complète.

## 3. Candidat exact, chemin et catalogue — PASS

Les octets des deux fichiers ont été lus indépendamment :

- `docs/skills/local-lab/ss-provider-benchmark/SKILL.md` ;
- `.tmp/pb-evaluations/run-02/PB-S01/.agents/skills/ss-provider-benchmark/SKILL.md`.

Ils sont identiques octet par octet : **6835 octets**, SHA-256 brut `4bcecee4dbe75ec74530a7bda63acc8cbc84cc15dcd7c07e2ab9e6f80cc7be94`. Le frontmatter du candidat versionné (`SKILL.md:1–6`) indique `0.1.0-candidate.1`. L'empreinte concorde avec le préflight, les valeurs avant/après de la trace et l'identité historique de run-01. `agents/openai.yaml` porte aussi le hash attendu `707b19c6ff8b73d6a5962244ef2855e163dc3c55b8f27ea14ef2e9863ac66bdd`.

`frozen/catalogue.txt:10,17` rattache l'unique entrée `ss-provider-benchmark` à la racine isolée r6 et au fichier ci-dessus. Les cinq skills personnels du lot 1 apparaissent une fois chacun (`catalogue.txt:26–30`). Le catalogue ne fournit que les noms, descriptions et chemins, sans corps du candidat.

Le chemin a également été extrait directement de l'argument `-LiteralPath` de l'événement natif terminé, et résolu après normalisation des séparateurs Windows doublés dans sa représentation. Il désigne exactement la copie isolée vérifiée, sans chemin concurrent. Ce contrôle ne repose pas sur le seul champ `trace.candidate_path`.

## 4. Chargement outil intégral — PASS

`frozen/native-events.json:18–26` contient le démarrage `item_1`, type `command_execution`. L'événement `item.completed` du même identifiant (`native-events.json:29–36`) reprend la même commande : une unique lecture locale `Get-Content -LiteralPath '<copie isolée>/SKILL.md' -Raw`, terminée avec **exit_code=0** et **status=completed**. La commande ne contient aucune autre opération.

Le champ **`aggregated_output` réel de l'événement terminé** (`native-events.json:34`) a été comparé intégralement aux deux fichiers ci-dessus, sans s'appuyer sur `trace.tool_body_complete_match` :

- Sortie native UTF-8 : **6837 octets**.
- Fichier versionné et copie isolée : **6835 octets**, chacun composé des 92 lignes complètes du skill.
- Vérification plus forte que la normalisation : la sortie native est **exactement le texte du fichier suivi d'un CRLF supplémentaire**. Aucun préfixe, suffixe de contenu, troncature, espace interne ou ligne du skill ne diffère.
- Après remplacement CRLF → LF et retrait des seuls caractères CR/LF terminaux, les trois textes sont strictement identiques : **6834 octets**, SHA-256 `7e908db329a8d386c39733542ee5c3efaa1b5b74a72db303603b50db3ca876eb`.

Aucun `Trim()` général, retrait d'espace, réécriture Markdown ou normalisation Unicode n'a été appliqué. Les empreintes brute du fichier et normalisée du texte retourné restent explicitement distinctes. Le corps complet comporte le frontmatter et toutes les sections jusqu'à la dernière ligne (`SKILL.md:92`). Le chargement matériel du candidat exact est donc établi dans cet essai.

## 5. Routage et absence d'autres actions observées — PASS

Le journal filtré contient exactement sept événements, dans l'ordre : `thread.started`, `turn.started`, message `item_0`, démarrage de commande `item_1`, fin de commande `item_1`, message final `item_2`, `turn.completed`. Une seule commande démarre et une seule se termine ; il n'existe aucun autre événement d'outil ou de commande dans ce journal publié.

`frozen/response.md` est identique caractère par caractère au texte du dernier message natif `item_2` (`native-events.json:40–44`), sans ajout de LF terminal. Sa première ligne choisit `ss-provider-benchmark`. Les lignes 7–10 justifient l'invocation explicite, la comparaison prematch/live, le bilan documentaire et la forme de la scorecard ; ces justifications correspondent aux sections du candidat (`SKILL.md:23–25,63–81,83–92`).

La réponse s'arrête au routage (`response.md:12`). Elle ne produit ni scorecard métier, mesure de capacité, classement fournisseur, adoption J9 ni nouvelle autorisation de collecte. Le seul outil observé lit le candidat ; aucun rapport J8/WO-058, application, build, DB, Docker, navigateur ou réseau fournisseur n'est consulté ou lancé dans le journal. Les mesures, dénominateurs et statuts de non-mesure métier ne sont pas à calculer dans ce cas de sélection.

## Constats et limites conservées

1. **SEL-01 — levé pour la qualification courante par PB-S01/run-02.** L'absence de preuve de chargement dans run-01 est compensée par une preuve directe et complète dans un nouvel essai identifié. Le critère figé « charge le candidat exact » est satisfait dans ce dispositif. Le résultat de run-01 reste BLOCKED.
2. **Méthode instrumentée.** La lecture est explicitement exigée par l'enveloppe générique. Le résultat ne démontre ni le même comportement avec le seul prompt métier ni un chargement automatique spontané. La différence de méthode ne doit pas être supprimée lors d'une synthèse ultérieure.
3. **Provenance des traces.** Le catalogue publié est une reconstruction locale postérieure (`trace.json:52`), pas une capture du catalogue réellement transmis. Le journal analysé est filtré ; la mention d'omission du seul raisonnement interne (`trace.json:42`) provient du superviseur. Aucun événement de raisonnement brut ni prompt-render complet n'a été lu, aucun évaluateur n'a été contacté et aucune empreinte du journal brut n'est présentée comme indépendamment vérifiée. L'absence d'autres commandes est établie dans le journal publié, sans audit indépendant de l'exhaustivité du filtrage.
4. **Injection CLI non certifiée.** La lecture prouve que l'outil a retourné le corps complet. Elle ne prouve ni ne réfute une transmission automatique antérieure du CLI. Le préflight et la trace ne remplacent pas une capture de requête réelle ; modèle et effort non exposés restent inconnus.
5. **Portée de qualification.** Cette revue ne constitue ni validation humaine du skill, ni installation personnelle, ni validation métier de la scorecard, ni mesure fournisseur. Aucun nouveau modèle, essai, accès réseau, lancement applicatif, build, DB ou Docker n'a été exécuté pour cette revue.

Seuls `review.md` et `review.json` sont produits par le relecteur. Les preuves gelées, les entrées A1, run-01 et le candidat restent inchangés.
