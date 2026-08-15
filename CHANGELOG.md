# Changelog

Les évolutions notables du SofaScore Local Lab sont consignées dans ce fichier.

## [Non publié]

### Corrigé

- correction du faux positif `SCHEMA_INCOMPATIBLE` sur les réponses réelles `EVENT_INCIDENTS` :
  `event-incidents-v3` reconnaît `addedTime=999` comme une sentinelle fournisseur uniquement sur
  un marqueur `period`, conserve les octets bruts inchangés, omet la sentinelle de la valeur
  normalisée et continue de refuser `999` sur tout incident métier latéralisé ;
- comptage de complétude des marqueurs techniques `period` et `injuryTime` sans exiger le champ
  latéral `isHome`, afin de ne pas inventer de côté pour un événement global à la période ;
- isolation du contexte PostgreSQL/Testcontainers vis-à-vis des opt-ins opérateur J3/J4/J5 : la
  suite d'intégration force désormais toutes les voies réseau à l'état désactivé sans lire ni
  modifier `.env` ;
- correction de la politique J5 qui traitait tout statut non `2xx` comme terminal : un HTTP `404`
  sur `EVENT_STATISTICS`, `EVENT_INCIDENTS` ou `EVENT_LINEUPS` devient une indisponibilité de
  famille persistée, sans retry, puis la campagne continue dans l'ordre prévu ;
- distinction explicite entre `UNAVAILABLE`, `EMPTY_VALID` et `TRANSPORT_ERROR`, avec normaliseur
  de provenance par famille, rendu `UNAVAILABLE · N/A` et absence de valeur synthétisée ;
- migration Flyway V9 reclassant les anciens snapshots J5 HTTP `404` de
  `TRANSPORT_ERROR/HTTP_STATUS_404` vers `ENDPOINT_UNAVAILABLE`, sans modifier les octets bruts ni
  créer rétroactivement une observation normalisée ;
- conservation de la date civile du match et de la zone IANA dans le lien de retour de la fiche
  J4 : Saint-Étienne — Clermont Foot revient désormais sur le `2026-08-14` au lieu de la date
  locale courante du `2026-08-15` ;
- remplacement des libellés statiques `SYNTHETIC_FIXTURE` et `event-details-v1` de la fiche par la
  provenance, la référence de source et le parseur réellement persistés, notamment
  `PROVIDER_SNAPSHOT`, `snapshot:16` ou `snapshot:17`, et `event-details-v2` ;
- ajout d’un test MVC de régression reproduisant localement l’événement `16386245`, sa date en
  `Europe/Paris` et sa provenance fournisseur, sans résolution d’URI ni appel réseau ;
- correction de l’upgrade Flyway V5 préremplie vers V6 : le trigger
  `event_detail_observation_append_only` est suspendu uniquement pendant le backfill transactionnel
  des nouvelles colonnes de provenance, puis réactivé avant les contraintes finales ;
- ajout d’un test PostgreSQL reproduisant une base V5 contenant déjà une identité, une observation
  canonique et un détail synthétique, puis vérifiant après V6 l’égalité des champs
  historiques, l’absence de ligne ajoutée ou supprimée et le refus persistant de `UPDATE`/`DELETE` ;
- qualification de l’échec de démarrage local du 2026-08-15 comme incident de migration avant
  campagne : rollback Flyway réussi, zéro appel fournisseur et aucune qualification réelle
  exécutée ;
- qualification humaine de la reprise corrective sur la base persistante : migration V5 → V6
  réussie avec les verrous réseau actifs, identité et deux versions synthétiques toujours
  consultables, puis arrêt gracieux complet de l’application sans appel fournisseur ;
- isolation des scénarios de binding J3/J4 vis-à-vis d’une configuration opérateur déjà armée :
  chaque mini-contexte retire la source d’environnement ambiante avant le chargement
  d’`application.yml` et fixe explicitement les opt-ins J3, J4 et J4 sous-étape 2. Les 212 tests
  standards passent avec la configuration J4 sous-étape 2 activée, sans appel fournisseur.
- extension de cette isolation au nouvel opt-in J5 : les trois scénarios historiques J3/J4 fixent
  désormais explicitement `SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false` et vérifient que
  J5 reste inactif. La commande `mvnw.cmd clean verify`, initialement en échec sur ces trois tests
  avec la configuration opérateur J5 armée, repasse avec 257 tests et zéro appel fournisseur.

### Ajouté

- migration Flyway V10 append-only autorisant la provenance `event-incidents-v3` sans réécrire les
  observations historiques V1/V2, avec test d'upgrade V9 → V10 sur PostgreSQL 18.4 ;
- fixture synthétique minimale et tests de régression du marqueur de période fournisseur, ainsi
  que couverture du parcours ordonné prouvant que les compositions sont atteintes après parsing
  compatible des incidents ;
