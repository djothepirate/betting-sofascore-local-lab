# J3 - transport fournisseur manuel Playwright et socle commun

## 1. Decision et portee

`WO-SS-20260827-013` remplace les deux adaptateurs J3 fondes sur `RestClient` par un worker
Playwright JVM enfant commun. Le changement porte uniquement sur les familles deja autorisees par
les contrats J3 :

```text
SCHEDULED_EVENTS
TOURNAMENT_SCHEDULED_EVENTS
```

Les requetes restent construites depuis des valeurs metier validees. Aucune URI libre ne traverse
l'interface ou le protocole du worker. Les deux routes admissibles restent :

```text
GET /api/v1/sport/football/scheduled-tournaments/{ISO_DATE}/page/{PAGE}
GET /api/v1/unique-tournament/{UNIQUE_TOURNAMENT_ID}/scheduled-events/{ISO_DATE}
```

`WO-SS-20260827-014`, valide le `2026-08-28`, reutilise ce socle pour J4 `EVENT_DETAILS`. Il
n'alterne pas les campagnes J3 et J4 dans un meme contexte : chaque campagne conserve son worker,
son navigateur, son contexte neuf et son allowlist propre. Le contrat J4 est documente dans
`J4-PLAYWRIGHT-EVENT-DETAILS.md`.

`WO-SS-20260831-020` corrige uniquement la sequence de fermeture et de nettoyage du superviseur
commun. Le worker de production, le protocole IPC, les familles, les routes et leurs semantiques
restent inchanges. Cette correction locale a ete validee par le proprietaire sous WO-020, desormais
archive, le 2026-08-31 ; cette validation n'autorise aucun appel fournisseur ni la reprise de
WO-019.

`WO-SS-20260831-021`, valide et archive le 2026-08-31, rend le delai minimal de trois secondes
mesurable et demonstrable en loopback. Il ajoute un fence monotone conservateur dans le superviseur
parent et une observation CDP du document principal exact dans le worker. Il ne change aucune
famille, route, origine fournisseur, limite de volume, commande IPC ou semantique de persistance. Sa
qualification locale n'autorise aucun appel fournisseur et ne reprend pas WO-019, qui reste
`STOPPED` avec ses 20 tentatives gelees.

`PAGE` reste compris entre `1` et `25`, la date est une `LocalDate` rendue en ISO et l'identifiant
de tournoi unique est strictement positif. Toute autre origine, methode, route, redirection ou
sous-requete est un incident terminal. Il n'existe aucun fallback vers `RestClient`, FlareSolverr,
un second contexte ou l'import local.

Ce contrat ne constitue pas une autorisation d'appel fournisseur. La qualification technique de ce
Work Order est exclusivement loopback tant qu'un go proprietaire distinct n'a pas fixe une
campagne reelle.

## 2. Inertie par defaut

Le runtime est absent du graphe Maven standard. Playwright Java `1.62.0`, les sources du worker et
son jar executable classe ne sont ajoutes que par le profil :

```text
provider-playwright-runtime
```

La configuration Spring reste bloquee par defaut :

```text
SOFASCORE_PLAYWRIGHT_ENABLED=false
SOFASCORE_PLAYWRIGHT_WORKER_JAR=
SOFASCORE_PLAYWRIGHT_MAXIMUM_HEAP_MIB=192
SOFASCORE_PLAYWRIGHT_STARTUP_TIMEOUT=30s
SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT=10s
SOFASCORE_PLAYWRIGHT_GRACEFUL_CLOSE_TIMEOUT=5s
SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION=false
SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN=
```

Compiler avec le profil ne demarre ni worker ni Chromium. `Playwright.create()` vit uniquement
dans le point d'entree du worker enfant ; il n'est execute ni par un constructeur Spring, ni au
demarrage normal, ni par un scheduler, ni par les tests standards. Un worker n'est cree qu'apres
un claim operateur valide, un cache miss et l'ouverture paresseuse de la campagne fournisseur.

