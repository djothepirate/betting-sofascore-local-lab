# Campagnes live locales J4/J5 — architecture WO-058

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Décision applicable : [ADR-SS-005 v0.1 accepté](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md).
Réalisation : [WO-058](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md).

## Session et autorité

`LiveCampaignService.prepare` lit les identités canoniques et leur provenance fournisseur,
refuse les sélections vides, dupliquées, forgées ou issues de fixtures, puis réserve un manifeste
immuable valable cinq minutes. Le manifeste fige les cibles, la durée, les plafonds et le profil
de capacité. Sa préparation ne crée ni worker ni contexte. Le lancement confirme son empreinte,
consomme le formulaire local, vérifie les trois opt-ins et refuse une politique modifiée depuis
la préparation.

Un seul thread propriétaire acquiert `ManualProviderRequestCoordinator`, puis le garde durable
commun aux parcours manuels et live. `LiveProviderSession` ouvre une fois la factory Playwright
avec EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS et EVENT_LINEUPS. Les wrappers manuels
conservent leurs politiques ; aucun endpoint, cache fournisseur ou mécanisme de repli n'est ajouté.

L'horloge du service est injectable. La session dérive ses échéances d'une origine UTC et du
temps monotone ; `LiveSchedule` reçoit explicitement les instants. Aucun ordonnanceur de
démarrage ne lance Playwright. Un watchdog créé avec la session interrompt celle-ci lorsqu'il
observe une suspension de plus de 2,5 secondes ou une divergence UTC/monotone supérieure à deux
secondes. Ce contrôle conservateur peut aussi arrêter une session après une longue suspension
du runtime ; il ne reprend jamais les tâches perdues.

## Ordonnanceur et limites

`LiveSchedule` sépare état sportif, état de collecte et complétude finale. J4 vérifie le statut
au départ puis toutes les 60 secondes en attente. En jeu, chaque triplet J5 reste contigu dans
l'ordre statistiques, incidents, compositions. Les départs d'une même famille sont espacés d'au
moins 60 secondes. Les signaux de phase sont produits par les normaliseurs ; le scheduler ne lit
pas de JSON. Un signal de première période entraîne un contrôle ponctuel, un signal de fin
potentielle arme J4 à la minute, et le secours intervient cinq minutes après le dernier J4 réussi.
Seul un J4 `finished` ouvre la finalisation. Son dernier triplet respecte encore l'espacement,
la fenêtre et les budgets ; une borne peut laisser la finalisation incomplète.

Le coordinateur transport conserve trois secondes après la fin de l'échange précédent. Les
échéances sont coalescées, sans rafale de rattrapage. Deux cycles successifs non servis avant
leur échéance suivante arrêtent la campagne pour capacité, y compris au milieu d'un triplet.
L'admission simule le cas récurrent de trois J5 et un J4 par match/minute. Le profil initial
conservateur compte 10 s de requête, 1 s de traitement et 3 s de délai, soit 56 s pour un match.
Les paliers 2/3 exigent une preuve de qualification renseignée et un profil qui passe l'admission.
Le hash déclaré de qualification doit renvoyer à une preuve revue ; sa forme seule ne prouve
aucune performance du fournisseur.

La fenêtre maximale est de quatre heures depuis le lancement. Les 1 000 tentatives par match
et 3 000 par campagne incluent les appels annulés après réservation et les issues inconnues.
Quatre places sont réservées pour une vérification J4 et les trois dernières familles J5.
Le plafond d'octets réserve le maximum de 5 Mio par réponse sans présumer de déduplication.
La sonde lit l'espace du volume PostgreSQL via une commande Docker fixe, sans shell intermédiaire,
avant préparation/lancement et avant chaque réservation. Elle exige deux fois l'enveloppe brute
restante plus au moins 1 Gio de marge ; une mesure indisponible refuse l'opération.

## Frontière d'arrêt

Pendant le délai réseau et immédiatement avant le dispatch, l'admission vérifie la session,
l'événement, la fenêtre et la propriété. Le contrôle SQL précède les verrous courts de départ :
aucun verrou d'arrêt du superviseur n'est détenu pendant une requête PostgreSQL. Le dernier
contrôle local et l'écriture de la commande GET sont synchronisés avec les arrêts.

