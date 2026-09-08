# WO-058 — Sélection live-v4, annulation et groupe J5 manuel

Date : 2026-09-08. Base : `be1f631474a71d316cd938b3dfed15335e5eaf5a`.
Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Demandes et résultat attendu

Le propriétaire signale les rencontres `notstarted` non sélectionnables, demande l’annulation
des préparations non lancées et étend explicitement la suppression des pauses internes à la
collecte J5 manuelle. [ADR-SS-005 v0.5](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md)
consigne cette extension ; aucun endpoint n’est ajouté et aucune collecte fournisseur n’est
lancée par la réalisation ou les tests.

## Diagnostic et configuration Eclipse

La page affichait une capacité qualifiée de zéro. Le lanceur
`SofaScore - PLAYWRIGHT J3-J5 (MANUEL - OPT-IN) (LIVE)` contenait le plafond historique 20,
mais aucune variable `SOFASCORE_LIVE_GROUPED_*`. Les rafraîchissements J3/J4 ne pouvaient
donc pas lever ce blocage de configuration.

Les neuf variables du [profil groupé mesuré](WO058-GROUPED-LIVE-V4-PROFILE-20260908.json)
ont été ajoutées au seul fichier de lanceur live dans les métadonnées Eclipse locales.
Le SHA-256 du profil a été recalculé :
`5d34019578a5f1b616f3570aa47d8451afe283eb52dee73e1c94eeb4f370fcc1`.
Une sauvegarde locale octet pour octet `.launch.before-live-v4-20260908.bak` a précédé
l’écriture. Retirer les neuf lignes ajoutées reproduit exactement le contenu précédent :
les autres paramètres, secrets et opt-ins n’ont pas été modifiés ou publiés.
Ni le lanceur ni sa sauvegarde ne sont ajoutés au dépôt.

Le probe local `LauncherV4BindingProbe.java` charge les seules variables de capacité et de
groupe depuis le XML, résout `application.yml` avec le binding Spring, puis exécute l’admission
de production. Résultats : **0 depuis la sauvegarde, 10 depuis le lanceur corrigé**, code 0.
Le script de lancement ne réécrit pas ces variables ; `application-local.yml` ne les remplace pas.
Le plafond opérateur 20 est conservé, mais l’admission de ce profil borne la capacité v4 à 10.
Le [runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) donne les valeurs et le redémarrage d’Eclipse
nécessaire pour garantir la relecture d’une configuration modifiée sur disque.

Cette vérification ne démarre pas Spring Boot, ne contacte pas PostgreSQL et n’ouvre aucun
navigateur. Elle ne remplace pas l’observation de la page après redémarrage par l’opérateur.
La qualification du profil garde sa portée loopback de 64 Kio et ne prouve pas la fraîcheur
réelle face au site SofaScore.

## Annulation des préparations

Le POST local `cancel-preparation` exige le jeton de formulaire et le hash du manifeste.
Le bouton apparaît uniquement pour `PREPARED`. La transaction verrouille la campagne comme
le lancement et ajoute les transitions `STOPPED_OPERATOR` / `PREPARATION_CANCELLED` aux
événements et à la campagne. Elle conserve le manifeste, les observations, les compteurs
nuls et l’absence de propriétaire et de démarrage. Aucune migration n’est nécessaire.

Une préparation expirée reste annulable. Une seconde annulation ne crée pas de transition
supplémentaire. Si le lancement a gagné, l’action est refusée ; si l’annulation gagne après
acquisition du lease, le nettoyage libère celui-ci sans remplacer le motif d’annulation.
Le polling masque les formulaires de préparation lorsqu’un autre onglet l’a annulée.

## Collecte J5 manuelle

La collecte confirmée d’un événement emploie une autorité `MANUAL_J5` et un groupe serveur
distinct de `live-v4`, dans l’ordre statistiques, incidents, compositions. Le coordinateur
et le superviseur suppriment leurs pauses internes uniquement dans ce groupe. Les contrôles
de claim, arrêt, événement, ordre et propriétaire sont effectués avant chaque dispatch.
Réception, conservation et traitement restent individuels et séquentiels.

Une famille HTTP 404 reste conservée comme indisponible puis la suivante est admissible.
Les autres erreurs gardent leur portée terminale. Un quatrième appel, un changement
d’événement, un ordre invalide ou un groupe réutilisé sont refusés. Trois secondes restent
appliquées aux transitions de collecte, y compris J4 → J5 et J5 → live.

## Vérifications

Résultats lus dans les XML Surefire/Failsafe effectifs, capturés séparément avant les commandes
suivantes. Les totaux, empreintes et fichiers modifiés sont conservés dans le
[relevé de vérification](WO058-PREPARATION-MANUAL-J5-VERIFICATION-20260908.json).

