# J9 — Qualification WO-037 de la frontière d’origine navigateur J7

## 1. Identité de la preuve

```text
REPORT_ID=J9-WO037-J7-BROWSER-ORIGIN-BOUNDARY-QUALIFICATION-20260903
WORK_ORDER=WO-SS-20260903-037-j9-j7-browser-origin-boundary
BASE_COMMIT=f4f53aa24b4a8a764819bdb7cf79c87482bd7244
RUNTIME_COMMIT=f28e4b6954c0fb703923f770ab9156326e212a07
QUALIFIED_HARNESS_COMMIT=2596f0592496b2ba84893c8f4ef2cf0185d46f8f
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
```

Ce rapport ne contient pas sa propre empreinte. Le SHA-256 est calculé après finalisation puis
consigné dans les documents qui référencent cette preuve et dans le bloc soumis au propriétaire.

## 2. Autorité et périmètre

Le propriétaire a autorisé une correction runtime distincte et une qualification dans un vrai
navigateur sur la seule interface loopback. La direction imposée consistait à limiter
`Referrer-Policy: same-origin` aux pages J7 concernées, sans accepter d’origine opaque.

```text
J9_WO037_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO037_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_NATIVE_BROWSER_REFERRER_POLICY_AND_LOCAL_ORIGIN_BOUNDARY_COMPATIBILITY
J9_WO037_LOOPBACK_BROWSER_QUALIFICATION_AUTHORIZED=YES
J9_WO037_PREFERRED_DIRECTION=SCOPE_SAME_ORIGIN_REFERRER_POLICY_TO_J7_DELIVERY_WITHOUT_ACCEPTING_OPAQUE_ORIGINS
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

La qualification couvre le filtre d’en-têtes, la frontière Spring MVC, les contrôleurs et le
template J7 de production. Les services de jeton de formulaire, de confirmation et la gate
d’exécution de production sont réels et restent en mémoire ; les services d’export, de lecture, de
livraison runtime et le ledger sont des doubles synthétiques. Aucune base de données, aucun ledger
durable, aucun certificat, aucun transport receiver et aucun composant fournisseur ne sont
construits.

## 3. Preuve antérieure préservée

WO-036 avait établi qu’une page J7 servie avec `Referrer-Policy: no-referrer` amenait Brave à
envoyer `Origin: null` lors du `POST` natif vers `delivery/prepare`. La frontière locale avait
correctement répondu `403` avant le contrôleur, le claim et le receiver.

Le présent rapport référence cette preuve sans la modifier :

```text
WO036_STATUS=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_REPORT=docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-20260903.md
WO036_REPORT_SHA256=5244422187f0bc591e3ce57f20b076037f64bad266ad9052b985d8127b475994
WO036_RESUME_AFTER_WO037_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
```

Le manifeste gelé de WO-036, son rapport d’arrêt et son Work Order n’ont pas été réécrits sous
WO-037.

## 4. Correction runtime qualifiée

Le commit `f28e4b6954c0fb703923f770ab9156326e212a07` modifie uniquement la sélection de
`Referrer-Policy` dans `SecurityHeadersFilter` et ses tests :

| Surface | Politique après correction | Qualification |
|---|---|---|
| `/events/{canonicalEventId}/exports` | `same-origin` | `PASS` |
| `/events/{canonicalEventId}/exports/` | `same-origin` | `PASS` |
| `/events/{canonicalEventId}/exports/{exportId}` | `same-origin` | `PASS` |
| actions sous `/events/{canonicalEventId}/exports/**` | `same-origin` | `PASS` |
| même sous-arbre sous un `contextPath` retiré | `same-origin` | `PASS` |
| routes représentatives hors du sous-arbre J7 et chemins adjacents | `no-referrer` | `PASS` |
| chemins adjacents, préfixés ou hostiles | `no-referrer` | `PASS` |

Les autres en-têtes restent inchangés : cache strict, `Pragma`, `Expires`, CSP,
`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` et `X-Robots-Tag`.

`J7DeliveryLocalRequestBoundaryInterceptor` n’a pas été élargi. Ses règles restent :

```text
HOST=127.0.0.1:8087_EXACTLY_ONCE
ORIGIN=ABSENT_OR_HTTP_127_0_0_1_8087_EXACTLY_ONCE
ORIGIN_NULL=REFUSE
FORWARDED=REFUSE
X_FORWARDED_HOST=REFUSE
X_FORWARDED_PROTO=REFUSE
BOUNDARY_SELECTOR=RESOLVED_J7_DELIVERY_HANDLER_METHOD
```

## 5. Harnais navigateur explicite

Le commit `2596f0592496b2ba84893c8f4ef2cf0185d46f8f` ajoute :

- le profil Maven explicite `j7-browser-origin-loopback-qualification` ;
- `scripts/Invoke-J7BrowserOriginLoopbackQualification.ps1` ;
- huit tests Pester des gardes du lanceur ;
- `J7BrowserOriginLoopbackQualificationIT`, isolé de la suite standard.

Le lanceur refuse de télécharger un navigateur. Il exige un cache Chromium dédié déjà présent,
un unique headless shell complet et les ports `8087` et `8444` libres avant exécution. Il rétablit
la variable de processus `PLAYWRIGHT_BROWSERS_PATH` puis réexamine les deux ports après le run.

Le test démarre Spring Boot/Tomcat sur `127.0.0.1:8087`, importe le filtre, l’intercepteur, les
contrôleurs, le template, le service de jeton, le service de confirmation et la gate d’exécution
réels. Il remplace uniquement l’export, la lecture, la livraison runtime et le ledger par des
doubles synthétiques en mémoire. Le contexte Chromium est neuf, non persistant, interdit les
téléchargements, bloque les service workers et intercepte toute requête hors des origines loopback
explicitement attendues.

Ni HAR, ni trace, ni vidéo, ni capture, ni téléchargement, ni `storageState` ne sont activés.

## 6. Observations du navigateur réel

### 6.1 Parcours depuis l’origine locale exacte

Chromium a chargé l’aperçu J7 réel puis soumis le formulaire HTML natif de préparation :

| Observation | Résultat |
|---|---|
| aperçu J7 | HTTP `200` |
| `Referrer-Policy` de l’aperçu | `same-origin` |
| cache/CSP/anti-frame/anti-MIME/anti-indexation | présents et exacts |
| nombre de formulaires POST initiaux | `1` |
| cible du formulaire | `delivery/prepare` canonique |
| méthode observée | `POST` |
| Host observé | une valeur exacte `127.0.0.1:8087` |
| Origin observé | une valeur exacte `http://127.0.0.1:8087` |
| en-têtes forwarded | absents |
| réponse de préparation | HTTP `200` |
| contrôleur atteint | `YES` |
| vue de confirmation rendue | `YES` |

La présence du formulaire d’exécution et de sa phrase de confirmation a seulement démontré que la
préparation avait atteint le contrôleur. Ce formulaire n’a pas été soumis.

### 6.2 Contre-preuve d’origine opaque

Dans le même contexte neuf, une page créée avec une origine opaque a été vérifiée comme
`location.origin == "null"`, puis a soumis nativement le même formulaire de préparation.

| Observation | Résultat |
|---|---|
| Host observé | une valeur exacte `127.0.0.1:8087` |
| Origin observé | une valeur littérale `null` |
| en-têtes forwarded | absents |
| réponse | HTTP `403` |
| corps de réponse | vide |
| contrôleur atteint | `NO` |

Cette contre-preuve établit que la correction réconcilie le formulaire same-origin sans accepter
une origine opaque.

## 7. Absence d’effets interdits

Les compteurs et interactions vérifiés à la fin du parcours sont :

```text
DELIVERY_EXECUTE_REQUESTS=0
RECONCILIATION_REQUESTS=0
DELIVERY_RUNTIME_SERVICE_CALLS=0
DELIVERY_LEDGER_CLAIMS=0
RECEIVER_CALLS=0
PROVIDER_CALLS=0
NON_LOOPBACK_BROWSER_CALLS=0
BROWSER_DOWNLOADS=0
FORBIDDEN_BROWSER_ARTIFACTS=0
```

Le service runtime de livraison n’a jamais reçu `deliver` et le ledger synthétique n’a jamais reçu
`claim`. Le port receiver `8444` était libre aux contrôles préflight et postcondition ; aucun
receiver n’a été construit ou appelé.

## 8. Cleanup

La fermeture qualifiée couvre le contexte, le navigateur, Playwright et l’application Spring. Les
identités de processus descendants capturées sont confrontées au PID et à l’instant de démarrage
afin d’éviter une attribution par PID seul.

```text
OWNED_BROWSER_PROCESS_RESIDUALS=0
LOCAL_LAB_LISTENER_8087_RESIDUALS=0
RECEIVER_LISTENER_8444_RESIDUALS=0
FORBIDDEN_ARTIFACT_RESIDUALS=0
```

## 9. Vérifications consolidées

| Porte | Tests | Échecs | Erreurs | Ignorés | Suites | Résultat |
|---|---:|---:|---:|---:|---:|---|
| Surefire | 1132 | 0 | 0 | 5 | 164 | `PASS` |
| Failsafe | 85 | 0 | 0 | 0 | 4 | `PASS` |
| Pester WO-037 | 8 | 0 | 0 | 0 | 1 | `PASS` |
| Chromium WO-037 | 1 | 0 | 0 | 0 | 1 | `PASS` |

```text
MVNW_CLEAN_VERIFY=PASS
MVNW_INTEGRATION_TESTS_VERIFY=PASS
TARGETED_SECURITY_AND_DELIVERY_TESTS=42
TARGETED_SECURITY_AND_DELIVERY_FAILURES=0
DOCKER_COMPOSE_CONFIG=PASS
GIT_DIFF_CHECK=PASS
MODIFIED_TEXT_UTF8_STRICT=PASS
MODIFIED_TEXT_UTF8_BOM_COUNT=0
MODIFIED_TEXT_NUL_COUNT=0
ADDED_SECRET_PATTERN_HITS=0
WO036_PROTECTED_DIFF_LINES=0
WO036_FROZEN_MANIFEST_SHA256=e996e631fdc905c91b8288852fbba7b878023985db7d5bdd228dccdaeefbe960
WO036_FROZEN_STOP_REPORT_SHA256=5244422187f0bc591e3ce57f20b076037f64bad266ad9052b985d8127b475994
SERVER_ADDRESS_DEFAULT=127.0.0.1
PROVIDER_DEFAULT_FLAGS=BLOCKED
REAL_RECEIVER_DEFAULT_FLAGS=BLOCKED
STANDARD_TEST_PROVIDER_CALLS=0
STANDARD_TEST_REAL_RECEIVER_CALLS=0
```

Aucun endpoint fournisseur, schéma SQL, migration, contrat J7, ACK, état de livraison, propriété
mTLS ou origine receiver n’a été modifié.

## 10. Conclusion

La cause locale établie par WO-036 est corrigée sans affaiblir la frontière. La page J7 conserve
désormais une origine same-origin utilisable par le navigateur ; la préparation exacte atteint le
contrôleur en HTTP `200`, tandis que `Origin: null` reste refusé en HTTP `403` avant contrôleur.

```text
WO037_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO037_LOCAL_READINESS=PASS
WO037_OWNER_REVIEW_REQUIRED=YES
WO037_WORK_ORDER_MOVE_TO_COMPLETED=NO
WO036_STATUS=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

WO-037 reste actif jusqu’à décision propriétaire. Une validation de WO-037 n’autorise pas la
reprise de WO-036 : cette reprise exige une décision distincte, un nouveau manifeste et une nouvelle
campagne complète liée au commit et au JAR qualifiés.
