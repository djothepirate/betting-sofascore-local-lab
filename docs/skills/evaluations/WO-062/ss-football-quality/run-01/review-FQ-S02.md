# Revue indépendante — FQ-S02

## Verdict : PASS — Sélection implicite

La demande ne nomme aucun skill. Le candidat retient seulement ss-football-quality et relie identité/horaire, HOME/AWAY, séance sans minute et historique J6 aux sections pertinentes du corps lu.

La revue applique l’oracle v1 et l’enveloppe de sélection de `docs/skills/evaluations/WO-062/ss-football-quality/cases.json`. Le gel existait avant lecture de la réponse. Portée : EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Intégrité et protocole

- Les six pièces du gel correspondent à `freeze.json`, en tailles et SHA-256.
- Réponse : `3d918d61364bafa9af0f8a4d87a8bdaf9d75a4aba132b06e96e0ecf64f6cfcb5` ; identique au final natif `item_4`, hors fin de ligne terminale.
- Événements natifs : `51897c08ad487f68b73f46288270cd5b5ad195a938a95edb865bc7ad99c03b09`.
- Catalogue : `5a01ab969fd197a2cb273ed8e7f69360f52f1d5f8615cea71d5bc4140ba14710`.
- Le prompt du cas et l’enveloppe de routage sont exacts ; aucun attendu supplémentaire n’est introduit dans la requête.
- Les entrées de `preflight.json`, la trace et les deux fichiers copiés concordent. Le corps du candidat reste identique au préflight original : `8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58`.
- Le YAML de cet essai est la version corrigée UTF-8 : `8d295af442808d3efebbe68e8165ef96efd086b5b84908fc12f04c1c66bf51f2`, 305 octets. SKILL.md et YAML se décodent strictement en UTF-8. Cette vérification n’est pas une installation personnelle.

## Catalogue et preuve native

Le catalogue reconstruit et figé inclut ss-football-quality avec la racine r6 propre à FQ-S02, ainsi que ss-verify et les autres skills du lot 1 sous r0. Les décisions et chemins effectivement utilisés sont cohérents avec ces entrées. Sa provenance est déclarée « Local reconstruction after execution, not a capture of the transmitted request ».

item_1 lit le chemin absolu de la copie isolée FQ-S02/.agents/skills/ss-football-quality/SKILL.md avec Get-Content -Raw -LiteralPath. Code de sortie 0 ; sortie complète de 7 142 caractères identique au fichier de 7 140 caractères hors ajout de fin de ligne terminale ; aucune troncature.

Le parcours chaîne de preuve → identité/horaires/HOME-AWAY → incidents → J6 sert à expliquer la sélection. Le final précise qu’il ne conclut pas sur la validité des écarts et s’arrête au routage ; aucune tâche métier n’est exécutée.

## Critères obligatoires

| Critère | État | Preuve |
|---|---|---|
| P1_FREEZE | PASS | Les six pièces figées correspondent à leurs hashes et tailles. Réponse SHA-256 3d918d61364bafa9af0f8a4d87a8bdaf9d75a4aba132b06e96e0ecf64f6cfcb5 égale au final natif item_4, hors fin de ligne terminale. |
| P2_EXACT_ENVELOPE | PASS | prompt.txt correspond au prompt du cas dans cases.json ; request.txt correspond exactement à selection_envelope avec remplacement unique du prompt, hors fin de ligne terminale. Aucun attendu ajouté. |
| P3_CATALOGUE_PROVENANCE | PASS | Le catalogue reconstruit et figé inclut ss-football-quality avec la racine r6 propre à FQ-S02, ainsi que ss-verify et les autres skills du lot 1 sous r0. Les décisions et chemins effectivement utilisés sont cohérents avec ces entrées. Sa provenance est déclarée « Local reconstruction after execution, not a capture of the transmitted request ». |
| P4_CANDIDATE_BEFORE_AFTER | PASS | Les deux entrées SKILL/YAML concordent entre préflight, trace et copies relues après exécution. SKILL SHA-256 8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58; YAML UTF-8 SHA-256 8d295af442808d3efebbe68e8165ef96efd086b5b84908fc12f04c1c66bf51f2. Corps inchangé par rapport au préflight original ; les deux fichiers se décodent en UTF-8 strict. |
| P5_FULL_CANDIDATE_TOOL_READ | PASS | item_1 lit le chemin absolu de la copie isolée FQ-S02/.agents/skills/ss-football-quality/SKILL.md avec Get-Content -Raw -LiteralPath. Code de sortie 0 ; sortie complète de 7 142 caractères identique au fichier de 7 140 caractères hors ajout de fin de ligne terminale ; aucune troncature. |
| FQ-S02_ROUTING | PASS | La demande ne nomme aucun skill. Le candidat retient seulement ss-football-quality et relie identité/horaire, HOME/AWAY, séance sans minute et historique J6 aux sections pertinentes du corps lu. |
| P6_STOP_AT_ROUTING | PASS | Le parcours chaîne de preuve → identité/horaires/HOME-AWAY → incidents → J6 sert à expliquer la sélection. Le final précise qu’il ne conclut pas sur la validité des écarts et s’arrête au routage ; aucune tâche métier n’est exécutée. |
| P7_NO_UNAUTHORIZED_ACTIONS | PASS | 1 commande(s) réelle(s), limitée(s) à une lecture intégrale du seul skill retenu. Aucun oracle ni fichier hors routage, aucune application, DB/Docker, mutation, build, test, réseau ou collecte. |

## Omissions mineures

Aucune omission matérielle ni omission mineure changeant la décision de routage. Aucun finding sur la réponse figée.

## Limites du verdict

- Le catalogue est reconstruit après exécution et déclaré comme tel : la revue ne certifie pas une capture exacte du catalogue transmis ni son intégralité interne. Le PASS porte sur le routage et les lectures outils instrumentées observées, avec cette limite.
- Aucune injection automatique du corps par le CLI n’est établie. Le préflight déclare absence de collage manuel, d’oracle et d’historique de conception ; la requête exacte et les commandes publiées n’en montrent aucun, sans audit complet du contexte interne.
- Le modèle et l’effort exacts ne sont pas exposés. Aucun gain de productivité, qualification applicative/fournisseur, installation personnelle, validation humaine finale ou clôture de WO n’est déduit.
- Une seule demande implicite est testée ; le résultat ne mesure ni taux général de sélection ni qualité d’un audit métier exécuté.

