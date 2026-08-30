# WO-SS-20260831-020 — Diagnostic et correction de la fermeture gracieuse Playwright

- **Statut :** `IN_DEVELOPMENT`
- **Date d'ouverture :** 2026-08-31
- **Jalon :** J9 — prérequis runtime de WO-019
- **Base locale :** `542f35246ae7680d6c66d05fb73fb634b5dc1165`
- **Branche :** `codex/j9-playwright-graceful-close`
- **Worktree dédié :** `.tmp/j9-playwright-graceful-close`
- **Work Orders liés :** WO-013 `VALIDATED`, WO-018 `IN_DEVELOPMENT`, WO-019 `OPEN_AWAITING_PREREQUISITES`
- **ADR applicables :** ADR-SS-001 v1.4 et ADR-SS-002 v1.0
- **Réseau fournisseur :** `NOT_AUTHORIZED`
- **Reprise de la campagne WO-019 :** `NOT_AUTHORIZED`
- **Intégration ou production :** `NOT_AUTHORIZED`
- **Nouvel endpoint, allowlist, parseur, schéma ou migration :** `NONE`

## 1. Objectif

Établir le prédicat exact qui provoque les erreurs `RUNTIME_FAILURE` pendant la fermeture normale
du worker Playwright, corriger le défaut au plus petit périmètre et requalifier le nettoyage
synchrone de l'arbre de processus sous les bornes existantes.

Le résultat attendu est une fermeture qui :

- authentifie le frame terminal du worker sans affaiblir le protocole IPC ;
- permet au worker, à Chromium, au contexte et à Playwright de se fermer normalement ;
- conserve l'inventaire exact du processus racine et de ses descendants ;
- termine l'annulation en vol en deux secondes au plus et le nettoyage complet en cinq secondes au
  plus ;
- ne laisse aucun processus possédé, listener ou artefact navigateur ;
- reste fail-closed lorsque l'identité, le frame terminal ou le nettoyage est inconclusif.

Le présent Work Order ne qualifie ni SofaScore, ni la campagne multi-dossier, ni une topologie VPS.

## 2. Autorisation propriétaire

L'autorisation propriétaire reçue le 2026-08-31 est enregistrée exactement comme suit :

```text
J9_RUNTIME_WORK_ORDER_OPENING=AUTHORIZE
J9_RUNTIME_SCOPE=DIAGNOSE_AND_CORRECT_PLAYWRIGHT_GRACEFUL_CLOSE_AND_PROCESS_TREE_CLEANUP
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
J9_INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```

Cette autorisation couvre le diagnostic, les tests de régression et la correction runtime locale.
Elle n'est ni un go réseau, ni une reprise implicite de WO-019, ni une autorisation de modifier les
bornes de sécurité ou le protocole sans preuve et revue explicites.

## 3. Preuve d'entrée faisant autorité

WO-019 a arrêté sa readiness hors ligne après deux exécutions identiques de la qualification J3
Playwright loopback, dont une contre-qualification hors sandbox :

```text
BASE_COMMIT=542f35246ae7680d6c66d05fb73fb634b5dc1165
LOOPBACK_J3=FAIL_14_TESTS_0_FAILURE_12_ERRORS_0_SKIPPED_2_PASS
ERROR_CLASS=RUNTIME_FAILURE_AT_GRACEFUL_CLOSE
OUT_OF_SANDBOX_COUNTER_QUALIFICATION=IDENTICAL_FAILURE
OPERATOR_STOP_SCENARIOS=PASS_2
REPORT_XML_SHA256=6567dbdf590dc5cdd76cfa9c80452324c92fbb00234151c537d943267ad94f37
REPORT_TEXT_SHA256=a9358021bf2299a954138ba69982f98628e4b4f2fcda72038d77c4f7806b62a3
PROVIDER_CALLS=0
RESIDUAL_OWNED_PROCESS_COUNT=0
LISTENER_127_0_0_1_8087_COUNT=0
FORBIDDEN_BROWSER_ARTIFACTS=NONE_FOUND
```

Les douze erreurs surviennent à la sortie des scénarios, dans `campaign.close()` puis
`closeCampaign()` et `terminateSynchronously()`. Les assertions de transport loopback ont été
franchies avant la fermeture. Le code d'entrée agrège néanmoins quatre conditions sous le même
`RUNTIME_FAILURE` : identité exacte, annulation dans la borne, nettoyage dans la borne et absence de
résidu. Aucune cause unique n'est déclarée avant un test discriminant.

