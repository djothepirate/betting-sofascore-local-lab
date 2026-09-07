# ADR-SS-005 — Campagnes live locales et bornées J4/J5

- **Version :** 0.1.
- **Statut :** `ACCEPTED` — arbitrages fonctionnels et v0.1 formellement acceptés.
- **Date :** 2026-09-07.
- **Décideur :** propriétaire du Betting Project.
- **Acceptation formelle de cette version :** `OWNER_ACCEPTED_2026_09_07` — « Je valide formellement la v0.1 de l'ADR ».
- **Observation de l'acceptation :** `2026-09-07T09:23:14Z` ; l'heure exacte du message n'est pas disponible.
- **Document accepté figé :** [copie exacte de la proposition v0.1](docs/validation/ADR-SS-005-v0.1-accepted-proposal-20260907.txt), SHA-256 `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e` ; draft non committé.
- **Autorité documentaire :** acceptation propriétaire et demande explicite d'enregistrement et de mise au propre du WO-058.
- **Work Order :** [WO-SS-20260907-058](docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md), cadrage aligné, validation du WO en attente.
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
- **Base :** `6dfd14286d4f269cbe100bd965257c20298538db`, train `feature/V0.1.0-RC01` vérifié à l'ouverture.
- **Effet de cette rédaction :** documentation seulement ; aucune activation, implémentation applicative ou campagne fournisseur.

Les prescriptions ci-dessous définissent la décision **acceptée**. L'acceptation établit le cadre
d'architecture ; la validation du WO et la qualification de la réalisation restent nécessaires
avant le manifeste concret et le lancement opérateur d'une campagne. Cette mise au propre
enregistre l'acceptation sans modifier les décisions normatives de la v0.1.

## 1. Contexte et problème

Le propriétaire veut sélectionner des rencontres dans les **Résultats normalisés** de `/events`,
lancer une campagne locale et consulter l'évolution des données. J4 doit constater le début et
la fin du match ; J5 doit collecter statistiques, incidents et compositions à la minute pendant
le jeu. La liste initiale conserve l'horaire `Europe/Paris`, la compétition, les identités locale
et fournisseur, le statut reçu et sa provenance.

Les parcours actuels autorisent un GET J4 phase 2 par confirmation et trois GET J5 par campagne,
avec un contexte Playwright neuf, sans cache fournisseur et une coordination exclusive commune
J3/J4/J5. Ils n'autorisent pas la répétition automatique de leurs claims. Le fence commun attend
au moins trois secondes après la fin observable de l'échange précédent ; dix matchs actifs
demanderaient déjà trente GET J5 par minute, au-delà de cette capacité avant même J4 et les latences.

[ADR-SS-001 v1.4](ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md), §3.10 et §9,
exige un nouvel ADR pour ce passage au live. Celui-ci définit une expérimentation bornée sur le
poste Windows local. Il ne transforme pas le Lab en service permanent.

## 2. Décision acceptée et portée par rapport aux ADR existants

Autoriser une **campagne locale lancée manuellement une seule fois**, qui exécute ensuite les
cycles J4/J5 de son manifeste immuable, pendant sa fenêtre et dans ses budgets. L'opérateur
choisit les événements et confirme explicitement le plan ; la consultation ou un timer ne peut
ni lancer une nouvelle campagne ni réutiliser une autorisation terminée.

