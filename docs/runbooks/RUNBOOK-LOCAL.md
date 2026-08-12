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
- fixtures `9 / 9 disponibles` ;
- résultats du corpus : `5` parsés, `3` incompatibilités de schéma prévues et `1` contenu inattendu prévu ;
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
transport n’est pas un bean Spring et ne peut pas être déclenché depuis l’interface. Ne pas ajouter
une base URL réelle pour « essayer » cette unité.

### 4.5 Confirmation J3

La suite standard couvre l’ordre des transitions, l’expiration à cinq minutes, la comparaison exacte,
l’arrêt global, le jeton lié à la session et son usage unique. La confirmation finale produit
`CONFIRMED_BLOCKED` : elle ne branche pas le transport simulé et ne peut pas joindre un fournisseur.

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
