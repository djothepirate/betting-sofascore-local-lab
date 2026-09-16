# WO-062 — Revue statique du publisher C4

**Verdict : FAIL — trois contrôles à renforcer avant publication.** Ce verdict porte
sur le dispositif de publication, sans changer la qualification des skills.

- HEAD : `cb67d18c66aaa3989bfc6121ed7b51945fd95f9f` ; état suivi propre constaté.
- Publisher : `.tmp/publish_c4.py`, SHA-256 `4e31efd63d9f0164e425dc5983454c30e3bb47ad9457511bb30582f389e490a8`.
- Base réutilisée : `.tmp/publish_c_run02.py`, SHA-256 `e1596d4c0731d1e71a521deb173d532aa6f44c14c9da25164e1e2013a871f5cd`.
- Script ni exécuté ni importé. Détail : [JSON](c4-publisher-review.json).

## Défauts concrets

1. **P2 — Revue non liée obligatoirement au run et au gel.** C4 ligne45 appelle
   `verify_review` via `verify_case`. La fonction réutilisée, lignes213–221, ne
   contrôle pas `run_id` et rend `freeze_sha256` optionnel. Une ancienne revue
   Windows `.1` BLOCKED sans réponse peut donc satisfaire les contrôles d'un nouveau
   gel sans réponse du même cas. Exiger le run courant et le hash exact du gel, ou
   une liaison obligatoire équivalente aux traces, avant de reprendre son verdict.
2. **P2 — Reprise Windows et compteurs insuffisamment contraints.** C4 lignes51–58
   reprend les six lignes restantes de l'agrégat run-03, sans imposer précisément
   C01/C02/S01–S04, `source_run=run-01`, PASS, candidat identique et concordance avec
   leurs résultats/revues historiques. Huit identités distinctes ne suffisent pas.
   Exiger cette liste et ses liaisons, ainsi que les totaux attendus de 25 exécutions
   Java et 13 Windows. Le calcul actuel les produit seulement si l'historique fourni
   contient respectivement 17 et 11 exécutions cohérentes.
3. **P2 — Publication partielle possible.** C4 lignes70–73 crée directement le
   répertoire final, puis réécrit `qualification.json` par `write_bytes`. Une erreur
   intermédiaire laisse un run incomplet que `dest.exists()` bloque ensuite ; une
   interruption pendant l'écriture peut tronquer la qualification. Reprendre le
   staging, la revérification préalable et le remplacement atomique déjà présents
   dans la fonction `publish` du publisher réutilisé.

## Contrôles satisfaisants

Les tailles/SHA des entrées et du gel, les identités de trace, les hashes de requête
et de réponse, le journal brut et l'export natif sont contrôlés. Un PASS incompatible
avec une collecte incomplète est refusé. Java `.3` utilise seulement ses huit cas
run-04 : aucun transfert des PASS `.2`. Run-01 à run-03 restent des lectures.
Aucun modèle n'est lancé par ce code ; les sous-processus réutilisés sont des lectures
Git. Validation humaine et installation personnelle restent fausses.

## Portée

Revue statique seulement, sans exécution du publisher, simulation, publication ou
lecture des preuves encore en cours. Aucun verdict de cas, script ou fichier suivi
modifié. Les seuls fichiers écrits sont les deux présents rapports sous `.tmp`.