- tests hors ligne du scénario mixte « statistiques HTTP `404`, incidents et compositions `2xx` »,
  de l'affichage d'indisponibilité, de la persistance PostgreSQL `UNAVAILABLE` et de l'upgrade
  V8 → V9 d'un snapshot historique mal classé ;
- voie de qualification réelle J5, désactivée par défaut et mutuellement exclusive de J3/J4,
  limitée à l'origine exacte `https://www.sofascore.com` et aux trois endpoints logiques
  `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` ;
- contrôle humain J5 avec préparation sans réseau, phrase exacte valable cinq minutes,
  acquittement, claim immuable, trois appels séquentiels maximum et verrou terminal dans le
  processus après succès, incident, expiration ou arrêt ;
- transport J5 sans proxy, redirection, cookie, jeton, compte, en-tête de navigateur ou retry,
  avec réponse bornée, délai minimal de trois secondes et arrêt avant toute famille restante au
  premier incident ;
- parseurs fournisseur `event-statistics-v2`, `event-incidents-v2` et `event-lineups-v2`,
  persistance brute avant parsing, normalisation `PROVIDER_SNAPSHOT` et migration Flyway V8
  append-only étendant les contraintes V7 sans modifier une migration partagée ;
- panneau J5 local de qualification gardée et résultat minimisé, protégés par le jeton de formulaire
  à usage unique, sans payload brut ni identifiant libre dans l'action finale ;
- couverture hors ligne de la configuration, de la confirmation, de l'ordre des trois transports,
  des `429`, des incompatibilités sans objet partiel, de la provenance PostgreSQL V8 et de
  l'absence de credentials ou d'en-tête navigateur ;
- jalon J5 hors ligne pour `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS`, rattaché aux
  identités canoniques J4 sans ajouter de transport fournisseur ;
- contrats synthétiques versionnés `event-statistics-v1`, `event-incidents-v1` et
  `event-lineups-v1`, corpus nominal/partiel/vide et ruptures de type sans coercition ;
- rapports déterministes `COMPLETE`, `PARTIAL` et `EMPTY_VALID`, avec score, nombres de signaux et
  chemins JSON manquants, sans donnée inventée ;
- migration Flyway V7 créant les observations J5, les métriques, les incidents, les deux côtés de
  composition et leurs joueurs, avec provenance, deux hashes, déduplication et triggers
  append-only ;
- import transactionnel et idempotent des trois fixtures nominales sur l'identité synthétique J4,
  et lecture de la dernière version de chaque famille ;
- page locale `/events/{canonicalEventId}/statistics` présentant valeurs, complétude et provenance,
  avec action d'import synthétique protégée par jeton de formulaire à usage unique ;
- sous-étape 2 J4 paramétrable dans `/events`, protégée par un opt-in distinct qui rend la
  sous-étape 1 indisponible pendant son activation ;
- préparation sans réseau d'un ID `EVENT_DETAILS` borné, phrase exacte liée à cet ID, acquittement
  et claim immuable empêchant de remplacer l'identifiant lors de l'action finale ;
- rafraîchissements manuels répétables du même match, avec exactement un nouvel appel fournisseur
  sans cache par cycle, nouvelle confirmation obligatoire et délai minimal de trois secondes ;
- persistance brute avant parsing sur chaque rafraîchissement, affichage minimisé du snapshot et
  indication `NOUVELLE_VERSION` ou `DÉDUPLIQUÉE` sans exposer le payload ;
- arrêt global commun aux deux sous-étapes et tests hors ligne des `429`, de l'absence de retry, de
  la répétition manuelle, de l'exclusion de configuration et du binding Web sans ID libre lors de
  l'exécution ;
- voie de qualification réelle J4 sous-étape 1, désactivée par défaut et limitée par construction
  aux événements `16386245` et `16421052` sur le chemin exact `/api/v1/event/{eventId}` ;
- parseur fournisseur versionné `event-details-v2`, distinct du contrat synthétique historique V1,
  avec enveloppe `event`, tour imbriqué, champs facultatifs et incompatibilité sans objet partiel ;
- migration Flyway V6 append-only ajoutant la provenance `PROVIDER_SNAPSHOT` aux observations de
  détail et étendant le cache local à `EVENT_DETAILS` sans déplacer les octets bruts ;
- circuit opérateur J4 à confirmation exacte, expiration cinq minutes, arrêt global et verrou
  terminal automatique après succès, incident ou expiration ;
- orchestration de deux événements maximum avec cache préalable, délai minimal de trois secondes,
  persistance brute avant parsing, normalisation atomique et aucun retry ;
- arrêt au premier `403`, `429`, `5xx`, timeout, contenu inattendu, rupture de schéma ou incohérence
  d’identifiant, couvert sans appel Internet par transport simulé et tests PostgreSQL ;
- migration Flyway V4 créant les identités canoniques d’événements et leurs observations
  append-only, avec UUID déterministe, provenance complète, déduplication et trigger d’immuabilité ;
- migration Flyway V5 conservant les détails J4 hors ligne sous forme d’observations append-only ;
- corpus synthétique `EVENT_DETAILS`, parseur strict `event-details-v1` et couverture des champs
  inconnus, absents ou de type incompatible ;
