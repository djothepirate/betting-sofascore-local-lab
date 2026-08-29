# J5 — Qualification comparative Playwright / FlareSolverr

- **Date d'ouverture :** 2026-08-23
- **Dernière mise à jour :** 2026-08-27
- **Work Order :** `WO-SS-20260823-011`
- **Branche :** `codex/j5-playwright-qualification`
- **Statut :** `VALIDATED`
- **Décision ADR :** `ADR-SS-001 v1.3`
- **Appels SofaScore exécutés par ce rapport :** `0`
- **Go fournisseur FlareSolverr S1 / S25 :** `SUPERSEDED_BY_OWNER_DECISION_NOT_RUN`
- **Go fournisseur Playwright S1 / S25 :** `NOT_RECEIVED`
- **Campagnes fournisseur S1 / S25 :** `BLOCKED_BY_PHASE_A_AND_GOVERNANCE`
- **Bras PktMon locaux S1 `NICS` / `ALL` :** `EXECUTED_Q08_INCONCLUSIVE_Q15_PARTIAL`
- **Essai S1 contrat 2.4 r11 :** `FAIL_BEFORE_GATE_RECOVERY_REQUIRED`
- **Essai S1 contrat 2.5 r15 :** `FAIL_RUNNER_NO_OBSERVATION_Q08_INCONCLUSIVE_Q15_PARTIAL`
- **Essai S1 contrat 2.5 r18 :** `FAIL_3_OF_3_EXECUTED_0_SUCCESS_0_FIDELITY_NETWORK_INCONCLUSIVE`
- **Essai S1 contrat 2.5 r19 :** `FAIL_CANDIDATE_AND_NETWORK_NEGATIVE_VERDICT_CHAIN_PASS`
- **Récupération r11 :** `A1_A3_FAIL_CLOSED_NO_MUTATION_A4_FAIL_CLOSED_A5_PASS`
- **Sonde PktMon filter-shape P1 :** `PASS_PROBE_UNCLASSIFIED_DECORATION`
- **Sonde PktMon filter-shape P2 :** `PASS_PROBE_UNCLASSIFIED_DECORATION_EXACT_INVENTORY_FALSE_DECLARED_1_0_1`
- **Sonde P3 :** `PASS_PROBE_OEM_VARIANT_AUTHENTICATED_EXACT_INVENTORY_FALSE_1_0_2_EVIDENCE_V2`
- **Sonde P4 :** `PASS_EXACT_RAW_AUTHENTICATED`
- **Récupération A4/A5 :** `A4_FAIL_CLOSED_A5_PASS_2_1_0`
- **Scan post-récupération :** `PASS_72_FILES_0_FINDING_APPEND_ONLY_TRANSACTIONAL`
- **Contrat réseau forward-only :** `2_5_0_S1_R19_EXECUTED_NETWORK_INCONCLUSIVE`
- **Contrat de verdict négatif :** `R19_REAL_DOUBLE_SCAN_AND_68_TAMPERS_PASS`
- **Diagnostic local à double observateur :** `CLOSED_NOT_PURSUED`
- **S25 FlareSolverr instrumenté sous contrat corrigé :** `NOT_RUN_AFTER_S1_FAILURE`
- **Conclusion technique Q01-Q15 :** `INCONCLUSIVE`
- **Décision comparative propriétaire :** `PLAYWRIGHT_SELECTED`
- **Disposition FlareSolverr :** `REJECTED_AS_FUTURE_TRANSPORT`
- **Implémentation Playwright :** `DEFERRED_TO_SEPARATE_WORK_ORDERS`
- **Qualification fournisseur :** `NOT_PERFORMED_BY_WO_011`

## 1. Portée de la preuve

Ce rapport qualifie d'abord le banc local déterministe. Il ne transforme ni Playwright ni
FlareSolverr en transport métier J5 et ne modifie pas l'import hors ligne multi-match. Le banc est
compilé uniquement avec le profil Maven opt-in `j5-browser-qualification`, sans Spring, base de
données, snapshot ou persistance de payload.

Les quatre campagnes réelles prévues sont :

```text
FLARESOLVERR / S1
PLAYWRIGHT / S1
FLARESOLVERR / S25
PLAYWRIGHT / S25
```

Le propriétaire avait donné les go conditionnels FlareSolverr S1 et S25. Ils ne levaient pas les
portes non compensables : S25 exigeait toujours les prédécesseurs S1, aucun go Playwright réel
n'avait été donné et l'ADR 1.2 limitait FlareSolverr aux fixtures locales. La décision finale du
27 août 2026 les remplace sans qu'aucune campagne fournisseur ait été exécutée.

Les go conditionnels FlareSolverr sont liés au candidat et au scénario fournisseur ; ils ne couvrent
ni l'ajout d'un second observateur réseau ni l'exécution d'un protocole local à double observateur.
Une telle instrumentation exige une autorisation propriétaire distincte. Elle ne vaut ni autorisation
fournisseur ni prédécesseur acquis pour S25.

## 2. Versions installées

| Élément | Version / état | Preuve |
|---|---|---|
| Java | `25.0.4` | exécution locale du banc |
| Playwright Java | `1.62.0` | propriété Maven épinglée |
| Chromium | Chrome for Testing `151.0.7922.34`, révision Playwright `1234` | installation locale du 2026-08-23 |
| Chromium headless shell | `151.0.7922.34`, révision Playwright `1234` | installation locale du 2026-08-23 |
| FFmpeg Playwright | révision `1011` | installation locale du 2026-08-23 |
| Répertoire navigateur | `.tmp/j5-browser-qualification/browser-cache` | ignoré par Git |
| FlareSolverr | `v3.5.0`, révision `4ca91a24f87a73f963e1d6610cbf3b9f01c1cc1b` | vrai sidecar local |
| Image OCI FlareSolverr | exécution locale `ghcr.io/flaresolverr/flaresolverr@sha256:139dfee1c6f89249c8d665d1333a42e8ec74ec0a86bc6bb1c8461e10d3a66a47` ; release qualifiée `ghcr.io/flaresolverr/flaresolverr:v3.5.0@sha256:258523d25e4e07028c3a206f0e03ae807b26a50a201dd320f09a18464ecf86fa` | index OCI et manifeste `linux/amd64` conservés séparément |
| Chromium FlareSolverr | `148.0.7778.178`, Debian 12 | inspection dans le conteneur |
| Docker Desktop | `4.87.0`, moteur/client `29.7.2` | preuve sidecar |

L'installation n'a ouvert aucune cible fournisseur. Le cache navigateur est local, remplaçable et
hors Git.

## 3. Contrôles de préparation

