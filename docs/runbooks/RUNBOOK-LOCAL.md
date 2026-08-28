# Runbook local — SofaScore Local Lab

## 1. Objectif

Démarrer, vérifier, exploiter et arrêter les jalons J0 à J7 sur Windows sans exposer de service
hors de la machine locale. J5 conserve sa qualification réelle exceptionnelle désactivée par
défaut ; J6 consulte l'historique et garde sa rétention hors interface ; J7 assemble uniquement les
données locales courantes et exige une décision humaine avant téléchargement. La découverte
tournoi → rencontres est qualifiée et son Work Order est clôturé depuis le 2026-08-21.

## 2. Première installation

### 2.1 Vérifier le JDK dans Eclipse

- JDK installé : Java 25 LTS ;
- JRE par défaut du workspace : JDK 25 ;
- niveau de compilation du projet : 25 ;
- encodage du workspace : UTF-8.

Contrôle PowerShell :

```powershell
java -version
```

Le premier numéro de version doit être `25`.

### 2.2 Générer la configuration locale

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Initialize-LocalConfig.ps1
```

Résultat attendu :

```text
LOCAL_CONFIG_RESULT=CREATED
POSTGRES_PASSWORD_DISPLAYED=NO
```

Le fichier `.env` est ignoré par Git.

### 2.3 Préflight

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Preflight-Local.ps1
```

Résultat attendu :

```text
PREFLIGHT_RESULT=PASS
JAVA_TARGET=25
DOCKER_CHECKED=True
```

## 3. Démarrage normal

### 3.1 PostgreSQL

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1
```

Résultat attendu :

```text
POSTGRES_STATUS=HEALTHY
APPLICATION_STATUS=NOT_STARTED
```

### 3.2 Application

Depuis Eclipse, lancer `SofascoreLocalApplication` avec le profil `local`.

Ou depuis PowerShell :

```powershell
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Ces deux démarrages sont volontairement inertes pour Playwright. Même si le `.env` contient une
combinaison métier J3/J4/J5 valide, `SOFASCORE_PLAYWRIGHT_ENABLED=false` et l'absence de JAR worker
maintiennent le transport J3 indisponible. Pour une campagne J3 explicitement autorisée, suivre la
section 3.14 et démarrer l'application avec `scripts/Start-J3PlaywrightLocal.ps1` à la place.

### 3.3 Contrôles de santé

```powershell
Invoke-RestMethod http://127.0.0.1:8087/actuator/health
Invoke-RestMethod http://127.0.0.1:8087/actuator/info
```

Ouvrir ensuite le tableau de bord :

```text
http://127.0.0.1:8087
```

Vérifier :

- statut `LOCKED_OFFLINE_J3_POLICY` ;
- connecteur `DISABLED` ;
- base URL `NON_CONFIGURED` ;
- PostgreSQL `AVAILABLE` ;
- migration Flyway `25` ;
- snapshots `0` sur une base neuve ;
- corpus hors ligne `AVAILABLE_OFFLINE` ;
- fixtures `12 / 12 disponibles` ;
- résultats du corpus : `7` parsés, `4` incompatibilités de schéma prévues et `1` contenu inattendu prévu ;
- origine `SYNTHETIC` et schéma fournisseur `NON VALIDÉ` ;
- toutes les familles `Appelable = NON` et `URI = ABSENTE`.
- contrôle manuel J3 avec `ARRÊT GLOBAL ACTIF`, circuit `LOCKED` et transport fournisseur
  `INDISPONIBLE`.

Le compteur de fixtures est calculé depuis les ressources classpath et reste disponible même si
PostgreSQL est arrêté. Un état `INCOMPLETE` signifie qu’au moins une fixture déclarée n’a pas pu
être chargée ou vérifiée ; il ne faut pas contourner le contrôle d’intégrité.

### 3.4 Vérifier la confirmation locale sans appel fournisseur

Cette procédure valide seulement les transitions de l’interface. Elle ne constitue ni une
autorisation ni une tentative d’appel réel.

1. dans « Contrôle opérateur local », vérifier les cinq verrous fournisseur et le bouton réel
   désactivé ;
2. sélectionner « Lever l’arrêt global » ;
3. sélectionner ensuite « Activer le circuit » ;
4. conserver ou choisir une date unique et sélectionner « Préparer l’intention » ;
5. avant cinq minutes, recopier exactement la phrase affichée, cocher l’acquittement puis confirmer ;
6. vérifier l’état `CONFIRMED_BLOCKED` et le message indiquant qu’aucun transport n’a été exécuté ;
7. sélectionner « Arrêt global immédiat » et vérifier le retour du circuit à `LOCKED`.

À chaque rechargement après une commande, un nouveau jeton de formulaire est émis. Ne pas rejouer
une page historique ou réutiliser un formulaire déjà envoyé. Un redémarrage de l’application remet
toujours l’arrêt global à l’état actif.

### 3.5 Préparer la qualification fournisseur cinq pages

Cette procédure rend le bouton réel disponible mais ne doit pas être utilisée pendant une validation
automatisée. La dernière action décrite ci-dessous exécute réellement les cinq URI autorisées. Ne la
sélectionner qu’après décision explicite du propriétaire de passer de l’implémentation à l’exécution.

