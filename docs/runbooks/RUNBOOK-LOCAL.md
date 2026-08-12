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
- migration Flyway `1` ;
- snapshots `0` sur une base neuve ;
- corpus hors ligne `AVAILABLE_OFFLINE` ;
- fixtures `9 / 9 disponibles` ;
- résultats du corpus : `5` parsés, `3` incompatibilités de schéma prévues et `1` contenu inattendu prévu ;
- origine `SYNTHETIC` et schéma fournisseur `NON VALIDÉ` ;
- toutes les familles `Appelable = NON` et `URI = ABSENTE`.

Le compteur de fixtures est calculé depuis les ressources classpath et reste disponible même si
PostgreSQL est arrêté. Un état `INCOMPLETE` signifie qu’au moins une fixture déclarée n’a pas pu
être chargée ou vérifiée ; il ne faut pas contourner le contrôle d’intégrité.

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

Docker doit être disponible. Testcontainers vérifie les tables et l’état initial du connecteur.

### 4.3 Script consolidé

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
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

L’échec est volontaire au J1. Ne pas contourner l’Enforcer. Créer le Work Order J3 après validation du J2.

## 7. Sauvegarde locale

Avant que les données réelles n’existent, le volume PostgreSQL reste recréable. Dès les premiers snapshots utiles :

1. arrêter les écritures ;
2. exécuter un `pg_dump` au format custom ;
3. conserver le dump chiffré hors du dossier Git ;
4. copier les exports et manifestes ;
5. tester une restauration sur une base distincte ;
6. documenter la date, le hash et le résultat.

## 8. Interdictions d’exploitation

- ne pas déployer l’application sur le VPS ;
- ne pas modifier `server.address` vers `0.0.0.0` ;
- ne pas ajouter de proxy, tunnel ou reverse proxy ;
- ne pas insérer une URI SofaScore dans les propriétés au J1 ;
- ne pas envoyer le contenu de `.env`, les dumps ou payloads bruts dans Chat, Git ou les logs ;
- ne pas transformer une réussite technique locale en validation de production.
