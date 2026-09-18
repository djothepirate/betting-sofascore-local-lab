# WO-062 — C4 — revue statique indépendante de Java .3

**Verdict : PASS — cohérence statique uniquement.** Les deux corrections ciblées sont conformes aux décisions et sources lues. Aucun élargissement de responsabilité ou contradiction relevé. La qualification comportementale de cette version reste **NOT_RUN** dans cette revue.

## Identité examinée

- Candidat : `ss-java-module 0.1.0-candidate.3`, **10 384 octets**.
- SHA256 : `15237ab0f6773c87187481622ba4947ebc00f8c7e9a50dd1589911ede17ec332`.
- Commit du corps et HEAD examiné : `cf36a0cea4788402a5da391867c58ef1a479c942`.
- Base de comparaison : `2428914d813ee1dcd05bdac29e25fe8c473c0521`.
- Version précédente : `0.1.0-candidate.2`, SHA256 `255655544771ae6cf2a71b967aba8efeb7e53e64acc41ca31dc784e16a719118`.
- Relecteur : `/root/jm_counter_review` ; contrôle indépendant complémentaire des métadonnées et de l'intégrité : `/root/jm_selection_review`.
- Date : 16 septembre 2026.

## Conclusions sur les deux corrections

### Fin de session après perte du live

Le passage `SKILL.md:85–93` exige désormais de décrire explicitement la fin de session après perte du navigateur ou du contexte live. Il distingue cette panne du retour nominal après la sous-opération J3. La règle vient d'`AGENTS.md:51` et de l'`ADR-SS-007:348/385`. Elle est cohérente avec `LiveCampaignService.runJ3:147–169` et les contrôles du worker `beginJ3/endJ3:282–306`.

Le contexte J3 temporaire autorisé dans le même worker reste permis. Le nettoyage précède toujours le retour nominal. L'ajout n'autorise ni remplacement du live perdu, ni reprise automatique, ni extension de la portée des ordres.

### Cible et portée exactes du garde

Le passage `SKILL.md:99–106` nomme `com.microsoft.playwright` et `src/main`, tout en distinguant les interfaces internes portant le nom Playwright. `Verify-Local.ps1:17–27/42–44` confirme la recherche textuelle correspondante, insensible à la casse, dans les fichiers `.java`, `.yml`, `.yaml` et `.properties` sous cette arborescence.

Le skill conserve explicitement la limite de ce scan : il n'établit pas un graphe exhaustif de dépendances ni une règle ArchUnit exécutée.

## Contrôles