1. arrêter l’application ;
2. vérifier que PostgreSQL local est disponible et sauvegarder les snapshots utiles ;
3. modifier uniquement le fichier local ignoré `.env` avec les valeurs suivantes :

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS
```

4. installer une fois le runtime avec `scripts/Install-J3PlaywrightRuntime.ps1`, puis démarrer
   l'application avec `scripts/Start-J3PlaywrightLocal.ps1` comme décrit en section 3.14 ; ne pas
   utiliser en parallèle la commande Spring Boot générique ;
5. vérifier dans le tableau de bord :
   - `J3_QUALIFICATION_READY` ;
   - date unique `2026-08-13` non élargissable ;
   - `SCHEDULED_EVENTS` appelable avec URI configurée ;
   - concurrence `1` et délai minimal `3 s` ;
6. lever l’arrêt global puis activer le circuit ;
7. préparer l’intention, recopier exactement la phrase à usage unique et cocher l’acquittement ;
8. vérifier `CONFIRMED_READY` et relire la date ainsi que la portée `PAGES 1-5` ;
9. seulement après autorisation d’exécution, sélectionner
   **« 5. Lancer le lot fournisseur unique — PAGES 1 À 5 »** une seule fois ;
10. attendre le retour de la même requête Web sans actualiser la page ;
11. vérifier soit `COMPLETED` avec `5 / 5`, soit `FAILED` avec la première page en incident ;
12. vérifier que l’arrêt global a été réappliqué automatiquement, que le circuit affiche
    `LOCKED / QUALIFICATION_TERMINAL_LOCK` et qu’aucun bouton de réarmement n’est disponible ;
13. dans « Preuve terminale minimisée », contrôler les pages tentées, les statuts, tailles,
    SHA-256 et classifications, puis sélectionner « Télécharger la preuve minimisée (.txt) » ;
14. vérifier dans le fichier téléchargé :
    - `RAW_PAYLOAD_INCLUDED=NO` ;
    - `PROVIDER_URI_INCLUDED=NO` ;
    - `REQUEST_OR_RESPONSE_HEADERS_INCLUDED=NO` ;
    - `COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO` ;
    - `AUTOMATIC_RETRY_EXECUTED=NO` ;
15. arrêter l’application puis remettre dans `.env` :

```text
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=
SOFASCORE_ALLOWED_ENDPOINTS=
```

Ne jamais redémarrer pour contourner `FAILED`, répéter une séquence ou reprendre à la page suivante.
Ne jamais ajouter de cookie, jeton, compte, en-tête de navigateur, proxy ou autre origine. Le brut
reste uniquement dans PostgreSQL local et ne doit être copié ni dans Git, ni dans un chat, ni dans
les logs.

La preuve minimisée est gardée en mémoire uniquement : la télécharger avant d’arrêter
l’application. Ne pas joindre les snapshots bruts au rapport de qualification.

### 3.6 Reprendre explicitement la qualification à la page 2

Cette procédure ne remplace pas historiquement la section 3.5 : elle applique la décision
propriétaire distincte prise après l’adaptation hors ligne du parseur. Elle n’est disponible que si
PostgreSQL contient exactement le snapshot de page 1 qualifié et aucune page 2 à 5 pour la date
`2026-08-13`.

1. conserver intact le snapshot local de page 1 et démarrer PostgreSQL ;
2. activer dans `.env` les quatre valeurs J3 documentées à la section 3.5, sans cookie, jeton,
   compte ni donnée de session ;
3. démarrer l’application avec le profil `local` ;
4. vérifier `REPRISE J3 PAGE 2 PRÊTE` et `CHECKPOINT PAGE 1 VALIDÉ` ;
5. vérifier que l’interface indique `page 1 conservée, reprise pages 2 à 5` ;
6. lever l’arrêt global puis activer le circuit ;
7. préparer la reprise et vérifier la clé
   `SCHEDULED_EVENTS|date=2026-08-13|pages=2-5` ;
8. recopier exactement la phrase contenant `REPRISE PAGES 2-5`, cocher l’acquittement et confirmer ;
9. vérifier `CONFIRMED_READY`, puis sélectionner une seule fois
   **« 5. Lancer la reprise fournisseur unique — PAGES 2 À 5 »** ;
10. attendre le retour sans actualiser la page ;
11. vérifier soit `COMPLETED` avec `5 / 5`, soit l’arrêt sans retry sur la première page en incident ;
12. télécharger la preuve minimisée et contrôler au minimum :

```text
J3_MINIMIZED_EVIDENCE_VERSION=2
VERIFIED_LOCAL_CHECKPOINT_PAGES=1
PROVIDER_RESUME_FIRST_PAGE=2
RAW_PAYLOAD_INCLUDED=NO
PROVIDER_URI_INCLUDED=NO
AUTOMATIC_RETRY_EXECUTED=NO
```

13. remettre immédiatement la configuration `.env` en état sûr comme à la section 3.5.

La présence d’un snapshot de page 2 bloque toute nouvelle reprise, même après redémarrage. Ne pas
effacer ou modifier les snapshots pour contourner ce verrou. L’interrogation complète répétable
depuis l’interface fera l’objet d’une unité ultérieure distincte.

### 3.7 Reprendre explicitement la qualification à la page 3

Cette procédure succède historiquement à la section 3.6. Elle applique la nouvelle décision
propriétaire prise après la qualification de la page 2 et son adaptation hors ligne. Elle n’est
disponible que si PostgreSQL contient exactement les checkpoints des pages 1 et 2 pour la date
`2026-08-13`, que chacun se reparcourt avec `PARSED` et `hasNextPage=true`, et qu’aucune page 3, 4
ou 5 n’est encore conservée.

1. conserver intacts les snapshots locaux des pages 1 et 2 et démarrer PostgreSQL ;
2. activer dans `.env` les quatre valeurs J3 documentées à la section 3.5, sans cookie, jeton,
   compte ni donnée de session ;
3. démarrer l’application avec le profil `local` ;
4. vérifier `REPRISE J3 PAGE 3 PRÊTE` et
   `CHECKPOINTS PAGES 1 ET 2 VALIDÉS — CONFIRMATION REQUISE` ;
5. vérifier que l’interface indique `pages 1 et 2 conservées, reprise pages 3 à 5` ;
6. lever l’arrêt global puis activer le circuit ;
7. préparer la reprise et vérifier la clé
   `SCHEDULED_EVENTS|date=2026-08-13|pages=3-5` ;
8. recopier exactement la phrase contenant `REPRISE PAGES 3-5`, cocher l’acquittement et confirmer ;
9. vérifier `CONFIRMED_READY`, puis sélectionner une seule fois
   **« 5. Lancer la reprise fournisseur unique — PAGES 3 À 5 »** ;
10. attendre le retour sans actualiser la page ;
11. vérifier soit `COMPLETED` avec `5 / 5`, soit l’arrêt sans retry sur la première page en incident ;
12. télécharger la preuve minimisée et contrôler au minimum :

```text
J3_MINIMIZED_EVIDENCE_VERSION=2
VERIFIED_LOCAL_CHECKPOINT_PAGES=1,2
PROVIDER_RESUME_FIRST_PAGE=3
PAGES_ATTEMPTED=3,4,5
RAW_PAYLOAD_INCLUDED=NO
PROVIDER_URI_INCLUDED=NO
AUTOMATIC_RETRY_EXECUTED=NO
```

13. remettre immédiatement la configuration `.env` en état sûr comme à la section 3.5.

Une page 1 ou 2 ne doit jamais apparaître dans `PAGES_ATTEMPTED` ni dans les lignes d’horodatage des
tentatives de cette reprise. La présence d’un snapshot de page 3, 4 ou 5 bloque toute nouvelle
reprise, même après redémarrage. Ne pas effacer ou modifier un snapshot pour contourner ce verrou.
L’interrogation complète répétable depuis l’interface demeure hors périmètre.

### 3.8 Effectuer une collecte manuelle répétable à pagination dynamique

Cette procédure remplace fonctionnellement la reprise fixe de la section 3.7. Elle démarre toujours
à la page 1 et laisse `scheduled-events-v1` décider de la terminaison à partir du booléen
`hasNextPage`. Les anciennes preuves et classifications de qualification restent historiques.

1. vérifier PostgreSQL local, l’exposition `127.0.0.1:8087` et les quatre valeurs J3 de la section
   3.5 ;
2. vérifier qu’aucun cookie, jeton, compte, donnée de session ou proxy n’est configuré ;
3. démarrer l’application avec le profil `local` ;
4. vérifier `COLLECTE MANUELLE DYNAMIQUE PRÊTE` et
   `PAGINATION DYNAMIQUE — CONFIRMATION REQUISE` ;
5. lever l’arrêt global puis activer le circuit ;
6. choisir la date au format `AAAA-MM-JJ` et sélectionner **« 3. Préparer la collecte »** ;
7. vérifier la clé `SCHEDULED_EVENTS|date=<date>|pagination=has-next-page|max=25` ;
8. recopier exactement la phrase, acquitter le départ page 1, `hasNextPage` et le plafond 25,
   puis confirmer ;
9. vérifier `CONFIRMED_READY`, puis choisir exactement une voie :
   - **Option A — collecte fournisseur directe** : sélectionner une seule fois
     **« 5A. Lancer la collecte paginée — APPELS FOURNISSEUR »** ;
   - **Option B — import J3 paginé sans réseau** : ouvrir manuellement, hors de l'application,
     `https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/<date>/page/1`,
     enregistrer uniquement le corps JSON sous `page-1.json`, puis répéter avec `page-2.json`, …
     tant que le corps précédent porte `hasNextPage=true`. Sélectionner ensemble toutes les pages
     contiguës 1 à N. Ne jamais fournir HAR, export DevTools, en-tête, cookie, jeton ou donnée de
     session ;
10. attendre le retour sans actualiser la page ;
11. vérifier soit `COMPLETED`, soit l’arrêt sans retry au premier incident ou avant la page 26 ;
12. télécharger la preuve et contrôler au minimum :

```text
J3_MINIMIZED_EVIDENCE_VERSION=6
PAGINATION_MODE=HAS_NEXT_PAGE
CACHE_POLICY=FRESH_PARSED_SNAPSHOT_FIRST
CACHE_TTL_SECONDS=600
PROVIDER_FIRST_PAGE=1
MAXIMUM_PAGE_LIMIT=25
PROVIDER_PAGES_REQUESTED=<liste ou NONE>
CACHE_HIT_PAGES=<liste ou NONE>
LOCAL_JSON_IMPORT_PAGES=<liste ou NONE>
LOCAL_JSON_IMPORT_COUNT=<nombre>
FINAL_GLOBAL_STOP=ACTIVE
AUTOMATIC_RETRY_EXECUTED=NO
POLLING_OR_SCHEDULE_EXECUTED=NO
RAW_PAYLOAD_INCLUDED=NO
PROVIDER_URI_INCLUDED=NO
```

Avant chaque page, l’application recherche la clé exacte
`SCHEDULED_EVENTS|date=<date>|page=<page>` parmi les snapshots HTTP réussis, `PARSED`, intègres et
produits par `scheduled-events-v1`. Un checkpoint de cache actualisé depuis strictement moins de dix
minutes est reparsé localement puis utilisé sans transport, sans attente et sans nouvelle écriture.
La preuve
doit alors indiquer `PAGE_<n>_RESOLUTION_SOURCE=CACHE` et
`PAGE_<n>_PROVIDER_REQUEST_EXECUTED=NO`, ainsi que l’instant `PAGE_<n>_CACHE_STORED_AT`.

Un cache miss conserve le comportement fournisseur manuel. Le délai minimal est mesuré entre les
départs fournisseur réels : une page intermédiaire servie par le cache ne remet pas cette horloge à
zéro. Il n’existe aucun bouton de contournement du cache ni du TTL. Pour observer volontairement un
nouveau transport sur la même date, attendre l’expiration normale ; ne jamais supprimer, modifier
ou reclasser un snapshot.

Pour l'Option B, l'application prévalide le lot entier avant de réclamer l'exécution : fichiers
numérotés sans doublon ni trou, 1 à 25 pages, 5 Mio maximum par fichier, 25 Mio maximum au total,
forme J3 `scheduled`, puis `hasNextPage=true` avant la dernière page et `false` sur celle-ci. Un
refus à ce stade laisse l'intention `CONFIRMED_READY` afin de corriger le lot. Un succès doit
afficher zéro transport et zéro cache hit, ainsi que :

```text
LOCAL_JSON_IMPORT_PAGES=1,...,N
LOCAL_JSON_IMPORT_COUNT=N
PAGE_<n>_RESOLUTION_SOURCE=LOCAL_JSON_IMPORT
PAGE_<n>_PROVIDER_REQUEST_EXECUTED=NO
PAGE_<n>_LOCAL_JSON_IMPORT_EXECUTED=YES
```

Chaque page acceptée est conservée avec `MANUAL_LOCAL_JSON_IMPORT`; elle ne crée aucun checkpoint
de cache fournisseur. Le statut local `HTTP 200` et la latence nulle signifient « corps JSON
accepté localement », pas « réponse reçue par le client Java ». Cette voie ne récupère rien à la
place de l'opérateur et n'imite pas Chrome.

Pour répéter l’interrogation, lever à nouveau l’arrêt global : l’intention terminale précédente est
alors retirée. Réactiver le circuit et recommencer depuis l’étape 6. Ne jamais contourner un
incident en modifiant ou supprimant un snapshot ; analyser d’abord la preuve et la classification
locale. En particulier, un `HTTP_FORBIDDEN` direct est terminal : l'Option B exige une nouvelle
intention et ne constitue jamais le retry de celle qui a échoué. Remettre la configuration `.env`
en état sûr après la séance.

### 3.9 Inspecter localement le JSON brut d’un snapshot

Cette opération ne contacte pas le fournisseur et ne nécessite pas d’armer le circuit J3. Elle
exige uniquement l’application et PostgreSQL locaux :

1. ouvrir `http://127.0.0.1:8087/dashboard#snapshot-inspection` ;
2. repérer le snapshot à partir de son identifiant, de sa clé date/page et de son horodatage ;
3. vérifier que la ligne ne contient que les métadonnées attendues ;
4. sélectionner **« Inspecter le JSON »** sur cette ligne uniquement ;
5. vérifier sur la page distincte l’identifiant, la taille et le SHA-256 avant de lire la vue
   formatée ;
6. revenir au tableau de bord avec **« Retour aux snapshots locaux »**.

Le catalogue est limité aux 50 snapshots récents et ne charge pas leurs payloads. L’action utilise
le jeton de formulaire lié à la session et à usage unique. Le serveur relit uniquement la ligne
choisie, recalcule la taille et le SHA-256, applique le détecteur de contenu sensible, exige un JSON
strict puis produit un formatage temporaire en mémoire.

Ne pas copier le JSON brut dans un rapport, un commit, un ticket, une capture destinée au partage
ou un journal. Utiliser la preuve minimisée téléchargeable pour la qualification. Il n’existe aucun
bouton de téléchargement brut, aucune modification de classification et aucun contournement du
cache. Un refus `PAYLOAD_INTEGRITY_FAILURE`, `SENSITIVE_CONTENT_BLOCKED` ou `INVALID_JSON` doit
rester bloquant et être analysé hors ligne sans modifier la ligne persistée.

### 3.9 bis Préparer une éventuelle nouvelle recette de découverte tournoi → rencontres

Cette section conserve le contrat opérateur de l'évolution `VALIDATED`. Sa clôture
**n'autorise aucun nouvel appel fournisseur**. Tant que le propriétaire n'a pas autorisé
séparément une date J3, sa pagination bornée et l'unique requête de découverte, conserver la
configuration par défaut bloquée et ne sélectionner aucune action finale.