Les bornes héritées de WO-013 restent :

```text
STOP_ACKNOWLEDGEMENT_MAX=500ms
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
RESIDUAL_OWNED_PROCESS_COUNT=0
```

## 4. Questions diagnostiques fermées

Le diagnostic doit répondre par preuve reproductible aux questions suivantes :

1. le worker reçoit-il `CLOSE`, termine-t-il `WorkerRuntime.close()` et émet-il exactement
   `CLOSED` ?
2. après `CLOSED`, quelle action parent permet la sortie normale et authentifiée de la JVM enfant ?
3. le canal IPC reste-t-il ouvert alors que le worker attend explicitement sa fermeture ou sa
   terminaison par le parent ?
4. les délais de fermeture Playwright, d'annulation et de nettoyage utilisent-ils un point de
   départ cohérent avec leur contrat respectif ?
5. l'inventaire Windows reste-t-il exact si le processus racine sort pendant qu'un descendant est
   encore observé ?
6. quel prédicat exact produit chaque `RUNTIME_FAILURE` reproduit ?

Une instrumentation éventuelle reste test-visible et bornée à des booléens, durées et comptes. Elle
ne journalise ni PID sensible, ni commande, ni chemin utilisateur, ni URI, ni payload, ni secret.

## 5. Contrat à préserver

- aucune terminaison par nom de processus, glob ou famille de navigateurs ;
- le PID et l'heure de création du worker restent authentifiés avant toute mutation ;
- les descendants sont retenus par identité exacte, y compris après reparentage ;
- `CLOSE`/`CLOSED` reste le seul handshake de fermeture gracieuse ;
- un frame absent, incorrect ou tardif ne devient jamais un succès supposé ;
- le double `close()` reste idempotent ;
- la lease n'est libérée qu'après nettoyage confirmé ;
- un échec après mutation reste terminal et observable ;
- les bornes 500 ms, 2 s et 5 s ne sont ni augmentées, ni contournées silencieusement ;
- chaque campagne conserve un worker, un Chromium et un contexte frais non persistant ;
- aucun profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement n'est conservé ;
- `server.address=127.0.0.1` et tous les flags fournisseur restent bloquants par défaut.

## 6. Périmètre inclus

- `ChildJvmPlaywrightProviderSupervisor` et son contrat de nettoyage exact ;
- le worker Playwright seulement si le diagnostic démontre un défaut côté worker ;
- tests unitaires discriminants du handshake, des horloges et de l'arbre possédé ;
- qualification Chromium réelle exclusivement contre un serveur `127.0.0.1` ;
- requalification loopback J3, J4 et J5, sans accès fournisseur ;
- documentation technique, README, changelog et Work Orders directement concernés.

## 7. Hors périmètre

- tout appel SofaScore ou autre accès fournisseur ;
- toute reprise, sauvegarde/restauration V28 ou demande de go sous WO-019 ;
- endpoint, URI, allowlist, transport métier, parseur, schéma SQL ou migration ;
- retry, fallback, polling, scheduler, live, cache forcé ou contournement ;
- suppression de l'exception ou transformation d'un nettoyage inconclusif en succès ;
- nettoyage asynchrone après retour réussi de `close()` ;
- kill global ou arrêt d'un processus non authentifié ;
- intégration, production, déploiement VPS ou dépendance critique.

Si la correction exige une augmentation de borne, une nouvelle version du protocole IPC ou un
élargissement fonctionnel, l'implémentation s'arrête avec `BLOCKED_REQUIRES_SCOPE_AMENDMENT`.

## 8. Lots d'exécution

1. ouvrir WO-020 sur un worktree propre et vérifier la baseline standard ;
2. reproduire et discriminer le défaut sans réseau fournisseur ;
3. ajouter un test de régression rouge couvrant le mécanisme causal ;
4. appliquer la correction minimale ;
5. exécuter les tests unitaires ciblés et les contrats worker ;
6. exécuter les qualifications loopback J3, puis J4, puis J5 ;
7. exécuter les suites standard et intégration, Compose et `Verify-Local` ;
8. auditer processus, listeners, artefacts, secrets, diff et valeurs par défaut ;
9. soumettre le résultat à la revue propriétaire sans reprendre WO-019.

