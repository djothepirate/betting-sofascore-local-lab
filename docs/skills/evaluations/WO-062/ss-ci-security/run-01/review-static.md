# WO-062 — Revue statique indépendante de ss-ci-security

- Date de revue : 15 septembre 2026.
- Type : revue statique, distincte des évaluations comportementales.
- Verdict : **NO_MATERIAL_FINDINGS — aucun défaut matériel relevé**.
- Relecteur : agent indépendant `/root/cs_candidate_review`.
- Racine examinée : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- Commit examiné : `658309c0e8d9d18e7d96396d8768ac90d8d16cca`.
- Commit de référence de l'inventaire : `57c0614627e628077b0a7775eb66a7c490b422e0`.
- Candidat : `ss-ci-security`, version `0.1.0-candidate.1`.

## Périmètre et méthode

Lecture des deux fichiers du candidat, de l'inventaire de sources préparatoire et des sources
locales utiles : AGENTS.md, ADR-SS-004, runbook CI-LOCAL-QUOTAS, workflows GitHub/GitLab,
gardes des rapports, de branche/version, de PR, de contexte GitLab et de MR, script de secrets,
script Dependency-Check, packaging local et routage ss-verify. Des extraits ciblés de la fixture
de reproductibilité ont permis d'établir sa portée avec Maven simulé, sans l'exécuter.

Les sources métier contrôlées sous AGENTS.md, ADR-SS-004, le runbook, les workflows, `ci/`
et ss-verify ne présentent aucune différence entre le commit d'inventaire et le HEAD examiné.
L'état Git était propre avant et après la revue. Aucune modification suivie n'a été effectuée.
La présente pièce fige cette revue achevée ; sa rédaction ne lance aucun nouveau test.

## Critères et constats

| Critère | Conclusion statique | Constat |
| --- | --- | --- |
| Frontières WO-061 et déclenchements | Conforme aux sources lues | GitHub : PR et demande manuelle ; GitLab : MR de promotion, tag et Web. Aucun rétablissement implicite de bundle intermédiaire ni de scanner d'observation automatique. |
| Tests et conservation des preuves | Conforme aux sources lues | Présence et intégrité XML, compteurs, exécution effective, résumé Failsafe, upload obligatoire et rétention correctement distingués. Un échec de conservation ne devient pas artificiellement une régression applicative. |
| Provenance et routes Git | Conforme aux sources lues | Distinction tête de PR/commit de merge ; égalité exacte du SHA source de promotion GitLab ; historique complet, cible ancêtre et exception seed bornée. |
| Contrôles de sécurité | Conforme aux sources lues | Seuil Dependency-Check 11 présenté comme observation, erreurs bloquantes, exemptions de secrets attachées aux blobs audités et aucune autorisation implicite de purge. |
| Distribution locale et SBOM | Conforme aux sources lues | Version finale et références alignées pour le tag ; métadonnées racine et comparaison des deux générations SBOM ; écriture de SHA256SUMS distinguée de sa vérification ; fixtures Maven simulé distinguées d'un bundle réel. |
| Routage et métadonnées | Cohérent | Frontières ss-verify/ss-review-closeout respectées ; le prompt openai.yaml invoque bien $ss-ci-security. |
| Références locales | Présentes | 16 chemins et routages locaux vérifiés existants ; les noms abrégés des gardes se résolvent dans le répertoire ci/ indiqué. |
| Encodage | Conforme | Les deux fichiers sont décodables en UTF-8 strict, sans BOM, caractère U+FFFD ni NUL. |

Aucun défaut matériel n'a été identifié. Cette formulation ne constitue ni une note
comportementale, ni un verdict de qualification d'une PR ou d'une CI réelle.

## Identité des fichiers examinés

| Fichier | Taille | SHA-256 des octets Windows examinés |
| --- | ---: | --- |
| `docs/skills/local-lab/ss-ci-security/SKILL.md` | 7 522 octets | `ca0bba1cf60c0c9965f21b3201e39c3e5dcba3bd73888c2a2ee5d148dbb8c0c5` |
| `docs/skills/local-lab/ss-ci-security/agents/openai.yaml` | 293 octets | `621ecacc03c3acf895d2bd5c87ed94461512e23a6e16cc321ae1818f0962d959` |

Ces empreintes identifient les octets de travail examinés ; elles ne sont pas des OID de blobs
Git et ne doivent pas être substituées à des empreintes d'une matérialisation différente.

## Limites et séparation de l'évaluation

- Aucun oracle, cas, input ou résultat d'évaluation n'a été consulté.
- Aucun réseau, pipeline, test, scanner, application ou base de données n'a été lancé.
- Aucun état distant, protection serveur, résultat de scanner ou bundle réel n'est qualifié.
- Les références locales ont été contrôlées ; aucun lien distant n'a été ouvert.
- Le comportement du skill en session évaluée reste non exécuté et non qualifié par cette revue.
- Aucun fichier suivi n'a été modifié. Les seules pièces produites lors de la fixation sont
  `review-static.md` et `review-static.json` dans le répertoire temporaire demandé.
- Les statuts EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY
  restent applicables.