| Référence | Portée de la décision acceptée |
|---|---|
| ADR-SS-001 §3.4, §3.5, §3.6.1, §3.6.1.1 et critère live du §8 | Exception limitée au parcours live WO-058 : une confirmation de campagne remplace les confirmations unitaires répétées, dont l'unique GET J4 phase 2 ; J4 et J5 sont répétés sans cache dans les bornes ci-dessous. Les parcours manuels existants restent inchangés. |
| ADR-SS-001 §3.6.1, arrêt opérateur | L'arrêt de campagne ferme le contexte partagé ; l'arrêt individuel supprime les tâches futures du match et laisse terminer sous timeout le GET engagé. Les garanties d'annulation/nettoyage du contexte concernent l'arrêt global. |
| ADR-SS-001 §3.10 et §9 | Exigence d'un nouvel ADR satisfaite pour cette portée seulement ; les futures extensions restent soumises au réexamen. |
| [ADR-SS-002](ADR-SS-002-bounded-multi-dossier-provider-robustness.md) | Aucune supersession : campagnes historiques, plafond 38/60 min et go consommés ne fournissent aucune autorité ni budget live. |
| [ADR-SS-003 v0.2](ADR-SS-003-optional-integration-topology.md) | Revue d'impact sans changement de topologie : aucune livraison J7 automatique ni dépendance du projet principal. L'audit `NOT_EVIDENCED` ne redevient pas une porte bloquante à lui seul pour le transfert J7 local V2. |
| [ADR-SS-004](ADR-SS-004-integration-continue-et-distribution-locale-uniquement.md) | CI sans fournisseur, distribution locale et réseau désactivé par défaut inchangés. |

L'acceptation de cette v0.1 est enregistrée avec les seuls renvois nécessaires à l'exception
live dans ADR-SS-001 et [AGENTS.md](AGENTS.md). Les décisions historiques sont conservées ;
ADR-SS-002 à 004 restent inchangés.

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`
restent obligatoires. Java 25 LTS, Spring Boot 4.1.0, Maven wrapper, PostgreSQL local Docker Desktop,
Eclipse et UTF-8 restent le socle. L'application écoute seulement `127.0.0.1:8087`.

## 3. Arbitrages propriétaires validés et bornes

Les choix ci-dessous proviennent des réponses du propriétaire pendant le cadrage, puis du plan
dont la réalisation documentaire a été explicitement demandée. Ce sont des limites locales
acceptées dans cette v0.1, pas des quotas SofaScore connus ni une qualification de débit.

| Paramètre | Décision retenue |
|---|---|
| Capacité | Pilote sur 1 match ; paliers 2 puis 3 seulement après qualification et contrôle d'admission. Une seule campagne fournisseur active globalement. |
| J4 avant début | Premier contrôle au lancement, puis cible de 60 s tant que `notstarted`. |
| J5 pendant le jeu | Cible de 60 s, trois familles conservées à chaque cycle normal dans l'ordre statistiques → incidents → compositions. |
| J4 pendant le jeu | Sur signaux pertinents, secours à 300 s depuis le dernier J4 réussi, puis 60 s en surveillance de fin. |
| Fenêtre | Au plus 4 h depuis le lancement, attente prématch comprise ; fin UTC exclusive, affichée aussi en heure de Paris. |
| Budget | Au plus 1 000 tentatives par match et 3 000 par campagne, J4, J5, appels échoués et clôture compris. |
| Erreur métier | Schéma incompatible isolable : arrêt du seul match ; autres événements maintenus. |
| J5 HTTP 404 | Indisponibilité de la famille, poursuite du cycle et nouvelle interrogation au cycle normal suivant ; aucun retry immédiat. |
| Finalisation | Un dernier cycle J5 après J4 `finished`, dans les délais, le budget et la fenêtre. |

Paramètres complémentaires conservés du WO : préparation valable cinq minutes ; timeout de
requête au plus dix secondes ; réponse bornée à 5 Mio ; réserve d'un J4 et trois familles J5 par
match **comprise** dans le budget ; lecture locale d'écran toutes les cinq secondes. Une réserve
ne permet jamais de dépasser l'heure limite ou de déclarer un match fini sans preuve.

Le contrôle d'admission évalue les échéances, les transitions possibles, la réserve, les durées
qualifiées et l'espace disque. Une sélection incompatible est refusée intégralement avant
lancement ; aucune rencontre n'est retirée silencieusement. Trois matchs sont un plafond, pas
une garantie de cadence. Une borne plus basse inscrite au manifeste ne peut pas être augmentée
pendant la session. Trois mille corps maximaux représentent environ 14,65 Gio avant projections
et index : le contrôle de volume ne suppose aucun gain de déduplication et arrête avant saturation.

## 4. Exécution de la session et cadence

La sélection serveur vérifie UUID, ID fournisseur, provenance et absence de doublon. Elle produit
un manifeste gelé, limité aux identités existantes et aux quatre endpoints déjà connus. Aucun
identifiant libre ou URL fournie par le navigateur utilisateur n'est admis.

Le clic de lancement crée un seul worker et un contexte Playwright neuf non persistant, conservé
en mémoire pour cette campagne. Aucun cycle ne recrée le navigateur. La fermeture du worker ou
de son contexte, une panne, une veille ou un redémarrage de l'application terminent la session.
Fermer un onglet de consultation ne ferme pas le processus de campagne ; la reconnexion reprend
seulement sa lecture. Une campagne terminée n'est jamais reprise automatiquement.

Un opt-in live et une politique dédiés restent inactifs par défaut. Ne pas basculer simplement
les propriétés de rafraîchissement automatique que les politiques manuelles actuelles refusent.
Le catalogue générique reste `callable=false`, le `ConnectorGate` générique bloquant, le profil
`sofascore-live-test` bloqué, et les claims manuels J3/J4/J5 restent unitaires.

La campagne utilise le transport Playwright existant, l'origine exacte autorisée
`https://www.sofascore.com` et uniquement :