- import transactionnel et idempotent du corpus J4, avec validation du rattachement à la même
  identité fournisseur avant toute écriture ;
- normalisation manuelle d’un snapshot local `SCHEDULED_EVENTS` après contrôle de son intégrité,
  sans mutation de sa classification historique et sans résultat partiel ;
- recherche locale par date civile et zone IANA, page de résultat et page de détail exposant
  identité, provenance et chronologie des observations ;
- manifeste de fixture v1 avec origine, preuve de schéma, taille maximale, hashes attendus et traçabilité de minimisation ;
- chargeur de fixtures classpath entièrement hors ligne, avec classification `JSON`, `HTML` ou `OTHER` ;
- calcul SHA-256 brut et JSON canonique stable malgré l’ordre des propriétés ;
- contrôles bloquants de taille, d’intégrité, de JSON ambigu et de motifs sensibles.
- corpus synthétique `SCHEDULED_EVENTS` de neuf scénarios avec manifestes, hashes réels et test d’inventaire classpath.
- parseur hors ligne `scheduled-events-v1`, DTO externe minimal et mapper vers un modèle local immuable ;
- résultats de parsing structurés avec statuts, avertissements, problèmes et preuve de traçabilité ;
- couverture hors ligne des incompatibilités `scheduled-events-v1` : champs obligatoires absents, type numérique modifié, structure inattendue et contenu HTML ;
- inventaire applicatif du corpus classpath avec disponibilité et répartition des résultats de parsing ;
- politique de décision J3 hors ligne avec résultats `USE_CACHE`, `BLOCKED` et `TRANSPORT_ELIGIBLE` ;
- circuit J3 en mémoire à activation explicite, incidents typés et absence de réouverture automatique ;
- garde atomique limitant à un le nombre de permis d’appel simultanés ;
- migration Flyway V2 append-only conservant les octets exacts, leur taille et la provenance `DIRECT_LOCAL_ENDPOINT` ;
- port et adaptateur JDBC de persistance des snapshots manuels bruts, avec résultat explicite `INSERTED` ou `DEDUPLICATED` ;
- validation bornée des métadonnées et payloads bruts, calcul SHA-256 et déduplication par endpoint, requête et hash ;
- tests PostgreSQL/Testcontainers de la migration V2, de la fidélité binaire, de la séparation brut/normalisé et de la déduplication ;
- contrat de transport `SCHEDULED_EVENTS` limité à l’origine exacte `127.0.0.1` et à une route de simulation fixe ;
- transport `RestClient` synchrone sans proxy ni redirection, avec délais plafonnés, lecture bornée et conservation des octets de réponse ;
- orchestrateur appliquant la politique J3 puis la garde atomique avant toute entrée/sortie simulée ;
- tests de serveur simulé couvrant la requête exacte, le statut `429` sans retry, la taille maximale et le rejet de contenu sensible ;
- contrôle opérateur J3 en mémoire démarrant sous arrêt global, avec réarmement et activation distincts ;
- intention manuelle `SCHEDULED_EVENTS` datée, phrase exacte à usage unique, acquittement explicite et expiration après cinq minutes ;
- tableau de bord local exposant circuit, incidents, arrêt global, confirmation et verrous fournisseur sans rendre le transport disponible ;
- processeur d’issues J3 conservant le brut avant parsing puis appliquant une classification idempotente au même snapshot ;
- couverture des arrêts sur `400`, `401`, `403`, `429`, `5xx`, timeout, erreur d’entrée/sortie, taille excessive, HTML et schéma incompatible ;
- conservation bornée de `Retry-After` comme frontière de blocage, sans programmation de retry ni réouverture automatique ;
- preuve automatisée de déduplication et d’absence de valeur sensible dans les sorties capturées ;
- chemin fournisseur J3 opt-in limité à l’origine exacte `https://www.sofascore.com`, à la date
  `2026-08-13` et aux pages `1` à `5` de `SCHEDULED_EVENTS` ;
- orchestrateur d’une action manuelle unique exécutant les pages séquentiellement, avec concurrence
  `1`, délai minimal de trois secondes, conservation avant parsing et arrêt au premier incident ;
- action Web distincte après confirmation, états `CONFIRMED_READY`, `EXECUTING`, `COMPLETED` et
  `FAILED`, et interdiction d’une seconde exécution dans le même processus ;
- client fournisseur sans proxy, redirection, cookie, jeton, compte ou donnée de session, couvert
  hors ligne avec `MockRestServiceServer` ;
- preuve terminale J3 minimisée, affichable et téléchargeable localement, composée uniquement des
  métadonnées de transport, de persistance et de classement des pages effectivement tentées ;
- verrou terminal automatique `QUALIFICATION_TERMINAL_LOCK` après succès ou incident, avec refus
  du réarmement dans le même processus après consommation de la qualification ;
