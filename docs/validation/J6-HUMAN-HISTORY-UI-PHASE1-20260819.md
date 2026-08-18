# J6 — Qualification humaine de l'interface historique, phase 1 — 2026-08-19

## 1. Décision

```text
J6_HUMAN_HISTORY_UI_PHASE1=PASS
J6_FIVE_HISTORY_STREAMS=PASS
J6_SEMANTIC_DIFF_RENDERING=PASS
J6_DEDICATED_COMPARISON=PASS
J6_NON_ADJACENT_STATE_COMPARISON=PASS
J6_BOUNDED_PAGINATION=PASS
J6_READ_ONLY_RETENTION_PREVIEW=PASS
J6_RAW_PAYLOAD_RENDERED=NO
J6_WEB_PURGE_ACTION=ABSENT
J6_SECOND_IMPORT_IDEMPOTENCE=NOT_RUN
J6_PERSISTENT_NETWORK_LOCK_VISUAL_CONFIRMATION=NOT_IN_CAPTURE_SET
J6_OPERATIONAL_BACKUP_RESTORE=NOT_RUN
J6_PRIMARY_DATABASE_PURGE=NOT_RUN_NOT_AUTHORIZED
J6_HUMAN_HISTORY_UI_QUALIFICATION=IN_PROGRESS
J6_WORK_ORDER_STATUS=IN_DEVELOPMENT
```

La première phase de qualification humaine de l'interface J6 est acceptée dans son périmètre. Les
seize captures fournies par le propriétaire établissent le chargement hors ligne, la présence des
cinq flux, les différences sémantiques, plusieurs comparaisons, la pagination bornée et l'aperçu de
rétention sans action Web de purge.

Cette décision ne qualifie pas encore l'idempotence humaine du second import, la visibilité du
verrou persistant dans le panneau réseau, l'arrêt final de l'application ni la procédure
interactive de sauvegarde/restauration. Elle n'autorise aucune purge sur la base primaire.

## 2. Configuration déclarée par l'opérateur

Le propriétaire indique avoir exécuté cette phase avec les valeurs suivantes :

```text
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=EMPTY
SOFASCORE_ALLOWED_ENDPOINTS=EMPTY
```

Ces valeurs ferment toutes les voies fournisseur et ne définissent ni origine ni endpoint
autorisé. L'agent n'a pas ouvert, lu ou modifié le fichier `.env` ; seules les valeurs non secrètes
transmises dans la conversation sont consignées ici. Les captures ne montrent aucun mot de passe,
cookie, jeton, en-tête de session ou payload fournisseur brut.

## 3. Observations fonctionnelles

### 3.1 Aperçu de rétention

Le tableau de bord présente le bloc **« Aperçu en lecture seule »** et le badge
**« AUCUNE PURGE WEB »**. Les valeurs visibles sont :

```text
RETENTION_DAYS=30
CUTOFF_STRICT=2026-07-19T21:50:27.279377Z
ELIGIBLE_PAYLOADS=0
ELIGIBLE_BYTES=0
BOUNDED_BATCH=0/500
PLAN_TRUNCATED=NO
PLAN_SHA256=7a8b9a334c689b0a1e577947727c07471c51ac92de80fd0a8f2ec8c3c78e3407
```

Le message indique qu'aucun payload ne satisfait toutes les conditions de rétention. Aucune action
d'exécution ou de purge n'est visible.

### 3.2 Chargement du corpus J6

La page locale des événements affiche l'action **« Charger la démonstration J6 »**, le badge
`SYNTHETIC_FIXTURE` et le texte précisant l'absence d'appel réseau et de payload fournisseur. Après
le POST, la chronologie affiche :

```text
CANONICAL_EVENT_ID=9740bb59-0207-31a3-a6ae-5c8463255887
PROVIDER_EVENT_ID=900001
LATEST_STATUS=finished
SELECTED_VERSIONS=12
ADDED_VERSIONS=6
ALREADY_PRESENT_VERSIONS=6
```

Le résultat `6 ajoutées / 6 déjà présentes` est cohérent avec un premier chargement J6 sur les six
versions nominales J4/J5 déjà présentes. Il ne constitue pas encore la preuve du second rejeu : le
prochain import devra afficher zéro ajout et douze versions déjà présentes.

