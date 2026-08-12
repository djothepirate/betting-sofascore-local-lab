# Qualification manuelle Windows J3 — Contrôle local et politiques simulées

## 1. Identification

| Élément | Valeur |
|---|---|
| Projet | SofaScore Local Lab |
| Date de qualification | 2026-08-12 |
| Environnement | Windows local |
| Jalon | J3 — Appel manuel borné |
| Work Order | `WO-SS-20260812-003` |
| Branche | `feat/j3-manual-call` |
| Commit technique qualifié | `f0cd29ccc40ac7c430130630f6e55f17b09f6968` |
| Libellé du commit | `test: cover J3 transport stop and incident policies` |
| Famille | `SCHEDULED_EVENTS` |
| Exposition applicative | `127.0.0.1:8087` |
| Qualification du parcours opérateur local | `PASS` |
| Qualification des politiques simulées | `PASS` |
| Appel SofaScore réel | `NOT_EXECUTED` |
| Preuve de sortie fournisseur réelle | `NOT_AVAILABLE` |
| Résultat de l’unité documentaire | `PASS_WITH_SCOPE_LIMITATION` |

## 2. Portée et limite de la décision

La session qualifie le parcours opérateur Windows visible, les transitions d’arrêt et de confirmation,
ainsi que les politiques de transport exercées exclusivement sur des doubles locaux. Elle ne qualifie
pas une réponse réelle du fournisseur.

Au moment de la session, le point de décision préalable à tout transport réel n’était pas complet :

- aucune autorisation explicite d’une requête réelle n’avait été donnée ;
- aucune URI fournisseur n’était configurée ni autorisée ;
- l’adaptateur fournisseur, le profil réel et l’action réseau restaient bloqués ;
- l’examen par le propriétaire des conditions d’utilisation n’était pas enregistré comme suffisant ;
- le besoin éventuel de cookies, jetons ou données de session côté fournisseur n’avait pas à être
  évalué, puisqu’aucun endpoint réel n’était défini.

Conformément au Work Order, la qualification s’est donc arrêtée avant le transport. Cette absence
d’appel est un résultat de sécurité attendu et non un échec du parcours local.

## 3. Validation automatisée associée

La validation consolidée du commit technique a été exécutée le 2026-08-12 :

```text
VALIDATION_COMMAND=scripts/Verify-Local.ps1 -WithIntegrationTests
PREFLIGHT_JAVA_25=PASS
DOCKER_CHECK=PASS
GUARDRAIL_SCAN=PASS
STANDARD_TESTS=115
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
INTEGRATION_TESTS=5
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
POSTGRESQL_TESTCONTAINERS=18.4
FLYWAY_SCHEMA_VERSION=2
BUILD_GROUP=com.bettingproject
REAL_SOFASCORE_CALLS_EXECUTED=0
RESULT=PASS
```

Les suites couvrent notamment :

- le démarrage sûr avec arrêt global actif et circuit `LOCKED` ;
- l’activation et la confirmation distinctes ;
- l’expiration et l’usage unique de l’intention et du jeton de formulaire ;
- le succès JSON simulé, la persistance du brut avant parsing et la déduplication ;
- les timeouts de connexion et de lecture ;
- `400`, `401`, `403`, `429`, `500` et `503` sans retry automatique ;
- les erreurs d’entrée/sortie, la taille excessive et le contenu sensible ;
- le HTML inattendu et le schéma incompatible ;
- la conservation bornée de `Retry-After` sans planification de retry ;
- l’absence de secret ou de valeur sensible dans les sorties capturées.

## 4. Matrice de qualification manuelle Windows

Les captures fournies par le propriétaire ont été examinées comme preuves visuelles. Elles restent
hors du dépôt : seule cette synthèse textuelle minimisée est versionnée.

| ID | Action ou contrôle | Résultat observé | Statut |
|---|---|---|---|
| J3-WIN-01 | État protégé | Arrêt global actif, circuit `LOCKED`, motif `STARTUP_LOCK`, activation opérateur `NON`, transport fournisseur `INDISPONIBLE` | PASS |
| J3-WIN-02 | Lever l’arrêt global | Arrêt global levé ; le circuit reste `LOCKED` jusqu’à une activation distincte | PASS |
| J3-WIN-03 | Activer le circuit | Circuit `CLOSED`, motif `NONE`, activation opérateur `OUI`, incident actif `NON` | PASS |
| J3-WIN-04 | Préparer une intention datée | Intention `SCHEDULED_EVENTS` en `AWAITING_CONFIRMATION`, clé canonique datée et expiration visible | PASS |
| J3-WIN-05 | Confirmer explicitement | État terminal `CONFIRMED_BLOCKED` et message confirmant qu’aucun transport n’a été exécuté | PASS |
| J3-WIN-06 | Vérifier l’action fournisseur | Bouton fournisseur désactivé avant et après confirmation ; transport `INDISPONIBLE` | PASS |
| J3-WIN-07 | Appliquer l’arrêt global | Circuit revenu à `LOCKED`, motif `OPERATOR_STOP`, activation `NON` | PASS |
| J3-WIN-08 | Annuler l’intention active | Intention passée à `CANCELLED_BY_GLOBAL_STOP` ; nouvelle séquence explicite requise | PASS |
| J3-WIN-09 | Appel réel | Aucun endpoint résolu, aucune requête fournisseur envoyée | PASS — arrêt avant transport attendu |

