# Changelog

Les évolutions notables du SofaScore Local Lab sont consignées dans ce fichier.

## [Non publié]

### J7 — Export canonique local qualifié humainement, publication en attente

- contrat JSON Draft 2020-12 v1 fermé, identifié par
  `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1`, chargé depuis le
  classpath sans résolution réseau et limité à une enveloppe déterministe `{manifest,data}` de
  5 Mio ;
- assemblage en lecture seule `REPEATABLE_READ` des cinq composants courants d'un événement,
  avec provenance complète, états `PRESENT`/`UNAVAILABLE`/`MISSING`/`EMPTY_VALID`, complétude J5
  et avertissements structurés sans lecture ni copie de `payload_raw`, puis verrou d'événement
  partagé avec les écritures d'observation, leurs lignes métier J5 et les mutations autorisées de
  snapshots pendant la validation de fraîcheur ;
- trois preuves distinctes : `dataSha256` invariant à la décision, `sourceSetSha256` recalculé
  avant validation et SHA-256 du fichier complet conservé hors enveloppe ;
- identifiants numériques positifs bornés à `Long.MAX_VALUE`, UTF-8 strict et scanner renforcé sur
  les octets, textes et clés JSON, bloquant payloads bruts, URL/URI, headers, cookies, credentials,
  JWT, clés privées, sessions, `.env`, preuves de sauvegarde et phrases secrètes ;
- cycle local `COHERENCE_CHECKED` vers une unique décision `HUMAN_VALIDATED` ou `REJECTED`, avec
  jeton de formulaire à usage unique, confirmations exactes, motif de rejet borné et
  téléchargement réservé au seul statut validé ;
- migration append-only V23 étendant `export_manifest` tout en préservant ses lignes génériques,
  avec unicités partielles, verrou de fraîcheur des sources jusque dans les quatre tables filles
  J5, refus fermé d'un parent non visible, intention terminale write-ahead, transition
  authentifiée, immutabilité et interdiction de suppression des lignes J7 ;
- fichiers exclusivement sous `sofascore.export-directory`, noms générés par le serveur,
  temporaire synchronisé puis publication atomique create-new par lien physique sur le même
  système de fichiers et refus de traversée, lien symbolique, écrasement ou altération ;
- pages HTML locales d'historique et d'aperçu, lien depuis la fiche événement, contenu échappé et
  en-têtes `no-store`/`noindex`, sans endpoint JSON général, collecte, polling, planification,
  retry réseau ou transfert vers le Betting Project ;
- amendement opérateur permettant d'enchaîner, dans une même instance locale, les campagnes
  manuelles J3, J4 phase 2 et J5 puis les parcours locaux J6/J7 : union exacte des cinq familles
  déjà approuvées, opt-ins toujours désactivés par défaut, J4 phase 1 toujours exclusif, et
  coordinateur commun sérialisant toute requête fournisseur J3/J4/J5 avec un délai minimal partagé
  de trois secondes ; la configuration locale combinée réelle démarre sur `127.0.0.1:8087` sans
  appel automatique ni nouvel endpoint fournisseur ;
- qualification opérateur du mode combiné : une première séquence a terminé J3 sur six pages et
  J4 phase 2 avant de s'arrêter sans retry sur un timeout J5 ; après redémarrage, la même instance a
  terminé J4 phase 2 sur le snapshot 315, J5 sur les snapshots 316/317/318, puis J3 sur les six
  pages 319 à 324, toujours sans activité automatique ;
- résultat de reparsage J4 clarifié pour la forme J3 `SCHEDULED_TOURNAMENT_LIST` : le nombre de
  compétitions est affiché séparément et l'écran précise que cet endpoint ne fournit aucune
  rencontre programmée, n'invente aucun match et ne crée aucune observation J4 ; la forme
  `EVENT_LIST` conserve ses compteurs de rencontres insérées ou dédupliquées ;
- documentation d'architecture, runbook opérateur et rapport de readiness J7 ajoutés ; la suite
  standard finale recense 457 tests, avec 0 échec, 0 erreur et 2 scénarios de création de liens symboliques
  ignorés faute de permission Windows ; la suite PostgreSQL/Testcontainers passe 43 tests avec
  0 échec, 0 erreur et 0 test ignoré, y compris l'upgrade V22→V23 avec corpus J4/J5/J6 préexistant,
  l'export fournisseur complet et les verrous concurrents de toutes les écritures sources, y
  compris les quatre tables filles J5 ; la revue technique passe,