| Contrôle | État | Observation |
|---|---|---|
| amendement ADR autorisé | `PASS` | v1.2 pour le comparatif historique ; clôture et sélection sous ADR-SS-001 v1.3 |
| profil Maven inactif par défaut | `PASS` | dépendance Playwright limitée au profil |
| résolution et compilation du profil | `PASS` | `mvnw.cmd -Pj5-browser-qualification -DskipTests compile` |
| Chromium versionné installé | `PASS` | cache local ignoré |
| source de qualification absente de `src/main` | `PASS` | source set séparé |
| garde statique qualification | `PASS` | atteint la suite Maven sans violation |
| tests Java du banc | `PASS` | tests ciblés du profil opt-in, 0 échec et 0 erreur |
| fidélité locale Playwright `S1` / `S25` | `PASS` | 3/3 puis 75/75 emplacements exacts |
| arrêt supervisé et nettoyage | `PARTIAL` | bornes processus respectées ; GET suspendu non prouvé |
| scan de canaris et purge | `PASS` | `r10-all` passe au niveau du run ; le scan officiel post-A5 r11 couvre ensuite les deux racines, 72 fichiers, 0 finding et aucun brut résiduel ; Q13 est acquis |
| vrai sidecar FlareSolverr local, série Phase A historique non corrigée | `FAIL_FIDELITY` | S1 0/3, S25 0/75 probants ; ce S25 n'est pas la campagne instrumentée corrigée, jamais exécutée |
| manifeste S1/S25 gelé | `PASS_PREPARATION` | 25 événements réels, hash canonique stable |
| mémoire comparable | `INCONCLUSIVE_NO_CORRECTED_FLARE_S25` | Playwright S1/S25 corrigés ; FlareSolverr S1 runner plus conteneur, mais gap maximal 3 019 ms supérieur à 2 500 ms et aucun S25 FlareSolverr corrigé |
| capture réseau OS indépendante | `INCONCLUSIVE_NICS_ALL` | r9/NICS et r10-all/ALL : cycle ETW exact et pertes nulles, mais aucune métadonnée paquet ; Q08 inconclusif, Q15 partiel |
| S1 forward-only 2.5 r15 | `PARTIAL_CONTRACT_PASS_RUNNER_FAIL` | préflight, cycle ETW et gate PASS ; runner 1 et zéro observation ; Q08 inconclusif, Q15 partiel |
| S1 forward-only 2.5 r18 | `FAIL_CANDIDATE_FIDELITY` | fixture locale 3/3 exécutée ; succès, statuts, types et corps exacts 0/3 ; premier scan PASS, second scan bloqué par le protocole |
| S1 forward-only 2.5 r19 | `FAIL_CANDIDATE_AND_NETWORK` | fixture locale 3/3 exécutée ; fidélité 0/3 ; double scan réel PASS, zéro finding et verdict négatif préservé ; Q08 inconclusif, Q15 partiel |
| protocole de verdict candidat négatif | `PASS_REAL_R19` | preuve structurelle PASS/FAIL découplée de la qualification ; double scan réel r19, double scan hors ligne et 68 falsifications couverts |
| go/no-go réel FlareSolverr | `RECEIVED_BLOCKED` | portes locales et ADR non satisfaits |

## 4. Résultats locaux

Dans le tableau suivant, tous les chiffres FlareSolverr S1/S25 proviennent de la première série
Phase A historique non corrigée. Son S25 ne constitue pas un S25 instrumenté sous le contrat final :
aucune campagne FlareSolverr S25 corrigée n'a été exécutée. Les mesures corrigées disponibles sont
présentées séparément sous le tableau.

| Mesure | FlareSolverr local | Playwright local |
|---|---:|---:|
| statut amont observé fidèlement S1 / S25 | `0/3 · 0/75` | `3/3 · 75/75` |
| octets / taille / SHA-256 fidèles S1 / S25 | `0/3 · 0/75` | `3/3 · 75/75` |
| durée scénario 1 | `6 997 ms` | `6 419 ms` |
| durée scénario 25 | `236 904 ms` | `229 657 ms` |
| démarrage S1 / redémarrage S25 | `724 / 523 ms` | `2 458 / 313 ms` |
| base / pic / p95 candidat, première série non comparable | `79,74 / 401 / 397,2 Mio` | `186,551 / 570,527 / 566,664 Mio` |
| pic mémoire incrémental, première série non comparable | `321,26 Mio` | `383,976 Mio` |
| mémoire résiduelle à 5 s / 30 s, première série non comparable | `0 / 0 Mio` | `0 / 0 Mio` |
| coût Docker hôte base / pic | `1 697,621 / 1 823,859 Mio` | `N/A` |
| teardown post-campagne, première série non comparable | `939 ms`, gracieux | `NOT_ISOLATED` |
| arrêt opérateur pendant un appel en vol | `NOT_MEASURED` | `23 / 642 / 136 ms`, preuve partielle sans latch réseau |
| ressources nettoyées | `PASS`, résidu `0` | `PASS`, résidu `0` |
| pages / contextes / sessions nettoyés | conteneur et sessions détruits ; compte interne non publié | `NOT_MEASURED` |
| scan des preuves et logs persistés | six preuves du run sans occurrence ; aucun journal Docker persisté | six preuves du run sans occurrence |

La première série `phasea-*` reste un diagnostic non comparable. Le S1 Playwright corrigé
`pw-s1-corrected-20260824095908` mesure tout l'arbre candidat : base/pic/p95/incrément
`499,680 / 744,328 / 744,328 / 244,648 Mio`, résidus `0 / 0 Mio`, écart médian/maximal
`2 025 / 2 041 ms`. Le S25 Playwright corrigé `pw-s25-corrected-20260824100224` mesure
`320,410 / 574,363 / 571,664 / 253,953 Mio`, résidus `0 / 0 Mio`, écart médian/maximal
`2 000 / 2 082 ms`, tendance `+23,980 Mio` et zéro résidu. Le bras FlareSolverr S1 corrigé
`flare-local-s1-20260824-r10-all` additionne runner Windows et cgroup Docker :
`734,120 / 940,855 / 935,719 / 206,735 Mio`, résidus `0 / 0 Mio`, écart médian/maximal
`2 009 / 3 019 ms` et zéro résidu. Les séries Playwright S1/S25 sont corrigées, mais le maximum
FlareSolverr S1 dépasse `2 500 ms` et aucun S25 FlareSolverr corrigé n'existe ; Q12 reste
`INCONCLUSIVE`. Le run
supervisé `phasea-stop-playwright-20260823` a interrompu `S25` après 54 emplacements : acquittement
en `23 ms`, arrêt en `642 ms`, nettoyage en `136 ms`, aucun arrêt forcé et aucun résidu. Comme le
geste n'est pas relié par un latch à une réponse effectivement suspendue, Q09 reste `INCONCLUSIVE`.

Les runs sont rattachés au commit de base `6c9a6ffef14f...`, mais indiquent
`gitWorktreeDirty=true`, `manifestSha256=null` et `manifestFileSha256=null`. Ils ne prouvent donc ni
Q01, ni une exécution depuis un état Git figé. `providerAccessAuthorized=false` et
`providerAccessPerformed=false` confirment zéro trafic fournisseur dans le périmètre du runner ;
ils ne remplacent pas une capture réseau OS exhaustive.

La sonde locale élevée `pktmon-structured-20260824-r5` a validé le cycle ETW structuré sans trafic :
zéro session cible avant, une session cible exacte pendant, puis zéro après, avec mode circulaire,
16 Mio, fournisseur PktMon attendu, nettoyage complet et aucune sortie native brute persistée. Son
SHA-256 est `e1d40115b91ac8db04d6b4c5de0fa67047883f17f49da4ae4dc7254fc3744615`.

