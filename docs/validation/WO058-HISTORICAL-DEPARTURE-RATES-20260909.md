# WO-058 — Débits historiques observés avant les arrêts du 9 septembre 2026

Statuts : **EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY**.

Date de relecture : 2026-09-09. Analyse hors fournisseur, sur des captures locales de métadonnées déjà présentes. Aucun appel réseau, accès à la base opérateur, relancement de campagne ou changement de configuration n'a été effectué pour cette relecture.

## Conclusion et portée

L'opérateur rapporte deux refus d'accès au site, observés également dans ses navigateurs, dans un contexte de campagnes qu'il décrit comme « 80 appels à la minute ». Les traces locales permettent de préciser le débit des campagnes, sans déterminer la règle appliquée par le fournisseur ni attribuer chaque timeout à un bannissement.

- La campagne `08c3cd3d-d81b-4d65-8927-73d9a5896c36`, à 20 rencontres, présente un maximum mesuré de **44 départs observés sur 60 secondes**, puis un **HTTP 403 enregistré**.
- La campagne `3362612a-14c2-427f-9816-4a5fce88276d`, à **19 rencontres effectivement sélectionnées**, présente un maximum de **62 départs observés sur 60 secondes**. Son dernier résultat enregistré est **PLAYWRIGHT_TIMEOUT**, sans statut HTTP conservé pour cet échange. Le refus constaté dans le navigateur reste une observation opérateur distincte.
- Le nombre **80 = 20 × 4** représente le volume théorique d'un passage initial sur quatre familles pour vingt rencontres toutes admissibles à ces quatre familles. Ce calcul ne constitue pas une mesure de 80 départs dans une minute. Aucun des cinq historiques d'arrêt examinés ici n'atteint 80 autorisations ou départs enregistrés dans une fenêtre glissante de 60 secondes.
- **40 appels/minute n'est pas une frontière d'acceptation du fournisseur.** C'est une moyenne nominale du plan historique avec vingt matchs en jeu ; elle n'inclut pas tous les effets du démarrage et ne borne pas les pics. Un premier HTTP 403 est déjà documenté dans une campagne dont le pic observé est de 44/60 s. Ces données ne prouvent pas non plus qu'un débit inférieur serait accepté.

Les résultats portent sur les échanges suivis par le laboratoire. Ils ne mesurent pas tout le trafic de l'adresse IP, l'historique antérieur de cette adresse ou les critères de refus du fournisseur. La proximité temporelle entre charge et refus est une observation ; la causalité et un seuil universel ne sont pas établis.

## Observation navigateur complémentaire

Le 9 septembre, l'opérateur a relevé dans les outils de développement, sans changer de
page d'événement, les rythmes approximatifs suivants : statistiques J5 toutes les
**10 secondes**, incidents J5 toutes les **15 secondes** et compositions J5 toutes les
**20 secondes**. Cela correspond à environ **6 + 4 + 3 = 13 requêtes J5 par minute**
pour une seule rencontre affichée.

Cette observation est manuelle : elle ne fournit ni un export complet du navigateur,
ni des fenêtres horodatées dont le laboratoire puisse refaire le calcul. Elle exclut
aussi J4 et les autres requêtes de la page. La capture montre des réponses `200` pour
les compositions ; les `404` affichés dans la console concernent la ressource
`highlights`, qui ne doit pas être confondue avec l'absence normale des familles J5.

À titre de comparaison uniquement, sept rencontres à ce rythme représenteraient
environ **91 requêtes J5 par minute**, avant J4 et le trafic annexe. Ces sept rencontres
ne constituent qu'une extrapolation de la mesure navigateur : elles ne sont pas le
plafond du profil `live-v7`. Celui-ci admet au plus **trois rencontres** et planifie, en
jeu, une demande J4 et une demande de chacune des trois familles J5 par minute et par
rencontre. Son maximum nominal est donc **12 départs par minute**, avant les réponses
indisponibles, les reports et les mécanismes de reprise. Cette différence ne détermine
pas une limite du fournisseur et ne justifie ni relèvement automatique du profil ni
rotation d'adresse ; elle motive le maintien de la cadence V7 et une qualification
progressive hors fournisseur.

