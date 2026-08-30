# Runbook J8 — Benchmark local reproductible

## 1. Objet et autorisations

Ce runbook qualifie la lecture HTML et l'export Markdown J8 à partir de PostgreSQL local. La recette
normale n'exécute aucun appel fournisseur et ne nécessite aucun opt-in réseau.

```text
WORK_ORDER_STATUS=READY_FOR_HUMAN_QUALIFICATION
PROVIDER_CALL_REQUIRED=NO
PROVIDER_CAMPAIGN_AUTHORIZED=SECOND_GO_CONSUMED_2026_08_30
PROVIDER_CAMPAIGN_RESULT=MEASURED_COMPLETED_20_OF_20
PLAYWRIGHT_REQUIRED=NO
POLLING_OR_SCHEDULING=NO
LOAD_TEST=NO
FINAL_BENCHMARK_REPORT=NOT_CREATED_PENDING_TARGETED_HUMAN_REVIEW
```

La section 10 conserve le protocole normatif d'une campagne fournisseur prospective. Le go explicite
et borné initial du 2026-08-30 a produit une fenêtre partielle. Après qualification de V15, un
second go distinct a été consommé par une seule exécution complète et mesurée. Aucun des deux go
n'autorise une reprise ou une troisième campagne. La réussite des tests, de la page ou de l'export
ne vaut jamais un nouveau go.

## 2. Prérequis

- Windows 11, Java 25 et Docker Desktop ;
- branche portant le Work Order 016 actif et le Work Order 017 validé puis archivé ;
- PostgreSQL local sain, avec Flyway V28 ; V27 reste la migration ayant créé le ledger J8 et V28
  autorise seulement le parseur incidents V15 ;
- application liée exclusivement à `127.0.0.1:8087` ;
- configurations J3, découverte tournoi, J4 et J5 désactivées et contrôles persistants `LOCKED` ;
- aucun worker Playwright, appel fournisseur, campagne ou tâche planifiée en cours ;
- aucun outil n'écrivant simultanément les preuves J4 à J8 pendant la comparaison reproductible ;
- répertoire `exports/j8` local, ignoré par Git et accessible en écriture pour l'export one-shot.

La présence d'une campagne J8 en base ne l'autorise pas. Pour la recette sans réseau, seules les
preuves déjà persistées sont lues. Si les prérequis sûrs ne peuvent pas être établis, arrêter et
consigner `J8_HUMAN_QUALIFICATION=NOT_RUN`.

## 3. Porte technique avant recette

Avec toutes les voies fournisseur verrouillées, exécuter sur le diff final :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config
git diff --check
```

Consigner les résultats réels dans
`docs/validation/J8-TECHNICAL-READINESS-20260829.md`. Une commande non exécutée, un échec, un appel
réel ou un démarrage Playwright interdit la qualification. Ne jamais remplacer `NOT_RUN` par
`PASS` sans sortie réelle relue.

Contrôler également :

```powershell
git status --short
git check-ignore .env exports\j8\control.md
Select-String -Path .\src\main\resources\application.yml -Pattern '127\.0\.0\.1'
```

Vérifier que V27 et V28 sont append-only, que V1 à V26 n'ont pas été réécrites et qu'aucun rapport
runtime, payload, secret, cookie ou jeton n'est suivi par Git. V28 ne doit contenir aucun DML ni
modifier les cinq tables du ledger créé par V27.

## 4. Démarrage local sans réseau

Depuis la racine du dépôt :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Avant d'ouvrir J8 :

1. vérifier que l'application écoute uniquement sur `127.0.0.1:8087` ;
2. vérifier J3, découverte tournoi, J4 et J5 à `LOCKED`, sans intention `READY` ou `EXECUTING` ;
3. vérifier qu'aucune campagne J8 n'est en cours d'exécution ; une campagne inachevée conservée
   après incident reste lisible comme preuve partielle et ne doit jamais être effacée ;
4. vérifier l'absence de polling, scheduler et worker Playwright ;
5. ne cliquer sur aucun contrôle fournisseur d'un autre jalon pendant la recette.

Ouvrir ensuite `http://127.0.0.1:8087/benchmark`.

