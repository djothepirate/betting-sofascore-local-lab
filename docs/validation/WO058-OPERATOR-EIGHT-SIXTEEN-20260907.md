# WO-058 — Retour opérateur à 8 rencontres et observation à 16 — 7 septembre 2026

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Référentiel relu : commit `c90c6b633c2fedabeaee5858dd9bd9c2039d3ab0`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`, worktree `.tmp/wo058-live-j4-j5`.
[WO-058](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md) : `READY_FOR_REVIEW`, sans clôture.

## Origine et portée du constat

Le propriétaire rapporte un essai de huit rencontres, dont certaines déjà terminées en réalité
mais encore représentées par un ancien statut local éligible. Il ne constate aucun problème
pendant environ trente minutes, puis arrête volontairement la campagne. Il constate aussi le
refus de lancer simultanément une autre sélection, même sans rencontre commune. Il poursuit
ensuite lui-même l'observation de la seconde campagne lancée.

Sources : déclaration opérateur et trente captures fournies, complétées par une lecture GET
ponctuelle des deux états persistés et de `/events`. Ces lectures sur `127.0.0.1:8087` n'ont
déclenché aucun appel fournisseur. Aucun POST, redémarrage, arrêt de processus, test Maven ou
changement de configuration/base opérateur n'a été effectué pour ce relevé. La campagne active
continue sous le contrôle de l'opérateur. Le commit du référentiel n'est pas une attestation
du binaire exact exécuté par Eclipse.

## Première campagne : huit rencontres, arrêt volontaire

Identifiant : `eeffbed1-c589-485c-94fe-f09ebc51ec2d`.
Lecture locale le `2026-09-07T17:41:38.7842992Z`, HTTP 200, révision 917.

| Mesure | Valeur observée |
|---|---|
| Préparation | 8 rencontres retenues, plafond 20, politique `live-v2` ; capture opérateur |
| Intervalle figé | 210 s, soit 3 min 30 s ; conforme à `max(60, 30 × (8 − 1))` |
| Démarrage | `2026-09-07T16:54:12.539453Z`, soit 18 h 54 à Paris |
| État de campagne au relevé | `STOPPED_OPERATOR` |
| Arrêt des quatre rencontres encore suivies | Transitions entre `17:28:14.685605Z` et `17:28:14.734693Z`, d'après les références de fraîcheur figée ; environ 34 min 02 s après lancement |
| Appels réservés | 181 / 3 000 |
| Octets reçus | 4 665 254 / 15 728 640 000 |
| Cycles manqués | 0 pour chacune des huit rencontres |
| Derniers résultats de famille | 32 `PARSED` ; les 24 derniers J5 sont `COMPLETE`, score de complétude 100 |
| Fraîcheur après arrêt/fin | 32 familles `FROZEN` |

| Rencontre | Dernier score J4 | État final de collecte | Appels réservés | Dernier cycle J5 de finalisation complet |
|---|---|---|---:|---|
| İstanbulspor — Iğdır FK | 1–2 | `FINISHED_CONFIRMED` | 4 | Oui |
| Esenler Erokspor — Kayserispor | 0–3 | `FINISHED_CONFIRMED` | 4 | Oui |
| FC Voluntari — FC Argeș Pitești | 1–0 | `FINISHED_CONFIRMED` | 4 | Oui |
| Al-Khaleej — Al-Riyadh | 0–0 | `FINISHED_CONFIRMED` | 38 | Oui |
| Cagliari — Lecce | 1–0 | `STOPPED_OPERATOR` | 38 | Non établi |
| Getafe — Celta Vigo | 1–0 | `STOPPED_OPERATOR` | 29 | Non établi |
| Göztepe — Gaziantep FK | 0–2 | `STOPPED_OPERATOR` | 32 | Non établi |
| Çaykur Rizespor — Alanyaspor | 0–0 | `STOPPED_OPERATOR` | 32 | Non établi |

Les trois premières rencontres étaient encore `inprogress` dans leurs anciennes observations
locales à la préparation. Leur premier J4 de campagne publie `finished` à `16:54:18.734Z`,
`16:54:21.875Z` et `16:54:25.027Z` (snapshots 1371, 1372 et 1373). Chacune reçoit ensuite
le dernier triplet J5, puis atteint `FINISHED_CONFIRMED`. L'admission locale et cette découverte
réseau de la fin sont deux étapes distinctes ; l'heure passée d'un match ne fabrique pas son statut.

Al-Khaleej — Al-Riyadh est observé en collecte, puis en surveillance de fin. J4 confirme
`finished` à `17:26:38.104Z` (snapshot 1542) ; les dernières réceptions statistiques, incidents
et compositions suivent à `17:26:44.326Z`, `17:26:47.433Z` et `17:26:50.553Z`.
Les captures montrent aussi des passages `WAITING_START` vers `COLLECTING` et des mises à jour
des scores et familles sur les autres rencontres. La valeur de complétude ci-dessus décrit
les dernières données normalisées, sans affirmer que chaque tentative historique a réussi.

## Refus d'une autre campagne simultanée

La préparation `63dcb5ca-8468-46fb-b0a8-001cdeb5ee1b` contient cinq rencontres différentes,
avec un intervalle de 120 s. La capture du lancement à 18 h 58 montre `LIVE_PROVIDER_BUSY`
alors que la première campagne fonctionne encore.

Ce refus est conforme à [ADR-SS-005 v0.2, §3](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) :
une seule campagne fournisseur active globalement. Le plafond configuré limite les rencontres
d'une campagne ; il n'augmente pas le nombre de sessions fournisseur simultanées. La préparation
seule reste locale et ne réserve pas cette session. La comparaison atomique de la session active
dans `LiveCampaignService.launch`, puis l'acquisition du coordinateur fournisseur, appliquent
cette exclusivité. Attendre la fin ou l'arrêt et le nettoyage de la campagne active avant un
nouveau lancement ; refaire une préparation expirée au-delà de ses cinq minutes de validité.
Le retour ne demande pas de modifier cette règle.

## Seconde campagne : seize rencontres, observation en cours

Identifiant : `11a22482-5997-4590-9058-3a89d55e8973`.
Lecture locale le `2026-09-07T17:41:39.1521167Z`, soit 19 h 41 à Paris, HTTP 200, révision 423.

| Mesure | Valeur observée à ce relevé |
|---|---|
| Sélection initiale | 20 cases, dont 4 désormais `finished`, selon les captures |
| Rencontres retenues | 16 ; aucune des quatre rencontres `FINISHED_CONFIRMED` de la première campagne n'y figure |
| Plafond affiché | 20 ; également vérifié par GET `/events` à `17:43:46.4489697Z`, HTTP 200 |
| Intervalle | 450 s, soit 7 min 30 s ; `max(60, 30 × (16 − 1))`, affiché à la préparation et retrouvé dans les intervalles de fraîcheur J4/J5 attendus |
| Démarrage / fin au plus tard | `17:30:21.855846Z` / `21:30:21.855846Z`, soit 19 h 30 / 23 h 30 à Paris |
| État de campagne | `RUNNING` |
| États des rencontres | 9 `COLLECTING`, 7 `WAITING_START` |
| Appels réservés / octets reçus | 83 / 1 828 296 |
| Cycles manqués | 0 sur les seize rencontres |
| Familles déjà collectées | 43 `PARSED` et `FRESH` : 16 J4 et 27 J5 ; ces 27 J5 sont `COMPLETE`, score 100 |
| Familles encore non demandées | 21 J5 `NOT_REQUESTED` et `NOT_EXPECTED`, soit les trois familles des sept rencontres en attente de début |

Ce relevé confirme l'admission au-delà de huit rencontres et un début de collecte cohérent.
Il ne constitue pas le bilan final de la seconde campagne. Les quatre exclusions montrent
le filtrage des fins connues localement avant gel du nouveau manifeste et calcul de sa cadence.

## Référence de qualification affichée dans Eclipse

La capture de configuration montre un plafond de 20, des enveloppes requête et traitement de
`1000ms`, et l'ancienne empreinte `0f1ae6ad44190ae965bec3ad670ffb3c45a140a29856d217f28e71b5db262679`.
Cette empreinte désigne la preuve historique des paliers 2/3. Pour aligner la prochaine
préparation sur le profil adaptatif, utiliser après l'observation en cours, au prochain
redémarrage prévu, `SOFASCORE_LIVE_QUALIFICATION_SHA256` avec la valeur suivante :

```text
99bffa13e057a07fd9493d26e8a186ffa25b2262f03f652da2869347850fceea
```

Le SHA-256 de la [preuve JSON adaptative](WO058-ADAPTIVE-CAPACITY-PROFILE-20260907.json) a été
revérifié. Ce constat porte sur la capture Eclipse ; le relevé GET d'état n'expose pas le profil
gelé du manifeste. Aucun manifeste existant n'est réécrit et aucun arrêt de l'essai n'est requis
pour consigner ce point. Les mesures hors fournisseur et leurs limites restent dans le
[rapport adaptatif](WO058-ADAPTIVE-CAPACITY-20260907.md).

## Traçabilité et vérification documentaire

Les deux réponses JSON de présentation sont conservées localement dans `.tmp/` ignoré, sous
`wo058-operator-<identifiant de campagne>-state.json`. Aucun payload fournisseur brut, cookie,
jeton, capture d'écran ni table normalisée complète n'est ajouté à Git par ce retour.

| Réponse GET | SHA-256 du fichier local UTF-8 |
|---|---|
| Première campagne, révision 917 | `165548c00a665eb28a1aa4b3e8135378f451b290383721083f7ecc088dc2e948` |
| Seconde campagne, révision 423 | `4e563b91bb64b8f5a1197b6802d0e42a638cd280d62e61b504783e567e8b161a` |

Captures pivots de la remise opérateur, identifiées par leur nom et leur empreinte, sans copie dans le dépôt :

| Élément | Fichier `codex-clipboard-<suffixe>.png` | SHA-256 |
|---|---|---|
| Préparation de 8 rencontres | `2bf59ff2-5a8b-49df-bafd-293ff7f78f9b` | `583cc13abf605168f95b00d68b1716ef33b5022ac07f12e77b0ae3d31a195a0b` |
| Arrêt volontaire | `ee789498-e32d-482a-b7f7-340985720c2f` | `b0fd5487d9d9333a8b21d769a5cc29daed36aba4656760740518ac87c05aa4e0` |
| Refus de simultanéité | `18f1dd46-128d-465b-9274-233366ed829c` | `03f1dd9fc0e6861534c9925833622a17c6eba65d78e2eac719c4067cb551ad7b` |
| Configuration Eclipse | `15b2481a-8ddb-41ca-9b8c-e7c562832595` | `f133ed9b2d1173c1d7cd2db9cc7f543a9697312b55738f40d94514602b77c491` |
| 20 sélectionnées, 4 exclues, 16 retenues | `ba8a60dc-1de9-4992-acd9-84a39b3e4dd6` | `aeb9dd0e4614ba6d66f8d5ac8cb54377d243adba26300708276c04073e124d4c` |
| Démarrage de la seconde campagne | `46e0100e-2774-4739-ad77-0484b9ea5b5c` | `1caea39a2b78c17d7e6a357bc0bb507dc1d6cff86d0c1f8a3a6c1b876f0f04b5` |

Changement documentaire uniquement : ce rapport, le WO, le changelog et le guide opérateur.
Vérifications : trois GET locaux HTTP 200, décomptes des réponses enregistrées, empreintes des
sources, liens relatifs, UTF-8 et `git diff --check`. Aucune nouvelle exécution Maven : les
résultats du commit applicatif précédent restent des preuves antérieures, sans réétiquetage.
L'essai à huit est un retour fonctionnel positif borné ; l'observation à seize reste en cours.
Ni un essai fournisseur à 25 rencontres, ni quatre heures de fonctionnement fournisseur, ni
l'absence de toute erreur historique ne sont revendiqués à partir de ces seuls relevés.