## Définitions de mesure

Les captures distinguent les champs issus de `live_call_dispatch` et de `live_call_receipt` : voir l'extraction locale [extract.sql](../../.tmp/08c3cd3d-cadence/extract.sql).

| Champ | Ce qu'il établit | Limite |
| --- | --- | --- |
| `authorized_at` | Instant local de réservation du départ dans le ledger, avant le transfert au transport et le départ réseau. | Une autorisation n'est pas, à elle seule, la preuve d'un GET parti. |
| `requested_at` | Instant local observé dans le worker lors du callback CDP `Network.requestWillBeSent`, corrélé au GET du document principal attendu ; transmis avec une précision d'une milliseconde. | Ce n'est ni l'heure d'autorisation, ni l'heure d'arrivée chez SofaScore. Dans ce transport historique, ce champ n'était conservé que si un reçu était produit. |
| `received_at` | Instant de réception conservé avec le reçu. | Des réponses peuvent se regrouper après des départs plus espacés : un pic de réceptions n'est pas un pic de départs. |
| `resolved_at` | Instant du résultat local de l'essai. | L'arrêt et le nettoyage de la campagne peuvent être enregistrés plus tard. |

La sémantique de `requested_at` a été contrôlée dans les sources historiques au commit `f2b28eca276e49476425ee0a12ed7bdda54d32df`, fichiers `ProviderMainDocumentNetworkObservation.java` et `ProviderPlaywrightWorkerMain.java` sous `src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/`. L'observateur valide les champs CDP `timestamp` et `wallTime`, mais affecte **`clock.instant()` au traitement du callback** ; le worker transmet ensuite `toEpochMilli()`. Il ne copie donc pas directement `wallTime` comme heure de départ.

Pour chaque horodatage observé `t`, le calcul compte les lignes de l'intervalle **`t − 60 s < horodatage ≤ t`**, puis retient le maximum. Autorisations et départs ont été recalculés séparément, sans imputer un `requested_at` manquant. Quand plusieurs fenêtres atteignent le même maximum, les tableaux ci-dessous présentent la première rencontrée dans l'ordre chronologique. Toutes les heures sont en **UTC, le 9 septembre 2026** ; ajouter deux heures pour l'affichage Europe/Paris de cette date.

Les durées vont de `started_at` au dernier `resolved_at`, et non à l'heure de capture de l'écran ou du fichier.

## Deux campagnes ciblées

| Mesure | `08c3cd3d…` | `3362612a…` |
| --- | --- | --- |
| Rencontres sélectionnées | 20 | 19 |
| Démarrage UTC | 10:04:07.988081 | 11:36:05.367943 |
| Dernier résultat UTC | 10:08:33.598208 | 11:40:22.010718 |
| Durée jusqu'au dernier résultat | 4 min 25,610 s | 4 min 16,643 s |
| Essais / autorisations | 149 / 149 | 136 / 136 |
| Départs horodatés / reçus | 149 / 149 | 135 / 135 |
| Résultats PARSED | 105 | 102 |
| HTTP 404 | 43 | 33 |
| HTTP 403 conservés | 1 | 0 |
| PLAYWRIGHT_TIMEOUT | 0 | 1 |
| Pic d'autorisations sur 60 s | 44 | 62 |
| Pic de départs observés sur 60 s | 44 | 62 |
| Pic de réceptions sur 60 s | 44 | 63 |

### Fenêtres exactes des pics