- fixture synthétique minimisée au contrat utile observé `scheduled` / `tournament` /
  `timezoneEventCount`, sans valeur, URI, en-tête ou donnée de session fournisseur ;
- modèle local explicite des disponibilités de tournois et des compteurs d’événements par décalage
  horaire, distinct de l’ancien modèle synthétique `events` ;
- politique de reprise J3 relisant et reparsant localement l’unique snapshot de page 1 avant
  d’autoriser une séquence fournisseur strictement limitée aux pages 2 à 5 ;
- checkpoint PostgreSQL contrôlant l’unicité, l’intégrité brute, le succès HTTP, la compatibilité du
  parseur et `hasNextPage=true`, avec blocage persistant dès qu’une page 2 existe ;
- intention, action graphique et preuve minimisée v2 dédiées à la reprise, sans ouvrir la future
  interrogation complète répétable ;
- fixtures synthétiques de la forme qualifiée page 2 et de sa rupture par tableau non vide, sans
  donnée fournisseur, URI, cookie, jeton, compte ou session ;
- reparsing applicatif en lecture seule des checkpoints J3, avec séparation explicite entre statut
  historique persisté et résultat courant en mémoire ;
- politique de reprise J3 à la page 3 exigeant les deux checkpoints locaux reparsables et l’absence
  persistée des pages 3 à 5 avant de rendre le transport éligible ;
- intention et orchestration bornées aux pages 3, 4 et 5, avec compteur initial à deux, absence de
  répétition des pages 1 et 2 et preuve minimisée distinguant checkpoints et tentatives réseau ;
- collecte manuelle répétable depuis le tableau de bord, repartant obligatoirement de la page 1 et
  progressant uniquement selon le booléen `hasNextPage` produit par `scheduled-events-v1` ;
- plafond local de 25 pages avec arrêt `PAGINATION_LIMIT_REACHED` avant toute tentative de page 26,
  réarmement explicite entre deux collectes et maintien de la concurrence à un ;
- preuve minimisée v3 indiquant le mode de pagination, la limite locale et `hasNextPage` pour les
  pages parsées, sans payload, URI, en-tête, secret ou donnée de session ;
- cache PostgreSQL du parcours manuel dynamique, indexé par la clé exacte date/page, limité aux
  snapshots HTTP réussis classés `PARSED`, frais pendant les dix minutes du catalogue et produits
  par la version courante de `scheduled-events-v1` ;
- migration Flyway V3 append-only séparant le checkpoint de fraîcheur du snapshot brut : une
  nouvelle observation identique peut rafraîchir le cache sans réécrire ni dupliquer le payload ;
- preuve minimisée v4 distinguant `CACHE` et `PROVIDER`, les pages réellement demandées au
  fournisseur et les cache hits locaux, sans inclure le payload ou l’URI ;
- catalogue borné des 50 snapshots bruts locaux les plus récents, sans chargement automatique des
  payloads, et action explicite permettant d’inspecter une seule ligne à la fois ;
- vue JSON formatée en mémoire après contrôle de la taille, du SHA-256, des motifs sensibles et du
  JSON strict, avec échappement HTML et en-têtes `no-store` ;

### Documentation

- diagnostic minimisé des snapshots réels 32 et 34 : réponses incidents HTTP `200`, respectivement
  20 et 22 objets, deux sentinelles de période `addedTime=999` dans chaque réponse, arrêt terminal
  avant les compositions sous V2, puis correction versionnée V3 validée exclusivement hors ligne ;
- consignation minimisée de la première campagne réelle J5 : détail J4 préalable dans le snapshot
  28, réponse JSON HTTP `404` de `EVENT_STATISTICS` conservée dans le snapshot 30, arrêt
  `FAILED_LOCKED` après un seul appel, zéro retry et aucune tentative `incidents` ou `lineups` ;
- Work Order séparé `WO-SS-20260815-006` pour la qualification réelle J5, avec revue de
  l'ADR-SS-001, configuration temporaire exacte, politique d'arrêt, preuve minimisée et obligation
  de reverrouillage avant tout redémarrage ;
- consignation minimisée du second test J4 fourni par l'opérateur : snapshot 25 de `16412917`,
  acquisition réussie de `16391135` dans le snapshot 26 et refus `EVENT_ID_MISMATCH` de l'import
  synthétique J5 sur cette identité réelle ;
- architecture et readiness de la voie réelle J5 : `257` tests standards, `18` tests
  PostgreSQL/Testcontainers, huit migrations Flyway et zéro appel fournisseur pendant la
  réalisation ;
- procédure humaine J5 définissant l'activation temporaire, l'ordre des trois appels, l'arrêt sans
  retry, les champs de preuve autorisés et l'état bloqué à restaurer après succès ou incident ;
- qualification humaine hors ligne de J5 à partir de huit captures opérateur non versionnées :
  navigation J4 → J5, absences explicites avant import, trois familles à `COMPLETE · 100%`,
  provenance, parseurs et hashes visibles, avec `PROVIDER_SCHEMA_VALIDATED=NO` et voies réseau
  toujours bloquées ;
