# Runbook local — SofaScore Local Lab

## 1. Objectif

Démarrer, vérifier, exploiter et arrêter les jalons J0 à J4 sur Windows sans exposer de service hors de la machine locale. Le parcours J4 décrit ici est entièrement local et n’ajoute aucun accès SofaScore.

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
- migration Flyway `2` ;
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

4. redémarrer l’application avec le profil `local` ;
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
9. vérifier `CONFIRMED_READY`, puis sélectionner une seule fois
   **« 5. Lancer la collecte manuelle paginée — PAGE 1 À N »** ;
10. attendre le retour sans actualiser la page ;
11. vérifier soit `COMPLETED`, soit l’arrêt sans retry au premier incident ou avant la page 26 ;
12. télécharger la preuve et contrôler au minimum :

```text
J3_MINIMIZED_EVIDENCE_VERSION=4
PAGINATION_MODE=HAS_NEXT_PAGE
CACHE_POLICY=FRESH_PARSED_SNAPSHOT_FIRST
CACHE_TTL_SECONDS=600
PROVIDER_FIRST_PAGE=1
MAXIMUM_PAGE_LIMIT=25
PROVIDER_PAGES_REQUESTED=<liste ou NONE>
CACHE_HIT_PAGES=<liste ou NONE>
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

Pour répéter l’interrogation, lever à nouveau l’arrêt global : l’intention terminale précédente est
alors retirée. Réactiver le circuit et recommencer depuis l’étape 6. Ne jamais contourner un
incident en modifiant ou supprimant un snapshot ; analyser d’abord la preuve et la classification
locale. Remettre la configuration `.env` en état sûr après la séance.

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
n’invente aucun événement ; une incompatibilité ne produit aucune écriture partielle et ne modifie
jamais le statut historique du snapshot.

L’absence de détail s’affiche comme un état local explicite. Elle ne déclenche aucun repli réseau.
Ne pas réutiliser le transport J3 pour compléter l’écran. La seule voie réelle autorisée est la
campagne fixe décrite ci-dessous ; elle n’est jamais déclenchée comme repli de la recherche.

### 3.11 Qualifier réellement les deux événements J4 — sous-étape 1

Cette campagne est un geste humain exceptionnel. Ne jamais l’exécuter depuis Maven, un script, un
navigateur automatisé ou une tâche planifiée. Ne pas commencer tant que la branche n’a pas été
revue et que les tests hors ligne V6 ne sont pas réussis.

À la suite de l’incident de migration V5 → V6 du 2026-08-15, appliquer d’abord la migration avec
le réseau bloqué. Application arrêtée, remettre ou conserver les six clés suivantes :

```properties
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
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

Application arrêtée, remettre d’abord les six clés de la section 3.11 à leur état bloqué. Démarrer
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

Au moindre écart, arrêter l’application et conserver J4 au statut `IN_DEVELOPMENT`. Ne pas
rejouer la campagne pour corriger un défaut d’affichage ou de navigation locale.

### 3.13 Qualifier un identifiant paramétrable et ses rafraîchissements manuels — sous-étape 2

Cette sous-étape est un geste humain explicite. Elle autorise un ID numérique par cycle confirmé et
peut être répétée manuellement pour actualiser un match. Elle n’autorise aucun polling, timer,
script, navigateur automatisé, retry ou rafraîchissement automatique.

Application arrêtée, partir des six valeurs bloquées de la section 3.11, puis régler temporairement
uniquement :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS
```

Ne pas copier les autres valeurs de `.env` dans une preuve et ne jamais ajouter ce fichier à Git.
Démarrer PostgreSQL puis l’application sur `127.0.0.1`. Sur `/events` :

1. vérifier que la sous-étape 1 affiche `J4_PHASE_2_MUST_BE_DISABLED` et que la sous-étape 2 ne
   présente plus de bloqueur ;
2. saisir un identifiant compris entre `1` et `999999999` dans **« Identifiant fournisseur de
   l’événement »** ;
3. sélectionner **« Préparer un rafraîchissement »** et vérifier qu’aucune requête n’est partie ;
4. contrôler que la phrase affichée contient exactement l’ID saisi ;
5. recopier la phrase, cocher l’acquittement puis sélectionner
   **« Appeler et actualiser une fois »** ;
6. attendre l’état terminal sans recharger ni resoumettre le formulaire ;
7. si le résultat est `COMPLETED`, contrôler les équipes, l’horaire, le statut, la compétition, le
   snapshot, le SHA-256, `PROVIDER` et l’absence de payload brut ;
8. ouvrir le détail, utiliser **« Recherche par date »** et confirmer que l’événement reste
   consultable à sa date civile ;
9. sélectionner **« Arrêt global J4 »** à la fin de la campagne.

Pour actualiser le même match, attendre le résultat `COMPLETED_LOCKED` puis recommencer les étapes
2 à 7.
Chaque rappel exige une nouvelle préparation et une nouvelle confirmation. Le service ne consulte
pas le cache sur cette voie et impose au moins trois secondes entre deux transports. Une réponse
identique peut être dédupliquée dans la vue normalisée ; une évolution de statut ou d’horaire doit
ajouter une nouvelle observation sans modifier les versions antérieures.

Au premier incident, `403`, `429`, `5xx`, timeout, contenu inattendu, incompatibilité, incohérence
d’ID ou erreur de persistance : ne pas réessayer, appliquer l’arrêt global, arrêter l’application et
soumettre le code terminal à une décision humaine. `FAILED_LOCKED` et `STOPPED_LOCKED` refusent une
nouvelle préparation dans le même processus. La permission générale de rappels manuels ne
constitue jamais une autorisation de retry après incident.

Après succès ou incident, arrêter l’application, remettre exactement les six valeurs bloquées de
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

Docker doit être disponible. Testcontainers vérifie les migrations V1 à V6, l’état initial du
connecteur, la conservation exacte du brut, sa déduplication et la provenance J4.

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

## 8. Interdictions d’exploitation

- ne pas déployer l’application sur le VPS ;
- ne pas modifier `server.address` vers `0.0.0.0` ;
- ne pas ajouter de proxy, tunnel ou reverse proxy ;
- ne pas insérer une URI SofaScore dans les propriétés au J1 ;
- ne pas envoyer le contenu de `.env`, les dumps ou payloads bruts dans Chat, Git ou les logs ;
- ne pas transformer une réussite technique locale en validation de production.