| Campagne et horodatage utilisé | Fenêtre de 60 s, borne gauche exclue | Première ligne comptée | Dernière ligne comptée | Nombre |
| --- | --- | --- | --- | --- |
| `08c3cd3d…`, `authorized_at` | (10:04:08.434595 ; 10:05:08.434595] | 10:04:11.268607 | 10:05:08.434595 | 44 |
| `08c3cd3d…`, `requested_at` | (10:04:08.497 ; 10:05:08.497] | 10:04:11.355 | 10:05:08.497 | 44 |
| `3362612a…`, `authorized_at` | (11:37:12.699912 ; 11:38:12.699912] | 11:37:16.106820 | 11:38:12.699912 | 62 |
| `3362612a…`, `requested_at` | (11:37:12.761 ; 11:38:12.761] | 11:37:16.169 | 11:38:12.761 | 62 |

Le maximum n'est pas nécessairement situé dans la première minute après lancement : cette première minute contient respectivement 42 et 22 autorisations. Pour `3362612a…`, la fenêtre maximale superpose 42 appels de groupes initiaux et 20 appels pendant le jeu, sur 19 rencontres. Le pic de 63 réceptions de cette campagne ne doit pas être présenté comme 63 départs.

Le premier historique contient également 12 autorisations dans une fenêtre de 10 secondes, `(10:04:09.039932 ; 10:04:19.039932]`, correspondant à trois groupes initiaux de quatre appels. Une moyenne sur 60 secondes masque donc une partie de la concentration à l'intérieur des groupes.

### Résultats d'arrêt conservés

Pour `08c3cd3d…`, le dernier appel `J5_PREMATCH_LINEUPS` porte le code `HTTP_403`, avec un départ observé à **10:08:33.556Z**, une réception à **10:08:33.576Z** et un résultat à **10:08:33.598208Z**. Les métadonnées de [stop-evidence](../../.tmp/08c3cd3d-cadence/stop-evidence-20260909.json) corroborent le statut 403 et le snapshot 6227. Aucun corps fournisseur n'est reproduit dans ce rapport.

Pour `3362612a…`, le dernier `J4_CYCLE` est autorisé à **11:39:51.929088Z** et aboutit à `PLAYWRIGHT_TIMEOUT` à **11:40:22.010718Z**. Cet essai n'a ni `requested_at`, ni `received_at`, ni snapshot dans le reçu historique. Les métadonnées de [stop-evidence](../../.tmp/3362612a-cadence/stop-evidence-20260909.json) ne donnent pas de statut HTTP. L'absence de reçu ne prouve pas l'absence de départ, et ce timeout seul ne prouve pas un HTTP 403.

## Volume initial et moyenne nominale historique

Avec vingt rencontres toutes en jeu, le plan nominal historique interroge J4, les incidents et les statistiques toutes les 100 secondes, et les compositions toutes les 300 secondes :

```text
20 × (3 × 60 / 100 + 60 / 300) = 40 appels/minute en moyenne nominale
20 × 4 = 80 appels pour un passage initial complet
80 appels répartis nominalement sur 100 secondes = 48 appels/minute en équivalent moyen
```

Ces calculs décrivent une planification, pas un plafond glissant ni un quota reconnu par le fournisseur. Avant le jeu, l'ancien plan n'interroge que J4 et les compositions ; l'admissibilité des familles dépend donc des phases réellement observées.

Dans les captures, les groupes initiaux comportent **58 appels** pour `08c3cd3d…` (20 J4, 9 incidents, 9 statistiques, 20 compositions) et **64 appels** pour `3362612a…` (19 J4, 13 incidents, 13 statistiques, 19 compositions). Le volume théorique de 80 ne correspond donc pas non plus au nombre d'appels initiaux effectivement enregistré dans ces deux campagnes.

## Autres arrêts et exemple à moindre charge

Les cinq captures d'arrêt examinées dans [summary.json](../../.tmp/resilience-study-20260909/summary.json) donnent les résultats suivants. Les maxima d'autorisations et de départs enregistrés sont identiques dans chaque ligne, malgré un `requested_at` manquant sur le dernier essai de chacun des quatre cas timeout.