Le bras `flare-local-s1-20260824-r9` sous `NICS`, puis `flare-local-s1-20260824-r10-all` sous `ALL`,
franchissent le gate avec PRE, ACTIVE, BEFORE_STOP et POST exacts, une session cible unique et des
pertes ETW nulles. Élargir PktMon de `NICS` à `ALL` ne fournit aucune visibilité supplémentaire :
`r10-all` publie zéro ligne de métadonnées paquet, endpoint ou record, deux routes locales manquantes
et zéro inattendue. Sa preuve réseau reste `FAIL` avec l'ancien libellé `PKTMON_BRIEF_PARSE_FAILED`,
mais Q08 vaut `INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE`, pas une sortie externe observée. Q15 reste
`PARTIAL_CONTAINER_PATH_ONLY` : le binding Docker `127.0.0.1` est authentifié, mais le chemin
conteneur/hôte ne l'est pas indépendamment. Aucun GET fournisseur ni accès fournisseur n'a été
exécuté.

Au niveau du run, le nettoyage passe : arrêt PktMon et cycle ETW terminal `PASS`, deux artefacts
bruts inventoriés puis écrasés et supprimés, arrêt sidecar gracieux en `1 815 ms`, suppression par
identifiants exacts et zéro résidu. Le scan terminal passe sur deux racines et 15 fichiers avec zéro
canari, corps brut, URL complète, valeur d'en-tête ou finding. Ces sous-contrôles ne valent pas un
PASS global de Q13, qui reste `PARTIAL` faute de couverture complète et de revalidation des contrats
forward-only.

Le run `flare-local-s1-20260825-r11-contract-v3`, sous contrat réseau `2.4.0`, s'arrête avant le gate
et avant toute campagne. PRE, ACTIVE et BEFORE_STOP sont exacts côté ETW, mais le contrôle textuel
localisé refuse l'arrêt réseau et conduit à `RECOVERY_REQUIRED`. Aucun accès fournisseur ni GET n'est
exécuté. Le sidecar est arrêté gracieusement et ses ressources Docker sont absentes ; l'ETL brut de
16 777 216 octets demeure toutefois dans la racine runtime. `r11` reste immuable en échec et, à son
terminal initial, sa récupération append-only n'était pas encore exécutée tant que l'état PktMon
courant n'était pas authentifié.

Le contrat réseau forward-only `2.5.0` publie le préflight v4, l'état v5 et l'attribution v4 avec
`lifecycleAuthority=QUERY_ALL_TRACES_W`, `textStatusRole=TELEMETRY_ONLY` et
`rawTextStatusPersisted=false`. Il introduit une récupération exacte des états historiques incomplets
et un inventaire réel des artefacts bruts, sans réécrire `r10-all` ou `r11`. Le run
`flare-local-s1-20260827-r15-contract-v4` exécute ce contrat, ouvre le gate authentifié et prouve le
cycle ETW exact, mais le runner termine avec le code `1` avant toute observation ;
`candidate-metrics.campaigns` reste vide. Il n'ajoute donc aucune preuve de succès, fidélité,
latence ou durée S1 aux résultats historiques. `NICS`, `ALL` et `r15` restant sans métadonnée paquet,
Q08 demeure inconclusif et Q15 partiel. Un protocole à double observateur exige toujours une
autorisation distincte et S25 reste bloqué.

Le durcissement P1 du 2026-08-25 a été vérifié entièrement hors ligne. Le producteur `state-v5` et
la récupération partagent 42 clés exactes et 11 tuples de provenance fermés ; toute propriété JSON
dupliquée, clé supplémentaire ou valeur d'un type JSON différent est refusée. Le profil historique
`r11` sous 2.4 reste lié à ses trois empreintes immuables, tandis que le profil 2.5 n'accepte que les
preuves forward exactes. Le producteur, le gate et la récupération utilisent tous
`network-runtime/raw/pktmon.etl`. Le runtime brut accepte uniquement l'absence, le dossier exact vide
ou l'ETL exact borné et monolien ; une transition dossier vide vers ETL après arrêt est réauthentifiée
avant suppression. Enfin, le gate relit sidecar, préflight et état réseau avec le même lecteur UTF-8
strict, les mêmes schémas et les mêmes types avant toute authentification persistée ou signature HMAC.
La preuve sidecar doit également porter les références et digests OCI gelés, l'`imageId` exact, le
média OCI, `linux/amd64` et `v3.5.0`; la récupération réutilise ce contrat avant toute mutation. Le
gate refuse tout autre ensemble que les deux routes canoniques dans l'ordre exact : `INBOUND/TCP`
vers `8191`, puis `OUTBOUND/TCP` vers le port réel de la fixture, toutes deux liées à la passerelle
Docker authentifiée.

Les contrôles hors ligne donnent : 28/28 AST PowerShell, politique réseau `PASS`, runtime brut
`PASS`, test d'architecture Maven 12/12 et profil opt-in sur fixtures locales 24/24. Ils n'exécutent
ni PktMon, ni Docker, ni accès fournisseur, ni requête HTTP. Le `mvnw.cmd clean verify` courant
exécute 632 tests mais échoue sur quatre tests exclusivement liés au POC propriétaire hors lot
inchangé, FlareSolverr sur `localhost:8191` et timeout de 15 secondes ; la suite globale reste
 bloquée et n'est pas présentée comme verte. À ce stade, les tentatives A1 à A3 n'exécutaient aucune
 mutation et aucune récupération terminale de r11 n'était encore acquise. Aucune nouvelle campagne
 S1 ou S25 n'était exécutée.

A1 échoue sur `RECOVERY_STATE_AUTHENTICATION_FAILED`; A2 et A3 échouent sur
`RECOVERY_LIVE_STATE_AUTHENTICATION_FAILED`. Elles s'arrêtent avant toute mutation et sans HTTP ni
fournisseur. La sonde PktMon filter-shape read-only `r11-filter-shape-20260825-p1` passe en tant que
sonde, mais classe l'inventaire `UNCLASSIFIED_DECORATION` avec `exactInventory=false`.

La sonde read-only `r11-filter-shape-20260825-p2` déclare le script `1.0.1` et passe elle aussi en
tant que sonde, mais conserve `UNCLASSIFIED_DECORATION` et `exactInventory=false`. Sa preuve SHA-256
vaut `0b80908c802dcbe04004f53d48546bc50035c104d2c33f84f8404e7cd67ff801`. Elle observe sans
changement l'`outputSha256` `4b75b68c9f9896d2a920fe69717e3ea7d3680405bfaa95bdd94a0cd109633fd7`
et l'état réseau source `e5914ff0526e33e8d5999b0aa7de6dba8f07151d7ac8c6db026b56ce50dbeb94`. Aucune mutation
PktMon, requête HTTP ou opération fournisseur n'est exécutée et aucune sortie native brute n'est
affichée ou persistée.

