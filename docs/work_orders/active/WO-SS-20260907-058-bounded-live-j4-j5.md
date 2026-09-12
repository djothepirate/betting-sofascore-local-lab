# WO-SS-20260907-058 — Campagnes live locales J4/J5 sur sélection de rencontres

- **Statut :** `IN_PROGRESS` — lot compositions/personnes V4 et cadence live-v7/V47 réalisé et qualifié hors fournisseur ; revue humaine, acceptation fournisseur et fusion restent distinctes. La révision `live-v8` à dix rencontres, 60 s et budget local relevé est désormais qualifiée sur loopback, avec son profil exact ; les neuf variables V8 ont été livrées de façon atomique au lanceur Eclipse local avec sauvegarde, tandis que la revue et la fusion restent distinctes. Cette preuve locale ne vaut ni acceptation ni quota du fournisseur. Le correctif v0.9 réponses lentes/timeouts isolés et groupes v6 avec familles différées reste qualifié fonctionnellement hors fournisseur. Le premier lot de résilience v6/V42–V44 et son profil temporel à sept rencontres conservent leurs validations réussies, distinctes de ce nouveau chemin. Les réalisations et preuves historiques v4/V39, v5/V40 et compositions V3/V41 restent conservées ci-dessous ; aucune clôture ni campagne fournisseur n'est effectuée par cette qualification.
- **Date :** 2026-09-07.
- **Premier lot du 09/09, antérieur au correctif courant :** résilience `live-v6`/V42–V44 qualifiée fonctionnellement hors fournisseur. Le [complément temporel dédié](../../validation/WO058-LIVE-V6-CAPACITY-20260909.md) qualifie son propre profil à sept rencontres, avec wrapper partagé, PostgreSQL 44 et ordonnanceur v6 réels ; sa vérification finale réussit (2 019 cas Surefire, cinq ignorés, 207 intégrations, trois contrôles Chromium UI sans échec ni erreur). Le plafond opérateur de six et le timeout de 30 s constatés pour cette qualification restent distincts de la capacité prouvée. Ces résultats restent distincts de la qualification fonctionnelle réussie du correctif v0.9.
- **Jalon :** expérimentation live locale après J9, distincte des parcours manuels existants.
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
- **Cible de PR :** `feature/V0.1.0-RC01`.
- **Base exacte :** `6dfd14286d4f269cbe100bd965257c20298538db`, sommet GitHub vérifié le 7 septembre.
- **Worktree :** `.tmp/wo058-live-j4-j5`, depuis le dossier Codex du Lab ; worktree distinct d'Eclipse.
- **Autorité reçue :** ADR-SS-005 v0.1 accepté, puis déclaration « Je valide le WO-058 les travaux peuvent commencer » et demande explicite d'exécuter le plan de réalisation ; port 8087 libéré pour les tests.
- **ADR courant :** [ADR-SS-005 v0.11](../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md), qui conserve les décisions historiques et encadre les nouvelles préparations `live-v10` à huit rencontres au plus, 35 départs comptabilisés / 60 s et 2 100 / heure. La protection persistante commune J3/J4/J5, la capacité v6 au plus sept et la capacité v7 au plus trois restent distinctes ; les manifestes v1–v9 gardent leurs règles. Proposition v0.1 acceptée conservée au SHA-256 `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e`.
- **Évolution V8 autorisée :** demande propriétaire du 10 septembre de conserver la cible de fraîcheur à 60 s et de préparer une admission locale plus agressive : dix rencontres au plus, fence local de 500 ms, 45 départs/minute et 2 756 départs/heure. Pour un lancement qui précède T0 de moins d'une minute, le premier contrôle de bascule reste sur la phase stable `max(T0 sérialisé, J4 initial + 60 s)` afin de ne pas créer une seconde vague J4/J5 au même créneau. Le constat ultérieur d'un report de cadence de près de six minutes autorise le correctif local associé : une vague J4 dont le départ authentifié sort de la fenêtre stricte reste manquée, mais son prochain J4 est reproposé sur la phase stable suivante au lieu d'hériter du backoff de timeout. Elle ne crée aucun endpoint, transport, proxy, rotation d'adresse ou lancement fournisseur ; l'ADR n'est pas modifié dans ce lot documentaire.
- **Révision V10 en cours :** décision propriétaire du 12 septembre de limiter les nouvelles campagnes à huit rencontres sélectionnables et d'appliquer un plafond local durable de 35 départs comptabilisés dans toute fenêtre de 60 s, 2 100 par heure. La charge nominale visée est `8 × 4 = 32` départs/minute. Cette borne est une protection interne du Lab : elle ne prouve aucun quota, accord, seuil ou disponibilité du fournisseur, n'ajoute aucun mécanisme d'évitement et ne modifie pas les campagnes V9 déjà persistées.
- **État V10 :** `IN_PROGRESS` — la mise en œuvre `live-v10`, la migration append-only V54, le replay local et les preuves ciblées unitaires, PowerShell et PostgreSQL sont réalisés. La validation opérateur, la revue humaine, la PR et la fusion restent à effectuer avant toute clôture. La révision V9 historique reste consultable avec sa preuve et ses règles propres.
- **Livrable présent :** ADR accepté, WO validé, correctif v0.9 réalisé et [rapport de qualification fonctionnelle](../../validation/WO058-SLOW-TIMEOUT-RECOVERY-20260909.md), avec inventaire de 56 fichiers, commandes, empreintes et résultats ; premier lot de résilience et profil temporel dédiés déjà qualifiés. La [preuve de capacité V8](../../validation/WO058-LIVE-V8-CAPACITY-20260910.md) versionne aussi le rapport natif et le profil loopback à dix rencontres ; la [note de reprise de cadence](../../validation/WO058-LIVE-V8-CADENCE-RECHECK-20260910.md) isole son recontrôle J4 sans modifier le transport. Ces preuves restent distinctes d'une acceptation fournisseur ou d'une modification du lanceur. Le complément V4/V46/v7/V47 est détaillé dans son [rapport hors fournisseur](../../validation/WO058-PEOPLE-AND-LIVE-V7-20260909.md) et le [profil v7 versionné](../../validation/WO058-LIVE-V7-CAPACITY-20260910.md) rend ses trois rencontres, ses enveloppes et son SHA reproductibles sans réglage automatique. Le [complément pays/métriques](../../validation/WO058-LINEUPS-COUNTRY-AND-METRICS-20260910.md) conserve la frontière de provenance des compositions V3. Les réalisations et preuves antérieures restent conservées, notamment v5/V40 et le complément compositions V3/V41 avec son [contrat](../../architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md) et sa [qualification du 09/09](../../validation/WO058-PLAYER-DETAILS-20260909.md). La preuve de fraîcheur fournisseur ne découle pas de la qualification locale.
- **Alignement de gouvernance :** renvois ciblés dans ADR-SS-001 et AGENTS.md ; ADR-SS-002 à 004 inchangés.
- **Réalisations historiques :** réalisées et qualifiées hors fournisseur, correctifs HTTP 404/sélection puis plafond paramétrable jusqu'à 25 vérifiés sous les anciennes politiques ; compléments prématch/phase/clôture et incidents V16/V17 décrits dans les retours ci-dessous, statistiques intégrées aux pages. Les préparations v5 conservées restent limitées à vingt rencontres selon leur propre qualification ; **validation formelle du WO :** acquise ; **revue de réalisation :** à effectuer ; **campagnes fournisseur historiques :** essai à 8 arrêté volontairement, essai à 16 interrompu après coupure PostgreSQL, puis nouveaux lancements manuels à 7 et à 4 ; dernière exécution de cette série terminée, observations distinctes des qualifications locales.