Après autorisation explicite seulement, application arrêtée, la configuration minimale de cette
session est exactement :

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS
```

Ce bloc rend la configuration cohérente avec les gardes ; il ne vaut pas permission d'exécution.
Ne pas y ajouter `EVENT_DETAILS`, `EVENT_STATISTICS`, `EVENT_INCIDENTS` ou `EVENT_LINEUPS` pour
cette qualification isolée.

La recette humaine autorisée devra respecter l'ordre suivant :

1. démarrer l'application sur `127.0.0.1` et vérifier les deux endpoints actifs exacts, la
   concurrence `1`, le délai minimal `3 s`, l'absence de polling/live et le stockage brut ;
2. exécuter une collecte J3 explicitement autorisée selon la section 3.8 — voie directe ou lot
   JSON local 1 à N — et obtenir `COMPLETED` dans le processus courant ;
3. sans redémarrer, vérifier que « Rencontres datées et accès direct à J5 » propose toutes les
   occurrences actionnables des pages exactes, avec le libellé exact
   `tournament.name - tournament.category.name` ; une occurrence sans portée géographique n'est pas
   proposée, et une occurrence restante n'est actionnable que si `timezoneEventCount` contient
   l'offset applicable à la date J3 dans `Europe/Paris` (`7200` en été, `3600` en hiver, l'un ou
   l'autre le jour d'une bascule) ;
4. choisir une occurrence et sélectionner **Préparer** ; vérifier qu'aucun transport n'est parti,
   que la date, `tournament.id`, `tournament.category.name` et le `uniqueTournament.id` numérique
   sont affichés, puis relire la phrase et l'expiration de cinq minutes ;
5. choisir une seule voie, sans resoumettre le formulaire :
   - **A — GET direct** : s'arrêter en l'absence d'une autorisation humaine distincte pour cet
     appel ; si elle est acquise, recopier la phrase, acquitter et soumettre une seule fois ;
   - **B — import JSON local** : enregistrer hors de l'application uniquement le corps JSON de la
     réponse correspondant exactement à la date et au tournoi unique affichés, puis sélectionner
     ce fichier, recopier la même phrase et acquitter. Ne jamais importer une capture HAR, des
     en-têtes, des cookies, un export de DevTools ou une donnée de session. La limite est 5 Mio ;
6. vérifier `COMPLETED` ou le premier code terminal verrouillé, le nombre d'appels `0` sur cache hit
   ou import local, ou `1` sur cache miss direct, la source affichée, les exclusions, le contrôle de
   compte et l'absence de retry ; un import doit afficher `LOCAL_JSON_IMPORT` et son snapshot
   `MANUAL_LOCAL_JSON_IMPORT` dans l'inspection locale ;
7. vérifier qu'un import local n'a créé aucun checkpoint de cache fournisseur et qu'il a suivi le
   même parseur `tournament-scheduled-v1`, la même projection et la même transaction canonique ;
8. pour chaque rencontre retenue, vérifier le lien
   `/events/{canonicalEventId}/statistics?zone=Europe%2FParis`. Avec l'opt-in J5 à `false`, ouvrir ce
   lien ne doit exécuter aucun appel fournisseur ; J4 et `EVENT_DETAILS` ne sont pas requis ;
9. ne jamais relancer après erreur, expiration ou arrêt. Un échec du GET ne donne pas accès à
   l'import dans la même intention : un tel terminal exige revue et redémarrage, pas un
   contournement automatique ;
10. arrêter l'application et restaurer immédiatement :

```text
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=
SOFASCORE_ALLOWED_ENDPOINTS=
```

Après redémarrage, la liste de tournois doit redevenir indisponible : la preuve J3 terminale reste
en mémoire et n'est pas reconstruite implicitement depuis des snapshots historiques. Une nouvelle
collecte J3 explicite, éventuellement résolue par son cache frais ou par un nouveau lot JSON local
complet, est requise pour recréer un catalogue. Ne jamais supprimer ou fusionner manuellement des
snapshots pour contourner cette frontière.

Après le correctif de précision PostgreSQL du 2026-08-20, une collecte J3 `COMPLETED` dans le
processus courant doit rendre le catalogue `AVAILABLE` même si `receivedAt` a été arrondi à la
microseconde lors de sa relecture. Seul un écart absolu strictement inférieur à une microseconde est
admis. Si `SNAPSHOT_METADATA_MISMATCH` persiste, ne pas relancer la découverte : conserver la preuve
minimisée et vérifier l'identifiant, l'endpoint, la clé date/page, le statut HTTP, le parseur et la
classification du snapshot. La taille ou le SHA-256 incohérent relève de
`SNAPSHOT_INTEGRITY_FAILURE`, jamais de cette tolérance temporelle.

### 3.10 Rechercher et consulter un événement J4 hors ligne

Ce parcours ne requiert ni activation J3, ni origine fournisseur, ni réseau. Avec PostgreSQL et
l’application démarrés :

1. ouvrir `http://127.0.0.1:8087/events` depuis le lien **« Événements locaux J4 »** du tableau de bord ;
2. choisir une date civile et saisir explicitement une zone IANA, par exemple `Europe/Paris` ;
3. sélectionner **« Rechercher localement »** ;
4. pour une preuve reproductible sans donnée fournisseur, sélectionner
   **« Charger la démonstration J4 »** ; l’import est idempotent et sa provenance reste
   `SYNTHETIC_FIXTURE` ;
5. ouvrir la rencontre pour vérifier l’UUID canonique, la provenance du détail et les observations
   historiques append-only ;
6. revenir à la recherche et confirmer que la même paire fournisseur/identifiant conserve le même
   UUID.

Un snapshot J3 local peut être normalisé manuellement en recopiant son identifiant numérique dans
**« Normaliser une ligne locale »**. Le service relit uniquement un snapshot `SCHEDULED_EVENTS`,
recalcule son SHA-256 et applique le parseur courant. Une forme `scheduled` sans liste `events`
n’invente aucun événement : le résultat affiche le nombre de compétitions trouvé pour la date et
précise que cet endpoint ne fournit aucune rencontre programmée, donc qu'aucune observation J4
n'est créée. Une forme `events` continue d'afficher séparément les rencontres parsées, insérées ou
dédupliquées. Une incompatibilité ne produit aucune écriture partielle et ne modifie jamais le
statut historique du snapshot.

L’absence de détail s’affiche comme un état local explicite. Elle ne déclenche aucun repli réseau.
Ne pas réutiliser le transport J3 pour compléter l’écran. La seule voie réelle autorisée est la
campagne fixe décrite ci-dessous ; elle n’est jamais déclenchée comme repli de la recherche.

### 3.10 bis Consulter les statistiques, incidents et compositions J5 hors ligne

Ce parcours ne demande aucune propriété réseau J3/J4 et ne doit jamais utiliser directement les
exemples d'URL J5. Avec PostgreSQL et l'application démarrés :

1. ouvrir `/events?date=2026-08-12&zone=Europe%2FParis` ;
2. si l'identité synthétique `900001` n'existe pas, sélectionner **« Charger la démonstration J4 »** ;
3. ouvrir sa fiche, puis **« Statistiques, incidents et compositions J5 »** ;
4. vérifier l'état initial explicitement absent des familles qui ne sont pas encore importées ;
5. sélectionner **« Importer les trois familles hors ligne »** ;
6. vérifier trois panneaux distincts, chacun avec sa complétude, sa source, son parseur et ses
   hashes : trois métriques, trois incidents et deux compositions de deux joueurs ;
7. vérifier `COMPLETE · 100%`, `SYNTHETIC_FIXTURE` et
   `PROVIDER_SCHEMA_VALIDATED=NO` ;
8. répéter l'import et confirmer que l'écran reste identique : l'opération est idempotente.

Le POST d'import exige le jeton de formulaire local lié à la session et à usage unique. Avec la
configuration normale, le panneau de qualification réelle reste bloqué et aucune action fournisseur
n'est éligible. La page ne contient aucun payload brut et n'effectue aucun repli lorsque les données
sont absentes. Les variantes `PARTIAL` et `EMPTY_VALID` sont qualifiées par les tests automatisés ;
elles peuvent être ajoutées comme nouvelles observations hors ligne sans modifier les versions
nominales.

La tentative de découverte du schéma réel s'est arrêtée sur `HTTP 403`, sans retry. Ne pas changer
de client, d'en-tête, d'adresse ou d'identité. La campagne distincte désormais autorisable reste
strictement limitée par le Work Order `WO-SS-20260815-006` et par la procédure suivante.

### 3.10 ter Qualifier réellement les trois familles J5

Cette campagne est un geste humain exceptionnel. Ne jamais l'exécuter depuis Maven, un script, un
navigateur automatisé, une tâche planifiée ou un mécanisme de rafraîchissement. Avant toute
activation, relire le Work Order actif, l'architecture J5 réelle, la readiness technique et le diff.
Les deux suites Maven doivent être vertes ; elles n'effectuent aucun appel fournisseur.

Application arrêtée, le propriétaire règle manuellement uniquement les clés réseau documentées :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

Ne pas modifier les autres valeurs de `.env`, ne pas les copier dans une preuve et ne jamais ajouter
ce fichier à Git. Démarrer PostgreSQL puis l'application sur `127.0.0.1`. Depuis la recherche locale,
ouvrir la fiche d'une identité canonique J4 existante puis sélectionner
**« Statistiques, incidents et compositions J5 »** :

1. vérifier que l'UUID, l'identifiant fournisseur et la zone correspondent à la fiche J4 choisie ;
2. vérifier que tous les bloqueurs de qualification J5 ont disparu ;
3. sélectionner **« Préparer la campagne J5 »** : cette action ne contacte pas le fournisseur ;
4. choisir une seule voie avant de soumettre la phrase :
   - **A — trois appels fournisseur** : recopier exactement la phrase, acquitter puis sélectionner
     **« Appeler les trois familles une fois »** une seule fois ;
   - **B — trois preuves locales, zéro appel** : ouvrir manuellement les trois URL exactes affichées
     et, pour chaque famille, choisir le fichier JSON disponible ou la case **« 404 observé »**.
     Recopier la phrase dans le formulaire B, acquitter puis sélectionner **« Importer les trois
     familles — ZÉRO APPEL »**. Chaque fichier est limité à 5 Mio. Ne jamais importer HAR, en-têtes,
     cookies, jetons, données de session ou export DevTools ;
5. pour la voie B, cocher « 404 observé » seulement après avoir constaté ce code sur l'URL exacte et
   lorsque le navigateur ne permet pas d'enregistrer le corps JSON. Le serveur crée alors la preuve
   locale canonique marquée `LOCAL_OPERATOR_DECLARED_HTTP_404`. Cette case n'est pas un statut libre :
   ne jamais l'utiliser pour un 403 ni la déduire de `notstarted`. Un fichier et la case de la même
   famille, ou l'absence des deux, sont refusés ;
6. ne pas recharger, revenir en arrière ou resoumettre le formulaire pendant l'exécution ;
7. attendre l'état terminal ; la voie directe tente au maximum `statistics`, puis `incidents`,
   puis `lineups`, avec au moins trois secondes entre deux départs. La voie locale produit zéro
   appel, `localJsonImports=3`, trois snapshots et trois observations dans le même ordre ;
8. si l'état du contrôle est `COMPLETED_LOCKED`, vérifier les trois panneaux locaux, leur complétude ou
   leur statut `UNAVAILABLE · N/A`, leur source `PROVIDER_SNAPSHOT`, leur parseur courant
   (`event-statistics-v2`, `event-incidents-v13`, `event-lineups-v2`) ou normaliseur
   `event-*-unavailable-v1`, leur snapshot, leur SHA-256 et leur heure de réception, sans ouvrir ou
   copier le payload brut ;
9. dans les panneaux incidents et compositions, vérifier qu'une complétude `PARTIAL` conserve son
   badge et son compteur de signaux, mais n'affiche plus la liste technique des chemins JSON
   manquants au-dessus du tableau ; vérifier que le contenu du tableau reste inchangé ;
10. si la campagne doit être abandonnée alors qu'elle est encore `AWAITING_CONFIRMATION` ou
   `EXECUTING`, sélectionner **« Arrêt global J5 »** ; après un état terminal, aucun nouvel appel
   n'est possible dans le processus et il suffit d'arrêter l'application.