| Famille | GET |
|---|---|
| `EVENT_DETAILS` | `/api/v1/event/{EVENT_ID}` |
| `EVENT_STATISTICS` | `/api/v1/event/{EVENT_ID}/statistics` |
| `EVENT_INCIDENTS` | `/api/v1/event/{EVENT_ID}/incidents` |
| `EVENT_LINEUPS` | `/api/v1/event/{EVENT_ID}/lineups` |

Tous les départs restent séquentiels, sous coordination commune avec le fence d'au moins trois
secondes après l'échange précédent. Aucun appel manuel fournisseur ne s'intercale pendant la
campagne. Les lectures locales restent disponibles. Un cycle J5 conserve l'ordre de ses familles
et ne chevauche pas le cycle suivant du même match.

Les échéances nominales suivent `t0 + k × 60 s` et une horloge monotone, avec au moins 60 s entre
deux départs de la même famille pour un événement. Les horaires d'audit restent UTC. Un retard
ne produit pas de rafale : coalescer les échéances dépassées, les compter et afficher le retard.
Deux cycles successifs non servis avant leur échéance suivante arrêtent globalement la campagne
pour capacité insuffisante. Une seule requête J4 satisfait un signal et un secours simultanément.

## 5. Début, surveillance de fin et finalisation

1. Exécuter J4 dès la première place admissible après lancement. `notstarted` entretient la boucle
   J4 à la minute, sans J5 ; l'heure prévue du coup d'envoi n'est pas une preuve de début.
2. Dès J4 `inprogress`, arrêter cette boucle d'attente et démarrer J5. Les contrôles J4 ultérieurs
   ne redémarrent pas un « premier cycle » J5. Un match déjà commencé suit ce chemin immédiatement.
3. Un nouvel `injuryTime` à 45, un `HT` ou un signal de mi-prolongation à 105 déclenche un J4
   ponctuel. Si J4 reste `inprogress`, conserver les échéances J5 sans armer une boucle de fin.
4. Un signal de fin potentielle à 90/120, `FT`, fin de prolongation/séance ou signal ambigu de fin
   arme J4 à la minute. J5 continue pendant mi-temps, prolongations et tirs au but tant que le
   match reste suivi. Ni `FT` historique, ni `Extra time` avec `isLive=true`, ni `time=120` comme
   borne de période ne suffit à conclure. Une boucle de fin déjà armée reste active jusqu'à une
   preuve J4 ou une borne ; les signaux répétés/corrigés ne créent pas de boucles supplémentaires.
5. Sans signal, J4 de secours reste programmé toutes les cinq minutes depuis le dernier succès.
   Il est remplacé par la cadence de fin, jamais cumulé. Un endpoint incidents vide/404 n'implique
   aucun statut sportif ; `addedTime=999` n'est pas une durée d'attente de 999 minutes.
6. Seul J4 `finished` confirme la fin, même s'il est reçu au premier contrôle. Annuler les tâches
   ordinaires et effectuer au plus un cycle J5 final à sa première échéance admissible, en
   conservant les 60 s par famille et le délai global. Aucune répétition de stabilisation n'est ajoutée.
