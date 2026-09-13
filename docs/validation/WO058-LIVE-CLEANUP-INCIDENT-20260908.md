# WO-058 — Incident de clôture locale du 8 septembre 2026

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Diagnostic en lecture seule, consigné le 2026-09-08 vers 20:42 UTC. Base du worktree : `9b96f0733e199bc80e71e8b7812926212eb7e599`. Les changements de pagination en cours ne sont pas qualifiés par cette note. Campagne observée : `60fd08dd-074e-4994-a0d5-51aff9eaa9db`, politique `live-v5`.

## Constats

L'utilisateur rapporte une collecte arrêtée, puis un clic sur « Finaliser la clôture locale » qui revient à la même campagne avec le même bandeau, sans page d'erreur. Les lectures ci-dessous placent la dernière réception publiée à 22:18:46 Europe/Paris.

Les lectures locales concordent :

| Source | Observation |
| --- | --- |
| GET `/state` | État durable `RUNNING`, révision `11782`, mais état d'exécution `STOPPED_ERROR`, motif `LOCAL_CLEANUP_PENDING`, `collectionStopped=true`, `cleanupPending=true`, `cleanupInProgress=false`. |
| PostgreSQL, campagne | `2189` appels réservés, `44027545` octets reçus ; démarrage `19:12:42.319084Z`, limite temporelle `23:12:42.319084Z`. |
| PostgreSQL, rencontres | `2 FINISHED_CONFIRMED`, `15 COLLECTING`. Les échéances des rencontres actives ne sont pas encore annulées. |
| PostgreSQL, dernier appel | `EVENT_STATISTICS`, groupe `652`, ordinal dans le groupe `2`, tentative `344e8b39-6787-4e03-860a-379faf910ac1`. Réception `20:18:46.012Z`, résultat `PARSED` à `20:18:46.163114Z`. Les cinq derniers appels sont tous `PARSED`. |
| PostgreSQL, dernières publications | `FAMILY_SCHEDULED` à `20:18:46.266366Z`, puis `SCHEDULED` à `20:18:46.316102Z`. Aucune tentative ne reste sans résultat. |
| PostgreSQL, garde | `CLEANUP_REQUIRED`, génération `43`, propriétaire `742e5b83-7769-448d-bd2e-f2742a93bab6`, PID `30196`, début du processus `19:07:24.780Z`, modification de la garde `20:26:42.464239Z`. |
| Processus Windows | PID `30196` toujours présent : `java.exe`, début `21:07:24.780737+02:00`, concordant avec l'identité enregistrée à la précision PostgreSQL. Son arbre visible contient seulement `conhost.exe` PID `26020`. Aucun worker, driver ou Chromium descendant n'a été observé dans cet inventaire. |
| Volume PostgreSQL | `999330268 KiB` disponibles lors de la lecture ; aucune saturation présente. |
| Journal PostgreSQL borné | Aucune ligne `ERROR:`, `FATAL:` ou `PANIC:` dans l'extrait contrôlé entre `20:18:35Z` et `20:28:00Z`. |

Le journal applicatif communiqué par l'utilisateur a été relu dans le diagnostic principal : il contient des avertissements Tomcat `HttpMessageNotWritableException` liés à une connexion interrompue pendant l'écriture de la réponse HTTP. Ils ne prouvent ni un échec du parseur ni la cause de l'arrêt du collecteur. Aucun payload, secret ou contenu complet de ce journal n'est reproduit ici.

## Lecture du code et limites

Cette lecture porte sur la version `9b96f07` effectivement utilisée lors de l’incident.
Le [complément de diagnostic](WO058-LIVE-FAILURE-DIAGNOSTICS-20260908.md) préparé après
l’arrêt du Lab corrige la perte d’information pour les exécutions suivantes ; il ne
reconstitue pas les exceptions disparues de celle-ci.

