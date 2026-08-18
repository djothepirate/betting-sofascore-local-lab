# J6 — Qualification humaine finale de l'interface historique — 2026-08-19

## 1. Décision

```text
J6_HUMAN_HISTORY_UI_QUALIFICATION=PASS
J6_FIRST_IMPORT=PASS_6_ADDED_6_ALREADY_PRESENT
J6_SECOND_IMPORT_IDEMPOTENCE=PASS_0_ADDED_12_ALREADY_PRESENT
J6_TOTAL_HISTORY_VERSIONS=STABLE_12
J6_FIVE_HISTORY_STREAMS=PASS
J6_SEMANTIC_DIFF_AND_COMPARISON=PASS
J6_CROSS_PARSER_SEMANTICALLY_UNCHANGED=PASS
J6_REAL_LOCAL_REPARSE_UI=PASS
J6_REAL_LOCAL_REPARSE_EVENT_ID=16412917
J6_REAL_LOCAL_REPARSE_SNAPSHOT_ID=32
J6_REAL_LOCAL_REPARSE_DIFF=18_CHANGES
J6_BOUNDED_PAGINATION=PASS
J6_READ_ONLY_RETENTION_PREVIEW=PASS
J6_RAW_PAYLOAD_RENDERED=NO
J6_WEB_PURGE_ACTION=ABSENT
J6_APPLICATION_STOPPED_AFTER_UI_QUALIFICATION=PASS
J6_OPERATIONAL_BACKUP_RESTORE=PASS
J6_PRIMARY_DATABASE_PURGE=NOT_RUN_NOT_AUTHORIZED
J6_WORK_ORDER_STATUS=VALIDATED
```

La qualification humaine de l'interface historique J6 est acceptée. Le premier lot de seize
captures qualifiait les cinq flux, les différences, les comparaisons, la pagination et l'aperçu de
rétention. Deux captures supplémentaires établissent le rejeu idempotent et un cas de contenu
sémantiquement inchangé entre deux sources et parseurs distincts. Cinq captures finales qualifient
en plus un `LOCAL_REPARSE` réel du même snapshot fournisseur entre les parseurs V3 et V4.

Le contrôle local postérieur ne trouve aucun listener sur `127.0.0.1:8087`. L'application est donc
arrêtée après cette qualification. La sauvegarde chiffrée et sa restauration temporaire ont ensuite
été qualifiées séparément ; le Work Order est désormais `VALIDATED`.

## 2. Configuration et périmètre

Le propriétaire a déclaré les cinq activations fournisseur à `false`, avec origine et liste
d'endpoints vides. Cette configuration est consignée dans le rapport de phase 1
`J6-HUMAN-HISTORY-UI-PHASE1-20260819.md`. Le panneau de verrou persistant ne figure pas dans le lot
visuel, mais aucune action fournisseur n'était activée ou nécessaire pour ce parcours synthétique.

L'agent n'a pas lu ou modifié `.env`. Les vingt-trois PNG restent externes au dépôt et ne montrent
ni secret, cookie, jeton, contenu du payload brut ni action Web de purge.

## 3. Preuve décisive d'idempotence

Après un second clic sur **« Charger la démonstration J6 »**, l'interface affiche :

```text
ADDED_VERSIONS=0
ALREADY_PRESENT_VERSIONS=12
SELECTED_VERSIONS=12
LATEST_STATUS=finished
CANONICAL_EVENT_ID=9740bb59-0207-31a3-a6ae-5c8463255887
PROVIDER_EVENT_ID=900001
```

Le premier chargement avait affiché six versions ajoutées et six déjà présentes. Le second
chargement n'ajoute donc aucune observation métier et retrouve les douze versions existantes. Le
compteur reste stable à douze : le contrat d'idempotence humaine est satisfait.

## 4. Comparaison sémantiquement inchangée 96 → 97

La comparaison dédiée `EVENT_STATE` entre les observations 96 et 97 affiche :

```text
CLASSIFICATION=SEMANTICALLY_UNCHANGED
SEMANTIC_CHANGES=0
BEFORE_SOURCE=j6-scheduled-finished
BEFORE_PARSER=scheduled-events-v1
AFTER_SOURCE=j6-details-late-correction
AFTER_PARSER=event-details-v1
NORMALIZED_SHA256=8cebc55a9478f527af611a00a3935ddb5a2d5f4bf128e3d2873cc9d488182654
```

Les hashes source diffèrent tandis que le hash normalisé reste identique. La classification
`SEMANTICALLY_UNCHANGED` et l'absence de ligne de différence sont donc cohérentes. Ce cas complète
les comparaisons déjà qualifiées en phase 1 :

- compositions 3 → 170, deux changements ;
- état 1 → 2, zéro changement ;
- état 2 → 96, `notstarted` → `finished` ;
- état 96 → 97, sources distinctes mais modèle normalisé identique.

## 5. Inventaire des deux captures de clôture

Les deux PNG de cette première clôture totalisent **339 745 octets**. Avec les seize captures de
phase 1, ce lot comptait alors **18 PNG et 2 317 259 octets**. Les cinq captures complémentaires de
la section 7 portent ensuite la preuve humaine consolidée à 23 PNG. Aucun PNG n'est ajouté à Git.