Les imports JSON locaux J3 et tournoi restent hors du graphe Playwright : ils n'ouvrent pas de
campagne, ne consultent pas le cache fournisseur et n'executent aucun transport.

## 3. Frontiere de processus et IPC

`ChildJvmPlaywrightProviderSupervisor` possede au plus un worker. Il :

1. valide le jar du worker et l'allowlist de la campagne ;
2. ouvre un serveur IPC uniquement sur `127.0.0.1` et un port ephemere ;
3. genere un secret aleatoire a usage de la liaison parent-enfant ;
4. lance la JVM avec une limite de tas et un environnement herite borne ;
5. authentifie la poignee de main avant toute commande ;
6. attribue le PID, l'instant de creation et les descendants a l'unique campagne ;
7. detruit cette arborescence exacte sur tout chemin terminal.

Le payload n'apparait jamais dans les arguments du processus, stdout, stderr, une exception ou un
fichier runtime. Le protocole binaire transporte seulement l'endpoint enumere, la date, la page ou
l'identifiant numerique, le timeout, puis le statut, le `Content-Type` borne et les octets. La taille
maximale reste celle de `RawPayloadEvidence`, soit 5 Mio. Les buffers temporaires du parent et du
worker sont effaces apres capture.

Le worker n'ecoute pas sur le LAN. Le mode de qualification peut substituer une origine uniquement
si elle est exactement de la forme `http://127.0.0.1:<port>`, sans chemin, user-info, query ni
fragment. WO-030 rend explicite l'intervalle ferme `[1, 65535]` dans la configuration Spring
parente et dans la validation defensive du worker enfant. Les ports limites sont verifies hors
ligne, sans connexion ; cette correction n'ajoute aucune origine, route ou autorisation reseau.

## 4. Contexte ephemere

Chaque campagne demarre un worker neuf, son Chromium headless et un `BrowserContext` non persistant
neuf. Les pages successives d'une pagination utilisent ce meme contexte de campagne afin de
conserver l'atomicite fonctionnelle, puis le contexte entier est ferme. Une campagne suivante ne
reutilise ni worker, ni navigateur, ni contexte.

Les options imposees sont :

- JavaScript desactive ;
- service workers bloques ;
- telechargements refuses ;
- cookies effaces avant et apres chaque GET ;
- popups, WebSockets et routes secondaires avortes ;
- toute reponse portant une chaine `redirectedFrom` est rejetee au stade CDP avant lecture du
  corps, et aucune commande n'est reexecutee ;
- aucun proxy, profil Chrome, extension, User-Agent personnalise, cookie injecte ou `storageState` ;
- aucun HAR, trace, video, capture ou telechargement conserve.

Le teardown d'une navigation timeout ferme d'abord la page, ce qui annule la navigation bloquee,
puis desactive et detache les sessions CDP. L'ordre inverse initialement teste sous WO-021 executait
`Fetch.disable`, `Network.disable` et les detachments synchrones avant `page.close()` : la trame
`TIMEOUT` etait retardee au-dela du canal IPC, puis le parent classait `PROTOCOL_ERROR` et la cloture
`RUNTIME_FAILURE`. Le nouvel ordre passe le test cible `1/1` en `5,323 s` puis les `14/14` tests
Chromium en `101,5 s`, sans modifier `clearCookies()` ni les bornes de nettoyage WO-020.

La reponse est issue uniquement du statut, des en-tetes bornes et de `Response.body()`. Le DOM,
`page.content()` et `Response.text()` ne servent jamais a reconstruire le brut.

## 5. Campagne, pagination et coordination

`ManualProviderRequestCoordinator` fournit une lease exclusive couvrant toute la campagne, pas
seulement un GET. Elle empeche J4 ou J5 de s'intercaler entre deux pages J3. Sa temporisation utilise
une horloge monotone, relit cette horloge apres chaque reveil et reattend tant que le minimum
configure n'est pas atteint. Les pages servies par le cache ne demarrent pas Playwright et
n'ajoutent pas d'attente artificielle.

La garantie on-wire ajoutee par WO-021 appartient au superviseur parent. Un
`ProviderNetworkStartDelayGate` commun a sa duree de vie :

