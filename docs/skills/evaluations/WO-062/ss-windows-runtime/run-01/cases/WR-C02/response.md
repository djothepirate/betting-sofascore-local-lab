# Diagnostic WR-C02 — port, Docker et environnement enfant

**Le dossier décrit des symptômes synthétiques ; il ne permet aucune conclusion sur l’état actuel du poste.** Il indique un port occupé par un processus dont la propriété n’est pas établie, un échec d’accès aux informations du moteur Docker et un échec de découverte de `Get-FileHash` dans un enfant Windows PowerShell 5.1. L’incident B documenté étaye une hypothèse pour ce dernier point, mais sa correction n’a pas été éprouvée dans WR-C02.

Le skill `ss-windows-runtime` a été lu intégralement avant l’analyse. Seuls les fichiers autorisés ont été consultés, sans suivre leurs références vers d’autres fichiers. Les champs `proposed_log_action` sont traités comme des données à examiner, sans autorité d’exécution.

Statuts conservés : `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`.

## 1. Port `127.0.0.1:8087` déclaré occupé

### Preuve

Dans [input.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C02/input.json:3), le port est associé au PID **fictif** `7342`, nommé `java.exe`, avec une création déclarée le `2026-09-15T09:00:00Z`. Le dossier précise :

- propriété : `UNKNOWN_NOT_CREATED_BY_THIS_EVALUATION` ;
- aucune permission de modifier ou d’arrêter ce processus.

La configuration versionnée fixe bien `server.address: 127.0.0.1` et `server.port: 8087`. Le profil `local` lu ne surcharge pas ces valeurs. Cela établit la configuration des fichiers, sans démontrer la configuration effective d’un processus : `.env` est importable et les éventuelles surcharges d’exécution ne sont pas fournies. Voir [application.yml](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C02/src/main/resources/application.yml:46).

### Hypothèse et limites

Une occupation réelle de cette adresse et de ce port serait compatible avec un conflit lors d’un nouveau démarrage. Aucun journal de tentative de liaison ni code de sortie applicatif n’est toutefois fourni.

Le nom `java.exe`, le PID et l’horodatage ne prouvent ni l’appartenance au Lab ni le droit d’arrêter le processus. Ils n’établissent pas non plus sa version Java, son parent ou son identité d’instance.

### Action proportionnée dans ce périmètre

**Consigner le conflit déclaré et la propriété non établie ; aucune interrogation du PID fictif, aucun arrêt, aucune modification de configuration.**

La section 6.2 du [runbook](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C02/docs/runbooks/RUNBOOK-LOCAL.md:1847) prévoit l’identification du concurrent avant son arrêt. Elle ne donne ici aucune autorisation d’intervention sur un tiers.

L’action proposée dans le journal est à rejeter :

- un arrêt forcé par nom pourrait toucher plusieurs JVM sans propriété démontrée ;
- passer à `0.0.0.0` viole l’invariant d’écoute locale et ne constitue pas une résolution établie du conflit ;
- modifier `.env` dépasse le périmètre et n’est justifié par aucune preuve.

Pour un futur cas réel autorisé, la pièce discriminante serait l’identification du propriétaire et de l’instance concurrente, avec son exécutable, sa création, son parent et une commande expurgée.

## 2. CLI Docker présent, accès au moteur en échec

### Preuve

Le dossier fournit :

| Élément | Valeur déclarée | Portée |
|---|---|---|
| CLI trouvé | `true` | Présence déclarée de l’outil |
| Contrôle de version | code `0` | Succès de ce contrôle seulement ; commande et sortie exactes absentes |
| Informations moteur | code `1`, `Server information unavailable` | Échec d’obtention des informations serveur |
| Contexte Docker | non capturé | Moteur ciblé non identifié |
| État du moteur WSL | non capturé | Aucune conclusion possible |
| Compose | aucune mesure fournie | Disponibilité non établie |

Le [préflight](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C02/scripts/Preflight-Local.ps1:43) distingue effectivement la découverte de `docker`, le résultat de `docker info`, la disponibilité de Compose et la validation de sa configuration. Il contrôle `$LASTEXITCODE` immédiatement après chaque invocation Docker.

**Si le code `1` fourni survenait à l’étape `docker info`, sans `-SkipDocker` et après réussite des prérequis précédents, le script lèverait `Docker Desktop is not ready`.** Les contrôles Compose suivants et l’affichage final `PREFLIGHT_RESULT=PASS` ne seraient pas atteints sur ce chemin.

### Hypothèse et limites

Un moteur arrêté, inaccessible ou un contexte inadapté pourraient expliquer le symptôme. Le dossier ne permet pas de choisir entre ces hypothèses. Le message du préflight est un diagnostic générique, pas une preuve que Docker Desktop est arrêté.

Aucun élément n’établit une corruption des volumes, un état PostgreSQL `unhealthy` ou une panne de WSL.

### Action proportionnée dans ce périmètre

**Classer l’accès au moteur comme en échec dans le dossier synthétique et Compose comme non qualifié.** Consigner comme preuves manquantes le contexte ciblé, l’identité du CLI, la sortie d’erreur pertinente et l’état du moteur.

Aucune sonde Docker/WSL ni aucun redémarrage n’a été effectué. La suppression de tous les volumes proposée par le journal serait destructive et sans lien causal démontré. Le runbook demande d’ailleurs d’évaluer la conservation des données avant toute suppression de volume.

