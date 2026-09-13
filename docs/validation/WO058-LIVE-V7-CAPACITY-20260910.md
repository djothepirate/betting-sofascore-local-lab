# WO-058 — Profil de qualification local live-v7

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`,
`NO_CRITICAL_DEPENDENCY`.

## Objet et limite de la preuve

Les nouvelles préparations utilisent exclusivement la politique `live-v7`. Elles restent
volontairement à capacité zéro lorsque les onze paramètres du profil ne sont pas fournis au
démarrage : cette absence ne constitue ni un refus fournisseur ni une indication sur l’état
sportif d’une rencontre. Les profils `live-v4`, `live-v5` et `live-v6` ne sont pas des replis
valables pour une préparation v7.

Ce document versionne la qualification locale qui était jusque-là seulement disponible dans
un répertoire de travail ignoré. Elle a été menée contre un serveur loopback synthétique,
avec trois scénarios, sans appel SofaScore, sans base de l’opérateur, sans changement de
lanceur Eclipse et sans démarrage de campagne réelle. Elle démontre l’admission locale de
trois rencontres pour ce profil précis ; elle ne démontre ni un seuil d’acceptation du
fournisseur, ni la latence Internet, ni une capacité supérieure.

## Preuves exactes et vérifiables hors fournisseur

Les fichiers sont conservés avec leurs octets exacts par `.gitattributes` :

| Artefact | SHA-256 | Rôle |
| --- | --- | --- |
| [Rapport natif](WO058-GROUPED-LIVE-V7-NATIVE-20260909.json) | `884e816b9fef1f2904b71b27314bc6fdded37e85a96a3a12c76186961aefba23` | 35 minutes locales : 420 départs/complétions, zéro cycle manqué. |
| [Profil calculé](WO058-GROUPED-LIVE-V7-PROFILE-20260909.json) | `7b339ee1664abb744639164aa2e7d69388de22ac49db51edadb3b9b33f619335` | SHA à charger dans le lanceur et enveloppes à appliquer. |
| [Calculateur de profil](v7-profile-builder/V7MeasuredProfile.java) | `3fe5a109356531b3160da4ba50e7a13d1cd01564597445284c4c79733890484f` | Rejeu hors réseau de l’admission et des 16 scénarios du planificateur. |

La mesure couvre cinq minutes de mise en régime et trente minutes établies. Elle utilise
un timeout effectif de 30 000 ms, des corps établis de 64 Kio et douze corps initiaux de
5 Mio mesurés séparément. Ces douze corps ne sont pas couverts par les enveloppes établies.
Le maximum observé est de 13 départs par fenêtre de 60 secondes ; les bornes persistantes
restent 25 départs par 60 secondes, 1 000 par heure et au moins deux secondes après une fin
d’échange prouvée.

## Profil à charger manuellement au prochain démarrage

Dans le lanceur Eclipse `SofaScore - PLAYWRIGHT J3-J5 (MANUEL - OPT-IN) (LIVE)`, définir
les variables suivantes avant de redémarrer le Lab. Leur application reste manuelle : le
présent changement ne modifie ni le fichier `.launch` de l’opérateur ni un processus actif.

| Variable | Valeur qualifiée |
| --- | --- |
| `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` | conserver la valeur opérateur existante (`10` actuellement) |
| `SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT` | `30s` |
| `SOFASCORE_LIVE_GROUPED_V7_QUALIFICATION_SHA256` | `7b339ee1664abb744639164aa2e7d69388de22ac49db51edadb3b9b33f619335` |
| `SOFASCORE_LIVE_GROUPED_V7_J4_REQUEST_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V7_J4_PROCESSING_ENVELOPE` | `400ms` |
| `SOFASCORE_LIVE_GROUPED_V7_INCIDENTS_REQUEST_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V7_INCIDENTS_PROCESSING_ENVELOPE` | `550ms` |
| `SOFASCORE_LIVE_GROUPED_V7_STATISTICS_REQUEST_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V7_STATISTICS_PROCESSING_ENVELOPE` | `500ms` |
| `SOFASCORE_LIVE_GROUPED_V7_LINEUPS_REQUEST_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V7_LINEUPS_PROCESSING_ENVELOPE` | `500ms` |

La valeur opérateur existante peut rester à `10` : l’admission v7 calcule une capacité
effective de trois et une valeur supérieure ne permet pas plus de trois rencontres. Il n’y
a donc aucune raison de modifier ce plafond pour charger V7. L’empreinte attendue est celle
du **profil** ci-dessus, et non l’empreinte du rapport natif. Les valeurs par défaut restent
volontairement invalides afin de préserver l’échec fermé en l’absence de qualification explicite.

## Rejeu de régression versionné

`GroupedLiveAdmissionPolicyV7EvidenceTest` lit les trois artefacts versionnés, vérifie leurs
empreintes, les limites de la mesure, les quatre enveloppes, la capacité de trois et le rejet
d’une quatrième sélection par le code de production. Il construit aussi le profil avec les
valeurs du lanceur sans activer le transport. Cette vérification est standard, sans navigateur,
réseau ni base.

La qualification précédente du calendrier v7 est décrite dans
[WO058-PEOPLE-AND-LIVE-V7-20260909.md](WO058-PEOPLE-AND-LIVE-V7-20260909.md). Le runbook
[LIVE-J4-J5-CAMPAIGNS.md](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) reprend les valeurs opératoires
et les frontières avant un lancement explicite.

## Frontières conservées

Les protections persistantes 403/429, l’espacement des 404, la fermeture d’échange et les
attentes bornées restent inchangés. Le profil n’augmente aucun plafond automatiquement,
ne réarme aucune suspension et ne transforme pas une campagne historique v6 en campagne v7.
Une préparation, puis un lancement, restent deux actions opérateur distinctes.
