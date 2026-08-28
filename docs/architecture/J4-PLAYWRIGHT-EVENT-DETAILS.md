# J4 - transport fournisseur manuel Playwright pour `EVENT_DETAILS`

## 1. Decision et statut

`WO-SS-20260827-014` migre les deux parcours fournisseur J4 vers le runtime Playwright commun
livre par `WO-SS-20260827-013`. L'implementation est en cours. La readiness loopback n'est pas
encore executee et aucun appel a SofaScore n'est autorise.

L'ADR-SS-001 v1.4 approuve les deux decisions propres a J4 :

```text
J4_404_CLASSIFICATION=ENDPOINT_UNAVAILABLE
J4_404_PARSE_ATTEMPTED=NO
J4_404_RETRY=NO
J4_PHASE1_404_NEXT_FIXED_TARGET=YES
J4_PHASE2_404_RESULT=COMPLETED_UNAVAILABLE
J4_PHASE1_CACHE_POLICY=FRESH_PARSED_SNAPSHOT_FIRST
J4_PHASE2_CACHE_POLICY=EXPLICIT_MANUAL_REFRESH_NO_CACHE_READ
J4_PHASE2_CACHE_EXCEPTION_SCOPE=ONE_CONFIRMED_EVENT_DETAILS_GET
```

Cette migration ne change ni les imports JSON hors ligne, ni `event-details-v2`, ni les identites
canoniques, ni la persistance append-only. Elle ne cree pas de runtime navigateur J4 autonome.

## 2. Allowlist et identite

Le worker n'accepte pour J4 que :

```text
Famille : EVENT_DETAILS
Methode : GET
Origine : https://www.sofascore.com
Chemin  : /api/v1/event/{EVENT_ID}
EVENT_ID: entier de 1 a 999999999
```

La phase 1 conserve exactement les cibles ordonnees `16386245`, puis `16421052`. Aucun parametre
Web ne peut remplacer cette liste.

La phase 2 part d'un UUID canonique deja affiche. Le serveur relit l'identite, impose son
`providerEventId` positif et lie ces deux valeurs au claim confirme. L'action finale ne recoit ni
URI ni nouvel identifiant fournisseur libre. Un evenement absent de la selection canonique ne peut
pas etre transporte.

Tout port explicite, user-info, query, fragment, autre schema, autre hote, autre methode,
redirection ou route secondaire est refuse. Le profil de qualification peut substituer uniquement
une origine `http://127.0.0.1:<port>` ephemere, sans chemin, user-info, query ni fragment.

## 3. Runtime commun et campagnes

```text
controle J4 confirme
        |
        v
lease ManualProviderRequestCoordinator
        |
        v
PlaywrightProviderCampaignFactory
        |
        v
ChildJvmPlaywrightProviderSupervisor
        |
        v
worker JVM + Chromium + BrowserContext neuf
```

Le worker, le superviseur, l'IPC prive, le controle des routes, la lease et le nettoyage restent
ceux de WO-013. Le protocole transporte seulement la famille logique, l'identifiant borne, le
timeout, le statut, le `Content-Type` borne et au plus 5 Mio d'octets.

En phase 1, les caches frais et parses sont consultes dans l'ordre contractuel avant le premier
transport. Le worker est cree paresseusement au premier cache miss, puis le meme contexte neuf est
utilise pour les autres cibles non servies par le cache. Les cache hits ne demarrent pas Playwright
et n'ajoutent pas d'attente artificielle.

En phase 2, chaque confirmation ouvre une nouvelle campagne et ne lit pas le cache. Elle autorise
au plus un GET. Une campagne suivante utilise un nouveau worker, un nouveau navigateur et un
nouveau contexte. Aucun etat ne traverse deux campagnes ou deux phases.

La lease couvre toute la campagne. Elle interdit qu'une campagne J3 ou J5 s'intercale entre les
deux cibles de phase 1 et conserve le delai minimal commun de trois secondes entre deux debuts de
transport effectifs.