L'arrêt individuel retire les tâches futures ; un GET déjà engagé peut finir sous timeout et
être persisté sans réactiver le match. L'arrêt global interdit les nouveaux départs et demande
l'annulation du transport partagé. Le nettoyage existant vérifie l'arbre des processus possédés.
Une exclusion durable reste occupée si la fermeture, la propriété ou le stockage sont incertains.
Au redémarrage, seule l'absence prouvée du processus propriétaire (PID et date de création)
permet de marquer une campagne orpheline interrompue. Une identité inaccessible n'est pas une preuve.
Le garde devient alors `CLEANUP_REQUIRED` ; aucune expiration ne le libère automatiquement.

## Transactions et provenance

La migration additive V33 crée sept tables `live_*` et `provider_campaign_guard`, distinctes
de J8. Les cibles et le manifeste sont immuables ; appels, dispatchs, réceptions, résultats et
transitions conservent leurs contraintes d'identité, d'unicité et de génération propriétaire.
Les curseurs par famille sont reconstruits depuis les occurrences et résultats référencés.

1. Une transaction réserve la tentative et consomme son budget avant tout réseau.
2. Une autorisation de dispatch est tracée. Cette date ne revendique pas un départ on-wire.
3. Le GET s'effectue sans transaction ni verrou SQL.
4. Une transaction sauvegarde le brut, son occurrence et le rattachement à l'appel.
5. Le parsing s'effectue hors transaction.
6. Une publication atomique rattache les observations normalisées, projections, complétude et
   état de collecte. Un échec conserve la réception déjà validée à l'étape 4.

La vue courante suit les occurrences A → A et A → B → A, sans réécrire l'heure ni la
classification d'un snapshot dédupliqué. Le score est une projection J4 versionnée, datée de sa
réception ; aucun score courant n'est reconstruit depuis les incidents. Absent, null, zéro et
indisponible restent distincts. Les signaux J5 conservent une clé indépendante de l'ordre du tableau.

Un contenu déjà purgé par J6 ne peut pas être silencieusement réhydraté via sa clé dédupliquée :
`LIVE_RAW_PREVIOUSLY_PURGED` fait échouer la nouvelle réception atomiquement et arrête la campagne
comme défaut de stockage. Les dates et preuves historiques demeurent inchangées. La rétention
refuse une campagne active ou un garde incertain ; la sauvegarde J6 couvre également les huit
tables du lot et leurs empreintes. Voir le [runbook J6](../runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md).

## Classification et consultation

Un JSON de schéma métier incompatible n'arrête que son match après contrôle de sécurité et
corrélation d'identité. J4 404, statut non géré ou régression sportive demandent une revue du match.
J5 404 conserve l'indisponibilité et attend le cycle normal suivant. Transport, refus HTTP hors
404, HTML/challenge, identité, contenu sensible, exception interne, stockage et anomalie non
classifiable arrêtent globalement. La portée live est distincte du statut renvoyé par un parseur.

MVC/Thymeleaf rend la préparation, les commandes et l'historique. Les GET d'état ne font que des
lectures locales. Le script de même origine lit toutes les cinq secondes, suspend son timer sur
onglet masqué, ignore une ancienne révision et conserve focus, sélection et dernières données.
Les DTO excluent payloads et identité de processus. Le retard affiché est celui de l'autorisation
transport, explicitement distinct d'une mesure on-wire. Les familles deviennent en retard après
deux intervalles sans succès : J4 utilise 60 s en attente/contrôle de fin et 300 s en jeu ; J5
utilise 60 s en jeu. La fin du suivi fige leur âge à la transition terminale persistée.
La CSP autorise `script-src 'self'` sur les pages live. Les lectures héritent de la restriction
`default-src 'self'` commune au site ; aucune directive `connect-src` explicite n'est déclarée.
Les scripts inline et l'évaluation dynamique restent interdits.

`LiveReplayRunner` rejoue des réponses synthétiques hachées, une horloge et des arrêts opérateur
avec le vrai scheduler et le normaliseur pur. Ses identifiants de trace ne sont pas des IDs SQL.
Il ne publie aucune observation ni validation humaine. Les tests de stockage, Web, replay et
Chromium restent des preuves séparées dans le [rapport du lot](../validation/WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md).