Les statuts restent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`. Le socle reste Java 25 LTS, Spring Boot 4.1.0, Maven wrapper,
PostgreSQL local Docker Desktop et application sur `127.0.0.1:8087`, textes UTF-8.

## Lot historique du 9 septembre — cartes, personnes J4 et live-v7

Autorité : demande des décorations de cartes, pays/entraîneurs/arbitre/tour nommé,
périodes d’incidents repliables et nouvelles règles de collecte. La clarification
« Oui, attendre le coup d’envoi » confirme la pause T−5/T0. Le dernier exemple fourni
montre des drapeaux chargés comme images ; le Lab utilise des SVG embarqués localement,
sans nouvel endpoint ou appel d’image fournisseur.

L’[ADR courant v0.10](../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) isole les
nouvelles préparations v7 des manifestes existants. La qualification du profil 25/min,
1 000/h et deux secondes après fin d’échange porte sur trois rencontres à 60/60 s.
Les deux plafonds sont des paramètres locaux révisables : à la demande du propriétaire,
un candidat 40/min et 2 000/h est analysé séparément, sans activation ou modification
rétroactive. La rotation automatique d’IP reste exclue et les 403/429 suspendent toujours
l’accès de façon persistante.

Le [contrat V4](../../architecture/J4-J5-PEOPLE-V4.md) décrit les métadonnées facultatives,
leur provenance, V46 et les drapeaux locaux. Le [bilan de campagne réelle 4087041a](../../validation/WO058-REAL-CAMPAIGN-4087041A-20260909.md)
est conservé comme observation opérateur distincte de la qualification synthétique.
Aucun déploiement Eclipse, redémarrage opérateur, lancement fournisseur ou migration
de la base opérateur n’est effectué par l’agent. Les résultats de qualification et leurs
limites sont consignés dans le [rapport V4/V46/v7](../../validation/WO058-PEOPLE-AND-LIVE-V7-20260909.md).

Complément validé pendant ce lot : un statut J4 `delayed` garde la rencontre dans une
nouvelle campagne `live-v7` lorsque J4 fournit un nouvel `startTimestamp`. Les fenêtres
T−60/T−5/T0 sont recalculées sur cet horaire ; un report sans horaire et une régression
`inprogress` → `delayed` restent à revoir. Cette évolution ne réactive pas le suivi
historique déjà arrêté. Les libellés des indisponibles sont aussi localisés dans la seule
projection web, y compris `Strain Injury` → « Blessure à l’entraînement », sans changer les
attributs fournisseur persistés. Pour les compositions historiques V3, le rendu peut compléter le
pays uniquement depuis le snapshot local référencé, après vérification stricte du type, de l’identité,
du hash et du JSON ; aucune observation normalisée ni provenance n’est réécrite. Sans preuve locale
exploitable, le champ pays est omis. Les six métriques individuelles `accurateKeeperSweeper`,
`totalKeeperSweeper`, `hitWoodwork`, `errorLeadToAShot`, `errorLeadToAGoal` et `clearanceOffLine`
sont localisées dans leur rubrique de présentation. Voir le [rapport pays et métriques](../../validation/WO058-LINEUPS-COUNTRY-AND-METRICS-20260910.md).

## Révision V8 du 10 septembre — dix rencontres et départs normaux à 60 s

Le propriétaire demande de conserver l'objectif de fraîcheur à **60 secondes** tout en
préparant un lissage local plus dense. Cette révision porte uniquement sur les nouvelles
préparations `live-v8` : les manifestes v1–v7, leurs preuves et leurs paramètres restent
lisibles avec leurs propres règles. Une préparation v8 sans profil complet et vérifié doit
échouer fermée, avec capacité zéro ; le profil v7 ne devient jamais un repli de v8.

| Élément V8 qualifié localement | Règle de conception et borne versionnée |
| --- | --- |
| Sélection | Au plus **10 rencontres**, sous réserve de l'admission du profil qualifié et des plafonds de campagne déjà figés. |
| Cadence normale en jeu | Pour **chaque couple rencontre/famille** (J4, incidents, statistiques, compositions), un départ est planifié dans une vague de 60 s. Le départ, et non l'heure d'affichage ni une réception inconnue, est la mesure de la cible. |
| Lissage local | Un contexte et un départ à la fois ; fence local fixe de **500 ms** après une fin d'échange prouvée. V51 réserve en plus **1 s** entre deux groupes V8, séparément des quatre fences internes, afin de préserver ce fence sous le jitter J4 borné de 500 ms. Une attente plus longue peut encore venir du slot déterministe, d'un budget, d'un 404, d'un timeout ou d'une suspension ; elle ne doit pas être masquée comme fraîcheur. |
| Budgets partagés | Au plus **45 départs sur 60 s glissantes** et **2 756 départs sur une heure glissante**, persistants entre campagnes. La planification horaire de dix rencontres consomme au plus **2 480 / 2 756** départs (276 restent non alloués) ; cette marge est distincte de la borne temporelle V51 `N × réserve de groupe <= 60 s`. Ce sont des budgets locaux choisis par le propriétaire, pas un seuil d'acceptation SofaScore. |
| Vague initiale sous lease | Après acquisition du `CampaignLease` exclusif, la prélecture persistante exige une marge locale de **`4 × N` départs** (40 pour dix cibles). Elle est volontairement en lecture seule et ne réserve aucun départ ; chaque émission continue à réserver atomiquement. Cette marge concerne la vague runtime V8 phasée, pas le scénario de stress `INITIAL_COLD_START_STRESS` à quarante réponses de 5 Mio, qui reste séparé et ne revendique pas une place dans les 60 s. |
| Planification | Les slots par rencontre et famille sont dérivés des enveloppes requête/traitement immuables du profil, des fences et de la réserve V51. Un J4 normal est proposé 500 ms avant son échéance de départ ; une émission authentifiée au-delà de cette borne bascule en `WAITING_CADENCE_RECHECK`, conserve son groupe J4/J5 manqué et ne lance aucun rattrapage. Le prochain J4 est offert sur la phase stable dérivée de son dernier `REQUEST_SENT` (`+ 60 s − 500 ms`), sans prendre le backoff de cinq minutes réservé aux timeouts et dépassements d'enveloppe. |

La révision persiste le fait de départ V8 dans un ledger append-only distinct de la réservation :
une trame worker `REQUEST_SENT` n'est retenue que si son instant est compris entre la
réservation atomique et l'observation parent. Sans cette preuve, la complétion conserve une
entrée de repli plus conservatrice ; le verrou non résolu reste inchangé. Les fenêtres de
45/minute et 2 756/heure reposent donc sur l'horodatage authentifié lorsqu'il existe, sans
réinterpréter l'historique. Un budget qui franchit une échéance V8 fait passer la cible et les
autres cibles en jeu touchées par le même hold à `WAITING_PRESSURE_RECHECK` : les familles
réellement non parties sont comptées manquées et seul un J4 explicite redémarre à `notBefore`.
Le chemin normal, les 404, les délais prématch, les timeouts et les refus gardent leurs règles
propres.

Un dépassement de cadence J4 n'est pas un timeout transport : le groupe concerné et ses J5
ordinaires restent explicitement manqués, puis seul un J4 `CADENCE_RECHECK` est offert à la
prochaine phase stable de cette rencontre. Cette reprise n'ajoute aucun départ et reste soumise
aux slots, au fence et au budget partagé. Les J5 ne reparaissent qu'après la réponse `inprogress`
de ce nouveau J4. Les délais de cinq minutes restent inchangés pour une fin de transport ambiguë,
un timeout récupérable ou un dépassement d'enveloppe.

La [note de validation de cette reprise](../../validation/WO058-LIVE-V8-CADENCE-RECHECK-20260910.md)
lie la régression à `500 ms + 1 ns`, le profil V8 recalculé et les limites qui demeurent inchangées.

V50 étend aussi la preuve J6 de sauvegarde/restauration au ledger
`provider_departure_accounting` : son empreinte complète et son compteur source/restauration
doivent être égaux, avec au moins une ligne comptable par complétion. V51 ne modifie pas cette
preuve : il valide la réserve temporelle inter-groupe V8 et devient la garde de schéma courante
du runbook J6. Cette préparation ne lance ni outil natif, ni sauvegarde, ni purge, ni opération
sur la base opérateur ; elle ne qualifie donc aucune exécution J6.

Les fenêtres prématch V7 sont conservées dans leur portée : groupe initial, éventuel contrôle
T−60, compositions toutes les cinq minutes entre T−60 et T−5 lorsqu'elles ne sont pas
confirmées, puis attente du coup d'envoi. Après `inprogress`, les quatre familles utilisent
la vague V8 de 60 s. Un J4 `delayed` avec un `startTimestamp` lisible recalcule ces fenêtres
sur le nouvel horaire ; une heure absente ou une régression après `inprogress` reste à revoir.
Un 404 demeure différé par couple rencontre/famille, sans rattrapage en rafale.

La suspension est indépendante du profil : un **403 ou 429 connu** reste persistant à travers
la fin de campagne, le redémarrage et une nouvelle sélection. Le réarmement reste manuel,
consultable et sans sonde fournisseur ; changer d'IP, ajouter un proxy, faire tourner un VPN
ou recréer un contexte pour contourner un refus ne fait pas partie de ce lot. Les timeouts et
échanges dont la fin n'est pas prouvée n'autorisent pas une nouvelle vague et ne comptent pas
comme une réception fraîche.

### Conditions observables V8 vérifiées sur loopback

| Contrôle hors fournisseur | Résultat établi par la passe locale |
| --- | --- |
| Admission | Dix cibles sont admises seulement par le profil mesuré ; une onzième et un profil absent/incohérent sont refusés sans création de transport. |
| Cadence normale | Le replay à coûts variables, mais contenus dans les enveloppes, mesure les départs `requestedNanos` de chacun des 40 couples rencontre/famille à 60 s au plus. |
| Pression et lissage | Aucun chevauchement ; fence de 500 ms, maximum observé ≤45 sur 60 s et ≤2 756 sur une heure, y compris après changement de campagne ou redémarrage simulé. |
| Réponse lente et absence | Un dépassement d'enveloppe, un timeout ou un 404 est étiqueté et reporté sans rafale ni instant de réception fictif ; la cadence normale n'est pas revendiquée pour ce cas. |
| Refus | 403/429 aux en-têtes suspend l'accès durablement, même avec corps incomplet ; une nouvelle campagne, un redémarrage ou un délai écoulé ne le réarme pas. |
| Confinement | Chromium/worker et PostgreSQL de test utilisent exclusivement le serveur loopback ; la preuve compte zéro appel fournisseur, zéro base opérateur et aucun artefact de session persistant. |

### Qualification V8 fraîche, exclusivement hors fournisseur

La passe `Invoke-LiveGroupedPlaywrightQualification.ps1 -PolicyVersion live-v8` du 10 septembre
a franchi la qualification V8 sur une origine éphémère `127.0.0.1`, avec worker de production,
Chromium et PostgreSQL de test. Elle a commencé la phase stricte après quarante réponses froides
de 5 Mio et leur drain de 60 001 ms, puis a tenu 1 800,0198031 s établies et 2 100,0198031 s de
voie stricte. Les résultats natifs sont 1 444 appels, dont 1 203 établis et 40 froids, zéro appel
fournisseur, zéro requête hors périmètre, zéro base opérateur et zéro cycle manqué. Le timeout
effectif est de 30 000 ms ; le replay de production exécute 16 scénarios.

Le [rapport V8](../../validation/WO058-LIVE-V8-CAPACITY-20260910.md) lie les octets du rapport
natif SHA-256 `f5b70709dcc51d9b40223fde1175d3c507190244562355e675689cb06e9a7fc0` au profil
SHA-256 `c5cef2745422d70bab769d03a93991af8ce3d685fb9daabb64fd00ab0a1ca3c8`. Le profil retient les
bornes immuables DETAILS 300/500 ms, INCIDENTS 300/400 ms, STATISTICS 350/400 ms et LINEUPS
300/450 ms ; les maxima observés arrondis sont respectivement 300/450, 300/400, 300/400 et
300/400 ms. Les quatre fences de 500 ms et la réserve V51 de 1 000 ms donnent une réservation
stricte de 6 000 ms, soit dix groupes dans les 60 s. Les plafonds locaux sont 45/minute et
2 756/heure, dont 2 480 départs horaires planifiés à dix rencontres.

Le WO reste `IN_PROGRESS` : revue humaine et fusion restent à réaliser ; les neuf variables V8
ont été livrées atomiquement dans le lanceur Eclipse local, avec une sauvegarde du fichier
précédent. Le travail ne lance aucune campagne live. Même
une passe verte locale ne démontre ni délai de réception fournisseur, ni acceptation de 45/min,
ni absence future de blocage d'adresse.

### Observations opérateur V8 du 10 septembre et diagnostic durable

Trois campagnes `live-v8` lancées manuellement par l’opérateur le 10 septembre ont reçu un
HTTP 403 complet après 9 min 01 s, 8 min 04 s et 11 min 44 s. Leurs pics locaux documentés
sur 60 s sont respectivement 37, 29 et 29 départs, sous le budget local de 45/minute. Ces
éléments ne caractérisent pas un seuil fournisseur : la famille du refus varie entre J4 détails
et J5 compositions, et l’accès peut être partagé avec du trafic hors du Lab.

Le [relevé opérateur V8](../../validation/WO058-REAL-LIVE-V8-403-OBSERVATIONS-20260910.md)
conserve les identifiants, instants, pics et limites de l’inférence. Le correctif associé expose
désormais, en lecture seule pour chaque campagne, les seuls `REQUEST_SENT` persistés par le worker,
leurs pics glissants de 60 s et cinq minutes, et leur répartition J4/J5. Il exclut les réservations,
le ledger partagé et le trafic hors du Lab ; il ne modifie ni la cadence, ni les budgets, ni la
suspension persistante 403/429, et n’exécute aucune collecte fournisseur.

### Correctif local de réarmement après refus

La page `/provider-access` contient un formulaire de décision locale. Elle recevait par erreur
`Referrer-Policy: no-referrer` alors que la frontière de réarmement exige une origine loopback
exacte : Chromium/Brave peut alors envoyer `Origin: null` et le refus local 403 se produisait
avant le contrôleur. Le correctif sert cette page, y compris sa variante `;jsessionid`, avec
`Referrer-Policy: same-origin`. Il ne relâche pas la frontière : `Origin: null`, une origine
étrangère, les en-têtes de proxy et les jetons invalides restent refusés. Le texte de la page
indique aussi les paramètres live-v8 réels (45/minute, 2 756/heure, fence 500 ms), sans prétendre
remplacer les profils plus conservateurs des parcours manuels ou historiques. Le réarmement reste
manuel, versionné et sans requête fournisseur.

## 1. Objectif et origine du besoin

### Correctif courant du 9 septembre — réponses lentes, timeout isolé et groupe différé

Après les campagnes 651d473e et 02ad2380, le propriétaire autorise le correctif et sa
qualification hors fournisseur, avec maintien de la suspension sur 403/429. Le
[rapport dédié](../../validation/WO058-SLOW-TIMEOUT-RECOVERY-20260909.md) distingue le
timeout historique sans preuve terminale nouvelle et le défaut local de contrôle des
groupes après un 404 ; aucune ancienne campagne n'est requalifiée ou reprise.

La réalisation v0.9 concerne seulement une session v6 déjà lancée. Un timeout peut être
toléré si sa fin CDP corrélée est prouvée (`FINISHED`/`ABORTED`), la page fermée, les cookies
nettoyés et le contexte existant confirmé réutilisable. Avant les en-têtes, l'annulation
est immédiate et exige `ABORTED` corrélé. Après les en-têtes, le worker attend la fin
naturelle `FINISHED` sans `Page.stopLoading`, dans la même grâce de deux secondes ;
cette commande peut supprimer le terminal après `COMMIT`. Sans terminal dans la grâce,
l'issue reste fatale avec fermeture. La seconde IPC supplémentaire n'allonge ni le
timeout de collecte ni ses budgets. Le corps après timeout est abandonné sans snapshot,
même s'il se termine dans cette grâce ; la
tentative reste chargée, le groupe est définitivement fermé et toutes les familles du
match sont différées d'au moins 300 secondes après fin/nettoyage. Le prochain groupe
commence par J4 ; les autres matchs peuvent poursuivre dans leurs budgets.

La politique permet trois tolérances au plus dans la session, refuse deux timeouts
sans `PARSED` intermédiaire et exige aussi un `PARSED` de la même famille avant sa
récidive. Un 404 ne réinitialise aucune de ces bornes. Un timeout admissible en
finalisation arrête le match, sans retry final ; une pause dépassant la fenêtre arrête
également le match. Les arrêts opérateur, quotas, réserves et durée restent prioritaires.
Une campagne peut terminer `COMPLETED` quand tous ses matchs sont terminaux, même si
l'un est `STOPPED_ERROR` : consulter la collecte finale de chaque match, ce statut
global ne signifie pas que toutes les finalisations ont réussi.
Après persistance de la preuve, la décision est sérialisée avec l'arrêt opérateur :
un timeout abandonné ne programme aucun report et n'écrase pas le motif d'arrêt.
La nouvelle échéance utilise le plus tardif de l'instant courant et de la fin observée,
puis au moins 300 s, sans contourner la fenêtre ni les quotas.

Les preuves incertaines et les erreurs hors de cette exception restent globales.
Les 403/429 connus suspendent toujours les accès, même avec un corps incomplet. V45
conserve la preuve de fin/nettoyage sans transformer les diagnostics historiques.
Un succès fixe son instant de réception immédiatement après `body()`, avant nettoyage
local obligatoire ; un timeout n'actualise ni `receivedAt` ni snapshot. Application et
worker doivent être reconstruits ensemble pour IPC v7. V45 attend le prochain démarrage
opérateur ; aucun démarrage ni migration de sa base n'est effectué par ce lot.
L'autorité `LIVE_V6` permet en jeu les familles encore admissibles dans leur ordre
strictement croissant après J4 ; le groupe avec statistiques différées et compositions
dues ne produit plus un refus local d'ordre. Les identités, répétitions, groupes fermés
et autorités historiques restent contrôlés.

Les passes intermédiaires rouges et l'interruption volontaire après revue restent
conservées dans le rapport. Les corrections finales réussissent leurs qualifications :
neuf cas Chromium transport/UI, deux contrôles natifs de réception, puis 100 tests
service/politique incluant deux régressions avec deux rencontres pour acquitter la
tâche en vol après arrêt individuel pendant timeout sans bloquer l'autre rencontre.
La dernière `-Pintegration-tests clean verify` réussit à 17:17:59Z en 8 min 12 s :
2 068 cas standards, cinq skips explicités, 213 cas PostgreSQL, zéro échec et erreur.
Le smoke nominal de trois minutes réussit ; la preuve de capacité de 35 minutes n'est
pas renouvelée. Aucun nouveau profil de capacité, hausse de timeout, changement de VPN,
lancement fournisseur ou livraison Eclipse n'est induit par ce complément.

### Priorité du 9 septembre — résilience face aux refus fournisseur

Après les arrêts sur 403 et timeouts à 10/20/30 secondes, le propriétaire demande de
prioriser la robustesse des campagnes avant toute nouvelle réduction de cadence.
Le [cadrage désormais autorisé pour le premier lot](../../architecture/LIVE-PROVIDER-RESILIENCE-PROPOSAL-20260909.md)
distingue capacité technique qualifiée et acceptation fournisseur, diagnostics de
transport, protection durable entre campagnes, plafonnement de pression et réduction
des interrogations indisponibles. Le propriétaire a ensuite autorisé le lot diagnostic/
suspension puis lissage/404 hors fournisseur. La réalisation ci-dessous est qualifiée
fonctionnellement hors fournisseur ; cette autorisation ne lance aucune campagne fournisseur.

| Règle de la réalisation v6 | Portée et preuve requise |
|---|---|
| Nouvelle sélection au plus sept | Profil `grouped-v6` et SHA propres ; refus au-delà, jamais de retrait silencieux de cibles |
| Nominal 100/300 s | Cible subordonnée au budget partagé ; prochaine échéance et âge restent visibles |
| Deux secondes après chaque fin d'échange | Commune à tous les accès Playwright J3/J4/J5 ; pauses historiques plus longues conservées |
| 25 charges/60 s et 1 000/h glissantes | Charges persistantes vieillissant depuis la fin ; réserve non résolue bloquante, sans remise à zéro au redémarrage |
| Admission avec marge | 120 appels ordinaires + quatre initiaux + quatre finaux par match et heure ; sept valent 896, dans 900 allouables |
| 404 J5 | Par couple : 300/600/900 s, LINEUPS en jeu 600/900 ; reset après PARSED ou transition J4, aucune extension finale |
| 403/429 connus | Suspension persistante dès statut reçu, même avec corps incomplet ; Retry-After validé, réarmement manuel sans requête |
| Diagnostic durable | Étape, code, tentative, famille, timeout et instants ; statut reçu distinct de réponse complète, première cause distincte du nettoyage |
| Budgets de volume | 2 500/20 000 appels, quatre heures, 15 728 640 000 octets ; timeout maximum v6 à 30 s, défaut de base 10 s et réglage explicite opérateur 30 s distincts |
| V42–V44 et J6 | Six tables nouvelles dans comptes/empreinte de restauration, schéma 44 exigé ; aucune réécriture historique ni purge élargie |

Les tests exécutés couvrent les fenêtres de charge et leur persistance, deux admissions
concurrentes, les refus connus avant un corps complet, les timeouts sans statut connu,
la reprise locale sans appel, les 404 puis données disponibles et les reports sans rafale.
Au point `3130e39`, les deux vérifications Maven générales du premier lot réussissent avec 2 016 cas Surefire (cinq ignorés)
et 207 intégrations chacune. Les 40 tests worker/contrôleur et les 20 scénarios Chromium
loopback passent sans échec, erreur ni ignoré. Le
[rapport du premier lot au point `3130e39`](../../validation/WO-058-provider-resilience-qualification-20260909.md)
conserve les commandes, résultats, corrections et limites de nettoyage. Les compteurs, SHA et résultats historiques ne sont pas repris
comme preuves de succès de v6. Aucun seuil antibot, accès autorisé ou capacité fournisseur
n'est déduit des paramètres locaux choisis.

La qualification fonctionnelle du premier lot est terminée. Le
[complément temporel v6 du 09/09](../../validation/WO058-LIVE-V6-CAPACITY-20260909.md)
ajoute une preuve dédiée avec le wrapper partagé, PostgreSQL 44 et l'ordonnanceur v6 :
sept rencontres pendant cinq minutes de mise en régime puis trente minutes établies,
494 échanges dont 420 établis, aucun cycle manqué, au plus 18 départs sur 60 secondes et
2,299724 s au minimum après la fin précédente. Les enveloppes mesurées arrondies vers le
haut et les 24 rejeux Java admettent sept rencontres. Le
[profil JSON](../../validation/WO058-GROUPED-LIVE-V6-PROFILE-20260909.json) porte le SHA-256
`5986e95306ef9b68cb0a96abdb02a5e312cb626277fd0720f9804b6ff5dd0e04`.

Sa portée est `STEADY_64_KIB_ONLY` : corps établis de 64 Kio avec compositions V3 enrichies ;
les 28 corps initiaux de 5 Mio sont mesurés séparément et exclus des enveloppes constantes.
Cette preuve ne qualifie ni des corps de 5 Mio récurrents ni l'acceptation du fournisseur.
Le plafond opérateur de six et le timeout explicite de 30 s sont conservés ; aucune
configuration ni collecte n'est activée automatiquement. La vérification finale du
complément et de son interface réussit. Le WO reste ouvert pour la revue humaine
et la livraison ; ce document n'annonce aucune modification du checkout Eclipse ou de
la base opérateur.

### Complément du 9 septembre — délai Playwright du prochain essai

Après le HTTP 403 de la campagne 08c3cd3d et le PLAYWRIGHT_TIMEOUT de la campagne
e7e7684b, le propriétaire demande un délai Playwright plus élevé pour le prochain
essai. Le changement retient vingt secondes dans le profil local et au maximum
pour live-v5, conformément au complément [ADR-SS-005 v0.7](../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md).
Les limites historiques, budgets et enveloppes d’admission restent distincts.
La vérification porte sur l’admission de vingt secondes, le refus au-delà, les
politiques historiques, une réponse loopback lente reçue et un timeout nettoyé.
Le [rapport ciblé](../../validation/WO058-PLAYWRIGHT-TIMEOUT-20260909.md) distingue
réalisation, tests locaux et prochain lancement opérateur. Aucun changement de
persistance ni lancement fournisseur ne fait partie de ce complément.

### Seizième retour — visibilité des remplaçants et capacité de la prochaine campagne

Après `06c7e3d`, le propriétaire confirme la clôture d’une session interrompue, la nouvelle
préparation et l’actualisation à la minute. Il signale temporairement douze remplaçants
comptés mais onze visibles, puis confirme l’apparition du joueur. La lecture seule de
l’observation concernée et de la projection retrouve les douze joueurs. Un scénario voisin
reproduit un défaut de visibilité Chromium après déplacement natif des cartes ; le correctif
et ses limites sont consignés dans le [rapport compositions](../../validation/WO058-LINEUPS-VISIBILITY-20260908.md).

Le propriétaire demande ensuite 15–20 matchs simultanés, 20 000 appels par campagne, une
hausse du plafond par rencontre, une seconde entre groupes et une cible initiale de vingt
secondes. Informé de l’incompatibilité de cette combinaison avec la file séquentielle et les
coûts qualifiés, il arbitre explicitement : **« Priorité aux 15–20 matchs, avec une cadence
explicitement qualifiée »**. Le candidat retenu est vingt rencontres à 100 secondes, avec
2 500/20 000 appels et compositions en jeu à 300 secondes, politique `live-v5`.

Le premier candidat à 75 s a tenu sa cadence native sur trente minutes établies, mais ses
maxima mesurés conduisent à une admission Java de dix-sept rencontres seulement. Le candidat
est donc porté à 100 s pour respecter la priorité à vingt, sans réduire les coûts retenus.
Le [profil diagnostique à 75 s](../../validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json)
est conservé séparément ; il ne qualifie pas vingt rencontres. La politique courante utilise
trois phases de compositions et vingt-quatre scénarios d’admission.

La mesure dédiée à 100 secondes est terminée le 08/09 à 18:28:31 UTC : 2 100,010 secondes,
dont 1 800,010 établies, 1 413 appels au total, 1 200 établis, zéro cycle manqué et 80 couples
rencontre/famille contrôlés. Le [profil final v5](../../validation/WO058-GROUPED-LIVE-V5-PROFILE-20260908.json)
de SHA-256 `923c4499d2005d4d2f91499148f749dc7f80ea0868ff1cfc7a42106ac9863225`
est `QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE` pour vingt rencontres. Les enveloppes
requête/traitement sont J4 400/600 ms, incidents 400/500 ms, statistiques 350/500 ms et
compositions 350/450 ms ; aucune ne descend sous le plancher du candidat précédent.
Le travail calculé vaut 241 secondes sur 270 allouables par cinq minutes ; les 24 scénarios
Java passent avec ce profil exact.

La réception critique atteint 100,183 s au P95 et 100,520 s au maximum ; le retard nominal
des compositions atteint 2,207 s au P95 et 2,633 s au maximum. Ces résultats concernent
Chromium loopback et PostgreSQL isolé, avec corps établis de 64 Kio. Les quatre-vingts réponses
initiales de 5 Mio sont mesurées séparément, hors des enveloppes constantes. Aucun appel
fournisseur ni accès à la base opérateur n’a été effectué. La fraîcheur réelle chez
l’opérateur reste à observer. Les vérifications Maven finales sont décrites ci-dessous.

Son objectif ultérieur est une bonne opérabilité à 50–100 rencontres. Le bilan doit donc
présenter les cadences minimales estimées selon la charge et séparer estimation, qualification
locale et observation fournisseur. La capacité supérieure à vingt n’est pas activée par
extrapolation. Aucun parallélisme fournisseur ni nouvel endpoint n’est introduit.

Le lot ajoute V40, le profil v5 séparé et la barrière de session à une seconde. Les campagnes
historiques conservent leur politique, leurs plafonds et leur empreinte. Les mesures ciblent
Chromium loopback et PostgreSQL isolé. La campagne opérateur a été préservée pendant les
premiers travaux, puis le propriétaire a libéré le port 8087. Les deux vérifications Maven
finales ont été exécutées après la qualification dédiée, sans concurrence avec son exécution
native, sous le run `20260908T183834Z-14f76e8f805746f8916f65da8aca920b` :

| Commande effective | Résultat et fin UTC le 08/09 |
|---|---|
| `.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify` | `BUILD SUCCESS`, code 0, 18:47:26 UTC |
| `.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pintegration-tests verify` | `BUILD SUCCESS`, code 0, 18:56:06 UTC |

Chaque commande produit 1 840 cas Surefire, zéro échec, zéro erreur et cinq ignorés, ainsi
que 166 cas Failsafe, sans échec, erreur ni ignoré. Les suites PostgreSQL comprennent 73 cas
Flyway et 54 cas de persistance live. Le manifeste de vérification local
`.tmp/wo058-v5-final-verification.json` conserve les commandes, empreintes et XML archivés ;
le [rapport v5](../../validation/WO058-GROUPED-LIVE-V5-20260908.md) relie cette preuve à
la qualification native et à ses limites.

La première passe complète, terminée à 18:34:44 UTC, reste conservée comme échec : les
fixtures de `LivePreparationAdmissionTest` attendaient encore les nouvelles préparations v4.
Leur correction vers le profil v5 explicite conserve les refus de capacité, de stockage et
de preuve manquante ; aucune garde de production n’a été abaissée pour obtenir les passes finales.

### Quinzième retour — clôture accessible après interruption d’Eclipse

Le 08/09 après `73cba517bb6e81d7e6054192027594fa27238ae6`, le propriétaire valide la
présentation des compositions et signale le blocage d’un nouveau lancement après avoir
arrêté Eclipse pour libérer 8087, sans arrêter préalablement la campagne depuis son bouton.
La campagne `35cae8c0-80e2-48c3-90bd-cd7240157efe` est correctement `INTERRUPTED`, mais le
garde `CLEANUP_REQUIRED` conservé au redémarrage n’a aucun parcours de clôture dans l’interface.

Le complément ajoute une clôture locale explicite après preuve d’absence du propriétaire
et des composants Playwright. La transaction compare la génération et toute l’identité du
garde, vérifie les états et tentatives terminaux, puis inscrit `LOCAL_CLEANUP_VERIFIED` et
libère le garde atomiquement. L’interruption, les 140 appels observés et les données de la
campagne restent historiques. Un bouton prépare ensuite une nouvelle sélection avec les
mêmes rencontres, en réappliquant l’éligibilité et l’admission ; aucune ancienne campagne
n’est reprise automatiquement. Les contrôles de report pendant la campagne sont conservés.

Le [rapport de clôture après interruption](../../validation/WO058-ORPHAN-CLEANUP-20260908.md)
consigne les commandes et résultats de ce complément. Le garde opérateur de génération 40
a seulement été lu ; il reste disponible pour la validation du nouveau bouton après livraison.
Le propriétaire a libéré le port 8087 pour les vérifications. Aucune migration, modification
de parseur, collecte fournisseur ni modification d’ADR n’est nécessaire à ce parcours local.

La sonde reconnaît le parent Maven du lanceur Eclipse et les JVM auxiliaires prouvées
antérieures à l’ancien propriétaire, tout en conservant les refus des workers et des identités
incertaines. Le test du vrai `spring-boot:run` utilise uniquement un main de diagnostic local :
verdict `ABSENT` en 1 321 ms, sans démarrer Spring ni accéder à la base. La reconstruction finale
`-Pintegration-tests clean verify` passe le 08/09 à 15:31:00 UTC : 1 788 tests standard exécutés,
cinq skips documentés et 160 tests d’intégration réussis, dont les neuf nouveaux cas de
récupération PostgreSQL. Les onze tests de sonde passent sans ignoré. Le `clean verify`
précédent est également réussi et son périmètre antérieur aux derniers ajustements est explicité.

### Quatorzième retour — compositions lisibles en live et en J5 manuel

Le 08/09 après `1ba2099`, le propriétaire confirme l’annulation des préparations, l’exclusion
des rencontres terminées/reportées et un triplet J5 manuel en environ trois secondes contre
une dizaine auparavant. Sa première campagne v4 à trois rencontres a rapidement reçu trois
compositions, dont une confirmée. Il poursuit ses observations : ce retour ne qualifie pas
encore la fraîcheur fournisseur à dix rencontres en jeu.

Il demande ensuite d’améliorer la présentation des compositions des deux parcours. Le
composant commun sépare domicile et extérieur, affiche noms d’équipes, formation et confirmation,
regroupe les titulaires selon les postes reçus et distingue le banc. Les panneaux sont
repliables et les rafraîchissements préservent leur état et le focus. Le terrain est un schéma
par poste ; aucune position tactique, photo, note, substitution ou information d’entraîneur
n’est inventée. Les observations, leurs empreintes, les parseurs et les cadences restent inchangés.

Le [rapport de présentation](../../validation/WO058-LINEUPS-PRESENTATION-20260908.md) relie
les fichiers, les tests standard/Chromium, les captures synthétiques et leurs limites. La
campagne de l’opérateur n’est pas pilotée par ces contrôles.
Le port est ensuite libéré par le propriétaire. Qualification : 128 tests ciblés et un
scénario Chromium réussis ; `clean verify` terminé le 08/09 à 14:31:57 UTC avec 1 740 tests
standard exécutés, cinq ignorés explicités dans le rapport et 151 tests d’intégration réussis.
Aucun échec ni erreur. Les vues desktop/mobile ont été inspectées sur données synthétiques.

### Treizième retour — activation du profil, annulation et J5 manuel groupé

Le 08/09, le propriétaire signale les cases `notstarted` désactivées avec capacité `live-v4`
à zéro, l’absence d’annulation d’une préparation non lancée et demande explicitement de supprimer
également la pause entre familles d’une collecte J5 manuelle. Ces demandes autorisent le
complément [ADR-SS-005 v0.5](../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md).

Le lanceur Eclipse live conservait le plafond historique 20 sans les neuf nouveaux paramètres
groupés. Le profil qualifié du 08/09 a été ajouté au seul lanceur live, avec sauvegarde locale,
sans modifier ses autres paramètres. Un probe utilisant le binding Spring de `application.yml`
et l’admission de production reproduit zéro depuis la sauvegarde et dix depuis le lanceur corrigé.
Aucune collecte ni écriture de données sportives n’a été déclenchée par ce réglage.

Le complément applicatif ajoute l’annulation explicite de `PREPARED`, conservée dans l’historique,
avec sérialisation PostgreSQL face au lancement. J5 manuel conserve son ordre statistiques,
incidents, compositions et ses contrôles ; seule la pause interne est supprimée dans son groupe
serveur distinct. Les transitions entre collectes, J3/J4 et les politiques live historiques
conservent trois secondes. Les mesures et commandes sont consignées dans le
[rapport de ce retour](../../validation/WO058-PREPARATION-MANUAL-J5-20260908.md).

Les passages `clean verify` et `-Pintegration-tests verify` du 08/09 réussissent chacun
avec 1 738 cas Surefire (5 ignorés) et 151 cas PostgreSQL sans échec. La qualification
Chromium ciblée réussit ses six cas ; les quatre enchaînements internes J5 manuels mesurés
sont de 61 à 63 ms, avec trois secondes entre collectes et aucun appel fournisseur réel.
Les fichiers et empreintes sont dans le relevé lié au rapport. Le lanceur opérateur reste
arrêté ; fermer puis rouvrir Eclipse est nécessaire pour recharger le profil ajouté sur disque.

### Douzième retour — fraîcheur à une minute et groupes live-v4

Le propriétaire a validé les correctifs précédents et constaté un retard de plusieurs minutes
sur les campagnes multi-matchs. Après arbitrage, il demande explicitement l’implémentation du
plan « Campagnes live à 60 secondes par match, avec appels regroupés ». La cible est J4,
incidents et statistiques chaque minute pour dix matchs ; les compositions restent à cinq
minutes pendant le jeu, avec une collecte initiale. Les budgets restent inchangés et la minute
ne doit pas être allongée pour prolonger la campagne ou admettre une sélection excessive.

Ce complément comprend ADR-SS-005 v0.4, contexte de groupe serveur jusqu’au superviseur,
ordonnanceur phasé, admission par enveloppes qualifiées de famille, migration append-only V39,
échéances et retards visibles, autonomie restante et timeout des lectures locales. Les anciennes
préparations et leurs empreintes restent inchangées ; aucune requête fournisseur n’est autorisée
par les tests. La qualification Chromium/PostgreSQL doit isoler l’initialisation et mesurer
au moins trente minutes en régime établi avant toute annonce de capacité à dix matchs.

Critères d’acceptation : P95 des intervalles J4/incidents/statistiques ≤65 s et maximum ≤75 s,
LINEUPS nominal 300 s avec retard P95 ≤15 s, écran actualisé sous dix secondes en situation
normale ; dégradation, annulation, budgets, reports, finalisation, équité, upgrade V38 prérempli,
concurrence et sauvegarde/restauration couverts. Les commandes Maven, mesures, écarts et capacités
effectivement prouvées sont consignés dans le
[rapport live-v4](../../validation/WO058-GROUPED-LIVE-V4-20260908.md).

La mesure native terminée le 08/09 à 11:22:23Z couvre 1 128 appels, dont 960 pendant
1 800,0136 secondes établies après cinq minutes initiales, sans cycle manqué. Les 40 couples
match/famille passent ; maximum de réception critique 60,520 s, retard LINEUPS P95 1,874 s.
Le candidat de traitement à 200 ms est rejeté. Le nouveau profil de coûts par famille contient
les maxima établis sur 64 Kio et admet dix matchs ; la vague initiale de quarante corps de
5 Mio est prouvée séparément. Les corps récurrents de 5 Mio et le réseau SofaScore ne sont
pas qualifiés par ces enveloppes. Aucun profil opérateur n'est abaissé automatiquement.
L'admission finale rejoue quarante scénarios, dont la fin des dix rencontres au même tour,
en plus des cohortes, cinq phases et variations de durée. Le rendu de trente familles sur
dix rencontres passe sous dix secondes : 4,940 s puis 4,880 s avec contenu inchangé reçu à nouveau.

`clean verify` final passe à 12:23:20Z (1 725 Surefire, 144 Failsafe), puis
`-Pintegration-tests verify` à 12:29:12Z avec les mêmes effectifs, sans échec ni erreur.
Les cinq skips Surefire sont les conditions Windows/opt-in historiques documentées ; aucun
skip d'intégration. La qualification Chromium finale des parcours historiques et de l'UI
est consignée dans le rapport de validation avec les empreintes des preuves.
Le smoke groupé final et l'affichage à dix matchs passent le 08/09 à 12:32:02Z :
deux tests, zéro échec/erreur/ignoré, 72 appels sur 120 secondes sans cycle manqué ; rendu
des publications en 4,932 et 4,938 secondes dans la dernière passe.
La qualification historique de coût des corps de 5 Mio échoue sur sa borne d'une seconde,
puis échoue encore au recontrôle identique (cas 5/10/25, maximum 1,254 s). Aucune assertion
n'est affaiblie et le bilan natif global n'est pas déclaré vert. Cette enveloppe historique
non reproduite est distincte de la preuve live-v4 en régime établi de 64 Kio.

La comparaison avec SofaScore reste une campagne opérateur distincte. Ce travail ne migre pas
la base de l’opérateur, ne relance pas une ancienne campagne et ne constitue pas la clôture du WO.

### Onzième retour — résultats J4 et report après lancement

Le 08/09, le propriétaire confirme les encadrés statistiques et demande la reconnaissance
de `finished` + `isAwarded=true` comme « Victoire sur tapis vert », avec les deux scores
`display`. Il demande également l'inéligibilité de `postponed` et précise qu'un report peut
survenir après le lancement : une nouvelle réponse J4 arrête alors uniquement cette rencontre.
Le traitement de `canceled` est explicitement conservé.

Ce complément ajoute `event-details-v3` et V38, sans modifier les parseurs ou migrations
historiques. Le score visible exige une paire domicile/extérieur complète et ne reprend pas
`current`. L'attribution n'est jamais inférée depuis une observation J3 `finished`. La préparation
et le lancement contrôlent les reports ; le manifeste reste immuable et les autres rencontres
continuent après un report live. Le périmètre et les preuves figurent dans le
[rapport J4 du 08/09](../../validation/WO058-J4-AWARDED-POSTPONED-20260908.md).

Qualification : `clean verify` et `-Pintegration-tests verify` verts, chacun avec 1 676 tests
Surefire (0 échec, 0 erreur, 5 ignorés) et 140 tests Failsafe (0 échec, 0 erreur, 0 ignoré).
Quatre scénarios Chromium passent après correction de la variable de cache du lanceur.
Les premiers essais non verts restent consignés séparément ; la base opérateur n'est pas modifiée.

### Dixième retour — encadrés statistiques repliables

Le 08/09, le propriétaire confirme que les améliorations graphiques sont visibles et demande
de pouvoir dérouler/enrouler les encadrés verts des périodes et les sous-encadrés gris des groupes.
Les deux niveaux deviennent des contrôles HTML natifs ouverts par défaut, actionnables au clic
et au clavier, avec états indépendants conservés pendant les rafraîchissements live. Fermer
une période conserve l'état de ses sous-groupes. Données, graphiques, provenance et collectes
restent inchangés ; aucune migration. Base du retour : `f0a32ff72d6eb21ec1c0ac2883b37a6c0cfea8f1`.
Qualification Chromium : 3 tests verts. Après un premier échec J6 dû à l'application opérateur
en écoute sur 8087, le propriétaire libère le port et `mvnw.cmd clean verify` réussit le 08/09
à 00:57:38 Europe/Paris : 1 590 tests Surefire (0 échec, 0 erreur, 5 ignorés) et 138 tests Failsafe
(0 échec, 0 erreur, 0 ignoré). Le premier résultat reste consigné séparément ; aucun processus
opérateur n'est arrêté par l'agent. Les captures bureau/mobile et la revue indépendante sont conformes.
Les preuves figurent dans le [rapport de repli des statistiques](../../validation/WO058-COLLAPSIBLE-STATISTICS-20260908.md).

### Neuvième retour — fin du 07/09, Elche et statistiques applicatives

Le propriétaire constate une nouvelle incompatibilité pour Elche–Real Sociedad, confirme la
fin des collectes du 07/09, et demande que les améliorations graphiques des statistiques soient
visibles dans l'application. Il précise ensuite que le libellé fournisseur `2nd half` est conforme
et doit rester inchangé. L'intégration porte donc sur les statistiques par période, possession
et indicateurs X/Y, à partir des seules données normalisées existantes.

La campagne `ce33a3a5-af5a-4080-94e0-4e87aa7337ae` est enregistrée `COMPLETED`, 15 appels,
558 672 octets : trois rencontres `FINISHED_CONFIRMED` et Elche `STOPPED_SCHEMA_INCOMPATIBLE`.
`COMPLETED` désigne ici la fin de l'exécution ; ce n'est pas une complétude de toutes les familles.
Elche, événement 16416319, échoue sur J5 Incidents, snapshot 2427/occurrence 2394 reçu à
23:39:41.935 Europe/Paris. V16 isole `VALUE_OUT_OF_RANGE` sur `$.incidents[13].reason`,
`Professional handball`, carton rouge extérieur à la minute 64. V17 ajoute ce motif exact,
V37 conserve toutes les anciennes versions admises. Aucun appel fournisseur ni reparse persistant.
Le propriétaire confirme « Main volontaire » pour son affichage français ; la valeur fournisseur
reste conservée dans les données normalisées et le libellé sportif `2nd half` n'est pas traduit.

Le port 8087 est à nouveau libéré par le propriétaire pour qualifier les correctifs.
Le [rapport de cette reprise](../../validation/WO058-ELCHE-STATISTICS-20260907.md) distingue
les preuves opérateur, le replay local, les tests et l'intégration graphique réelle.
Aucune nouvelle collecte n'est démarrée pour compléter la journée.
Qualification finale du 08/09 à 00:20:31 Paris : `mvnw.cmd clean verify -Pintegration-tests`,
code zéro, 1 590 cas Surefire (cinq ignorés documentés) et 138 cas PostgreSQL sans échec/erreur.
Trois tests UI Chromium ciblés passent ; le replay exact V17 produit 26 incidents, 98/98 signaux,
zéro erreur et deux avertissements conservés. Un premier passage a détecté une attente V16
obsolète dans le test d'import courant ; sa correction est couverte par la relance complète.

### Huitième retour — incident `inGamePenalty/awarded` incompatible

Le propriétaire demande l'analyse du JSON de CE Sabadell — Córdoba après identification de
`GET /api/v1/event/16418278/incidents`, snapshot 2340, occurrence 2307, reçu à 22:26:03.170 Paris.
Le replay exact V15 isole `VALUE_OUT_OF_RANGE` sur `$.incidents[5].incidentClass` : `awarded`,
pour un `inGamePenalty` à la minute 83. V16 accepte cette attribution sans lui inventer de
résultat, tireur ou score ; le même corps produit 27 incidents et une complétude de 107/107.
Les règles des anciennes versions et les observations historiques sont conservées. V36 ajoute
la version à l'allowlist SQL, sans modifier les migrations appliquées. Le correctif est raccordé
au live, au J5 manuel et à l'import local. La base opérateur n'est pas migrée dans ce travail.

Le port 8087 a été libéré explicitement par le propriétaire pour reprendre la vérification
complète sans exclusion de méthode. Le [rapport d'incident V16](../../validation/WO058-INCIDENT-AWARDED-20260907.md)
conserve les octets identifiés par hash, le diagnostic, les limites et les résultats exécutés.
Qualification finale : `mvnw.cmd clean verify -Pintegration-tests`, code zéro à 23:27:44 Paris ;
1 530 tests Surefire sans échec/erreur (cinq ignorés documentés) et 136 Failsafe sans
échec/erreur/ignoré. Les 42 cas V16, l'upgrade prérempli et la concurrence SQL sont inclus.
La base opérateur et son checkout Eclipse restent distincts du candidat qualifié.

### Septième retour — compositions prématch, temporalité et interruption Docker

Le propriétaire demande une collecte LINEUPS avant le début et confirme « Oui, collecte initiale
puis périodique ». Les nouveaux manifestes `live-v3` déclenchent donc LINEUPS après J4 `notstarted`,
puis à D ; statistiques et incidents attendent J4 `inprogress`. Les politiques historiques et
la formule de D sont conservées. V35 ajoute leur contrainte sans modifier V33/V34.

La description du statut J4 remplace le seul libellé `inprogress` sur les vues sportives, avec
repli sur le type et provenance inchangée. La demande graphique statistiques par période et
composants possession/X-Y est matérialisée par un aperçu HTML autonome, avec données synthétiques
signalées. Ce prototype ne remplace pas encore le tableau applicatif.

La temporalité est analysée par couple match/famille : réception, changement, répétitions
identiques et dispersion, sans seuil métier arbitraire. Les 937 tentatives des deux premières
campagnes servent au replay documentaire. L'écart Elche à 21 h 19 correspond à l'absence de requête
LINEUPS dans la politique v2 ; aucune conclusion fournisseur comparative n'en est déduite.

La campagne à seize a cessé de recevoir après 21:54:27.534 Paris (756 appels). Les erreurs Hikari
à 21:54:30 et le démarrage du conteneur à 21:55:26 concordent avec la mise à jour/redémarrage Docker
confirmé par l'opérateur. Le worker a disparu, mais l'état durable était encore RUNNING.
Au redémarrage opérateur vers 22 h 17, la campagne devient INTERRUPTED / OWNER_PROCESS_ABSENT ;
le garde CLEANUP_REQUIRED est horodaté 22:16:58.770678. Après preuve locale d'absence des processus, seule cette exclusion orpheline
a été libérée conditionnellement à 22:23:59.691819. Le nouveau lancement manuel à sept a réussi
à 22:25:12.506282, sous un autre identifiant ; l'histoire précédente est conservée.

Le correctif de clôture sépare l'arrêt d'exécution de l'état durable si PostgreSQL est indisponible,
et permet une nouvelle tentative locale de clôture sur commande explicite, sans reprise fournisseur.
Preuves, commandes, limites et bilan : [rapport prématch et temporalité](../../validation/WO058-PREMATCH-TEMPORAL-20260907.md).

### Sixième retour opérateur — huit rencontres puis seize, session unique confirmée

Après le correctif de capacité adaptative, le propriétaire rapporte environ trente minutes
sans problème sur huit rencontres puis un arrêt volontaire. Le relevé GET du 7 septembre à
19 h 41 Paris confirme `STOPPED_OPERATOR`, 181 appels, 4 665 254 octets et zéro cycle manqué.
Les trois statuts locaux anciens encore `inprogress` ont été finalisés après leur premier J4
`finished` ; Al-Khaleej — Al-Riyadh a ensuite terminé pendant le suivi. Les quatre dernières
collectes de finalisation J5 sont complètes. La cadence du manifeste est de 210 s pour huit cibles.

Une autre préparation à cinq rencontres différentes a été refusée au lancement sous
`LIVE_PROVIDER_BUSY` : comportement conforme à l'unique campagne fournisseur active globale
d'ADR-SS-005 v0.2, indépendant du plafond par campagne. Après arrêt de la première, la sélection
de vingt rencontres en exclut quatre déjà `finished` et retient seize cibles à 450 s. À 19 h 41,
la seconde campagne reste `RUNNING`, avec 83 appels, neuf rencontres en collecte, sept en attente
et zéro cycle manqué. Le propriétaire poursuit l'observation ; aucun arrêt ou redémarrage n'est
effectué pour ce relevé documentaire.

La capture Eclipse conserve l'empreinte historique 2/3 ; la référence adaptative à utiliser
pour une nouvelle préparation après l'observation est précisée dans le
[rapport opérateur à huit et seize rencontres](../../validation/WO058-OPERATOR-EIGHT-SIXTEEN-20260907.md).
Ce retour positif borné ne clôture pas le WO et ne vaut pas bilan final de la campagne à seize.

### Cinquième retour opérateur — plafond paramétrable et cadence adaptative

Après `a7f544cc2f70`, le paramètre réglé à 5 provoque `LIVE_POLICY_INVALID`, même avec une
seule rencontre. Le propriétaire précise que ce paramètre doit porter sur le maximum de
rencontres éligibles sélectionnables par campagne et confirme les valeurs 10 et 25.
Il choisit 60 s pour 1–3 cibles, 90 s pour 4 et 120 s pour 5 ; la progression est prolongée
par `D = max(60, 30 × (N − 1))` secondes selon N cibles retenues. Un plafond à 25 avec
trois cibles donne 60 s ; 25 cibles donnent 720 s.

Cette décision remplace les seuls paliers 1/2/3 et cadences fixes du cadrage historique
ci-dessous, et est enregistrée dans ADR-SS-005 v0.2. La préparation filtre `finished` avant
décompte ; le serveur et J4 appliquent le plafond. Les exceptions de sélection `STOPPED_ERROR`
et les réinterrogations J5 après HTTP 404 sont conservées. Le manifeste `live-v2` fige D ;
V34 conserve à 60 s les anciens `live-v1`, sans changer empreintes, provenance ou historiques.
Le plafond d'entrée de 100 identifiants, les quatre heures, 1 000/3 000 tentatives, délai de
trois secondes après échange et contexte unique restent applicables.

Validation du correctif : `clean verify -Pintegration-tests`, 1 424 tests Surefire (cinq ignorés
documentés) et 131 Failsafe, sans échec ni erreur ; 13 cas de charge au profil commun réexécutés
avec succès ; 12 tests Chromium dédiés réussis. Le passage natif de 25 cibles traite 100 réponses
de 5 Mio en 351,23 s, dans D = 720 s ; sa récurrence est vérifiée à horloge virtuelle.
Le PID 37724 a été arrêté après autorisation explicite, libérant le port 8087 requis par J6.
La base opérateur n'a pas été modifiée. Preuves et limites :
[rapport de capacité adaptative](../../validation/WO058-ADAPTIVE-CAPACITY-20260907.md).

### Retour fonctionnel du 7 septembre 2026, après commit `54d1597`

Le propriétaire confirme le comportement du bouton selon la sélection, signale un 403 au POST
de préparation depuis Chromium et précise : un événement dont le statut local initial est
`finished` ne doit pas démarrer de campagne live. Cette précision porte sur l'éligibilité locale,
avant le premier appel fournisseur. Elle complète AC01/AC04 ; elle ne modifie pas la finalisation
d'un match admis dont le premier J4 réseau découvre ensuite la fin.

Les rencontres terminées sont exclues avant admission et gel du manifeste. Une sélection
entièrement terminée affiche la liste et l'absence de campagne/appel. Pour une sélection mixte,
le récapitulatif indique les exclusions et ne retient que les autres rencontres. Le plafond
d'entrée de 100 identifiants protège la lecture locale ; la capacité qualifiée 1/2/3 porte seulement
sur les cibles retenues. Identités, doublons et provenance restent vérifiés côté serveur.
Au lancement d'un ancien manifeste, une rencontre devenue terminée est arrêtée sans appel
(`STOPPED_ALREADY_FINISHED`) ; si toutes sont terminées, aucun navigateur n'est ouvert.
Les contrôles locaux sont répétés après acquisition avant ouverture du navigateur.

La preuve du correctif figure dans le [rapport de retour fonctionnel](../../validation/WO058-FUNCTIONAL-FEEDBACK-20260907.md).
L'ADR accepté et les inventaires/preuves initiaux restent inchangés.

### Deuxième retour opérateur, après commit `017888b`

L'opérateur confirme les résultats conformes pour un puis trois événements déjà `finished`.
Il constate ensuite un refus de préparation pour une observation ancienne `notstarted`, ainsi
que pour une sélection mixte avec un événement resté `notstarted`. Aucune campagne n'a été
lancée. Un J4 manuel sur Olympique Lyonnais — Auxerre (`providerEventId=16310940`) publie
`finished` avec le snapshot 1246 et actualise la liste. L'essai d'un vrai match futur n'a pas
encore été effectué ; la boucle automatique J4/J5 n'est pas validée par ce retour.

Le propriétaire confirme que les paramètres Docker du live ne sont pas renseignés. Le chemin
manquant bloque la mesure du volume PostgreSQL sous `LIVE_STORAGE_PROBE_NOT_CONFIGURED`,
masquée jusque-là par le message générique. La correction précise cette cause et les autres
refus locaux connus, puis documente/teste les deux variables Eclipse explicites. La préparation
reste sans réseau et aucun statut sportif n'est inféré à partir de la date d'un match.
Après libération du port 8087 par le propriétaire, `mvnw.cmd clean verify` réussit le 7 septembre
à 12:44:01Z : 1 341 tests Surefire (0 échec, 0 erreur, 5 ignorés) et 122 tests Failsafe
(0 échec, 0 erreur, 0 ignoré). Le premier échec J6 de cette reprise, lié au port occupé, reste
consigné séparément. Le propriétaire demande un commit local comme dernière action avant remise
en main ; la configuration du lanceur et les prochains essais opérateur restent à effectuer.
Preuves et reprise : [rapport d'admission locale](../../validation/WO058-OPERATOR-RETEST-STORAGE-20260907.md).

### Troisième retour opérateur, après commit `4edc8bb`

Le propriétaire confirme le parcours d'US Boulogne Côte-d'Opale — Dijon : observation locale
ancienne `notstarted`, préparation, confirmation manuelle, J4 `finished`, trois familles J5,
mise à jour du score et des consultations, arrêt devenu indisponible après complétion.
La lecture locale de la campagne `a556dbef-4c56-48a3-8ed5-08735f024e5f` confirme `COMPLETED`,
quatre appels, 133 667 octets, score 1–1, `finalComplete=true`, zéro cycle manqué et snapshots
1270 à 1273. Aucun appel fournisseur supplémentaire n'est déclenché par cette vérification.

L'attendu suivant porte sur plusieurs rencontres éligibles. Le profil initial est encore limité
à un match : 4 × (10 s de requête + 1 s de traitement + 3 s de délai) = 56 s par minute.
Les paliers deux et trois nécessitent le profil de charge et sa preuve, en plus du plafond de
sélection. Les matchs `finished` restent exclus avant ce calcul. La reprise qualifie ces paliers
hors fournisseur et expose leurs paramètres Eclipse, sans augmenter le plafond accepté de trois.
La suite complète passe avec 1 354 tests Surefire (cinq ignorés) et 122 Failsafe ; les huit tests
Chromium dédiés passent après correction d'une fixture de qualification. Les essais multi-match
réels restent à lancer par l'opérateur avec le profil documenté et un nouveau manifeste.
Preuves : [rapport de capacité et de retour opérateur](../../validation/WO058-MULTIMATCH-CAPACITY-20260907.md).

### Quatrième retour opérateur, après commit `64b70bf`

Les captures confirment le refus de quatre rencontres éligibles malgré un match `finished`
supplémentaire, puis la finalisation des trois cibles d'une sélection mixte. Les actualisations
de la campagne, des détails J5 et du tableau J4 fonctionnent ; l'arrêt global est disponible
pendant le suivi puis désactivé à la fin. La campagne `eb18a1b6-35f6-4931-83c1-8699d4771ab4`
termine avec 12 appels et 411 982 octets.

La campagne `62d2249c-de36-4c1a-acd6-9ab20b107a67` suit ensuite deux matchs déjà commencés et
FC Voluntari — FC Argeș Pitești, attendu à 16 h 30 Europe/Paris. Le J4 constate son démarrage
à 14:32:06.962Z ; le premier J5 statistiques répond HTTP 404 à 14:32:10.080Z. La publication
transmettait `HTTP_404` comme erreur de schéma d'un snapshot indisponible, ce que le contrat du port de persistance
refuse. L'exception déclenche le `STOPPED_ERROR` global confirmé sur les trois rencontres.

Le propriétaire précise que les trois familles J5 404 doivent continuer au prochain cycle
planifié et demande de bloquer la sélection J4 des rencontres déjà dans une campagne en cours,
sauf `STOPPED_ERROR`. Le correctif respecte ces deux règles, sans changer l'ADR, les migrations,
le traitement des pannes internes ou l'exclusion fournisseur. Le port 8087 a été libéré par
le propriétaire pour les contrôles complets. La suite complète passe avec 1 376 tests Surefire
(cinq ignorés explicités dans le rapport) et 126 Failsafe, sans échec ni erreur. Les huit tests
Chromium passent après précision du contrôle natif de fermeture puis de la notification Java ;
le premier échec de ce contrôle reste conservé. Aucun appel SofaScore n'est exécuté pendant
ces qualifications. Diagnostic, résultats et limites :
[rapport 404 et sélection](../../validation/WO058-J5-404-AND-SELECTION-20260907.md).

Depuis le bloc **Résultats normalisés** de `/events`, sélectionner une ou plusieurs rencontres,
préparer puis lancer explicitement une campagne locale. Pour chaque rencontre, suivre J4 jusqu'à
son démarrage, collecter automatiquement les trois familles J5 toutes les minutes pendant le
match, vérifier sa fin avec J4 et observer les données qui évoluent sans recharger toute la page.

La capture fournie montre la journée du 7 septembre en `Europe/Paris`, dix événements, leur
identité canonique, `providerEventId`, compétition, horaire, statut et provenance. Elle sert de
référence d'interface ; ses valeurs ne constituent ni un manifeste exécutable ni une observation
réseau actuelle. Le statut d'une collecte terminée ne signifie pas qu'un match est `finished`.

La tâche liée [Poursuivre collectes](thread://01a076a2-5c0b-7342-a0f4-b9feb99032b2?hostId=local),
lue le 7 septembre, décrit la campagne comparative COV-002 de dix rencontres et des collectes Lab
encore manuelles. Ses échéances et quotas Highlightly/football-data.org sont un contexte de
comparaison ; ils ne sont pas des autorisations, quotas ou paramètres SofaScore. Le présent lot
ne modifie pas cette tâche ni sa planification et n'automatise aucun transfert vers le projet principal.

### Besoin acquis et arbitrages validés

| Élément | Nature |
|---|---|
| Sélection multiple depuis `/events`, lancement opérateur | Demande explicite |
| Premier appel J4, puis répétition à la minute tant que `notstarted` | Demande explicite |
| Arrêt de cette boucle au premier J4 `inprogress`, puis trois endpoints J5 chaque minute | Demande explicite |
| Retour à J4 pour détecter `finished`, notamment à partir d'`injuryTime` | Demande explicite ; règles retenues dans le plan ADR-SS-005 |
| Affichage dynamique des données et de leur fraîcheur | Demande explicite |
| Secours J4 cinq minutes, dernier cycle J5, pilote 1→2/3, quatre heures, 1 000/3 000 tentatives | Arbitrages propriétaires confirmés dans ADR-SS-005 v0.1 formellement accepté le 7 septembre |
| Schéma métier incompatible | Arrêt du seul match ; remplace la proposition initiale d'arrêt global |
| J5 404 | Indisponibilité conservée et nouvelle interrogation au prochain cycle normal, choix propriétaire validé |

## 2. Existant vérifié et conséquences

La racine initiale est sur l'ancienne branche `codex/j9-decision` au SHA `1a58a3b`.
WO-058 part du train RC01 plus récent, contrôlé par `git ls-remote`, et non de cette branche
historique. Les synthèses anciennes sont départagées par les décisions et le code du train.

| Point d'appui | État établi | Conséquence live |
|---|---|---|
| [J4 canonique](../../architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md), `EventExplorerController` et `events.html` | Recherche locale, identités et observations avec provenance | Étendre la sélection et la lecture, conserver les colonnes existantes |
| [J4 Playwright](../../architecture/J4-PLAYWRIGHT-EVENT-DETAILS.md), WO-014 | Phase 2 : un GET `EVENT_DETAILS` par préparation/confirmation, sans cache | Une boucle n'est pas autorisée par la confirmation unitaire actuelle |
| [WO-015 J5 Playwright](../completed/WO-SS-20260827-015-j5-playwright-event-data.md) | Une identité, trois GET ordonnés, sans cache, contexte neuf et lease commune | Prévoir une autorité de campagne live et un ordonnanceur dédiés |
| `ManualProviderRequestCoordinator`, runtime Playwright commun | Une campagne active, coordination thread-affine, délai global minimal de trois secondes | Pas de worker par match ; sérialisation globale et arrêt commun |
| [Contrat J5](../../architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md), incidents V15 | Vide, partiel, indisponible et incompatible distincts ; données révisables | Réutiliser les normaliseurs ; versionner seulement les extensions nécessaires |
| Migrations jusqu'à V32, occurrences de snapshots V21, observations J4/J5 | Bruts et normalisés append-only, déduplication existante | Ajouter l'historique des cycles et une projection par dernière occurrence |
| Ledger J8 V27 | Unités ponctuelles et unicité de requête dans une campagne | Ne pas assimiler tous les ticks d'un match à une campagne J8 existante |
| ADR-SS-003 v0.2 et WO-047 | Livraison J7 locale et gouvernance séparées de l'audit de permission | Ne pas réintroduire l'ancienne porte de livraison ; le live reste une autre décision |

## 3. Périmètre de la réalisation validée

### Inclus

- Sélection des événements locaux éligibles ; préparation sans réseau d'un manifeste immuable.
- Lancement explicite, arrêt d'un événement et arrêt global ; une seule campagne fournisseur active.
- Orchestration J4/J5 locale à durée, volume et cadence bornés, avec retour à J4 en fin de rencontre.
- Consultation dynamique des statuts, scores, statistiques, incidents, compositions et provenance.
- Historique durable des échéances, tentatives, résultats, révisions et motifs d'arrêt.
- Replay intégralement hors réseau, tests de concurrence PostgreSQL et qualification Playwright loopback.
- Runbook d'expérimentation et rapport permettant de comparer fraîcheur, complétude et coût réel.

### Exclus

- Collecte au démarrage de l'application, tâche quotidienne, redémarrage automatique ou service permanent.
- Nouveaux endpoints fournisseur, découverte J3 automatique, API alternative ou fallback de transport.
- Collecte J5 prématch dans ce premier parcours ; les compositions déjà présentes restent consultables.
- Livraison J7 automatique, génération de box score par inférence, modification de COV-002 ou du VPS.
- Changement des fixtures historiques, effacement d'observations, rétention/purge automatique.
- Mise en production, recommandations de paris ou dépendance du Betting Project à cette expérimentation.

## 4. Décision d'architecture préalable à l'activation

[ADR-SS-001 v1.4](../../../ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md),
§3.10, demande : « Toute mise en production, tout polling live, toute augmentation importante du
volume ou toute intégration directe dans le coeur du Betting Project exige un nouvel ADR. »
Ses §3.6.1 et §9 encadrent également le démarrage opérateur et le réexamen du navigateur automatisé.
La [gouvernance AGENTS.md](../../../AGENTS.md) interdit le démarrage automatique de Playwright
par scheduler ou polling. Ces textes n'empêchent pas de rédiger ce WO ; ils imposent de décider
l'exception bornée avant son implémentation activable et sa qualification fournisseur.

[ADR-SS-005 v0.1](../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) est formellement
accepté par le propriétaire. Sa table de supersession ciblée définit le cadre applicable aux
seuls cycles live J4/J5. La [copie exacte acceptée](../../validation/ADR-SS-005-v0.1-accepted-proposal-20260907.txt)
est conservée et son empreinte vérifiée dans le rapport. Les renvois dans ADR-SS-001 et AGENTS.md
sont ajoutés sans réécriture des décisions historiques ni modification d'ADR-SS-002 à 004.
Cette acceptation établit le cadre d'architecture. Le propriétaire a ensuite validé ce WO
le 7 septembre 2026 ; la revue de réalisation et la qualification fournisseur restent distinctes.

La décision acceptée est : un clic opérateur autorise **une session locale bornée**, son
manifeste exact et ses répétitions ; le navigateur est créé par ce lancement, jamais par un tick.
Un seul contexte non persistant neuf appartient à cette campagne ; sa perte ou sa fermeture termine
la campagne. Aucun tick ne recrée un contexte. Une nouvelle campagne exige un nouveau lancement humain.
Cette durée de contexte supérieure aux campagnes actuelles doit être qualifiée en mémoire et en arrêt.

Créer une politique et un opt-in live dédiés, inactifs par défaut. Les propriétés actuelles
`automatic-refresh-enabled`/`live-polling-enabled` sont refusées par la configuration et les
politiques manuelles : leur simple bascule ne constitue pas l'implémentation. Garder le catalogue
générique `callable=false`, le `ConnectorGate` générique bloquant et les parcours manuels bornés.
Réutiliser les ports de transport/normalisation ; ne pas simuler des clics de confirmation en boucle.

L'[ADR-SS-002 v1.1](../../../ADR-SS-002-bounded-multi-dossier-provider-robustness.md) porte une
exception historique de 38 tentatives sur une fenêtre de 60 minutes et un go déjà consommé.
Elle ne fournit ni budget live ni autorité réutilisable. Les anciens go J9 ne sont pas réarmés.
L'état documentaire `NOT_EVIDENCED` de permission fournisseur n'est pas transformé en permission
par une réussite HTTP ; la portée d'ADR-SS-005 conserve la séparation des décisions J7 locales
déjà prises et ne revendique aucune permission fournisseur nouvelle.

## 5. Parcours opérateur depuis les résultats normalisés

1. Ajouter une case par ligne et un compteur de sélection. Conserver horaire Paris, rencontre,
   compétition, statut source, UUID, ID fournisseur, snapshot et bouton **Ouvrir**.
2. Autoriser la préparation d'une sélection non vide sans réseau. La rafraîchir sans perdre les
   cases, même si les lignes changent de statut ou d'ordre ; aucune sélection cachée n'est ajoutée.
3. Vérifier côté serveur que chaque UUID existe et correspond à l'identité fournisseur attendue,
   avec origine fournisseur traçable. Refuser les doublons, identités ambiguës, événements purement
   synthétiques pour une campagne réelle et IDs ajoutés librement par le client.
4. Présenter **Préparer la campagne live** : rencontres exactes, statut connu et date de réception,
   intervalle J4/J5, règles de fin, heure limite Paris/UTC, plafond d'appels, capacité et exclusions.
   Tous les événements démarrent leur contrôle J4 au lancement ; aucune attente implicite jusqu'à T0.
5. Réévaluer l'admission avant **Lancer la campagne live**. Un manifeste expiré, modifié, une lease
   occupée ou un budget invalide refuse tout lancement sans appel. Une double soumission ne crée
   qu'une campagne. Le TTL de préparation retenu reste cinq minutes, distinct de sa durée d'exécution.
6. Ouvrir la vue de campagne avec ses rencontres et un accès au détail J5. Afficher les prochains
   appels, le budget consommé/restant, les retards et **Arrêter ce match / Arrêter la campagne**.
   L'arrêt individuel supprime immédiatement les tâches futures du match et laisse son éventuel
   GET engagé terminer sous timeout ; son résultat ne le réactive pas. Une interruption immédiate
   du transport nécessite l'arrêt global, car le worker et le contexte appartiennent à toute la campagne.
7. Fermer/recharger un onglet ne lance aucun appel fournisseur supplémentaire. La campagne reste
   active dans le processus local jusqu'à son terme, son arrêt ou sa limite ; ce comportement est
   annoncé au lancement. Une reconnexion d'écran reprend seulement la lecture de son identifiant.
8. Ne pas proposer de reprise automatique : après arrêt, crash ou redémarrage, consulter l'historique
   ou préparer une nouvelle campagne explicitement, après nettoyage et réévaluation de l'état.

## 6. Machine à états par événement

Les noms ci-dessous définissent les **états d'orchestration du lot**. Ils restent distincts du statut
sportif brut J4 et des états de collecte J5. Le fournisseur peut renvoyer d'autres situations que
`notstarted → inprogress → finished` ; la machine ne fabrique pas cette succession.

| État / observation | Action | Suite |
|---|---|---|
| `INITIAL_CHECK` | Un J4 `EVENT_DETAILS` dès la première place admissible après lancement | Classer la réponse reçue, pas l'ancien statut de la liste |
| J4 `notstarted` | `WAITING_START`, nouvelle échéance J4 à D ; aucun J5 | Répéter jusqu'à statut différent ou borne/arrêt |
| J4 `inprogress` en `INITIAL_CHECK` ou `WAITING_START` | Arrêter la boucle d'attente ; premier cycle J5 dès disponibilité du coordinateur | `COLLECTING`, puis J5 chaque D |
| Signal de première période/mi-prolongation, boucle de fin non armée | Un contrôle J4 ponctuel ; si `inprogress`, maintien en `COLLECTING` | Échéancier J5 inchangé ; pas de nouveau « premier cycle » |
| J4 `inprogress` ponctuel ou de secours en `COLLECTING` | Conserver l'état et les échéances J5 | Aucun redémarrage ni double cycle |
| J4 `finished`, même au premier contrôle | Annuler les échéances ordinaires, conserver l'observation de fin | `FINALIZING`, un dernier cycle J5 borné, puis `FINISHED_CONFIRMED` |
| Signal J5 classé comme fin potentielle selon §6 | Programmer une vérification J4 à la première échéance respectant D, sans doubler une échéance existante | `CHECKING_FINISH`, J4 chaque D, J5 continue ; le signal de mi-temps ne demande qu'un contrôle ponctuel |
| J4 toujours `inprogress` pendant vérification de fin | Ne pas conclure ; continuer J4 et J5 bornés | Attendre une preuve J4 `finished` |
| Statut reporté/annulé/interrompu/suspendu/inconnu, ou régression `inprogress → notstarted` | Conserver le statut brut et cesser les appels de ce match | `STOPPED_REVIEW_REQUIRED`, pas `finished` |
| J4 404 | Conserver `ENDPOINT_UNAVAILABLE`, aucune preuve de statut | Arrêt de ce match, poursuite possible des autres |
| Borne de durée/appels, arrêt opérateur | Annuler les tâches futures ; traiter l'appel déjà engagé selon la procédure d'arrêt | `STOPPED_LIMIT` ou `STOPPED_OPERATOR`, résultat sportif inchangé |
| JSON de schéma métier incompatible après contrôles de sécurité/corrélation satisfaits | Arrêt de ce seul match, annulation de ses familles restantes et cycles futurs ; autres matchs poursuivis | `STOPPED_SCHEMA_INCOMPATIBLE`, brut et résultats acquis conservés |
| Sécurité, transport, contenu inattendu, incohérence d'identité, exception interne du parseur, stockage/runtime ou anomalie non classifiable | Arrêt global et nettoyage | `STOPPED_ERROR`, aucune reprise automatique |

Un événement `FINISHED_CONFIRMED` peut avoir des données J5 finales partielles/indisponibles.
La fin sportive, le succès de la dernière collecte et la complétude sont trois informations séparées.
La campagne devient terminale lorsque tous ses événements le sont et que le nettoyage est vérifié.

Chaque résultat et transition porte une portée **événement** ou **campagne**. Un défaut de sécurité
ou d'identité prime sur une étiquette de schéma ; `UNEXPECTED_CONTENT` et une exception imprévue
ne sont pas des erreurs métier isolables. Ne pas confondre `status != PARSED` avec une portée
unique d'arrêt. Si la persistance de l'erreur métier échoue, arrêter globalement. Un match arrêté
pour schéma incompatible ne reçoit ni correction du parseur ni nouvelle tentative automatiques.
Cette politique live ne modifie pas les arrêts des campagnes manuelles historiques.

### Cadence et ordre des requêtes

- J4 réel réutilisé : `GET /api/v1/event/{EVENT_ID}` (`EVENT_DETAILS`, phase 2).
- Un cycle J5 complet conserve strictement l'ordre actuel :
  `GET /api/v1/event/{EVENT_ID}/statistics`, puis `/incidents`, puis `/lineups`.
- L'origine est celle déjà autorisée par les contrats Playwright : `https://www.sofascore.com`.
  Les URI viennent des requêtes typées existantes ; l'allowlist reste exacte. Les GET locaux de
  consultation ne peuvent pas demander une URI fournisseur.