- archivage du Work Order J5 au statut `VALIDATED`, tout en conservant la future campagne en
  conditions réelles à `NOT_RUN` et hors autorisation de ce Work Order ;
- architecture hors ligne historique J5 précisant les trois formes de chemins cibles, l'absence de
  transport dans le périmètre du Work Order 005, les algorithmes de complétude, la séparation
  source/normalisé et le schéma V7 ;
- consignation minimisée de l'unique tentative de découverte J5 : `HTTP 403`, zéro retry, arrêt
  immédiat, cinq exemples non appelés et `providerSchemaValidated=false` maintenu ;
- rapport de qualification technique J5 couvrant parsing, MVC, Flyway/PostgreSQL, déduplication,
  append-only et invariants réseau ;
- consignation minimisée de la campagne humaine J4 sous-étape 1 : deux transports autorisés, deux
  snapshots HTTP `200` classés `PARSED`, arrêt global appliqué, anomalie locale corrigée puis retest
  humain concluant des deux retours par date et des provenances ;
- procédure de requalification utilisant exclusivement les snapshots locaux 16 et 17 après
  reverrouillage de la configuration, sans préparation ni réexécution de la campagne ;
- protocole correctif imposant une première application de V6 avec les cinq clés réseau remises à
  l’état bloqué, avant toute nouvelle activation de la campagne réelle ;
- amendement du Work Order J4 autorisant uniquement la campagne réelle sous-étape 1 et consignant
  la revue compatible de l’ADR-SS-001, avec politique J4 plus stricte sans retry sur `5xx` ;
- protocole Windows J4 pour l’activation temporaire, la validation humaine des deux matches,
  l’arrêt au premier incident et la remise obligatoire de la configuration à l’état bloqué ;
- amendement du Work Order après qualification humaine de la sous-étape 1, autorisant la
  sous-étape 2 paramétrable et les rappels manuels unitaires avec une nouvelle confirmation par
  appel, avec une qualification fournisseur réelle alors encore à `NOT_RUN` au moment de cette
  autorisation ;
- revue de l'ADR-SS-001 concluant `COMPATIBLE_NO_CHANGE_REQUIRED` pour le parcours paramétrable :
  appel manuel, concurrence unitaire, absence de polling, compte, cookie, jeton, proxy ou retry ;
- qualification technique hors ligne de la sous-étape 2 avec 212 tests standards et 16 tests
  d'intégration réussis, sans appel fournisseur ; sa qualification humaine et réelle était encore
  en attente au stade de cette readiness ;
- qualification humaine réelle de la sous-étape 2 sur les identifiants paramétrables `16483632`
  et `16412917`, avec une préparation, une confirmation et exactement un transport fournisseur
  par cycle ;
- validation du rappel manuel de `16412917` : réponse inchangée dédupliquée avant le coup d'envoi,
  puis nouvelle observation `inprogress` issue du snapshot 23 après le coup d'envoi, sans
  réécriture du snapshot 19 `notstarted` ;
- confirmation que les deux versions restent consultables dans l'historique append-only et que
  les snapshots bruts demeurent inspectables localement, sans intégrer le JSON aux preuves ;
- arrêt global J4, arrêt de l'instance de campagne, remise des six paramètres locaux à l'état
  bloqué, puis vérification après redémarrage du statut `LOCKED`, du champ d'identifiant
  insaisissable et de l'action de préparation désactivée ;
- arrêt gracieux final de Tomcat, JPA et Hikari après la vérification du reverrouillage ;
- vérification Maven finale en configuration bloquée : 212 tests standards et 16 tests
  PostgreSQL/Testcontainers réussis, Flyway V6 validé et aucun appel fournisseur ;
- fusion sans conflit de la Pull Request `#8` par le commit
  `950bb0f0ddd0edfe2a5e7d1e768498c049a86467`, après qualification humaine réelle et réussite des
  vérifications Maven finales ;
- clôture du Work Order `WO-SS-20260815-004` au statut `VALIDATED` et archivage dans
  `docs/work_orders/completed`, sans autoriser polling, production ou déploiement VPS ;
- ouverture du Work Order `WO-SS-20260815-004` sur la branche `codex/j4-events` depuis le merge J3
  `b79ccd62e7863718f49a22b6c54a7fc73cf87986` ;
- contrats J4 de l’identité canonique, de la normalisation versionnée et du détail synthétique hors
  ligne, avec rapport de qualification technique Windows ;
- validation fonctionnelle humaine J4 sous Windows, sans modification de `.env`, confirmant le
  verrouillage visible du connecteur, la recherche locale, l'identité synthétique stable, ses deux
  versions et son détail hors ligne ;
- intégration de l'implémentation J4 par la Pull Request `#6`, sans conflit, après réussite de 180
  tests standards, 14 tests d'intégration et du parcours fonctionnel hors ligne ;
