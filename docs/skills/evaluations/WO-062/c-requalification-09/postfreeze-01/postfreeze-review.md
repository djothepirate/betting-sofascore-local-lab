# C9 — revue postfreeze Windows

## Verdict

`INCOMPLETE_QUALIFICATION` — `WR-H01` est **PASS** et `WR-N01` est **BLOCKED**.

Cette revue est distincte de l'exécution : elle a relu exclusivement les fichiers gelés,
vérifié chaque taille et SHA-256 déclarés par les deux manifests de gel, et n'a lancé ni
session de modèle, ni PowerShell, ni JVM. Elle ne constitue pas une validation humaine.

## WR-H01 — PASS

- Le contexte, le candidat et les entrées sont restés intègres ; l'audit de lecture est PASS.
- Le harnais réparé a produit le code natif `0`, les deux itérations, le marqueur distinct
  `WR_PROMPT_RETURNED` et un postflight distinct.
- Le collecteur intégré a accepté `output/runtime` car son parent était vide ; aucune
  suppression n'a été tentée.
- La session a duré 391,672 s : sous le watchdog de 900 s, mais au-dessus de l'objectif
  opérationnel de six minutes. Cet écart n'annule pas les preuves fonctionnelles.

## WR-N01 — BLOCKED

- Le préflight PS5.1 s'est terminé `PREFLIGHT_VERIFIED_NO_JAVA`.
- L'invocation normale a refusé d'affirmer l'identité de `failure` après
  `Process main-module image is empty`. Elle n'a pas exécuté `sleep` et n'a forcé aucun arrêt.
- Le collecteur a relevé une racine temporaire détenue réellement non vide. Elle reste
  conservée comme preuve ; le parent `output/runtime` n'est pas confondu avec ce résidu.
- La campagne ne démontre donc ni le code 23 de `failure`, ni READY, ni l'expiration de
  `sleep`, ni le nettoyage complet. La cause de l'image vide reste `NOT_ESTABLISHED`.

## État

Le bilan courant exact du candidat devient **7 PASS / 0 FAIL / 1 BLOCKED**. Les preuves
historiques C4/C5 et leurs verdicts restent inchangés. Il n'y a ni validation humaine,
ni installation personnelle. Une future reprise requiert une analyse ciblée et une nouvelle
autorisation explicite ; cette campagne ne doit pas être rejouée automatiquement.