La preuve P2 ne liait pas les SHA-256 du script de sonde et de la politique. P3
`r11-filter-shape-20260826-p3`, script `1.0.2` et schéma `j5-pktmon-filter-shape-v2`, acquiert cette
provenance : les sources de sonde et de politique valent respectivement
`ecaae860e25c667d32b568810762472348743b821ffcc96cd2cddfac431bb361` et
`c0752cd3713e8dc0fe504866a4aaef4de38a51cb2d927a0779b130e47fda9b2f`. P3 rend `PASS`, avec
`UNCLASSIFIED_DECORATION`, `exactInventory=false` et l'enum fermé
`FR_PACKET_SINGULAR_OEM_UTF8_NBSP`. Sa preuve SHA-256 vaut
`b40ad1b24ca5c32eb18e1097b6378f6214ec16f2778eb44d9efa66c8d05aaf40` et son `outputSha256`
`4b75b68c9f9896d2a920fe69717e3ea7d3680405bfaa95bdd94a0cd109633fd7`.

Le diagnostic prouve que le titre est exactement `Filtres de paquet` suivi de `U+252C`, `U+00E1`
puis `:`. Il n'exécute aucun accès fournisseur, requête HTTP ou mutation PktMon et ne persiste ou
n'affiche aucune sortie native brute. Le correctif consécutif accepte ordinalement ce seul titre et
exige sa forme exacte de quatre lignes ordonnées : titre, en-tête, séparateur, unique enregistrement
possédé.

P4 `r11-filter-shape-20260826-p4`, script `1.0.2` et schéma
`j5-pktmon-filter-shape-v2`, acquiert `PASS_EXACT_RAW_AUTHENTICATED`. Sa preuve de 4 969 octets,
strictement UTF-8 sans BOM, porte le SHA-256
`d2e048a05b8380b161427e2929bd839922459a6f723859c67bfbe2e3d75c47f1`. Elle lie l'état réseau
`e5914ff0526e33e8d5999b0aa7de6dba8f07151d7ac8c6db026b56ce50dbeb94`, le script de sonde
`ecaae860e25c667d32b568810762472348743b821ffcc96cd2cddfac431bb361`, la politique
`42d730160cfd917ce492cae1b0e9576bb6c7be8b29c37113ce0afb470e7362a9` et la sortie
`4b75b68c9f9896d2a920fe69717e3ea7d3680405bfaa95bdd94a0cd109633fd7`. La grammaire contient
exactement un titre, un en-tête, un séparateur, un enregistrement possédé et aucune ligne `other` ou
`unclassified`. Elle ne signale aucune normalisation ou anomalie et n'exécute ni fournisseur, HTTP,
Docker ou mutation PktMon ; aucune sortie native brute ni donnée sensible n'est affichée ou
persistée.

A4 passe à `READY_AUTHORIZED_NOT_RUN_2_1_0`, sans acquérir de succès de récupération. Le SHA-256
final de `Recover-J5QualificationNetwork.ps1` `2.1.0` vaut
`7fafa8d6a0fa3aefecb45d0abbbfc1692060751e733ec3c7942b3bf946e062ae`. En phase 5
`AUTHENTICATE_EXECUTABLE_CHAIN`, le récupérateur authentifie la sonde
`ecaae860e25c667d32b568810762472348743b821ffcc96cd2cddfac431bb361`, la politique
`42d730160cfd917ce492cae1b0e9576bb6c7be8b29c37113ce0afb470e7362a9` et l'aide ETW structurée
`e4724bba68414f2fa778ca0893a145e27446f056d3a973df6c97f05b99f7ad80`. En phase 8
`AUTHENTICATE_FILTER_SHAPE_PROOF`, il authentifie la preuve P4
`d2e048a05b8380b161427e2929bd839922459a6f723859c67bfbe2e3d75c47f1` et son contrat réel exact.
Ces empreintes sont recalculées avant chaque mutation et liées aux preuves intention, résultat et
terminal au schéma v3 ainsi qu'aux checkpoints de phase au schéma v2.

Après revue, l'état ETW exact et sa continuité sont relus immédiatement avant `PktMon stop` ;
`filter list` est relu immédiatement avant `filter remove`, avec nom et IPv4 exacts et SHA-256 égal
à la sortie P4. Aucun checkpoint ni autre E/S ne s'intercale entre chaque revalidation et sa
mutation. Les contrats PowerShell et Java verrouillent cet ordre.

La validation hors ligne rend AST PowerShell 28/28 `PASS`,
`Test-J5QualificationNetworkPolicy.ps1` `PASS`, Maven ciblé 17/17 `PASS` et
`P4_REAL_CONTRACT=PASS`. Le `mvnw.cmd clean verify` exécute 632 tests mais échoue sur quatre tests
exclusivement liés au POC propriétaire hors lot inchangé, FlareSolverr sur `localhost:8191` et
timeout de 15 secondes ; il n'est pas présenté comme vert. A4 n'est pas encore exécutée et son
succès n'est pas acquis. Aucun PktMon, Docker, HTTP ou accès fournisseur supplémentaire n'a été
effectué ; la preuve P4 n'autorise aucun nouveau S1 ou S25.

Le vrai sidecar FlareSolverr a terminé les 78 commandes locales, mais sa solution expose un statut
déclaré et une représentation reconstruite. Le banc les classe `DECLARED_BY_SIDECAR` et
`BODY_NOT_BYTE_FAITHFUL`. Aucune valeur n'est inventée ; c'est un échec de Q02/Q03, pas une panne du
sidecar.

## 5. Manifeste réel préparatoire

Le manifeste réel contient uniquement sa version, le fuseau, les trois familles, l'ordre, la
concurrence maximale, le retry, la cadence, les scénarios, puis pour chaque événement l'identifiant
fournisseur, `observedAt`, les deux équipes et le statut canonique `finished`. Il porte son SHA-256
canonique et ne contient ni URL, payload, en-tête, cookie, jeton, profil ou session. `S1` est le
premier événement de `S25`. Une préparation incomplète doit échouer avant tout lancement.

Lorsque le runner réel sera explicitement autorisé et implémenté, les URI exactes ne pourront être
construites qu'en mémoire après validation du manifeste, du candidat, du scénario et du go/no-go.
Aucun fichier de préparation ne les persiste aujourd'hui.

Le fichier CSV source doit se trouver sous `.tmp/j5-browser-qualification/input`, contenir exactement
25 événements `finished` et exactement les colonnes suivantes :

```text
providerEventId,observedAt,homeTeam,awayTeam,canonicalStatus
```

La commande de préparation est :

```powershell
.\scripts\New-J5QualificationManifest.ps1 `
  -EventsCsv .tmp\j5-browser-qualification\input\events.csv
