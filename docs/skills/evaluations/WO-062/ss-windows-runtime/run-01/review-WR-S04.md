# Revue indépendante WR-S04 — PASS

- Relecteur : `jm_selection_review` ; revue : 2026-09-15 21:31:29 UTC.
- Révision source : `9e7b777e3be75f582f2198dfa8cca386b43426c2`.
- Candidat : `ss-windows-runtime 0.1.0-candidate.1`.
- SHA-256 candidat : `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`.
- SHA-256 réponse : `062cd7a7704976261537d7fe5c97ef624f03ddbc23df177253a16e89c492b9b0`.
- SHA-256 gel : `5a5827f1d9d948c8722876ad5f91e9584c8d3d72bed2df4843dcb2ac9dac9367`.
- Skills retenus : aucun.

## Verdict et preuves

Aucun skill n’est retenu pour atelier-recettes, projet Python indépendant. Le candidat Windows est examiné puis écarté à cause de son périmètre limité au Lab ; aucun autre ss-* n’est appliqué au projet tiers.

### Gel et intégrité — PASS

- Les huit fichiers de frozen/WR-S04/freeze.json ont des tailles et SHA-256 recalculés conformes. Les six empreintes de trace.json pour événements bruts/natifs, prompt, requête, réponse et catalogue concordent.
- native-events.json égale exactement les événements bruts après seule exclusion des entrées reasoning ; les sorties des commandes restent intégrales. response.md égale exactement le texte du dernier agent_message.

### Prompt et isolation déclarée — PASS

- prompt.txt égale le prompt de cases.json suivi de LF ; request.txt égale selection_envelope substituée par ce prompt, suivie de LF. Les empreintes du préflight et de la trace concordent.
- Le prompt et son enveloppe maintiennent le projet indépendant atelier-recettes. Aucun nom attendu ni corps de skill n’est ajouté.
- Le lancement enregistré utilise exec --ephemeral --sandbox read-only dans le répertoire isolé du cas ; aucun oracle ou historique n’est lu par les commandes observées. Le CLI termine sans expiration ni relance automatique déclarée.

### Identité du candidat — PASS

- La copie isolée et le SKILL canonique sont identiques en UTF-8 strict : name ss-windows-runtime ; version 0.1.0-candidate.1 ; 9 217 octets ; SHA-256 b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6.
- agents/openai.yaml : 300 octets ; SHA-256 3038015980952bc18b21f45f0d4af514b35a8841394e4b80824986b7e30ccd9f ; default_prompt référence $ss-windows-runtime. Les copies concordent avec le préflight.

### Découverte, chemins et dédoublonnage — PASS

- Le catalogue figé égale le bloc Skills de output/prompt-render.json suivi de LF. Ses 24 noms et 24 chemins résolus sont uniques ; une seule entrée ss-windows-runtime résout C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-S04/.agents/skills/ss-windows-runtime/SKILL.md.
- Les neuf entrées ss-* comprennent le candidat local du cas et huit skills personnels sous C:/Users/geoff/.agents/skills. REPO et USER sont déduits de ces chemins ; le catalogue textuel n’expose pas un champ scope.
- La reconstruction après exécution corrobore les noms, descriptions et chemins. Elle n’est ni une capture de la requête transmise au modèle ni une preuve d’injection automatique du corps.

### Routage attendu — PASS

- Aucun skill n’est retenu pour atelier-recettes, projet Python indépendant. Le candidat Windows est examiné puis écarté à cause de son périmètre limité au Lab ; aucun autre ss-* n’est appliqué au projet tiers.

### Lecture par outil et attribution des chemins — PASS

- item_1 recherche les SKILL.md locaux sous .agents/skills, puis item_3 lit le candidat exact par Get-Content -LiteralPath absolu -Raw. Les deux commandes réussissent. La sortie de lecture de 9 002 caractères égale le candidat complet plus le seul CRLF console. La lecture sert à son exclusion et ne constitue pas une sélection ou un diagnostic métier.
- Les commandes et sorties sont conservées dans les événements natifs et concordent avec les événements bruts. Aucun marqueur de troncature n’est observé.

### Arrêt au routage — PASS

- 2 commande(s) de lecture ou de découverte seulement. Aucun fichier métier, diagnostic de processus, reproduction, build, Java, Maven, Docker, réseau, navigateur, base, installation ou mutation ne suit le routage.
- La réponse termine le routage et le CLI finit avec code 0 et turn.completed. Cette réussite n’établit aucun résultat de sonde Windows, de WR-H01 ou de WR-N01.

## Limites

- Catalogue reconstruit après exécution : corroboration de découverte, pas capture de la requête modèle. Aucune injection automatique du corps CLI démontrée ou revendiquée.
- Modèle et effort exacts non exposés ; traces déclarant les valeurs configurées par défaut sans substitution.
- PASS porte sur le routage et la lecture instrumentée. Il ne qualifie aucune exécution applicative Windows, aucun incident réel ni les sondes métier du run. Gain global de temps/qualité non mesuré.
- Aucun cas ou modèle relancé par le relecteur. Aucun fichier suivi, candidat ou résultat figé modifié ; seuls les rapports et un auxiliaire runtime de vérification ont été écrits.

## Résultat

Aucun écart bloquant. Le JSON associé conserve les commandes observées, les empreintes du gel et l’identité résolue du candidat.