1. laisse passer le premier dispatch worker sans attente artificielle ;
2. enregistre en temps monotone la fin de chaque dispatch ayant fourni une reponse exploitable ;
3. avant le dispatch suivant, attend le minimum configure entier apres cette fin, par tranches d'au
   plus `20 ms`, avec relecture reelle de l'horloge et verification de l'arret ;
4. reste actif lors d'un changement de campagne, worker, navigateur ou contexte ;
5. se verrouille si l'horloge recule, si une pause ne progresse pas ou si la preuve temporelle de la
   reponse precedente est perdue ;
6. traite une interruption avant ou pendant l'attente comme une perte de preuve, conserve le statut
   d'interruption et refuse d'ecrire la commande `GET`.

Cette borne est volontairement conservatrice : le depart reseau precedent et son arrivee loopback
precedent necessairement la fin de lecture de sa reponse par le parent. Attendre encore trois
secondes a partir de cette fin demontre donc au moins trois secondes entre deux departs et entre deux
arrivees, sans ajouter de trame IPC.

Dans le worker, `ProviderMainDocumentNetworkObservation` ecoute `Network.requestWillBeSent`,
`Network.requestServedFromCache` et `Network.responseReceived`. Seul un `GET` de type `Document`,
dans la frame principale, vers l'URI exacte admise et avec un unique `requestId` correle peut fournir
`requestedAt`. Redirection, cache disque, prefetch, service worker, seconde observation ou reponse
incoherente invalident la preuve. Les URI restent confinees au worker et ne sont pas journalisees.

Pour `SCHEDULED_EVENTS`, le service ouvre le worker au premier cache miss, le reutilise jusqu'au
terminal `hasNextPage=false`, a la page 25 ou au premier incident, puis ferme campagne et lease en
`finally`. Pour `TOURNAMENT_SCHEDULED_EVENTS`, une confirmation distincte autorise au plus un GET
et une campagne distincte.

## 6. Persistance raw-first et HTTP 404

Le statut, le type de contenu et les octets exacts entrent dans la chaine de persistance existante
avant tout parsing. Un statut `2xx` suit les parseurs J3 existants. Un statut `404` :

1. conserve le brut, sa taille et son SHA-256 ;
2. classe le snapshot `ENDPOINT_UNAVAILABLE` ;
3. verrouille l'intention avec le motif homonyme ;
4. ne lance aucun parseur, retry ou appel suivant.

Le `404` d'une page paginee est terminal : aucune page suivante n'est deduite. Les `403`, `429`,
autres non-`2xx`, timeout, redirection, HTML ou challenge, route inattendue, contenu sensible et
corps trop grand sont egalement terminaux, sans repli automatique.

## 7. Arret cible

Les actions d'arret J3 signalent le superviseur avant de verrouiller le controle metier. Le worker
et ses descendants restent identifies exactement par PID et instant de creation. Aucun processus
n'est tue par nom.

La fermeture normale et l'arret operateur sont deux modes explicites :

- en mode gracieux, le parent envoie `CLOSE`; le worker ferme son runtime, emet `CLOSED`, puis
  attend l'EOF parent. Apres `CLOSED`, le superviseur capture un nouvel inventaire de l'arbre avant
  de faire `shutdownOutput()`. Le worker peut alors sortir naturellement sans perdre l'identite
  d'un descendant reparente ;
- le superviseur laisse `250 ms` a cette sortie naturelle, avant les replis conserves a `1 s` pour
  le signal souple, `2 s` pour l'annulation operateur et `5 s` pour le nettoyage total ;
- le timeout gracieux configure a `5 s` utilise le budget de nettoyage restant et n'est pas
  plafonne par la borne d'annulation operateur de `2 s` ;
- un `ReentrantLock` et des attentes bornees de `20 ms` permettent a une demande d'arret operateur
  de preempter une fermeture gracieuse en cours ;
- l'instant de la premiere demande d'arret est immuable et reste l'origine des bornes operateur,
  meme si un inventaire ou un handshake etait deja en cours ;