7. Si cette collecte est impossible ou partielle, conserver la fin sportive et indiquer la
   finalisation incomplète. À expiration sans J4 final, arrêter avec fin non confirmée.

## 6. Erreurs, indisponibilités et portée de l'arrêt

La portée de l'erreur est explicite dans les résultats et transitions : **événement** ou
**campagne**. Un statut sportif, un résultat de collecte et une complétude restent distincts.
L'ancienne règle du draft WO-058 qui arrêtait tous les matchs pour un schéma incompatible est
remplacée, pour ce seul parcours live, par la classification ci-dessous.

| Situation | Effet obligatoire |
|---|---|
| JSON reçu, contrôles de sécurité et de corrélation applicables satisfaits, schéma métier incompatible explicitement identifié | Arrêter seulement ce match ; conserver le brut et les familles déjà reçues. Ne plus exécuter ses familles restantes ni ses cycles futurs ; poursuivre les autres matchs dans le même contexte valide. |
| J4 404, statut non géré ou régression sportive incohérente | Arrêter ce match avec motif de revue, sans inventer `finished`. |
| J5 404 | Conserver `ENDPOINT_UNAVAILABLE`, traiter les familles suivantes ; au prochain cycle normal, réinterroger la famille indisponible si le match reste actif. |
| Transport, timeout, HTTP non-2xx hors 404, HTML/challenge, redirection/route inattendue, corps trop grand, incohérence d'identité, contenu sensible, exception interne du parseur, stockage/runtime défaillant | Arrêter globalement, nettoyer et interdire toute reprise automatique. |
| Anomalie non classifiable avec certitude | Arrêt global ; aucun classement métier permissif par défaut. |
| Arrêt individuel demandé | Supprimer immédiatement les tâches futures ; laisser son GET engagé terminer sous timeout, conserver son résultat sans réactiver le match. |
| Arrêt global demandé | Interdire tout nouveau départ et annuler le transport partagé. |

Les défauts de sécurité, de corrélation et d'identité priment sur une étiquette de schéma. Un
`UNEXPECTED_CONTENT`, un JSON dont la sécurité n'est pas établie ou une exception imprévue ne
devient pas une erreur métier isolable. Ne pas utiliser uniquement `status != PARSED` pour
déterminer la portée. Si le stockage du résultat métier échoue, cet échec reste global.

Un arrêt métier ne corrige pas automatiquement le parseur et ne réessaie pas le match. La future
reprise exige une nouvelle campagne manuelle après traitement de la cause. Le 404 J5 constitue
l'unique cas d'indisponibilité explicitement rééchantillonné à cadence normale ; il n'autorise
aucun retry technique accéléré. Un `Retry-After` ne réarme pas la campagne.

L'arrêt de tous les événements termine la campagne et ferme ses ressources. Préserver les
bornes existantes d'acquittement opérateur ≤500 ms, annulation ≤2 s et nettoyage ≤5 s. Un
nettoyage non confirmé conserve l'exclusion fournisseur ; l'arrêt individuel ne promet pas
l'annulation isolée d'un GET dans le contexte partagé.

## 7. Provenance, persistance et consultation

J4 phase 2 et J5 restent sans cache fournisseur ; la politique cache J3 n'est pas modifiée.
Les bruts restent locaux et séparés des normalisés. Conserver chaque occurrence avec son
snapshot, son hash, le parseur, l'observation et les heures de demande/réception. L'heure
pré-navigation n'est pas présentée comme une mesure de départ réseau.

Le ledger live distingue échéance, réservation, tentative, réception, résultat et transition.
Réserver le budget avant départ ; conserver le brut avant parsing ; publier atomiquement le
résultat normalisé et sa projection via les ports existants. La panne après départ avant résultat
laisse une issue inconnue et un budget consommé conservateur, sans renvoi automatique. Les
nouveaux éléments durables sont append-only pour les preuves et transitions, avec projection
courante reconstructible ; migrations après la dernière version partagée, sans faux backfill.

