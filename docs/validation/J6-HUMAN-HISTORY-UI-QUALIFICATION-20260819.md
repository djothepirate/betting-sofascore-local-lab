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
J6_BOUNDED_PAGINATION=PASS
J6_READ_ONLY_RETENTION_PREVIEW=PASS
J6_RAW_PAYLOAD_RENDERED=NO
J6_WEB_PURGE_ACTION=ABSENT
J6_APPLICATION_STOPPED_AFTER_UI_QUALIFICATION=PASS
J6_OPERATIONAL_BACKUP_RESTORE=NOT_RUN
J6_PRIMARY_DATABASE_PURGE=NOT_RUN_NOT_AUTHORIZED
J6_WORK_ORDER_STATUS=IN_DEVELOPMENT
```

La qualification humaine de l'interface historique J6 est acceptée. Le premier lot de seize
captures qualifiait les cinq flux, les différences, les comparaisons, la pagination et l'aperçu de
rétention. Deux captures supplémentaires établissent le rejeu idempotent et un cas de contenu
sémantiquement inchangé entre deux sources et parseurs distincts.

Le contrôle local postérieur ne trouve aucun listener sur `127.0.0.1:8087`. L'application est donc
arrêtée après cette qualification. Le Work Order reste néanmoins `IN_DEVELOPMENT`, car la
sauvegarde chiffrée et sa restauration temporaire n'ont pas encore été qualifiées.

## 2. Configuration et périmètre

Le propriétaire a déclaré les cinq activations fournisseur à `false`, avec origine et liste
d'endpoints vides. Cette configuration est consignée dans le rapport de phase 1
`J6-HUMAN-HISTORY-UI-PHASE1-20260819.md`. Le panneau de verrou persistant ne figure pas dans le lot
visuel, mais aucune action fournisseur n'était activée ou nécessaire pour ce parcours synthétique.

L'agent n'a pas lu ou modifié `.env`. Les dix-huit PNG restent externes au dépôt et ne montrent ni
secret, cookie, jeton, payload brut ni action Web de purge.

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

Les deux nouveaux PNG totalisent **339 745 octets**. Avec les seize captures de phase 1, la preuve
humaine complète totalise **18 PNG et 2 317 259 octets**. Aucun PNG n'est ajouté à Git.

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
| comparaison arbitraire non adjacente | PASS |
| provenance, parseurs et hashes | PASS |
| pagination bornée | PASS |
| payload JSON brut dans le rendu | ABSENT |
| action de purge Web | ABSENT |
| application après qualification | STOPPED — aucun listener 8087 |

## 7. Reste à faire pour J6

La partie historique et interface ne possède plus de porte humaine ouverte. Les opérations encore
requises avant la clôture du Work Order sont :

1. produire une sauvegarde PostgreSQL directement chiffrée avec `age`, hors dépôt ;
2. la restaurer dans une base temporaire et qualifier les empreintes ;
3. confirmer la suppression de cette base temporaire ;
4. effectuer la revue finale du diff et de la documentation.

La purge de la base primaire n'est pas requise pour valider J6 et reste `NOT_AUTHORIZED`.