Un HTTP `404` sur l'un des trois endpoints exacts est une indisponibilité de famille : vérifier
`ENDPOINT_UNAVAILABLE` et `UNAVAILABLE · N/A`, puis laisser la campagne poursuivre sans recharger
la page et sans répéter l'appel. Au premier incident réel — notamment `403`, `429`, `5xx`, timeout,
contenu non JSON sur une réponse `2xx`, contenu sensible, réponse trop volumineuse, incohérence
d'identité, incompatibilité de schéma ou erreur de persistance — ne pas réessayer et ne pas tenter
les familles restantes. Appliquer l'arrêt global si l'interface répond encore, arrêter
l'application et soumettre l'incident à une décision humaine. Un nouvel en-tête, un autre client
ou une autre adresse reste interdit.

Un terminal direct `HTTP_403` ne rend pas le formulaire d'import disponible dans la même campagne.
Ce verrou est volontaire : arrêter l'application, la redémarrer, préparer une nouvelle campagne,
obtenir une nouvelle phrase et choisir immédiatement l'option B. L'import n'est jamais un retry ni
un fallback automatique de la campagne échouée.

Sous `event-incidents-v13`, qui hérite sans modification de la règle V12, une séance terminale
entièrement non minutée peut être compatible à
`PARTIAL`. Vérifier alors que le marqueur `PEN` et chaque `penaltyShootout` sans minute affichent
`—`, que les tirs `missed` sans `reason` ni `description` sont conservés sans motif inventé, et que
la campagne atteint les compositions. Ne jamais interpréter l'ordre de séance comme une minute.

Pour un carton portant `reason="Leaving field"`, vérifier que le motif exact est conservé et rendu
dans la colonne `MOTIF`, puis que la campagne atteint les compositions. Toute autre valeur de motif
non documentée doit encore arrêter la campagne en `SCHEMA_INCOMPATIBLE`.
Toute séance mixte ou incohérente doit rester `SCHEMA_INCOMPATIBLE` et verrouiller la campagne.

Après succès, incident ou abandon, et avant tout autre redémarrage, remettre exactement :

```properties
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=
SOFASCORE_ALLOWED_ENDPOINTS=
```

Pour la clôture du Work Order réel J5, un redémarrage local de contrôle doit confirmer que les
panneaux J4 et J5 sont bloqués ; arrêter ensuite l'application. La preuve humaine doit rester
minimisée : identifiant fournisseur, UUID canonique,
code terminal, nombre de tentatives et, pour chaque famille réellement reçue, identifiant du
snapshot, taille, SHA-256, parseur, complétude et identifiant d'observation. Ne jamais consigner le
JSON brut, l'URI complète, les en-têtes, la phrase de confirmation, un secret ou une autre valeur de
`.env`.

```text
J5_REAL_EVENT_ID=<identifiant confirmé>
J5_REAL_CANONICAL_EVENT_ID=<UUID affiché>
J5_REAL_TERMINAL_STATE=COMPLETED_LOCKED|FAILED_LOCKED|STOPPED_LOCKED
J5_REAL_PROVIDER_CALLS=<0..3>
J5_REAL_STATISTICS_QUALIFICATION=PASS|UNAVAILABLE|FAIL|NOT_ATTEMPTED
J5_REAL_INCIDENTS_QUALIFICATION=PASS|UNAVAILABLE|FAIL|NOT_ATTEMPTED
J5_REAL_LINEUPS_QUALIFICATION=PASS|UNAVAILABLE|FAIL|NOT_ATTEMPTED
J5_PROVIDER_SCHEMA_VALIDATED=YES|NO
LOCAL_CONFIGURATION_RELOCKED=YES|NO
APPLICATION_STOPPED=YES|NO
```

### 3.10 quater Enchaîner J4 phase 2 et J5 dans une même session

Ce mode remplace le double cycle de build/démarrage lorsque l'objectif est de créer une identité
avec un identifiant J4 choisi, puis d'exécuter J5 sur cette même identité. Il n'automatise aucune
étape : les deux préparations, phrases, acquittements et confirmations restent distincts.

Application arrêtée, le propriétaire règle temporairement uniquement les clés réseau documentées :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

Ce profil à quatre endpoints est limité à J4 sous-étape 2 + J5 et laisse volontairement J3
désactivé. Pour ajouter J3 à la même instance, utiliser exclusivement le profil à cinq endpoints de
la section 3.10 quinquies. J4 sous-étape 1 reste incompatible avec tout mode combiné. Une famille
absente ou supplémentaire bloque le démarrage. Ne pas modifier les autres valeurs de `.env`, ne pas
les copier dans une preuve et ne jamais ajouter ce fichier à Git.

Effectuer un seul build, puis démarrer PostgreSQL et une seule instance de l'application sur
`127.0.0.1` :

1. suivre la procédure J4 sous-étape 2 de la section 3.13 jusqu'au résultat terminal de l'identifiant
   choisi ;
2. ouvrir le détail de l'identité canonique créée et contrôler son identifiant fournisseur ;
3. l'arrêt global J4 peut être appliqué à ce stade : il verrouille uniquement J4 et ne doit pas
   faire apparaître de bloqueur J5 ;
4. sans arrêter ni reconstruire l'application, ouvrir **« Statistiques, incidents et compositions
   J5 »** ;
5. vérifier que la préparation J5 est disponible, puis suivre les étapes 3 à 9 de la section
   3.10 ter ;
6. ne jamais ouvrir les deux confirmations dans des onglets concurrents. Un coordinateur commun
   sérialise néanmoins les transports et impose au moins trois secondes entre deux départs, y
   compris entre J4 et J5 ;
7. au premier incident, ne pas réessayer : appliquer uniquement l'arrêt correspondant au jalon en
   cours, puis arrêter l'application ;
8. après le dernier test, remettre les sept valeurs bloquées de la section 3.10 ter, redémarrer une
   fois pour constater les bloqueurs J4 et J5, puis arrêter gracieusement.

La preuve minimale doit montrer un seul démarrage d'application, le résultat J4, la disponibilité
de la préparation J5 après J4, le résultat J5 et l'absence de retry. Elle ne doit contenir ni JSON
brut, ni phrase de confirmation, ni URI complète, ni en-tête, ni autre valeur de `.env`.

```text
COMBINED_SESSION_APPLICATION_STARTS=1
COMBINED_SESSION_J4_EVENT_ID=<identifiant choisi>
COMBINED_SESSION_J4_TERMINAL=COMPLETED_LOCKED|FAILED_LOCKED|STOPPED_LOCKED
COMBINED_SESSION_J4_GLOBAL_STOP_APPLIED=YES|NO
COMBINED_SESSION_J5_PREPARATION_AFTER_J4=AVAILABLE
COMBINED_SESSION_J5_TERMINAL=COMPLETED_LOCKED|FAILED_LOCKED|STOPPED_LOCKED
COMBINED_SESSION_RESTART_BETWEEN_J4_AND_J5=NO
COMBINED_SESSION_RETRY=0
LOCAL_CONFIGURATION_RELOCKED=YES|NO
APPLICATION_STOPPED=YES|NO
```

### 3.10 quinquies Enchaîner J3, J4 phase 2, J5, J6 et J7 dans une même instance

Ce mode répond au besoin de mener plusieurs campagnes manuelles puis leurs inspections locales
sans reconstruire ni redémarrer entre les jalons. Il n'automatise aucun geste : J3, J4 phase 2 et
J5 conservent chacun leur préparation, leur phrase exacte, leur acquittement, leur limite d'appels
et leur verrou terminal. J6 et J7 ne réalisent aucun transport fournisseur.

Application arrêtée, armer temporairement uniquement l'union exacte suivante :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS,EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

J4 phase 1 est interdit dans cette combinaison. Une famille absente, supplémentaire ou non liée à
un opt-in bloque le démarrage. Tous les opt-ins restent `false` par défaut dans l'application. Ne
jamais recopier le reste de `.env`, et ne jamais ajouter ce fichier à Git.

Après un seul build et un seul démarrage sur `127.0.0.1:8087` :

1. exécuter la campagne J3 depuis le tableau de bord, avec sa préparation et sa confirmation
   exactes ; attendre son état terminal `COMPLETED`, `FAILED` ou `STOPPED` et son verrou J3 avant de
   poursuivre ;
2. ouvrir la recherche événement et exécuter une campagne J4 phase 2 explicitement préparée sur
   l'identifiant choisi ; attendre son état terminal verrouillé ;
3. depuis l'identité canonique correspondante, exécuter J5 après une nouvelle préparation et une
   nouvelle confirmation ; attendre la fin des trois familles ou le premier état terminal d'échec ;
4. consulter ensuite J6 et réaliser les gestes J7 nécessaires. Ces deux parcours lisent ou écrivent
   uniquement les données locales déjà persistées et ne consomment aucune autorisation réseau ;
5. ne pas soumettre deux confirmations fournisseur en parallèle. En défense supplémentaire,
   `ManualProviderRequestCoordinator` maintient une seule section HTTP active et au moins trois
   secondes entre tous les départs J3/J4/J5, y compris entre deux jalons ;
6. ne jamais réessayer automatiquement après un incident. Appliquer l'arrêt du jalon concerné,
   conserver uniquement les preuves minimisées et décider humainement de la suite ;
7. à la fin de la session complète, remettre les sept valeurs réseau à leur configuration bloquée,
   redémarrer une fois pour contrôler l'indisponibilité des préparations fournisseur, puis arrêter
   gracieusement l'application.

Le verrou terminal de J3 ne verrouille pas J4/J5 ; le verrou terminal J4 ne verrouille pas J5 ; et
aucun verrou fournisseur ne bloque les consultations J6/J7. Inversement, une consultation ou une
décision J7 n'arme jamais un transport J3/J4/J5.

Preuve minimale à conserver, sans JSON brut, URI, phrase de confirmation ni valeur `.env` :

```text
FULL_COMBINED_SESSION_APPLICATION_STARTS=1
FULL_COMBINED_SESSION_J3_TERMINAL=COMPLETED_LOCKED|FAILED_LOCKED|STOPPED_LOCKED
FULL_COMBINED_SESSION_J3_PROVIDER_CALLS=<compteur explicite>
FULL_COMBINED_SESSION_J4_PHASE2_TERMINAL=COMPLETED_LOCKED|FAILED_LOCKED|STOPPED_LOCKED
FULL_COMBINED_SESSION_J4_PROVIDER_CALLS=<0..1>
FULL_COMBINED_SESSION_J5_TERMINAL=COMPLETED_LOCKED|FAILED_LOCKED|STOPPED_LOCKED
FULL_COMBINED_SESSION_J5_PROVIDER_CALLS=<0..3>
FULL_COMBINED_SESSION_J6_LOCAL_INSPECTION=PASS|NOT_RUN
FULL_COMBINED_SESSION_J7_LOCAL_DECISION=HUMAN_VALIDATED|REJECTED|NOT_RUN
FULL_COMBINED_SESSION_RESTART_BETWEEN_JALONS=NO
FULL_COMBINED_SESSION_CONCURRENT_PROVIDER_CALLS=0
FULL_COMBINED_SESSION_AUTOMATIC_PROVIDER_CALLS=0
LOCAL_CONFIGURATION_RELOCKED=YES|NO
APPLICATION_STOPPED=YES|NO
```

