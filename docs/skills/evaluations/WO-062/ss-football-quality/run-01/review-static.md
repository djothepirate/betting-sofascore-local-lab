# Revue statique indépendante — ss-football-quality

## Verdict et périmètre

**PASS_STATIC_REVIEW_AFTER_ENCODING_FIX** — aucun défaut matériel restant constaté dans le candidat examiné. Ce verdict concerne exclusivement la revue statique ; il ne qualifie aucun essai comportemental.

- Relecteur : sous-agent indépendant `fq_candidate_review`.
- Revue initiale : `bfbe3fb6e787756544508e1a3e5a92674a678bd7`.
- Correction vérifiée : `11394e327448827c13ad3006d3afc2d9199c1e15`.
- HEAD lors de la vérification finale : `658309c0e8d9d18e7d96396d8768ac90d8d16cca`.
- Worktree : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- Statuts préservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Défaut initial et résolution

**P2 — Métadonnées non encodées en UTF-8**, `docs/skills/local-lab/ss-football-quality/agents/openai.yaml`, lignes 2 à 4.

Le décodage UTF-8 strict du fichier initial échouait à l'octet 34 : `E9` isolé dans « Qualité ». Les autres accents comprenaient notamment `F4` et `E0`. Le fichier utilisait un encodage sur un octet incompatible avec UTF-8 ; cela violait `AGENTS.md:23` et pouvait provoquer un refus de lecture ou des caractères de remplacement dans les métadonnées.

La correction `11394e327448827c13ad3006d3afc2d9199c1e15` résout ce défaut. La vérification finale a contrôlé le décodage avec `UTF8Encoding(false, true)` et l'égalité exacte du contenu avec les quatre lignes attendues, terminées par LF. Les trois textes sont conservés :

- `Qualité football du Local Lab`
- `Contrôler identité, incidents, complétude et historique`
- `Utilise $ss-football-quality pour examiner la cohérence des données football du SofaScore Local Lab et relier chaque constat à ses preuves.`

Le YAML est inchangé entre la correction et le HEAD de vérification. Le blob Git de `SKILL.md` est identique entre la revue initiale et la vérification finale ; la revue métier initiale reste donc applicable.

## Conclusions métier

Aucun autre écart matériel important ni instruction excessivement rigide n'a été constaté dans les sources contrôlées.

Le candidat concorde avec les contrats et implémentations lus sur l'identité canonique, la fenêtre civile IANA, les scores J4 indépendants, les variantes exactes V15–V17, les avertissements facultatifs V4, la priorité de classification J6 et les exclusions `score.*`/`completeness.*`. Il distingue explicitement preuve historique, octets privés accessibles, fixture synthétique, replay et nouvelle observation fournisseur.

Précision facultative, sans défaut retenu : `J6SemanticDiffService.java:560` contient un dernier recours positionnel borné à `sequence + type + côté`, sans identifiant fournisseur ni séquence de tirs au but. Le skill ne l'explicite pas ; sa règle interdisant de forcer un appariement ambigu reste compatible avec le comportement lu.

Sources locales principales contrôlées :

- `docs/skills/local-lab/ss-football-quality/SKILL.md`
- `docs/skills/local-lab/ss-football-quality/agents/openai.yaml`
- `docs/skills/evaluations/WO-062/ss-football-quality/source-inventory.md`
- `AGENTS.md`
- `docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md`
- `docs/architecture/J4-J5-PEOPLE-V4.md`
- `docs/architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md`
- `docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md`
- `docs/architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md`
- `docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md`
- Sections pertinentes de `docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md`
- `EventIncidentsV15Parser.java`, `EventIncidentsV16Parser.java`, `EventIncidentsV17Parser.java`
- `EventDetailsV4Parser.java`, `EventLineupsV4Parser.java`, `EventStatisticsV2Parser.java`
- `J6HistoryClassifier.java`, `J6SemanticDiffService.java` et extraits pertinents de leurs sources de tests
- Références aux parseurs effectivement consommés dans les services applicatifs

## Empreintes

| Fichier | SHA-256 des octets corrigés du worktree | Blob Git initial | Blob Git vérifié |
|---|---|---|---|
| `SKILL.md` | `8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58` | `614081aa31aa7744f1ef6b56131997dfd7c92335` | `614081aa31aa7744f1ef6b56131997dfd7c92335` |
| `agents/openai.yaml` | `8d295af442808d3efebbe68e8165ef96efd086b5b84908fc12f04c1c66bf51f2` | `ec98cc7278eb2d676db78c30e6a9e1a0fc35013d` | `b514b4b2be910ac56b9ae487b922c2265dca7ad4` |

## Limites

Revue en lecture seule du candidat et des sources utiles ; seuls les présents comptes rendus ont été écrits dans le répertoire temporaire demandé. Aucun fichier suivi modifié. Aucun oracle, cas d'entrée ou résultat d'évaluation consulté. Aucun test, serveur applicatif, accès DB, navigateur ou appel fournisseur exécuté. La vérification finale porte seulement sur les textes et l'encodage corrigés, et sur l'absence de changement de `SKILL.md`. Aucun résultat comportemental n'est déduit de cette revue.