## 4. Reponses et `404`

Le statut, le type utile, la taille, le SHA-256 et les octets de `Response.body()` sont controles
avant parsing. Le DOM, `page.content()`, `Response.text()` et tout reencodage sont interdits.

Un `2xx` suit strictement la chaine J4 existante : brut persiste avant parsing, parseur
`event-details-v2`, verification de l'identifiant, observation canonique et detail append-only.

Un `404` conserve le brut et son empreinte, puis classe le snapshot `ENDPOINT_UNAVAILABLE`. Aucun
parseur ni retry n'est lance :

- en phase 1, la cible courante produit un resultat indisponible et la cible fixe suivante reste
  eligible. Si aucun autre incident ne survient, le controle se reverrouille en succes avec les
  resultats individuels `CACHE`, `PROVIDER` ou `ENDPOINT_UNAVAILABLE` ;
- en phase 2, le resultat metier terminal est `COMPLETED_UNAVAILABLE`. Il s'agit d'un cycle acheve
  sans detail normalise, pas d'une autorisation de nouvelle tentative automatique.

Tout autre non-`2xx`, timeout, redirection, HTML ou challenge, route inattendue, contenu sensible,
corps trop grand ou erreur de persistance est terminal au premier incident. Aucun fallback
`RestClient`, FlareSolverr, import local ou second contexte n'est permis.

## 5. Arret et nettoyage

Les actions d'arret J4 signalent d'abord le superviseur de la campagne en vol, puis reverrouillent
l'intention metier. Apres un arret, aucun nouvel appel et aucun contexte de remplacement ne sont
autorises.

```text
STOP_ACKNOWLEDGEMENT_MAX=500ms
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
RESIDUAL_OWNED_PROCESS_COUNT=0
```

La fermeture cible le PID et l'heure de creation du worker possede, puis ses descendants. Aucun
processus n'est termine par nom. Le contexte, le navigateur, le worker, Playwright et la lease sont
fermes en `finally` sur chaque chemin terminal.

## 6. Configuration et commandes explicites

Le build standard ne contient pas le runtime Playwright et reste inerte. Les valeurs persistantes
sures restent notamment :

```text
SOFASCORE_PLAYWRIGHT_ENABLED=false
SOFASCORE_PLAYWRIGHT_WORKER_JAR=
SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION=false
SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN=
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
```

L'installation historique `scripts/Install-J3PlaywrightRuntime.ps1` installe le Chromium epingle
du runtime desormais commun. Une campagne J4 eventuellement autorisee utilise le lanceur dedie
`scripts/Start-J4PlaywrightLocal.ps1`. La readiness locale utilise exclusivement
`scripts/Invoke-J4PlaywrightLoopbackQualification.ps1` contre `127.0.0.1`.

Ni le profil Maven, ni l'installation, ni le lanceur, ni l'ouverture de l'interface ne constituent
un claim ou une autorisation fournisseur. La preparation, la confirmation, l'acquittement et
l'action finale restent distincts.

## 7. Portes de qualification

La validation doit couvrir l'inertie au demarrage, les claims invalides, les cache hits phase 1,
les deux cibles ordonnees dans un seul contexte, la phase 2 sans lecture cache, le `404` propre a
chaque phase, tous les incidents terminaux, l'arret en vol, la lease partagee et une nouvelle
campagne sans etat reutilise.

Les suites Maven standard et PostgreSQL ne lancent jamais Chromium et ne contactent jamais
SofaScore. Le vrai Chromium est reserve au script loopback opt-in. Une readiness verte ne vaut ni
qualification fournisseur, ni autorisation de cloture.

```text
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STATUS=COMPLETED
LOCAL_READINESS=PASS
TECHNICAL_QUALIFICATION_PROVIDER_ACCESS=NO
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_28
J3_PLAYWRIGHT_REGRESSION_CHECK=PASS_NO_REGRESSION_IDENTIFIED
CLOSURE=AUTHORIZED_BY_OWNER_2026_08_28
```