### 3.10 sexies Ajouter la découverte tournoi à la session combinée

Pour enchaîner J3, la découverte tournoi, J4 phase 2 et J5 dans la même instance, application
arrêtée, utiliser exclusivement le profil suivant :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS,EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

L'union doit rester exacte. Ne pas ajouter `TOURNAMENT_STANDINGS` ni `TEAM_RECENT_EVENTS` : ces
familles sont différées, ne sont liées à aucun opt-in qualifié et leur présence bloque
volontairement le démarrage. Modifier `.env` ne recharge pas une application déjà démarrée : après
un changement, arrêter puis redémarrer depuis la racine du dépôt. Cette configuration à six
endpoints est le contrat métier valide ; elle n'active pas à elle seule le transport J3 Playwright.
Un démarrage Spring Boot générique doit encore afficher un transport J3 indisponible. Pour exercer
J3 après autorisation distincte, installer une fois le runtime puis lancer exclusivement :

```powershell
pwsh -NoProfile -File .\scripts\Install-J3PlaywrightRuntime.ps1
pwsh -NoProfile -File .\scripts\Start-J3PlaywrightLocal.ps1
```

Le second script construit le worker, vérifie le cache Chromium et injecte dans son seul processus
`SOFASCORE_PLAYWRIGHT_ENABLED=true`, le chemin absolu du JAR worker et
`PLAYWRIGHT_BROWSERS_PATH`. Il ne contacte pas SofaScore au démarrage : l'accès reste impossible
avant la préparation, la confirmation et l'action finale distincte dans l'interface. Le build Maven
et les tests ne constituent jamais une autorisation d'appel réel et restent isolés des opt-ins du
`.env` local.

Après une collecte J3 terminale `COMPLETED`, sélectionner le tournoi, préparer puis confirmer la
découverte. Cette action reste distincte et autorise soit au plus un GET sur cache miss, soit un
import local du seul corps JSON avec zéro transport et sans cache fournisseur. Les liens J5
créés n'exécutent rien automatiquement : J4 phase 2 et J5 conservent chacun leur propre préparation,
confirmation et verrou terminal. Au premier incident, ne pas réessayer. À la fin de la campagne,
restaurer tous les opt-ins à `false`, vider l'origine et l'allowlist, redémarrer pour constater les
verrous, puis arrêter l'application.

### 3.10 septies Importer un lot J5 multi-match strictement hors ligne

Ce parcours automatise uniquement la validation et l'ingestion de preuves remises ou déclarées
manuellement. Il reste strictement hors ligne même lorsque le connecteur maître et les voies
manuelles fournisseur sont armés. La configuration `.env` suivante est explicitement supportée :

```dotenv
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS,EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

`sofascore.automatic-refresh-enabled=false`, `sofascore.live-polling-enabled=false` et
`sofascore.store-raw-payloads=true` restent imposés par `application.yml`. Le lot refuse la
préparation et l'exécution si une automatisation est activée ou si la conservation brute est
coupée. `SOFASCORE_ENABLED=false` reste également accepté, mais n'est pas une condition du lot.
Après toute modification de `.env`, redémarrer l'application : aucune propriété de sécurité n'est
rechargée à chaud.

1. ouvrir `http://127.0.0.1:8087/j5-import-batches` ;
2. choisir une date et une zone IANA, puis rechercher les identités canoniques déjà persistées ;
3. cocher entre 1 et 25 rencontres dans la liste, sans saisir d'identifiant fournisseur ;
4. sélectionner **« Préparer le plan J5 sans importer »**, puis contrôler l'ordre par
   `providerEventId`, les `3N` noms, l'expiration à quinze minutes et le SHA-256 ;
5. contrôler les trois noms de preuves attendus pour chaque match :

```text
event-<providerEventId>-statistics.json
event-<providerEventId>-incidents.json
event-<providerEventId>-lineups.json
```

6. pour chaque nom, sélectionner le fichier JSON dans `batchFiles` ou cocher sa case 404, jamais les
   deux. Une case est autorisée seulement après un HTTP 404 réellement observé lorsque le corps ne
   peut pas être téléchargé ; elle crée le JSON local canonique portant
   `LOCAL_OPERATOR_DECLARED_HTTP_404`. Chaque fichier doit être non vide et limité à 5 Mio, le lot
   entier à 25 Mio ; aucun HAR, en-tête, cookie, jeton ou export DevTools n'est accepté. Avant de
   poursuivre, contrôler la synthèse `fichiers · déclarations 404 · saisies / 3N` et vérifier que
   chaque ligne porte `FICHIER` ou `404`. `CONFLIT`, `DOUBLON`, `INVALIDE` ou `À FOURNIR` maintient
   le bouton désactivé et nomme l'anomalie à corriger ;
7. recopier exactement `IMPORTER {N} MATCHS J5 HORS LIGNE {PLAN_SHA256}`, cocher
   l'acquittement et exécuter une seule fois ;
8. après succès, vérifier l'état de contrôle `COMPLETED_LOCKED`, le résultat
   `terminalCode=COMPLETED`, `localJsonImports=3N` et les compteurs fournisseur, cache et
   coordinateur tous à zéro ;
9. consulter les fiches J5/J6 locales. Un nouveau plan explicite permet un réimport : snapshots et
   observations peuvent être dédupliqués, mais chaque acquisition committée ajoute une occurrence
   J6 ;
10. sur erreur de manifeste, contenu ou dérive canonique avant claim, corriger puis resoumettre le
    même plan encore en attente. Après claim, toute erreur verrouille le contrôle et rollbacke le
    lot entier ; préparer alors un nouveau plan, sans retry automatique ;
11. un redémarrage efface le plan, son état et son résultat en mémoire. Il ne supprime aucune donnée
    committée et ne reprend jamais un lot interrompu.

La prévalidation sans écriture et le rollback tardif ne nécessitent ni pgAdmin ni console
PostgreSQL. Docker Desktop doit simplement être démarré ; Maven compile le test, Testcontainers
lance une base PostgreSQL éphémère et les assertions JDBC comparent les comptes avant/après :

```powershell
.\mvnw.cmd -Pintegration-tests "-Dit.test=FlywayMigrationIT#rejectsTheWholeOfflineBatchDuringPrevalidationWithoutPersistence+rollsBackTheWholeOfflineBatchWhenTheLastFamilyOfTheLastEventFails" test-compile failsafe:integration-test failsafe:verify
```

Le résultat attendu est `Tests run: 2, Failures: 0, Errors: 0` puis `BUILD SUCCESS`. Le premier
scénario remplace la composition du dernier match par un JSON incompatible et vérifie que les
comptes filtrés de `provider_snapshot`, `provider_snapshot_occurrence` et
`j5_event_data_observation` sont strictement identiques avant et après le refus. Le second installe
temporairement un trigger sur la dernière famille du dernier match, provoque l'échec après les
écritures précédentes, vérifie leur rollback global, puis supprime automatiquement le trigger et sa
fonction. Aucune commande `psql` et aucune inspection manuelle de la base ne sont requises.

Preuve minimisée à conserver, sans nom local reçu, payload, phrase, chemin, URI ou secret :

```text
J5_OFFLINE_BATCH_CONTROL_STATE=COMPLETED_LOCKED|FAILED_LOCKED|STOPPED_LOCKED|EXPIRED_LOCKED
J5_OFFLINE_BATCH_TERMINAL_CODE=<code borne>
J5_OFFLINE_BATCH_EVENT_COUNT=<1..25>
J5_OFFLINE_BATCH_LOCAL_JSON_IMPORTS=<0|3N>
J5_OFFLINE_BATCH_DECLARED_404=<0..3N>
J5_OFFLINE_BATCH_PROVIDER_CALLS=0
J5_OFFLINE_BATCH_CACHE_OPERATIONS=0
J5_OFFLINE_BATCH_COORDINATOR_ACQUISITIONS=0
J5_OFFLINE_BATCH_AUTOMATIC_RETRY=0
```

### 3.11 Qualifier réellement les deux événements J4 — sous-étape 1

Cette campagne est un geste humain exceptionnel. Ne jamais l’exécuter depuis Maven, un script, un
navigateur automatisé ou une tâche planifiée. Ne pas commencer tant que la branche n’a pas été
revue et que les tests hors ligne V6 ne sont pas réussis.

`WO-SS-20260827-014` est valide depuis le `2026-08-28` et le transport fournisseur de cette
procedure est Playwright. Ne pas rejouer la campagne historique pour maintenir son statut. Toute
nouvelle execution exceptionnelle conserve les deux identifiants fixes, le plafond, la fenetre, la
confirmation humaine et l'arret au premier incident.

À la suite de l’incident de migration V5 → V6 du 2026-08-15, appliquer d’abord la migration avec
le réseau bloqué. Application arrêtée, remettre ou conserver les sept clés suivantes :

```properties
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=
SOFASCORE_ALLOWED_ENDPOINTS=
```

Avec le code correctif, démarrer PostgreSQL puis l’application une première fois. Vérifier :

1. que Flyway annonce la migration de la version 5 vers la version 6 puis un démarrage réussi ;
2. que `/events` est disponible avec les bloqueurs `J4_EVENT_DETAILS_QUALIFICATION_DISABLED` et
   `CONNECTOR_DISABLED` ;
3. que les observations synthétiques historiques restent consultables ;
4. qu’aucune action fournisseur n’est disponible.

Arrêter ensuite l’application. Si V6 échoue encore, ne pas exécuter `flyway repair`, ne pas
supprimer la base ou ses observations, ne pas modifier le schéma manuellement et ne pas activer la
campagne. Conserver uniquement le code d’erreur de migration et soumettre l’incident à une revue.

Après réussite de cette mise à niveau bloquée, conserver localement les anciennes valeurs sans les
copier dans une preuve, puis régler temporairement les seules clés réseau ainsi :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS
```

Ne pas modifier les autres clés de `.env`. Ne jamais ajouter ce fichier à Git. Démarrer ensuite
PostgreSQL puis l’application locale avec les commandes normales. Sur
`http://127.0.0.1:8087/events` :

1. vérifier que le panneau **« Qualification réelle · sous-étape 1 »** affiche les deux seuls IDs
   `16386245` et `16421052` et aucun champ libre ;
2. vérifier que les bloqueurs de configuration ont disparu ;
3. sélectionner **« Préparer les deux événements »** : cette action ne contacte pas le fournisseur ;
4. recopier la phrase exacte, cocher l’acquittement et sélectionner
   **« Exécuter la sous-étape 1 »** une seule fois ;
5. attendre le résultat terminal sans recharger ni resoumettre le formulaire ;
6. si le résultat est `COMPLETED`, ouvrir chacun des deux détails locaux et vérifier humainement
   équipes, horaire, statut, compétition, saison, tour et provenance ;
7. vérifier que chaque détail référence un snapshot `event-details-v2`, un SHA-256 et une heure de
   réception, sans payload brut dans l’écran ou les logs ;
8. sélectionner **« Arrêt global J4 »**, même après un succès terminal ;
9. arrêter l’application.

