## Décision de routage

**Skill retenu : `ss-verify`, uniquement.**

La demande concerne exclusivement la vérification du **build Windows du SofaScore Local Lab avec le wrapper Maven**, suivie de la **lecture des rapports effectifs**. `ss-verify` couvre précisément ce périmètre : choix des commandes Maven du Lab, lanceur PowerShell, contrôle du code de sortie et lecture des rapports Surefire/Failsafe ainsi que des rapports Pester pertinents. Il précise qu’un code zéro ou un JAR produit ne suffit pas à prouver la qualification.

### Fichier effectivement lu

Le fichier suivant a été **lu intégralement avec un outil**, via `Get-Content -Raw` :

- [`C:/Users/geoff/.agents/skills/ss-verify/SKILL.md`](C:/Users/geoff/.agents/skills/ss-verify/SKILL.md)

### Skills non retenus

| Skill du catalogue | Motif de non-sélection |
|---|---|
| `ss-football-quality` | Sa description exclut explicitement un simple build. Aucun audit de cohérence des données football n’est demandé. |
| `ss-data-contract-replay` | Aucun contrat, parseur, import/export ou replay n’est à examiner. |
| `ss-provider-benchmark` | Aucun benchmark fournisseur ni examen de campagne n’est demandé ; sa description exclut également un simple build. |
| `ss-postgres-change` | Aucune migration, modification de persistance ou analyse de concurrence PostgreSQL n’est demandée. |
| `ss-work-order` | La demande ne porte pas sur la reprise, le cadrage ou le découpage d’un Work Order. |
| `ss-review-closeout` | Aucune préparation de revue ni clôture de Work Order ou de PR n’est demandée. |
| Autres skills du catalogue | Leurs périmètres ne correspondent pas à cette vérification de build Windows. |

**Arrêt après le routage :** aucun build ni test exécuté, aucun rapport de build consulté, aucun accès réseau, DB ou Docker, aucun démarrage d’application et aucune modification. Aucun skill absent du catalogue n’a été recherché.