- première preuve opérateur partielle réussie sur l'événement fournisseur complet `16707704` :
  candidat inspecté, décision `HUMAN_VALIDATED`, téléchargement de 34 739 octets sans erreur,
  SHA-256 `2f60b37151e81625a96ab9426f5474f4600191d102c398a4be8cfd96c5a230a9` identique au terminal,
  historique relu avec une ligne terminale identique, empreintes de données et de sources
  cohérentes, cinq provenances fournisseur `COMPLETE`, aucun avertissement ni contenu sensible et
  aucun artefact de recette versionné ;
- preuve opérateur de rejet réussie sur l'événement fournisseur ciblé `16691018` : les trois
  avertissements attendus (`EVENT_STATISTICS` indisponible, incidents et compositions partiels)
  sont visibles, le terminal de 28 767 octets est seul conservé sous l'extension `.rejected.json`,
  ses hashes de données et de sources se recalculent à l'identique, son scan sensible est vide et
  la tentative locale de téléchargement renvoie `404` avec un corps vide ;
- preuve opérateur bout en bout dans une seule instance, commencée avant minuit le 19 août et
  terminée après minuit le 20 août en heure de Paris : J3 a persisté les six pages 325 à 330, J5 a
  terminé les trois familles sur 331/332/333, J6 a comparé localement les incidents append-only,
  J4 phase 2 a créé la version courante depuis 334, puis J7 a validé et téléchargé l'export
  `696c1965-183e-43ba-ba47-4ee4d2ad5481` de l'événement `16421055` ; le fichier de 37 239 octets
  porte le SHA-256 `37784bb4187fb80de06b765d8a9cb54b36f8035ed24eee82f9d6791c5a388880`,
  cinq sources fournisseur complètes, aucun avertissement et aucun contenu sensible ; les dix
  transports de cette séquence sont exclusivement les gestes humains J3/J5/J4, tandis que J6 et
  J7 restent sans transport et que les trois campagnes réseau finissent verrouillées ;
- recette humaine obligatoire fermée : `16691018` a été recréé, validé et téléchargé sous l'export
  `af9735bc-ccfa-419e-bfea-fb6e500bc5bb` ; son fichier de 28 701 octets porte le SHA-256
  `87ed3d0e17a3150bf3a7aee1834da35e40a8dbcafecb037032cdf893ec543063` et conserve les trois
  avertissements attendus, sans constat sensible ni transport J7 ;
- rejet puis validation synthétiques fermés sur l'événement `900001` : le terminal rejeté
  `e6bd61ab-80e2-4a9d-854c-015ea37521a1` est conservé mais non téléchargeable, le terminal validé
  `563dbeb8-8660-422b-b214-e3b1b306aa6d` est téléchargé à l'identique, et les deux décisions
  conservent le même `dataSha256` et le même jeu de cinq sources `SYNTHETIC_FIXTURE` ; le fichier
  validé de 8 707 octets porte le SHA-256
  `fb637b4c87fc6a8cba230c6d4ad0928af3d52c1a793d379ae6ac9b9d1888b1f4`, avec cinq avertissements
  `SYNTHETIC_SOURCE` et zéro constat sensible ;
- le Work Order est `READY_FOR_PR_REVIEW` après réussite des deux suites Maven, de la revue
  technique et de la recette humaine ; seule une PR `CLEAN/MERGEABLE` reste requise avant
  `VALIDATED`, et la phase applicative demeure
  volontairement `J6-HISTORY-AND-GUARDED-RETENTION-VALIDATED` jusqu'à cette publication revue.

### J6 — Historique et rétention gardée

- migration Flyway V21 ajoutant une occurrence append-only pour chaque tentative de persistance
  brute future, y compris les réponses dédupliquées, et un backfill `BASELINE` prudent pour les
  snapshots antérieurs ;
- agrégation locale des cinq flux `EVENT_STATE`, `EVENT_DETAILS`, `EVENT_STATISTICS`,
  `EVENT_INCIDENTS` et `EVENT_LINEUPS`, avec ordre stable, pagination bornée, provenance complète et
  état explicite des octets bruts ;
- différences sémantiques calculées à la demande pour les champs scalaires, métriques, incidents,
  compositions, score et complétude, sans relecture du JSON brut ni mutation de l'historique ;