Sous le contrat Playwright de WO-014, un HTTP `404` complet est conserve
`ENDPOINT_UNAVAILABLE`, sans parsing ni retry, puis autorise uniquement la cible fixe suivante. Il
ne constitue pas un incident permettant de rearmer ou de modifier l'allowlist.

Au premier incident, `403`, `429`, `5xx`, timeout, contenu inattendu, incompatibilité ou incohérence
d’ID :

1. ne pas réarmer ;
2. ne pas relancer la campagne ou le second événement ;
3. sélectionner l’arrêt global si l’interface répond encore ;
4. consigner uniquement le code terminal, l’ID tenté, l’identifiant du snapshot s’il existe, sa
   taille et son SHA-256 ;
5. arrêter l’application et soumettre l’incident à une décision humaine.

Après succès ou incident, remettre les clés locales à l’état bloqué avant tout autre démarrage :

```properties
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=
SOFASCORE_ALLOWED_ENDPOINTS=
```

Un contrôle facultatif peut redémarrer l’application puis vérifier que le panneau affiche
`J4_EVENT_DETAILS_QUALIFICATION_DISABLED` et `CONNECTOR_DISABLED`. Arrêter ensuite l’application.
La confirmation humaine doit inclure `LOCAL_CONFIGURATION_RELOCKED=YES` et
`APPLICATION_STOPPED=YES`, sans divulguer les valeurs du reste de `.env`.

Cette procédure de sous-étape 1 ne permet aucun troisième ID. La sous-étape 2 désormais autorisée
dispose de son propre opt-in, de son propre contrôle et de la procédure 3.13 ; ne pas la simuler en
modifiant l’URL ou les outils de développement du navigateur.

### 3.12 Requalifier localement les snapshots J4 après la correction de navigation

La campagne réelle du 2026-08-15 a déjà consommé les deux identifiants autorisés et produit les
snapshots 16 et 17. Le retest correctif ne doit effectuer aucun transport : ne pas sélectionner
**« Préparer les deux événements »**, ne pas recopier de phrase de confirmation et ne pas réarmer
la campagne.

Application arrêtée, remettre d’abord les sept clés de la section 3.11 à leur état bloqué. Démarrer
ensuite PostgreSQL et l’application corrigée, puis effectuer uniquement les lectures locales
suivantes :

1. rechercher `2026-08-14` avec la zone `Europe/Paris` ;
2. ouvrir Saint-Étienne — Clermont Foot et vérifier la provenance `PROVIDER_SNAPSHOT`, la source
   `snapshot:16` et le parseur `event-details-v2` ;
3. utiliser **« Recherche par date »** et confirmer que le formulaire reste au `2026-08-14` et que
   le match demeure affiché ;
4. rechercher `2026-08-15`, ouvrir Sevilla — Rayo Vallecano et vérifier
   `PROVIDER_SNAPSHOT`, `snapshot:17` et `event-details-v2` ;
5. utiliser le même retour et confirmer que la recherche reste au `2026-08-15` ;
6. vérifier que le panneau de qualification réelle signale la configuration bloquée, puis arrêter
   l’application.

La preuve humaine doit rester minimisée. Elle peut mentionner l’identifiant fournisseur,
l’identité canonique, la date civile, le type et la référence de provenance, le parseur et le
résultat du retour. Elle ne doit contenir ni JSON brut, ni URI, ni en-tête, ni valeur non documentée
de `.env`.

```text
CORRECTIVE_RETEST_PROVIDER_CALLS_AUTHORIZED=0
CORRECTIVE_RETEST_EXPECTED_SNAPSHOTS=16,17
CORRECTIVE_RETEST_EXPECTED_DATES=2026-08-14,2026-08-15
LOCAL_CONFIGURATION_RELOCKED=YES
APPLICATION_STOPPED=YES
```

Au moindre écart, arrêter l’application et ouvrir un Work Order correctif sans modifier le statut
historique `VALIDATED` de WO-014. Ne pas
rejouer la campagne pour corriger un défaut d’affichage ou de navigation locale.

### 3.13 Qualifier une identité canonique et ses rafraîchissements manuels — sous-étape 2

Cette sous-étape est un geste humain explicite. Elle autorise une identité canonique déjà affichée
par cycle confirmé et peut être répétée manuellement pour actualiser un match. Elle n’autorise
aucun polling, timer, script, navigateur automatisé, retry ou rafraîchissement automatique.

Depuis la validation de `WO-SS-20260827-014`, cette procedure utilise la cible Playwright migree.
Elle part d'une identite canonique deja affichee, impose son `providerEventId` cote serveur et
n'accepte aucun identifiant fournisseur libre transmis par l'action finale. Chaque appel reste une
action humaine unitaire explicitement preparee et confirmee.

Application arrêtée, partir des sept valeurs bloquées de la section 3.11, puis régler temporairement
uniquement :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS
```

Ne pas copier les autres valeurs de `.env` dans une preuve et ne jamais ajouter ce fichier à Git.
Démarrer PostgreSQL puis l’application sur `127.0.0.1`. Sur `/events` :

1. vérifier que la sous-étape 1 affiche `J4_PHASE_2_MUST_BE_DISABLED` et que la sous-étape 2 ne
   présente plus de bloqueur ;
2. partir de la fiche d'une identité canonique existante et sélectionner l'action de préparation ;
3. vérifier que la préparation n'exécute aucune requête ;
4. contrôler que la phrase affichée contient exactement l'UUID canonique et l'ID fournisseur
   résolu par le serveur ;
5. recopier la phrase, cocher l’acquittement puis sélectionner
   **« Appeler et actualiser une fois »** ;
6. attendre l’état terminal sans recharger ni resoumettre le formulaire ;
7. si le résultat est `COMPLETED`, contrôler les équipes, l’horaire, le statut, la compétition, le
   snapshot, le SHA-256, `PROVIDER` et l’absence de payload brut ;
8. ouvrir le détail, utiliser **« Recherche par date »** et confirmer que l’événement reste
   consultable à sa date civile ;
9. sélectionner **« Arrêt global J4 »** à la fin de la campagne.

Pour actualiser le même match, attendre l'état du contrôle `COMPLETED_LOCKED` puis recommencer les
étapes 2 à 7 depuis sa fiche canonique.
Chaque rappel exige une nouvelle préparation et une nouvelle confirmation. Le service ne consulte
pas le cache sur cette voie et impose au moins trois secondes entre deux transports. Une réponse
identique peut être dédupliquée dans la vue normalisée ; une évolution de statut ou d’horaire doit
ajouter une nouvelle observation sans modifier les versions antérieures.

Au premier incident, `403`, `429`, `5xx`, timeout, contenu inattendu, incompatibilité, incohérence
d’ID ou erreur de persistance : ne pas réessayer, appliquer l’arrêt global, arrêter l’application et
soumettre le code terminal à une décision humaine. `FAILED_LOCKED` et `STOPPED_LOCKED` refusent une
nouvelle préparation dans le même processus. La permission générale de rappels manuels ne
constitue jamais une autorisation de retry après incident.

Après succès ou incident, arrêter l’application, remettre exactement les sept valeurs bloquées de
la section 3.11, redémarrer facultativement pour vérifier les bloqueurs, puis arrêter de nouveau.
La preuve doit rester minimisée : ID, identité canonique, champs normalisés, statut, snapshot,
taille, SHA-256, parseur, heure de réception, nombre de cycles humains et résultat. Aucun JSON brut,
URI, header, secret ou valeur non documentée de `.env` ne doit y figurer.

```text
PHASE_2_PROVIDER_CALLS_PER_CONFIRMED_CYCLE=1
PHASE_2_MANUAL_CYCLES=OPERATOR_REPORTED
PHASE_2_AUTOMATIC_REFRESH=NO
PHASE_2_RETRY=NO
PHASE_2_HUMAN_QUALIFICATION=PENDING_UNTIL_EXECUTED
LOCAL_CONFIGURATION_RELOCKED=PENDING_UNTIL_CONFIRMED
APPLICATION_STOPPED=PENDING_UNTIL_CONFIRMED
```

### 3.14 Installer et qualifier localement le runtime Playwright J3

Cette procédure appartient à `WO-SS-20260827-013`. Elle installe puis exerce le vrai worker et
Chromium uniquement contre un serveur éphémère lié à `127.0.0.1`. Elle ne contacte pas SofaScore,
n’autorise aucune campagne réelle et ne valide pas humainement le Work Order.

Le build et le démarrage habituels restent inertes. Conserver ces valeurs sûres dans `.env`, y
compris lorsque la configuration métier combinée à six endpoints est armée :

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

Le profil `provider-playwright-runtime` ajoute Playwright Java `1.62.0` et les sources du worker à
la compilation. L’activer ne démarre ni worker, ni navigateur, ni transport fournisseur. Pour
vérifier le packaging sans exécuter la qualification :

```powershell
.\mvnw.cmd -Pprovider-playwright-runtime -DskipTests package
```

Installer explicitement le Chromium associé dans le cache local ignoré par Git :

```powershell
pwsh -NoProfile -File .\scripts\Install-J3PlaywrightRuntime.ps1
```

Le cache doit rester exactement sous `.tmp/provider-playwright-browsers`. Le script doit terminer
avec :

```text
PLAYWRIGHT_RUNTIME_INSTALL=PASS
PLAYWRIGHT_BROWSERS_PATH=<racine>\.tmp\provider-playwright-browsers
PROVIDER_ACCESS_PERFORMED=NO
```

Ne pas copier ce cache dans Git, un profil navigateur personnel ou un répertoire partagé.

Le packaging explicite produit le JAR classifié suivant :

```powershell
.\mvnw.cmd -Pprovider-playwright-runtime -DskipTests package
Resolve-Path '.\target\betting-sofascore-local-lab-0.1.0-SNAPSHOT-provider-playwright-worker.jar'
```

Le JAR doit être un fichier régulier lisible dont le manifeste porte exactement le `Start-Class`
du worker J3. Un chemin absent, vide, non JAR ou un manifeste différent maintient la qualification
bloquée avant le claim.

Pour une campagne fournisseur J3 autorisée séparément, ne pas persister les chemins ni remplacer
les valeurs sûres du `.env`. Depuis la racine du dépôt, utiliser le lanceur dédié :

```powershell
pwsh -NoProfile -File .\scripts\Start-J3PlaywrightLocal.ps1
```

Ce script reconstruit et vérifie le JAR classifié, exige une installation Chromium complète dans le
cache dédié, puis affecte
`SOFASCORE_PLAYWRIGHT_ENABLED=true`, `SOFASCORE_PLAYWRIGHT_WORKER_JAR` et
`PLAYWRIGHT_BROWSERS_PATH` seulement dans le processus qui démarre l'application. Il force aussi
les paramètres loopback de qualification à leur état inactif. Le superviseur du worker impose
`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` : une campagne ne télécharge jamais Firefox, WebKit ou une
révision manquante et échoue fermée si Chromium n'est pas utilisable. Son terminal doit annoncer
`J3_PLAYWRIGHT_LOCAL_LAUNCH=READY` et `PROVIDER_ACCESS_PERFORMED=NO` avant le démarrage Spring Boot.
Ni le script, ni l'ouverture du tableau de bord ne contacte SofaScore : une intention valide, sa
confirmation opérateur et l'action finale distincte sur cache miss restent nécessaires. La
campagne réelle demeure interdite tant qu'un go propriétaire distinct n'en a pas figé le périmètre.

Exécuter ensuite la qualification loopback :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
```