| N° global | Preuve | Dimensions | Taille | SHA-256 du PNG |
|---:|---|---:|---:|---|
| 17 | comparaison d'état 96 → 97 sémantiquement inchangée | 1326×988 | 152 485 | `f644d3b5b034c6c60adb5760579323cf5965a8123105998d782659b3a1c92372` |
| 18 | second import : zéro ajout et douze versions présentes | 1272×1248 | 187 260 | `b435cc6d796d2211a351fb7740d72e35daada205bea514b6f9b90a2150a26569` |

L'inventaire des captures 1 à 16 est conservé dans
`J6-HUMAN-HISTORY-UI-PHASE1-20260819.md`.

## 6. Matrice finale de l'interface

| Contrôle | Résultat |
|---|---|
| configuration fournisseur déclarée désactivée | PASS |
| chargement synthétique initial | PASS — `6 / 6` |
| second chargement idempotent | PASS — `0 / 12` |
| compteur historique stable | PASS — 12 versions |
| cinq flux, baselines et corrections | PASS |
| changements ajoutés et modifiés | PASS |
| contenu sémantiquement inchangé | PASS |
| reparse local d'un snapshot fournisseur existant | PASS |
| comparaison arbitraire non adjacente | PASS |
| provenance, parseurs et hashes | PASS |
| pagination bornée | PASS |
| payload JSON brut dans le rendu | ABSENT |
| action de purge Web | ABSENT |
| application après qualification | STOPPED — aucun listener 8087 |

## 7. Preuve complémentaire d'un `LOCAL_REPARSE` réel

Cinq captures fournies avant publication GitHub montrent l'historique `EVENT_INCIDENTS` de FC
Kryvbas Kryvyi Rih — FC Livyi Bereh Kyiv, identité fournisseur `16412917`. Les observations 10 et
12 référencent toutes deux le snapshot 32 et le même SHA-256 source
`b1e20d212a51beded7cd21f53fc3a139af0d6fd70b1783953cea6c677972b0cf`. Les octets sont donc identiques
et restent `RETAINED` ; seule leur interprétation locale évolue :

| Élément | Observation 10 | Observation 12 |
|---|---|---|
| parseur | `event-incidents-v3` | `event-incidents-v4` |
| SHA-256 normalisé | `206af04000046e453534901c0d22f8907c57855d1296091949ce98d14583dbfce` | `0c2fb3e3c0fd7e536a7dade195e33ca8e7af32cceac80b95f8403326b23eaa00` |
| complétude | `COMPLETE · 100% · 20/20` | `COMPLETE · 100% · 36/36` |
| score dérivé | `1–0` | `1–0` |
| payload | `RETAINED` | `RETAINED` |

V4 ajoute les identités `playerIn` et `playerOut` déjà qualifiées au J5 pour les huit substitutions,
soit seize signaux supplémentaires. Comme ces identités n'existaient pas en V3, le service J6 ne
force pas leur appariement avec des incidents moins précis : il rend huit `REMOVED`, huit `ADDED`
et les deux compteurs de complétude `CHANGED`, soit 18 changements. Cette représentation respecte
la règle documentée selon laquelle un appariement d'incidents ambigu devient un retrait et un ajout.

La comparaison dédiée 10 → 12 confirme simultanément :

```text
CLASSIFICATION=LOCAL_REPARSE
SOURCE_SNAPSHOT_ID_BEFORE=32
SOURCE_SNAPSHOT_ID_AFTER=32
SOURCE_SHA256_UNCHANGED=YES
NORMALIZED_SHA256_CHANGED=YES
SEMANTIC_CHANGES=18
RAW_PAYLOAD_STATE=RETAINED
SCORE_UNCHANGED=1-0
```

Les cinq nouveaux PNG totalisent **503 650 octets**. La preuve humaine consolidée compte désormais
**23 PNG et 2 820 909 octets**.

| N° global | Preuve | Dimensions | Taille | SHA-256 du PNG |
|---:|---|---:|---:|---|
| 19 | identité, filtre incidents et observation 12 `LOCAL_REPARSE` | 1286×1240 | 107 396 | `fc2bd1b807b99f376988553174b6f4943bcfef8c615c32a15e6151fca604f294` |
| 20 | retraits, ajouts et compteurs de l'observation 12 | 1276×1258 | 92 462 | `75882e6372e4b967d7e8c3310e97ca70496fc894e697dae28d919b18e3fc74e3` |
| 21 | fin du diff et observation 10 de référence | 1406×1188 | 97 047 | `5f13327026a0af048a4b9eee3847ccdbfea01a32cb70235f541abe850c2d2481` |
| 22 | comparaison dédiée 10 → 12, provenance et début du diff | 1282×1264 | 114 708 | `56815a95b5f6dc829da5f83c0cc459f6e01a5600bb4e42381c349c94915e11ec` |
| 23 | suite complète des 18 changements | 1264×1252 | 92 037 | `bf5af5edb205b036b1d06bc7935484b3f484488833db9c3574fd03d1955f3624` |

## 8. État final J6

La qualification opératoire est consignée dans
`J6-BACKUP-RESTORE-QUALIFICATION-20260819.md`. L'interface, le reparse local, la sauvegarde et la
restauration ne possèdent plus de porte ouverte ; le Work Order J6 est `VALIDATED` et archivé dans
`completed`.

La purge de la base primaire n'est pas requise pour valider J6, n'a pas été exécutée et reste
`NOT_AUTHORIZED`.
