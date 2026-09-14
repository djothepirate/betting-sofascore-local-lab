# WO-060 — Erreurs de programmation J3 sur le tableau de bord

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Symptôme et reproduction

Le 14 septembre 2026 à 13 h 31, la saisie d'un déclenchement à **12 h 35 le même jour**
sur `/j3/plans` affiche Whitelabel HTTP 500. Les captures montrent auparavant une collecte
manuelle du calendrier du 2 septembre réussie, puis un horaire du 14 septembre à 13 h 30
réussi. La date du calendrier à collecter et l'instant du déclenchement ont des contraintes
différentes : le calendrier peut être historique, mais le déclenchement doit être futur.

Le test `J3WebApplicationIT.invalidPastPlanReturnsToDashboardWithoutChangingOrders`
reproduit l'exception avant correction, avec le vrai contexte Web, le runtime J3 et PostgreSQL
18.4 jetable. Le fournisseur est désactivé et sa fabrique doublée, sans interaction admise.
Le test termine en erreur le 14 septembre à **11:39:13 UTC** :

~~~text
InvalidDataAccessApiUsageException: J3_PLAN_MUST_BE_FUTURE
  EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible
  PersistenceExceptionTranslationInterceptor.invoke
Caused by: IllegalArgumentException: J3_PLAN_MUST_BE_FUTURE
  JdbcJ3AutomationStore.schedule
~~~

Le dépôt JDBC refuse correctement l'horaire passé. La traduction d'exceptions activée par
JPA transforme ce refus en `InvalidDataAccessApiUsageException`. Le contrôleur ne capturait
que `IllegalArgumentException` et `IllegalStateException`. Un test MVC avec runtime simulé
ou un proxy transactionnel sans traduction JPA ne reproduisait donc pas ce chemin.

## Changement

Le contrôleur reconnaît l'enveloppe Spring uniquement si sa cause est un refus de
programmation connu : instant passé, identité/révision concurrente, ordre déjà admis ou
limite de plans. Toute autre erreur de persistance continue de se propager ; elle n'est pas
présentée comme une faute de saisie. Le schéma, les transactions et le runtime ne changent pas.

Les champs `date`, `at` et `offset` sont validés dans l'action `/j3/plans`, après contrôle
de l'origine et consommation du jeton local. La lecture ISO est stricte, notamment pour les
dates impossibles. Les champs absents/vides, les formats incorrects, l'heure inexistante,
l'heure doublée sans choix et les secondes non admises disposent d'un message en français.

Le refus redirige vers le tableau de bord, ancre `#j3-automation`, avec une alerte accessible
dans **Collecte automatique**. La date valide du calendrier est conservée dans la navigation.
Le formulaire soumis conserve les valeurs, l'identité de règle et la révision. Une modification
refusée rouvre le formulaire de l'ordre concerné, sans le transformer en nouvelle programmation.
Les valeurs de formulaire sont échappées par Thymeleaf ; aucun message technique n'est affiché.
Le GET renouvelle le jeton consommé et permet un nouvel enregistrement après correction.

Message du scénario signalé :

> La date et l’heure de déclenchement sont déjà passées. Choisissez un horaire futur à Paris.

Les attributs HTML `required` restent présents. Le serveur traite également les requêtes qui
n'ont pas été bloquées par la validation native du navigateur. Aucun plan ni préférence
n'est écrit en cas de refus et les catalogues conservés ne changent pas.

## Vérifications

- Reproduction avant correction : **FAIL attendu**, une erreur Web sur le refus traduit
  `J3_PLAN_MUST_BE_FUTURE` ; 23 tests MVC préexistants passaient dans la même commande.
- Qualification ciblée après correction : **PASS**, le 14 septembre à **11:52:00 UTC**, en
  **42,567 secondes** : 44 cas `J3AutomationControllerTest`, 13 cas `DashboardControllerTest`
  et 6 cas `J3WebApplicationIT`, sans échec, erreur ni exclusion.
- Les scénarios PostgreSQL vérifient les deux hôtes locaux, le refus sans mutation, le
  message réellement rendu sous l'ancre d'automatisation, la conservation des champs,
  le renouvellement du jeton, la correction acceptée pour un calendrier historique,
  puis la modification refusée/acceptée d'un plan existant avec la bonne identité.
- Les champs absents, vides ou impossibles sont également exercés dans le contexte complet.
  Les tests MVC couvrent les motifs de changement d'heure, l'offset mal formé, la borne
  minute, les refus Spring reconnus et la propagation d'une erreur de persistance étrangère.
  Une origine opaque ou un mauvais jeton reste refusé même avec une date invalide.

Qualification globale : **PASS**, `mvnw.cmd clean verify`, terminé le 14 septembre à
**12:05:37 UTC** en **13 min 08 s**. La lecture des rapports XML confirme :