## 9. Matrice de validation

| Cas | Résultat requis |
|---|---|
| Fermeture nominale J3/J4/J5 | aucune exception, sortie authentifiée, zéro résidu |
| `2xx`, `404` et incidents loopback | sémantiques existantes inchangées |
| `CLOSED` valide puis sortie différée | canal libéré et arbre disparu sous les bornes |
| `CLOSED` absent ou incorrect | échec fermé, nettoyage exact, aucun succès implicite |
| Identité racine ou descendante incomplète | échec fermé sans kill global |
| Arrêt opérateur en vol | ACK ≤ 500 ms, annulation ≤ 2 s, nettoyage ≤ 5 s |
| Double `close()` | idempotent, aucun second signal ou second worker |
| Worker déjà sorti ou erreur d'initialisation | attribution exacte et état terminal cohérent |
| Campagne suivante | nouveau worker et nouveau contexte, aucun état réutilisé |
| Hygiène | zéro réseau fournisseur, secret, brut ou artefact navigateur |

## 10. Vérifications obligatoires

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
git diff --check
```

Chaque qualification Playwright reste explicitement locale et loopback. Les flags fournisseur du
`.env` restent à `false`, les origines/allowlists fournisseur restent vides et aucun lancement
standard ne crée de worker.

## 11. Fichiers candidats

La liste est indicative jusqu'au diagnostic :

```text
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/
  ChildJvmPlaywrightProviderSupervisor.java
src/provider-playwright/java/.../ProviderPlaywrightWorkerMain.java
src/provider-playwright/java/.../ProviderPlaywrightWorkerProtocol.java
src/test/java/.../ChildJvmPlaywrightProviderSupervisorTest.java
src/provider-playwright-qualification-test/java/.../ProviderPlaywrightLocalQualificationIT.java
docs/architecture/J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md
docs/validation/
README.md
CHANGELOG.md
docs/work_orders/active/WO-SS-20260831-018-decision-j9.md
docs/work_orders/active/WO-SS-20260831-019-j9-provider-robustness.md
docs/work_orders/active/WO-SS-20260831-020-j9-playwright-graceful-close.md
```

Un fichier candidat n'est modifié que si une preuve ou la documentation finale l'exige.

## 12. Livraison Git

Les commits locaux prévus sont :

```text
docs(j9): open Playwright graceful close runtime work order
fix(playwright): complete graceful worker termination
docs(j9): qualify Playwright graceful close correction
```

Aucun push, PR, merge vers `main`, payload brut ou artefact navigateur n'est autorisé. Après
validation technique, l'intégration dans `codex/j9-decision` reste linéaire ou fast-forward.

## 13. Portes de statut

```text
IN_DEVELOPMENT
  -> ROOT_CAUSE_ESTABLISHED
  -> CORRECTION_IMPLEMENTED
  -> LOCAL_READINESS_PASS
  -> READY_FOR_OWNER_REVIEW
  -> VALIDATED
```

Terminaux alternatifs :

```text
BLOCKED_REQUIRES_SCOPE_AMENDMENT
FAILED_SAFETY_REGRESSION
CANCELLED
```

Même `LOCAL_READINESS_PASS` n'autorise pas la reprise de WO-019. Une décision propriétaire
distincte restera nécessaire.

## 14. Journal d'exécution

```text
OPENING_BASE=542f35246ae7680d6c66d05fb73fb634b5dc1165
OPENING_BRANCH=codex/j9-playwright-graceful-close
OPENING_WORKTREE=.tmp/j9-playwright-graceful-close
WORKTREE_CLEAN_AT_OPENING=YES
WO020_NUMBER_AVAILABLE=YES
ECLIPSE_CONCURRENT_EDIT=NONE_OBSERVED
BASELINE_STANDARD_VERIFY=PASS_928_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
ROOT_CAUSE_STATUS=UNDER_DIAGNOSIS
IMPLEMENTATION_STATUS=NOT_STARTED
NETWORK_AUTHORIZED=NO
WO019_CAMPAIGN_RESUME_AUTHORIZED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
PROVIDER_CALLS_UNDER_WO020=0
```
