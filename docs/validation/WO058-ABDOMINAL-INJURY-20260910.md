# WO-058 — Traduction de `Abdominal Injury`

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Portée

La projection web des joueurs indisponibles J5 traduit exactement :

```text
Abdominal Injury → Blessure abdominale
```

La description fournisseur brute reste dans `MissingLineupPlayer`, le snapshot et l'observation
normalisée. Les descriptions non reconnues restent affichées telles quelles. Aucun parseur,
transport Playwright, budget, cadence, stockage ni campagne locale n'est modifié.

## Régression ciblée

`LineupsPresentationTest` vérifie simultanément le libellé français affiché et la conservation de
la liste source non traduite.

Exécution finale locale le 10 septembre 2026 à 23:08 (Europe/Paris) :

```powershell
.\mvnw.cmd -q -Dtest=LineupsPresentationTest,IncidentPresentationTest,LiveCampaignPresentationTest,J5EventDataControllerTest test
```

Résultat : 135 tests réussis, sans échec, erreur ni test ignoré (`13 + 46 + 58 + 18`). Cette
exécution standard ne lance aucun transport Playwright et ne contacte aucun fournisseur. La passe
`clean verify` finale rencontre seulement les deux contrôles Windows fail-closed documentés dans
[la validation du motif de carton](WO058-PROFESSIONAL-FOUL-LAST-MAN-20260910.md#v%C3%A9rification-compl%C3%A8te).