```

Un manifeste existant ne peut pas être écrasé avec des octets différents. La readiness réelle exige
en plus un commit identifiable, un worktree propre, une preuve Phase A `BOTH` et le go/no-go demandé.
Le runner réel demeure volontairement inerte et retourne `REAL_EXECUTION_RESERVED_NOT_STARTED` : le
protocole est préparé, aucune voie réseau réelle n'est encore livrée.

Le panneau réel a été exporté depuis PostgreSQL en lecture seule. Il contient vingt-cinq identités
distinctes et terminées, sans URL ni payload. Son CSV porte le SHA-256
`9d12afe5ddf9b03d5e61022a3766e7e752df981a45254fba428c6d4320cfde99`; le manifeste canonique
porte `eb558bd241a35fca820ade30f8cb307700bf6c1d935ff00aa7910f98f9a6cb0d`. Deux exports et deux
générations ont confirmé `UNCHANGED`. `S1=1`, `S25=25` et S1 appartient à S25. La readiness
`PREPARE_ONLY` retourne `PASS`, `PROCESS_STARTED=NO` et `PROVIDER_ACCESS_PERFORMED=NO`.

Le panneau ci-dessous est l'artefact durable minimal, dans l'ordre canonique du manifeste. Il n'a
été présenté à aucune campagne réelle ni aux runs synthétiques de phase A.

| Ordre | `providerEventId` | `observedAt` | Domicile | Extérieur | Statut |
|---:|---:|---|---|---|---|
| 1 | 15272817 | `2026-08-17T17:05:00.3175120+00:00` | IF Gnistan | Ilves | `finished` |
| 2 | 16248427 | `2026-08-17T13:07:59.5104790+00:00` | Arsenal | Manchester City | `finished` |
| 3 | 16251986 | `2026-08-18T19:58:07.3213780+00:00` | Al Diriyah | Al-Nassr | `finished` |
| 4 | 16251990 | `2026-08-18T18:55:22.0166970+00:00` | Al Jabalin | Al-Ettifaq | `finished` |
| 5 | 16251992 | `2026-08-18T18:53:35.6789880+00:00` | Al-Najma SC | Al-Ittihad | `finished` |
| 6 | 16251994 | `2026-08-17T17:01:37.6338750+00:00` | Al Wehda | Al-Shabab | `finished` |
| 7 | 16251995 | `2026-08-18T19:59:08.2311300+00:00` | Al Faisaly | Neom SC | `finished` |
| 8 | 16251996 | `2026-08-18T18:55:02.0292120+00:00` | Al-Okhdood | Al-Khaleej | `finished` |
| 9 | 16251997 | `2026-08-17T17:01:17.4665470+00:00` | Al Orobah | Abha | `finished` |
| 10 | 16281047 | `2026-08-17T16:27:25.2179860+00:00` | RC Lens | Paris Saint-Germain | `finished` |
| 11 | 16316768 | `2026-08-17T16:29:53.5851890+00:00` | Feyenoord | Go Ahead Eagles | `finished` |
| 12 | 16316781 | `2026-08-17T16:29:35.4923960+00:00` | AFC Ajax | SC Heerenveen | `finished` |
| 13 | 16361884 | `2026-08-17T16:30:22.8496480+00:00` | SK Beveren | RSC Anderlecht | `finished` |
| 14 | 16361886 | `2026-08-17T16:28:14.8525860+00:00` | KV Mechelen | Standard Liège | `finished` |
| 15 | 16390491 | `2026-08-17T16:26:57.8566970+00:00` | Lazio | Mantova | `finished` |
| 16 | 16412912 | `2026-08-17T16:30:09.1469840+00:00` | FC Kharkiv | Shakhtar Donetsk | `finished` |
| 17 | 16421055 | `2026-08-19T21:59:46.1110670+00:00` | Atlético Madrid | Málaga CF | `finished` |
| 18 | 16421061 | `2026-08-17T16:29:06.9738440+00:00` | Real Racing Club | Villarreal | `finished` |
| 19 | 16483640 | `2026-08-17T16:27:46.8668160+00:00` | Beşiktaş JK | Eyüpspor | `finished` |
| 20 | 16483707 | `2026-08-17T16:28:35.3720680+00:00` | Başakşehir FK | Kocaelispor | `finished` |
| 21 | 16671566 | `2026-08-18T11:49:43.7427310+00:00` | Barracas Central | Rosario Central | `finished` |
| 22 | 16707695 | `2026-08-18T21:44:27.8576640+00:00` | Levski Sofia | AEK Athens | `finished` |
| 23 | 16707702 | `2026-08-18T21:44:12.1166990+00:00` | GNK Dinamo Zagreb | Viking FK | `finished` |
| 24 | 16707704 | `2026-08-18T21:43:53.2014290+00:00` | Fenerbahçe | Olympique Lyonnais | `finished` |
| 25 | 16851672 | `2026-08-18T13:43:37.2599750+00:00` | Shanghai Shenhua | Beijing Guoan | `finished` |

## 6. Matrice de décision actuelle

| Porte | État | Motif |
|---|---|---|
| Q01 manifeste réel identique | `PASS_PREPARATION` | panneau gelé ; pas encore présenté à quatre campagnes réelles |
| Q02-Q05 fidélité et campagnes Playwright | `PASS_LOCAL` | S1 3/3, S25 75/75 |
| Q02-Q05 fidélité et campagnes FlareSolverr | `FAIL_LOCAL` | vrai sidecar : S1 0/3, S25 0/75 probants |
| Q06-Q08 concurrence, retry, routes | `INCONCLUSIVE` | `r15` exécute le contrat 2.5, ouvre le gate et prouve le cycle ETW exact ; aucune métadonnée paquet, aucune route normalisée, 2 routes locales manquantes et 0 inattendue : Q08 reste inconclusif |
| Q09 arrêt immédiat en vol | `INCONCLUSIVE` | r15 : acquittement `514 ms > 500`, annulation `151 ms <= 2 s`, nettoyage `112 ms <= 5 s`, sans arrêt forcé ni résidu ; absence persistante de latch en vol |
| Q10-Q11 nettoyage | `PARTIAL` | r15 : PktMon, filtre, conteneur, réseau, volumes et 2 bruts de `15 118` octets nettoyés sans résidu ; les comptes internes page/contexte/session restent non publiés |
| Q12 mémoire comparable | `INCONCLUSIVE` | r15 mesure runner plus conteneur à `354,589 / 544,214 / 539,948 / 189,625 Mio`, cadence `2 008 / 2 021 ms` et résidus `0 / 0`, mais zéro observation et aucun S25 FlareSolverr corrigé |
| Q13 logs sensibles | `PASS` | après A5, le scan officiel r11 passe ; le scan terminal r15 passe aussi sur 16 fichiers, 0 finding et aucun brut résiduel |
| Q14 tests Maven standards | `BLOCKED_BY_OWNER_POC_BASELINE` | vérification finale du 2026-08-27 : 638 tests exécutés, 4 échecs exclusivement liés au POC propriétaire hors lot inchangé : FlareSolverr `localhost:8191` et timeout 15 s |
| Q15 liaison loopback | `PARTIAL` | binding Docker loopback authentifié ; `NICS`, `ALL` et r15 n'attribuent indépendamment que le chemin conteneur, pas la publication côté hôte |

## 7. Décision technique historique et sélection propriétaire

```text
WORK_ORDER_STATUS=VALIDATED
PHASE_A_GATE=FAIL
PLAYWRIGHT_LOCAL_NOMINAL=PASS
FLARESOLVERR_LOCAL=FAIL_FIDELITY
FLARESOLVERR_LOCAL_S1_NETWORK=NICS_ALL_INCONCLUSIVE
FLARESOLVERR_LOCAL_S1_R11=FAIL_BEFORE_GATE_RECOVERY_REQUIRED
R11_RECOVERY=A1_A3_FAIL_CLOSED_NO_MUTATION_A4_FAIL_CLOSED_A5_PASS
PKTMON_FILTER_SHAPE_P1=PASS_UNCLASSIFIED_DECORATION
PKTMON_FILTER_SHAPE_P2=PASS_UNCLASSIFIED_DECORATION_EXACT_INVENTORY_FALSE_DECLARED_1_0_1
PKTMON_FILTER_SHAPE_P3=PASS_OEM_VARIANT_AUTHENTICATED_EXACT_INVENTORY_FALSE
PKTMON_FILTER_SHAPE_P3_SCRIPT=1.0.2
PKTMON_FILTER_SHAPE_P3_EVIDENCE=j5-pktmon-filter-shape-v2
PKTMON_FILTER_SHAPE_P4=PASS_EXACT_RAW_AUTHENTICATED
PKTMON_FILTER_SHAPE_P4_SCRIPT=1.0.2
PKTMON_FILTER_SHAPE_P4_EVIDENCE=j5-pktmon-filter-shape-v2
R11_RECOVERY_A4=FAIL_CLOSED_POST_STOP_RAW_IDENTITY_CHANGED_2_1_0
R11_RECOVERY_A5=PASS_RAW_RUNTIME_REMOVED_2_1_0
R11_POST_RECOVERY_LOG_SCAN=PASS_72_FILES_0_FINDING_APPEND_ONLY_TRANSACTIONAL
NETWORK_CONTRACT=2_5_0_S1_R19_EXECUTED_NETWORK_INCONCLUSIVE
FLARESOLVERR_LOCAL_S1_R15=FAIL_RUNNER_NO_OBSERVATION
R15_NETWORK_CONTRACT=2_5_0_EXECUTED_LIFECYCLE_PASS
R15_Q08=INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE
R15_Q15=PARTIAL_CONTAINER_PATH_ONLY
R15_STOP=FAIL_ACK_514MS_CANCELLATION_CLEANUP_PASS
R15_LOG_SCAN=PASS_16_FILES_0_FINDING
FLARESOLVERR_LOCAL_S1_R18=FAIL_3_OF_3_EXECUTED_0_SUCCESS_0_FIDELITY
R18_INITIAL_LOG_SCAN=PASS_0_FINDING_BINDING_5
R18_SECOND_LOG_SCAN=BLOCKED_BY_CANDIDATE_RESULT_PASS_COUPLING
NEGATIVE_VERDICT_PROTOCOL=R19_REAL_DOUBLE_SCAN_AND_68_TAMPERS_PASS
FLARESOLVERR_LOCAL_S1_R19=FAIL_CANDIDATE_AND_NETWORK_NEGATIVE_VERDICT_CHAIN_PASS
P1_HARDENING=OFFLINE_VERIFIED_R19_EXECUTED
FLARESOLVERR_LOCAL_CORRECTED_S25=BLOCKED
FORWARD_ONLY_EVIDENCE_FIXES=R19_DOUBLE_SCAN_PASS
DUAL_OBSERVER_NETWORK_DIAGNOSTIC=NOT_AUTHORIZED
PLAYWRIGHT_REAL=NOT_RUN
FLARESOLVERR_REAL=NOT_RUN
FLARESOLVERR_REAL_GO=SUPERSEDED_NOT_RUN
REAL_EXECUTION=BLOCKED_BY_PHASE_A_AND_ADR
NO_PROVIDER_CALL_EXECUTED
FORMAL_Q01_Q15_CONCLUSION=INCONCLUSIVE
OWNER_DECISION=PLAYWRIGHT_SELECTED_FOR_IMPLEMENTATION
FLARESOLVERR_DISPOSITION=REJECTED_AS_FUTURE_TRANSPORT
IMPLEMENTATION=DEFERRED_TO_SEPARATE_WORK_ORDERS
```

Playwright est concluant sur les deux campagnes nominales locales. Le vrai sidecar FlareSolverr
échoue aux critères non compensables de statut et d'octets. Le run `r10-all` acquiert le scan et le
nettoyage au niveau du run, mais ne résout ni l'attribution réseau ni Q15. La mesure mémoire S1 couvre
désormais runner et conteneur sans satisfaire la cadence Q12. `r11` échoue avant gate ; A1-A3
échouent avant mutation, puis P1 isole la décoration localisée. P2, déclarée `1.0.1`, reproduit la
même sortie et le même classement non exact sans lier la provenance. P3 `1.0.2` / preuve v2
authentifie ensuite le titre OEM exact et autorise le correctif ordinal étroit. P4 authentifie
`EXACT_RAW` avec la politique corrigée. A4 `2.1.0` arrête PktMon et retire le filtre, puis refuse
l'ETL nouvellement finalisé car son identité diffère de celle authentifiée avant l'arrêt. A5 repart
du mode `RAW_DELETION_REQUIRED`, réauthentifie cet objet de `13 217` octets et le supprime avec un
terminal `PASS`, sans réécrire l'échec historique. À ce point de la séquence, le scan sensible
séparé restait à exécuter ; il a ensuite terminé `PASS` sous le chaînage multi-génération corrigé,
comme établi au paragraphe 8. Aucun HTTP ou accès fournisseur n'a lieu pendant la récupération.
Le contrat 2.5 est forward-only et `r15` en valide l'orchestration réseau, sans réécrire `r10-all`
ni `r11`. Le runner échoue toutefois avant toute observation, de sorte que S1 reste non qualifié.
Aucun de ces résultats n'est remplacé par r18 : celui-ci exécute bien les trois cibles locales mais
confirme un verdict fonctionnel négatif avec zéro succès et zéro fidélité. Son premier scan est
intègre ; le second a été bloqué par un couplage erroné entre recevabilité de la preuve et résultat
`PASS`. Le correctif forward-only dissocie désormais ces deux notions, sans assouplir la
qualification ni le verdict global. Le run neuf r19 établit ensuite la chaîne réelle à deux scans
sous ce contrat corrigé, comme détaillé au paragraphe 11.
Aucun S25 FlareSolverr instrumenté sous contrat corrigé ni protocole à double observateur n'est
autorisé dans cet état.

La conclusion formelle de la matrice ne doit pas être confondue avec la décision propriétaire.
Playwright est le seul candidat retenu parce qu'il franchit localement S1 et S25 avec fidélité,
tandis que FlareSolverr échoue dès S1. La sélection n'affirme ni campagne fournisseur réussie, ni
transport métier déjà implémenté, ni aptitude à la production.

## 8. Récupération r11 A4/A5

La récupération A4 est un échec fermé attendu après mutation partielle : l'arrêt PktMon transforme
l'ETL actif de `16 Mio` en un fichier final de `13 217` octets et d'identité nouvelle. La phase
d'autorisation refuse de confondre ces deux objets. A5 authentifie séparément l'objet final, confirme
son lien unique, son accès exclusif, son identité avant et après écrasement, puis son absence.

Les empreintes terminales A5 sont :

```text
intent.json   e42f0f5972808aec00f1815b397f2f1feee73965d0936de75bb3294a10021092
result.json   57b51df2a3b4bec743b4a1786e0fcbdab35b5a5c0cd8c641bf541f72bbbd8444
terminal.json fdcae6d32031e35c570ba0f939425209e84592345ed7ddca2c182ad369eeb046
```

Les 16 phases A5 sont présentes, ordonnées et authentifiées. `network-runtime/raw` est absent,
la session ETW PktMon cible est absente, le nombre de filtres vaut zéro, les preuves r11/A4 restent
inchangées, et tous les
indicateurs HTTP/fournisseur valent `NO`. L'effacement est une réduction de rétention *best effort*,
pas une garantie d'effacement physique du support.

Le scan sensible post-récupération reste une preuve distincte. Le scanner conserve maintenant une
chaîne append-only au-delà de deux passages : premier scan dans `log-scan-prior.json`, générations
suivantes adressées par SHA-256 sous `log-scan-history`, validation récursive et métriques candidates
inchangées. Un mutex global lié au dépôt et au `RunId`, un journal write-ahead durable, des snapshots
de fichiers revalidés avant chaque mutation, un CAS de la tête et une publication atomique protègent
l'unicité de la transaction. Seuls les flux bruts authentifiés sont supprimés, jamais les JSON
d'évidence. Le contrat local passe sur quatre générations et refuse explicitement concurrence,
chaîne altérée, métriques altérées, jonction descendante, preuve tardive, tête modifiée, verrou
préexistant et runtime brut retenu ; il reprend aussi sans divergence une interruption avant ou après
publication de la tête.

Après A5, le passage officiel conjoint des racines run et sidecar termine `PASS` : 72 fichiers
contrôlés, zéro finding, `RAW_LOGS_REMOVED=YES` et `RAW_RUNTIME_FILES_REMOVED=YES`. La nouvelle tête
`073fe2170e190bf210703460953c1d767c032607cb67b5c7688dfcd9f8f4b207` référence l'archive exacte
`43ac1528cb9aae16dc245678e8c85fcd4efc3fa466818b78a70e303eece14bfc`, qui référence elle-même
`8c32732df78cf5dca28542b5c0c3476ca6fdc54346f1505d3b707a175bbf8af5`. Les métriques
`f477333e4e38daf21bdb5a22a3b05c4155d0bb771e30d394a1c238a235ea1767` et le terminal A5
`fdcae6d32031e35c570ba0f939425209e84592345ed7ddca2c182ad369eeb046` restent inchangés. Aucun
journal, verrou, temporaire ou runtime brut ne subsiste. Q13 passe donc à `PASS`, sans requalifier
le run r11 ni autoriser une campagne S1/S25 supplémentaire.
La readiness FlareSolverr S1 échoue sur
`LOCAL_PHASE_A` et `CLEAN_REAL_WORKTREE`, sans accès fournisseur. Les campagnes réelles restent
bloquées ; les quatre échecs de la suite standard proviennent des modifications POC propriétaire
conservées hors lot.

## 9. Requalification S1 2.5 r15

La campagne locale `flare-local-s1-20260827-r15-contract-v4` est la première exécution S1 sous le
contrat forward-only `2.5.0`. Le préflight v4, l'état v5, l'attribution v4, le cycle
`QueryAllTracesW` et le gate HMAC sont acquis. Le sidecar OCI épinglé démarre sur `127.0.0.1`, sans
pull, et le runner est autorisé uniquement vers les fixtures locales déterministes.

Le runner termine néanmoins avec le code `1` avant la première observation. Les métriques
candidates valent `result=FAIL` et `campaigns=[]` : le run ne mesure ni taux de succès, ni fidélité
des statuts ou des octets, ni latence, ni durée S1. Les résultats historiques FlareSolverr
`0/3 · 0/75` ne sont donc ni remplacés ni aggravés artificiellement par cette absence de mesure.

La capture OS conserve zéro ligne de métadonnées, zéro record et zéro route normalisée. Deux routes
locales attendues manquent et aucune route inattendue n'est observée. Q08 vaut
`INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE`, Q15 `PARTIAL_CONTAINER_PATH_ONLY` et le code réseau est
`PKTMON_NO_PACKET_METADATA_VISIBLE`. Cette insuffisance de preuve ne constitue pas une sortie réseau
inattendue observée.

La mémoire candidat runner plus conteneur mesure base/pic/p95/incrément
`354,589 / 544,214 / 539,948 / 189,625 Mio`, avec une cadence active médiane/maximale
`2 008 / 2 021 ms`, 24 échantillons de campagne, résidus `0 / 0 Mio` à 5/30 secondes et zéro
ressource résiduelle. La cadence respecte la borne, mais zéro observation interdit de qualifier
Q12 ou une durée S1.

L'arrêt superviseur échoue sur le seul acquittement `514 ms > 500 ms`; l'annulation `151 ms` et le
nettoyage `112 ms` respectent leurs bornes, sans arrêt forcé ni processus résiduel. Le sidecar
s'arrête gracieusement en `1 555 ms`, puis son conteneur et son réseau sont supprimés par identifiants
exacts, avec zéro résidu et zéro volume anonyme. Le cycle PktMon terminal passe ; ses deux fichiers
bruts, `15 118` octets, sont inventoriés, écrasés et supprimés. Le scan terminal final passe sur deux
racines et 16 fichiers, avec zéro finding, canari, corps brut, URL complète ou valeur d'en-tête.

Les scripts attestent `providerAccessPerformedByScript=false`, `requestGetExecutedByScript=false`
et `rawRetained=false`. Les principales preuves minimisées et leurs SHA-256 sont :

```text
flare-comparable-local-terminal.json 21d777b94df6bb8b61e36962a907c39f5c6f50835e0069688549acd87da2248e
wrapper-evidence.json                 ca93852dc0ee0008174b1360caa6df7fa978957b15f1e8a4de0f79e1b21ecd08
network-evidence.json                 87895056e908cd2a903c2a0ab6e554efac97fe673e5c583450c142b69947cf46
candidate-memory-metrics.json         6a61d2a2735f4866bfa445786b1d9abe1abfd8795b33fc4c2dad999cecd3c393
candidate-metrics.json                e95bb74104455da740e53856f7065f07741e005f43c2ae2df500bc0d286cfb86
flare-sidecar-state.json              d73d1f3dca60847a452c8b24e78f7b5a1174b557aaf88f1d31e81821dca4e4db
flare-sidecar-stop.json               5495fdc567aff71a44a956bd9874c471ad52ec47c460a08afa105a045172593b
log-scan.json                          40771095bee767d11ff08942dc953d608139a0b8988146e92fcdb40708f8c15b
```

Verdict : `2_5_0_EXECUTED_LIFECYCLE_PASS`, `S1_FAIL_RUNNER_NO_OBSERVATION`, Phase A `FAIL`, S25
`BLOCKED`. Une prochaine exécution devra d'abord rendre la classe d'échec du cycle candidat dans une
preuve minimisée et préserver les lignes `RUN_1_*` même en sortie non nulle ; elle ne doit pas être
confondue avec un nouveau go de campagne.

## 10. Requalification S1 2.5 r18 et protocole négatif

`flare-local-s1-20260827-r18-forward-only-250` exécute les trois cibles de la fixture locale et
termine son runner. Les métriques candidates sont cohérentes avec un verdict négatif : `3/3` cibles
terminées, `0/3` succès, `0/3` statuts exacts, `0/3` types de contenu exacts et `0/3` corps exacts.
Ce résultat confirme l'échec de qualification de fidélité du candidat pour ce run ; il n'est ni une
panne de preuve à convertir en succès, ni une conclusion définitive sur une évolution future.

Le premier scan sensible r18 termine `PASS`, détecte zéro finding et authentifie les cinq liaisons
attendues. Aucun second scan n'est publié : l'acquittement de l'orchestrateur imposait
`candidateMetricsResult=PASS`, alors que le document immuable portait légitimement `FAIL`. La preuve
terminale r18 reste donc globalement `FAIL` et ne permet pas de conclure à l'absence sensible sur la
chaîne complète.

Le correctif forward-only valide désormais séparément :

1. la structure exacte d'un résultat candidat cohérent, qu'il soit `PASS` ou `FAIL` ;
2. la qualification fonctionnelle, qui exige toujours `result=PASS`, harnais complet et fidélité ;
3. la chaîne des deux scans, y compris lorsque le verdict fonctionnel est négatif.

Le contrat hors ligne couvre un double scan négatif préservant les métriques byte-for-byte et 68
falsifications de champs, schémas, types, compteurs et cohérence. Il ne modifie aucune preuve r18 et
ne change ni la Phase A `FAIL`, ni Q08/Q15, ni le blocage S25. Le RunId immuable r19 produit ensuite
la preuve terminale réelle sous le protocole corrigé ; aucun appel fournisseur n'est autorisé par
cette étape.

La validation locale du correctif termine avec : analyse AST PowerShell `29/29`, politique réseau
`PASS`, contrat du verdict négatif `PASS` avec `68/68` falsifications refusées, contrat de chaîne
de scans `PASS`, test d'architecture ciblé `12/12` et profil Maven opt-in `25/25`. Tous ces contrôles
attestent `PROVIDER_ACCESS_PERFORMED=NO` et `HTTP_REQUEST_EXECUTED=NO` lorsqu'ils exposent ces
compteurs ; ils ne constituent pas une nouvelle exécution S1.

## 11. Requalification S1 2.5 r19 et verdict négatif réel

Le run `flare-local-s1-20260827-r19-negative-verdict` franchit le gate et exécute les trois cibles
locales en `6 945 ms`. Son document candidat de 740 octets, SHA-256
`5b8830da36fbf0f74961c769884c9740c8f5373d33d3f68fe310bb5bcf78446a`, est structurellement
cohérent avec `result=FAIL` : `3/3` cibles terminées, `harnessCompleted=false`, `0/3` succès,
`0/3` statuts exacts, `0/3` types de contenu exacts et `0/3` corps exacts. Le runner et le sampler
terminent avec un code `0`, mais le terminal conserve les codes
`CANDIDATE_EXECUTION_NOT_QUALIFIED` et `CANDIDATE_FIDELITY_NOT_QUALIFIED`.

Le premier scan, archivé dans `log-scan-prior.json`, termine `PASS` sur 14 fichiers, zéro finding,
et authentifie cinq liaisons terminales sous
`ec9dc434daeec97c38d26b334421f966f5ed5eb73ac252708910e0984e63e0d1`. Son SHA-256 est
`730b5a0683c50ae6f538d739e46158289bd5fc06f62aa29686301ce451606581`. Le scan terminal termine
ensuite `PASS` sur 17 fichiers, référence exactement ce SHA antérieur, préserve les mêmes métriques
et leur verdict `FAIL`, et publie zéro finding, canari, valeur d'en-tête, URL complète ou corps brut.
La chaîne réelle prouve donc que l'absence de donnée sensible peut être établie sans convertir un
résultat candidat négatif en qualification fonctionnelle. Les 11 références du terminal et les 14
liaisons minimisées readiness/gate/processus/réseau/sidecar/wrapper/scans/métriques existent et leurs
empreintes correspondent toutes.

Le résultat global r19 reste `FAIL`. La preuve réseau ne contient aucune métadonnée paquet visible :
`PKTMON_NO_PACKET_METADATA_VISIBLE`, Q08 `INCONCLUSIVE_CAPTURE_OR_ROUTE_PARSE` et Q15
`PARTIAL_CONTAINER_PATH_ONLY`. Le terminal ajoute donc `NETWORK_ATTRIBUTION_INCONCLUSIVE` et
`NETWORK_ATTRIBUTION_NOT_PROVED`. L'effacement des deux fichiers bruts réseau termine `PASS` en
meilleur effort, aucun brut n'est retenu, et le sidecar s'arrête gracieusement en `1 566 ms` par ses
identifiants exacts, avec zéro résidu réseau, conteneur ou volume.

La série mémoire r19 contient 79 échantillons à 500 ms : base `87,988 Mio`, pic `545,363 Mio`, p95
`545,145 Mio`, pic incrémental `457,375 Mio`, puis résidus `0 / 0 Mio` à 5 s et 30 s. Aucun accès
fournisseur ni `request.get` n'est exécuté. Le protocole de verdict négatif est acquis, mais S1 et la
Phase A restent `FAIL`; le S25 FlareSolverr demeure bloqué. Le `PASS` de `wrapper-evidence.json`
atteste uniquement la fin correcte du wrapper : il conserve `phaseAGate=FAIL`,
`s1BothCandidatesResult=FAIL`, zéro candidat qualifié, `goNoGo=PREPARE_ONLY` et
`providerAccessAuthorized=false`.

## 12. Décision propriétaire finale

Après plusieurs jours de qualification, le propriétaire constate que Playwright a terminé
rapidement les campagnes locales reproductibles S1 et S25 avec la fidélité attendue, alors que les
requalifications FlareSolverr n'ont jamais fourni un S1 local fidèle. Il décide donc le 27 août 2026 :

```text
PLAYWRIGHT_SELECTED_FOR_IMPLEMENTATION
PLAYWRIGHT_TARGET_SCOPE=J3_J4_J5_AND_FUTURE_SOFA_ENDPOINT_MILESTONES
FLARESOLVERR_REJECTED_AS_TRANSPORT_AND_FALLBACK
FLARESOLVERR_FURTHER_QUALIFICATION=ABANDONED
PROVIDER_QUALIFICATION_BY_WO_011=NOT_PERFORMED
BUSINESS_TRANSPORT_IMPLEMENTATION=DEFERRED_TO_SEPARATE_WORK_ORDERS
```

Les preuves locales établissent Playwright comme le seul candidat retenu : S1 `3/3` et S25
`75/75` satisfont la fidélité des statuts, types de contenu et octets, tandis que FlareSolverr
termine r19 avec `0/3` sur ces critères. La matrice comparative globale reste historiquement
inconclusive et aucune campagne fournisseur n'a été exécutée par ce Work Order.

L'ADR-SS-001 v1.3 porte désormais l'orientation d'architecture. L'intégration sur J3, J4, J5 et
chaque futur jalon exige un Work Order distinct, une allowlist exacte et la reconduction des
garde-fous locaux, manuels, opt-in, sans retry, fallback, état navigateur persistant ni démarrage
automatique. Le POC propriétaire FlareSolverr reste hors lot et ses preuves historiques restent
conservées sans être requalifiées.
