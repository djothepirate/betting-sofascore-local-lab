# WO-058 — Qualification du délai Playwright à vingt secondes

EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Périmètre et autorité

Le 9 septembre, le propriétaire demande un timeout plus élevé pour le prochain
essai après la campagne e7e7684b, arrêtée sur PLAYWRIGHT_TIMEOUT à environ dix
secondes. Référence : [ADR-SS-005 v0.7](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md).
Worktree WO-058, branche feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058,
base de ce complément f2b28eca276e49476425ee0a12ed7bdda54d32df.

Le service autorise vingt secondes uniquement pour live-v5. Le profil local
utilise vingt secondes par défaut ; l’environnement conserve la priorité.
Les campagnes historiques gardent dix secondes ; les coûts d’admission et leurs
preuves ne changent pas. Le réglage local est partagé avec les parcours manuels
Playwright. Il n’allonge pas le parsing ni la persistance métier.

## Résultats du 9 septembre 2026

Java 25.0.4, Maven wrapper, Windows et PostgreSQL de test dans Docker.
Les commandes utilisent `--offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository`.

| Vérification | Résultat |
|---|---|
| `mvnw.cmd -Dtest=LiveCampaignServiceTest,LiveCampaignControllerTest test` | BUILD SUCCESS, 10:48:54Z ; 182 cas, zéro échec, erreur ou ignoré |
| `mvnw.cmd clean verify` | BUILD SUCCESS, 11:01:36Z ; 2 122 cas, zéro échec/erreur, cinq ignorés, durée 6 min 48 s ; inclut 171 tests Failsafe PostgreSQL |
| `mvnw.cmd -Pprovider-playwright-runtime,provider-playwright-local-qualification -DskipTests package` | BUILD SUCCESS, 11:05:38Z ; compilation du worker et de la qualification, aucun test exécuté à cette étape |
| Qualification Chromium native ciblée ci-dessous | BUILD SUCCESS, 11:08:02Z ; deux cas, zéro échec/erreur/ignoré |
| Binding Spring des YAML de production, profil local | `request-timeout=20s`, `server.address=127.0.0.1` |
| `git diff --check` et revue du diff | Réussis ; pas de secret introduit, pas de modification de migration ou des opt-in réseau |

Les cinq tests ignorés standards sont quatre cas de création de liens symboliques
indisponible sur le poste (`LocalJ7ExportFileStoreTest`, `J8BenchmarkExportCommandTest`)
et le scénario `J6NativeBinaryPipelineQualificationTest` soumis à la propriété
opt-in absente `j6.docker.qualification`. Aucun ne concerne le timeout.
Aucun changement de persistance : pas de relance du profil `integration-tests`,
dont les suites PostgreSQL sont déjà exécutées par ce `clean verify`.

Le service accepte vingt secondes en v5 en conservant le profil d’admission.
Zéro, une durée négative et 20 001 ms sont refusés avant acquisition ; les
politiques v1 à v4 refusent toujours les durées supérieures à dix secondes.

## Deux scénarios Chromium réels, exclusivement en loopback

La commande ciblée utilise également les options Maven hors ligne ci-dessus :

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path (Get-Location) '.tmp/provider-playwright-browsers'
.\mvnw.cmd -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  -DskipTests=false -DskipITs=false `
  '-Dit.test=ProviderPlaywrightLocalQualificationIT#acceptsALiveV5J4ResponseAfterTwelveSecondsWithTwentySecondTimeout+timesOutALiveV5J4RequestAtTwentySecondsWithoutRetryAndCleansTheExactProcessTree' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

- Réponse J4 libérée douze secondes après réception par le serveur local : HTTP 200
  reçu en **12 423 ms**, avec corps attendu et latence supérieure à douze secondes.
- Réponse J4 retenue : `TIMEOUT` après **20 106 ms**.
- Chaque scénario produit exactement une requête autorisée, sans retry ni appel
  d’une autre famille. Fermeture et nettoyage de l’arbre de processus exact vérifiés ;
  aucun artefact navigateur interdit ou sortie de worker contenant les données.

Le premier lancement de ces deux cas a échoué avant démarrage du worker :
`PLAYWRIGHT_BROWSERS_PATH` manquait dans le lanceur de qualification temporaire.
Le chemin du cache existant a été renseigné, puis les deux cas réexécutés avec succès,
sans changement du code applicatif. Les rapports de l’échec initial sont conservés.

Les preuves Maven, rapports XML standards archivés et hash du rapport natif sont
conservés localement dans `.tmp/timeout-verification`. Aucun appel SofaScore n’a
été réalisé pour ce complément ; la base opérateur n’a pas été modifiée.

## Livraison et prochain essai opérateur

Le lanceur Eclipse utilise la copie
`C:/Dev/BettingProject/human/betting-sofascore-local-lab`, sur la même base Git.
La livraison des douze fichiers validés est encadrée par un contrôle de copie propre,
de HEAD identique et d’absence d’écoute sur 8087. Les originaux sont sauvegardés ;
chaque copie est contrôlée par SHA-256. Le reçu local `eclipse-delivery.json`
porte le résultat et les empreintes de livraison.

Le Lab a été arrêté après vérification de l’absence de campagne active, conformément
à l’autorisation opérateur de libérer 8087. Redémarrer avec le lanceur Eclipse habituel
après actualisation du projet, puis préparer et lancer manuellement une nouvelle campagne.
Ni le lanceur ni `.env` ne portent d’override du timeout lors de la vérification ;
une future valeur explicite `SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT` primerait sur ce défaut.

La prochaine observation fournisseur reste distincte de cette qualification locale.
Le délai plus long ne corrige pas un HTTP 403 et ne démontre pas la tenue de la cadence
à vingt rencontres avec des réponses lentes. L’arrêt global et l’absence de retry restent
applicables ; aucune campagne n’a été relancée par l’agent.

## Fichiers du complément

- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java`
- `src/main/resources/application-local.yml`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderPlaywrightLocalQualificationIT.java`
- `ADR-SS-005-bounded-local-live-j4-j5-campaigns.md`
- `README.md`
- `CHANGELOG.md`
- `docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/architecture/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/validation/WO058-PLAYWRIGHT-TIMEOUT-20260909.md`