- réouverture corrective du Work Order `WO-SS-20260815-004` au statut `IN_DEVELOPMENT` : J4 est
  `IMPLEMENTATION_MERGED` et `OFFLINE_PATH_QUALIFIED`, tandis que
  `REAL_MATCH_QUALIFICATION_PENDING` interdisait alors sa clôture définitive ;
- démarrage du Work Order `WO-SS-20260808-002` sur la branche `feat/j2-scheduled-events-fixtures` depuis le tag `j0-j1-v0.1.1` ;
- passage du jalon J2 au statut `IN_DEVELOPMENT` avec `SCHEDULED_EVENTS` comme première famille de fixtures hors ligne.
- contrat d’architecture `scheduled-events-v1` détaillant les champs obligatoires, facultatifs et inconnus.
- validation Windows et clôture du Work Order J2 après fusion de la Pull Request `#2` sur `main` ;
- ouverture du Work Order `WO-SS-20260812-003` sur la branche `feat/j3-manual-call` depuis le commit de fusion `b2561e542b1f893ec2f15c5eaeb67a361ee551ea` ;
- périmètre J3 découpé en unités hors ligne avec un point de décision explicite avant toute URI ou requête réelle.
- contrat d’architecture de la politique réseau J3 hors ligne, de son ordre d’évaluation et de ses transitions de circuit.
- contrat de persistance J3 du snapshot brut, de ses contraintes, de sa clé de déduplication et de ses limites de sécurité.
- contrat de transport J3 simulé, de sa frontière loopback et de sa composition avec les politiques.
- contrat de confirmation manuelle J3, de ses transitions sûres et de ses protections Web locales.
- matrice d’architecture des politiques J3 d’arrêt, d’incident, de conservation du brut et d’absence de retry.
- qualification Windows J3 du parcours opérateur local, de l’arrêt global et des politiques simulées,
  avec déclaration distincte de l’absence d’appel réel et de preuve de sortie fournisseur.
- amendement du Work Order J3 avec le périmètre cinq pages, la source
  `OBSERVATION_MANUELLE_DANS_LE_NAVIGATEUR`, la décision propriétaire et l’interdiction d’exécuter
  un appel pendant l’implémentation ;
- contrat d’architecture et procédure opérateur du chemin fournisseur J3 borné.
- rapport Windows de qualification pré-exécution du chemin cinq pages, couvrant la liaison de la
  configuration locale, l’injection Spring, le parcours opérateur jusqu’à `CONFIRMED_READY` et la
  disponibilité du bouton final sans l’exécuter.
- contrat et procédure de collecte de la preuve minimisée après l’unique lot réel, sans copie du
  payload brut et avec réapplication automatique de l’arrêt global.
- rapport terminal de la qualification réelle cinq pages, limité à la page 1 par l’arrêt sûr sur
  incompatibilité, puis diagnostic structurel hors ligne du snapshot local conservé.
- contrat d’architecture et procédure Windows de la reprise explicite à la page 2, sans appel réel
  pendant l’implémentation ni les tests.
- diagnostic et contrat d’adaptation hors ligne du schéma qualifié de la page 2, avec frontière
  explicite interdisant la reprise à la page 3 sans nouvelle décision.
- contrat d’architecture et procédure Windows de la reprise explicitement autorisée à la page 3,
  sans appel réel pendant l’implémentation ou les tests.
- contrat d’architecture et procédure opérateur de la collecte manuelle répétable à pagination
  dynamique, sans appel fournisseur pendant l’implémentation ou les tests.
- rapport Windows de qualification humaine de la pagination dynamique, confirmant dix pages sur
  dix pour le `2026-08-14`, la progression `hasNextPage=true` des pages 1 à 9, la terminaison sur
  `false` en page 10, la persistance avant parsing et la réapplication du verrou terminal.
- qualification complémentaire Windows du cache et de l’inspection JSON locale, avec preuve
  PostgreSQL de non-mutation entre les deux fonctions et matrice humaine minimisée des snapshots
  historiques déjà présents.
- clôture du Work Order `WO-SS-20260812-003` au statut `VALIDATED` après qualification du chemin
  manuel, de la pagination dynamique, du cache et de l’inspection JSON locale ;
- archivage du Work Order J3 dans `docs/work_orders/completed`, sans appel fournisseur pendant la
  clôture et sans étendre l’autorisation au polling, à la production ou au VPS.

### Modifié

- tableau de bord enrichi d’un accès à l’explorateur J4, sans modifier les contrôles réseau J3 ;
- modèle de données local étendu aux observations normalisées tout en conservant une séparation
  stricte avec les octets bruts de `provider_snapshot` ;
- renommage du package Java de base de `com.geoffrey.betting.sofascorelocal` vers `com.bettingproject.sofascorelocal`.
- renommage du groupId `com.geoffrey.betting` vers `com.bettingproject` dans le pom.xml
- tableau de bord enrichi avec le compteur des dix fixtures hors ligne et l’état distinct de
  validation structurelle du schéma fournisseur.