- Chaque famille J5 a une échéance nominale à l'intervalle D du manifeste, avec des décalages internes dus aux
  appels séquentiels. Un cycle signifie trois requêtes par match, même si les compositions n'ont
  pas changé. Ne pas réduire discrètement leur fréquence au motif qu'elles changeraient moins.
- Utiliser une horloge monotone pour les délais et UTC pour les instants persistés ; afficher Paris.
  Cible `t0 + k × D`, délai minimal D entre deux départs de la même famille/événement et
  aucun chevauchement de cycles d'un événement. Les familles d'un cycle restent contiguës et ordonnées.
- Sérialiser tous les départs J3/J4/J5 dans le coordinateur commun, avec son fence conservateur et
  sa preuve temporelle minimale de trois secondes. Interdire les appels manuels concurrents pendant
  la campagne live ; autoriser les lectures locales.
- Un retard ne produit jamais de rattrapage en rafale. Coalescer les échéances devenues obsolètes,
  compter les cycles non exécutés et rendre le retard visible. La cible D n'est pas garantie
  en cas de réponse lente : qualifier la distribution des écarts réels, pas seulement le timer.
- Une requête J4 déjà prévue satisfait simultanément un signal d'incident et le contrôle périodique.
  Un signal lu pendant le cycle J5 est traité après ce cycle ; aucune quatrième famille J5 n'est ajoutée.