- classification `BASELINE`, `TECHNICAL_DUPLICATE`, `LOCAL_REPARSE`,
  `SEMANTICALLY_UNCHANGED`, `SYNTHETIC_CHANGE`, `PROVIDER_UPDATE`, `LATE_ENRICHMENT` ou
  `LATE_CORRECTION`, avec prise en compte du dernier état terminal strictement antérieur ;
- pages Thymeleaf d'historique et de comparaison sur boucle locale, réponses `no-store`, contenu
  échappé, pagination et erreurs explicites, sans endpoint JSON, export ni téléchargement brut ;
- corpus synthétique J6 idempotent ajoutant un état final et quatre changements postérieurs à cet
  état aux fixtures nominales J4/J5, classés `SYNTHETIC_CHANGE` et sans transport fournisseur ;
- migration Flyway V22 limitant la rétention aux seuls octets `payload_raw` éligibles, avec
  conservation des lignes, hashes, tailles, métadonnées, occurrences et observations normalisées,
  audit append-only et trigger bloquant toute mutation non auditée ;
- aperçu Web en lecture seule et commande non Web limitée à 500 éléments, avec plan SHA-256,
  phrase exacte, relecture transactionnelle `SERIALIZABLE` et refus sur dérive ou couverture de
  sauvegarde insuffisante ;
- scripts PowerShell de sauvegarde PostgreSQL directement chiffrée par `age`, restauration dans
  une base temporaire, comparaison des empreintes, manifeste qualifié et invocation manuelle de la
  rétention ; aucun dump clair, secret en argument, bouton de purge ou planification ;
- phase applicative d'abord avancée à `J6-HISTORY-AND-GUARDED-RETENTION-READINESS`, avec
  documentation d'architecture, runbook opérateur et rapport de readiness ;
- première phase humaine de l'interface J6 acceptée sur seize captures opérateur non versionnées :
  cinq flux, douze versions, différences sémantiques, trois comparaisons dont l'état 2 → 96,
  pagination à trois éléments sur quatre pages et aperçu de rétention sans purge Web ; le second
  import idempotent et la sauvegarde/restauration restent ouverts ;
- qualification humaine finale de l'interface J6 sur deux captures supplémentaires : second import
  à `0 ajoutée / 12 déjà présentes`, total stable, comparaison d'état 96 → 97 correctement
  `SEMANTICALLY_UNCHANGED`, puis absence de listener sur le port 8087 ;
- qualification opératoire finale de la sauvegarde/restauration chiffrée : archive `age` et
  manifeste créés hors dépôt, restauration PostgreSQL temporaire qualifiée par égalité des treize
  mesures source/restauration, couverture jusqu'au snapshot 272, contrôle d'intégrité brut sans
  échec, suppression de la base temporaire et refus d'écraser les artefacts lors d'un second
  lancement ; aucune purge de la base primaire n'a été exécutée ou autorisée ;
- contrôle visuel complémentaire sur cinq captures opérateur non versionnées du snapshot réel 32
  de l'événement `16412917` : même brut et même SHA-256 source pour les observations incidents 10
  et 12, passage de `event-incidents-v3` à `event-incidents-v4`, complétude `20/20` → `36/36`,
  classification `LOCAL_REPARSE` et 18 écarts prudents correspondant à huit retraits, huit ajouts et
  deux compteurs ; aucun appariement ambigu, appel fournisseur ou changement du brut n'est inventé ;
- J6 passe à `VALIDATED`, la phase applicative devient
  `J6-HISTORY-AND-GUARDED-RETENTION-VALIDATED` et le Work Order rejoint `completed`.

### Corrigé

- correction de l'empreinte des provenances normalisées dans le script de sauvegarde/restauration
  J6 : les trois tables d'observations utilisent désormais leur colonne réelle
  `source_payload_sha256` au lieu de `payload_sha256` ; une régression d'intégration extrait les
  requêtes du script et les exécute contre le schéma Flyway V22 afin de détecter toute nouvelle
  dérive avant la qualification opérateur ;
- retrait de la liste technique des chemins JSON manquants au-dessus des tableaux
  `EVENT_INCIDENTS` et `EVENT_LINEUPS` : les rapports de complétude, badges, compteurs, données et
  lignes des tableaux restent inchangés ; une régression MVC alimente volontairement les deux
  familles avec des chemins manquants et vérifie leur absence dans le HTML ;
