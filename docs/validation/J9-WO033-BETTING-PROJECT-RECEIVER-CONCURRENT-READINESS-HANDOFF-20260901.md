# J9 / WO-033 — Handoff de readiness du receiver Betting Project concurrent

- **Version :** `1.2`
- **Préparé le :** 2026-09-01
- **Révisé le :** `2026-09-01T18:13:34.3075675Z`
- **Statut :** `PREPARED_NOT_IMPLEMENTED`
- **Dépôt émetteur :** `betting-sofascore-local-lab`
- **Dépôt receiver :** `betting-project`
- **Topologie :** `OPTIONAL_LOCAL_PUSH`
- **Contrat :** `J7_OPTIONAL_LOCAL_PUSH` v1.0

```text
HANDOFF_STATUS=PREPARED_NOT_IMPLEMENTED
BETTING_PROJECT_RECEIVER_REQUIRED=YES
BETTING_PROJECT_RECEIVER_IMPLEMENTED=NO
CONCURRENT_RUNTIME_REQUIRED=YES
CONCURRENT_RUNTIME_QUALIFIED=NO
LAB_RUNTIME_ADDRESS=127.0.0.1
LAB_RUNTIME_PORT=8087
RECEIVER_RUNTIME_ADDRESS=127.0.0.1_FOR_LOCAL_QUALIFICATION
RECEIVER_RUNTIME_PORT=8444_PROPOSED_TBD_BY_RECEIVER_WORK_ORDER
RECEIVER_DATABASE_PORT=5433_PROPOSED_TBD_BY_RECEIVER_WORK_ORDER
J7_DELIVERY_TRIGGER=EXPLICIT_OPERATOR_ACTIVATION_AFTER_HUMAN_VALIDATED
J7_DELIVERY_ACTION=SUBMIT_ONE_REQUEST_TO_BETTING_PROJECT_RECEIVER
J7_DELIVERY_TRIGGERS_PROVIDER_ACQUISITION=NO
E2E_STAGE_1=WINDOWS_LOCAL_LAB_TO_WINDOWS_BETTING_PROJECT
E2E_STAGE_2=WINDOWS_LOCAL_LAB_TO_PRODUCTION_VPS_BETTING_PROJECT
E2E_STAGE_3=POTENTIAL_PRODUCTION_VPS_LOCAL_LAB_TO_SAME_VPS_BETTING_PROJECT
OWNER_DECLARED_PRODUCTION_VPS_IPV4=51.255.167.32
OWNER_DECLARED_PRODUCTION_VPS_STATE=AVAILABLE_UNCONFIGURED_NOT_QUALIFIED
VPS_CONNECTION_OR_PROBE_PERFORMED=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
RECEIVER_VPS_DEPLOYMENT_AUTHORIZED=NO
LOCAL_LAB_VPS_DEPLOYMENT_AUTHORIZED=NO
PROVIDER_VPS_ACQUISITION_AUTHORIZED=NO
PRODUCTION_INGESTION_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Ce handoff établit les prérequis d'un futur test end-to-end avec les deux vraies applications. Il
ne crée aucun endpoint et n'autorise aucun démarrage, socket, certificat, livraison ou appel
fournisseur.

L'IPv4 du VPS est une coordonnée fournie par le propriétaire, pas une cible technique déjà
acceptée. Aucun contrôle DNS, routage, port, système, compte, firewall, patch, certificat,
sauvegarde ou accès administratif n'a été effectué sous l'ancien identifiant WO-031, désormais
renuméroté WO-033.

## 1. Résultat attendu

Le test end-to-end ne devra pas utiliser le receiver synthétique de WO-027 comme substitut au
Betting Project. Le processus réel du monolithe `betting-project` devra exposer le receiver qualifié
pendant que le processus réel `betting-sofascore-local-lab` est lui aussi démarré.

```text
export J7 déjà HUMAN_VALIDATED
            |
            v
betting-sofascore-local-lab                     betting-project
processus Java réel                             processus Java réel
127.0.0.1:8087                                  127.0.0.1:<port distinct>
sender explicite, concurrence 1   -- mTLS -->   receiver dédié, idempotent
ledger de livraison séparé        <-- ACK ---   effet unique + audit minimisé
            |
            +-- ne déclenche jamais J3/J4/J5 ou Playwright
