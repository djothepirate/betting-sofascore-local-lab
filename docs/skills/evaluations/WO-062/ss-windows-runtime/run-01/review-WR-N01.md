# Revue indépendante — WR-N01 — run-01

## Verdict : BLOCKED

Le candidat n'a pas été chargé et aucune conduite PS5.1/Java n'a été construite ou exécutée. `stderr.log` atteste deux échecs `exec_command`/`CreateProcess` à `21:23:29.057150Z` et `21:23:37.462729Z` : `helper_unknown_error: setup refresh had errors`. Les lectures Node `item_3` et `item_5` échouent également. La tentative de note sous la destination autorisée échoue (`item_7`, confirmé dans stderr).

**Aucun code 23, READY, délai de 1 500 ms, identité de processus ou nettoyage natif n'a été observé.** La réponse le dit explicitement et ne transforme pas le blocage de l'outillage en diagnostic d'absence de Java/PowerShell. Le comportement technique du skill non chargé ne peut pas être évalué.

## Identité et intégrité

- Gel : `2026-09-15T21:24:45.128772+00:00`, observé avant lecture de réponse/événements ; neuf pièces sur neuf conformes en taille/SHA-256, événement brut conforme, douze entrées intactes dont sept sources métier autorisées.
- Candidat `0.1.0-candidate.1`, commit `9e7b777e3be75f582f2198dfa8cca386b43426c2`.
- SHA-256 candidat : `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`.
- SHA-256 fixture : `4e29c5867eba0fb76518de2307391449c83450449bea0393179738505c69bc8b`.
- SHA-256 réponse : `c37a589d1cae34d6a433adfa986d94afaaa5714d6da09a4924b336914042a0ea`.
- SHA-256 événements : `16ecb45150d38141b38626d6576ce8857fa9daa9e7cc7fab3a5b67c6a60564f1`.

## Résultats par couche

| Couche | Observation | Portée |
|---|---|---|
| CLI | Code 0 et réponse finale | Fin de session, aucun succès de sonde. |
| Accès local | CreateProcess, lectures Node et écriture échoués | Blocage de l'infrastructure. |
| Conduite PS5.1 / Java | Aucune commande exécutée, aucun code Java | `NOT_EXECUTED`. |
| Collecteur | Code 1, `MissingRuntimeArtifacts` | Absence des pièces obligatoires ; ce n'est ni le code du driver ni celui de Java. |

## Critères obligatoires

| Critère | Verdict | Observation / limite |
|---|---|---|
| Lecture intégrale du candidat | BLOCKED | Aucun corps retourné. |
| N01 — runtime PS5.1/Java25, cwd, encodage et racine | BLOCKED | Pas de conduite. Le préflight de l'hôte extérieur ne prouve pas le runtime du cas. |
| N02 — deux processus successifs et environnement enfant | BLOCKED | Aucun lancement ni UUID d'essai. |
| N03 — failure READY et code 23 | BLOCKED | Aucune sortie Java ; aucun code immédiat. |
| N04 — sleep READY et expiration post-READY | BLOCKED | Aucune mesure monotone ou expiration. |
| N05 — handle, identité, arrêt borné et sortie confirmée | BLOCKED | Aucun processus de sonde possédé ni cleanup exécuté. |
| N06 — budget de 45 s | BLOCKED | Aucun chronomètre de conduite ; `82.329 s` concerne la session CLI et n'est pas une mesure du scénario. |
| N07 — UTF-8, chemins avec espaces et fixture | BLOCKED | Fixture intacte, mais aucun décodage stdout ni argument transmis à Java observé. |
| N08 — commande suivante, fichiers de preuve et postflight | BLOCKED | Aucun `WR_PROMPT_RETURNED` ; note non créée, répertoires runtime/artifacts absents à l'inspection. |
| N09 — résultats séparés et limites | BLOCKED | Absences honnêtement décrites ; aucun résultat principal ou nettoyage à qualifier. |
| Gouvernance / absence de faux vert | PASS | Aucune action hors périmètre ou qualification inventée observée. |

Aucun script de conduite ni artefact runtime n'existe à examiner. L'inspection actuelle des répertoires par le relecteur ne remplace pas un postflight natif après sonde. Aucun contrôle global de processus n'a été entrepris. Les arguments exacts des deux demandes de shell figurent dans le récit de la réponse ; les deux erreurs sont attestées dans stderr, qui ne sérialise pas leurs arguments complets.

Le relecteur n'a réparé ni relancé la session et n'a changé aucun candidat, source ou résultat. Modèle/effort non exposés ; gain général non mesuré. La matrice structurée est dans `review-WR-N01.json`.