- qualification humaine V13 des deux formes courantes : Cittadella — Atalanta U23 (`16691018`)
  termine trois appels avec incidents snapshot 189 / observation 100 à
  `PARTIAL · 83% · 142/171` et compositions snapshot 192 / observation 101 à `94/95`, puis
  Shanghai Shenhua — Beijing Guoan (`16851672`) termine trois appels avec incidents snapshot 195 /
  observation 103 à `COMPLETE · 81/81`, motif `Leaving field` rendu à la minute 74 et compositions
  snapshot 196 / observation 104 à `97/97` ; `PROVIDER_SCHEMA_VALIDATED=YES` dans cette portée
  bornée ;
- clôture fonctionnelle J5 sur neuf captures opérateur conservées hors dépôt : listes de chemins
  techniques absentes, tableaux incidents/compositions inchangés, motif `Leaving field` conservé,
  configuration locale reverrouillée, contrôles J4/J5 `LOCKED` après redémarrage et application
  finalement arrêtée ; le Work Order réel J5 passe à `VALIDATED` et rejoint `completed` ;
- correction versionnée `event-incidents-v13` de la valeur fournisseur
  `reason="Leaving field"` sur un carton jaune : le libellé exact rejoint le vocabulaire fermé,
  reste persisté et affiché comme motif, tandis que V12 rejette encore la même forme et que toute
  autre valeur non documentée demeure `SCHEMA_INCOMPATIBLE` ; V13 conserve sans modification la
  règle stricte V12 des séances terminales entièrement non minutées ;
- qualification hors ligne de la structure opérateur de Yongjing Cao à la minute 74, de la
  poursuite ordonnée jusqu'aux compositions après trois transports sans retry et de la fermeture
  du vocabulaire ; cette preuve hors ligne est désormais complétée par les deux campagnes humaines
  V13 consignées ci-dessus ;
- correction versionnée `event-incidents-v12` de la séance terminale entièrement non minutée
  observée dans le snapshot 189 de l'événement `16691018` : V11 rejetait exactement le marqueur
  `PEN` et quatorze `penaltyShootout` sans minute globale ni action imbriquée ; V12 conserve la
  minute absente, la persiste en `NULL` et l'affiche par `—` sans jamais la déduire de la séquence ;
- validation contextuelle stricte de cette exception temporelle : marqueur `PEN` inactif et score
  complet, marqueur `FT` ou `ET` minuté, totalité de la séance non minutée, séquences uniques et
  contiguës, scores présents et score final concordant ; les séances mixtes, isolées, lacunaires ou
  contradictoires ainsi qu'un `inGamePenalty` sans minute restent `SCHEMA_INCOMPATIBLE` ;
- régression explicite de l'absence simultanée de `reason` et `description` sur un penalty
  `missed` : elle produit `PARTIAL` sans motif inventé pour `inGamePenalty` comme pour
  `penaltyShootout`, tandis que les tuples fournis, dont `Woodwork/woodwork`, restent stricts ;
- retour du statut courant à `PROVIDER_SCHEMA_VALIDATED=NO` après la campagne `16691018` :
  statistiques snapshot 188 indisponibles, incidents snapshot 189 persistés avant rejet V11,
  verrou `FAILED_LOCKED` après deux appels, zéro retry et compositions non tentées ; le payload
  complet passe hors ligne sous V12 avec 33 incidents, quatorze tirs au but, quinze avertissements
  temporels et aucun problème de schéma ;
- qualification humaine réelle du mode combiné J4 phase 2 + J5 sur Barracas Central — Rosario
  Central (`16671566`) dans un même démarrage : J4 termine après un appel et le snapshot 183, puis
  J5 termine trois appels ordonnés sans retry avec les snapshots 184/185/186 et atteint les
  compositions à `COMPLETE · 95/95` ;
- qualification réelle du motif V11 `Off the ball foul` dans le snapshot incidents 185,
  observation append-only 96 : `event-incidents-v11` traite 18 incidents à `COMPLETE · 69/69` et
  rend le carton jaune extérieur de Facundo Mallo à la minute 77 sans modifier le libellé ; les
  onze preuves PNG restent hors dépôt et leur inventaire minimisé est consigné dans les rapports de
  validation ;
- constat d'arrêt postérieur à la campagne combinée, sans listener local ni processus Java du
  laboratoire ; le Work Order reste actif uniquement pour la remise de la configuration locale à
  l'état bloqué, sa vérification après redémarrage et l'arrêt final de l'instance de contrôle ;