- un nettoyage echoue avant toute mutation peut etre retente avec un nouveau budget. Apres une
  mutation, l'echec reste terminal et la lease demeure fail-closed.

Les bornes du contrat sont :

```text
NATURAL_PROCESS_EXIT_MAX=250ms
SOFT_PROCESS_TERMINATION_MAX=1s
STOP_ACKNOWLEDGEMENT_MAX=500ms
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
RESIDUAL_OWNED_PROCESS_COUNT=0
```

Un arret qui gagne la course avec une reponse ou un parseur produit un terminal
`OPERATOR_STOP`. La fermeture idempotente reste appliquee au demarrage Spring interrompu et au
`@PreDestroy`.

## 8. Profils et qualification locale

L'installation explicite de Chromium utilise un cache dedie ignore par Git :

```powershell
pwsh -NoProfile -File .\scripts\Install-J3PlaywrightRuntime.ps1
```

Le script active seulement `provider-playwright-runtime`, place Chromium sous
`.tmp/provider-playwright-browsers` et publie `PROVIDER_ACCESS_PERFORMED=NO`.

La qualification locale execute le vrai worker et Chromium contre un serveur ephemere lie a
`127.0.0.1` :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
```

Elle active ensemble `provider-playwright-runtime` et
`provider-playwright-local-qualification`. Elle verifie les octets, statuts et types de contenu,
le `404` terminal, l'absence de fuite sensible et le nettoyage de l'arbre de processus. Elle ne
contacte pas SofaScore et ne vaut ni qualification humaine ni autorisation de campagne reelle.

La qualification WO-021 ajoute `ProviderPlaywrightWorkerNetworkObservationTest` aux suites worker.
Chacun des scripts J3, J4 et J5 exige exactement `21` tests worker verts (`10` protocole, `1`
securite, `10` observation reseau) puis `14` tests Chromium loopback verts. Le script J5 verifie en
plus :

```text
J5_REQUESTED_AT_GAPS=PASS_GE_3000_MS
J5_LOOPBACK_ARRIVAL_GAPS=PASS_GE_3000000000_NS
CROSS_WORKER_REQUESTED_AT_GAP=PASS_GE_3000_MS
CROSS_WORKER_LOOPBACK_ARRIVAL_GAP=PASS_GE_3000000000_NS
STOP_DURING_DELAY_NEW_REQUEST_COUNT=0
```

Le discriminant pre-correction a ete observe rouge en `0,08 s`. Apres le correctif et la correction
du teardown timeout, le premier `Verify-Local.ps1 -WithIntegrationTests` est vert avec `941` tests
standards et `67` tests d'integration. Les suites ciblees ajoutees apres le garde d'interruption
passent `40/40` pour le superviseur et `8/8` pour le coordinateur. Le `clean verify` final apres ce
garde passe `943` tests, zero echec, zero erreur et quatre skips a `2026-08-31T09:48:10Z`. Les trois
scripts loopback sont verts, sans acces fournisseur. La validation proprietaire a place WO-021 dans
les Work Orders termines ; reseau, reprise de WO-019, nouveau go, integration et production restent
interdits.

## 9. Exclusions

- la livraison WO-013 reste qualifiee uniquement sur ses endpoints J3 ; toute extension J4/J5 du
  worker commun exige son propre Work Order, son allowlist et sa qualification loopback ;
- aucun appel au demarrage, scheduler, polling, retry ou multi-campagne ;
- aucun transport HTTP direct ou FlareSolverr de secours ;
- aucun navigateur personnel, profil persistant ou reutilisation de session ;
- aucune modification des imports JSON locaux ;
- aucune execution sur le VPS ou dependance du Betting Project principal ;
- aucun appel fournisseur sans autorisation proprietaire distincte.

La readiness technique est suivie dans
[`J3-PLAYWRIGHT-TRANSPORT-TECHNICAL-READINESS-20260827.md`](../validation/J3-PLAYWRIGHT-TRANSPORT-TECHNICAL-READINESS-20260827.md).
