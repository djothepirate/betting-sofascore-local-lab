# WO-058 — Récupération opérateur et accueil pendant une clôture en attente

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Base du complément : `d375213`. Demande du 8 septembre 2026 : captures opérateur
après redémarrage, clôture de la session interrompue, réutilisation de la sélection,
puis trace de l’erreur HTTP 500 de l’accueil.

## Récupération constatée

Les captures ont été recoupées avec les lectures locales HTTP et une transaction
PostgreSQL `BEGIN READ ONLY`, sans commande fournisseur ni modification de la campagne.
Les heures suivantes sont en Europe/Paris (UTC+02:00).

| Étape | Preuve |
| --- | --- |
| Ancienne campagne `60fd08dd-074e-4994-a0d5-51aff9eaa9db` reconnue interrompue | Transition `INTERRUPTED`, motif `OWNER_PROCESS_ABSENT`, à 23:21:19.333916 ; révision 11783. |
| Clôture orpheline effectuée par l’opérateur | Transition `LOCAL_CLEANUP_VERIFIED` à 23:24:55.679104 ; révision 11844. L’état historique de campagne reste `INTERRUPTED`. |
| Historique conservé | 2 189 appels, 44 027 545 octets, deux rencontres `FINISHED_CONFIRMED` et quinze `INTERRUPTED`. |
| Nouvelle préparation issue des 17 rencontres | La capture indique deux exclusions déjà `finished`, Al-Qadsiah — Al-Ahli et Al-Ittihad — Al-Fayha ; quinze rencontres retenues. |
| Nouvelle campagne `c74d878b-7ecf-4156-9c21-34c7a8d06453` | Lancement opérateur à 23:25:42.679650 ; `COMPLETED` à 23:27:17.683546 ; révision 496. |
| Bilan des quinze rencontres | Toutes `FINISHED_CONFIRMED`, `finalComplete=true`, quatre appels chacune, zéro cycle manqué et aucune échéance restante. |
| Répartition des 60 appels | Quinze `EVENT_DETAILS`, quinze `EVENT_INCIDENTS`, quinze `EVENT_STATISTICS` et quinze `EVENT_LINEUPS`. Dernière réception à 23:27:17.306. Total reçu : 2 299 868 octets. |
| Verrou fournisseur après les opérations | `FREE`, aucune campagne propriétaire, génération 45, dernière modification à 23:29:08.271978. |

Le parcours après redémarrage a donc réellement abouti. `INTERRUPTED` est conservé
pour décrire la fin anormale de l’ancienne collecte ; cet état ne signifie plus que
sa clôture locale reste à effectuer. Le motif `OWNER_PROCESS_ABSENT` est établi lors
de la reprise et ne reconstitue pas la cause de l’arrêt initial à 22:18.

La nouvelle campagne est un rattrapage final : J4 retrouve les matchs terminés puis
les trois familles J5 sont collectées une fois. Les quelque 95 secondes entre le
lancement et `COMPLETED` mesurent ce passage précis, pas une cadence soutenue qualifiée
pour quinze rencontres encore en jeu.

La pagination HTTP a aussi été vérifiée sur les deux états : 10 puis 7 rencontres
pour l’ancienne campagne ; 10 puis 5 pour la nouvelle. Les totaux et le nombre de
pages sont cohérents. Les lectures ne déclenchent aucune collecte.

## Cause prouvée de l’erreur HTTP 500 de l’accueil

La console transmise par l’utilisateur comporte deux erreurs à 23:22:40.266 et
23:22:49.303, concordant avec la capture de l’accueil. La chaîne utile est :

```text
J6RetentionException: PROVIDER_CAMPAIGN_ACTIVE
JdbcJ6RawPayloadRetentionStore.requireProviderQuiescent
JdbcJ6RawPayloadRetentionStore.preview
J6RawPayloadRetentionService.preview
DashboardController.dashboard
```

Le contrôleur calcule un aperçu de rétention pour un panneau de l’accueil. Ce calcul
est refusé quand le verrou fournisseur n’est pas libre ou qu’une campagne durable
reste active. C’est également le cas après une interruption tant que sa clôture locale
n’est pas validée. `J6RetentionException` hérite directement de `RuntimeException` et
échappe au bloc de repli qui capturait d’autres types : l’indisponibilité de ce panneau
fait ainsi échouer toute la page. Les routes `/events` et des campagnes restent lisibles.

Le retour de l’accueil après la clôture est cohérent avec cette cause ; un GET local
ultérieur confirme HTTP 200. Flyway V40 est appliqué avec succès. Les avertissements
Tomcat de connexion interrompue sont distincts de la trace causale ci-dessus.

## Correctif borné

Le contrôleur traite uniquement le refus métier `PROVIDER_CAMPAIGN_ACTIVE` comme
un aperçu temporairement indisponible, avec un message couvrant campagne active ou
clôture non finalisée. Le reste du tableau de bord reste accessible. Aucun aperçu vide,
zéro fictif, liste de candidats, hash ou phrase de purge n’est fabriqué.

Les autres codes métier et les exceptions d’invariant sont relancés. Le repli générique
existant pour `DataAccessException` reste disponible. Ce changement concerne seulement
le bloc de rétention, pas le catalogue des tournois. La sonde de stockage, les cadences,
la persistance, le verrou fournisseur et le protocole de purge ne changent pas.

La purge conserve sa transaction `SERIALIZABLE` et la vérification verrouillée de
l’exclusion fournisseur. Rendre l’accueil lisible ne permet donc pas de purger pendant
une campagne ni de forcer une clôture.

## Qualification du complément

Les treize cas du contrôleur d’accueil réussissent dans la passe ciblée de 232 cas
du 8 septembre à 21:49:34 UTC, sans échec, erreur ou ignoré. Ils couvrent `/` et
`/dashboard`, le refus fournisseur, la disparition de l’avertissement après libération,
le repli sur erreur d’accès aux données et la propagation des autres erreurs métier
ou d’invariant. Aucun plan de rétention vide n’est substitué au refus.

La qualification Chromium finale à 22:01:05 UTC réussit également le rendu d’accueil
avec ce refus exact en large et à 390 pixels, sans plan ni commande de purge. Toutes
les réponses viennent de MockMvc en mémoire. Les commandes et les quatre scénarios
réussis du complément sont détaillés dans le [rapport graphique](WO058-INCIDENT-GRAPHICS-20260908.md).
La suite complète du complément, après la convention finale des penalties, réussit
le 8 septembre à **22:19:24 UTC** : **1 912 cas Surefire, dont cinq ignorés, et 166 cas
Failsafe ; aucun échec ni erreur**, en 7 min 26 s. Les quatre limites de liens symboliques
Windows et la qualification Docker J6 opt-in non activée expliquent les cinq ignorés.
Le journal `.tmp/incident-graphics-clean-verify-r3.log` et les décomptes XML conservés
appartiennent à cette exécution ; aucun résultat des commits précédents n’est repris
comme preuve du nouveau code.
La passe Chromium finale après cette convention réussit aussi les quatre scénarios
à **22:22:33 UTC**, dont l’accueil indisponible pour rétention mais lisible en HTTP 200.

## Fichiers concernés

- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/DashboardController.java`
- `src/main/resources/templates/dashboard.html`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/DashboardControllerTest.java`
- `README.md`, `CHANGELOG.md`, runbook live, WO-058 actif, notes historiques et présent rapport.