- correction versionnée `event-incidents-v11` de la nouvelle valeur fournisseur
  `reason="Off the ball foul"` sur un carton : le libellé exact rejoint le vocabulaire fermé, reste
  persisté et affiché comme motif, tandis que toute autre valeur non attestée demeure
  `SCHEMA_INCOMPATIBLE` ; son sens métier est documenté comme obstruction ou faute loin du ballon ;
- première qualification humaine bornée du parseur courant V11 sur Al Tai — Al-Qadsiah : campagne
  `COMPLETED_LOCKED`, trois appels sans retry, snapshot incidents historique 179 reparsé dans
  l'observation append-only 93 à `COMPLETE · 24/24`, cinq buts `regular/from=regular` visibles, puis
  compositions snapshot 182 / observation 94 à `COMPLETE · 67/67` ; cette campagne intermédiaire
  ne contenait pas le nouveau motif `Off the ball foul`, qualifié depuis sur l'événement `16671566` ;
- correction versionnée `event-incidents-v10` des cinq buts du snapshot 179 portant le tuple
  fournisseur redondant `incidentClass="regular"` / `from="regular"` : la valeur reste dans le
  brut, elle est omise de l'origine spéciale normalisée et toute combinaison croisée ou nouvelle
  valeur inconnue reste `SCHEMA_INCOMPATIBLE` ;
- retour du statut courant à `PROVIDER_SCHEMA_VALIDATED=NO` après la campagne de l'événement
  `16251993` : statistiques snapshot 178 et observation 91 complètes, incidents snapshot 179
  conservés avant le rejet V9, verrou `FAILED_LOCKED` après exactement deux appels, zéro retry et
  compositions non tentées ; le payload opérateur complet passe hors ligne sous V10 avec sept
  incidents sur sept et `24/24` signaux ;
- correction versionnée `event-incidents-v9` du carton de banc observé dans le snapshot 162 :
  `Other reason` rejoint le vocabulaire fermé des motifs et `benchAddedTime` est accepté uniquement
  avec un `card`, un `time` technique négatif, un `benchTime` présent et aucun `addedTime`
  concurrent ; le cas attesté est normalisé à `90+9`, tandis que tout usage hors contexte et tout
  nouveau motif inconnu restent `SCHEMA_INCOMPATIBLE` ;
- retour du statut courant à `PROVIDER_SCHEMA_VALIDATED=NO` après la campagne Cardiff City —
  Wrexham : statistiques snapshot 161 compatibles, incidents snapshot 162 conservés avant le rejet
  V8, verrou `FAILED_LOCKED` après exactement deux appels, zéro retry et compositions non tentées ;
  le payload complet passe hors ligne sous V9 avec 20 incidents sur 20 ;
- restauration bornée de `PROVIDER_SCHEMA_VALIDATED=YES` après le retest humain V9 de Cardiff City
  — Wrexham : `COMPLETED_LOCKED`, trois appels sans retry, snapshot incidents 162 reparsé dans
  l'observation 77 à `COMPLETE · 100%`, carton `Other reason` rendu à `90+9`, puis compositions
  snapshot 165, observation 78, `event-lineups-v2`, `85/85` ;
- correction versionnée `event-incidents-v8` du marqueur terminal de séance de tirs au but : la
  sentinelle `time=999` n'est acceptée que pour la combinaison exacte `period/PEN/penalties`,
  inactive, avec score complet et tirs au but minutés ; elle reste dans le brut tandis que la
  minute normalisée devient la dernière minute effective de la séance ;
- confirmation par rejeu hors ligne que les neuf `penaltyShootout` sans minute globale sont déjà
  normalisés depuis leur première action imbriquée et que les trois cartons sans `reason` restent
  valides, persistés sans motif et affichés par `—` ;
- prise en charge bornée du champ fournisseur `from="shot"` sur un but `regular`, conservé dans le
  brut mais omis de l'origine spéciale normalisée ; cette variante connexe empêchait encore le
  payload comportant les cartons sans motif d'être accepté intégralement ;
- ajout du résultat cohérent `incidentClass="missed"`, `description="Woodwork"`,
  `reason="woodwork"` pour `inGamePenalty` et `penaltyShootout`, avec rejet maintenu des tuples
  incomplets, croisés ou inconnus ;
- remplacement de la règle incidents V5 volontairement étroite par le contrat métier V6 couvrant
  les huit types de faits de jeu documentés ; les métadonnées d'affichage facultatives manquantes
  produisent désormais `PARTIAL`, tandis que les contradictions atomiques, types inconnus et
  valeurs hors vocabulaire restent bloquants en `SCHEMA_INCOMPATIBLE` ;
