# WO-062 — Candidats football quality et CI security

**Rapport historique de recette.** La décision humaine et l’installation personnelles intervenues
ensuite sont consignées dans le [rapport d’installation B](WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md).
Les états d’attente ci-dessous décrivent la fin de la recette, avant cette décision.

- **Date :** 15 septembre 2026.
- **Résultat :** deux candidats `0.1.0-candidate.1`, chacun qualifié sur huit cas figés ; **16 PASS** après revue indépendante.
- **Statut :** `QUALIFIED_ON_FROZEN_CASES_PENDING_OWNER_VALIDATION`.
- **Périmètre :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Livrables et décision

| Candidat | Contenu et qualification | Résultat |
|---|---|---|
| ss-football-quality | [SKILL.md](../skills/local-lab/ss-football-quality/SKILL.md), [métadonnées](../skills/local-lab/ss-football-quality/agents/openai.yaml), [qualification](../skills/evaluations/WO-062/ss-football-quality/qualification.json) | 8 PASS ; identité, horaires, données absentes, incidents, complétude et historique J6. |
| ss-ci-security | [SKILL.md](../skills/local-lab/ss-ci-security/SKILL.md), [métadonnées](../skills/local-lab/ss-ci-security/agents/openai.yaml), [qualification](../skills/evaluations/WO-062/ss-ci-security/qualification.json) | 8 PASS ; SHA, contrôles exigés, XML, conservation, scans et distribution locale. |

Le propriétaire a demandé de réaliser ces deux skills, puis a autorisé explicitement les
seize sessions éphémères et la transmission au service de modèle configuré du candidat,
du catalogue local et des seules entrées internes prévues par cas. Le premier lancement
avait été refusé par le contrôle automatique, l'autorisation précédente étant limitée à PB.
Aucune session FQ/CS n'a été lancée avant cette autorisation complémentaire.

**La validation humaine du contenu et des limites de ces deux versions reste à recueillir.**
Les candidats sont versionnés dans le dépôt ; aucune installation personnelle FQ/CS n'est
réalisée. L'autorisation et l'installation antérieures de PB restent propres à PB.

## Préparation et identité des versions

Les [25 sources football](../skills/evaluations/WO-062/ss-football-quality/source-inventory.md)
et [25 sources CI](../skills/evaluations/WO-062/ss-ci-security/source-inventory.md), les cas,
entrées, critères attendus et plans ont été gelés au commit `15158a5`, avant rédaction.
Les sources ont pour base `57c0614627e628077b0a7775eb66a7c490b422e0`.
Leurs manifestes conservent taille/SHA-256 de la matérialisation et OID du blob Git.
Les mentions PREPARED_NOT_RUN restent l'état historique exact de ce gel.

| Pièce | SHA-256 |
|---|---|
| Manifeste de préparation football | `ab5cf47000fe47b9fc76541ecf9e29a9a429e17d9947c86004078ae850f49adc` |
| Manifeste de préparation CI | `6691b5a6be971320b35b62e7da93fb0d28cff7c56afb91f94e111cfad66ee570` |
| Corps ss-football-quality | `8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58` |
| Métadonnées football livrées | `8d295af442808d3efebbe68e8165ef96efd086b5b84908fc12f04c1c66bf51f2` |
| Corps ss-ci-security | `ca0bba1cf60c0c9965f21b3201e39c3e5dcba3bd73888c2a2ee5d148dbb8c0c5` |
| Métadonnées CI | `621ecacc03c3acf895d2bd5c87ed94461512e23a6e16cc321ae1818f0962d959` |

Football a été rédigé au commit `bfbe3fb`, ses métadonnées corrigées au commit `11394e3`,
puis CI rédigé au commit `658309c`. Les recettes ont été lancées dans cet ordre ; les
revues et essais indépendants des deux domaines ont pu se poursuivre en parallèle.

## Résultats des seize essais

| Cas football | Objet | Verdict |
|---|---|---|
| FQ-H01 | WO-017, snapshot 717, séance sans minutes et périmètre V15 | PASS |
| FQ-N01 | Nouvel audit WO-058 : snapshot 2340, versions actives, projection J6 V4 | PASS |
| FQ-C01 | Identité/HOME-AWAY, journée de 25 heures, reprogrammation, scores/statistiques/compositions | PASS |
| FQ-C02 | Penalty awarded, séances invalides, ambiguïtés et neuf transitions J6 | PASS |
| FQ-S01 / S02 | Sélection explicite / implicite et lecture intégrale du candidat | PASS / PASS |
| FQ-S03 / S04 | Routage lot 1 / exclusion d'un autre projet | PASS / PASS |

| Cas CI | Objet | Verdict |
|---|---|---|
| CS-H01 | WO-061 : tests réussis puis quota, portée de la qualification historique | PASS |
| CS-N01 | Nouvelle grille sur dossier synthétique : SHA PR/merge, tests, upload et obligations | PASS |
| CS-C01 | XML absent/invalide/incohérent, zéro test, rapports obligatoires et after_script | PASS |
| CS-C02 | Promotion, version, SBOM, reproductibilité, scanners et exemptions de secrets | PASS |
| CS-S01 / S02 | Sélection explicite / implicite et lecture intégrale du candidat | PASS / PASS |
| CS-S03 / S04 | Routage lot 1 / exclusion d'un autre projet | PASS / PASS |

Les [résultats football](../skills/evaluations/WO-062/ss-football-quality/run-01/results.json)
et [résultats CI](../skills/evaluations/WO-062/ss-ci-security/run-01/results.json) relient
chaque réponse figée à sa revue détaillée, son verdict et son empreinte. Les omissions
mineures et limites restent dans les revues ; elles ne sont pas effacées par le bilan PASS.