```

La coexistence temporelle des processus est nécessaire à l'échange HTTP. Elle ne doit créer ni
dépendance critique, ni démarrage coordonné d'une acquisition, ni chemin de retour du receiver vers
SofaScore.

### 1.1 Sémantique de l'activation J7

L'activation J7 est l'action manuelle de livraison prévue par ADR-SS-003 v0.1. Elle s'applique à un
export déjà `HUMAN_VALIDATED`, fige son identité et ses hashes, réclame son entrée de ledger puis
soumet exactement une requête au receiver. Elle n'est ni le passage automatique à
`HUMAN_VALIDATED`, ni le téléchargement J7, ni une action susceptible de lancer J3/J4/J5 ou
Playwright. Un déclenchement événementiel automatique exigerait une révision d'ADR-SS-003.

### 1.2 Trajectoire propriétaire en trois paliers

| Palier | Local Lab | Betting Project | Flux | État courant |
|---|---|---|---|---|
| 1 — Windows/Windows | poste Windows, loopback | même poste Windows, processus et base distincts | HTTPS/mTLS loopback | `OWNER_REQUIRED_NOT_IMPLEMENTED` ; synthétique éligible à une autorisation locale séparée |
| 2 — Windows/VPS | poste Windows ; acquisition locale seulement | VPS de production déclaré `51.255.167.32` | HTTPS/mTLS sortant Windows vers ingress VPS à définir | `OWNER_REQUIRED_NOT_AUTHORIZED` ; compatible avec la direction `OPTIONAL_LOCAL_PUSH`, sans autoriser le VPS |
| 3 — VPS/VPS éventuel | VPS de production ; acquisition hébergée potentielle | même VPS, isolation obligatoire | réseau interne hôte à concevoir | `BLOCKED_BY_CURRENT_GOVERNANCE` ; exige permission et révisions ADR/règles |

Les trois lignes décrivent des objectifs successifs, pas trois autorisations. Le palier 1 prouve le
contrat et les processus ; le palier 2 ajoute l'infrastructure et la frontière Internet ; le palier
3 change en plus le lieu d'acquisition et de conservation brute. La réussite d'une ligne ne permet
pas d'exécuter la suivante.

## 2. État constaté des deux dépôts

### 2.1 SofaScore Local Lab

| Dimension | État versionné actuel | Conséquence pour le test futur |
|---|---|---|
| Application | Spring Boot sur `127.0.0.1:8087` | port réservé au laboratoire ; le receiver doit en choisir un autre |
| PostgreSQL | défaut `127.0.0.1:5432`, base `sofascore_local_lab` | le receiver doit utiliser un autre port hôte et une autre base |
| Compose | projet `betting-sofascore-local-lab`, conteneur et volume dédiés | conserver cette identité sans la réutiliser côté receiver |
| Sender | désactivé par défaut | activation future uniquement sous Work Order et décision exacts |
| URI receiver | vide | aucune cible réelle n'est actuellement définie |
| Permission | `NOT_EVIDENCED` | toute livraison de données dérivées de SofaScore est refusée avant socket ; un E2E synthétique des vrais processus reste une qualification distincte |
| mTLS | obligatoire ; cible Windows `Windows-MY` pour l'identité client | profil réel et clé native non exportable encore à qualifier |
| Retry/concurrence | zéro retry automatique, concurrence `1` | invariants à préserver pendant le test inter-processus |

La livraison réelle n'est pas déverrouillable par de simples variables d'environnement sur cette
base : `optional-integration.enabled=false`, `remote-delivery-authorized=false` et
`official-permission-status=NOT_EVIDENCED` sont complétés par des barrières de code qui refusent la
livraison réelle. Le transport HTTP n'expose actuellement qu'une fabrique de loopback synthétique,
sans bean Spring ni fabrique de cible réelle. Un futur Work Order sender est donc requis même après
l'implémentation du receiver.

### 2.2 Betting Project

| Dimension | État versionné actuel | Travail receiver nécessaire |
|---|---|---|
| Architecture | monolithe modulaire ; aucun second service autorisé dans le dépôt | implémenter le receiver comme adaptateur du monolithe, pas comme microservice |
| Profils | `replay`, `control-api`, `batch-worker` | le receiver HTTP devra être qualifié sous un profil autorisé, vraisemblablement `control-api` |
| Profil par défaut | `replay`, sans serveur web ni datasource | ne peut pas servir de receiver tel quel |
| Serveur `control-api` | port Spring par défaut si non configuré ; adresse non bornée dans le fichier courant | imposer explicitement loopback et un port non conflictuel pour la qualification locale |
| PostgreSQL | défaut hôte `5432`, base `betting` | conflit avec le défaut du laboratoire ; imposer un port hôte distinct |
| Compose | projet `betting-project`, volume PostgreSQL propre | conserver l'isolation et ne monter aucun volume du laboratoire |
| Receiver J7 | absent | Work Order, endpoint, persistance, idempotence, ACK et tests requis |
| Dépendance au lab | interdite par les règles du dépôt | aucune dépendance Maven/runtime au laboratoire ; implémenter le contrat accepté à la frontière |

Le worktree courant de `betting-project` contient des travaux CAT-002 non finalisés. WO-033 ne le
modifie pas. L'implémentation du receiver exigera une branche et un worktree distincts créés depuis
une base propre et vérifiée, conformément aux règles du dépôt receiver.

CAT-002 annonce des migrations additives à partir de `V003`. Un receiver persistant ouvert en
parallèle depuis le même `main` risquerait de revendiquer la même version. Le propriétaire devra
donc décider le séquencement : fusionner CAT-002 avant d'attribuer la prochaine version libre au
receiver, ou suspendre CAT-002 et le reprendre après le receiver. Deux migrations `V003` ne doivent
jamais être développées en parallèle, et une version artificiellement élevée ne doit pas contourner
ce conflit.

### 2.3 VPS de production déclaré

| Dimension | Fait propriétaire | Qualification technique |
|---|---|---|
| IPv4 | `51.255.167.32` | déclarée seulement ; aucune connexion ou sonde |
| Destination | VPS destiné à la production | aucune production autorisée par ce document |
| Disponibilité | propriétaire : prêt à être utilisé | `AVAILABLE_UNCONFIGURED_NOT_QUALIFIED` |
| Configuration/installation manuelle | aucune | OS, comptes, patchs, firewall, ports, runtime, stockage et sauvegarde inconnus |
| DNS et TLS | non fournis | URI, SAN, chaîne de confiance et rotation à définir |
| Accès administratif | non fourni | méthode, MFA, clés, bastion et moindre privilège à définir hors Git |
| Hébergeur/région | non fournis | nécessaires au rendu final de la demande SofaScore et au modèle de menace |

« Prêt à être utilisé » signifie ici que le propriétaire met l'hôte à disposition d'une future
préparation. Cela ne signifie pas que l'hôte est durci, joignable, sauvegardé, observable ou prêt à
recevoir une application. Aucun secret, identifiant ou clé d'accès ne doit être ajouté à ce dépôt.

## 3. Contrat minimal à implémenter côté receiver

Le futur Work Order `betting-project` doit consommer le contrat versionné par
[`J9-OPTIONAL-LOCAL-PUSH.md`](../architecture/J9-OPTIONAL-LOCAL-PUSH.md), sans importer le code du
laboratoire.

| Dimension | Exigence receiver |
|---|---|
| Méthode | `POST` HTTPS sur un endpoint d'import dédié |
| Route | `/api/imports/sofascore/j7-canonical-events` |
| Authentification | mTLS obligatoire ; aucune authentification par cookie ou jeton fournisseur |
| Corps | octets exacts d'une enveloppe J7 canonique v1 `HUMAN_VALIDATED` |
| Schéma | `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1` |
| Version de schéma | `1.0.0` |
| Protocole de livraison | `J7_OPTIONAL_LOCAL_PUSH` v1.0, distinct du schéma payload |
| Média requête | `application/vnd.betting-project.j7-canonical-event+json;version=1.0` |
| Média accusé | `application/vnd.betting-project.j7-delivery-ack+json;version=1.0` |
| Taille | maximum `5 242 880` octets |
| Idempotence | clé exacte `j7:<exportId>:sha256:<fileSha256>` ; effet unique |
| Accusé | objet JSON borné à 16 KiB, corrélé à l'export et au hash reçus |
| Résultats positifs | première insertion `201 IMPORTED` ; répétition exacte `200 DUPLICATE` avec le même identifiant distant |
| Conflit | même `exportId`, hash différent : refus déterministe, aucun effet |
| Persistance | état receiver et audit minimisé dans la base Betting Project uniquement |
| Données interdites | réponse brute, cookie, jeton, session, certificat privé et URL fournisseur |
| Retour fournisseur | aucun appel, refresh, callback, message ou commande vers le laboratoire |

Le receiver doit vérifier le schéma, la version, la taille, les hashes, la corrélation, le statut
`HUMAN_VALIDATED` et les champs obligatoires avant tout effet métier. Une indisponibilité ou un
refus receiver ne doit jamais provoquer une acquisition ou une relance fournisseur.

Il doit lire le corps de manière bornée avant désérialisation, appliquer UTF-8 strict, refuser les
clés JSON dupliquées et les tokens finaux, puis valider le schéma JSON local sans chargement
distant. La transaction PostgreSQL doit être atomique avant émission de l'accusé. Les tables
`persistent_job` et `outbox_message` actuellement présentes ne constituent pas un ledger d'import
J7 et ne doivent pas être réutilisées pour mélanger ces responsabilités.

## 4. Isolation exigée pour l'exécution simultanée

### 4.1 Processus et ports

- deux JVM distinctes, démarrées et arrêtées séparément ;
- laboratoire fixé à `127.0.0.1:8087` ;
- receiver fixé explicitement à `127.0.0.1:<port receiver>` pour le test local ;
- port receiver dans `[1, 65535]`, différent de `8087` et contrôlé libre avant le démarrage ;
- aucun binding `0.0.0.0`, LAN ou public pour la qualification locale ;
- inventaire et qualification de toutes les routes déjà exposées par `control-api`, dont
  `/internal/bootstrap/status` et les Actuator `health`, `info` et `flyway` ; désactivation,
  restriction ou protection mTLS explicite de chaque route non nécessaire au parcours ;
- zéro listener résiduel après le postflight.

La matrice de ports proposée pour la qualification est la suivante ; les deux valeurs receiver
restent à accepter et à reverifier immédiatement avant chaque démarrage :

| Composant | Adresse | Port proposé | Statut |
|---|---:|---:|---|
| SofaScore Local Lab | `127.0.0.1` | `8087` | existant et qualifié |
| PostgreSQL du lab | `127.0.0.1` | `5432` | valeur versionnée par défaut, à confirmer sans lire ni exposer `.env` |
| Receiver HTTPS Betting Project | `127.0.0.1` | `8444` | proposition du futur Work Order |
| PostgreSQL receiver | `127.0.0.1` | `5433` | proposition évitant la collision |
| Management receiver | même listener mTLS | `8444` | proposition évitant un second listener moins protégé |

Ces propositions ne constituent ni une configuration implicite, ni une autorisation d'exposition.

### 4.2 PostgreSQL et Compose

- deux projets Compose distincts : `betting-sofascore-local-lab` et `betting-project` ;
- deux conteneurs, bases, utilisateurs et volumes distincts ;
- deux ports PostgreSQL hôte différents ; les deux configurations actuelles utilisent `5432` par
  défaut et ne peuvent donc pas être démarrées simultanément sans override explicite ;
- le Compose actuel de `betting-project` publie le port PostgreSQL sans adresse hôte explicite ; un
  override ou un durcissement qualifié devra imposer `127.0.0.1` ;
- aucune base, table, migration, sauvegarde, volume ou compte partagé ;
- validation de chaque configuration par `docker compose --env-file .env config` dans son dépôt ;
- les secrets locaux restent dans les mécanismes ignorés propres à chaque dépôt et ne passent pas
  par la ligne de commande, Git, les logs ou le payload.

### 4.3 mTLS

- certificat serveur du receiver portant un SAN IP exact pour `127.0.0.1` pendant la qualification
  loopback ;
- autorité serveur explicitement approuvée par le sender ;
- certificat client du laboratoire explicitement approuvé par le receiver ;
- magasins et clés distincts, aucun secret partagé par fichier versionné ;
- refus des certificats absents, expirés, non approuvés, à mauvais SAN ou mauvaise identité ;
- profil final Windows attestant une clé privée client native non exportable avant cible réelle ;
- rotation, révocation et récupération à définir dans les futurs Work Orders.

Le sender actuel charge l'identité client depuis `Windows-MY`, mais fait confiance au trust store
Java effectivement consulté par sa JVM pour valider le serveur. Le profil devra y provisionner
l'autorité exacte du receiver ou faire évoluer le sender, sous Work Order, vers un ancrage borné.
Un trust-all est interdit. La JVM du laboratoire devra s'exécuter sous le compte Windows qui possède
la clé : une qualification lancée sous un compte sandbox différent ne prouverait pas l'accès au
magasin utilisateur du propriétaire.

### 4.4 Frontière VPS pour les paliers 2 et 3

L'IPv4 seule ne définit pas le receiver. Avant le palier 2, un Work Order d'infrastructure devra
choisir et qualifier une frontière parmi des options explicites. La préférence de sécurité à
évaluer est un réseau privé/overlay borné complété par mTLS applicatif ; une exposition publique sur
`443` derrière un ingress durci et mTLS reste une alternative à décider. Exposer directement le
processus Java ou PostgreSQL sur l'IPv4 publique est refusé par défaut.

Exigences communes :

- aucun port PostgreSQL public ; écoute base locale au VPS seulement ;
- receiver désactivé par défaut et endpoint unique borné ;
- DNS ou URI exacte, SAN serveur et trust anchors figés avant le go ;
- firewall entrant allowlisté au poste émetteur ou au réseau privé choisi ;
- compte système non privilégié, fichiers et volumes séparés, secrets hors Git et hors arguments ;
- patchs, horloge, journaux expurgés, limites CPU/mémoire/disque, sauvegarde/restauration et rollback
  qualifiés ;
- health/readiness sans payload et sans endpoint de management public superflu ;
- zéro retry automatique et arrêt global au premier écart TLS, réseau, schéma ou audit ;
- inventaire postflight des listeners, processus, conteneurs et fichiers temporaires.

Pour le palier 3, Local Lab et Betting Project devront en outre utiliser des comptes, processus,
réseaux, bases et volumes isolés sur l'hôte partagé. Cette isolation ne lève pas le blocage
normatif : l'acquisition fournisseur depuis le VPS reste interdite tant que la gouvernance et la
permission ne l'autorisent pas explicitement.

Le profil sender accepté est aujourd'hui propre à Windows : l'identité client est sélectionnée dans
`Windows-MY` et la clé privée doit rester native et non exportable. Ce mécanisme ne peut pas être
copié sur un VPS Linux. Le palier 3 devra choisir et qualifier un magasin/agent de clé VPS distinct,
la rotation, la révocation et les ACL, puis réviser le contrat mTLS sans affaiblir la non-exportabilité
ni introduire de secret versionné.

## 5. Séquences de qualification inter-processus

### 5.1 Palier 1 — Windows/Windows

Deux catégories normatives ne doivent pas être confondues :

```text
REAL_APPLICATION_SYNTHETIC_E2E=TWO_REAL_PROCESSES_SYNTHETIC_J7_NO_SOFASCORE_RIGHT_REQUIRED
PROVIDER_DERIVED_REAL_DELIVERY=TWO_REAL_PROCESSES_SOFASCORE_DERIVED_J7_PERMISSION_REQUIRED
```

La première catégorie prouve le code, les processus, mTLS, le contrat et les ledgers, mais pas le
droit de transférer des données fournisseur. La seconde exige toutes les portes de la section 8.

La première qualification avec le vrai receiver doit rester offline vis-à-vis de SofaScore et
utiliser une enveloppe J7 entièrement synthétique. Tant que la permission reste `NOT_EVIDENCED`, un
export dérivé de réponses SofaScore, même déjà validé localement, ne doit pas être transféré. La
qualification ne doit jamais joindre acquisition et livraison dans une action unique.

1. contrôler les commits, Work Orders, profils, ports, bases et empreintes du corpus de test ;
2. valider séparément les deux fichiers Compose et démarrer les deux PostgreSQL isolés ;
3. appliquer les migrations du Betting Project et démarrer son vrai processus receiver en
   loopback ;
4. vérifier sa readiness sans envoyer de payload ;
5. démarrer le vrai processus `betting-sofascore-local-lab` sur `127.0.0.1:8087`, avec tous les
   flags fournisseur bloqués ;
6. sélectionner une enveloppe J7 synthétique, byte-identique, marquée `HUMAN_VALIDATED` et
   inférieure ou égale à 5 MiB ;
7. vérifier l'autorisation propriétaire à usage unique, l'URI exacte et les empreintes des
   certificats de qualification ;
8. initier une seule livraison manuelle ;
9. vérifier l'effet receiver, l'accusé corrélé et l'état du ledger sender ;
10. ne pas réclamer à nouveau cette livraison via le sender : son ledger refuse un nouveau claim
    après un état terminal ;
11. arrêter les deux applications et les ressources de qualification ;
12. vérifier zéro listener, worker, navigateur, conteneur temporaire, secret ou payload brut
    résiduel.

L'accès réseau fournisseur reste `NO` pendant cette phase. Après une éventuelle permission
compatible, une acquisition réelle devra être achevée, Playwright fermé et tous les flags
fournisseur reverrouillés avant de démarrer la phase receiver. Les deux applications ne doivent
être simultanément disponibles que pendant la livraison. Une livraison ne pourra jamais déclencher
une acquisition.

### 5.2 Palier 2 — Windows vers receiver VPS

Ce palier ne sera préparé qu'après un `PASS` du palier 1 et la validation du receiver. Il conserve
le Local Lab et toute acquisition fournisseur sur Windows. Avant une livraison distante :

1. inventorier le VPS sans y déployer le runtime et geler OS, hébergeur/région, accès, ownership et
   baseline ;
2. ouvrir et valider les Work Orders infrastructure et déploiement `betting-project` ;
3. choisir l'ingress, le DNS ou l'URI, les ports, le firewall et la chaîne mTLS ;
4. qualifier sauvegarde/restauration, rétention/purge, logs, monitoring, limites de ressources et
   rollback sur une cible isolée ;
5. déployer le receiver désactivé, puis vérifier la readiness sans payload ;
6. figer le commit, l'image, les empreintes TLS, l'URI et le corpus synthétique dans un manifeste ;
7. obtenir une autorisation réseau et un go propriétaire à usage unique ;
8. exécuter d'abord une requête synthétique, puis seulement après permission compatible une
   livraison de données SofaScore dérivées ;
9. rapprocher les ledgers et effectuer le postflight des deux hôtes.

Le fait que le receiver soit installé sur un VPS destiné à la production exige une autorisation de
changement de cet environnement même si le payload de qualification est synthétique. Aucun accès à
`51.255.167.32` n'est déduit de la présente trajectoire.

### 5.3 Palier 3 — Local Lab et receiver sur le VPS

Ce palier est seulement envisagé. Si le Local Lab y effectue l'acquisition, il devient la topologie
`VPS_PLAYWRIGHT` différée par ADR-SS-003 v0.1. Avant tout prototype connecté, il faudra :

1. obtenir une permission officielle couvrant explicitement l'usage betting, l'environnement
   hébergé, les familles, l'authentification et les limites ;
2. réviser ADR-SS-001, ADR-SS-003, `AGENTS.md` et les statuts `LOCAL_ONLY` /
   `NOT_PRODUCTION_APPROVED` concernés ;
3. décider la frontière et le cycle de vie des payloads bruts sur VPS ;
4. ouvrir un Work Order Playwright VPS distinct avec modèle de menace, sandbox, egress, cleanup,
   stockage, chiffrement, sauvegarde/restauration, supervision et arrêt d'urgence ;
5. isoler le Local Lab du receiver et des fonctions critiques du Betting Project sur le même hôte ;
6. qualifier d'abord hors ligne, puis sous manifeste et go réseau propres.

Déplacer uniquement un processus de livraison sans Playwright ne prouverait pas l'acquisition VPS.
La portée exacte devra donc être figée avant le réexamen de gouvernance.

## 6. Cas d'acceptation obligatoires

Le vrai parcours `lab -> receiver` couvre une livraison nominale unique. Le doublon exact, la
course concurrente de même clé et l'ACK perdu après commit doivent être injectés côté receiver avec
un client de qualification local dédié ; ils ne constituent pas une seconde action du sender réel,
dont la concurrence vaut `1` et dont le ledger terminal interdit le nouveau claim.

| Cas | Résultat attendu |
|---|---|
| deux JVM et deux bases simultanément disponibles | readiness indépendante, aucun conflit de port ou volume |
| activation manuelle d'un J7 `HUMAN_VALIDATED` | une requête receiver ; aucun appel fournisseur ni seconde livraison implicite |
| livraison nominale mTLS | un effet receiver, ACK valide et corrélé, état sender `DELIVERED` |
| même export et même hash, client receiver dédié | zéro second effet, doublon exact confirmé |
| deux requêtes concurrentes de même clé, client receiver dédié | une seule transaction et un seul effet receiver |
| même `exportId`, hash divergent | refus avant effet ; conflit auditable |
| schéma/version/taille/hash/statut invalides | refus receiver déterministe, aucune mutation métier |
| certificat client absent ou erroné | handshake refusé, aucun effet, aucune donnée exposée |
| certificat serveur ou SAN erroné | sender refuse, aucun claim final positif |
| receiver indisponible ou timeout | état sender ambigu à réconcilier manuellement, zéro retry |
| ACK perdu après commit puis répétition exacte, client receiver dédié | doublon confirmé avec le même identifiant, zéro second effet |
| ACK absent, surdimensionné, malformé ou non corrélé | `UNKNOWN_RECONCILIATION_REQUIRED`, zéro retry |
| demande receiver visant un refresh fournisseur | fonctionnalité absente et refusée |
| livraison en échec | aucun appel J3/J4/J5, aucun démarrage Playwright |
| palier 2 synthétique Windows/VPS | même contrat et ACK que le loopback, ingress borné, aucun port base public |
| tentative palier 2 sans URI/SAN/go exacts | refus avant socket ou avant claim, selon la porte manquante |
| tentative palier 3 sous gouvernance v0.1 | refus avant démarrage du Local Lab sur VPS |
| arrêt des deux applications | zéro listener/processus possédé résiduel |

## 7. Répartition des futurs Work Orders

### 7.1 Dépôt `betting-project`

Un Work Order distinct, dont le numéro sera attribué depuis le catalogue propre à ce dépôt, devra
au minimum couvrir :

- contrat d'import et modèle de menace côté receiver ;
- endpoint du monolithe sous profil autorisé, binding loopback de qualification et port explicite ;
- validation J7, idempotence, effet unique, ACK borné et erreurs déterministes ;
- migration append-only, audit, rétention, purge, sauvegarde et restauration receiver ;
- authentification mTLS et qualification des cas négatifs ;
- tests offline et PostgreSQL/Testcontainers ;
- absence de dépendance au code ou au runtime du laboratoire ;
- démarrage simultané documenté avec le laboratoire sur Windows ;
- artefact de déploiement reproductible et désactivé par défaut, sans en déduire un déploiement VPS.

Le profil existant `control-api`, conditionné par une propriété receiver désactivée par défaut, est
la voie recommandée. Créer implicitement un quatrième profil runtime contreviendrait à la
gouvernance actuelle. Le management devrait partager le listener mTLS qualifié, sans ouvrir de
second port moins protégé.

### 7.2 Dépôt `betting-sofascore-local-lab`

Après validation du receiver, un Work Order sender distinct devra couvrir :

- URI exacte, port et identité TLS du receiver qualifié ;
- permission officielle devenue suffisamment établie et revue d'ADR-SS-003 ;
- profil mTLS réel et clé privée native Windows non exportable ;
- confirmation à usage unique et qualification inter-processus ;
- réconciliation manuelle et postflight ;
- première livraison réelle uniquement sous une nouvelle décision propriétaire.

### 7.3 Infrastructure et paliers VPS

Le palier 2 exige au minimum un Work Order d'infrastructure/déploiement distinct couvrant le VPS
déclaré, l'ingress, le DNS, le firewall, le mTLS, l'utilisateur de service, les secrets, la base,
les sauvegardes, l'observabilité, le rollback et le runbook. Sa validation n'autorise pas le palier
3.

Le palier 3 exige une étude et une décision de gouvernance avant tout Work Order d'exécution. Il ne
doit pas être ajouté au Work Order receiver ou sender du palier 1/2 comme extension implicite.

L'ouverture ou la validation de l'un de ces Work Orders ne vaut pas validation automatique de
l'autre.

## 8. Portes avant première livraison réelle dérivée fournisseur

```text
J9_OFFICIAL_PERMISSION_STATUS=EVIDENCED_COMPATIBLE
ADR_SS_003_REVIEW_STATUS=SATISFIED_FOR_REAL_DELIVERY
BETTING_PROJECT_RECEIVER_WORK_ORDER=VALIDATED
BETTING_PROJECT_RECEIVER_IMPLEMENTATION=QUALIFIED
CONCURRENT_REAL_APPLICATION_QUALIFICATION=PASS
REAL_IMPORT_ENDPOINT_URI=EXPLICITLY_AUTHORIZED
REAL_MTLS_PROFILE=QUALIFIED
CLIENT_PRIVATE_KEY_NATIVE_POLICY=QUALIFIED_NON_EXPORTABLE
RETENTION_PURGE_RESTORE_RECEIVER=QUALIFIED
REAL_DELIVERY_OWNER_DECISION=GRANT
PROVIDER_ACQUISITION_TRIGGERED_BY_DELIVERY=NO
PRODUCTION_AUTHORIZED=SEPARATE_DECISION_REQUIRED
```

Tant qu'une seule de ces portes manque, la topologie peut être préparée et qualifiée en
loopback/offline avec un J7 synthétique, mais aucune livraison dérivée de SofaScore ne doit partir.

Portes supplémentaires du palier 2 :

```text
WINDOWS_WINDOWS_E2E_RESULT=PASS
VPS_INVENTORY_AND_HARDENING=QUALIFIED
VPS_TARGET_IDENTITY=MANIFESTED_WITHOUT_SECRET
VPS_INGRESS_MODEL=EXPLICITLY_SELECTED_AND_QUALIFIED
VPS_RECEIVER_DEPLOYMENT_WORK_ORDER=VALIDATED
VPS_RECEIVER_BACKUP_RESTORE=QUALIFIED
VPS_RECEIVER_ROLLBACK=QUALIFIED
REMOTE_MTLS_AND_DNS_OR_URI=QUALIFIED
VPS_CHANGE_AUTHORIZATION=GRANT
REMOTE_RECEIVER_NETWORK_OWNER_GO=GRANT_ONE_TIME
```

Portes supplémentaires du palier 3 :

```text
SOFASCORE_HOSTED_PROVIDER_PERMISSION=EVIDENCED_COMPATIBLE
ADR_SS_001_VPS_REVIEW=ACCEPTED_COMPATIBLE
ADR_SS_003_VPS_REVIEW=ACCEPTED_COMPATIBLE
LOCAL_ONLY_AND_PRODUCTION_GOVERNANCE=EXPLICITLY_REDECIDED
RAW_PROVIDER_DATA_ON_VPS_POLICY=APPROVED
VPS_PLAYWRIGHT_WORK_ORDER=VALIDATED
COLOCATED_PROCESS_DATA_SECRET_ISOLATION=QUALIFIED
VPS_PLACEMENT_MODEL=<B1_COLOCATED_PRODUCTION_VPS|B3_ISOLATED_CONTAINER_ON_SHARED_HOST>
VPS_SENDER_PRIVATE_KEY_MODEL=QUALIFIED_NON_EXPORTABLE_NON_WINDOWS_MY
PROVIDER_VPS_NETWORK_OWNER_GO=GRANT_ONE_TIME
```

## 9. Décisions attendues avant ouverture du receiver

```text
BETTING_PROJECT_RECEIVER_WORK_ORDER_OPENING=REQUIRED
BETTING_PROJECT_RECEIVER_BASE=<CLEAN_VERIFIED_ORIGIN_MAIN_COMMIT>
BETTING_PROJECT_CAT002_MIGRATION_SEQUENCE_DECISION=REQUIRED
BETTING_PROJECT_RECEIVER_DEFAULT_ENABLED=NO
BETTING_PROJECT_RECEIVER_PROFILE=CONTROL_API_PROPOSED
BETTING_PROJECT_RECEIVER_BIND_ADDRESS=127.0.0.1
BETTING_PROJECT_RECEIVER_HTTPS_PORT=8444_PROPOSED
BETTING_PROJECT_RECEIVER_DB_PORT=5433_PROPOSED
BETTING_PROJECT_RECEIVER_MTLS_REQUIRED=YES
BETTING_PROJECT_RECEIVER_REAL_CODE_LOOPBACK_QUALIFICATION=AUTHORIZE_SEPARATELY
BETTING_PROJECT_RECEIVER_EXTERNAL_NETWORK_CURRENT=NO
BETTING_PROJECT_RECEIVER_VPS_DEPLOYMENT_CURRENT=NO
BETTING_PROJECT_PRODUCTION_CURRENT=NO