- clarification du temps des cartons de banc : un `time` technique négatif exige `benchTime`, qui
  devient la minute métier ; à l'inverse, un carton portant déjà une minute non négative ne peut pas
  aussi porter `benchTime` ; le brut, notamment `time=-5`, reste inchangé ;
- correction de la fiche métier incidents : sous-sections `10.2` et `10.3` du chapitre 10,
  libellés entrant/sortant, vocabulaire `Forward`, distinction brute `owngoal` / canonique
  `ownGoal`, règles VAR et correspondance des résultats de séance de tirs au but ;
- correction bornée du carton de banc réel qui arrêtait la seconde campagne J5 après les
  statistiques : `event-incidents-v5` traite uniquement un `card` portant le marqueur exact
  `time=-5` avec un `benchTime` valide, conserve le brut, normalise la minute depuis `benchTime` et
  retient la classe ainsi que le motif ; toute autre valeur négative reste incompatible dans
  l'attente de la fiche de règles de gestion des incidents football ;
- réarmement explicite de J5 après une campagne réussie : `COMPLETED_LOCKED` continue d'interdire
  tout rejeu de l'ancien claim, mais permet une nouvelle préparation locale avec nouvel identifiant
  de requête, nouvelle phrase et nouvel acquittement ; `FAILED_LOCKED`, `STOPPED_LOCKED` et
  `EXPIRED_LOCKED` restent verrouillés jusqu'au redémarrage ;
- poursuite de la campagne J5 lorsqu'une réponse brute identique est dédupliquée vers une preuve
  historique déjà classée : la classification V1/V2 reste immuable, le résultat du parseur courant
  est porté par une nouvelle observation append-only et `EVENT_LINEUPS` n'est plus bloqué par un
  faux `RAW_CLASSIFICATION_ERROR` ;
- conservation de l'identité des joueurs entrant et sortant sur tous les incidents
  `substitution` : `event-incidents-v4` lit séparément `playerIn` et `playerOut`, conserve leurs
  identifiants et noms, mesure toute absence comme `PARTIAL` et les affiche dans deux colonnes
  dédiées sans synthétiser de joueur ;
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

- migration Flyway V20 append-only autorisant `event-incidents-v13`, avec preuve V19 → V20 sans
  réécriture d'une observation V12 ni de sa minute nulle ; un carton `Leaving field` est ensuite
  persisté et relu avec son motif exact ;
- rapport minimisé `J5-OBSERVED-V13-LEAVING-FIELD-CARD-REASON-20260818.md`, règle métier,
  comparaison historique V12, poursuite de service, upgrade PostgreSQL et bilan courant de 369
  tests standards plus 35 tests d'intégration réussis sans appel fournisseur ;
- migration Flyway V19 append-only autorisant `event-incidents-v12`, rendant la minute nullable
  sous une contrainte réservée aux seuls tirs au but et marqueurs `PEN`, avec preuve V18 → V19 sans
  réécriture de l'historique V11 et round-trip PostgreSQL d'une minute absente ;
- rapport minimisé `J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md`, inventaire SHA-256 des trois
  captures conservées hors dépôt, diagnostic V11 des quinze chemins temporels, rejeu V12 de la
  pièce opérateur, 366 tests standards et 34 tests PostgreSQL/Testcontainers réussis, puis nouvelle
  séquence de retest humain avant reverrouillage ;
- mode de qualification combiné J4 phase 2 + J5 dans un même processus : union exacte des quatre
  familles, J3 et J4 phase 1 toujours exclus, contrôles terminaux indépendants et preuve que
  l'arrêt global J4 n'empêche pas une préparation J5 sans redémarrage ;
- coordinateur de requêtes partagé par les services réels J4/J5, maintenant une seule section HTTP
  active et le délai minimal de trois secondes entre deux départs, y compris entre les jalons ;
- rapport `J4-J5-COMBINED-QUALIFICATION-SESSION-20260818.md`, configuration temporaire bornée,
  procédure humaine en un seul démarrage et bilan de 358 tests standards plus 32 tests
  d'intégration réussis sans appel fournisseur ;
- migration Flyway V18 append-only autorisant `event-incidents-v11`, avec preuve d'upgrade V17 →
  V18 sans réécriture d'une observation ni d'un incident V10 ;
