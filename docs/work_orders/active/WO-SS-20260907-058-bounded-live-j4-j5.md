# WO-SS-20260907-058 — Campagnes live locales J4/J5 sur sélection de rencontres

- **Statut :** `READY_FOR_REVIEW` — retour fonctionnel du 7 septembre 2026 corrigé et qualifié hors fournisseur ; reprise des essais opérateur et revue de réalisation attendues, aucune clôture.
- **Date :** 2026-09-07.
- **Jalon :** expérimentation live locale après J9, distincte des parcours manuels existants.
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
- **Cible de PR :** `feature/V0.1.0-RC01`.
- **Base exacte :** `6dfd14286d4f269cbe100bd965257c20298538db`, sommet GitHub vérifié le 7 septembre.
- **Worktree :** `.tmp/wo058-live-j4-j5`, depuis le dossier Codex du Lab ; worktree distinct d'Eclipse.
- **Autorité reçue :** ADR-SS-005 v0.1 accepté, puis déclaration « Je valide le WO-058 les travaux peuvent commencer » et demande explicite d'exécuter le plan de réalisation ; port 8087 libéré pour les tests.
- **ADR live :** [ADR-SS-005 v0.1](../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md), `ACCEPTED` le 7 septembre 2026 ; proposition acceptée de SHA-256 `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e`.
- **Livrable présent :** ADR accepté, WO validé, réalisation locale et qualification hors fournisseur ; état des preuves dans le rapport de réalisation.
- **Alignement de gouvernance :** renvois ciblés dans ADR-SS-001 et AGENTS.md ; ADR-SS-002 à 004 inchangés.
- **Réalisation applicative :** réalisée et qualifiée hors fournisseur ; **validation formelle du WO :** acquise ; **revue de réalisation :** à effectuer ; **campagne fournisseur :** non exécutée.