### Déclenchement J4 de fin : rôle exact d'`injuryTime`

1. Un nouvel `injuryTime` peut justifier une lecture J4, mais ses minutes ne sont ni un compte à
   rebours fiable ni une preuve du coup de sifflet. `addedTime=999` sur un marqueur de période est
   une valeur source spéciale, pas 999 minutes à attendre.
2. Un `injuryTime` de première période (`time=45`), de mi-prolongation (`time=105`) ou `HT`
   déclenche au plus un contrôle J4
   ponctuel ; si J4 reste `inprogress`, la collecte continue sans boucle de fin permanente.
3. Un `injuryTime` de fin potentielle (`time=90`, ou `120` en prolongation), un marqueur `FT`,
   une fin de prolongation/séance, ou un signal ambigu de fin arme `CHECKING_FINISH`. Un signal
   ambigu est présenté comme tel ; il permet de demander J4, jamais d'attribuer `finished`.
4. Une prolongation en cours, un temps additionnel à 105, `Extra time` avec `isLive=true`, ou un
   marqueur ayant une borne `time=120` ne prouve pas une fin. Si la boucle de fin est déjà armée,
   elle continue jusqu'à la preuve J4 ou une borne ; J5 continue également pendant les tirs au but.
5. Les incidents étant rediffusés et parfois corrigés/réordonnés, mémoriser le signal et sa
   provenance ; relire le même incident ne recrée pas de boucle ni d'appel supplémentaire.
