# Revue statique séparée — `ss-java-module` candidate.5

**Verdict : `PASS_STATIC_ONLY`.** Cette revue relit séparément l'édition du
candidat, ses octets, le diff, les preuves C6/C7 et l'état de qualification. Elle
n'exécute aucun modèle, aucune session de qualification, aucun test applicatif et
ne constitue ni une validation humaine ni une installation personnelle.

## Objet relu

- Candidat actif : `0.1.0-candidate.5`, SHA-256
  `d7171f978c2b13c852c2ce5e170d68c1bfc5087552661cfdc6271077a0fe6637`
  (`11 263` octets).
- Archive historique : `0.1.0-candidate.4`, SHA-256
  `f1f6533f678c5cdb4ae6a5bc9c996c062af419005e4f4cd6104a51c91e442d97`
  (`10 901` octets).
- Métadonnées `agents/openai.yaml` : SHA-256 identique pour les deux candidats,
  `ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae`.

## Résultat de la relecture

Le diff `.4` → `.5` contient exactement deux blocs : le numéro de version et la
règle de pause live/J3. Cette dernière dit désormais explicitement que le contexte
J3 temporaire reste isolé du contexte live conservé ; aucun cookie, état de
stockage (`storageState`) ou autre donnée de session n'est transféré du live vers
J3, ni employé pour fabriquer une continuité de session. Elle impose aussi de
restituer cette interdiction dans la réponse lorsque la pause est examinée.

Les trois corrections acquises de `.4` sont encore présentes : perte du navigateur
ou du contexte live terminale sans reprise ni recréation, garde ciblant exactement
`com.microsoft.playwright` dans `src/main`, et profil `sofascore-live-test` maintenu
bloqué. Les métadonnées, le code applicatif, les fixtures, oracles, critères et les
preuves C6/C7 ne sont pas modifiés. L'archive `.4` et sa qualification restent
cohérentes avec le résultat historique **7 PASS / 1 FAIL**.

Les contrôles de forme du front matter, de version, de hashes, de périmètre Git et
`git diff --check` sont passants. Le validateur standard `quick_validate.py` n'a pas
pu démarrer, car le Python fourni ne contient pas `PyYAML`, dépendance requise par
cet outil ; aucun paquet n'a été installé. La revue consigne donc ce contrôle comme
non disponible, sans le faire passer pour une qualification.

## Limites et suite

Il s'agit d'une revue statique déterministe, séparée de l'opération d'édition ; ce
n'est pas une seconde session de modèle ni une revue humaine. Le comportement de
la réponse `.5` reste à établir. Le statut reste donc
**`PREPARED_NOT_QUALIFIED`**, avec zéro cas `.5` exécuté.

Le protocole attaché au SHA du candidat impose huit nouveaux cas pour qualifier
formellement `.5` : `JM-H01`, `JM-N01`, `JM-C01`, `JM-C02`, `JM-S01`, `JM-S02`,
`JM-S03` et `JM-S04`. Une future exécution isolée de `JM-N01` ne serait qu'une
observation ciblée et ne permettrait pas de transférer les sept PASS `.4`.