## 5. Recette de la fenêtre et des réponses HTTP

### 5.1 Historique mesurable

Ouvrir `/benchmark` sans paramètre. Vérifier :

- réponse HTML 200 ;
- fenêtre annoncée comme tout l'historique mesurable jusqu'à un `asOf` UTC unique ;
- `asOf`, hash SHA-256 de population et état global visibles ;
- aucune valeur absente transformée en zéro ;
- population vide rendue `NOT_MEASURED` avec HTTP 200 ;
- aucun bouton ni action de collecte, POST, JSON, téléchargement, jeton ou action fournisseur ;
  le formulaire GET de filtrage de la fenêtre reste strictement en lecture seule.

### 5.2 Fenêtre UTC explicite

Choisir deux instants UTC correspondant à une population locale connue, puis ouvrir :

```text
http://127.0.0.1:8087/benchmark?from=<Instant Z>&to=<Instant Z>
```

Vérifier la sémantique `[from,to)`, l'inclusion de `from`, l'exclusion de `to` et l'absence de
preuve postérieure à `asOf`. Une fin future syntaxiquement valide est acceptée puis bornée à
`asOf`; une fenêtre entièrement future devient `[asOf,asOf)` et rend `200 NOT_MEASURED`, sans
lecture SQL ni valeur inventée.

### 5.3 Refus attendus

Vérifier sans donnée sensible dans la réponse :

| Essai | Résultat attendu |
|---|---|
| seulement `from` ou seulement `to` | 400 `INVALID_BENCHMARK_WINDOW` |
| chaîne vide, contrôle, plus de 64 caractères | 400 `INVALID_BENCHMARK_WINDOW` |
| instant sans suffixe `Z` ou avec `+01:00` | 400 `INVALID_BENCHMARK_WINDOW` |
| format invalide, `from = to` ou `from > to` | 400 `INVALID_BENCHMARK_WINDOW` |
| preuve locale volontairement incohérente dans un test contrôlé | 422 `INCOHERENT_LOCAL_EVIDENCE` |
| PostgreSQL arrêté dans un test contrôlé | 503 `LOCAL_DATABASE_UNAVAILABLE` |
| `Accept: application/json` | 406 |
| `POST /benchmark` | 405 |

Ne pas altérer la base de l'opérateur pour provoquer 422. Ce cas est qualifié par les tests
d'intégration. Les réponses, y compris `/benchmark;jsessionid=...`, doivent conserver `no-store`,
`no-cache`, expiration immédiate et `X-Robots-Tag: noindex, nofollow, noarchive`.

### 5.4 Contrôle visuel local sans JavaScript

Avec tous les connecteurs fournisseur et automatismes désactivés, inspecter la même page au moyen
du navigateur local :

1. en viewport desktop, vérifier la hiérarchie, la lisibilité des cartes et tableaux, la phrase
   « agrégation locale — aucun appel fournisseur » et l'absence de contrôle de collecte ;
2. en viewport étroit, vérifier que le contenu reste lisible, que les tableaux défilent dans leur
   conteneur et qu'aucun élément ne provoque de débordement horizontal de la page ;
3. confirmer l'absence de balise `script`, de JavaScript exécuté, d'appel fournisseur et de
   démarrage Playwright fournisseur pendant cette inspection.

Consigner séparément `J8_VISUAL_DESKTOP` et `J8_VISUAL_NARROW`. Tant que l'inspection réelle n'a
pas été effectuée, conserver honnêtement `NOT_RUN`.

## 6. Lecture des niveaux de preuve

Pour chaque population affichée, relever le niveau annoncé :

- `FULL_ATTEMPT_LEDGER` : ledger prospectif exact pour les unités déclarées et leurs tentatives,
  y compris une campagne inachevée, une unité non atteinte ou une tentative sans snapshot ;
- `RESPONSE_ONLY` : occurrences directes `INSERTED`/`DEDUPLICATED`, réponses persistées seulement ;
- `LEGACY_BASELINE` : snapshot historique, sans nombre d'acquisitions.

