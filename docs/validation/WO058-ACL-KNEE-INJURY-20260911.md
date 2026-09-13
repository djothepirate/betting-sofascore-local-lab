# WO-058 — Traduction de `ACL Knee Injury`

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Portée

La projection web des joueurs indisponibles J5 traduit exactement :

```text
ACL Knee Injury → Ligament Croisé Antérieur du genou
```

La description fournisseur brute reste dans `MissingLineupPlayer`, le snapshot et l'observation
normalisée. Les descriptions non reconnues restent affichées telles quelles. Aucun parseur,
transport Playwright, budget, cadence, stockage ni campagne locale n'est modifié.

## Régression ciblée

`LineupsPresentationTest` vérifie simultanément le libellé français affiché et la conservation de
la liste source non traduite. Il vérifie aussi que la variante `ACL knee injury` reste affichée telle
quelle : la règle n'infère aucune traduction à partir d'une valeur proche.

Exécution finale locale le 11 septembre 2026 à 00:02 (Europe/Paris), avec dépôt Maven et réglages
locaux isolés :

```powershell
.\mvnw.cmd "-Dmaven.repo.local=<cache Maven local isolé>" --settings <settings Maven locaux isolés> -q "-Dtest=LineupsPresentationTest,IncidentPresentationTest,LiveCampaignPresentationTest,J5EventDataControllerTest" test
```

Résultat : 135 tests réussis, sans échec, erreur ni test ignoré (`13 + 46 + 58 + 18`). Cette
exécution standard ne lance aucun transport Playwright et ne contacte aucun fournisseur.

La vérification complète `clean verify` relancée juste après exécute 2 213 tests puis s'arrête
sur les deux contrôles Windows d'identité de processus déjà documentés :

- `LiveOrphanProcessProbeTest.currentJavaIdentityMatchesItsRealCimCreationDate` reçoit
  `UNVERIFIED` au lieu de `ABSENT` ;
- `J6NativeBinaryPipelineQualificationTest.syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput`
  ferme en échec face à `CIM_ERROR,TASKLIST_ERROR,CLASS_UNVERIFIABLE`.

Ces garde-fous sont hors du périmètre de la traduction, restent fail-closed et n'ont été ni
contournés, ni modifiés, ni désactivés.
