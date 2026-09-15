# WO-062 — Publisher run-02 : revue et vérification simulée

**PASS — vérifié uniquement sur fixtures synthétiques.** Aucune publication réelle ni session de modèle lancée par cette sous-tâche.

- Révision initiale : `1bf14ddadd38ee4cbba34ee09bf2c729fb0ab890`, état Git propre au début.
- Publisher : `.tmp/publish_c_run02.py` ; SHA-256 `e1596d4c0731d1e71a521deb173d532aa6f44c14c9da25164e1e2013a871f5cd`.
- Tests : `.tmp/test_c_run02_publisher.py` ; SHA-256 `ebdc7572ab98d48bc4939c2af4c51e109c9da778230036d541be447b92ff9664`.
- Résultat final : **31 tests, 0 échec, 0 erreur**, code 0. Aucun appel modèle, sonde ou processus externe dans les tests.
- Date de la revue : 2026-09-15 21:56:24 UTC.

## Contrat publié

| Élément | Java | Windows |
|---|---|---|
| Candidat courant | 0.1.0-candidate.2 | 0.1.0-candidate.1 |
| Nouveaux cas autorisés | 8 cas JM | WR-H01 et WR-N01 uniquement |
| run-02/results.json | 8 verdicts réellement revus | 2 verdicts réellement revus |
| Qualification courante | 8 résultats .2 seulement | 6 PASS du run-01 + les 2 verdicts du run-02 |
| executions | 8 pour .2 | 10 pour .1 |
| total_executions | 16, dont historique .1 | 10 |
| Historique conservé | .1 : 5 PASS / 3 FAIL | run-01 : 6 PASS / 2 BLOCKED |

Les six copies Windows préparées mais non exécutées restent dans le préflight avec leurs empreintes ; elles sont explicitement exclues du run publié. Aucun résultat ni revue de ces copies n’est copié.

`current-results.json` conserve huit cas courants avec leur `source_run`. Les références Windows conservées pointent vers `../run-01/`. La qualification garde `source_commit`, `candidate_files`, les compteurs courants, les liens de résultats, les historiques et les empreintes des manifests.

## Contrôles

- **AUTH — PASS** : Exact owner reply, ten-session limit, ordered eight Java .2 cases and two Windows .1 cases, false human/install flags. Other attempted cases or additional runs are refused.
- **INPUTS — PASS** : All eight prepared input sets of the selected skill are hashed, including the six unexecuted Windows copies. Canonical and isolated candidate bodies/metadata, prompt/request, preparation manifest and sandbox mode are checked.
- **FREEZE — PASS** : Strict path and inventory checks reject traversal, duplicate entries, missing/unlisted files, links/junctions and size/SHA mismatch. Raw/native events, trace fields, final response, runtime artifacts and review bindings are checked before publication.
- **VERDICTS — PASS** : Only review PASS/FAIL/BLOCKED is accepted. A PASS contradicting completion/collector evidence is refused. Valid FAIL/BLOCKED and a reviewed missing-response BLOCKED remain unchanged. Static failure prevents complete qualification.
- **HISTORY — PASS** : Run-01 artifact manifests are pinned and verified, including re-sealed tampering refusal. Java retains .1 five PASS/three FAIL; Windows retains six PASS/two BLOCKED. No run-01 write operation exists.
- **AGGREGATION — PASS** : Java current qualification uses only eight .2 outcomes, executions=8 and total_executions=16. Windows current eight cases comprise six run-01 cases plus two run-02 cases, executions=10. Historical attempts and current verdict counts are separate.
- **PUBLICATION — PASS** : run-02/results.json contains only authorized executed cases; six prepared Windows cases are explicitly excluded. Only named frozen case files, their corresponding reviews, exact static review, preflight, authorization and unchanged reviewed launcher/17-mock evidence are copied. Derived current-results/provenance/manifest are generated.
- **PROVENANCE — PASS** : Qualification keeps source_commit, source_head, candidate_files, body/package/publisher commits, hashes, result links and historical records. Real default provenance checks source-head ancestry and the candidate body at that commit using read-only Git.
- **WRITE — PASS** : Validate-only default; explicit --publish required. Existing run-02 is refused. Input fingerprints and budget checked again before promotion. Qualification replacement is atomic; simulated failure moves only the newly created publication back to its owned staging path.
- **LIMITS — PASS** : Human validation and personal installation remain false; model/effort remain unknown. Catalogue reconstruction and the narrow Windows synthetic probes retain their stated limitations.

## Usage prévu pour la coordination

Depuis le worktree, avec le Python local déjà identifié :

```text
python.exe -X utf8 .tmp/publish_c_run02.py ss-java-module
python.exe -X utf8 .tmp/publish_c_run02.py ss-windows-runtime
```

Ces commandes valident seulement. Ajouter `--publish` réalise la publication locale après toutes les validations. La commande refuse une destination run-02 existante. Aucune commande de publication réelle n’a été exécutée pendant cette sous-tâche.

Commande de vérification réellement exécutée :

```text
C:/Users/geoff/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe -X utf8 .tmp/test_c_run02_publisher.py
```

Les passages intermédiaires à 27 puis 30 tests ont réussi ; le passage final à 31 couvre aussi le refus d’un mode workspace-write Java. Le dernier résumé et son dossier UUID constituent la preuve courante.

## Préservation

Le publisher run-01, le lanceur et ses 17 mocks restent inchangés. Les manifests historiques restent exactement `6b6b057a742ebd8f3b064adf4be7b57ac02ff9309ad76cf7b3599b7e72e56649` (Java) et `9203e756a55fbba088f4d33beb42b79894edb0a2f0aa654565b7da3e54ee6bd8` (Windows). La vérification simulée prouve aussi l’absence de modification du run-01 après une publication de fixture.

## Limites

- No real run-02 build_plan or publication was performed in this subtask; final native-case readiness and real publication are left to the coordinator after all independent reviews exist.
- Tests use small synthetic repositories and inject fixed Git provenance; they launch no model, runtime probe or external process. The default real read-only Git checks run during the coordinator's validation.
- This verifies the publisher, not any candidate skill or Windows runtime behavior. Existing 17 launcher mocks are checked by their reviewed hashes/results, not rerun here.
- On a staging failure, owned staging files may remain for inspection; no broad cleanup or deletion is attempted.
- Tracked documentation changes observed at the end belong to the coordinating task; this subtask writes only .tmp.

Les portées du catalogue reconstruit après exécution, de la capture d’arguments PowerShell 7 et de la sonde Java synthétique sous Windows PowerShell 5.1 sont conservées. Aucun gain général de temps/qualité n’est revendiqué. Validation humaine et installation personnelle restent fausses.