Vérifier que :

- une baseline ne contribue ni au compte d'appels, ni au taux d'erreur, ni à la déduplication ;
- un import manuel J3 scheduled, J3 découverte tournoi ou J5, une fixture et un cache hit sont
  séparés et valent zéro tentative ;
- `RESPONSE_ONLY` reste `PARTIAL` pour une métrique qui exigerait tous les appels ;
- des populations aux dénominateurs incompatibles ne sont pas additionnées ;
- une absence de preuve rend `NOT_MEASURED`, pas zéro.

## 7. Lecture des métriques

### 7.1 Endpoint, stabilité et latence

Pour chaque endpoint logique, vérifier les appels ou réponses éligibles, succès parsés, 404
indisponibles, refus, schémas incompatibles, contenus inattendus, erreurs de transport, autres
erreurs, déduplications et versions de parseur.

Les latences publient `n`, minimum, P50, P95 et maximum. Pour un petit lot contrôlable, trier les
latences et vérifier les rangs `ceil(0,50*n)` et `ceil(0,95*n)`. Si `n=0`, les valeurs restent
absentes et l'état est `NOT_MEASURED`.

### 7.2 Complétude et corrections J6

Vérifier que `COMPLETE`, `PARTIAL`, `EMPTY_VALID`, `UNAVAILABLE`, `MISSING` et incompatible restent
distincts. Contrôler les ventilations par compétition, saison et état seulement lorsqu'elles sont
documentées par la population.

Les corrections tardives ne comptent que `LATE_ENRICHMENT` et `LATE_CORRECTION` de provenance
fournisseur. Un reparsing, une fixture synthétique, un doublon technique ou une version
sémantiquement inchangée ne doit pas augmenter ces comptes.

### 7.3 Coût et dossiers

Un dossier ciblé correspond à un `provider_event_id` distinct d'une unité J4 phase 1, J4 phase 2
ou J5 `GUARDED_PROVIDER`. Vérifier qu'un dossier exploitable possède à `asOf` état, détail et trois
familles J5 directs ; les familles acceptées sont `COMPLETE`, `PARTIAL` et `EMPTY_VALID`. Une
absence, `UNAVAILABLE`, incompatibilité, import-only ou fixture exclut le dossier. Un dossier strict
exige les trois familles à `COMPLETE`.

Recalculer sur une petite population :

```text
discoveryOverhead = tentatives J3 + tentatives de découverte tournoi

marginalCallsPerExploitableDossier =
  (tentatives J4 phase 2 + tentatives J5) / dossiers directs exploitables

effectiveCallsPerExploitableDossier =
  toutes les tentatives directes de la fenêtre / dossiers directs exploitables
```

Le premier résultat est un compte absolu. Si le dénominateur vaut zéro, les deux ratios sont
`NOT_MEASURED`. Les numérateurs et dénominateurs restent visibles pour éviter qu'un arrondi ne
masque le calcul.

## 8. Export Markdown reproductible

Arrêter d'abord l'application normale afin qu'aucune campagne ne soit en cours d'exécution. Une
campagne inachevée après incident n'interdit pas l'export : sa tentative orpheline doit rester
visible comme `INCOMPLETE_ATTEMPT` et rendre le rapport `PARTIAL`. Choisir `From`, `To` et `AsOf` en
UTC avec `from < to <= asOf`, puis lancer :

```powershell
.\scripts\Export-J8Benchmark.ps1 `
  -From '<Instant Z>' `
  -To '<Instant Z>' `
  -AsOf '<Instant Z>'