### 3.3 Cinq flux et différences sémantiques

Les captures établissent les résultats suivants :

| Flux | Versions visibles | Résultat principal |
|---|---|---|
| `EVENT_STATE` | observations 1, 2, 96 et 97 | `notstarted` → `finished`, puis état sémantiquement inchangé |
| `EVENT_DETAILS` | observations 1 et 93 | `venue.city` : `Local City` → `Reviewed City` |
| `EVENT_STATISTICS` | observations 1 et 168 | six métriques domicile/extérieur modifiées |
| `EVENT_INCIDENTS` | observations 2 et 169 | deux buts ajoutés, score `1–0` → `2–1`, complétude `3/3` → `5/5` |
| `EVENT_LINEUPS` | observations 3 et 170 | joueur extérieur 9802 : numéro `4` → `5`, `starter=false` → `true` |

Les versions de référence portent `BASELINE`. Les corrections des quatre flux spécialisés portent
`SYNTHETIC_CHANGE`. Les observations d'état 2 et 97, issues respectivement du détail nominal et de
sa correction, portent correctement `SEMANTICALLY_UNCHANGED` lorsque le modèle d'état normalisé ne
change pas. Aucun libellé `LATE_*` n'est attribué à une source synthétique.

### 3.4 Comparaisons dédiées

Trois cas distincts sont démontrés :

1. `EVENT_LINEUPS`, observation 3 → 170 : `SYNTHETIC_CHANGE`, deux changements ;
2. `EVENT_STATE`, observation 1 → 2 : `SEMANTICALLY_UNCHANGED`, zéro changement ;
3. `EVENT_STATE`, observation 2 → 96 : `SYNTHETIC_CHANGE`, deux changements
   (`status.type` et `status.description`).

Le troisième cas confirme explicitement qu'une comparaison arbitraire non adjacente restitue le
passage de `notstarted` à `finished` avec les deux provenances, parseurs et empreintes attendus.

### 3.5 Pagination bornée

Le réglage manuel de la taille à `3` produit quatre pages pour douze versions. Les captures montrent
les positions `1/4`, `2/4` et `4/4`, ainsi que les commandes **Page précédente** et
**Page suivante** aux emplacements appropriés. Le filtre par flux est également exercé sur
`EVENT_DETAILS` et `EVENT_STATE` avec la taille par défaut `25`.

## 4. Matrice de qualification de la phase 1

| Contrôle | Résultat | Limite de la preuve |
|---|---|---|
| toutes les activations fournisseur à `false` | PASS — déclaration opérateur | panneau réseau non inclus dans les captures |
| origine et endpoints autorisés vides | PASS — déclaration opérateur | fichier `.env` non lu par l'agent |
| import J6 hors ligne accessible | PASS | action et badge synthétique visibles |
| identité canonique et état terminal | PASS | UUID, ID 900001, `finished` et 12 versions visibles |
| cinq flux historiques | PASS | chaque famille apparaît dans le lot |
| `BASELINE` | PASS | versions de référence visibles |
| `SYNTHETIC_CHANGE` | PASS | quatre familles corrigées et état final |
| `SEMANTICALLY_UNCHANGED` | PASS | états 1 → 2 et observation 97 |
| valeurs ajoutées et modifiées | PASS | incidents, score, statistiques, compositions et détail |
| comparaison dédiée | PASS | trois comparaisons, dont une non adjacente |
| pagination personnalisée | PASS | taille 3, quatre pages |
| provenance, parseur et hashes | PASS | visibles sur les cartes et comparaisons |
| complétude et score dérivé | PASS | statistiques, incidents et compositions |
| JSON brut dans l'historique | ABSENT | aucune capture n'en rend |
| bouton ou action de purge Web | ABSENT_VISUALLY | aperçu en lecture seule uniquement |
| second import idempotent | NOT_RUN | nouvelle capture requise après un second clic |
| verrou persistant visible | NOT_EVIDENCED_VISUALLY | à inclure dans le prochain lot si disponible |
| sauvegarde/restauration `age` | NOT_RUN | phase opératoire séparée |
| purge primaire | NOT_RUN_NOT_AUTHORIZED | hors périmètre du Work Order actuel |

