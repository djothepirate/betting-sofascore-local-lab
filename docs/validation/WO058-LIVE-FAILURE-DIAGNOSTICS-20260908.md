# WO-058 — Diagnostic séparé de l’arrêt et de la clôture live

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Base du complément : `b80d05e` (pagination déjà vérifiée). Demande du propriétaire le
8 septembre 2026 après arrêt de son Lab : identifier également la cause de l’arrêt
de la campagne `60fd08dd-074e-4994-a0d5-51aff9eaa9db`.

## Problème et portée

La campagne s’est arrêtée après la réception de 22:18:46 Europe/Paris. Les
[preuves historiques](WO058-LIVE-CLEANUP-INCIDENT-20260908.md) placent la piste la plus
étayée dans le contrôle de stockage avant réservation. Elles ne permettent pas de
récupérer l’exception initiale ni de prouver un dépassement des deux secondes de la sonde.

Dans la version observée, une erreur de budget, de sonde ou de réservation avant l’appel
remonte au `catch` de la boucle sans tentative `FAILED`. Le résultat du lancement est
déjà communiqué : `completeExceptionally` ne transmet alors plus cette nouvelle erreur.
Un échec ultérieur de clôture est lui aussi absorbé. `LOCAL_CLEANUP_PENDING` masque donc
deux événements distincts et ne suffit pas au diagnostic.

Ce complément conserve des observations de l’exécution courante :

- `firstFailure` : première erreur d’exécution, fixée avant une éventuelle erreur
  secondaire pendant l’enregistrement d’un résultat `FAILED` ;
- `cleanupFailure` : dernier échec de tentative de clôture, séparé de la première erreur ;
- pour chacun, une phase contrôlée, un code connu et un instant UTC ;
- les mêmes informations dans le JSON local et les journaux, sans exception brute,
  stacktrace, message arbitraire, commande, payload ou secret ;
- un encart global repliable sur la campagne, actualisé même si la révision du ledger
  ne change pas, afin de conserver ces informations avant un redémarrage.

Il n’invente aucune tentative avant réservation et ne modifie ni les états durables,
ni les budgets/cadences, ni le timeout Docker, ni le protocole de clôture. La garde et
la vérification des processus continuent de conditionner la libération. Aucun nouveau
fournisseur, endpoint, retry automatique ou redémarrage du transport n’est ajouté.

Ces diagnostics en mémoire et les lignes de console ne reconstruisent pas la cause de
l’incident passé. Ils disparaissent de l’API au redémarrage ou quand la session est
libérée ; aucune migration de conservation durable n’est introduite.

## Qualification

Java 25.0.4, Maven Wrapper, Spring Boot 4.1.0, Windows. Le cache Maven local et le
cache Chromium déjà installé sont utilisés ; les scénarios ne font aucun appel fournisseur.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-Dtest=LiveCampaignCleanupTest,LiveCampaignServiceTest,LiveCampaignPresentationTest,LiveCampaignControllerTest' test
```

À **21:06:06 UTC : 246 cas réussis**, zéro échec, erreur ou ignoré : 24 clôture,
61 service, 47 présentation et 114 contrôleur. Cette commande compile également le scénario
navigateur. Les pannes simulées couvrent les trois points avant réservation effective,
la conservation de la première erreur malgré deux étapes de clôture en échec, l’absence
de reprise sur lecture, la projection JSON et l’exclusion de messages/causes sensibles.

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = (Resolve-Path .tmp/provider-playwright-browsers).Path
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-DskipTests=false' '-DskipITs=false' `
  '-Dit.test=LiveCampaignPaginationBrowserQualificationIT' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  'failsafe:integration-test@provider-playwright-loopback-qualification' `
  'failsafe:verify@provider-playwright-loopback-qualification'
```

À **21:07:43 UTC : 1 scénario Chromium réussi**, zéro échec, erreur ou ignoré.
Les réponses HTML/JSON proviennent de MockMvc et sont interceptées en mémoire, sans
socket sur le port opérateur. Le scénario vérifie absence, première erreur seule,
clôture seule au rendu initial, deux erreurs, changement de clôture à révision 3 inchangée,
maintien de l’ouverture/fermeture et du focus, puis masquage et effacement des champs.
La pagination 10/7 et les actions globales restent vérifiées dans ce même parcours.

La capture synthétique `.tmp/live-pagination-qualification/live-stop-diagnostic.png`
a été relue : un seul encart, les deux diagnostics lisibles et séparés, aucun chevauchement.
Elle illustre une panne injectée ; ses codes ne sont pas la preuve de la cause historique.

Journaux propres à ces exécutions : `.tmp/live-diagnostic-targeted.log` et
`.tmp/live-diagnostic-browser.log`, ignorés Git. La revue indépendante de la cause
initiale, de la clôture et de la confidentialité des champs ne relève aucun défaut bloquant.
`node --check src/main/resources/static/js/live-campaign.js` et `git diff --check` réussissent.

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' clean verify
```

À **21:15:30 UTC : `BUILD SUCCESS`**, durée 7 min 13 s. Surefire : **1 869 cas,
zéro échec ou erreur, 5 ignorés** ; Failsafe : **166 cas, zéro échec, erreur ou ignoré**.
Les cinq cas ignorés correspondent à quatre contrôles de liens symboliques indisponibles
dans cet environnement Windows et à la qualification J6 complète exigeant son opt-in
explicite. Le contrôle négatif de ce parcours a bien été exécuté. Le journal de cette
vérification est `.tmp/live-diagnostic-clean-verify.log`, ignoré Git.

La vérification complète a été lancée après l’arrêt du Lab par le propriétaire, avec le
port 8087 libre. Ses conteneurs Testcontainers sont des ressources de test ; aucun appel
fournisseur ni redémarrage de la campagne opérateur n’a été déclenché.

## Fichiers du complément

| Portée | Fichiers |
| --- | --- |
| Diagnostic et capture | `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignDiagnostic.java`, `LiveCampaignService.java` |
| Projection publique | `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java` |
| Encart initial et dynamique | `src/main/resources/templates/live-campaign.html`, `src/main/resources/static/js/live-campaign.js` |
| Pannes et projection | `src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignCleanupTest.java`, `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java`, `LiveCampaignControllerTest.java` |
| Navigateur synthétique | `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPaginationBrowserQualificationIT.java` |
| Documentation | `CHANGELOG.md`, runbook live, WO-058 actif, note historique et présent rapport |

## Exploitation

Lors d’un arrêt, ouvrir « Diagnostic de l’arrêt » et conserver la première erreur et
le dernier échec de clôture avec leurs heures. Les journaux permettent de les corréler
à l’identifiant de campagne. Un code générique ne permet pas d’affirmer que la cause
était un timeout Docker, un problème PostgreSQL ou un crash de navigateur.

L’ancienne campagne a ensuite été clôturée par le parcours opérateur après redémarrage,
décrit dans le [runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md). La transition
`LOCAL_CLEANUP_VERIFIED` est confirmée à 23:24:55.679104 Europe/Paris dans le
[rapport de récupération](WO058-RECOVERY-DASHBOARD-20260908.md). Ce résultat ne prouve pas
qu’un bouton de clôture déjà bloqué deviendrait réessayable dans la même JVM.