Ce script active `provider-playwright-runtime` et
`provider-playwright-local-qualification`. Il doit vérifier sur `127.0.0.1` :

- un worker, un Chromium headless et un `BrowserContext` non persistant neufs par campagne ;
- zéro réutilisation de profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement ;
- la fidélité du statut, du `Content-Type` et des octets, sans reconstruction DOM ;
- la persistance raw-first d’un HTTP `404`, son classement `ENDPOINT_UNAVAILABLE` et l’absence de
  parse, retry ou page suivante ;
- l’arrêt ciblé de l’arbre attribué à la campagne, sans arrêt par nom, avec acquittement en 500 ms,
  annulation en 2 s et nettoyage en 5 s au maximum ;
- l’absence de payload, cookie, token, header ou URI complète dans les journaux.

Le terminal attendu reste minimisé :

```text
J3_PLAYWRIGHT_LOOPBACK_QUALIFICATION=PASS
ORIGIN=http://127.0.0.1:<ephemeral>
PROVIDER_ACCESS_PERFORMED=NO
```

Un échec reste terminal pour cette qualification. Ne pas remplacer le worker par un transport
HTTP direct ou FlareSolverr, ne pas réutiliser un contexte et ne pas transformer l’essai loopback
en appel fournisseur.

### 3.15 Démarrer et qualifier localement le transport Playwright J4

Cette procédure appartient à `WO-SS-20260827-014`, valide le `2026-08-28`. Les commandes de
readiness restent exclusivement locales et loopback. Les valeurs sûres de la section 3.14 restent
désactivées dans `.env` hors campagne humaine explicitement preparee.

L'installateur historique de WO-013 fournit le Chromium du runtime commun :

```powershell
pwsh -NoProfile -File .\scripts\Install-J3PlaywrightRuntime.ps1
```

Ne pas répéter l'installation si le cache `.tmp/provider-playwright-browsers` est déjà complet et
qualifié. L'installation ne doit jamais être déclenchée au démarrage ou par une suite standard.

Le lanceur J4 dédié est :

```powershell
pwsh -NoProfile -File .\scripts\Start-J4PlaywrightLocal.ps1
```

Il construit et vérifie le JAR worker commun, refuse un cache Chromium incomplet et injecte les
chemins Playwright uniquement dans le processus enfant qui démarre l'application. Il ne modifie pas
`.env` et n'exécute aucun GET par lui-même. Une campagne reelle ne commence qu'apres la selection,
la preparation, la phrase exacte, l'acquittement et l'action finale dans l'interface.

La qualification technique autorisée utilise exclusivement :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
```

Le script exerce le vrai worker et Chromium uniquement sur un serveur éphémère lié à
`127.0.0.1`. Il vérifie le protocole et la sécurité du worker, les routes `EVENT_DETAILS` exactes,
les statuts et octets rendus par le serveur loopback, le renouvellement du worker et du contexte,
ainsi que l'arrêt ciblé en vol. Les politiques de cache des deux phases et l'exclusivité de la lease
partagée sont vérifiées séparément par la suite standard ; ce script loopback ne les certifie pas à
lui seul. Le terminal attendu reste minimisé :

```text
J4_PLAYWRIGHT_LOOPBACK_QUALIFICATION=PASS
ORIGIN=http://127.0.0.1:<ephemeral>
PROVIDER_ACCESS_PERFORMED=NO
```

Consigner les résultats dans
`docs/validation/J4-PLAYWRIGHT-EVENT-DETAILS-TECHNICAL-READINESS-20260828.md`. Le loopback seul ne
vaut ni qualification humaine fournisseur, ni autorisation de cloture ; ces deux portes ont ete
franchies separement par la decision proprietaire du `2026-08-28`.

## 4. Validation

### 4.1 Suite standard

```powershell
.\mvnw.cmd clean verify
```

Aucun Docker ni accès SofaScore n’est requis par les tests standards.

### 4.2 Suite PostgreSQL

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Docker doit être disponible. Testcontainers vérifie les migrations V1 à V25, l’état initial du
connecteur, la conservation exacte du brut, sa déduplication, les provenances J4/J5, les occurrences
J6, la rétention auditée et la portée cache `TOURNAMENT_SCHEDULED_EVENTS` dans une base éphémère.
Pour WO-010, il vérifie aussi le commit atomique des `3N` familles, le réimport avec occurrences
append-only et le rollback intégral lors d'une panne sur la dernière famille du dernier événement.

### 4.3 Script consolidé

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
```

### 4.4 Transport J3 simulé

La suite standard exerce un `RestClient` avec `MockRestServiceServer`. Aucun serveur public ni
SofaScore n’est contacté. Le contrat autorise seulement :

```text
http://127.0.0.1:<port>/simulated/scheduled-events?date=AAAA-MM-JJ
```

Toute autre origine, tout chemin libre, proxy, redirection ou retry doit faire échouer la revue. Le
transport simulé n’est pas un bean Spring et ne peut pas être déclenché depuis l’interface. Le
transport fournisseur distinct reste inéligible avec la configuration par défaut et ses tests sont
interceptés par `MockRestServiceServer`.

### 4.5 Confirmation J3

La suite standard couvre l’ordre des transitions, l’expiration à cinq minutes, la comparaison exacte,
l’arrêt global, le jeton lié à la session et son usage unique. Avec la configuration normale, la
confirmation finale produit `CONFIRMED_BLOCKED`. Avec les quatre propriétés exactes de la section
3.5, elle produit `CONFIRMED_READY`, sans exécuter de transport tant que l’action séparée n’est pas
soumise.

### 4.6 Politiques d’arrêt et d’incident J3

La suite standard simule les statuts `400`, `401`, `403`, `429`, `500` et `503`, le timeout, les
erreurs de lecture, le dépassement de 5 Mio, le contenu sensible, le HTML inattendu et la rupture de
schéma. Pour vérifier également la transition PostgreSQL `RAW_ONLY` avant parsing :

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Pour `429`, `Retry-After` est seulement une frontière de blocage du circuit. Ne jamais l’interpréter
comme une autorisation de retry ou de réouverture automatique. Un incident exige toujours l’arrêt,
la revue puis une nouvelle activation explicite.

La preuve attendue conserve uniquement des codes d’incident et des métadonnées bornées. Ne pas
copier un payload, un header ou une valeur sensible depuis les rapports de test.

### 4.7 Qualification manuelle Windows J3

Le rapport `docs/validation/J3-WINDOWS-MANUAL-CALL-QUALIFICATION-20260812.md` distingue deux
résultats :

- `PASS` pour le parcours opérateur local, l’arrêt global et les politiques simulées ;
- `NOT_EXECUTED` pour l’appel réel et `NOT_AVAILABLE` pour sa preuve de sortie.

Le point de décision est désormais satisfait pour le développement du chemin exact, mais pas pour
une exécution automatique pendant l’implémentation. La qualification réelle doit suivre la section
3.5 et rester un geste ultérieur du propriétaire. Le profil Maven `sofascore-live-test` demeure
bloqué : il n’est pas nécessaire au chemin manuel de l’interface et ne doit pas être contourné.

La synthèse versionnée peut contenir les états du circuit, les codes d’incident et les comptes de
tests. Elle ne doit jamais reproduire une phrase de confirmation active, un UUID, un jeton de
formulaire, un cookie, un header, une URI fournisseur, un payload brut ou une donnée de session.

### 4.8 Qualification technique Windows J4

Exécuter les deux suites :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Puis suivre la section 3.10 avec le corpus synthétique. Vérifier que l’écran affiche une seule
identité stable, deux observations sources, le détail `event-details-v1` et aucune erreur de
navigateur. Le rapport technique est conservé dans
`docs/validation/J4-WINDOWS-TECHNICAL-QUALIFICATION-20260815.md`.

Cette preuve historique hors ligne ne clôture pas le Work Order. La campagne réelle sous-étape 1
est qualifiée techniquement par les tests V6, puis doit être validée humainement selon la section
3.11. Elle ne doit jamais inclure un payload brut, une valeur de `.env`, un cookie ou une donnée de
session.

### 4.8 bis Qualification technique Windows J5

Exécuter les deux suites avec toutes les propriétés réseau dans leur état bloqué :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Puis suivre la section **3.10 bis**. Vérifier Flyway V7, l'import idempotent, les trois panneaux à
`COMPLETE · 100%`, les références de fixtures et l'absence d'erreur dans le navigateur. Consigner
uniquement les métadonnées minimisées dans
`docs/validation/J5-WINDOWS-TECHNICAL-QUALIFICATION-20260815.md` ; ne jamais y copier un payload,
une valeur de `.env`, un cookie, un jeton ou une donnée de session.

### 4.8 ter Readiness technique de la qualification réelle J5

Cette étape historique de la voie réelle J5 avait été qualifiée hors ligne par `261` tests
standards et `20` tests PostgreSQL/Testcontainers, avec Flyway V9 et zéro appel fournisseur. Sa
preuve est
`docs/validation/J5-REAL-EVENT-DATA-TECHNICAL-READINESS-20260815.md`.

Ces résultats valident les garde-fous, l'ordre des transports simulés, la persistance brute avant
parsing, les parseurs V2, l'arrêt sans retry, le verrou terminal et la poursuite après un HTTP
`404` de famille explicitement classé `UNAVAILABLE`. À cette date, ils ne validaient pas les
schémas fournisseur. Les campagnes humaines ultérieures ont depuis qualifié V13 et le Work Order
006 est archivé ; ce paragraphe reste la procédure historique de readiness, pas l'état courant.

### 4.8 quater Readiness technique J6

Exécuter les deux suites avec toutes les voies fournisseur bloquées :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Le rapport `docs/validation/J6-TECHNICAL-READINESS-20260818.md` couvre Flyway V22, 394 tests
standards et 38 tests PostgreSQL/Testcontainers. La qualification humaine de l'historique et la
qualification interactive de sauvegarde/restauration suivent exclusivement
`docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md`.

L'aperçu de rétention est sans mutation. Le mode `Execute` reste interdit tant que le propriétaire
n'a pas donné une autorisation distincte après revue d'une sauvegarde restaurée et de l'aperçu
final. Il n'est pas nécessaire pour qualifier l'interface historique.

### 4.8 quinquies Readiness et recette J7

J7 ne requiert aucun opt-in ou appel fournisseur. Exécuter les deux suites avec J3/J4/J5
verrouillés :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Consigner les résultats dans
`docs/validation/J7-TECHNICAL-READINESS-20260819.md`, puis suivre exclusivement
`docs/runbooks/J7-CANONICAL-EVENT-EXPORT.md`. La recette crée et rejette un premier candidat
synthétique, recrée et valide un second candidat, puis inspecte l'événement fournisseur local déjà
persisté `16691018`. Elle ne doit jamais activer une voie fournisseur pour obtenir ou rafraîchir
cet événement.