Le bouton de clôture de la session encore détenue passe par le POST `/live-campaigns/{campaignId}/stop`, puis [LiveCampaignService.stop](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java#L334). Quand la session est déjà terminée en mémoire, il réveille la demande de cleanup sur le thread propriétaire. La redirection HTTP constate l'acceptation de cette demande, pas son succès ultérieur.

Dans [LiveCampaignService.cleanup](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java#L420), la fermeture du transport et la preuve de disparition du superviseur précèdent la publication des états terminaux, la résolution des tentatives restantes et la libération de la garde. Cette réconciliation n'apparaît pas dans le ledger de l'incident.

Dans [ChildJvmPlaywrightProviderSupervisor.terminateSynchronously](../../src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java#L589), une `cleanupTerminalFailure` déjà mémorisée est relancée immédiatement. Cette mémoire est conservée après une erreur survenue une fois la mutation de l'arbre de processus commencée ; la campagne active n'est pas libérée. Le mécanisme explique un retry qui reste refusé même si les processus ont disparu depuis. Sa présence effective dans la mémoire de cette JVM n'a pas été inspectée : il s'agit d'une explication cohérente avec les observations, pas d'une exception originale récupérée.

La **cause initiale de l'arrêt demeure inconnue**. La dernière réponse et ses publications sont réussies. Le `catch RuntimeException` de `run` et celui de `cleanup` ne conservent pas séparément les causes. Les contrôles de capacité, de garde et de réservation précédant un appel restent des possibilités ; la sonde Docker possède notamment un timeout de deux secondes. Aucune preuve ne permet de désigner ce timeout, une contrainte SQL, la pagination ou les avertissements Tomcat comme cause initiale. L'absence d'erreur dans un extrait de journal n'exclut pas toutes les défaillances SQL ou de transport.

L'inventaire de descendants actuel n'est **pas** la sonde exhaustive de clôture de l'application : il ne suffit pas à libérer la garde. Le premier essai CIM en sandbox avait été refusé ; seule la lecture autorisée suivante a fourni les identités ci-dessus.

## Parcours de récupération existant — attente initiale

1. L'utilisateur arrête puis redémarre le lanceur Eclipse du Lab. Ce diagnostic n'a arrêté ni redémarré aucun processus et n'a lancé aucune collecte.
2. Au démarrage, [markProvenOrphanWithoutRestart](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java#L635) vérifie l'absence de l'ancien propriétaire par PID et date de création. Quand elle est établie, [interruptOrphan](../../src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignStore.java#L331) enregistre l'interruption, annule les échéances restantes et maintient `CLEANUP_REQUIRED`. Aucun navigateur n'est recréé et la garde n'est pas libérée automatiquement.
3. L'utilisateur rouvre cette campagne et emploie alors le parcours de clôture d'une session orpheline : POST `/live-campaigns/{campaignId}/finalize-interruption`, distinct du POST `/stop` de la session actuelle. [finalizeInterruptedCleanup](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java#L291) exige le jeton local, la génération attendue, l'exclusion locale, l'absence de session active et la sonde conservative [LiveOrphanProcessProbe](../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveOrphanProcessProbe.java). Une identité illisible ou un processus candidat résiduel maintient le refus.
4. Seulement après cette preuve, [completeOrphanCleanup](../../src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignStore.java#L362) compare sous verrou l'identité exacte et la génération, vérifie les états terminaux et le ledger, puis enregistre `LOCAL_CLEANUP_VERIFIED` et libère la garde atomiquement. Les observations, compteurs et traces antérieures restent conservés. La réussite doit être constatée dans l'interface après l'action, sans la présumer.

Il n'est pas proposé d'effacer `cleanupTerminalFailure`, de forcer la garde en SQL ni de contourner la sonde de processus. Aucun correctif de service, de superviseur ou de persistance n'a été appliqué pour ce diagnostic initial. À ce stade de l’enquête, la récupération réelle restait **en attente de l'action opérateur**.

**Complément après l’action opérateur :** la clôture orpheline est maintenant confirmée
par `LOCAL_CLEANUP_VERIFIED` à **23:24:55.679104 Europe/Paris**, révision 11844.
La campagne conserve son état historique `INTERRUPTED`. Une nouvelle campagne de
quinze rencontres a ensuite atteint `COMPLETED`, et le verrou fournisseur a été
constaté `FREE`. Le [rapport de récupération et de l’accueil](WO058-RECOVERY-DASHBOARD-20260908.md)
consigne ces preuves ainsi que la cause distincte et prouvée du HTTP 500 après redémarrage.

## Complément historique après arrêt du Lab — 20:54 UTC

L'utilisateur a ensuite arrêté le Lab. À `20:54:21Z`, `Get-Process -Id 30196` ne retrouve plus l'ancien propriétaire. Une nouvelle transaction `BEGIN READ ONLY` confirme toutefois la campagne `RUNNING`, révision `11782`, et la garde `CLEANUP_REQUIRED`, génération `43`, inchangées. Le redémarrage et la clôture orpheline ne sont pas encore constatés par cette enquête.

**Faits nouveaux :** `Get-WinEvent` sur `Application` et `System` ne retourne aucun événement de niveau critique, erreur ou avertissement entre `22:10` et `22:30` Europe/Paris. La lecture de tous les niveaux entre `22:16` et `22:22` retourne seulement des informations Razer/Security-SPP, aucune entrée System. Le journal Eclipse `C:/Dev/BettingProject/eclipse-workspaces/human-sofascore/.metadata/.log` n'a aucune entrée entre `21:07:51` et `22:29:56`. La configuration du lanceur LIVE ne définit pas de redirection persistante de la console. Aucun `hs_err_pid*` n'est présent directement à la racine du worktree ou du workspace Eclipse ; cette recherche n'a pas parcouru les répertoires personnels.

Le journal Docker conservé `C:/Users/geoff/AppData/Local/Docker/log/host/monitor.log.20260908-222133.170` contient les opérations suivantes sur le conteneur Lab. Les durées ci-dessous mesurent seulement la portion d'API Docker observée, de la création de l'exec à la fin de son inspection ; elles n'incluent pas le démarrage du processus Docker côté Java.

| Exec successifs : début → fin UTC | Portion API observée | Lignes du journal |
| --- | --- | --- |
| `20:18:41.705720 → 20:18:41.881826` | 176 ms | 3406–3412 |
| `20:18:43.465491 → 20:18:43.637960` | 172 ms | 3450–3457 |
| `20:18:45.451626 → 20:18:45.606402` | 155 ms | 3472–3478 |
| `20:18:48.546890 → 20:18:49.163169` | 616 ms | 3484–3505 |

Le dernier exec intervient après la publication `20:18:46.316102Z`, alors que la prochaine échéance minimale enregistrée est `20:18:47.024966Z`. Il n'est suivi d'aucune réservation SQL ni d'autre création d'exec sur ce conteneur jusqu'à la fin de ce journal à `20:21:33Z`. Le journal ne contient aucun marqueur `timeout`, `deadline`, `exit137`, `kill`, `OOM`, `restart`, `fatal` ou `panic` dans la fenêtre `20:18–20:19`. Les erreurs récurrentes `event streamer: error: EOF` sont déjà présentes avant l'incident ; une erreur d'export de métriques Docker `context canceled` à `20:18:59Z` ne prouve pas un arrêt du moteur.

**Inférence bornée :** cette dernière opération locale sans réservation ultérieure renforce la piste du contrôle de stockage avant appel. Le code [DockerLiveStorageCapacityProbe](../../src/main/java/com/bettingproject/sofascorelocal/application/live/DockerLiveStorageCapacityProbe.java) attend au plus deux secondes la commande `docker exec … df -Pk /var/lib/postgresql`, et son échec remonte au `catch` générique de `run`. Le timeout de deux secondes n'est pas démontré : l'heure de lancement du processus Java, la commande exacte et le code de sortie de cet exec ne figurent pas dans le journal récupéré.

**Limite de récupération :** `docker events` borné à `20:18:35–20:19:05Z` et au seul conteneur Lab ne retourne plus les anciens événements `exec_create`, `exec_start`, `exec_die`. L'identifiant du dernier exec, extrait du journal, a fait l'objet d'un unique GET `/v1.55/exec/{id}/json` sur le pipe local `dockerDesktopLinuxEngine` : réponse `404 Not Found`. Aucun exec n'a été créé par cette lecture. Ces archives ne permettent donc pas de récupérer le code de sortie ou l'exception originale. Aucune cause historique certaine supplémentaire ne peut être affirmée à partir de ces sources.

## Complément des captures PostgreSQL et Testcontainers

Les captures Docker Desktop transmises ensuite montrent un checkpoint commencé à
`20:15:52.820Z` et terminé à `20:18:24.813Z` : 1 510 buffers écrits, `write=151.881 s`,
`sync=0.090 s`, `total=151.993 s`. Il se termine environ 21,5 secondes **avant** la
dernière publication réussie de la campagne (`20:18:46.316102Z`). Le checkpoint suivant
se termine également normalement à `20:21:54.810Z` ; les lignes visibles sont des
messages `LOG`, sans erreur de checkpoint.

Une lecture de `pg_settings` dans une transaction `READ ONLY` confirme les valeurs
locales : `checkpoint_completion_target=0.9`, `checkpoint_timeout=300 s`,
`log_checkpoints=on`, `shared_buffers=16384` unités de 8 KiB (128 MiB). Aucun réglage
n’a été modifié. PostgreSQL répartit les écritures du checkpoint dans le temps selon
`checkpoint_completion_target` pour lisser les entrées/sorties : les 152 secondes de
durée totale **ne désignent pas 152 secondes de blocage de la base**. Référence primaire :
[documentation PostgreSQL 18 sur les checkpoints](https://www.postgresql.org/docs/18/wal-configuration.html).
Ces messages ne prouvent pas une cause PostgreSQL ; ils ne remplacent pas non plus
des mesures historiques de latence disque permettant d’exclure toute contention.

Requête de configuration exécutée dans cette transaction de lecture :

```sql
SELECT name, setting, unit
FROM pg_settings
WHERE name IN ('checkpoint_completion_target', 'checkpoint_timeout',
               'log_checkpoints', 'shared_buffers')
ORDER BY name;
```

Les lignes `testcontainers-ryuk` de `20:52:17Z` (22:52 Europe/Paris) correspondent à la
vérification complète de la pagination lancée par l’agent après l’arrêt du Lab par
l’utilisateur. Cette exécution s’est terminée à `20:57:02Z` avec succès. Elle est
postérieure à l’incident de `20:18Z` et ne peut donc pas l’avoir déclenché. Les résultats
des tests sont conservés séparément dans les rapports de qualification ; leurs
conteneurs ne sont pas ceux d’une nouvelle campagne fournisseur.

## Commandes de lecture exécutées

Les commandes suivantes ne déclenchent aucun appel fournisseur. Les lectures Docker et CIM nécessitant l'accès local ont été autorisées ; aucune commande `UPDATE`, `DELETE`, `INSERT`, arrêt de processus ou redémarrage n'a été exécutée. Aucun test Maven n'a été lancé dans ce diagnostic.

```powershell
$incidentState = Invoke-RestMethod -Uri 'http://127.0.0.1:8087/live-campaigns/60fd08dd-074e-4994-a0d5-51aff9eaa9db/state' -TimeoutSec 10
$incidentState | Select-Object campaignId,revision,state,reason,reservedCalls,receivedBytes,runtimeStatus | ConvertTo-Json -Depth 3

$incidentSql = @'
BEGIN READ ONLY;
SELECT campaign_id,policy_version,state,reason,reserved_calls,received_bytes,revision,started_at,ends_at,owner_instance_id,generation FROM live_campaign WHERE campaign_id='60fd08dd-074e-4994-a0d5-51aff9eaa9db';
SELECT state,campaign_id,generation,owner_instance_id,owner_process_id,owner_process_started_at,changed_at FROM provider_campaign_guard WHERE singleton_id=1;
SELECT state,count(*) FROM live_event WHERE campaign_id='60fd08dd-074e-4994-a0d5-51aff9eaa9db' GROUP BY state ORDER BY state;
SELECT revision,state,reason,changed_at,attempt_id FROM live_transition WHERE campaign_id='60fd08dd-074e-4994-a0d5-51aff9eaa9db' ORDER BY revision DESC LIMIT 5;
SELECT c.ordinal,c.endpoint,c.group_sequence,c.group_ordinal,c.reserved_at,d.authorized_at,p.received_at,r.resolved_at,r.outcome FROM live_call c LEFT JOIN live_call_dispatch d USING(attempt_id) LEFT JOIN live_call_receipt p USING(attempt_id) LEFT JOIN live_call_result r USING(attempt_id) WHERE c.campaign_id='60fd08dd-074e-4994-a0d5-51aff9eaa9db' ORDER BY c.ordinal DESC LIMIT 5;
SELECT count(*) AS unresolved_attempts FROM live_call c LEFT JOIN live_call_result r USING(attempt_id) WHERE c.campaign_id='60fd08dd-074e-4994-a0d5-51aff9eaa9db' AND r.attempt_id IS NULL;
SELECT endpoint,count(*),sum(missed_cycles) AS missed_cycles,max(changed_at),min(next_due_at),max(next_due_at) FROM live_family_schedule WHERE campaign_id='60fd08dd-074e-4994-a0d5-51aff9eaa9db' GROUP BY endpoint ORDER BY endpoint;
COMMIT;
'@
& 'C:/Users/geoff/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe' exec betting-sofascore-local-lab-postgres psql -X -v ON_ERROR_STOP=1 -P pager=off -U sofascore_lab -d sofascore_local_lab -c $incidentSql

& 'C:/Users/geoff/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe' exec betting-sofascore-local-lab-postgres df -Pk /var/lib/postgresql

& 'C:/Users/geoff/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe' logs --since 2026-09-08T20:18:35Z --until 2026-09-08T20:28:00Z betting-sofascore-local-lab-postgres 2>&1 |
    Where-Object { $_ -match 'ERROR:|FATAL:|PANIC:|database system|terminating connection' } |
    Select-Object -Last 30

$taskProcesses = @(Get-CimInstance Win32_Process | Select-Object Name, ProcessId, ParentProcessId, CreationDate)
$taskRelevant = [System.Collections.Generic.HashSet[uint32]]::new()
[void]$taskRelevant.Add(30196)
do {
    $taskAdded = $false
    foreach ($taskProcess in $taskProcesses) {
        if ($taskRelevant.Contains([uint32]$taskProcess.ParentProcessId) -and $taskRelevant.Add([uint32]$taskProcess.ProcessId)) { $taskAdded = $true }
    }
} while ($taskAdded)
$taskProcesses | Where-Object { $taskRelevant.Contains([uint32]$_.ProcessId) } | ConvertTo-Json -Depth 3
```