## 5. Inventaire minimisé des captures

Les seize PNG totalisent **1 977 514 octets**. Ils restent dans le répertoire temporaire de
l'opérateur et ne sont pas copiés dans Git. L'ordre ci-dessous correspond à l'ordre de transmission.

| N° | Preuve principale | Dimensions | Taille | SHA-256 du PNG |
|---:|---|---:|---:|---|
| 1 | aperçu de rétention en lecture seule | 1238×462 | 48 701 | `fbd0b97169449872dbd7d5e4857e65d9b2453960160436282857d78052476de7` |
| 2 | action de démonstration J6 | 1278×1080 | 166 843 | `b152b1c146c4099b89087bc5a96ae5595a4a988a91c55d40a32cea6f9ddaa510` |
| 3 | résultat d'import et chronologie à 12 versions | 1340×1250 | 187 500 | `9e9fb804ccfb3eb549a0bd407f286f7f7d34ddc489f096363ccb6e99862095f3` |
| 4 | comparaison compositions 3 → 170 | 1316×1072 | 171 242 | `183d6019447640509d3e48f1ede2ed64b8c47577d28e3afa3e6e42c1688210e9` |
| 5 | historique des compositions | 1384×1064 | 109 876 | `46e504d885d5a6fee710b07918ee5642355bc47bfdbca4c7c7de726520dadb56` |
| 6 | historique des incidents et score | 1326×1250 | 120 260 | `edfae38d1e8d5586177dea75fa82d9507c0d5a7ffa5c41bf6ec87ab29925fadb` |
| 7 | historique des statistiques | 1320×1216 | 125 577 | `bdb2b439dc1d9bccae2a8b3f3a15e1928e72ebfe71d1b583204b5b9fa00d3139` |
| 8 | filtre et historique des détails | 1312×1242 | 115 263 | `95d5295c0c0f10683f27eeda196d49a267532ca0394ea0b481531d6f15c6a0bc` |
| 9 | historique d'état final et état inchangé | 1348×1254 | 116 734 | `b213b35b953c25580176f9db58b8772db4326e28a7e8c9041997a8e24b76f1e7` |
| 10 | état nominal et baseline | 1312×986 | 96 667 | `666881e0306b175e425a6e24aca955735c3b2a2b6188f33ecafb613e12fd012e` |
| 11 | réglage de pagination à trois éléments | 1292×638 | 50 985 | `3d82371a4d5796a765e7346b49ad71bbfb08abe800ca20d5e94197dda97d16dd` |
| 12 | pagination 1/4 | 1308×1246 | 107 089 | `d6816824ff723face8a7d3105cc256a4c4fdc5b84777b77eb70c39ca513b1675` |
| 13 | pagination 2/4 | 1388×1164 | 113 359 | `152c293c36e90f9c4d9d18b8ce3335315d1ed8c9007274fe9d9579076d2b8845` |
| 14 | pagination 4/4 | 1334×1210 | 115 435 | `ddcca232c8721ad9665c0519869fa64cafaa0256a7bedeea2879c67d5f08a928` |
| 15 | comparaison d'état 1 → 2 sans changement | 1428×966 | 160 392 | `f4e80fe4e137e5be71e968f15ae2e289c7c4047b934d317e90fc363f75bf2ace` |
| 16 | comparaison d'état 2 → 96 avec état final | 1424×1068 | 171 591 | `d38ba9f38af3f162b776f864afeaddba4eeb8dc4ef469ff0e9a953eb5a606ed2` |

## 6. Étape suivante

La phase suivante de qualification de l'interface doit :

1. cliquer une seconde fois sur **« Charger la démonstration J6 »** ;
2. constater `0 version ajoutée` et `12 déjà présentes` ;
3. vérifier que le compteur total reste à 12 ;
4. confirmer visuellement le verrou réseau persistant si le panneau correspondant est disponible ;
5. arrêter proprement l'application après la qualification de l'interface.

La sauvegarde chiffrée et sa restauration temporaire constituent ensuite une qualification
opératoire distincte. Le mode de rétention `Execute` de la base primaire reste interdit sans une
nouvelle autorisation explicite.