```

Le script doit :

1. lancer un exporteur Spring one-shot non-Web ;
2. forcer tous les gates fournisseur à `false` ;
3. ne démarrer aucun navigateur, worker, cache, polling ou appel ;
4. créer automatiquement
   `exports/j8/J8-BENCHMARK-REPORT-<asOf UTC compact>.md` ;
5. refuser un chemin de sortie fourni par l'opérateur et ne pas écraser un fichier arbitraire ;
6. afficher le chemin final et son SHA-256.

Les trois valeurs brutes sont bornées à 64 caractères avant tout `Trim` et tout caractère ISO de
contrôle est refusé. Le script sauvegarde et restaure toutes les variables d'environnement de
processus qu'il force. L'exporteur n'accepte
aucun argument CLI : sa fenêtre provient exclusivement de ces trois variables bornées. Avant le
refresh Spring, une source de propriétés de priorité maximale impose le mode non-Web et désactive
les gates fournisseur, Playwright, polling et scheduling, même si `.env` contient des valeurs plus
permissives.

Avant la seconde exécution, déplacer le premier fichier vers un second nom borné sous le même
répertoire `exports/j8/` ; l'exporteur utilise `CREATE_NEW` et refuse tout écrasement. `exports` et
`exports/j8` doivent être des répertoires physiques directs du dépôt : tout lien symbolique,
jonction ou autre reparse point est refusé avant l'écriture. Relancer avec
exactement la même base, la même fenêtre et le même `AsOf`. Comparer le hash de population, le
SHA-256 des deux fichiers et leurs octets : ils doivent être identiques. Le Markdown doit
porter les mêmes mesures que l'HTML construit depuis le même `J8BenchmarkReport`, sans payload,
URI, header, cookie, jeton, secret ni chemin de base de données.

Le fichier sous `exports/j8` reste runtime et ignoré par Git. Ne pas créer de rapport final dans
`docs/benchmark` pendant cette qualification préparatoire.

## 9. Fin de la qualification locale

Consigner au minimum :

```text
J8_HUMAN_QUALIFICATION=<PASS|FAIL|NOT_RUN>
J8_HTML_ALL_HISTORY=<PASS|FAIL|NOT_RUN>
J8_HTML_EXPLICIT_WINDOW=<PASS|FAIL|NOT_RUN>
J8_ERROR_CONTRACT=<PASS|FAIL|NOT_RUN>
J8_EVIDENCE_LEVELS=<PASS|FAIL|NOT_RUN>
J8_METRIC_FORMULAS=<PASS|FAIL|NOT_RUN>
J8_MARKDOWN_REPRODUCIBILITY=<PASS|FAIL|NOT_RUN>
J8_PROVIDER_CALLS_DURING_QUALIFICATION=0
J8_PROVIDER_CAMPAIGN=NOT_AUTHORIZED_NOT_RUN
J8_FINAL_REPORT=NOT_CREATED
```

Une tentative affichée `INCOMPLETE_ATTEMPT` est une preuve conservée, non un zéro ni une permission
de reprise : vérifier qu'elle contribue une fois au coût et à l'erreur, que la campagne reste
`PARTIAL` et qu'aucun retry n'est possible.

Arrêter l'application, vérifier le port 8087 libéré et confirmer les contrôles réseau `LOCKED`.
Un échec ou une valeur `NOT_RUN` garde le Work Order actif. Aucun fichier de preuve ne doit contenir
de donnée brute ou sensible.

## 10. Campagne fournisseur prospective — arrêt obligatoire sans go

Cette section décrit le protocole retenu pour une opération future ; elle ne l'autorise pas. Avant
la première requête fournisseur, le propriétaire doit donner un go séparé qui fige la fenêtre UTC
exclusive, le dossier, le manifeste, les plafonds et les arrêts.

### 10.1 Sélection et portes pré-réseau

1. En lecture locale, sélectionner le dernier événement fournisseur J7 `HUMAN_VALIDATED`, terminal
   et possédant déjà état, détail, statistiques, incidents et compositions directs. Départager les
   égalités par identifiant fournisseur croissant. S'il n'existe aucun candidat, arrêter avant
   réseau.
2. Reprendre sa date, son tournoi et sa saison. Vérifier en lecture seule que les caches J3 et
   tournoi correspondants ont expiré naturellement. Ne supprimer, invalider ni contourner aucun
   cache.
3. Réserver `[from,to)` en UTC exclusivement à ce parcours : aucune autre campagne manuelle ne doit
   commencer dans cette fenêtre.
4. Vérifier les confirmations et gates existants, la concurrence maximale de un, le délai minimal
   partagé de trois secondes et l'absence de retry.

### 10.2 Ordre et plafond absolu

Après ce go seulement, exécuter exactement dans cet ordre :

1. J3 `SCHEDULED_EVENTS`, pages 1 à N réellement atteintes, avec plafond 25 ;
2. une découverte `TOURNAMENT_SCHEDULED_EVENTS` ;
3. un rafraîchissement J4 phase 2 `EVENT_DETAILS` ;
4. une campagne J5 ordonnée `STATISTICS`, `INCIDENTS`, `LINEUPS`.

Le plafond absolu vaut 30 tentatives directes : `25 + 1 + 1 + 3`. Au premier incident terminal,
refus, incompatibilité, contenu inattendu ou arrêt opérateur, ne pas poursuivre manuellement les
étapes suivantes. Chaque tentative admise à la frontière est écrite exactement une fois, même si
aucun résultat terminal n'est ensuite observable.

Utiliser un contexte Playwright non persistant neuf. Ne conserver ni profil, cookie,
`storageState`, HAR, trace, vidéo, capture ou téléchargement. Aucun retry, polling, scheduler,
fallback, nouvelle URI ou extension d'allowlist n'est autorisé. Si un cache hit imprévu, une panne
ou un dossier inexploitable empêche une métrique obligatoire, publier `PARTIAL` ou `NOT_MEASURED` ;
ne pas lancer de seconde campagne sans une nouvelle autorisation explicite.

### 10.3 Revue humaine ciblée

Examiner trois dossiers distincts lorsque le corpus le permet :

1. le dossier de la campagne J8 ;
2. le dossier direct non ciblé le plus récent présentant `PARTIAL` ou `UNAVAILABLE` ;
3. le dossier direct non ciblé le plus récent présentant une `LATE_CORRECTION`.

Si un archétype n'existe pas, utiliser le dossier direct le plus proche et consigner
`ARCHETYPE_NOT_AVAILABLE`, sans provoquer d'appel supplémentaire. Déclarer par libellé, avant la
revue, chaque source actuellement admise du Betting Project. Comparer identité, horaire, état,
score, statistiques, incidents, compositions, correction tardive, unicité/détail/fraîcheur,
divergences et coût opérateur. Ne versionner que le libellé de source, l'heure du contrôle, les
verdicts `PASS`/`PARTIAL`/`FAIL`/`NOT_MEASURED` et une synthèse bornée : aucune donnée brute et aucune
URL sensible.

Conclure séparément sur accessibilité, complétude, exactitude, fraîcheur, stabilité, efficacité,
maintenabilité, risque et valeur analytique. Une dimension non observable reste `NOT_MEASURED`; J8
ne prend aucune décision d'adoption J9.

### 10.4 Gel du rapport après qualification

Après la campagne et la revue seulement, relancer l'export avec la fenêtre exclusive et l'`AsOf`
figés. Copier manuellement la sortie minimisée vers
`docs/benchmark/J8-BENCHMARK-REPORT-<date-campagne>.md`. Conserver sans modification le bloc
automatique délimité par le renderer, son hash de population et ses formules ; ajouter la revue
humaine dans une section séparée après ce bloc. Recalculer le SHA-256 du fichier exporté et vérifier
le hash de population avant toute proposition de commit. Le rapport versionné exclut payload, URI,
`request_key`, header, cookie, jeton, `.env`, log brut et artefact navigateur.

À la fin, arrêter Playwright et l'application, reverrouiller tous les contrôles et vérifier les
processus/ports. Les résultats réels sont consignés dans une preuve de validation distincte ; le
rapport final n'est créé qu'après revue humaine. Dans l'état actuel :

```text
J8_PROVIDER_CAMPAIGN_GO=CONSUMED_2026_08_30
J8_PROVIDER_CAMPAIGN=SECOND_BOUNDED_EXECUTION_COMPLETED
J8_PROVIDER_CAMPAIGN_RESULT=MEASURED_COMPLETED_20_OF_20
J8_FINAL_BENCHMARK_REPORT=NOT_CREATED_PENDING_TARGETED_HUMAN_REVIEW
```

### 10.5 Exécution du 2026-08-30

La preuve complète est `docs/validation/J8-BOUNDED-CAMPAIGN-20260830.md`. La fenêtre exclusive est
`[2026-08-30T03:39:12.086771Z,2026-08-30T04:32:04.339732Z)`. Le parcours a déclaré vingt unités et
consommé dix-neuf tentatives sur le plafond de trente : quinze pages J3 parsées, une découverte
tournoi parsée, un détail J4 phase 2 parsé, puis deux appels J5. Les statistiques J5 sont
`PARSED/COMPLETE`; les incidents sont `SCHEMA_INCOMPATIBLE`; les compositions sont
`NOT_REACHED_AFTER_TERMINAL_FAILURE` et n'ont provoqué aucun appel.

L'arrêt au premier incident a été respecté. À ce terminal précis, aucun retry, import, fallback ou
seconde campagne n'était autorisé. Le double export local est byte-identique et rend `PARTIAL`; la
configuration est reverrouillée, l'application et le worker sont arrêtés et le port 8087 est libre.

L'analyse hors ligne compare l'ancienne et la nouvelle preuve du même événement. Elle isole une
nouvelle représentation observée des tirs au but non minutés : propriété d'actions absente dans
l'ancienne réponse, tableau vide dans la nouvelle. Le contrat strict V14 ne traite pas encore ces
formes comme équivalentes dans le seul contexte terminal cohérent. Le transport et le parseur sont
inchangés par J8 et l'instrumentation ne transforme pas le payload. Toute version corrective du
parseur, migration append-only, reparse ou nouvelle campagne relève d'un Work Order et d'un go
propriétaire séparés.

### 10.6 Correctif incidents V15 postérieur à la campagne

WO-017 ajoute `event-incidents-v15` et Flyway V28 sans modifier la preuve historique. Une sonde
locale en transaction PostgreSQL read-only a relu les octets exacts du snapshot 717 : SHA-256
identique, résultat V15 `PARSED`, complétude `PARTIAL · 91%`, 35 incidents et zéro problème. Cette
sonde n'a persisté ni observation, ni occurrence, ni résultat J8 et n'a effectué aucun transport.

À cet instant, le résultat de campagne restait donc
`event-incidents-v14 / SCHEMA_INCOMPATIBLE`, son coût et son hash de population restaient
inchangés, et J8 restait `PARTIAL`. La readiness V15 n'autorisait ni reparse persistant, ni retry,
ni seconde campagne fournisseur.

Une qualification fournisseur V15 a ensuite été autorisée sous WO-017 et exécutée une seule fois
par Codex sur instruction explicite du propriétaire. Elle a terminé `COMPLETED_LOCKED` après trois
appels, parsé la réponse incidents dédupliquée vers le snapshot `717` sous V15 en
`PARTIAL · 91%`, observation `324`, puis atteint les compositions `720`/`325`. Son ledger J5 est
une nouvelle preuve prospective normale, extérieure à la fenêtre exclusive J8.

Le résultat historique reste `event-incidents-v14 / SCHEMA_INCOMPATIBLE`; son coût, sa fenêtre et
son hash de population restent inchangés, et cette première fenêtre J8 reste `PARTIAL`. Une lecture
« tout historique » peut inclure la campagne corrective ; pour reproduire le rapport initial,
utiliser obligatoirement les `from/to/asOf` gelés. Le go WO-017 est consommé et n'autorisait aucun
autre appel sous WO-017.

### 10.7 Seconde campagne J8 mesurée du 2026-08-30

Après validation de WO-017, le propriétaire a donné un second go distinct pour un seul parcours
J8 complet. La preuve est conservée dans
`docs/validation/J8-SECOND-BOUNDED-CAMPAIGN-20260830.md`. La fenêtre exclusive est
`[2026-08-30T09:24:51.0887925Z,2026-08-30T09:44:03.2695965Z)` et l'`asOf` est égal à sa borne
supérieure.

Le parcours a terminé quatre campagnes : quinze pages J3, une découverte tournoi, un détail J4
phase 2 et trois familles J5. Les vingt unités portent chacune une tentative unique, une réponse
HTTP 200 et une issue `PARSED`. J5 a produit statistiques `COMPLETE · 100%`, incidents
`event-incidents-v15 / PARTIAL · 91,62%` et compositions `PARTIAL · 99,03%`. Aucun retry, import,
fallback, arrêt opérateur ou tentative incomplète n'est présent.

Deux exports non-Web lancés avec les mêmes `from/to/asOf` sont byte-identiques : 15 202 octets,
SHA-256 `ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25` et hash de population
`c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726`. Le rapport automatique est
`MEASURED / FULL_ATTEMPT_LEDGER`, avec vingt réponses et vingt parsings compatibles sur vingt,
zéro refus, 404, erreur opérationnelle ou tentative incomplète. Un dossier sur un est exploitable
mais aucun n'est strictement complet; les coûts exacts sont seize appels de découverte, quatre
appels marginaux et vingt appels effectifs par dossier exploitable.

L'instance qualifiée a été arrêtée. Un redémarrage inerte a confirmé le verrou de démarrage J3,
la découverte tournoi indisponible, les préparations J4/J5 désactivées, Playwright, polling et
refresh à `false`; il a ensuite été arrêté et le port 8087 est libre. `.env` est inchangé. Ce
résultat n'autorise aucune troisième campagne.

### 10.8 Préparation de la revue humaine ciblée

La campagne mesurée rend les métriques automatiques obligatoires disponibles, mais elle ne remplace
pas la revue humaine. Les trois candidats locaux sont préparés dans
`docs/validation/J8-TARGETED-HUMAN-REVIEW-READINESS-20260830.md`. Avant toute comparaison, le
propriétaire doit fournir les libellés des sources actuellement admises du Betting Project ou
accepter explicitement `CONTROL_SOURCE_LABEL=CONTROL_SOURCE_ABSENT` et
`EXTERNAL_COMPARISON_SOURCE=EXTERNAL_COMPARISON_ABSENT`. Sans source déclarée, exactitude et
valeur analytique restent `NOT_MEASURED`; la fraîcheur reste `PARTIAL` et la maintenabilité reste
`NOT_MEASURED`.

Ne copier l'export dans `docs/benchmark/` qu'après cette revue et son acceptation. Ne modifier ni le
bloc automatique, ni son hash; ajouter la section humaine après le marqueur de fin prévu. WO-016
reste actif et `info.app.phase` reste `J8-BENCHMARK-READY-FOR-HUMAN-QUALIFICATION` jusque-là.

## 11. Clôture

Le Work Order ne peut passer à `VALIDATED` qu'après résultats techniques réels, campagne réelle
bornée exécutée après go séparé, qualification humaine terminée, rapport final gelé et accepté,
revue des secrets, arrêt propre et décision propriétaire. Une recette hors ligne réussie ne
remplace jamais la campagne. Tout push, toute Pull Request et toute fusion vers `main` exigent une
demande explicite séparée et ne constituent pas une preuve de clôture J8.

```text
J8_CLOSURE_CRITERIA=NOT_MET
J8_WORK_ORDER_STATUS=READY_FOR_HUMAN_QUALIFICATION
J8_OWNER_CLOSURE_DECISION=NOT_GRANTED
```

La clôture exige que les six lignes suivantes soient simultanément prouvées et reproduites
exactement. Elles ne constituent pas l'état courant de cette readiness :

```text
COMPLETENESS_METRICS=AVAILABLE
LATENCY_METRICS=AVAILABLE
SCHEMA_STABILITY=MEASURED
ERROR_RATE=MEASURED
PROVIDER_CALL_COST=MEASURED
AUTOMATIC_POLLING=NO
```