La valeur exacte de la phrase de confirmation, son identifiant interne et son jeton de formulaire ne
sont pas reproduits dans ce rapport.

## 5. Qualification des politiques d’arrêt et d’incident

| Politique | Preuve | Résultat |
|---|---|---|
| Arrêt opérateur | Transition visuelle vers `LOCKED` et annulation de l’intention | PASS |
| Réarmement | L’arrêt global peut être levé sans activer implicitement le circuit | PASS |
| Activation explicite | Une action distincte est requise avant la préparation | PASS |
| Confirmation explicite | Phrase exacte, acquittement et expiration couverts localement | PASS |
| Transport après confirmation | Reste indisponible ; état `CONFIRMED_BLOCKED` | PASS |
| `400`, `401`, `403` | Arrêt sans retry sur transport simulé | PASS |
| `429` | Circuit ouvert, `Retry-After` borné, aucun retry planifié | PASS |
| `5xx` | Arrêt sans retry automatique | PASS |
| Timeout et erreur d’entrée/sortie | Incident sûr, circuit ouvert, aucun diagnostic sensible exposé | PASS |
| HTML ou schéma incompatible | Brut conservé avant classement, aucune normalisation silencieuse | PASS |
| Payload excessif ou sensible | Rejet borné sans persistance partielle ni exposition de contenu | PASS |

Ces preuves démontrent la politique applicative sur des scénarios locaux déterministes. Elles ne
constituent pas une observation du comportement réel du fournisseur.

## 6. État du point de décision réseau

| Condition préalable du Work Order | État au 2026-08-12 |
|---|---|
| Autorisation explicite d’une requête unique | `NOT_GRANTED` |
| Examen des conditions d’utilisation jugé suffisant par le propriétaire | `NOT_RECORDED` |
| Source et exactitude d’une URI réelle documentées | `NO` |
| Paramètre de date unique validé | `PASS` |
| Tests standards hors ligne | `PASS` |
| Tests d’intégration PostgreSQL | `PASS` |
| Tests de transport sur doubles locaux | `PASS` |
| Boucle locale, concurrence 1, délai 3 s et arrêt global | `PASS` |
| Absence de cookie, jeton ou secret fournisseur requise | `NOT_VERIFIED` |
| Stratégie locale de conservation et de sauvegarde documentée | `PASS` |
| Application et PostgreSQL démarrés localement | `PASS` |
| Procédure d’incident et d’arrêt disponible | `PASS` |

Le point de décision reste `NOT_PASSED`. Les conditions techniques satisfaites ne compensent pas les
conditions d’autorisation, d’URI et de contexte fournisseur manquantes.

## 7. Preuves minimisées collectées

La preuve versionnée se limite à :

1. l’identification du commit technique qualifié ;
2. les comptes de tests et leur résultat ;
3. les états fonctionnels observés dans le tableau de bord ;
4. les codes d’incident et les politiques attendues ;
5. la confirmation explicite qu’aucun appel fournisseur n’a été exécuté.

Ne sont pas versionnés : capture contenant une phrase active, UUID, jeton de formulaire, cookie,
header, URI fournisseur, payload brut, secret, dump PostgreSQL ou donnée de session.

## 8. Bilan

```text
J3_WINDOWS_LOCAL_OPERATOR_QUALIFICATION=PASS
J3_SIMULATED_TRANSPORT_POLICY_QUALIFICATION=PASS
J3_GLOBAL_STOP=PASS
J3_EXPLICIT_CONFIRMATION=PASS
J3_CONFIRMED_STATE=CONFIRMED_BLOCKED
J3_PROVIDER_ACTION_AVAILABLE=NO
J3_PROVIDER_TRANSPORT_ACTIVE=NO
J3_REAL_ENDPOINT_URI=ABSENT
J3_REAL_CALL_AUTHORIZED=NO
J3_REAL_SOFASCORE_CALLS_EXECUTED=0
J3_REAL_OUTPUT_PROOF=NOT_AVAILABLE
J3_POINT_OF_DECISION=NOT_PASSED
J3_WORK_ORDER_STATUS=IN_DEVELOPMENT
VALIDATION_DECISION=TECHNICAL_OFFLINE_QUALIFICATION_ACCEPTED
```

## 9. Décision

La dernière unité documentaire prévue est acceptée pour son périmètre réellement démontré : le
contrôle opérateur Windows, l’arrêt global, la confirmation explicite et les politiques de transport
simulées sont qualifiés.

Le Work Order reste `IN_DEVELOPMENT`. Il ne peut pas passer à `VALIDATED`, car la preuve de sortie J3
« une requête réelle » n’existe pas et aucune autorisation ne permet de la produire. Toute poursuite
vers un endpoint réel exige une nouvelle décision explicite du propriétaire et la satisfaction de
toutes les conditions encore ouvertes de la section 7 du Work Order.