Les statuts restent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`. Le socle reste Java 25 LTS, Spring Boot 4.1.0, Maven wrapper,
PostgreSQL local Docker Desktop et application sur `127.0.0.1:8087`, textes UTF-8.

## 1. Objectif et origine du besoin

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
| J4 `notstarted` | `WAITING_START`, nouvelle échéance J4 à 60 s ; aucun J5 | Répéter jusqu'à statut différent ou borne/arrêt |
| J4 `inprogress` en `INITIAL_CHECK` ou `WAITING_START` | Arrêter la boucle d'attente ; premier cycle J5 dès disponibilité du coordinateur | `COLLECTING`, puis J5 chaque 60 s |
| Signal de première période/mi-prolongation, boucle de fin non armée | Un contrôle J4 ponctuel ; si `inprogress`, maintien en `COLLECTING` | Échéancier J5 inchangé ; pas de nouveau « premier cycle » |
| J4 `inprogress` ponctuel ou de secours en `COLLECTING` | Conserver l'état et les échéances J5 | Aucun redémarrage ni double cycle |
| J4 `finished`, même au premier contrôle | Annuler les échéances ordinaires, conserver l'observation de fin | `FINALIZING`, un dernier cycle J5 borné, puis `FINISHED_CONFIRMED` |
| Signal J5 classé comme fin potentielle selon §6 | Programmer une vérification J4 dans la minute, sans doubler une échéance existante | `CHECKING_FINISH`, J4 chaque 60 s, J5 continue ; le signal de mi-temps ne demande qu'un contrôle ponctuel |
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
- Chaque famille J5 a une échéance nominale toutes les 60 s, avec des décalages internes dus aux
  appels séquentiels. Une minute signifie trois requêtes par match, même si les compositions n'ont
  pas changé. Ne pas réduire discrètement leur fréquence au motif qu'elles changeraient moins.
- Utiliser une horloge monotone pour les délais et UTC pour les instants persistés ; afficher Paris.
  Cible `t0 + k × 60 s`, délai minimal de 60 s entre deux départs de la même famille/événement et
  aucun chevauchement de cycles d'un événement. Les familles d'un cycle restent contiguës et ordonnées.
- Sérialiser tous les départs J3/J4/J5 dans le coordinateur commun, avec son fence conservateur et
  sa preuve temporelle minimale de trois secondes. Interdire les appels manuels concurrents pendant
  la campagne live ; autoriser les lectures locales.
- Un retard ne produit jamais de rattrapage en rafale. Coalescer les échéances devenues obsolètes,
  compter les cycles non exécutés et rendre le retard visible. La cible de minute n'est pas garantie
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
6. Prévoir un contrôle J4 de secours toutes les cinq minutes depuis le dernier J4 réussi en phase
   active. Il couvre l'absence d'`injuryTime`, un endpoint incidents indisponible et un événement
   arrêté autrement. Ce contrôle est remplacé par la cadence J4 de 60 s dès `CHECKING_FINISH`.
7. Lorsqu'une fin manque encore, la limite de durée arrête la collecte avec fin **non confirmée**.
   Ni T+90, ni T+120, ni le score, ni une liste d'incidents vide ne suffisent à conclure.

Le dernier cycle J5 est au plus unique, ordonné et compris dans le budget. Il commence après
réception du J4 `finished`, à sa première échéance respectant les 60 s depuis le dernier départ
de chaque famille et le délai global, pour obtenir une observation postérieure ; aucune attente de stabilisation
ou répétition post-match n'est ajoutée. S'il n'est pas exécutable ou échoue, le statut sportif reste
confirmé et la finalisation est explicitement incomplète.

## 7. Bornes retenues et contrôle de capacité

Ces bornes sont celles d'ADR-SS-005 v0.1 formellement accepté par le propriétaire. Elles ne
constituent pas un quota fournisseur connu ou une garantie de performance déjà mesurée.

| Paramètre | Valeur retenue pour la première version |
|---|---|
| Campagnes actives | Une seule globalement, lease commune conservée |
| Nombre de matchs | Premier essai réel : 1 ; extension à 2 puis 3 seulement après qualification de charge ; aucune admission automatique des dix lignes |
| J4 attente / J5 / J4 fin | 60 s par événement/famille |
| J4 de secours actif | 300 s ; remplacé par J4 fin, jamais cumulé |
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
hors secours est `Q = W + 3L + F` appels/minute. Ajouter les contrôles de secours réellement dus,
les contrôles ponctuels et la réserve finale. Même avec des réponses instantanées, dix matchs
actifs exigent 30 appels/minute, soit une cadence moyenne de 2 s incompatible avec le minimum de
3 s. En vérification de fin, ils en exigent 40. Une heure de J5 seul coûte 180 appels par match ;
deux heures pour dix matchs coûtent 3 600 appels avant J4.

Le fence attend aussi la fin observable du dispatch précédent : le débit réel est inférieur à
20 appels/minute. Le contrôle d'admission doit simuler les échéances sur la fenêtre, les transitions
possibles, les durées de requête/traitement qualifiées, les délais et la réserve ; il ne se limite
pas à `N <= 3`. Le plafond de 3 est un maximum candidat, pas une promesse de cadence. En cas
d'incompatibilité, refuser le manifeste entier avec le nombre admissible et une proposition de
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
| J5 404 | `ENDPOINT_UNAVAILABLE`, cycle suivant à cadence normale tant que match actif | Occurrence 404, familles suivantes traitées, pas de retry immédiat |
| Statistique absente/null, score non renseigné | Manquant distinct d'un zéro fourni | Zéro réel et absence testés séparément |
| Score J4 / scores d'incidents, prolongation et tirs au but | Projection versionnée J4, temporalités et natures séparées | Pas de reconstruction par comptage des buts ni dernier index |
| Compositions partielles/non confirmées | Complétude actuelle préservée | Fixture partielle et rendu explicite |
| `injuryTime` 45/90/105/120, HT/FT/Extra time/isLive | Signal sourcé, vérification J4 selon §6, jamais fin inférée | Cas nominal, ambigu, sans signal, prolongation et tirs au but |
| Réponse identique A → A | Deux réceptions mesurées, même contenu possible | Deux occurrences/cycles, dernière réception plus récente |
| Retour exact A → B → A | La vue live revient à A avec sa nouvelle occurrence | Contre-épreuve du tri historique par seule observation |
| Incidents répétés/réordonnés/corrigés | Pas de double déclenchement, révision consultable | Replay de permutations et annulation de but |
| Statistiques à t1, incidents à t2, compositions à t3 | Triplet explicitement non atomique côté fournisseur | Affichage des trois réceptions et cycle associé |
| JSON de schéma métier incompatible, sécurité/corrélation satisfaites | Arrêt du seul match ; brut conservé, familles restantes annulées, autres matchs poursuivis | Deux matchs simulés, compteur consommé, aucun retry du match arrêté |
| Transport/HTML/challenge/401/403/429/5xx/timeout, identité, exception interne du parseur ou stockage | Arrêt global tracé, dernière donnée bonne toujours visible comme ancienne | Contre-épreuve à deux matchs, compteur de tentatives et nettoyage |

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
| AC03 — attente du début | `notstarted` répété puis `inprogress`, retard du coup d'envoi | J4 à échéances 60 s, aucun J5 avant preuve de début |
| AC04 — lancement en cours/fini | Statut local déjà `finished` à la préparation/lancement ; premier J4 réseau déjà `inprogress` ou `finished` pour une cible admise | Aucun appel pour les matchs déjà terminés localement ; chemin réseau direct et finalisation bornée conservés pour les autres |
| AC05 — cycle J5 | Plusieurs matchs et trois familles, dont lineups inchangées | Ordre et cadence mesurés par famille, aucun overlap |
| AC06 — fin sans inférence | injuryTime première/seconde période, HT, prolongation, tirs au but, correction VAR | J4 déclenché correctement, fin seulement sur J4 `finished` |
| AC07 — incident absent | Incidents vides/404 ou signal manquant | J4 secours, limite atteinte sans faux `finished` |
| AC08 — fraîcheur vraie | A→A et A→B→A pour J4 et chaque famille J5 | Occurrences conservées et projection/rendu de la dernière réception |
| AC09 — indisponibilité partielle | 404 J5 puis succès au cycle normal suivant | Partiel visible, pas de zéro ni retry accéléré |
| AC10 — portée des erreurs | Deux matchs : schéma métier incompatible admissible sur le premier ; puis contre-épreuves 403, timeout, identité, contenu inattendu, exception interne et stockage | Schéma : seul match arrêté, brut conservé et second poursuivi ; contre-épreuves : arrêt global, pas de fallback/retry/contexte recréé |
| AC11 — cadence bornée | Réponses lentes, 1/2/3 matchs, dix matchs refusés, signaux simultanés | Mesure monotone/on-wire, respect 3 s, retards/coalescence/arrêt de capacité |
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

Les arbitrages fonctionnels ne sont plus à redemander : pilote 1→2/3, quatre heures,
1 000/3 000 tentatives, J4 sur signaux + secours cinq minutes, dernier cycle J5, isolation du
schéma métier incompatible et nouvel échantillon J5 après 404 au cycle normal. La lecture locale
cinq secondes et les autres paramètres conservés figurent dans le plan demandé.

Restent les décisions distinctes suivantes :

1. Relire la réalisation et ses preuves hors fournisseur. L'ADR et le WO sont validés ;
   ces décisions ne sont pas à redemander. La revue de réalisation ne vaut pas clôture.
2. Après réalisation et qualification hors fournisseur, préparer un manifeste concret et lancer
   manuellement le pilote. Les paliers 2/3 restent soumis à qualification et admission.

### Définition de fini

Le cadrage et l'autorisation de réalisation sont acquis. La réalisation, ses limites et la
qualification de B–D sont consignées dans le
[rapport d'exécution](../../validation/WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md),
avec l'[architecture](../../architecture/LIVE-J4-J5-CAMPAIGNS.md) et le
[runbook](../../runbooks/LIVE-J4-J5-CAMPAIGNS.md). L'ancien rapport de cadrage conserve séparément
la preuve Maven non verte liée au port 8087. Aucun résultat historique n'est transformé en succès.

Les étapes B–D sont réalisées et qualifiées hors fournisseur ; une
campagne fournisseur exige en plus le manifeste concret et le lancement de E. La revue n'est ni une campagne réussie,
ni la preuve d'une donnée sportive exacte. La clôture et la livraison Git sont séparées : une PR
du WO cible exclusivement `feature/V0.1.0-RC01`, avec revue humaine et fusion avant classement terminé.
