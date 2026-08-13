# Runbook local — SofaScore Local Lab

## 1. Objectif

Démarrer, vérifier, exploiter et arrêter le bootstrap J0/J1 sur Windows sans créer d’accès SofaScore et sans exposer de service hors de la machine locale.

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

Docker doit être disponible. Testcontainers vérifie les migrations V1/V2, l’état initial du
connecteur, la conservation exacte du brut et sa déduplication.

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
