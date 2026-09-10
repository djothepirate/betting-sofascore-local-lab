# WO-058 — Profil de qualification locale live-v8 à dix rencontres

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`,
`NO_CRITICAL_DEPENDENCY`.

## Objet et portée de la preuve

Cette preuve qualifie **localement** la politique `live-v8` à dix rencontres, pour une vague
normale de 60 secondes par couple rencontre/famille : `EVENT_DETAILS`,
`EVENT_INCIDENTS`, `EVENT_STATISTICS` et `EVENT_LINEUPS`. Elle est liée au worker de
production, à Chromium et à PostgreSQL de test, tous exécutés contre une origine synthétique
éphémère `127.0.0.1`.

Elle ne lance aucune campagne réelle, n'appelle pas SofaScore et n'utilise pas de base
opérateur. Elle ne démontre donc ni seuil d'acceptation du fournisseur, ni quota ou latence
Internet, ni résistance future à un bannissement ou à un refus HTTP. Les protections durables
403/429 restent inchangées et ne sont ni réarmées ni contournées par cette qualification.

## Artefacts figés

Les fichiers JSON sont versionnés sans transformation de leurs octets par `.gitattributes`.

| Artefact | SHA-256 | Rôle |
| --- | --- | --- |
| [Rapport natif](WO058-GROUPED-LIVE-V8-NATIVE-20260910.json) | `f5b70709dcc51d9b40223fde1175d3c507190244562355e675689cb06e9a7fc0` | Mesure Chromium/worker/PostgreSQL de test, statut `PASSED`. |
| [Profil calculé](WO058-GROUPED-LIVE-V8-PROFILE-20260910.json) | `c25d65be2a42969c561eadc631eb3499ee21519990e7b44e790a21410efcc1d6` | Profil local à charger manuellement avec ses huit enveloppes V8. |
| [Calculateur de profil](v8-profile-builder/V8MeasuredProfile.java) | `3bcefc3b3f6f49582e03a9ed8bc852d5870832666e1c1e3a86635cb4e522f31f` | Rejeu hors réseau des bornes immuables et des scénarios d'admission V8. |

L’empreinte attendue du lanceur est celle du **profil calculé**, pas celle du rapport natif.

## Protocole réellement exécuté

La passe a été déclenchée explicitement avec :

```powershell
.\scripts\Invoke-LiveGroupedPlaywrightQualification.ps1 -PolicyVersion live-v8
```

Le scénario commence par une voie de stress froid distincte de l'ordonnanceur V8 normal :

- 40 réponses de 5 Mio, une par couple rencontre/famille ;
- un drain de 60 001 ms après la dernière complétion froide ;
- cinq minutes de mise en régime, puis trente minutes établies ;
- timeout Playwright effectif de 30 000 ms ;
- contexte et transport exclusivement loopback, sans appel fournisseur, requête hors périmètre,
  base opérateur ou artefact de session persistant.

Les 40 réponses froides ne sont pas couvertes par les enveloppes établies et ne revendiquent
aucune place dans la fenêtre V8 de 60 secondes. Elles sont mesurées pour exercer la récupération
locale avant la voie stricte.

## Résultats mesurés

| Mesure | Résultat |
| --- | --- |
| Rencontres | 10 |
| Familles par rencontre | 4 |
| Durée établie exigée / mesurée | 1 800 s / **1 800,0198031 s** |
| Voie stricte mesurée | **2 100,0198031 s** |
| Appels totaux | **1 444** |
| Appels établis / froids | **1 203 / 40** |
| Départs et complétions durables | **1 444 / 1 444** |
| Pic observé de départs et d'arrivées sur 60 s | **41 / 41** |
| Plafonds persistants du profil | **45 / 60 s** et **2 756 / h** |
| Cycles manqués / décalages de pression | **0 / 0** |
| Appels fournisseur / requêtes hors périmètre | **0 / 0** |
| Base opérateur utilisée | **non** |
| Rejeu de production | **16 scénarios exécutés** |

La passe, plus courte qu'une heure, ne sature pas une fenêtre horaire complète. Le calculateur
rejoue séparément la frontière entière de dix rencontres : 2 480 départs planifiés par heure
sur le budget de 2 756, soit 276 départs laissés non alloués.

## Bornes immuables et calcul d'admission

Chaque maximum observé dans la voie établie est arrondi vers le haut au pas de 50 ms. Il doit
rester sous la borne qualifiée correspondante ; une exécution plus rapide ne réduit jamais
automatiquement le profil, et un dépassement l'invalide.

| Famille | Borne requête / traitement qualifiée | Maximum établi arrondi observé |
| --- | --- | --- |
| `EVENT_DETAILS` | **300 / 500 ms** | 300 / 450 ms |
| `EVENT_INCIDENTS` | **300 / 400 ms** | 300 / 400 ms |
| `EVENT_STATISTICS` | **350 / 400 ms** | 300 / 400 ms |
| `EVENT_LINEUPS` | **300 / 450 ms** | 300 / 400 ms |

La somme des quatre enveloppes requête + traitement vaut 3 000 ms. Les quatre fences après
fin d'échange prouvée ajoutent `4 × 500 ms = 2 000 ms`; la réserve statique V51 entre groupes
ajoute 1 000 ms. La réservation stricte d’un groupe est donc **6 000 ms**. Dix groupes occupent
exactement 60 000 ms, ce qui fixe la capacité temporelle à dix et conserve la cible de fraîcheur
locale de 60 s lorsque chaque échange reste dans ses bornes.

Le fence de 500 ms est une règle interne V8. Il ne modifie pas `SOFASCORE_MINIMUM_DELAY`, qui
conserve sa valeur globale existante ; aucune configuration à 500 ms de cette variable globale
n'est autorisée par cette preuve.

## Configuration et frontières de livraison

Les neuf variables V8 exactes sont reprises dans le
[runbook des campagnes live](../runbooks/LIVE-J4-J5-CAMPAIGNS.md). Elles comprennent le SHA-256
du profil ci-dessus, non celui du rapport natif. `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY=10` et
`SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT=30s` restent les paramètres opérateur associés.

La qualification ne modifie pas elle-même le lanceur Eclipse, ne redémarre pas le Lab et ne
démarre pas de campagne. Le WO-058 reste `IN_PROGRESS` jusqu'à la revue humaine, la fusion et
la livraison atomique séparée du lanceur. Une campagne réelle éventuelle exige ensuite les
actions opérateur explicites prévues par le runbook et reste soumise aux suspensions durables,
aux budgets partagés et aux règles de sélection.