| Critère | Résultat | Preuve | Limite |
|---|---|---|---|
| identity-and-diff | PASS | SKILL.md lu intégralement ; diff 2428914..cf36a0c limité à version .2→.3 et deux passages du corps. 10 384 octets, SHA256 conforme à revision-03/revision.json. | Version .2 conservée comme antécédent ; aucune réussite ancienne transférée. |
| terminal-loss-transition | PASS | SKILL.md 85–93 distingue le retour nominal de la perte du navigateur/contexte live : session terminée, sans reprise automatique ni recréation. AGENTS.md:51 et ADR-SS-007:348/385 portent cette règle. | Règle de session live ; ne supprime ni les ordres J3 autorisés ni le contexte temporaire J3 dans la pause nominale. |
| source-consistency | PASS | LiveCampaignService.java:147–169 interdit la reprise en cas de transport/nettoyage incertain et publie STOPPED ; WorkerMain.java:282–306 exige navigateur connecté et identité du liveContext avant/après le temporaire. | Revue statique des sources ; ni panne injectée ni cycle navigateur exécuté. Le détail des transitions demeure à vérifier lors des futurs essais. |
| exact-textual-guard | PASS | SKILL.md:99–106 demande cible et arborescence exactes ; Verify-Local.ps1:17–27 vise src/main et le motif \\bcom\\.microsoft\\.playwright\\b, puis Select-String insensible à la casse aux lignes 42–44. | Le scan filtre .java/.yml/.yaml/.properties. Ce filtre réel est consigné ici ; le skill ne revendique pas une analyse exhaustive ni un graphe ArchUnit. |
| interfaces-and-library | PASS | Le nom des interfaces internes Playwright est distingué du paquet de la bibliothèque. La séparation worker/profil/classpath existante est conservée. | Aucune interdiction générique des interfaces internes, aucun nouveau module ou mécanisme d'isolation inventé. |
| scope-and-contradictions | PASS | Les deux ajouts renforcent la restitution de règles déjà adoptées. Le contexte J3 temporaire, le nettoyage avant retour, le live conservé, le monomodule et les relais spécialisés restent présents. | Aucun endpoint, transport, automatisation, responsabilité supplémentaire, nouveau pouvoir d'action ou réponse de cas prescrite. |
| metadata-stability | PASS | Sous-revue parallèle indépendante : frontmatter identique à 2428914 hors metadata.version ; name/description inchangés ; agents/openai.yaml strictement identique, SHA256 ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae. | Métadonnées de sélection inchangées ; leur conservation n'établit pas une sélection native réussie. |
| preparation-immutability | PASS | Oracle, cases, inputs, manifest, source-inventory et evaluation-plan identiques à 2428914 et au gel initial 21e16cc ; empreintes recalculées et consignées. | Aucune adaptation de l'oracle ou préparation aux réponses nouvelles. |
| historical-evidence | PASS | Aucun diff des run-01, run-02 ou run-03 ; revision-03/previous-qualification.json est la copie exacte de qualification.json à2428914. | Les échecs antérieurs restent historiques ; leur conservation ne qualifie pas le nouveau corps. |
| revision-record-and-status | PASS | revision.json annonce .3 et les deux seules corrections, base2428914, 8 cas NOT_RUN ; qualification.json porte CORRECTED_PENDING_EVALUATION, 0 nouvelle exécution et 8 NOT_RUN. | Validation humaine et installation personnelle false ; corps .3 au commit cf36a0c vérifié par git log du chemin. |
| read-only-review | PASS | État Git propre à l'ouverture ; lectures, recherches, hashes et git diff --check effectués. Diff whitespace sans anomalie. Seuls les deux rapports de revue sont écrits sous .tmp. | Aucun modèle, probe, test applicatif, navigateur, base de données ou modification du candidat par cette revue. |

## Métadonnées et préparation conservées

Le frontmatter et la description sont strictement identiques à la base, hors changement de `metadata.version`. Le fichier `agents/openai.yaml` conserve SHA256 `ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae`.

Les six pièces préparatoires sont identiques à la base `2428914` et au gel initial `21e16cc` :

| Pièce | SHA256 |
|---|---|
| oracle.md | `d13dc8c5ec833f30dc9025c3c22a22c00e60656eae3a89dc30361861ea390bd1` |
| cases.json | `c3e76e6b92ea2ac3ec3ef03684512b41b70be90355ac1be87bbf3fffc3d228aa` |
| inputs.json | `302410617523c10bd12015ad72d0f9869ca455715528e12008e5a0e3cdf4440f` |
| manifest.json | `a0907baba5a15606b6a735873a3e8ab9cc4043dfe8d852f3b843e1ec61b68290` |
| source-inventory.md | `5a374ccbbbb25e39f87b17909c28083d9bd0567f03d184162c302719c8ddd4ca` |
| evaluation-plan.md | `d015760826da4c55dadff09cea6b91d3e4aea9cf44dff3cfbda02bbfbaab86e7` |

Les run-01/run-02/run-03 restent sans diff. Le fichier `revision-03/previous-qualification.json` conserve exactement l'état de qualification précédent. Le nouvel état annonce huit cas non exécutés pour .3, sans transférer de PASS antérieur.

## Vérifications et limites

Lectures intégrales du candidat, diff et références ciblées ; contrôles SHA256, comparaisons Git/binaires et `git diff --check`. Ce dernier ne signale aucune anomalie.

Le coordinateur a communiqué un premier échec du validateur dû à l'absence de PyYAML, puis une réussite après réutilisation du module local existant. Cette séquence est conservée comme information du coordinateur ; le relecteur n'a pas relancé ce validateur.

Aucun essai modèle, test applicatif, navigateur ou base de données lancé. Aucun candidat, source ou oracle modifié. Seuls les deux rapports de revue sous `.tmp` sont écrits. Ce PASS statique ne préjuge pas des huit réponses à qualifier, d'une validation humaine ou d'une installation personnelle.