- rapport minimisé `J5-REAL-V11-OFF-BALL-CARD-REASON-20260818.md`, régression sur la structure JSON
  fournie par l'opérateur, comparaison historique V10, poursuite de service jusqu'aux compositions
  et bilan courant de 358 tests standards plus 32 tests d'intégration réussis sans appel
  fournisseur ;
- migration Flyway V17 append-only autorisant `event-incidents-v10`, avec preuve d'upgrade V16 →
  V17 sans réécriture d'une observation ni d'un incident V9 ;
- rapport minimisé `J5-REAL-V10-REGULAR-GOAL-ORIGIN-20260818.md`, trois captures de preuve
  conservées hors dépôt, rejeu exact de la transcription opérateur hors réseau, régressions V10 et
  bilan de 348 tests standards plus 31 tests d'intégration réussis ; ce retest a ensuite été
  remplacé par la qualification humaine du parseur courant V11 ;
- qualification fonctionnelle humaine V9 à partir de cinq captures conservées hors dépôt :
  préparation sans transport, campagne à trois appels, observation incidents append-only et
  compositions complètes ; le reverrouillage, alors encore attendu, est couvert par la preuve de
  clôture finale J5 ;
- migration Flyway V16 append-only autorisant `event-incidents-v9`, avec preuve d'upgrade V15 →
  V16 sans réécriture d'une observation V8 et persistance du carton à la minute `90+9` avec le
  motif `Other reason` ;
- rapport minimisé `J5-REAL-V9-BENCH-CARD-OTHER-REASON-20260818.md`, tests de contrat V9, rejeu
  opérateur complet hors dépôt et bilan de 344 tests standard plus 30 tests d'intégration réussis ;
- qualification fonctionnelle humaine V8 à partir d'un lot externe de 48 captures, conservé hors
  dépôt : trois campagnes `COMPLETED_LOCKED` le 2026-08-18, dont le rejeu réussi par V8 du snapshot
  139 contenant neuf tirs au but et `PEN/time=999`, les cartons sans motif du snapshot 144 et le
  `inGamePenalty/missed/Woodwork` du snapshot 150 ; les deux campagnes entièrement disponibles
  qualifient de façon bornée les trois schémas courants ; l'arrêt local est confirmé par l'absence
  de listener 8087 et de processus Java du laboratoire, tandis que le reverrouillage de la
  configuration reste à confirmer avant archivage ;
- migration Flyway V15 append-only autorisant la provenance `event-incidents-v8`, avec preuve
  d'upgrade V14 → V15 sans réécriture d'une observation V7 ni modification de ses incidents ;
- régressions hors ligne V8 sur les deux payloads opérateur conservés hors dépôt : 36 incidents
  dont neuf tirs au but et un marqueur `PEN` normalisé à 146, puis 16 incidents dont trois cartons
  sans motif et trois buts ordinaires `from="shot"` ;
- rapport `J5-REAL-V8-PENALTY-INCIDENT-VARIANTS-20260818.md` et extension de la fiche métier aux
  résultats sur le poteau ou la barre transversale pour les deux familles de pénalty ;
- parseur `event-incidents-v7` limité à la classe fournisseur de substitution sur blessure
  `incidentClass="injury"`, conservation de `injury`, du joueur entrant et du joueur sortant,
  rejet de la contradiction explicite `injury=false`, et régression prouvant la poursuite vers
  `EVENT_LINEUPS` sans retry ;
- migration Flyway V14 append-only autorisant la provenance V7, avec preuve d'upgrade V13 → V14
  sans réécriture d'une observation V6 ni modification de ses incidents ;
- preuve minimisée Arsenal — Manchester City : statistiques snapshot 70 compatibles, brut
  incidents snapshot 71 persisté avant le rejet V6, cause isolée sur le remplacement sur blessure
  de Jérémy Doku par Jack Grealish parmi 23 incidents, puis validation hors ligne intégrale par V7 ;
- qualification humaine V6 de Lens — Paris Saint-Germain : marqueurs `HT`/`FT`, carton de
  l'entraîneur, temps additionnels de trois et cinq minutes et participants visibles ; campagne
  terminée avec les snapshots dédupliqués 64/65 et le snapshot compositions 69 ;
- rapport de validation `J5-REAL-V6-LENS-PASS-AND-V7-INJURY-SUBSTITUTION-CORRECTION-20260817.md`
  réunissant la preuve réelle V6, la cause exacte du rejet Arsenal — Manchester City, la
  validation hors ligne V7 des 23 incidents sur 23, la poursuite vers les compositions, les 330
  tests standards et les 28 tests d'intégration, tous réussis sans appel fournisseur ;