- mode visible du verrou mis à jour vers `LOCKED_OFFLINE_J3_POLICY` sans ouvrir le transport.
- phase applicative avancée à `J3-OFFLINE-RAW-PERSISTENCE` sans modifier l’état du connecteur.
- phase applicative avancée à `J3-GUARDED-SIMULATED-TRANSPORT`, toujours sans transport fournisseur actif.
- phase applicative avancée à `J3-EXPLICIT-MANUAL-CONFIRMATION`, avec confirmation d’intention uniquement.
- phase applicative avancée à `J3-TRANSPORT-STOP-INCIDENT-POLICIES`, toujours sans transport fournisseur actif.
- phase applicative avancée à `J3-FIVE-PAGE-PROVIDER-QUALIFICATION-PATH`, avec chemin dédié
  désactivé par défaut et connecteur général toujours verrouillé.
- phase applicative avancée à `J3-MINIMIZED-PROVIDER-EVIDENCE`, sans modifier la désactivation par
  défaut du chemin fournisseur.
- phase applicative avancée à `J3-PAGE-TWO-PROVIDER-RESUME`, avec checkpoint local obligatoire et
  reprise fournisseur limitée aux pages 2 à 5.
- identité de build Maven protégée par Enforcer et par un test des métadonnées Actuator générées,
  afin d’empêcher la réapparition de `com.geoffrey.betting` depuis un dossier `target` obsolète.
- calcul SHA-256 et détection de contenu sensible mutualisés entre les fixtures hors ligne et les futures preuves brutes.
- classification JSON/HTML/OTHER mutualisée entre le corpus hors ligne et les réponses du transport simulé.
- adaptateur JDBC étendu avec une transition contrôlée de `RAW_ONLY` vers le résultat final du parseur.
- liaison explicite de `SOFASCORE_ENABLED`, `SOFASCORE_J3_QUALIFICATION_ENABLED`,
  `SOFASCORE_BASE_URL` et `SOFASCORE_ALLOWED_ENDPOINTS` vers les propriétés Spring, avec valeurs
  versionnées toujours sûres par défaut ;
- sélection explicite des constructeurs Spring de production du transport, de l’orchestrateur cinq
  pages et du contrôle manuel ;
- présentation du connecteur prête en vert avec un libellé humain et classes CSS exclusives.
- parseur `scheduled-events-v1` étendu au schéma fournisseur qualifié dont la racine contient
  `scheduled` et `hasNextPage`, tout en conservant la compatibilité du corpus J2 `events` ;
- inventaire hors ligne porté à dix fixtures et indicateur de schéma fournisseur validé après
  relecture locale réussie du snapshot qualifié, sans nouvel appel réseau.
- parseur `scheduled-events-v1` adapté à la représentation `[]` strictement vide observée pour
  `timezoneEventCount` en page 2, tout tableau non vide restant incompatible ;
- inventaire hors ligne porté à douze fixtures (`7` parsées, `4` incompatibles et `1` contenu
  inattendu) et phase applicative avancée à `J3-PAGE-TWO-SCHEMA-ADAPTATION`.
- phase applicative avancée à `J3-DYNAMIC-MANUAL-PAGINATION` ; le chemin actif n’est plus limité à
  la date qualifiée ni aux cinq pages observées le `2026-08-13`.
- phase applicative avancée à `J3-DYNAMIC-CACHE-POLICY` ; le cache frais est désormais évalué et
  reparsé avant le délai et avant tout transport du chemin réel dynamique.
- phase applicative avancée à `J3-LOCAL-RAW-JSON-INSPECTION`, sans modification de la politique
  réseau, de la persistance brute ou du cache dynamique.

### Sécurité

- maintien des trois définitions J5 `callable=false` et sans URI dans le catalogue général ; la
  voie spécialisée est désactivée par défaut, exige l'opt-in J5 et les trois familles exactes, et
  reste inaccessible si J3 ou J4 est actif ;
- aucun appel fournisseur dans Maven ou pendant l'implémentation J5 ; les tests du `RestClient`
  dédié sont interceptés localement par `MockRestServiceServer`, et la campagne humaine réelle
  demeure `NOT_RUN` avec `providerSchemaValidated=false` ;
- arrêt de la découverte J5 au premier `HTTP 403`, sans variation d'en-tête, de client, d'adresse
  ou d'identité, et maintien de toutes les fixtures à `providerSchemaValidated=false` ;
- séparation des octets de fixture et des données J5 normalisées, conservation obligatoire de la
  source, du SHA-256, du parseur et de l'heure, et refus SQL de toute mutation ;
- maintien de `EVENT_DETAILS` sans URI et `callable=false` dans le catalogue général ; seule la
  voie spéciale J4 sous-étape 1 possède un transport HTTP, limité à l'origine, au chemin et aux
  deux identifiants autorisés, sans polling, retry ou repli fournisseur ;
- protection des actions J4 par jeton de formulaire local à usage unique, et réponses de lecture
  marquées `no-store`/`noindex` ;