| Commande / exécution | Résultat effectif | Fin UTC |
| --- | --- | --- |
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify` | PASS — Surefire : 1 738 cas, 0 échec, 0 erreur, 5 ignorés ; Failsafe : 151 cas, 0 échec, 0 erreur, 0 ignoré | 13:19:41 |
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pintegration-tests verify` | PASS — mêmes totaux, nouveau passage complet ; 39 cas `LiveCampaignPersistenceIT`, dont annulation et courses avec le lancement | 13:27:05 |
| Packaging du worker avec `-Pprovider-playwright-runtime,provider-playwright-local-qualification -DskipTests package` | PASS — compilation et packaging uniquement, aucun résultat de test attribué à cette commande | 13:30:13 |
| Qualification Chromium ciblée ci-dessous | PASS — 3 suites, 6 cas, 0 échec, 0 erreur, 0 ignoré | 13:31:41 |

Les cinq cas ignorés sont quatre contrôles de liens symboliques non exécutables dans cet
environnement Windows et la qualification native J6 opt-in ; aucun test de ce complément
n’est ignoré. Le mode Maven offline utilise les dépendances déjà présentes. Il ne remplace
pas les contrôles réseau des tests : les tests Chromium dédiés utilisent uniquement loopback.

Commande native effective, après packaging, avec `PLAYWRIGHT_BROWSERS_PATH` et
`provider.playwright.browser-cache` pointant vers le même cache local, téléchargements désactivés :

```powershell
.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-DskipTests=false' '-DskipITs=false' `
  '-Dit.test=ManualJ5GroupedQualificationIT,LiveGroupedDegradationQualificationIT,LiveCampaignFormBrowserQualificationIT' `
  '-Dprovider.playwright.browser-cache=C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5/.tmp/provider-playwright-browsers' `
  'failsafe:integration-test@provider-playwright-loopback-qualification' `
  'failsafe:verify@provider-playwright-loopback-qualification'
```

`ManualJ5GroupedQualificationIT` reçoit dix réponses dans son scénario de transitions et deux
triplets. Les quatre écarts internes mesurés sont **61, 62, 63 et 62 ms**. Les transitions J4,
entre deux collectes et vers live respectent la barrière de trois secondes ; le 404 incidents
est suivi des compositions. Son second scénario arrête le claim après une réponse : aucun
départ de continuation. `LiveGroupedDegradationQualificationIT` conserve l’arrêt entre appels
et le traitement d’un 404 retardé. Les deux parcours de formulaire Chromium passent sur
`localhost` et `127.0.0.1`. Nettoyage physique : PASS ; appels réels fournisseur : zéro.
Ces écarts loopback vérifient l’absence de pause ajoutée, pas la latence du fournisseur.

Deux imports manquants ont d’abord
arrêté la compilation principale puis celle des tests ; ils ont été corrigés. Un passage
Surefire a ensuite exécuté 1 738 cas avec deux échecs dans les nouveaux tests HTTP : ils
attendaient 403 pour un jeton absent/réutilisé, alors que `InvalidLocalFormTokenException`
renvoie historiquement 400. Seules les attentes de ces tests ont été corrigées, avec assertion
du type d’exception ; le refus d’origine externe reste 403. Ces échecs initiaux sont conservés
dans les logs locaux r1/r2/r3 et ne sont pas comptés comme qualifications réussies.

La revue indépendante des chemins d’annulation et de transport J5 n’a pas relevé de défaut
bloquant. Le natif J5 utilise un coordinateur sans store PostgreSQL et un claim d’arrêt simulé :
il mesure les deux couches de temporisation, l’adaptateur et Chromium. Le contrôle applicatif,
les publications et la persistance sont vérifiés séparément. Il n’y a pas de nouveau test
Chromium d’annulation depuis un second onglet ; ce cas utilise le masquage de préparation du
lecteur JavaScript existant, relu et conservé.

Les preuves historiques de cadence
live-v4 et les échecs historiques d’enveloppe de 5 Mio restent inchangés ; ce complément
ne requalifie pas une charge soutenue ni un parcours fournisseur.

Contrôles du diff : UTF-8 strict, espaces Git et syntaxe JavaScript/PowerShell conformes ;
aucun secret détecté dans les ajouts, aucune migration existante ni preuve de profil modifiée.
L’écoute reste `127.0.0.1`, les opt-ins fournisseur restent désactivés par défaut. Le lanceur
opérateur n’a pas été redémarré par ces vérifications. La reprise opérateur est décrite dans
le runbook ; revue humaine et fusion restent distinctes de ce complément local.