LAB_REAL_RECEIVER_SENDER_WORK_ORDER_OPENING=AFTER_RECEIVER_READINESS
LAB_PROVIDER_FLAGS_DURING_DELIVERY=ALL_FALSE
LAB_DELIVERY_TRIGGERS_PROVIDER_ACQUISITION=NEVER
LAB_REAL_PROVIDER_DERIVED_EXPORT_DELIVERY=PENDING_OFFICIAL_PERMISSION
LAB_SYNTHETIC_TWO_PROCESS_E2E=ELIGIBLE_FOR_SEPARATE_LOCAL_QUALIFICATION

E2E_STAGE_1_WINDOWS_WINDOWS=REQUIRES_SEPARATE_OWNER_AUTHORIZATION
E2E_STAGE_2_WINDOWS_VPS=REQUIRES_STAGE_1_PASS_AND_SEPARATE_INFRA_NETWORK_AUTHORIZATION
E2E_STAGE_2_OWNER_DECLARED_VPS_IPV4=51.255.167.32
E2E_STAGE_2_VPS_STATE=AVAILABLE_UNCONFIGURED_NOT_QUALIFIED
E2E_STAGE_2_RECEIVER_VPS_DEPLOYMENT_AUTHORIZED=NO
E2E_STAGE_2_PRODUCTION_INGESTION_AUTHORIZED=NO
E2E_STAGE_3_VPS_VPS=BLOCKED_BY_CURRENT_GOVERNANCE
E2E_STAGE_3_GOVERNANCE_REVIEW=REQUIRED_BEFORE_WORK_ORDER_OPENING
E2E_STAGE_3_LOCAL_LAB_VPS_DEPLOYMENT_AUTHORIZED=NO
E2E_STAGE_3_PROVIDER_VPS_ACQUISITION_AUTHORIZED=NO
```
