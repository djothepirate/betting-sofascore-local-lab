# WO-062 — C4 — revue du correctif de compatibilité des schémas historiques

**Verdict : PASS pour cette correction isolée.** La version historique est désormais lue depuis le preflight déjà couvert par le manifeste. Les désaccords de candidat ou de version restent bloquants ; aucun contrôle des nouvelles revues C4 n'est assoupli.

## Identité et comparaison

- HEAD : `cb67d18c66aaa3989bfc6121ed7b51945fd95f9f`.
- Script actuel : `.tmp/publish_c4.py`.
- SHA256 actuel : `8ffbd5bbf50e17578ed193f2874e462c9fd04c92cfdf346c91051ad0e5dea8dc`.
- Copie précédente figée : `docs/skills/evaluations/WO-062/c-requalification-04/publication-support/publisher.py.txt`.
- SHA256 précédent : `702819539f583435082609adf703e87f38a18c40aa7dcd38640bc5e872a49985`.
- Relecteur : `/root/jm_counter_review`, 16 septembre 2026.

## Correction vérifiée

Le diff est limité aux lignes62–68 de la boucle historique :

1. `verify_manifest` fournit les octets vérifiés du run historique.
2. Le hash de ce manifeste reste confronté à celui attendu.
3. La version vient de `old['preflight.json']`.
4. Le hash candidat du preflight doit être identique à celui de `results.json`.
5. Lorsque `results.json` contient une version, elle doit aussi être identique.
6. L'entrée historique publiée reprend cette version vérifiée.

Il ne s'agit pas d'une version devinée depuis le candidat courant. L'absence du champ reste permise uniquement dans le résultat historique ; le preflight doit le porter.

## Contrôles

| Critère | Résultat | Preuve | Limite |
|---|---|---|---|
| delta-limited | PASS | Diff contre publication-support/publisher.py.txt : seul le bloc historique62–68 change ; ajout de lecture old['preflight.json'], comparaison de hash candidat et comparaison de version si présente dans results. | Aucun import/lancement du publieur ; comparaison de code uniquement. |
| verified-version-source | PASS | La version provient des octets old['preflight.json'] retournés par verify_manifest ; le hash du manifest est confronté à expected_manifests avant cette lecture. | La version historique doit toujours exister dans le preflight ; aucune valeur actuelle ou supposée n'est substituée. |
| identity-preserved | PASS | oldpre.candidate_sha256 == res.candidate_sha256 obligatoire ; si results expose candidate_version, égalité avec preflight obligatoire. L'agrégat reprend alors la version du preflight et le hash du résultat. | Un désaccord de hash/version reste bloquant ; le correctif ne tolère que l'absence historique du champ dans results. |
| actual-historical-schemas | PASS | Six couples preflight/results lus pour Java et Windows run01–03. Les deux run01 n'ont pas candidate_version dans results ; leurs preflight la portent. Les quatre autres versions concordent et les six hashes candidat sont égaux. | Les douze fichiers correspondent à leurs entrées de manifest ; aucun historique réécrit. |
| previous-controls-unchanged | PASS | Le diff laisse inchangés les contrôles run/skill/version/freeze des revues C4, les six reprises Windows, les compteurs25/13 et publish_validated avec staging/replace/rollback. | L'échec ultérieur pour skill absent de WR-H01 est rapporté par le coordinateur ; aucun assouplissement ni correction de cette revue par le présent relecteur. |
| frozen-support-and-evidence-scope | PASS | Les 9 pièces de publication-support restent conformes à leur manifeste ; ancien publisher SHA70281953…49985, anciennes revues et 4 tests conservés. | Les 4 tests fournis portent sur la version précédente et la fonction de publication inchangée ; ils ne sont pas présentés comme une nouvelle exécution de ce script corrigé. |

Les deux `run-01/results.json` n'ont effectivement pas `candidate_version`. Leurs preflight déclarent Java `.1` et Windows `.1`. Les autres historiques déclarent Java `.2` et Windows `.1` avec versions identiques dans les résultats. Les six hashes candidat concordent ; les douze fichiers lus correspondent aux entrées de leurs manifestes.

## Portée des preuves

Le premier dry-run ayant révélé le champ absent et le second ayant atteint le contrôle de revue WR-H01 sont des résultats communiqués par le coordinateur. Le présent relecteur n'a pas exécuté ces commandes. La garde `skill` obligatoire reste inchangée ; le défaut de métadonnée de cette revue doit rester traité par son auteur.

Les neuf pièces du support précédent restent conformes à leur manifeste. Les quatre tests antérieurs ne sont pas attribués à une nouvelle exécution du script corrigé, même si la fonction de publication testée est inchangée.

Aucun import, lancement de publieur, modèle, probe, test ou délégation. Aucun candidat, source, preuve ancienne ou fichier suivi modifié par cette revue. Les modifications documentaires du coordinateur constatées à l'ouverture ont été laissées intactes.
