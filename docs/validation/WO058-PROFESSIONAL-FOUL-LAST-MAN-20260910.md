# WO-058 — Traduction de `Professional foul last man`

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Portée

La seule projection web des incidents traduit la raison exacte d'un carton sans `description`
fournisseur :

```text
Professional foul last man → Faute volontaire du dernier défenseur
```

La raison brute reste dans `EventIncident` et dans la donnée normalisée. Une `description`
fournisseur non vide garde sa priorité. Les autres types d'incident et les variantes proches,
notamment les différences de casse ou de formulation, restent inchangés. Le parseur, le transport
Playwright, les budgets, les cadences, la persistance et toute campagne locale restent hors portée.

## Régression ciblée

La suite `IncidentPresentationTest` vérifie :

- la traduction sur les cartes graphique et technique ;
- la conservation de `reason` et de `motifLabel()` bruts dans le domaine ;
- la priorité d'une `description` fournisseur ;
- l'absence de traduction hors des cartes ou pour les variantes proches.

Exécution finale locale le 10 septembre 2026 à 23:08 (Europe/Paris) :

```powershell
.\mvnw.cmd -q -Dtest=LineupsPresentationTest,IncidentPresentationTest,LiveCampaignPresentationTest,J5EventDataControllerTest test
```

Résultat : 135 tests réussis, sans échec, erreur ni test ignoré (`13 + 46 + 58 + 18`). Cette exécution
standard ne lance aucun transport Playwright et ne contacte aucun fournisseur.

## Vérification complète

La passe finale `clean verify` du 10 septembre 2026, après les deux ajouts, a exécuté 2 213 tests
avec cinq ignorés. Elle reste en échec sur deux contrôles indépendants et déjà reproduits de
l'identité de processus Windows :

- `LiveOrphanProcessProbeTest` reçoit `UNVERIFIED` au lieu de `ABSENT` ;
- `J6NativeBinaryPipelineQualificationTest` échoue volontairement fermé car les sondes système
  renvoient `CIM_ERROR`, `TASKLIST_ERROR` et `CLASS_UNVERIFIABLE`.

Ces deux contrôles ne concernent ni les incidents, ni les compositions, ni les traductions de ce
lot. Le code de présentation est validé par la régression ciblée ci-dessus ; aucun correctif n'a
été apporté pour masquer ou contourner la vérification système.