- fiche canonique `docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md`, parseur
  `event-incidents-v6` et matrice hors ligne couvrant `period`, `substitution`, `goal`, `card`,
  `injuryTime`, `varDecision`, `inGamePenalty` et `penaltyShootout` ;
- migration Flyway V13 append-only conservant texte de période, blessure, passeur, origine du but,
  temps additionnel, décision VAR, description et ordre de séance de tirs au but, sans réécriture
  des observations V1–V12 ;
- affichage J5 étendu aux colonnes passeur, détail et ordre de séance, avec participants et motifs
  issus exclusivement des observations normalisées ;
- preuve minimisée de Paris Saint-Germain — Lens : campagne réussie en première mi-temps sur les
  snapshots 61/62/63, puis rejeu manuel réussi à la fin du match sur les snapshots 64/65/66 ;
- preuve minimisée du retest réel du réarmement : une première campagne sur `16391135` a terminé
  ses trois appels, puis une seconde campagne distincte sur `16483632` a été préparée et exécutée
  dans la même instance ; les statistiques du snapshot 59 ont été normalisées avant l'arrêt strict
  de V4 sur le snapshot incidents 60, sans appel compositions ni retry ;
- migration Flyway V12 append-only ajoutant `incident_class` et `reason`, autorisant
  `event-incidents-v5` et qualifiant les upgrades V1/V11 → V12 sans réécriture de l'historique ;
- tests hors ligne du carton de banc (`benchTime=58`, classe jaune, motif `Argument`), de la
  persistance PostgreSQL, du rendu MVC et de la poursuite ordonnée vers les compositions avec V5 ;
- preuve minimisée du retest réel V4 sur `16412917` : statistiques HTTP `404` indisponibles,
  incidents V4 `36/36` avec joueurs entrant/sortant, compositions V2 `85/85`, exactement trois
  appels ordonnés sans retry, puis régression hors ligne du nouveau cycle après succès ;
- migration Flyway V11 append-only ajoutant les couples identifiant/nom des joueurs entrant et
  sortant, autorisant `event-incidents-v4` et qualifiant l'upgrade V10 → V11 sans réécriture de
  l'historique ;
- régression hors ligne du retest réel : statistiques 404 dédupliquées, incidents 200
  dédupliqués et reparsés par V4, puis unique appel compositions, ainsi qu'une preuve PostgreSQL
  que le snapshot V2 conserve `SCHEMA_INCOMPATIBLE` pendant qu'une observation V4 est ajoutée ;
- migration Flyway V10 append-only autorisant la provenance `event-incidents-v3` sans réécrire les
  observations historiques V1/V2, avec test d'upgrade V9 → V10 sur PostgreSQL 18.4 ;
- fixture synthétique minimale et tests de régression du marqueur de période fournisseur, ainsi
  que couverture du parcours ordonné prouvant que les compositions sont atteintes après parsing
  compatible des incidents ;
- tests hors ligne du scénario mixte « statistiques HTTP `404`, incidents et compositions `2xx` »,
  de l'affichage d'indisponibilité, de la persistance PostgreSQL `UNAVAILABLE` et de l'upgrade
  V8 → V9 d'un snapshot historique mal classé ;
- voie de qualification réelle J5, désactivée par défaut et exclusive de J3, mais partageable avec
  J4 phase 2 sous l'union exacte des quatre endpoints,
  limitée à l'origine exacte `https://www.sofascore.com` et aux trois endpoints logiques
  `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` ;
- contrôle humain J5 avec préparation sans réseau, phrase exacte valable cinq minutes,
  acquittement, claim immuable et trois appels séquentiels maximum ; le succès verrouille le claim
  terminé tout en autorisant une campagne ultérieure distincte, tandis qu'incident, expiration ou
  arrêt verrouillent le processus ;
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
  voie spécialisée est désactivée par défaut, exige l'opt-in J5 et l'ensemble exact des familles
  actives, reste inaccessible si J3 ou J4 phase 1 est actif et n'accepte J4 que dans sa phase 2 ;
- sérialisation commune des transports réels J4/J5 avec concurrence maximale de un, verrou détenu
  pendant l'échange et délai minimal partagé, sans polling, automatisation ni retry ajouté ;
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
