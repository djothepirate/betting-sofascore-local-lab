# WO-058 — Retour fonctionnel propriétaire du 7 septembre 2026

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Base du correctif : `54d1597bdd0e90c44a3f1e9613a73468248323d9`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`. État : `READY_FOR_REVIEW` ; correctif qualifié,
essais opérateur à reprendre et revue de réalisation toujours ouverte.

## Demande et diagnostic

Le propriétaire confirme le bouton de préparation désactivé sans sélection et activé avec
sélection. Sur des événements déjà terminés du 6 septembre, le POST `/live-campaigns/prepare`
aboutit à un 403, avec ou sans activation Playwright. Il précise qu'aucun live ne doit démarrer
pour un événement dont le statut local initial est `finished`.

La politique `Referrer-Policy: no-referrer` des pages live fait émettre `Origin: null` au POST
de navigation Chromium. L'intercepteur refuse cette origine avant le contrôleur, indépendamment
des opt-ins du fournisseur. Le test navigateur initial ne soumettait pas réellement ce formulaire
et son serveur synthétique ne reproduisait pas cet en-tête. La qualification complémentaire
soumet les formulaires Thymeleaf réels via Chromium et les dirige intégralement vers MockMvc.

## Correction et portée

- Politique `same-origin` sur les pages locales live ; gardes Host/Origin et jetons inchangés.
- Validation de toute la sélection locale, puis exclusion des statuts `finished` avant admission.
  Zéro manifeste/worker/appel quand toutes les rencontres sont terminées ; sinon récapitulatif
  des exclusions et manifeste limité aux rencontres retenues.
- Nouvelle lecture locale au lancement et avant ouverture de la session ; un match devenu terminé
  reste sans appel. Les autres suivent leurs échéances habituelles. Aucun statut sportif ni score
  n'est fabriqué, et le manifeste existant reste immuable.
- La découverte de `finished` par le premier J4 réseau d'un match admis conserve la finalisation
  prévue par ADR-SS-005. L'ADR accepté et sa copie octet pour octet ne sont pas modifiés.
- Aucun changement de migration, de normaliseur, de configuration runtime ou de base opérateur.
  Les preuves et l'inventaire du candidat initial décrivent toujours `54d1597` et restent figés.

## Qualification de ce correctif

Java 25.0.4, Maven wrapper, Docker Desktop et cache Chromium dédié au worktree. Aucun résultat
du commit initial n'est présenté comme preuve du nouveau diff.

| Commande / preuve | Résultat observé |
|---|---|
| `mvnw.cmd clean verify`, terminé le 7 septembre à 11:52:09Z, durée 4 min 55 s | PASS ; 175 suites Surefire, 1 324 tests, 0 échec/erreur, 5 ignorés ; Failsafe hérité : 6 suites, 122 tests, 0 échec/erreur/ignoré |
| `LiveCampaignServiceTest` | 29 tests verts, dont 11 nouveaux scénarios d'éligibilité, de sélection mixte et de changement de statut avant transport |
| `LiveCampaignControllerTest` | 28 tests verts : restitution HTTP 200 des rencontres terminées, exclusions, refus au lancement, historique explicite et gardes existants |
| `SecurityHeadersFilterTest` | 32 tests verts : politique limitée aux pages concernées et refus/protections préservés |
| `scripts/Invoke-LivePlaywrightLoopbackQualification.ps1`, terminé à 11:56:13Z | PASS ; 6 tests, 0 échec/erreur/ignoré : 1 navigateur dynamique, 2 formulaires natifs, 3 transport/session ; contrôles du lanceur verts |

Les 122 tests d'intégration comprennent FlywayMigrationIT (69), J7DeliveryLedgerMigrationIT
(29), J7DeliveryMutualTlsLoopbackIT (4), J7OptionalDeliveryEndToEndIT (2),
J7ProviderOwnerGoV32MigrationIT (2) et LiveCampaignPersistenceIT (16). Aucun code de persistance
ni migration n'a changé ; le profil `-Pintegration-tests verify` n'est pas rejoué en doublon
de ces exécutions héritées. Les 5 tests ignorés restent les quatre cas Windows de liens et le
scénario Docker J6 optionnel à trois passages. Le contrôle J6 exigeant 8087 libre est vert.

Les régressions ajoutées relient AC01/AC04 à : sélection entièrement terminée de 1 et 15 matchs
avec opt-ins désactivés/capacité 1, absence de contrôle de volume, sélection mixte, ID forgé,
limite d'entrée 100, changement de statut avant lancement et pendant l'acquisition, exclusion
individuelle et absence totale de dispatch pour les matchs arrêtés. Les tests de J4 initial
réseau `finished` confirment que le dernier cycle J5 accepté reste présent.

Les deux tests de formulaires soumettent réellement les vues MVC/Thymeleaf dans Chromium,
sur les deux hôtes autorisés. Ils prouvent la préparation/lancement/arrêts, la contre-épreuve
`no-referrer → Origin: null → 403` et le résultat HTTP 200 explicatif pour `finished`, sans
lancement supplémentaire. Le navigateur est hors ligne et toutes ses requêtes sont satisfaites
par MockMvc. Le test de vue dynamique maintient focus, sélection et ordre des révisions.
La qualification de transport a servi 24 requêtes synthétiques dans un seul worker/contexte,
avec espacements et nettoyage vérifiés ; ce contrôle ne vaut pas campagne fournisseur.

Empreintes SHA-256 des artefacts lus (journaux/XML runtime ignorés par Git) :

| Artefact | SHA-256 |
|---|---|
| `.tmp/wo058-feedback-clean-verify.log` | `0848d876219d326e86feae8db4eda85e618640001022f33f14da592951f793ff` |
| XML Surefire `LiveCampaignServiceTest` | `bd45aa3f89eaa688984c5960e6d2ca6eabe51376b014c427cc1078c3dd23cc41` |
| XML Surefire `LiveCampaignControllerTest` | `8f12bd6cf9989c166620bbfbeb98b9ae9696a98ab989556a5c49f8a834318fcd` |
| XML Surefire `SecurityHeadersFilterTest` | `049e010d0c9ee35e52cb30859fa3e2d70a2292fa71e0babbfc9fd9bdff3bf855` |
| XML Failsafe `LiveCampaignPersistenceIT` | `e8a3cc0df3a796d3d9b006985462da015f21971cac4b77d854e9ba5571f6c99d` |
| XML Failsafe `LiveCampaignFormBrowserQualificationIT` | `72e0d5ba9de6c4615175bcd51606cf319d53ab4726c182eb717e0aeb17d53988` |
| XML Failsafe `LiveCampaignBrowserQualificationIT` | `22ec3d07119b4b1e3b862004bc669819807796b6b9d151757e3a52c5c628fa89` |
| XML Failsafe `LiveProviderSessionQualificationIT` | `ec5cb4e67c9bb088acdee76c7a374575e08c1e670ef924173db1a34a9976f4ca` |
| `.tmp/wo058-feedback-live-qualification.log` | `239f4caffaf3bf98a337da4f572c397b8f4c7e99adf3fc2cce37abd50ec35658` |

La revue finale vérifie les 18 fichiers du correctif, leur UTF-8, les liens locaux et les espaces.
Elle ne trouve ni secret ni fichier runtime dans le candidat. Les valeurs réseau par défaut,
les migrations, ADR-SS-002 à 005 et la copie acceptée restent inchangés. Le statut de préparation
affiché pour un match exclu tardivement est explicitement historique : aucune source ni date
d'observation n'est remplacée artificiellement.

## Historique des diagnostics et limites

- Première compilation refusée par le sandbox à la résolution du parent Maven : journal
  `.tmp/wo058-form-browser-compile.log`. La compilation autorisée suivante est verte.
- Première version du harnais Chromium : erreur `ERR_CONNECTION_REFUSED` sur une redirection
  non interceptée. Journal `.tmp/wo058-form-browser-qualification.log`. Le harnais suit désormais
  les redirections locales dans MockMvc et utilise un contexte explicitement hors ligne ; il
  ne peut plus atteindre le listener de l'opérateur. Aucun changement des gardes applicatifs.
- Qualification ciblée suivante verte à 11:45:58Z : deux tests, sans échec/erreur/ignoré, avant
  l'ajout du cas `finished`. Ce succès intermédiaire ne remplace pas la qualification finale.
- Les preuves initiales de `54d1597`, dont leurs échecs historiques, sont conservées.
  Aucun essai fournisseur réel, aucune migration de la base de l'opérateur ni validation propriétaire
  de réalisation n'est revendiqué par ces tests synthétiques.

Le propriétaire a demandé que la dernière action avant restitution soit un commit local des
modifications, pour reprendre les essais Eclipse. Ce commit suit les contrôles et la mise à jour
de cette preuve ; il ne vaut ni publication, ni fusion, ni clôture du WO.