6. Prévoir un contrôle J4 de secours à `max(300 s, D)` depuis le dernier J4 réussi en phase
   active. Il couvre l'absence d'`injuryTime`, un endpoint incidents indisponible et un événement
   arrêté autrement. Ce contrôle est remplacé par la cadence J4 D dès `CHECKING_FINISH`.
7. Lorsqu'une fin manque encore, la limite de durée arrête la collecte avec fin **non confirmée**.
   Ni T+90, ni T+120, ni le score, ni une liste d'incidents vide ne suffisent à conclure.

Le dernier cycle J5 est au plus unique, ordonné et compris dans le budget. Il commence après
réception du J4 `finished`, à sa première échéance respectant D depuis le dernier départ
de chaque famille et le délai global, pour obtenir une observation postérieure ; aucune attente de stabilisation
ou répétition post-match n'est ajoutée. S'il n'est pas exécutable ou échoue, le statut sportif reste
confirmé et la finalisation est explicitement incomplète.

## 7. Bornes historiques et contrôle de capacité

Ces bornes sont celles d'ADR-SS-005 v0.2, issue des décisions du propriétaire, conservées pour
les anciennes politiques. Le complément v5 à vingt rencontres conserve sa qualification
synthétique historique. Le §0 de l'ADR courant définit désormais v6 : au plus sept rencontres,
cible 100/300 s soumise au budget temporel global, limites 2 500/20 000 appels et plafond brut
indépendant ; son [profil temporel dédié](../../validation/WO058-LIVE-V6-CAPACITY-20260909.md) est désormais qualifié en boucle locale synthétique pour sept rencontres, dans la portée établie de 64 Kio. Les valeurs historiques ci-dessous ne
constituent pas un quota fournisseur connu ou une garantie de performance déjà mesurée.

| Paramètre | Valeur historique v0.2 |
|---|---|
| Campagnes actives | Une seule globalement, lease commune conservée |
| Nombre de matchs | Maximum éligible configuré par campagne ; 5, 10 et 25 admis avec profil de charge adapté ; limite technique d'entrée de 100 identifiants |
| J4 attente / J5 / J4 fin | D = max(60, 30 × (N − 1)) s par événement/famille, selon N cibles du manifeste |
| J4 de secours actif | max(300 s, D) ; remplacé par J4 fin, jamais cumulé |
| Durée | Au plus 4 h depuis lancement, heure UTC de fin exclusive commune au manifeste |
| Plafond | Au plus 1 000 tentatives par match et 3 000 par campagne ; la réservation réelle peut être plus basse |
| Réserve de clôture | Un J4 et trois familles J5 par match, inclus dans le plafond |
| Retard durable | Alerte dès échéance dépassée ; arrêt de capacité après deux cycles successifs non servis avant leur échéance suivante |
| Retry technique | Aucun ; un nouvel échantillon normal après succès/404 J5 n'est pas un retry immédiat |
| Timeout de requête | 10 s au plus, inchangé ; un timeout arrête globalement la campagne |
| Préparation | TTL de 5 min, puis nouvelle préparation sans réseau |
| Rafraîchissement de l'écran | Lecture locale nominale toutes les 5 s |

La réserve de clôture ne permet pas de franchir l'heure limite ni de transformer un J4 non final
en fin sportive. Elle évite d'épuiser tout le budget dans les cycles ordinaires ; si J4 reste actif
au dernier contrôle disponible, l'événement termine en `STOPPED_LIMIT`.

Pour `W` matchs en attente, `L` actifs dont `F` en contrôle de fin (`F <= L`), la demande nominale
hors secours est `Q = W + 3L + F` appels par intervalle D. Ajouter les contrôles de secours
réellement dus, les contrôles ponctuels et la réserve finale. Dix matchs à D = 270 s demandent
30 GET J5 par intervalle, jusqu'à 40 GET avec vérification J4 de fin ; 25 matchs à D = 720 s
demandent jusqu'à 100 GET. À 1 s de requête + 1 s de traitement + 3 s de délai, les charges
modélisées sont respectivement de 200 s et 500 s, dans leurs intervalles annoncés.

Le fence attend aussi la fin observable du dispatch précédent : le débit réel est inférieur à
20 appels/minute. Le contrôle d'admission doit simuler les échéances sur la fenêtre, les transitions
possibles, les durées de requête/traitement qualifiées, les délais et la réserve ; il ne se limite
pas à `N <= plafond`. Le plafond sélectionnable ne prouve pas la capacité temporelle. En cas
d'incompatibilité, refuser le manifeste entier avec un motif de capacité et une proposition de
sélection réduite ; ne pas écarter silencieusement des matchs ni ralentir les appels sans l'annoncer.

Le dimensionnement tient aussi compte des octets : chaque réponse garde la borne de 5 Mio,
mais 3 000 réponses maximales représentent environ 14,65 Gio de bruts avant index et projections.
Préflight espace disque, plafond de volume accepté et arrêt avant saturation sont donc requis.
La déduplication peut réduire le volume ; ce gain ne doit pas être présumé pour admettre la campagne.

## 8. Contrat de données, fraîcheur et replay

### Données à préserver

- Séparer UUID canonique, ID fournisseur et identité des participants ; aucun rapprochement par
  simple nom. Conserver l'ordre domicile/extérieur, compétition et heure source avec son offset.
- Conserver octets bruts, SHA-256, snapshot, occurrence de réception, observation normalisée,
  version du parseur, `requested_at`, `received_at` et preuve de départ réseau lorsque mesurée.
  Un timestamp pré-navigation ne devient pas une mesure on-wire.
- Pour chaque famille, afficher séparément dernier essai, dernier succès reçu, dernière nouvelle
  version de contenu, résultat de complétude, ancienneté et éventuellement absence de données.
- L'objet `EventIncident` actuel ne conserve pas tous les champs de phase (`isLive` et temps source
  détaillés) et son `sequence` reflète la position JSON, pas un identifiant durable d'incident.
  Si ces signaux commandent l'orchestration, ajouter une projection de phase versionnée, sourcée
  depuis le parseur, avec référence à l'occurrence et au fragment logique concerné. Aucun parsing
  ad hoc dans le scheduler et aucune heuristique par index seul.
- Le contrat J4 `event-details-v2` ne normalise actuellement pas le score. Pour le score dynamique
  du lot, l'extension versionnée de la projection J4 conserve les champs réellement
  présents : score courant, période et tirs au but séparés, absent/null distinct de zéro. Son
  actualisation suit les J4 reçus, pas chaque tick J5 ; afficher cette date. Le détail peut montrer
  les scores associés aux incidents avec leur minute/période/provenance, sans les promouvoir en
  score courant par lecture du dernier index. Qualifier VAR, prolongations et corrections par fixtures.
- Conserver les révisions VAR, buts annulés, incidents déplacés/supprimés et corrections statistiques
  sous forme de nouvelles observations ; ne pas assimiler tout changement à un incident nouveau.

### Matrice entrée → résultat → preuve attendue

| Entrée | Résultat exigé | Preuve hors réseau |
|---|---|---|
| J4 `notstarted`, `inprogress`, `finished` | Transitions par statut reçu et dernière occurrence | Série synthétique datée, appels et annulations attendus |
| Statut inconnu ou champ absent/incompatible dans un JSON admissible | Conservation du brut et arrêt individuel pour revue ou schéma selon la cause | Fixture et classification ; toute anomalie de sécurité garde priorité globale |
| J5 `incidents: []` valide | Vide valide selon contrat, aucune conclusion automatique sur le match | Fixture vide distincte de champ absent/null |
| J5 404 | `ENDPOINT_UNAVAILABLE`, backoff par famille en v6 ou cadence historique conservée | Occurrence 404, familles suivantes traitées, pas de retry immédiat |
| Statistique absente/null, score non renseigné | Manquant distinct d'un zéro fourni | Zéro réel et absence testés séparément |
| Score J4 / scores d'incidents, prolongation et tirs au but | Projection versionnée J4, temporalités et natures séparées | Pas de reconstruction par comptage des buts ni dernier index |
| Compositions partielles/non confirmées | Complétude actuelle préservée | Fixture partielle et rendu explicite |
| `injuryTime` 45/90/105/120, HT/FT/Extra time/isLive | Signal sourcé, vérification J4 selon §6, jamais fin inférée | Cas nominal, ambigu, sans signal, prolongation et tirs au but |
| Réponse identique A → A | Deux réceptions mesurées, même contenu possible | Deux occurrences/cycles, dernière réception plus récente |
| Retour exact A → B → A | La vue live revient à A avec sa nouvelle occurrence | Contre-épreuve du tri historique par seule observation |
| Incidents répétés/réordonnés/corrigés | Pas de double déclenchement, révision consultable | Replay de permutations et annulation de but |
| Statistiques à t1, incidents à t2, compositions à t3 | Triplet explicitement non atomique côté fournisseur | Affichage des trois réceptions et cycle associé |
| JSON de schéma métier incompatible, sécurité/corrélation satisfaites | Arrêt du seul match ; brut conservé, familles restantes annulées, autres matchs poursuivis | Deux matchs simulés, compteur consommé, aucun retry du match arrêté |
| Timeout v6 avec preuve terminale réutilisable et tolérances disponibles | Groupe abandonné, match différé 300 s puis nouveau J4 ; finalisation arrêtée sans retry | Preuve transport, compteur de tolérances, autres matchs poursuivis, fenêtre/arrêt respectés ; qualification fonctionnelle v0.9 réussie hors fournisseur |
| Autre erreur transport/timeout, HTML/challenge/401/403/429/5xx, identité, exception interne du parseur ou stockage | Arrêt global tracé, dernière donnée bonne toujours visible comme ancienne ; 403/429 jamais tolérés | Contre-épreuve à deux matchs, compteur de tentatives et nettoyage |