| Phase | Suites | Tests recensés | Échecs | Erreurs | Exclus |
|---|---:|---:|---:|---:|---:|
| Surefire | 236 | 2 408 | 0 | 0 | 5 |
| Failsafe / PostgreSQL | 17 | 281 | 0 | 0 | 0 |

Les cinq exclusions préexistantes sont quatre cas de liens symboliques/reparse points
soumis aux capacités Windows et la qualification native J6 à activation distincte.
Les six scénarios Web J3/PostgreSQL sont exécutés, sans exclusion. Tous les essais restent
hors fournisseur ; aucune instance opérateur ni base opérateur ne sert à la qualification.

Le diff ne contient aucune modification de migration, de paramètres du transport ou de
configuration serveur. `server.address=127.0.0.1`, les opt-in fournisseur désactivés par défaut
et le profil live bloqué restent conservés. `git diff --check` et le décodage UTF-8 strict des
neuf fichiers du correctif passent. Le scan des fichiers modifiés applique les neuf règles
de secrets à haute confiance de `ci/check-no-secrets.sh` : aucun signal détecté.

Les essais intermédiaires ont corrigé un import ambigu de matcher dans le test, remplacé
les assertions XML par le parseur HTML déjà disponible dans Thymeleaf, et annulé les plans
synthétiques après leur scénario pour éviter une collision entre les deux hôtes sur la même
base de test. Ces essais incomplets ne sont pas comptés comme des preuves vertes.

Commandes exécutées depuis le worktree WO-060, avec Java **25.0.4** :

~~~powershell
.\mvnw.cmd "-Dtest=J3AutomationControllerTest" "-Dit.test=J3WebApplicationIT#invalidPastPlanReturnsToDashboardWithoutChangingOrders" verify
# Avant correctif : reproduction de l'exception Spring.

.\mvnw.cmd "-Dtest=J3AutomationControllerTest,DashboardControllerTest" "-Dit.test=J3WebApplicationIT" verify
# Après correctif : 57 tests Surefire et 6 tests Failsafe, tous verts.

.\mvnw.cmd clean verify
git diff --check
~~~

Le correctif n'ajoute ni migration ni changement de persistance. Le `clean verify` du projet
exécute déjà Failsafe et ses tests PostgreSQL ; le profil supplémentaire `integration-tests`
n'est donc pas répété pour cette correction Web.

Traces locales ignorées par Git :

| Trace | SHA-256 |
|---|---|
| `.tmp/wo060-j3-date-reproduction.log` | `f878cad8c97385a9817e2b8df22b440588c65029fb926b1bce8720c39263d9b2` |
| `.tmp/wo060-j3-date-targeted.log` | `47bc5dc2a689316db956622c8862ab2d5c824fb5ab1b2fd7fddccd83f4ecc241` |
| `.tmp/wo060-j3-date-clean-verify.log` | `124c87055d3eb781e13ac114bd6fc273bfab9d4130c182ac437d5ade7c2bc79d` |

## Fichiers du correctif

- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/J3AutomationController.java` :
  validation locale, reconnaissance bornée du refus traduit et restitution du formulaire.
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/J3Presentation.java` : messages
  de validation en français.
- `src/main/resources/templates/dashboard.html` : alerte dans le bloc visé, aide, valeurs
  conservées et formulaire de modification rouvert.
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/J3AutomationControllerTest.java` :
  champs absents/invalides, motifs horaires, enveloppe Spring bornée et sécurité des refus.
- `src/test/java/com/bettingproject/sofascorelocal/integration/J3WebApplicationIT.java` :
  refus, rendu, conservation des ordres et correction/revalidation sur PostgreSQL réel isolé.
- `CHANGELOG.md`, guide J3, WO-060 et présent rapport : comportement et preuves alignés.

## Livraison locale

Base du correctif : `37d8af2934ae4f857cc0c9dba0d1d2f37e60c307`, également constatée
dans le worktree HUMAN propre au début du diagnostic. Les modifications sont réalisées
dans le worktree CODEX du WO-060. La base opérateur et le serveur actif n'ont pas été modifiés
pour qualifier ce correctif. La revue humaine et la fusion de la PR restent nécessaires
avant clôture effective du WO.

Le patch `.tmp/WO060-J3-DATE-VALIDATION-CORRECTIF.patch` contient les trois fichiers applicatifs
et les deux fichiers de tests. Il mesure **30 997 octets**, SHA-256
`a8fab5ec8d51810d85c5791156925e1b9367575ad04296aa159e122f1666cb83`.
Le contrôle `git apply --check` réussit sur la copie HUMAN au commit de base ; ce contrôle
ne modifie pas ses fichiers. Les documents et le présent rapport restent dans le worktree WO.

Pour activer le correctif dans Eclipse, arrêter le Lab, appliquer le patch dans le projet HUMAN,
actualiser/recompiler le projet puis relancer l'application. Revenir ensuite au tableau de bord
et le recharger. Un déclenchement passé doit afficher l'alerte de validation dans le bloc
**Collecte automatique** ; modifier l'heure et enregistrer utilise le formulaire conservé.