Un candidat `COHERENCE_CHECKED` n'est pas utilisable en dehors de l'aperçu. Seul
`HUMAN_VALIDATED` est téléchargeable après contrôle du hash. Le Work Order J7 reste actif tant que
les suites, le rejet/validation synthétiques, la validation locale de `16691018`, l'absence de
brut/secrets/sessions, les verrous J3/J4/J5 et zéro appel SofaScore ne sont pas tous prouvés.

La validation de fraîcheur tient un verrou PostgreSQL par événement partagé avec toutes les
écritures d'observation. Une décision persiste ensuite une intention write-ahead complète avant le
fichier terminal ; en cas d'erreur, répéter exactement la même décision afin que statut, heure,
motif, chemin, hash et taille authentifient la reprise. Le stockage synchronise un temporaire dans
la racine puis le publie, sans remplacement, par lien physique atomique sur le même système de
fichiers ; ne jamais renommer ni remplacer manuellement un fichier J7.

### 4.8 sexies Readiness découverte tournoi → rencontres

Cette readiness reste entièrement hors ligne. Exécuter les deux suites avec toutes les propriétés
réseau dans leur état bloqué :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Le rapport de readiness et de clôture
`docs/validation/J3-J5-TOURNAMENT-EVENT-DISCOVERY-TECHNICAL-READINESS-20260820.md` consigne le
catalogue exact issu d'une preuve J3 `COMPLETED`, la requête par
`tournament.uniqueTournament.id`, le parser `tournament-scheduled-v1`, le cache V24, la provenance
V25, le contrôle,
la projection `Europe/Paris`, l'atomicité et les liens J5 sans J4. L'ajout V25 a été revalidé par
les suites finales avant la décision propriétaire de clôture du 2026-08-21.

Les suites n'autorisent et n'exécutent aucun appel fournisseur. Leur réussite ne qualifie pas à
elle seule le fournisseur, l'import local ou la sémantique de `timezoneEventCount` ; la qualification
du Work Order résulte de la décision explicite du propriétaire appuyée sur ces preuves et sur les
vérifications fonctionnelles consignées. Toute nouvelle recette appartient encore explicitement au
propriétaire.

### 4.8 septies Readiness du lot J5 hors ligne multi-match

Exécuter les trois portes avec toutes les propriétés de la section 3.10 septies dans leur état
strictement hors ligne :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
```

Consigner commandes, compteurs et audits dans
`docs/validation/J5-OFFLINE-MULTI-MATCH-IMPORT-TECHNICAL-READINESS-20260822.md`. Les tests doivent
prouver sélection et hash, manifeste et limites, claim unique, prévalidation avant claim,
transaction atomique, réimport/occurrences, rollback tardif, en-têtes Web et zéro dépendance
transport/cache/coordinateur. Leur réussite autorise au plus le statut
`IMPLEMENTED_AWAITING_HUMAN_QUALIFICATION` : la recette hors ligne de la section 3.10 septies et la
décision de clôture restent au propriétaire.

### 4.9 Qualification réelle J4 sous-étape 1

Avant exécution humaine, vérifier la preuve de readiness
`docs/validation/J4-REAL-EVENT-DETAILS-PHASE1-READINESS-20260815.md`. Après la campagne, compléter
un rapport distinct avec les deux lignes suivantes laissées à `PENDING` tant que le propriétaire
n’a pas contrôlé les écrans :

```text
J4_REAL_MATCH_QUALIFICATION=PENDING
J4_REAL_EVENT_DETAILS_QUALIFICATION=PENDING
```

La campagne et le retest correctif ont désormais permis de passer ces deux lignes à `PASS`. La
sous-étape 2 a ensuite été autorisée ; la clôture du Work Order et son déplacement vers `completed`
restent interdits tant que la procédure 3.13 n’a pas été qualifiée humainement et que la preuve de
reverrouillage final n’est pas consignée.

### 4.10 Qualification réelle J4 sous-étape 2

Les tests Maven de la sous-étape 2 sont exclusivement hors ligne. Ils prouvent le binding de
l’opt-in distinct, l’exclusion de la sous-étape 1, la liaison de la confirmation à l’ID, un seul
transport simulé par cycle, le délai minimal, la persistance brute avant parsing, l’arrêt sans retry
et la répétition manuelle de deux cycles. Ils ne prouvent pas la compatibilité d’un nouvel événement
réel.

Après la procédure 3.13, consigner séparément :

```text
J4_REAL_PHASE_2_EVENT_ID=<id saisi>
J4_REAL_PHASE_2_MANUAL_REFRESH_COUNT=<nombre de cycles confirmés>
J4_REAL_PHASE_2_EVENT_DETAILS_QUALIFICATION=PASS|FAIL
J4_REAL_PHASE_2_REFRESH_QUALIFICATION=PASS|FAIL
J4_CONFIGURATION_RELOCK_AFTER_PHASE_2=YES|NO
J4_APPLICATION_STOPPED_AFTER_PHASE_2=YES|NO
```

### 4.11 Readiness du transport J3 Playwright

Sur le diff final du Work Order, exécuter dans cet ordre :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
pwsh -NoProfile -File .\scripts\Install-J3PlaywrightRuntime.ps1
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
git diff --check
```

Les trois premières commandes restent sans navigateur et sans appel fournisseur. Les deux scripts
Playwright exigent une action opérateur explicite ; l’installation alimente seulement le cache
local et la qualification utilise uniquement un serveur loopback éphémère. Une exécution locale
réussie permet de renseigner la readiness technique, mais ne vaut ni autorisation d’appel réel, ni
qualification humaine, ni déplacement du Work Order vers `completed`.

Consigner les résultats dans
[`J3-PLAYWRIGHT-TRANSPORT-TECHNICAL-READINESS-20260827.md`](../validation/J3-PLAYWRIGHT-TRANSPORT-TECHNICAL-READINESS-20260827.md)
sans y inclure de payload, cookie, token, header ou URI complète.

### 4.12 Readiness du transport J4 Playwright

Sur le diff final de `WO-SS-20260827-014`, exécuter dans cet ordre :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
git diff --check
```

Les trois premières commandes ne chargent ni Playwright ni Chromium. La quatrième exige le cache
Chromium commun déjà installé et contacte uniquement un serveur éphémère sur `127.0.0.1`. Aucun
appel SofaScore n'est autorisé par cette séquence.

Consigner les résultats dans
[`J4-PLAYWRIGHT-EVENT-DETAILS-TECHNICAL-READINESS-20260828.md`](../validation/J4-PLAYWRIGHT-EVENT-DETAILS-TECHNICAL-READINESS-20260828.md).
WO-014 est `VALIDATED` et archive sous `docs/work_orders/completed` depuis la decision proprietaire
du `2026-08-28`. Toute regression future exige un Work Order correctif distinct.

## 5. Arrêt

Arrêt conservant les données :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1
```

Suppression irréversible du volume local :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1 -RemoveData
```

## 6. Incidents

### 6.1 Port 5432 déjà occupé

1. identifier le processus ou le conteneur existant ;
2. ne pas exposer PostgreSQL sur une autre interface ;
3. choisir un port local libre dans `.env`, par exemple `POSTGRES_PORT=55432` ;
4. relancer `Preflight-Local.ps1` puis `Start-Local.ps1`.

L’application construit son URL avec la même variable `POSTGRES_PORT`.

### 6.2 Port 8087 occupé

Le port fait partie du cadrage. Identifier et arrêter le processus concurrent plutôt que de modifier silencieusement le port. Toute modification durable doit être documentée.

### 6.3 PostgreSQL `unhealthy`

```powershell
docker compose --env-file .env ps
docker compose --env-file .env logs --tail 200 postgres
```

Ne pas supprimer le volume avant d’avoir déterminé si des données doivent être conservées.

### 6.4 Échec Flyway

1. arrêter l’application ;
2. conserver les logs sans secret ;
3. ne pas modifier une migration déjà partagée ;
4. corriger au moyen d’une nouvelle migration ;
5. rejouer `-Pintegration-tests verify` sur une base éphémère ;
6. si la base locale est jetable, la recréer seulement après validation humaine.

### 6.5 Application accessible depuis le LAN

Situation non conforme. Arrêter immédiatement l’application, vérifier :

```yaml
server:
  address: 127.0.0.1
```

Puis rechercher toute surcharge de propriété dans Eclipse, les variables d’environnement et la ligne de commande.

### 6.6 Profil `sofascore-live-test` activé

L’échec reste volontaire pendant les unités hors ligne J3. Ne pas contourner l’Enforcer. Une
éventuelle qualification réelle exige le point de décision complet du Work Order J3.

### 6.7 Métadonnées Actuator obsolètes après une modification du POM

Si `/actuator/info` affiche un ancien `build.group` alors que le `pom.xml` contient
`com.bettingproject`, l’application utilise un `target/classes/META-INF/build-info.properties`
généré avant l’actualisation du modèle Maven Eclipse.

1. arrêter l’application ;
2. dans Eclipse, exécuter **Maven → Update Project** sur le projet ;
3. exécuter **Project → Clean** ;
4. lancer `mvnw.cmd clean verify` ;
5. redémarrer l’application et vérifier que `build.group` vaut `com.bettingproject`.

Le build échoue désormais si le `project.groupId` diffère de `com.bettingproject` ou si les
métadonnées Spring Boot générées exposent encore l’ancienne valeur.

## 7. Sauvegarde locale

Avant que les données réelles n’existent, le volume PostgreSQL reste recréable. Dès les premiers snapshots utiles :

1. arrêter les écritures ;
2. exécuter un `pg_dump` au format custom ;
3. conserver le dump chiffré hors du dossier Git ;
4. copier les exports et manifestes ;
5. tester une restauration sur une base distincte ;
6. documenter la date, le hash et le résultat.

Les octets bruts résident uniquement dans la colonne locale `provider_snapshot.payload_raw`. Ne pas
les copier dans Git, un ticket, un chat ou les logs. `payload_jsonb` reste réservé à une évolution
distincte de normalisation et ne doit pas servir de duplicata du brut.

À partir de Flyway V22, toute sauvegarde destinée à autoriser une rétention doit suivre le runbook
J6. Il impose un chiffrement direct par `age`, une restauration dans une base temporaire, des
empreintes identiques et un manifeste qualifié. La procédure générique ci-dessus ne constitue pas,
à elle seule, une preuve suffisante pour une purge J6.

## 8. Interdictions d’exploitation

- ne pas déployer l’application sur le VPS ;
- ne pas modifier `server.address` vers `0.0.0.0` ;
- ne pas ajouter de proxy, tunnel ou reverse proxy ;
- ne pas insérer une URI SofaScore dans les propriétés au J1 ;
- ne pas envoyer le contenu de `.env`, les dumps ou payloads bruts dans Chat, Git ou les logs ;
- ne pas transformer le lot J5 hors ligne en acquisition automatique, watcher, scheduler, retry,
  staging, archive ou tâche de fond ;
- ne pas transformer une réussite technique locale en validation de production.