Les cas A→A et A→B→A réutilisent éventuellement des observations dédupliquées, mais chaque nouvelle
réception reste visible. La vue live suit la dernière occurrence traitée, pas seulement le tri
historique par date d'observation. Aucune date ancienne n'est réécrite pour simuler de la fraîcheur.

Les signaux de phase viennent d'une projection versionnée du normaliseur, avec provenance.
L'index JSON d'un incident n'est pas une identité durable. Le score courant exige l'extension
versionnée J4 prévue dans le WO ; il porte la date du J4 et n'est pas reconstitué en comptant les
buts ou depuis le dernier index des incidents. Absent, null, zéro, vide valide, partiel et
indisponible restent distincts ; VAR, prolongations et tirs au but restent représentables.

Le rafraîchissement local toutes les cinq secondes ne déclenche aucun GET fournisseur. Afficher
par famille dernière tentative, dernière réception réussie, dernière nouvelle version,
complétude, ancienneté et motif d'arrêt. Les familles peuvent provenir de réceptions différentes.
Préserver la sélection/focus, ignorer une réponse locale de révision plus ancienne et signaler
les anciennes données après erreur. Le replay synthétique reste intégralement hors réseau.

## 8. Alternatives écartées et conséquences

| Alternative | Motif du choix retenu |
|---|---|
| Dix matchs à la minute dès la première version | Incompatible avec le débit séquentiel existant ; les paliers permettent une mesure réelle. |
| Cadence plus lente calculée pour dix matchs | Non retenue par le propriétaire pour cette première version ; toute extension se revoit. |
| J4 systématique chaque minute pendant tout le jeu | Plus d'appels et score potentiellement plus frais ; le propriétaire retient signaux + secours cinq minutes. |
| Session huit heures ou 600/1 800 tentatives | Options présentées mais non retenues ; fenêtre quatre heures et plafonds 1 000/3 000 retenus. |
| Arrêt global pour toute incompatibilité métier | Non retenu ; isolation du match si l'intégrité de la session est établie. |
| Suspension définitive d'une famille J5 au premier 404 | Non retenue ; nouvel échantillon au prochain cycle normal, dans les bornes. |
| Arrêt immédiat de J5 après `finished` | Non retenu ; un dernier cycle borné collecte des données postérieures à la confirmation. |
| Recréation de contexte par timer, fallback, polling permanent ou reprise automatique | Hors modèle de session choisi et hors périmètre de cette décision. |

Conséquences : fraîcheur observable et historique de révisions ; volume de stockage accru ;
qualification longue durée du contexte et des arrêts ; score J4 potentiellement plus ancien que
les incidents J5 ; sélection multiple limitée par capacité ; pertes de fenêtres possibles sans
rattrapage. Une fin sportive confirmée ne garantit pas la complétude de la dernière collecte.

Playwright reste le seul transport ; aucun proxy, rotation d'adresse, furtivité, résolution de
challenge ou fallback FlareSolverr/HTTP. Aucun cookie, profil, storageState, HAR, trace, vidéo,
capture ou téléchargement conservé/journalisé ; aucun brut dans les logs ou vers le VPS.
Les contrôles existants d'effacement des cookies et d'isolation entre commandes sont conservés.

## 9. Critères et passage à la réalisation

Les critères ci-dessous sont **à qualifier**, pas des résultats acquis par la rédaction :

- Deux matchs simulés, JSON de schéma incompatible sur le premier : arrêt de ce seul match,
  autres familles du match annulées, brut conservé et second match poursuivi.
- Même scénario avec 403, timeout, incohérence d'identité, contenu inattendu, exception interne
  ou panne de stockage : arrêt global, sans nouveau départ ni contexte recréé.
- 404 J5 suivi d'un succès au cycle normal suivant : aucune tentative immédiate ; J4 404 arrête
  seulement le match. Statut sportif et complétude restent distincts.
- Début retardé, `inprogress`/`finished` initiaux, HT, prolongations, tirs au but et absence de
  signal : aucun début/fin inféré depuis l'heure théorique ou le seul incident.
- Dernier cycle J5 après `finished` respectant les 60 s, les budgets et l'heure limite ;
  finalisation partielle explicitée si une famille manque.