J4 phase 2 et J5 restent sans cache fournisseur. Le cache J3, l'import local multi-match et les
contrats historiques ne sont pas assouplis. Une réponse identique reçue est une collecte nouvelle,
pas un cache hit ; une lecture d'écran est une lecture locale, pas une collecte.

Le replay prend un manifeste de fixtures synthétiques ou expurgées, leurs hashes, l'ordre des
réponses, l'horloge contrôlée et les décisions opérateur. Il reproduit la machine, les budgets,
les signaux et le rendu sans Playwright ni réseau fournisseur. Il conserve la distinction entre
observation reçue historiquement et réinterprétation avec un parseur plus récent. Un parsing réussi
ne confère pas `HUMAN_VALIDATED` et n'autorise aucun export/livraison automatique.

## 9. Persistance PostgreSQL du lot

### État durable et contraintes

L'agrégat dédié V33 utilise `live_campaign`, `live_event`, `live_call`,
`live_call_dispatch`, `live_call_receipt`, `live_call_result`, `live_transition`
et `provider_campaign_guard`. Les curseurs sont reconstruits depuis les occurrences référencées.
Le profil d'admission (durées exactes et hash de qualification) appartient au manifeste immuable.

| Élément | Contenu minimal et contraintes |
|---|---|
| Campagne/manifeste | UUID, hash et version de politique, sélection gelée, preuve de lancement opérateur, fenêtre, bornes et résultat terminal |
| Événement sélectionné | FK identité canonique, ID fournisseur vérifié, provenance initiale, dernier J4 observé, état machine, échéance, motif d'arrêt ; unicité campagne/événement |
| Cycle et tentative | Numéro logique, famille, échéance, réservation budgétaire, départ éventuel, réception, résultat ; unicité campagne/événement/cycle/famille |
| Liens de résultat | Snapshot ET occurrence, observation J4/J5 ou résultat indisponible/incompatible ; références cohérentes avec identité et famille |
| Journal de transition | État avant/après, raison, horodatage, occurrence/signal déclencheur et action opérateur ; append-only |
| Projection courante | Dernière occurrence traitée par événement/famille, état et fraîcheur, reconstructibles depuis les résultats |
| Claim/lease | Propriété exclusive de campagne et jeton de génération ; sérialisation des lancements concurrents et refus des résultats d'un ancien propriétaire |

Réutiliser `provider_snapshot_occurrence` et les observations existantes. La déduplication par
contenu et les requêtes historiques `findLatest` ne suffisent pas : A→B→A peut retrouver l'ancienne
observation A et laisser B en tête du tri historique. La projection live suit la **dernière occurrence
traitée**, sans mettre à jour l'heure d'une observation passée ni inventer un nouveau hash.

Les index couvrent campagne/état/échéance, événement/famille/ordre de réception et les FK de preuves.
La rétention existante doit reconnaître ces liens : aucune suppression de métadonnées nécessaires
au replay. L'expiration ultérieure autorisée de bruts se signale comme telle ; WO-058 n'ajoute pas de purge.

### Transactions, concurrence et panne

1. Transaction courte pour valider la campagne et réserver atomiquement une tentative/un budget,
   puis commit **avant** tout départ réseau. La réservation ne démontre pas un départ effectif.
2. Appel Playwright hors transaction SQL, sur l'exécuteur sérialisé qui possède le coordinateur.
   Aucun verrou SQL n'est tenu pendant le GET ou la minute d'attente.
3. Persister d'abord le brut et son occurrence dans une transaction dédiée, avant parsing. Publier
   ensuite, par transaction courte, les observations normalisées ou le résultat indisponible,
   les liens, journal, compteurs et projection via les ports existants. Un échec de parsing ou
   de publication ne supprime pas la réception brute déjà acquise. La tentative rend visible cet
   état incomplet. Chaque famille réussie est conservée ; un cycle J5 partiel n'est pas annulé
   comme l'import hors ligne atomique `3N`. Le détail et l'observation canonique J4 restent atomiques.
4. Une panne entre dispatch et commit laisse une tentative `UNKNOWN/INTERRUPTED`, budget consommé
   de manière conservatrice. Aucun renvoi automatique : SQL ne garantit pas un GET externe exactement
   une fois. Une tentative non dispatchée prouvée reste distincte d'une issue réseau inconnue.
5. Double clic, deux onglets et deux processus ne doivent jamais doubler un départ. L'exclusivité
   durable complète la lease mémoire ; pas de récupération automatique de lease ouvrant du réseau.
6. Au redémarrage, identifier les seules campagnes orphelines du propriétaire précédent, après
   vérification d'exclusivité durable, et les marquer interrompues/revue requise sans instancier
   Playwright. Un second processus ne vole pas la lease et ne termine pas la campagne d'un autre
   processus encore actif. Conserver le budget et les preuves. Une reprise du poste après veille
   interrompt la session et ne rejoue pas les échéances passées ; aucun nouveau départ avant ce contrôle.
7. Sur arrêt, interdire tout nouveau dispatch avant de nettoyer ; une réponse déjà reçue peut encore
   être persistée avec son état d'arrêt, sans recréer de tâche ni réactiver un événement terminal.
   Préserver les bornes d'acquittement opérateur ≤500 ms, annulation ≤2 s et nettoyage ≤5 s.
   Si le nettoyage n'est pas confirmé, conserver l'exclusion fournisseur et exposer l'incident.

V33 est ajoutée après V32, dernière version effectivement présente à la réalisation ; aucune
migration V1..V32 partagée n'est modifiée. Une extension
du parseur/projection impose ses contraintes de version et fixtures de compatibilité. Aucun backfill
de campagne live à partir des anciennes collectes manuelles et aucune provenance reconstruite.

Prévoir une installation neuve et un upgrade depuis la dernière version précédant les migrations
du lot, préremplie (V32 à cette ouverture), dans PostgreSQL Testcontainers :
observations, occurrences, hashes, ledgers J8/J7 et décisions humaines conservés. Avant tout déploiement
sur la base utilisée par l'opérateur, préparer la sauvegarde/reprise adaptée au
[runbook J6](../../runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md) sous l'autorité opérationnelle du lot.
La rédaction et la qualification synthétique de WO-058 n'impliquent aucune connexion à cette base.

## 10. Interface dynamique locale

Spring MVC/Thymeleaf conserve une lecture locale incrémentale toutes les cinq secondes
sur `/events`, la campagne et les détails consultés. Les routes réalisées sont :

| Méthode et route | Fonction |
|---|---|
| POST `/live-campaigns/prepare` | Préparer la sélection et le manifeste, sans réseau |
| POST `/live-campaigns/{id}/launch` | Confirmer et lancer une seule fois |
| GET `/live-campaigns/{id}` et `/live-campaigns/{id}/state` | Consulter les preuves locales |
| POST `/live-campaigns/{id}/stop` | Arrêter la campagne |
| POST `/live-campaigns/{id}/events/{eventId}/stop` | Arrêter le seul événement |
| GET `/events/state` et `/events/{eventId}/state` | Actualiser tableau et détail localement |

Ces GET sont sans effet de bord et ne dépendent d'aucun port de transport fournisseur. La perte du
canal de lecture ne relance pas de collecte. Arrêter le timer de lecture des onglets masqués ; au
retour, relire l'état courant sans rejouer leurs anciennes échéances.

Afficher par match : statut sportif J4 et date de réception, phase d'orchestration, score connu,
dernier cycle J5 et résultat de chaque famille, prochaine échéance, retard, budget et raison d'arrêt.
Les détails montrent statistiques par période, incidents ordonnés selon leur sens, compositions,
complétude et liens de provenance existants. Les différences entre versions doivent être consultables.

Une réponse de lecture porte une révision croissante et une vue cohérente des pointeurs du cycle ;
le client ignore une réponse plus ancienne arrivée après une plus récente. Les données des trois
endpoints ne sont jamais présentées comme reçues au même instant. Sur échec, garder les dernières
données bonnes avec l'âge et l'erreur de collecte ; ne pas remplacer par des zéros ou un écran vide.
Au-delà de deux intervalles attendus sans nouveau succès, marquer la donnée en retard/périmée selon
la phase et la famille ; un match terminé conserve ses dates sans animation de collecte active.

La préparation, le lancement et l'arrêt utilisent les contrôles Web locaux existants contre les
origines étrangères et soumissions forgées. Ajouter leurs tests, y compris iframe, double clic et
rejeu du formulaire. Les cases, compteurs et arrêt restent accessibles au clavier ; les mises à jour
préservent le focus et ne déplacent pas la sélection de l'opérateur.

## 11. Qualification et critères observables

### Matrice invariant → scénario → preuve