Deux nouveaux livrables utiles sont produits : l'[audit WO-058](../skills/evaluations/WO-062/ss-football-quality/run-01/cases/FQ-N01/response.md)
et la [grille CI](../skills/evaluations/WO-062/ss-ci-security/run-01/cases/CS-N01/response.md).
La première analyse des preuves locales existantes ; la seconde applique la politique réelle
à un dossier synthétique déclaré. Elle ne qualifie pas une PR réelle de WO-062.

## Indépendance, instrumentation et limites

Chaque essai utilise un répertoire neuf, un dépôt Git isolé et Codex CLI
`0.154.0-alpha.6.2`, en mode éphémère et lecture seule. Le candidat est copié dans
`.agents/skills` de ce contexte. Seuls son cas et ses sources autorisées sont fournis ;
ni oracle, ni inventaire commenté, ni résultats précédents, ni historique de conception.
Les outils réels, réponses, durée et usage disponibles sont conservés dans les traces.
Modèle et effort suivent les réglages configurés ; leurs valeurs exactes ne sont pas
exposées dans les événements disponibles et ne sont pas inventées.

L'enveloppe de sélection, figée avant rédaction, exige génériquement la lecture intégrale
du skill choisi et l'arrêt au routage. La preuve porte donc sur **sélection et lecture
par outil instrumentées**. Elle ne certifie pas une injection automatique du corps par
le CLI. Le catalogue est reconstruit localement après exécution ; il ne constitue pas
une capture de la requête envoyée. Chaque candidat a été confronté au catalogue personnel
existant, sans installation personnelle des deux nouveaux skills ni essai conjoint de leur
coexistence. La consolidation du lot complet reste une étape distincte.

Les sorties ont été figées avant revue. Les relecteurs ont ensuite reçu l'oracle et les
preuves, sans intervenir dans les sessions. Les journaux publiés conservent les commandes
et leurs sorties ; les événements de raisonnement interne sont omis. Les réponses restent
inchangées, avec leurs chemins de contexte d'exécution ; les inventaires et manifestes de
sources fournissent la correspondance au dépôt versionné. Aucun gain de productivité,
comparaison A/B ou généralisation hors de ces cas n'est mesuré.

### Défaut d'encodage trouvé et corrigé

La [revue statique football](../skills/evaluations/WO-062/ss-football-quality/run-01/review-static.md)
a détecté que le générateur officiel avait écrit `agents/openai.yaml` en encodage Windows
CP1252. Les trois textes ont été réenregistrés en UTF-8 sans changement Unicode ni changement
du SKILL.md. Les préflights initial et corrigé conservent la transition avant les six essais
non démarrés. H01/N01, déjà en cours, gardent les métadonnées initiales et leurs empreintes :
ils qualifient le corps métier exact, pas ce YAML ancien, ignoré par le CLI.
Les contre-épreuves et quatre sélections football utilisent le YAML UTF-8 livré.
La structure, l'encodage et la découverte de cette version sont vérifiés séparément.
Les huit essais CI utilisent dès le départ leurs deux fichiers livrés exacts.

La [revue statique CI](../skills/evaluations/WO-062/ss-ci-security/run-01/review-static.md)
ne relève aucun défaut matériel. Ces revues statiques sont distinctes des recettes métier.

## Contrôles de livraison

Les [résultats structurés des contrôles](WO062-FOOTBALL-QUALITY-CI-SECURITY-CHECKS-20260915.json)
conservent leur périmètre, les commandes et la distinction entre nouveaux contrôles de skills
et suite applicative déjà exécutée sur le code inchangé.

- Génération `agents/openai.yaml` avec l'outil officiel et validation `quick_validate.py` : PASS pour les deux SKILL.md ; contrôle UTF-8 et métadonnées livrées : PASS.
- Sources et préparation figées, entrées, réponses, revues et manifestes taille/SHA-256 : cohérence vérifiée. Le WO est une source de cadrage datée, exclue des entrées évaluées ; sa mise à jour ne réécrit pas le gel.
- Diff documentaire, liens du guide/WO/rapport, encodage et secrets : contrôles de livraison consignés avec les preuves.
- Code applicatif, tests, migrations, workflows, ADR, installateur, paquet lot 1, attestations SKL-002 et tout le dossier PB conservés depuis `57c0614`.

La suite applicative de ce WO porte sur ce même code inchangé :
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1`,
soit `mvnw.cmd -DskipITs clean verify`, puis `java ci/VerifyTestReports.java standard` :
**2 440 déclarés, 2 435 exécutés, cinq ignorés, zéro échec/erreur**, fin
`2026-09-15T15:58:40Z`. La [preuve de cadrage](WO062-SKILLS-LOT2-SCOPING-20260915.md)
est réutilisée pour ce code inchangé ; aucun nouveau lancement Maven n'est revendiqué.
Les nouvelles vérifications portent sur les skills et leurs preuves. Aucun test de
persistance, pipeline distant, scanner réel ou collecte n'est exécuté dans ces recettes.

## Suite et état Git

WO-062 reste actif sur `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`, cible
`feature/V0.1.0-RC01`. Validation humaine FQ/CS, installation correspondante et étapes C/D
restent distinctes. Aucun push, PR, fusion, promotion, tag ou clôture n'est réalisé ici.
Fichiers livrés : quatre fichiers candidats, deux dossiers de préparation/évaluation,
ce rapport et leurs renvois dans WO-062, README, guide des skills et CHANGELOG ;
`.gitattributes` protège les octets des preuves.