- Surcharge refusée, deux cycles manqués, budget/réserve exacts, volume maximal, arrêt individuel
  en vol et arrêt global ; délais et nettoyage mesurés en qualification loopback séparée.
- Redémarrage, veille, résultat tardif et second processus : pas de reprise, vol de lease ou
  double départ ; occurrence et état terminal préservés.
- PostgreSQL neuf et upgrade depuis le précédent prérempli, rollback, concurrence réelle,
  A→A/A→B→A ; aucune perte de provenance ni altération des ledgers J8/J7.
- Vue locale dynamique et replay sans transport fournisseur ; aucun lancement Playwright dans
  le démarrage normal ou les tests standards.

La [preuve documentaire WO-058](docs/validation/WO058-LIVE-J4-J5-SCOPING-20260907.md) distingue ces
critères futurs de la revue exécutée. Le `clean verify` de référence reste non vert : le test J6
exige un port 8087 libre alors que le Lab l'utilisait. Aucun succès Maven n'est revendiqué par
ce nouveau document ; une relance complète exige une fenêtre appropriée.

Ordre de passage : **ADR rédigé, relu et accepté → WO-058 aligné puis validé → réalisation et
qualification hors fournisseur → manifeste concret et lancement opérateur du pilote**.
L'acceptation de l'ADR établit le cadre ; elle ne lance pas une campagne, ne valide pas les tests
et ne clôture pas le WO. Sa clôture Git reste soumise à la revue
humaine et à la fusion vers le train exact.

## 10. Historique des décisions et état d'acceptation

| Date | Événement | Portée |
|---|---|---|
| 2026-09-07 | Demande de cadrage live et création du draft WO-058 | Documentation initiale ; certaines bornes et politiques étaient proposées. |
| 2026-09-07 | Première série de réponses : pilote 1→2/3, signaux + secours 5 min, durée 4 h | Arbitrages fonctionnels validés par le propriétaire. |
| 2026-09-07 | Deuxième série : 1 000/3 000, isolation des erreurs métier, dernier cycle J5 | Remplace l'ancienne proposition d'arrêt global sur incompatibilité de schéma. |
| 2026-09-07 | Réponse 404 J5 : nouvelle interrogation au cycle normal suivant | Indisponibilité distinguée du retry technique terminal. |
| 2026-09-07 | Demande explicite de réaliser le plan ADR-SS-005 | Création de cette v0.1 proposée et alignement documentaire du WO ; pas d'acceptation formelle implicite. |
| 2026-09-07 | « Je valide formellement la v0.1 de l'ADR » | Acceptation explicite de la proposition au SHA-256 `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e` ; observation à `2026-09-07T09:23:14Z`, distincte de l'heure exacte du message non disponible. |
| 2026-09-07 | Demande de réaliser le plan d'enregistrement de l'acceptation | Copie exacte figée, métadonnées actualisées sans changement normatif, renvois ciblés et WO prêt pour revue ; aucune validation implicite de réalisation ou clôture. |

```text
ADR_SS_005_VERSION=0.1
ADR_SS_005_STATUS=ACCEPTED
FUNCTIONAL_CHOICES=OWNER_VALIDATED
ADR_SS_005_FORMAL_ACCEPTANCE=OWNER_ACCEPTED_2026_09_07
ADR_SS_005_ACCEPTANCE_OBSERVED_AT=2026-09-07T09:23:14Z
ADR_SS_005_ACCEPTANCE_MESSAGE_EXACT_TIME=NOT_AVAILABLE
WO058_STATUS=READY_FOR_OWNER_REVIEW
WO058_VALIDATION=PENDING_OWNER_REVIEW
RUNTIME_IMPLEMENTATION=NOT_STARTED
PROVIDER_CAMPAIGN=NOT_AUTHORIZED_BY_THIS_DOCUMENT
```

La copie figée identifie les octets effectivement acceptés. Le rapport distingue leur empreinte
de celle du présent document après enregistrement administratif de l'acceptation ; cette seconde
empreinte reste externe au fichier. Aucun commit n'est attribué au draft non committé. Toute
modification normative ultérieure exige une nouvelle version et une décision traçable.
