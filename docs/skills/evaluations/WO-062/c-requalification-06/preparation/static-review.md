# Revue statique C6 avant `JM-H01`

**Verdict : `PASS_PRELAUNCH`.** Cette revue porte seulement sur l'autorisation, le
lanceur et le contexte neufs de `run-06`. Elle ne lance ni modèle, ni build, ni test,
ni application.

| Contrôle | Résultat | Preuve |
| --- | --- | --- |
| Portée | PASS | `authorization.json` limite C6 à une session `JM-H01`, candidat Java `.4`, sans relance automatique. |
| Isolation | PASS | Le lanceur C6 refuse tout autre run, skill ou cas, et impose `workers=1`. |
| Fraîcheur | PASS | Le contexte est créé sous `run-06`; les sorties, réponse et gel sont absents avant lancement. |
| Entrées | PASS | Les quatorze entrées, le prompt et la demande sont identiques au contrat C5 original par SHA-256, mais copiés depuis les sources canoniques dans une nouvelle racine. |
| Indépendance | PASS | Oracle, réponses, revues, résultats, plans, inventaires, traces C5/C4 et matériel Windows sont exclus de la racine modèle. |
| Candidate | PASS | `SKILL.md` est `0.1.0-candidate.4`, 10 901 octets, SHA-256 `f1f6533f678c5cdb4ae6a5bc9c996c062af419005e4f4cd6104a51c91e442d97`. |

Le préflight C6 distingue le commit de référence de contenu C5
`d0021090d9cf718d5b338bbf8d06109586e4518a` du HEAD de préparation C6. Cette comparaison
est effectuée hors du contexte envoyé au modèle ; elle ne fournit ni oracle ni réponse
antérieure à la session.

La prochaine étape autorisée est une seule commande hôte `codex exec --ephemeral --sandbox
read-only` pour `JM-H01`. Une absence de réponse, une erreur de transport ou un timeout est
gelé comme résultat C6 unique et ne déclenche pas de relance automatique.