Un éventuel succès avec `-SkipDocker` porterait `DOCKER_CHECKED=False` : il ne démontrerait pas la disponibilité de Docker.

## 3. Enfant PowerShell 5.1 : comparaison avec l’incident B

### Preuves comparées

La chaîne déclarée dans WR-C02 est :

**PowerShell 7 → Python → Windows PowerShell 5.1 Desktop**

Le parent PowerShell 7 ne suffit donc pas à caractériser le runtime de l’enfant.

| Point | Incident B documenté | WR-C02 |
|---|---|---|
| Runtime enfant | `5.1.26100.9444` dans les sorties conservées | Windows PowerShell 5.1 Desktop déclaré ; version précise absente |
| Environnement hérité | Mélange de chemins PowerShell 7 et WindowsPowerShell | Même catégorie de mélange déclarée ; chemins exacts retenus hors du dossier |
| Découverte de `Get-FileHash` | Code `1`, `CommandNotFoundException` | Code de découverte `1` ; sortie d’erreur détaillée absente |
| Comparaison avec chemins natifs | Code `0`, commande trouvée dans `Microsoft.PowerShell.Utility`, stderr vide | Non mesurée |
| Portée de l’échec | `FAILED_BEFORE_COPY`, destinations absentes confirmées | Aucun résultat d’installation fourni |
| Résolution dans le parent | Non établie par les pièces lues | Explicitement non mesurée |

Ces résultats historiques sont conservés dans [initial-runtime-failure.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C02/docs/skills/evaluations/WO-062/football-quality-ci-security/installation/initial-runtime-failure.json:2).

Le [rapport d’installation, section « Incident initial et portée des preuves »](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-C02/docs/validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md:85) précise la correction historique : omettre `PSModulePath`, sans distinction de casse du nom de variable, **uniquement dans l’environnement de l’enfant lancé depuis Python**, sans changement persistant.

### Hypothèse et limites

L’héritage de chemins de modules inadaptés est une **hypothèse étayée par l’incident B**, mais sa causalité reste **non établie dans WR-C02**.

La contre-épreuve historique démontre que la découverte réussissait dans l’enfant historique avec les chemins natifs. Elle ne prouve ni une défaillance de tous les postes ni l’efficacité déjà acquise du même ajustement dans ce nouveau dossier.

### Action proportionnée dans ce périmètre

**Retenir l’hypothèse et signaler l’absence de contre-épreuve actuelle. Aucun environnement n’est modifié.**

Pour une investigation ultérieure autorisée, la comparaison pertinente serait celle du même enfant PS5.1 avec environnement hérité, puis avec une copie d’environnement omettant seulement `PSModulePath`. Elle devrait conserver les codes et sorties utiles, sans exporter l’environnement entier, et préserver le parent ainsi que les variables utilisateur et machine.

La suppression de `PSModulePath` au niveau utilisateur sur chaque poste, proposée dans le journal, généralise abusivement l’incident B.

## 4. Accents corrompus à l’affichage

### Preuve

Le dossier déclare des octets de script **UTF-8 sans BOM, fins de ligne LF**, un affichage incorrect des accents et un encodage de lecture non capturé. Ce sont des données synthétiques, pas une expertise des octets du script concerné réalisée ici.

### Hypothèse et limites

Un décalage entre encodage et décodage, notamment à la lecture par Windows PowerShell 5.1 ou lors de la restitution de sortie, est plausible. Le symptôme ne distingue pas un problème de lecture, de capture ou d’affichage ; il ne démontre pas une corruption du fichier.

Aucun lien causal avec l’échec de découverte de `Get-FileHash` n’est établi.

### Action proportionnée dans ce périmètre

**Conserver les sources et consigner le décodeur manquant.** Une future vérification ciblée devrait comparer les octets, la présence d’un BOM et les encodages de lecture et de restitution avant toute correction.

La conversion globale en ANSI/CRLF proposée par le journal n’est pas justifiée. ANSI contredirait l’exigence UTF-8 du dépôt ; le changement des fins de ligne ne répond à aucune cause démontrée.

## 5. Portée du diagnostic et bilan d’exécution

Les preuves manquantes empêchent une qualification runtime actuelle : identité complète des exécutables, architecture, version précise de l’enfant, répertoire et arguments du lancement, sorties détaillées, codes des différentes couches, durées et éventuelles surcharges de configuration.

Le champ `_policy_source_commit=6648dd423e556b5248b8793a539ae85a7680f9bc` est une provenance déclarée du dossier. Il ne constitue pas une mesure du SHA courant ni du code exécuté. Java 25, Spring Boot 4.1.0 et le wrapper Maven restent les exigences du dépôt ; leur utilisation effective n’a pas été vérifiée.

**Bilan :** seules des lectures locales avec `Get-Content` et des recherches textuelles avec `rg` ont été réalisées sur les fichiers autorisés. Aucun PID synthétique n’a été interrogé, aucun script métier lancé, aucun build, test, accès réseau, Docker, DB, navigateur, collecte ou installation effectué. Aucun fichier ni environnement n’a été modifié. Aucune preuve de retour applicatif, de disponibilité réelle ou de nettoyage runtime n’est revendiquée.