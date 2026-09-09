# WO-058 — personnes J4/J5 et calendrier live-v7

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Périmètre de la demande

Le propriétaire a demandé les buts et passes décisives sur les cartes joueurs, les
pays sous forme de drapeaux, les entraîneurs et l’arbitre J4, le nom du tour et les
incidents repliables. Il a également demandé une nouvelle cadence : groupe initial
complet, contrôle à T−60 min, compositions toutes les cinq minutes jusqu’à T−5 min,
silence jusqu’au coup d’envoi, puis J4 toutes les minutes jusqu’au début confirmé et
les quatre familles toutes les minutes pendant le jeu.

La décision T−5 min → coup d’envoi a été confirmée explicitement. Les essais réels,
l’augmentation des plafonds partagés et les modifications du lanceur Eclipse ne sont
pas exécutés par cette qualification. Les refus 403/429 gardent leur suspension
persistante ; aucune rotation d’adresse ou nouvelle route fournisseur n’est ajoutée.

## Implémentation et compatibilité

- Parseurs `event-details-v4` et `event-lineups-v4`, colonnes facultatives V46,
  empreintes des champs nouveaux et conservation des observations historiques.
- Pays des titulaires, remplaçants et indisponibles ; entraîneurs/arbitre avec pays ;
  `roundInfo.name` valide prioritaire sur le numéro de secours.
- 260 SVG locaux, manifeste SHA-256 et licence MIT de flag-icons 7.3.2 ; icône de
  chaussure originale, compteurs compacts et aucun panneau statistique vide.
- Sections incidents et périodes repliables ; ouverture et focus conservés au
  rafraîchissement ; affichage sans JavaScript toujours utilisable.
- Politique `live-v7` et migration V47 ajoutées sans réinterpréter les manifestes
  v1–v6. Départs sérialisés, plafonds partagés et suspension persistante conservés.

## Environnement et séparation des preuves

Java 25.0.4, Spring Boot 4.1.0, wrapper Maven, PostgreSQL jetable dans Docker Desktop,
Chromium dédié au laboratoire. Les commandes Maven emploient `--offline` pour la
résolution des dépendances. Les tests de navigateur et de transport utilisent des
réponses synthétiques locales ; aucun appel SofaScore ni base opérateur n’est utilisé.

Le test natif prolongé est exécuté dans le worktree WO-058. Pendant sa durée, une
copie de qualification indépendante reçoit les tests standards/PostgreSQL et une
autre reçoit la qualification d’interface. Leurs sorties sont séparées ; aucune
compilation ne remplace les classes ni le JAR du transport en cours d’exécution.
Cette charge locale concurrente fait partie des conditions observées.

Les états réels sont documentés séparément dans le
[bilan de la campagne 4087041a](WO058-REAL-CAMPAIGN-4087041A-20260909.md) et le
[relevé des débits historiques](WO058-HISTORICAL-DEPARTURE-RATES-20260909.md).
Ils ne qualifient pas les changements v7 ni un seuil d’acceptation du fournisseur.

## Résultats

| Contrôle | Preuve | Résultat |
| --- | --- | --- |
| Unitaire ciblé | `GroupedLiveScheduleV7Test`, `LineupsPresentationTest` | 33 tests, zéro échec ou erreur : 23 contrôles du calendrier v7, dont le recalage `delayed`, et 10 contrôles de présentation. |
| Qualification J6 | `J6NativeBinaryPipelineQualificationTest` | 4 tests, zéro échec ou erreur, un skip explicite. La présence du Lab opérateur sur `127.0.0.1:8087` est reconnue comme ligne de base ; seul un écouteur nouveau ferait échouer le contrôle. |
| Vérification standard | `mvnw.cmd --offline … clean verify` | 2 129 tests Surefire, cinq skips explicités, et 219 tests Failsafe ; zéro échec ou erreur, terminé le 9 septembre à 20:25:00Z. |
| Vérification d'intégration explicite | `mvnw.cmd --offline … -Pintegration-tests verify` | Même résultat fonctionnel : 2 129 tests Surefire, cinq skips explicités, et 219 tests Failsafe ; zéro échec ou erreur, terminé à 20:35:10Z. |
| Navigateur/transport en boucle locale | Profiles `provider-playwright-runtime,provider-playwright-local-qualification` | 7 tests Failsafe, zéro échec, erreur ou skip, terminé à 20:41:37Z. Les sorties déclarent `REAL_PROVIDER_CALLS=0`, `HTTP_POSTS=0` et `DATABASE_USED=false`. |
| Exécution soutenue v7 | Manifeste local de 35 min | Trois scénarios de rencontre, 2 100,020 s, 420 tentatives/départs durables et zéro cycle manqué. SHA-256 du manifeste : `884E816B9FEF1F2904B71B27314BC6FDDED37E85A96A3A12C76186961AEFBA23`. |

Les commandes Maven ont utilisé le dépôt local Maven et les réponses synthétiques ; les
tests n'ont contacté ni SofaScore ni la base de l'opérateur. Le rapport Failsafe de la
qualification navigateur est conservé sous `target/failsafe-reports/failsafe-summary.xml` :
sept cas terminés, zéro erreur, échec ou skip.

L'exécution soutenue vérifie le comportement du planificateur dans un environnement
local avec trois scénarios. Elle n'accorde pas à elle seule une capacité fournisseur, ni
un seuil d'acceptation, ni une admission supplémentaire. Le profil `live-v7` reste
explicitement plafonné à **trois rencontres** et nécessite son empreinte de qualification
ainsi que ses huit enveloppes avant qu'une nouvelle préparation puisse proposer une
capacité positive.

Les campagnes existantes conservent le manifeste et la politique avec lesquels elles ont
été créées : une campagne v6 ne devient pas v7 à la suite de ce changement. Aucun
redémarrage du Lab de l'opérateur, lancement de campagne réelle, modification de lanceur
Eclipse, rotation d'adresse ou requête fournisseur n'a été effectué pour cette
qualification.