| Préfixe de campagne | Rencontres | Durée jusqu'au dernier résultat | Essais | Pic sur 60 s | Dernier code |
| --- | --- | --- | --- | --- | --- |
| `08c3cd3d` | 20 | 4 min 25,610 s | 149 | 44 | HTTP_403 |
| `e7e7684b` | 20 | 1 min 15,299 s | 49 | 48 | PLAYWRIGHT_TIMEOUT |
| `857c2cc7` | 20 | 2 min 53,123 s | 88 | 50 | PLAYWRIGHT_TIMEOUT |
| `ef4f028f` | 20 | 3 min 05,309 s | 109 | 52 | PLAYWRIGHT_TIMEOUT |
| `3362612a` | 19 | 4 min 16,643 s | 136 | 62 | PLAYWRIGHT_TIMEOUT |

Aucune de ces cinq captures ne documente plus de cinq minutes jusqu'au dernier résultat. En revanche, la campagne antérieure **`f887fd79-0926-410f-9cf9-7755a13a1ae9`**, avec trois rencontres initiales, couvre **1 h 42 min 47,494 s** entre son lancement à 08:17:51.378261Z et le dernier résultat à 10:00:38.871801Z. Elle compte 391 essais, 313 résultats PARSED et 78 HTTP 404, sans HTTP 403 ni timeout conservé, puis un état `STOPPED_OPERATOR`.

Son pic est de **7 départs observés sur 60 secondes**, dans `(09:34:38.845 ; 09:35:38.845]`, du premier départ compté à 09:35:04.937Z au dernier à 09:35:38.845Z. Le pic d'autorisations est également de 7.

Ce cas confirme qu'une campagne à moindre charge a duré largement plus de cinq minutes. Il ne constitue pas une comparaison contrôlée : le nombre de rencontres, leurs phases et l'historique d'accès diffèrent. Il ne permet pas de fixer un débit garanti acceptable.

## Sources et reproductibilité

Les captures suivantes sont des artefacts locaux ignorés par Git, préexistants à cette relecture. Les liens servent à leur consultation dans ce worktree ; le présent rapport conserve les agrégats, la méthode et les empreintes, mais ne transforme pas ces captures en preuves distribuées avec le dépôt.

| Capture locale | SHA-256 contrôlé lors de la relecture |
| --- | --- |
| [08c3cd3d — capture 10:11:32](../../.tmp/08c3cd3d-cadence/capture-20260909T101132140Z.json) | `132475b5d44b6bdef7ec6b8c29e86f7439dffd6947d2507fb3f0ccea05c6d238` |
| [3362612a — capture 11:41:03](../../.tmp/3362612a-cadence/capture-20260909T114103579Z.json) | `c04149a9de5817b90d1e1f4b675cf2c7d50ee118c40f21d91c9591abd21fcc87` |
| [f887fd79 — capture 10:05:01](../../.tmp/f887fd79-cadence/capture-20260909T100501075Z.json) | `b988af60650229ca7dbf24150859c034c321895ab54afb1b389a6dcf5a9bd796` |

Autres sources du tableau : [e7e7684b](../../.tmp/e7e7684b-cadence/capture-20260909T103230861Z.json), [857c2cc7](../../.tmp/857c2cc7-cadence/capture-20260909T111959640Z.json) et [ef4f028f](../../.tmp/ef4f028f-cadence/capture-20260909T113119293Z.json). Leur synthèse et leurs empreintes figurent dans [summary.json](../../.tmp/resilience-study-20260909/summary.json), schéma `wo058-resilience-offline-metadata-v1`.

Les maxima ont été recalculés en Python à partir des listes triées de `authorized_at`, `requested_at` et `received_at`, avec des fenêtres glissantes de 60 secondes et exclusion des valeurs absentes. Les trois empreintes du tableau ont été recalculées sur les octets des fichiers. La lecture du code historique confirme la sémantique des horodatages. Aucune suite Maven n'a été exécutée pour cette analyse documentaire ; aucun comportement logiciel n'est qualifié par ces seules mesures.