- maintien du verrouillage réseau pendant J2 : aucune URI d’endpoint réelle et aucun appel SofaScore réel ne sont autorisés.
- maintien du connecteur et du profil réel bloqués au démarrage de J3 ; la revue des conditions officielles impose une décision humaine préalable avant tout appel.
- dans l’unité de politique hors ligne, résultat `TRANSPORT_ELIGIBLE` explicitement sans effet :
  aucun client HTTP, aucune URI réelle et aucun appel fournisseur n’y étaient introduits.
- rejet avant persistance des payloads dépassant 5 Mio ou contenant des motifs de secret, cookie, jeton ou clé privée ; aucun octet brut n’est journalisé ou versionné.
- maintien de `payload_jsonb` à `NULL` pour les snapshots bruts afin d’éviter toute normalisation implicite avant parsing.
- maintien de `ConnectorGate`, du profil réel et de l’adaptateur fournisseur général en état
  bloqué ; le transport simulé reste strictement limité à `127.0.0.1` et le chemin J3 dédié ne peut
  être activé que par sa politique distincte.
- formulaires opérateur protégés par un jeton aléatoire lié à la session et à usage unique, avec cookie `HttpOnly` et `SameSite=Strict`.
- suppression de la phrase de confirmation après usage, expiration ou arrêt global ; aucun contenu saisi n’est journalisé.
- exceptions de transport réduites à des codes sûrs, sans URI, payload ou diagnostic interne ; aucune donnée partielle n’est persistée après un échec de lecture.
- maintien de tous les incidents en circuit `OPEN` jusqu’à un arrêt et une nouvelle activation explicites, y compris après `Retry-After`.
- arrêt de la qualification Windows avant transport tant que le point de décision réel reste incomplet ;
  aucune phrase active, jeton, URI fournisseur ou donnée brute n’est versionné comme preuve.
- activation du chemin fournisseur subordonnée à quatre propriétés concordantes, à une origine
  exacte, à l’unique famille `SCHEDULED_EVENTS`, au stockage brut actif et à l’absence de mode live ;
- validation de domaine de la date et des cinq pages, sans chemin libre, redirection, proxy,
  pagination découverte, retry ni donnée de session ;
- maintien de tous les tests Maven hors ligne et absence d’appel SofaScore pendant l’implémentation.
- qualification Windows arrêtée avant le bouton final : aucune page fournisseur demandée, aucun
  snapshot réel persisté et aucune preuve de sortie fournisseur ajoutée au dépôt.
- relecture locale des snapshots 1 et 2 sans transport ni écriture, avec confirmation que leur
  statut historique `SCHEMA_INCOMPATIBLE` reste inchangé.
- arrêt normal uniquement sur `hasNextPage=false`, rupture sûre si ce champ n’est pas un booléen,
  arrêt au premier incident, délai inter-pages minimal de trois secondes et absence de retry,
  polling ou planification dans le parcours répétable.
- un cache hit ne déclenche aucun appel fournisseur, aucune attente inter-page et aucune écriture ;
  le délai minimal reste calculé exclusivement entre deux départs fournisseur réels, y compris
  lorsqu’une page intermédiaire est résolue depuis le cache.
- l’inspection brute reste une opération PostgreSQL locale en lecture seule, soumise au jeton Web
  à usage unique ; elle refuse toute divergence d’intégrité, contenu sensible ou JSON ambigu et ne
  propose aucun téléchargement du payload.
- l’inspection d’un snapshot n’altère ni son statut historique, ni le nombre de lignes brutes, ni le
  checkpoint de cache ; un snapshot `SCHEMA_INCOMPATIBLE` inspectable reste inéligible au cache.

## [0.1.0] — 2026-08-08

### Ajouté

- dépôt Git autonome `betting-sofascore-local-lab` ;
- ADR-SS-001 et cadrage PDF de référence ;
- bootstrap Spring Boot 4.1.0 / Java 25 LTS ;
- Maven Wrapper verrouillé sur Maven 3.9.16 avec contrôle SHA-256 ;
- interface Spring MVC + Thymeleaf liée à `127.0.0.1:8087` ;
- garde de liaison locale et en-têtes de sécurité ;
- PostgreSQL 18.4 local via Docker Compose ;
- migration Flyway V1 : snapshots bruts, manifestes d’export et contrôle du connecteur ;
- catalogue logique des familles d’endpoints, sans URI ni appel possible ;
- verrou logiciel `LOCKED_OFFLINE_J1` ;
- tests unitaires hors ligne et test d’intégration PostgreSQL/Testcontainers ;
- scripts PowerShell de configuration, préflight, démarrage, arrêt et vérification ;
- documentation d’architecture, runbook et Work Orders J0/J1 et J2.

### Sécurité

- connecteur désactivé par défaut ;
- profil `sofascore-live-test` bloqué ;
- aucune URI SofaScore intégrée ;
- aucun accès au VPS ni exposition réseau non locale.