| Critère | Scénario discriminant | Preuve attendue |
|---|---|---|
| AC01 — sélection exacte | 0/1/N événements, ID forgé, doublon, provenance fixture, manifeste modifié/expiré ; exclusion des statuts locaux `finished` avant admission | Tests contrôleur/service et POST Chromium réel, zéro dispatch refusé ; sélection entièrement terminée expliquée sans campagne |
| AC02 — lancement unique | Double clic, deux onglets/processus et lease J3 occupée | Une seule campagne admise et une seule tentative par clé |
| AC03 — attente du début | `notstarted` répété puis `inprogress`, retard du coup d'envoi | J4 à échéances D, aucun J5 avant preuve de début |
| AC04 — lancement en cours/fini | Statut local déjà `finished` à la préparation/lancement ; premier J4 réseau déjà `inprogress` ou `finished` pour une cible admise | Aucun appel pour les matchs déjà terminés localement ; chemin réseau direct et finalisation bornée conservés pour les autres |
| AC05 — cycle J5 | Plusieurs matchs et trois familles, dont lineups inchangées | Ordre et cadence mesurés par famille, aucun overlap |
| AC06 — fin sans inférence | injuryTime première/seconde période, HT, prolongation, tirs au but, correction VAR | J4 déclenché correctement, fin seulement sur J4 `finished` |
| AC07 — incident absent | Incidents vides/404 ou signal manquant | J4 secours, limite atteinte sans faux `finished` |
| AC08 — fraîcheur vraie | A→A et A→B→A pour J4 et chaque famille J5 | Occurrences conservées et projection/rendu de la dernière réception |
| AC09 — indisponibilité partielle | 404 J5 puis succès au cycle normal suivant | Partiel visible, pas de zéro ni retry accéléré |
| AC10 — portée des erreurs | Deux matchs : schéma métier incompatible admissible sur le premier ; puis contre-épreuves 403, timeout hors exception v0.9, identité, contenu inattendu, exception interne et stockage | Schéma : seul match arrêté, brut conservé et second poursuivi ; contre-épreuves : arrêt global, pas de fallback/retry/contexte recréé |
| AC10-v0.9 — timeout terminé et isolé | Fin/nettoyage prouvés, tolérances disponibles ; contre-épreuves de récidive, preuve absente, finalisation et fenêtre | Nouveau J4 du match au moins 300 s après nettoyage, autres matchs poursuivis, tentative conservée sans snapshot ; aucune reprise lorsque les conditions sont absentes. Qualification fonctionnelle du correctif réussie hors fournisseur |
| AC11 — cadence bornée | Plafond distinct des cibles retenues, 1/2/3/4/5/10/25 cibles, réponses lentes et signaux simultanés | D selon N, mesure monotone/on-wire, respect 3 s, retards/coalescence/arrêt de capacité ; refus si les enveloppes ne tiennent pas dans D |
| AC12 — arrêt | Stop pendant délai/GET/commit, fin naturelle, budget et volume atteints | Aucun départ post-arrêt, preuve de nettoyage et résultat final distinct |
| AC13 — panne/reprise | Crash avant GET, après GET avant commit, redémarrage/veille, second processus pendant campagne active | État orphelin interrompu, budget conservateur, zéro reprise réseau/vol de lease |
| AC14 — PostgreSQL | Neuf, upgrade depuis le précédent prérempli (V32 à l'ouverture), rollback de famille, collisions FK/identités | Tests réels PostgreSQL, aucune perte de provenance ni altération des ledgers |
| AC15 — vue dynamique | Deux réponses locales hors ordre, onglet masqué, perte du canal, navigation | Révision monotone, sélection/focus conservés, aucun GET fournisseur |
| AC16 — invariants par défaut | Démarrage normal et tests standards | Loopback effectif, fournisseur désactivé, aucun démarrage Playwright |
| AC17 — limites de preuve | Replay synthétique puis campagne opérateur bornée | Rapports séparés, pas de qualification fournisseur revendiquée depuis le replay |

Utiliser une horloge pilotable et de faux transports pour les scénarios longs ; aucune attente
réelle de quatre heures dans les tests standards. La qualification Playwright loopback distincte
mesure le fence, les sessions longues, la mémoire, les annulations et l'absence d'artefacts interdits.
Conserver le seul transport Playwright, les contextes non persistants et l'absence de cookies,
storageState, HAR, trace, vidéo, capture, téléchargement et payload dans les logs.

### Commandes et séparation des preuves

Le `pom.xml` et `scripts/Verify-Local.ps1` de la base sont lus. Le lanceur standard appelle
`mvnw.cmd -DskipITs clean verify` ; avec `-WithIntegrationTests`, il ajoute
`mvnw.cmd -Pintegration-tests verify`. Failsafe peut être hérité dans `clean verify` : relever ses
exécutions et les rapports réels, pas seulement le profil passé en argument.

Pour la réalisation future :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
pwsh -NoProfile -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
git diff --check
```

Capturer immédiatement chaque code natif ; arrêter les commandes dépendantes en cas d'échec.
Planifier ces portes sans répétition aveugle : le second cycle réexécute des tests standards et le
lanceur encapsule les deux. Documenter les exécutions exactes retenues et toute omission motivée.
Employer ensuite les scripts qualifiés `Invoke-J4PlaywrightLoopbackQualification.ps1` et
`Invoke-J5PlaywrightLoopbackQualification.ps1`, puis le complément réalisé
[`Invoke-LivePlaywrightLoopbackQualification.ps1`](../../../scripts/Invoke-LivePlaywrightLoopbackQualification.ps1). Leurs paramètres
doivent être relevés dans le worktree de réalisation ; aucun lancement fournisseur ne valide une suite standard.

Les preuves de **cette ouverture documentaire**, distinctes des AC futurs, sont consignées dans
[le rapport de cadrage WO-058](../../validation/WO058-LIVE-J4-J5-SCOPING-20260907.md).
Ni la présence d'un test ni un ancien total vert ne vaut nouvelle exécution.

## 12. Découpage de réalisation et acceptations restantes

| Étape | Livrable et porte de sortie |
|---|---|
| A — décision | ADR-SS-005 v0.1 accepté, renvois ciblés effectués et WO validé par le propriétaire le 7 septembre |
| B — contrats et stockage | Machine à états, projection des signaux, migrations append-only et replay ; qualification PostgreSQL neuf/upgrade |
| C — orchestration locale | Admission, budget, ordonnanceur, lease, arrêt et reprise fermée ; tests déterministes et loopback, fournisseur toujours désactivé par défaut |
| D — interface | Sélection, préparation, lancement/arrêt et vues dynamiques ; qualification fonctionnelle hors fournisseur |
| E — expérimentation | Readiness et manifeste propres au lot, lancement opérateur sur 1 match ; mesurer coût/fraîcheur puis décider des paliers 2/3 |
| F — revue et livraison | Rapport, anomalies/limites, revue humaine et tests, PR vers le train exact ; clôture seulement après fusion |

Les étapes peuvent former des commits séparés de WO-058. Si elles deviennent plusieurs lots,
réserver leurs identifiants à l'ouverture ; ne pas déclarer dès maintenant des WO enfants créés.

Les arbitrages fonctionnels historiques ne sont plus à redemander : plafond paramétrable et
cadence D, quatre heures, 1 000/3 000 tentatives, J4 sur signaux + secours max(300 s, D), dernier
cycle J5, isolation du schéma métier incompatible et nouvel échantillon J5 après 404 au cycle
normal. Les nouveaux arbitrages v5 sont également acquis et les remplacent dans leur portée :
100 secondes pour J4/incidents/statistiques, 300 secondes pour LINEUPS en jeu, vingt rencontres
au plus, pause d'une seconde entre groupes de la même session et 2 500/20 000 appels. Leur
qualification synthétique dédiée et les deux vérifications Maven finales sont réussies ;
l’observation fournisseur reste distincte. La lecture locale cinq secondes est conservée.

Restent les décisions distinctes suivantes :

1. Relire la réalisation et ses preuves hors fournisseur. L'ADR et le WO sont validés ;
   ces décisions ne sont pas à redemander. La revue de réalisation ne vaut pas clôture.
2. Après réalisation et qualification hors fournisseur, préparer un manifeste concret et lancer
   manuellement la campagne. Les sélections élargies restent soumises au profil qualifié et à l'admission.

### Complément demandé le 8 septembre — pagination et lisibilité après arrêt

Le propriétaire demande dix rencontres par page au-delà de dix rencontres. Le complément
borne les projections HTML et JSON utilisées par le lecteur de campagne, conserve l’ordre
du manifeste et les commandes portant sur toute la campagne. Les pages Rencontres et
Détail gardent leur contrat JSON et leurs liens ciblent la page du match.

Les captures de la campagne `60fd08dd-074e-4994-a0d5-51aff9eaa9db` montrent ensuite une
collecte arrêtée avec clôture en attente, un état durable encore RUNNING, une autonomie
positive et une notice qui se chevauche dans la liste. Le complément corrige ces deux
restitutions sans modifier la clôture ni relancer la collecte. La cause initiale de l’arrêt
reste distincte de ce correctif d’affichage.
Le [diagnostic en lecture seule](../../validation/WO058-LIVE-CLEANUP-INCIDENT-20260908.md)
consigne les preuves et l’attente initiale du parcours de récupération opérateur.

Réalisation et résultats propres à ce complément :
[pagination et affichage après arrêt](../../validation/WO058-LIVE-PAGINATION-20260908.md).
Les succès Maven v5 ci-dessus restent les preuves du contenu antérieur ; ils ne valident
pas automatiquement ce complément.

### Définition de fini

Le 9 septembre, le propriétaire étend les compositions avec trois attributs déjà reçus dans
`EVENT_LINEUPS` : indicateur de capitaine, statistiques individuelles accessibles depuis les
cartes et liste des joueurs indisponibles. Ce complément autorisé inclut leur normalisation et
conservation V3/V41, le rendu live/J5 et le suivi des différences J6. Le contrat J7 v1 reste sa
projection fermée existante. Aucun nouveau endpoint ni appel à l'ouverture d'une carte n'est ajouté.
Le [contrat détaillé](../../architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md) et le
[rapport propre au complément](../../validation/WO058-PLAYER-DETAILS-20260909.md) distinguent
la compatibilité historique, les validations synthétiques et le retour opérateur futur.

Après `51d78e0`, les captures opérateur montrent le badge capitaine, les statistiques
dépliées et la liste des indisponibles. Elles révèlent aussi cinq libellés encore en anglais :
« Cruciate Ligament Injury », « Toe Injury », « Muscle Injury », « Unknown » et le motif de
carton « Other reason ». La présentation commune live/J5 les traduit respectivement en
« Blessure au ligament croisé », « Blessure à un orteil », « Blessure musculaire »,
« Motif inconnu » et « Autre motif ». Les descriptions stockées sont conservées ; il n'y a ni
migration ni nouveau parsing. Voir le [suivi des libellés](../../validation/WO058-FRENCH-REASONS-20260909.md).
La vérification complète de cette retouche réussit le 09/09 à 09:04:31 heure de Paris :
1 944 cas Surefire (cinq ignorés) et 171 intégrations, sans échec ni erreur. L'arrêt autorisé
du Lab occupant 8087 et la passe précédente bloquée sont consignés dans ce suivi.

Après `2afa484`, le propriétaire précise les catégories de deux statistiques individuelles.
`savedShotsFromInsideTheBox`, visible sur la capture, et `savedFromInsideTheBox`, indiqué dans
le message, sont présentés sous « Gardien · Arrêts dans la surface ». `bigChanceMissed` devient
« Attaque · Grosses occasions manquées ». La correction concerne uniquement le dictionnaire
de présentation ; les clés et valeurs déjà conservées par V3 restent distinctes. Le même
retour ajoute « Back Injury » → « Blessure au dos », « Broken Ankle »/« Broken ankle » →
« Fracture de la cheville » et « Knee Injury » → « Blessure au genou ». Le
[suivi de la présentation](../../validation/WO058-PLAYER-DISPLAY-LABELS-20260909.md) porte sa qualification :
`clean verify` réussi le 9 septembre à 07:34:11 UTC, avec 1 944 cas Surefire (cinq ignorés)
et 171 intégrations, sans échec ni erreur.

Le 9 septembre, le propriétaire demande de supprimer les rôles répétés sur les cartes
joueurs et de traduire les groupes et noms de statistiques. Ce complément concerne
uniquement la présentation commune live/J5 : sections, valeurs, identifiants, ordre,
sources et états des panneaux restent conservés. La réalisation et ses résultats propres
sont consignés dans le [rapport des libellés](../../validation/WO058-UI-LABELS-20260909.md).
Après libération du port 8087 par le propriétaire, `clean verify` réussit à 22:55:21 UTC
le 8 septembre (00:55:21 Europe/Paris le 9) : 1 913 cas Surefire dont cinq ignorés,
166 cas Failsafe, zéro échec ou erreur. Les 214 cas ciblés et les deux scénarios Chromium
réussissent également. Les ignorés et les preuves de ce complément sont détaillés dans le rapport.
La retouche opérateur suivante remplace la formulation de la zone par « dans le tiers offensif »
dans les trois libellés des fautes subies, entrées et phases. Sa validation est consignée
séparément dans le même rapport ; elle ne modifie que ces chaînes d’affichage.
Ses 29 tests ciblés réussissent ; sa propre passe `clean verify` réussit à 23:14:49 UTC le 8 septembre
(01:14:49 Europe/Paris le 9), avec 1 913 cas Surefire dont cinq ignorés et 166 cas Failsafe,
sans échec ni erreur.

Après redémarrage, le propriétaire a clôturé la session interrompue puis lancé un
rattrapage final de quinze rencontres. La clôture `LOCAL_CLEANUP_VERIFIED` et la nouvelle
campagne `COMPLETED` sont confirmées en lecture seule. La trace opérateur identifie
séparément la cause du HTTP 500 de l’accueil : le refus de l’aperçu de rétention pendant
le verrouillage fournisseur échappait au repli du contrôleur. Le
[complément accueil](../../validation/WO058-RECOVERY-DASHBOARD-20260908.md) corrige
cette restitution sans modifier l’exclusion ou la purge.

Le propriétaire demande également les améliorations graphiques des incidents dans ce
lot. Leur périmètre est la présentation commune live/J5, les repères visuels et le
rafraîchissement de la liste ; les observations normalisées, parseurs, cadences et
contrats de collecte restent inchangés. Les propositions externes jointes aux axes de
travail ne constituent pas une autorisation de modifier le transport ou ses limites.
Le [rapport graphique](../../validation/WO058-INCIDENT-GRAPHICS-20260908.md) décrit
la présentation, la fidélité aux données et la qualification du complément.
La convention finale du 9 septembre distingue 🧤 Penalty arrêté et ❌ Penalty manqué.
Après cette dernière adaptation, `clean verify` réussit à 22:19:24 UTC le 8 septembre
(00:19:24 Europe/Paris le 9) : 1 912 cas Surefire dont cinq ignorés, 166 cas Failsafe,
zéro échec ou erreur. La qualification Chromium finale réussit quatre cas à 22:22:33 UTC.
Les ignorés, commandes et captures sont détaillés dans le rapport graphique ; ces
résultats valident ce complément sans remplacer les limites du diagnostic historique.

Le 8 septembre, après arrêt du Lab par le propriétaire, l’enquête sur l’arrêt initial
identifie une perte certaine des diagnostics de la boucle et de la clôture. Le complément
[diagnostic d’exécution](../../validation/WO058-LIVE-FAILURE-DIAGNOSTICS-20260908.md)
conserve ces deux observations séparément, sans changer le protocole de collecte ou de
clôture. La piste historique du contrôle de stockage reste une inférence documentée ;
elle ne vaut pas identification certaine de l’exception disparue.

### Complément du 9 septembre — V4/V46 et calendrier live-v7/V47

Le propriétaire autorise les cartes de composition enrichies, les personnes J4, les
incidents repliables et le calendrier live-v7. La réalisation ajoute les contrats V4,
les migrations V46/V47 et le profil `live-v7` sans réinterpréter une campagne historique.
Un J4 `delayed` avant le début poursuit désormais le groupe sur le nouvel horaire lisible ;
un horaire absent, ou un passage `inprogress` vers `delayed`, reste soumis à revue. La
traduction française comprend notamment `Strain Injury` → « Blessure à l’entraînement ».

La qualification est terminée hors fournisseur : 33 tests unitaires ciblés, la suite J6,
`clean verify`, `-Pintegration-tests verify`, sept cas Chromium/loopback et une exécution
soutenue de 35 minutes avec trois scénarios sont verts. Les commandes et les limites sont
consignées dans le [rapport V4/V46/v7](../../validation/WO058-PEOPLE-AND-LIVE-V7-20260909.md).
Les contrôles navigateur n'ont fait aucun appel réel, aucun POST et n'ont utilisé aucune
base opérateur. Le scénario soutenu ne prouve ni capacité fournisseur ni seuil de refus.

Le profil v7 admet au plus **trois rencontres** ; il reste conditionné à son SHA et à ses
huit enveloppes de qualification au prochain démarrage. Les sept rencontres concernent le
profil v6 historique et ne doivent pas être reportées sur v7. L'observation manuelle du
navigateur — environ 13 requêtes J5/minute pour une page — est documentée dans le
[relevé des débits historiques](../../validation/WO058-HISTORICAL-DEPARTURE-RATES-20260909.md)
comme indication de charge, pas comme seuil d'acceptation. Aucun lancement réel, changement
de VPN ni mécanisme de rotation d'adresse n'est réalisé par ce lot.

Le cadrage et l'autorisation de réalisation sont acquis. La réalisation, ses limites et la
qualification de B–D sont consignées dans le
[rapport d'exécution](../../validation/WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md),
avec l'[architecture](../../architecture/LIVE-J4-J5-CAMPAIGNS.md) et le
[runbook](../../runbooks/LIVE-J4-J5-CAMPAIGNS.md). L'ancien rapport de cadrage conserve séparément
la preuve Maven non verte liée au port 8087. Aucun résultat historique n'est transformé en succès.

Les étapes B–D historiques sont réalisées et qualifiées hors fournisseur ; le complément v5/V40
dispose de sa qualification synthétique dédiée et de ses deux vérifications Maven finales réussies. Une
campagne fournisseur exige en plus le manifeste concret et le lancement de E. La revue n'est ni une campagne réussie,
ni la preuve d'une donnée sportive exacte. La clôture et la livraison Git sont séparées : une PR
du WO cible exclusivement `feature/V0.1.0-RC01`, avec revue humaine et fusion avant classement terminé.

### Complément du 10 septembre — libellés français de pays et repli de drapeau local

Le complément corrige exclusivement la projection web des pays dans les personnes J4
(entraîneurs et arbitre) et dans les compositions J5 (joueurs et indisponibles). Il obtient un
libellé français depuis le code pays local lorsqu'il est connu. Les cas d'associations de football
britanniques sont volontairement bornés aux noms exacts `England`, `Scotland`, `Wales` et
`Northern Ireland`. Ces noms prévalent dans la projection lorsque le snapshot leur associe un code
contradictoire : les trois premiers choisissent leur SVG local dédié, tandis que l'Irlande du Nord
emploie `gb.svg`, déjà versionné. Les autres pays continuent à être résolus par leur propre code ISO.

`ProviderCountry`, les données normalisées, les snapshots, hashes, parseurs et écritures de
persistance ne changent pas. Les drapeaux restent des SVG embarqués et versionnés dans le Lab :
aucune image, API ou autre appel fournisseur n'est ajouté. Le nom français est conservé dans
l'arbre d'accessibilité et reste visible tant que le chargement réel du SVG local n'est pas
confirmé, après une erreur d'image, sans JavaScript et en mode de contraste forcé. La présentation
ne masque donc jamais une nationalité parce qu'une image n'a pas été chargée.

Le même complément traduit `Physical Discomfort` en « Inconfort physique », `Abdominal Injury`
en « Blessure abdominale » et `ACL Knee Injury` en « Ligament Croisé Antérieur du genou » parmi
les motifs d'indisponibilité J5, sans modifier les valeurs brutes de provenance.

Dans la projection des incidents de carte, il traduit aussi exactement `Professional foul last man`
en « Faute volontaire du dernier défenseur ». La raison brute est conservée ; une description
fournisseur non vide garde sa priorité, et les autres types d'incident ou variantes proches du
libellé source restent inchangés.

La régression finale des présentations incident/J5 couvre 135 cas sans échec ni erreur. Les
traductions d'indisponibilité, celle du motif de carton et la tentative complète `clean verify`,
limitée uniquement par les deux contrôles Windows fail-closed déjà documentés, sont détaillées
dans les rapports
[motif de carton](../../validation/WO058-PROFESSIONAL-FOUL-LAST-MAN-20260910.md) et
[blessure abdominale](../../validation/WO058-ABDOMINAL-INJURY-20260910.md) et
[ligament croisé antérieur](../../validation/WO058-ACL-KNEE-INJURY-20260911.md).

La syntaxe des deux rafraîchissements JavaScript, la régression Maven ciblée, la qualification
Chromium conjointe J4/compositions et la tentative `clean verify` sont enregistrées dans la
[note de validation pays](../../validation/WO058-COUNTRY-FRENCH-LABELS-AND-FLAG-FALLBACK-20260910.md).
La passe générale s'arrête actuellement sur deux contrôles Windows d'identité de processus qui
ferment en échec sûr ; ils sont distincts des régressions pays et de la qualification loopback.
Ces contrôles sont entièrement locaux et ne constituent ni collecte ni acceptation fournisseur.

Le même complément classe `clearanceOffLine` sous **Défense** avec le libellé
« Sauvetages sur la ligne », exclusivement dans `PlayerStatisticsPresentation`. La clé et sa
valeur source restent inchangées dans `PlayerMatchStatistics`. La régression ciblée de cette
projection, de la vue campagne et de la vue J5 compte 79 tests sans échec, erreur ni test
ignoré. Une nouvelle tentative `clean verify` exécute 2 213 tests puis retrouve les deux mêmes
échecs fail-closed Windows d'identité de processus (`UNVERIFIED` au lieu de `ABSENT`, et
`CIM_ERROR,TASKLIST_ERROR,CLASS_UNVERIFIABLE`). Ces échecs sont hors périmètre ; ils ne sont ni
assouplis ni masqués.

### Complément du 11 septembre — politique locale `live-v9`, faits J4 et cartes enrichies

Le propriétaire demande une nouvelle politique de préparation `live-v9` pour réduire les
appels J5 qui ne sont pas utiles pendant une rencontre. Cette politique conserve le plafond
de dix rencontres et l'enveloppe locale stricte de `live-v8` : quatre familles, quatre fences
de 500 ms, une réserve inter-groupe d'une seconde, 45 départs sur 60 secondes et 2 756 sur une
heure. Elle ne tire aucun quota ou accord du fournisseur de ces bornes, n'ajoute aucun endpoint,
proxy, cookie, réutilisation de contexte ou mécanisme de résolution de challenge. Son profil
est indépendant : une preuve V8 ne qualifie jamais automatiquement une préparation V9.

Le premier J4 persistant porte une projection typée de `finalResultOnly`, `detailId`,
`hasEventPlayerStatistics`,
`tournament.uniqueTournament.hasEventPlayerStatistics` et de la description de statut. Avant ce
premier J4, une sélection locale ne dispose d'aucun fait sûr permettant d'écarter une cible ;
`finalResultOnly=true` arrête donc cette cible dès cette première lecture, sans groupe J5. Les
statuts `notstarted`, `postponed` et `delayed` n'autorisent ni statistiques ni incidents ;
`inprogress`, `interrupted`, `canceled` et `finished` les autorisent. Les compositions sont
autorisées lorsque `hasEventPlayerStatistics` est vrai ou absent, et refusées lorsqu'il est
explicitement faux.

`detailId=1` conserve le chemin J5 normal. Lorsque `detailId` est réellement absent, seules
trois réponses consécutives dont le code exact est `HTTP_404` suspendent J5 statistiques pour la
rencontre. Une autre issue, y compris un succès, remet cette séquence à zéro. Après la
suspension, un seul essai final de statistiques est prévu au premier J4 terminal
`interrupted`, `canceled` ou `finished`; un doublon, un timeout ou une autre réponse terminale
ne crée pas de boucle de relance. Un `detailId=1` ultérieur retire la suppression. Les valeurs
nulles ou inconnues restent en arrêt de revue sûr et ne sont pas assimilées à l'absence.

Un J4 `inprogress` décrit comme `halftime` met la rencontre en silence local pendant quinze
minutes. À échéance, V9 exécute un J4 seul. Si la description reste autre que `2nd half`, il
continue les J4 seuls toutes les minutes; dès la confirmation `2nd half`, les groupes J4/J5
normaux reprennent. Un statut terminal lors d'une relecture de mi-temps garde sa priorité et
termine selon le groupe J5 final permis.

La capacité tournoi J4 est séparée de la présence ponctuelle de mesures dans un retour J5
lineups. Seule une valeur explicitement vraie rend une carte joueur ouvrable; les autres cas
restent statiques et affichent que les statistiques des joueurs ne sont pas disponibles. Dès que
J5 incidents et lineups sont lisibles, la présentation joint les faits par le couple strict
`(équipe, providerPlayerId)`, sans créer de joueur ni remplacer une statistique lineups réelle.
Les métriques directes `goals` et `goalAssist` de J5 lineups restent prioritaires lorsqu'elles
sont présentes, y compris à zéro. Les cartons et les entrées/sorties de joueur avec leur minute
observée restent des faits `EVENT_INCIDENTS` lorsque J5 lineups ne les porte pas, même si une
note ou des minutes de jeu sont déjà présentes. Cette décoration est en lecture seule, sans
écriture dans les observations normalisées.

La migration append-only V52 fait accepter le manifeste et le profil V9 tout en gardant le
ledger de départ sous `admission_profile='live-v8'`, qui désigne l'enveloppe de pression partagée
et non la version de planification. V48 et V50 ne sont pas réécrites. Les scripts de
sauvegarde/restauration J6 exigent désormais Flyway V52. Aucune campagne en cours n'est
réarmée, relancée ou modifiée par ce travail.

L'observation opérateur `PROVIDER_CLOCK_REGRESSION` du 11 septembre reste un refus de
planification locale avant départ worker : l'écran ne montre ni tentative, ni transport, ni
statut HTTP. La garde conserve donc l'arrêt en lecture seule et exige de corriger l'horloge
locale avant toute action opérateur explicite; elle n'est ni contournée ni transformée en
réarmement automatique.

#### Validation locale V9 — 11 septembre, hors fournisseur

Cette qualification couvre la planification locale `live-v9`, la projection durable des
faits J4, la migration V52 et la restitution des cartes joueur. Elle n'autorise ni
acceptation fournisseur, ni contournement de refus ou challenge, ni lancement,
réarmement, relance ou modification d'une campagne existante. Les scénarios utilisent
des fixtures, mocks, replays et, lorsque nécessaire, PostgreSQL local dans Docker ;
aucun appel réel au fournisseur n'est effectué par les tests.

| Commande | Résultat |
|---|---|
| `.\mvnw.cmd -q "-Dtest=LivePreparationAdmissionTest,GroupedLiveAdmissionPolicyV8EvidenceTest,GroupedLiveScheduleV8Test,GroupedLiveScheduleV9Test,LiveCampaignPropertiesTest" test` | **101 tests**, zéro échec, zéro erreur et zéro ignoré. Cette commande confirme l'admission V9 indépendante, la compatibilité du profil de pression V8, les séquences V8/V9 et la configuration. |
| `.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Dtest=LiveCampaignPersistenceIT#v9SqlReusesTheV8PressureBoundsAndRequiresItsOwnImmutableGroupedProfile" test` | **1 test d'intégration PostgreSQL**, zéro échec ni erreur ; Flyway applique V52 et vérifie que V9 exige son profil immuable tout en réutilisant l'enveloppe de pression V8. |
| `.\mvnw.cmd clean verify` | **BUILD SUCCESS** ; Surefire : **2 253 tests**, zéro échec, zéro erreur, cinq ignorés ; Failsafe : **232 tests**, zéro échec, zéro erreur, zéro ignoré ; durée 10 min 11 s. |
| `.\mvnw.cmd -Pintegration-tests verify` | **BUILD SUCCESS** ; Surefire : **2 253 tests**, zéro échec, zéro erreur, cinq ignorés ; Failsafe : **232 tests**, zéro échec, zéro erreur, zéro ignoré ; durée 9 min 24 s. |

Les tests V9 couvrent explicitement l'arrêt après le premier J4 `finalResultOnly`, l'absence de
statistiques et d'incidents avant le jeu, la désactivation explicite des compositions, les trois
`HTTP_404` consécutifs suivis d'un unique essai final, la réinitialisation sur une autre réponse
et le silence de quinze minutes à la mi-temps puis les relectures J4 seules. Les cartes sont
rendues ouvrables uniquement avec la capacité tournoi J4 explicitement vraie. Les incidents et
les compositions lisibles sont associés par `(équipe, providerPlayerId)` ; les buts et passes
directement présents dans J5 lineups ont priorité, alors que les cartons et changements complets
restent visibles depuis `EVENT_INCIDENTS`, avec minute et sans modifier les données normalisées.

#### Finition V9 et complétion des cartes — 11 septembre

Le profil de préparation V9 est maintenant versionné dans
`docs/validation/WO058-GROUPED-LIVE-V9-PROFILE-20260911.json`. Il porte le SHA-256 immuable
`f994415c00c9d97cb797a9f0b52c773efea044d827abc25282ae7f3ab17365da`, son statut local
`QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE`, une capacité de dix rencontres et les neuf valeurs
à reporter dans le lanceur. La qualification démontre les séquences de planification V9 par
replay local et par tests de production de l'ordonnanceur ; elle ne revendique aucune acceptation,
quota ou autorisation du fournisseur.

Le lanceur Eclipse local a été sauvegardé avant de recevoir exactement ces neuf variables V9.
Les neuf entrées V8 ont été relues après écriture et sont restées inchangées. Le script
`scripts/Show-LiveGroupedV9LauncherConfiguration.ps1` reste en lecture seule : il vérifie le
profil versionné et affiche les neuf entrées, sans modifier de configuration, sans démarrer
l'application ni lancer une campagne.

La restitution complète désormais les cartes dont J5 lineups apporte une note ou des minutes mais
aucun fait de carton ou de remplacement. Les buts et passes J5 lineups restent la source prioritaire
lorsque leurs métriques sont présentes, y compris à zéro. Les cartons jaune, rouge ou double jaune,
ainsi que les entrées et sorties, proviennent des incidents lisibles, complets et non annulés,
associés par `(équipe, providerPlayerId)`. La vue de campagne et la consultation manuelle J5
appliquent la même règle, sans double comptage ni écriture dans les données normalisées.

Pour `live-v9` seulement, la présentation rend aussi l'état terminal
`FINISHED_J5_INCOMPLETE` lorsque J4 confirme le résultat final mais que le dernier cycle J5
facultatif est incomplet. Cet état reste terminal et ne réarme aucune collecte ; les politiques
V4 à V8 conservent leur libellé historique `FINISHED_CONFIRMED`.

| Commande | Résultat |
|---|---|
| `Invoke-Pester -Path .\scripts\Tests\WO058LiveV9LauncherConfiguration.Tests.ps1` | **2 tests**, zéro échec ; le script est confirmé sans réseau, processus, écriture ou lancement. |
| `.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Dtest=GroupedLiveAdmissionPolicyV8EvidenceTest,GroupedLiveAdmissionPolicyV9EvidenceTest,LiveCampaignPropertiesTest,GroupedLiveScheduleV9Test,LineupIncidentOverlayTest,LiveCampaignPresentationTest,LiveCampaignControllerTest,J5EventDataControllerTest" test` | Régressions ciblées vertes : profil V9, état terminal, overlays incidents, vue de campagne et vue J5 manuelle. |
| `.\mvnw.cmd clean verify` | **BUILD SUCCESS** ; Surefire : **2 257 tests**, zéro échec, zéro erreur, cinq ignorés ; Failsafe : **232 tests**, zéro échec, zéro erreur, zéro ignoré ; durée 10 min 14 s. |

Cette finition n'a démarré ni navigateur, ni application, ni campagne, et les validations ne
contiennent aucun appel réel au fournisseur.

#### Validation fonctionnelle opérateur — 11 septembre

Après ajout délibéré du profil V9 dans son lanceur local, l'opérateur a lancé la campagne locale
`5ab7dd5f-2f03-4181-9281-fe4dfb351e40` et a confirmé que la restitution graphique est conforme.
Les cartes affichent notamment les sorties observées et un carton jaune issus de `EVENT_INCIDENTS`,
tandis que le panneau individuel d'un joueur entrant conserve ses minutes, sa note et ses mesures
J5 lineups. Cette observation couvre le cas réel où les compositions comportent déjà des mesures
joueur mais omettent les faits de carton ou de remplacement ; la formulation visuelle des
remplacements est précisée dans la finition ci-dessous.

Les captures ont été fournies dans le suivi opérateur et ne sont pas copiées dans le dépôt. Ce
retour valide l'affichage local; il ne constitue ni une preuve d'acceptation fournisseur, ni une
modification d'opt-in, de quota ou de politique de transport.

#### Retour opérateur et finition visuelle des compositions — 11 septembre

Une campagne distincte observée par l'opérateur s'est arrêtée après une réponse fournisseur
HTTP 403. Le diagnostic local affichait 190 départs worker observés et les quatre familles J4/J5 ;
ce compteur local ne permet pas d'attribuer le refus à un seuil fournisseur. Le traitement reste
un arrêt sûr et persistant, sans nouvelle tentative, réarmement automatique, proxy, changement
d'adresse, réutilisation de contexte ou résolution de challenge. Cette observation ne déclenche
aucune action de campagne par le présent lot.

La projection des **compositions**, commune à la campagne et à la consultation manuelle J5,
allège les décorations provenant d'incidents déjà lisibles. Un carton reste une image seule sur la
carte du joueur : le libellé, la minute et la provenance ne sont plus affichés visuellement. Un
remplacement montre la personne opposée et la minute : sur le joueur entrant, une flèche montante
verte accompagne le nom du sortant ; sur le joueur sortant, une flèche descendante rouge accompagne
le nom de l'entrant. Une croix médicale supplémentaire n'apparaît que lorsqu'une blessure est
explicitement fournie par l'incident. Le libellé visuel « Incidents J5 » est retiré de ces
décorations de carte. Les noms, minutes et le fait de blessure restent des faits incidents J5
observés ; aucune observation normalisée n'est modifiée. Le tableau des incidents conserve sa
présentation détaillée existante, y compris ses libellés, équipes et joueurs.

Validation locale sans campagne ni navigateur :

| Commande | Résultat |
| --- | --- |
| `mvnw.cmd --offline -Dmaven.repo.local=C:\\Users\\geoff\\.m2\\repository -q -Dtest=J5EventDataControllerTest,LineupIncidentOverlayTest test` | Réussite : rendu MVC et overlay des incidents couverts. |
| `node --check src/main/resources/static/js/lineups.js` | Réussite : syntaxe du rafraîchissement dynamique valide. |
| `mvnw.cmd clean verify` | Réussite : Surefire `2257/0/0/5`, Failsafe `232/0/0/0`, en 9 min 58 s. |

#### Extension V9 — suspension, cartes et révalidation conditionnelle

Le complément demandé le 11 septembre conserve une rencontre dont le statut J4 devient
`suspended`. La rencontre reste suivie par un J4 seul toutes les minutes ; les familles J5 sont
retirées tant que ce statut persiste, puis elles sont réintroduites seulement lorsqu'un J4 de
référence redevient `inprogress`. Le fait J4 `event.statusReason` est conservé typé et affiché
uniquement pendant cette suspension. Une valeur absente ou nulle n'autorise pas un libellé
interprété ; une raison de suspension est effacée dès que le statut de référence n'est plus
`suspended`.

La vue campagne et la consultation J5 manuelle appliquent la même présentation de composition.
Les sections Gardien, Défenseur, Milieu et Attaquant portent la répartition visible ; les mêmes
libellés ne sont donc plus répétés visuellement dans chaque carte, mais restent disponibles pour
les technologies d'assistance. Lorsqu'un SVG local de drapeau est chargé correctement, seul le
drapeau est visible et le nom français du pays demeure accessible. En cas d'absence de drapeau,
d'erreur de chargement, de JavaScript désactivé ou de contraste forcé, ce nom redevient le repli
visible. Cette règle ne crée aucun chargement d'image, endpoint ou donnée fournisseur.

La révision borne aussi une révalidation HTTP conditionnelle à `live-v9`, aux seules quatre
familles de la campagne (J4 détails, J5 incidents, statistiques et compositions). Un
`If-None-Match` n'est possible qu'après une réponse du même couple rencontre/famille, reçue,
normalisée et publiée avec succès dans la même campagne. Son validateur reste volatil dans le
contexte Playwright neuf : ni observation, snapshot, journal, parcours manuel, campagne suivante
ni redémarrage ne le récupère. Il n'est pas une permission de réutiliser un cookie, une session ou
un état de navigateur.

Un `304 Not Modified` avec ce contexte exact laisse la dernière réponse `200` acceptée comme
source de la consultation. Il ne produit ni corps brut, snapshot, normalisation, nouvelle
occurrence ni octet métier reçu, et n'altère pas l'heure de réception de cette donnée. En revanche,
le `304` répond à un GET réellement envoyé par le Lab : son départ physique et sa tentative sont
conservés append-only pour l'audit et la protection de cadence, avec le diagnostic
`COMPLETE/304`, mais ne figurent ni dans les compteurs fonctionnels, ni dans le budget, ni dans
la pression affichée de la campagne. La ligne `live_call_result`
`NOT_MODIFIED/NONE/HTTP_304` déjà append-only établit une libération idempotente sans migration
historique. Un `304` non corrélé à un contexte mémoire accepté est refusé sans parsing d'un corps
vide, retry, boucle, réarmement ou reprise automatique. Cette mesure ne revendique aucune
acceptation fournisseur, ni réduction garantie des HTTP 403.

Lorsque toutes les preuves persistées sont présentes, la vue affiche séparément la dernière
revalidation du cache à `headersReceivedAt`. Elle peut maintenir la fraîcheur de contrôle de la
famille sans toucher à la dernière réception `200`, à son âge ni à l'occurrence fournisseur.

Le lissage reste celui déjà qualifié : slots par famille déterministes, fences de 500 ms et réserve
statique de 1 s entre groupes. La vague de dix rencontres occupe déjà exactement la fenêtre de
60 s ; aucun jitter aléatoire ou adaptatif n'est ajouté. Une validation dédiée doit vérifier la
réponse `304` valide et non corrélée, l'absence de nouveau contenu/normalisation, la conservation
du seul audit de départ et son exclusion des compteurs fonctionnels, le confinement du validateur,
la suspension J4/J5 et les deux rendus de composition avant que cette extension soit déclarée
qualifiée. La preuve V9 déjà versionnée reste historique et n'est ni modifiée ni réinterprétée par
cette section.

#### Qualification finale de la révision V9 — 11 septembre

La révision finale confirme que le `304` conditionnel strict est une revalidation locale du
cache, et non une collecte fonctionnelle. La donnée consultée reste la dernière réponse `200`
publiée ; le budget, les compteurs et la pression n'incluent pas le `304` prouvé. Seule la trace
technique append-only de départ et de diagnostic subsiste pour l'audit et la cadence. La vue
distingue explicitement cette revalidation de la dernière réception et du dernier succès `200`,
y compris lorsqu'une section de famille est créée dynamiquement dans le navigateur.

| Commande | Résultat |
| --- | --- |
| `.\mvnw.cmd clean verify` | **BUILD SUCCESS** ; Surefire : **2 274 tests**, zéro échec, zéro erreur, cinq ignorés ; Failsafe : **235 tests**, zéro échec, zéro erreur, zéro ignoré ; durée 10 min 04 s. |
| `.\mvnw.cmd -Pintegration-tests verify` | **BUILD SUCCESS** ; Surefire : **2 274 tests**, zéro échec, zéro erreur, cinq ignorés ; Failsafe : **235 tests**, zéro échec, zéro erreur, zéro ignoré ; durée 9 min 47 s. |
| `.\mvnw.cmd --offline "-Pprovider-playwright-runtime,provider-playwright-local-qualification" -DskipTests package` | **BUILD SUCCESS** ; compilation du runtime Playwright local et du worker, sans test ni accès fournisseur. |
| Qualification Failsafe `LiveCampaignBrowserQualificationIT` avec `PLAYWRIGHT_BROWSERS_PATH` local et `-Dit.test=LiveCampaignBrowserQualificationIT` | **3 tests**, zéro échec, zéro erreur, zéro ignoré ; Chromium headless dialogue uniquement avec le serveur `127.0.0.1` de test et vérifie le champ `Dernière revalidation du cache (304)` créé dynamiquement. |
| `node --check src/main/resources/static/js/live-campaign.js` et `node --check src/main/resources/static/js/lineups.js` | Réussite : syntaxes JavaScript valides. |
| `Invoke-Pester -Script .\scripts\Tests\WO058LiveV9LauncherConfiguration.Tests.ps1 -PassThru` | **2 tests**, zéro échec ; le script reste en lecture seule, sans réseau, processus, écriture ni lancement. |
| Vérification ciblée PostgreSQL de `LiveCampaignPersistenceIT` pour les trois scénarios `304` V9 | **3 tests**, zéro échec ni erreur ; libération logique, pression fail-closed et conservation de la réservation physique finale vérifiées. |

Les tests standards et d'intégration utilisent leurs doubles, fixtures et PostgreSQL local ; la
qualification Chromium route toutes ses requêtes vers loopback. Aucun endpoint SofaScore, cookie,
session, proxy, défi ou navigateur persistant n'a été utilisé par cette qualification.

#### Correction de présentation `statusReason` — 11 septembre

La qualification Chromium loopback couvre la correction de présentation de `statusReason` :
hors `suspended`, la ligne porte `hidden` et n'a pas de boîte de mise en page ; avec un statut
`suspended`, la raison J4 observable redevient visible. Aucune route extérieure n'est autorisée par
ce test.

## Révision du 12 septembre — politique `live-v10` à huit rencontres

### Objectif et périmètre

La décision propriétaire du 12 septembre borne les **nouvelles préparations** `live-v10` à
**huit rencontres sélectionnables**. Les quatre familles planifiées par rencontre — J4 détails,
J5 incidents, J5 statistiques et J5 compositions — portent une charge nominale maximale de
**32 départs comptabilisés par minute** (`8 × 4`). Le profil durable doit refuser tout départ qui
ferait dépasser **35 départs comptabilisés dans une fenêtre glissante de 60 secondes** ou
**2 100 dans une fenêtre glissante d'une heure**.

Cette réduction remplace uniquement la politique des nouvelles préparations. Les manifestes,
preuves, statistiques de pression, résultats `304`, diagnostics et campagnes `live-v9` déjà
persistés restent historiques et inchangés. V10 reprend les règles J4/J5 et de révalidation
conditionnelle V9, sans changer leur sens : un `304` corrélé ne devient pas une donnée fraîche et
ne justifie ni relance, ni réarmement. La révision ne crée aucun endpoint, proxy, rotation ou
changement d'adresse, furtivité, cookie/session persistante, défi, retry ou reprise automatique.

| Élément | Borne V10 attendue |
| --- | --- |
| Politique des nouvelles préparations | `live-v10`, distincte de V8 et V9 |
| Sélection maximale | 8 rencontres |
| Familles nominales | 4 par rencontre et par minute |
| Charge nominale à capacité maximale | 32 départs comptabilisés / 60 s |
| Garde durable de 60 s | 35 départs comptabilisés au plus dans toute fenêtre glissante |
| Garde durable horaire | 2 100 départs comptabilisés au plus dans toute fenêtre glissante |
| Prévol de lancement | marge locale de `4 × N` avant la vague initiale, donc 32 au maximum |
| Admission sans profil V10 complet | capacité zéro, sans repli V8/V9 |

Les valeurs ci-dessus sont des paramètres de sécurité et de diagnostic du Local Lab. Elles ne
constituent pas une mesure de débit du fournisseur, une garantie d'acceptation, un quota public,
ou une promesse d'absence de refus HTTP. La campagne reste locale, manuelle, opt-in, attachée à
un contexte Playwright neuf et exécutable seulement après action explicite de l'opérateur.

### Réalisation et qualification locale effectuées

La réalisation isole V10 de V9 : elle introduit un profil de départ durable, l'admission et le
replay V10, un profil de configuration à empreinte propre et la migration Flyway append-only V54.
Les contraintes SQL et l'admission Java convergent sur le manifeste V10, la sélection de huit, les
fenêtres 35/60 s et 2 100/h, la première vague `4 × N`, le budget et la lecture de pression.
Aucune migration antérieure ni ligne V9 existante n'est réécrite.

La preuve versionnée
`docs/validation/WO058-GROUPED-LIVE-V10-PROFILE-20260912.json` est qualifiée pour le replay local
à huit rencontres, 32 départs nominaux/minute, 35/60 s et 2 100/h. Son lecteur
`scripts/Show-LiveGroupedV10LauncherConfiguration.ps1` reste en lecture seule : il affiche les
**dix** variables V10 de revue du lanceur Eclipse, dont
`SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY=8`, sans écrire de configuration, démarrer
l'application ou Playwright, ni lancer une campagne.

#### Validation V10 déjà exécutée

| Validation ciblée | Résultat vérifié |
| --- | --- |
| Preuve Java V10 avec la preuve historique V9 | **4 tests Maven**, zéro échec et zéro erreur. |
| Lecteur PowerShell V10 | **Pester 3/3** vert ; il affiche dix entrées Eclipse, dont `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY=8`, sans écriture, mutation d'environnement, processus ou trafic réseau. |
| Migration V54 | **FlywayMigrationIT : 74 tests** verts. |
| Persistance des campagnes V10 | **LiveCampaignPersistenceIT : 65 tests** verts. |
| Persistance de la protection de départ V10 | **ProviderResiliencePersistenceIT : 30 tests** verts. |
| Régression V10 ciblée | **294 tests** verts. |
| `mvnw.cmd clean verify` | **BUILD SUCCESS** en 10 min 26 s ; Surefire : **2 304 tests**, zéro échec, zéro erreur, cinq ignorés ; Failsafe : **241 tests**, zéro échec, zéro erreur, zéro ignoré. |
| `mvnw.cmd -Pintegration-tests verify` | **BUILD SUCCESS** en 10 min 11 s ; Surefire : **2 304 tests**, zéro échec, zéro erreur, cinq ignorés ; Failsafe : **241 tests**, zéro échec, zéro erreur, zéro ignoré. |

Ces résultats sont des qualifications locales ciblées et ne constituent ni une campagne
fournisseur, ni une acceptation ou un quota du fournisseur.

### État et prochaine action

Cette révision reste `IN_PROGRESS`. Le Work Order demeure dans `docs/work_orders/active` : la
validation opérateur, la revue humaine, la PR et la fusion sont des étapes distinctes et restent
à effectuer. Aucune clôture n'est déduite de cette qualification locale avant validation opérateur
explicite.
