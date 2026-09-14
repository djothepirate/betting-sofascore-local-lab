# WO-060 — Bornes des dates J3 et motifs d'annulation

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Périmètre et décision

Suite du [correctif de validation](WO060-J3-DATE-VALIDATION-FIX-20260914.md), sur la base
`c27913a70af00df4e101588ee64ad05213846afe`, dans le worktree
`.tmp/wo060-j3-automation-design`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260913-060`.

Les captures opérateur du 14 septembre montrent des horaires acceptés en année 9999 et une
date cible `9999-01-31`. L'année 9999 est syntaxiquement valide ; aucun horizon métier ne
l'interdisait. Après annulation, le motif technique `OPERATOR_CANCELLED` tombait sur une
explication générique d'indisponibilité du transport. Une révision remplacée utilisait le même
repli faute de traduction de `PLAN_REVISED`.

Le propriétaire a choisi explicitement : **« Depuis 2000, avec un horizon futur de 12 mois »**.
Le correctif applique ce choix aux nouvelles collectes manuelles A/B et à la création ou
révision des horaires J3. Il ne change ni endpoint, ni transport, ni activation de
l'automatisation, ni migration. Le WO reste actif ; aucune fusion ou publication n'est effectuée.

## 2. Comportement livré

- Date à collecter comprise entre le **01/01/2000** et **aujourd'hui à Paris + 12 mois**,
  bornes incluses. Les collectes historiques telles que le 02/09/2026 restent autorisées.
- Déclenchement ponctuel limité au même dernier jour, jusqu'à **23:59 inclus**. Le contrôle
  existant exige toujours un instant strictement futur, après résolution du décalage de Paris.
- Exemple au **14/09/2026** : dernière date et dernier jour de programmation **14/09/2027**.
  Douze mois calendaires ne sont pas assimilés à 365 jours ; les années bissextiles sont testées.
- Bornes `min`/`max` sur les champs des formulaires de collecte, création et modification ;
  plage lisible et liée aux champs par `aria-describedby`.
- Contrôle serveur dans `J3RuntimeService`, avant validation métier du lot, accès fournisseur ou
  écriture d'ordre. Un POST contournant les contraintes HTML reste refusé ; l'ancienne
  révision et les préférences restent intactes. La saisie de plan refusée reste affichée.
- La borne basse HTML de déclenchement est le début du jour de Paris. Le refus du passé se
  fait côté serveur sur l'instant résolu : imposer l'heure civile courante interdirait à tort
  une seconde occurrence future lors du passage à l'heure d'hiver.
- Motifs affichés : **« Annulée à votre demande. »** et **« Remplacée par une nouvelle version
  de cet horaire. »**. Les lignes annulées existantes bénéficient de cette traduction à la lecture.
- Les lectures de dates et l'historique conservé ne sont pas bornés par cette règle de création.
  Aucune suppression, correction de ligne historique ou mutation de la base opérateur.

Les captures montrent aussi une saisie **31/02/2028**, puis une ligne **29/02/2028**. Cette
succession ne suffit pas à établir où la valeur a changé. La reproduction Chromium avec
saisie au clavier conserve le 31 février comme invalide et bloque le POST ; le parseur ISO
strict déjà livré refuse aussi `2028-02-31T10:00` côté serveur. La conversion en 29 février
n'a pas été reproduite. Le 29 février 2028 est valide au calendrier ; il n'est admissible que
si l'horizon courant le permet. Aucun remplacement du contrôle natif ni ajout de JavaScript
au tableau de bord n'est nécessaire pour les défauts confirmés.

## 3. Qualifications

Environnement : Windows, Java 25.0.4, Maven Wrapper 3.9.16, Spring Boot 4.1.0,
version applicative `0.1.0-rc.1-SNAPSHOT`, PostgreSQL 18.4 Testcontainers jetable.

### Vérification ciblée

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.4'
.\mvnw.cmd '-Dtest=J3DatePolicyTest,J3AutomationDataTest,J3AutomationControllerTest,DashboardControllerTest' '-Dit.test=J3WebApplicationIT' verify
```

Résultat : **BUILD SUCCESS**, 57,527 s, terminé le 14/09/2026 à 12:34:32 UTC.
Surefire : **73 tests, zéro échec, zéro erreur, zéro ignoré**.
Failsafe : **8 tests, zéro échec, zéro erreur, zéro ignoré**.

Les cas PostgreSQL vérifient les refus en création et modification (année 9999, avant 2000,
lendemain de la borne), l'absence de toute mutation d'ordre ou de préférence, la conservation
de la saisie, les limites inclusives, les motifs d'annulation et la consultation hors plage.
Les refus des collectes manuelles A/B ne sollicitent pas la fabrique de transport.
Les tests de domaine couvrent minuit à Paris, les 12 mois calendaires, les années bissextiles
et la seconde occurrence d'une heure doublée.

### Vérification complète

`mvnw.cmd clean verify` : **BUILD SUCCESS**, durée **13 min 08 s**, terminé le
**14/09/2026 à 12:47:58 UTC**. Rapports XML recomptés après terminaison :

| Ensemble | Suites | Tests déclarés | Échecs | Erreurs | Ignorés |
|---|---:|---:|---:|---:|---:|
| Surefire | 237 | 2 421 | 0 | 0 | 5 |
| Failsafe | 17 | 283 | 0 | 0 | 0 |

Les cinq exclusions sont les mêmes que dans le correctif précédent :

- `LocalJ7ExportFileStoreTest#refusesASymbolicLinkInsteadOfFollowingIt` ;
- `LocalJ7ExportFileStoreTest#refusesAMatchingCandidateSymlinkDuringOrphanEnumeration` ;
- `J8BenchmarkExportCommandTest#refusesAnExportsSymbolicLinkOrReparsePointWhenSupported` ;
- `J8BenchmarkExportCommandTest#refusesAJ8SymbolicLinkOrReparsePointWhenSupported` ;
- `J6NativeBinaryPipelineQualificationTest#dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults`
  (propriété opt-in `j6.docker.qualification` absente).

Les quatre premières concernent les capacités de lien symbolique de l'environnement Windows.
La suite complète inclut les tests PostgreSQL Failsafe. Aucune migration ni implémentation de
persistance n'est modifiée dans cet addendum ; aucune seconde passe intégrale n'est nécessaire.

### Chromium explicite, hors réseau

```powershell
.\scripts\Invoke-J3DateFormQualification.ps1 -BrowserCachePath 'C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/j9-playwright-graceful-close/.tmp/provider-playwright-browsers'
```

Le programme Java est exécuté séparément des tests standards et utilise uniquement un cache
Chromium déjà installé. Le fragment réel `#manual-call-control` de `dashboard.html` est rendu
avec Thymeleaf et un modèle synthétique daté du **01/03/2027**. Le filtre de sécurité effectif
fournit les en-têtes ; aucune politique CSP n'est assouplie.

Le contexte neuf est hors réseau (`setOffline(true)`), bloque les service workers et remplit
chaque requête en mémoire. Un POST de test reçoit un HTTP 200 synthétique, sans redirection,
sans contrôleur applicatif ni base. Toute autre destination ou route inattendue fait échouer
la qualification. Aucune session, aucun cookie, aucune capture fournisseur n'est conservé.

Le lanceur PowerShell complet a été exécuté avec succès après `clean verify` (code de sortie 0).
Résultat du programme : **22 cas PASS**, créations et modifications aux bornes, dates hors
plage, 29 février valide, 31 février saisi au clavier bloqué, limites partagées par A et B.

```text
WO060_J3_DATE_FORMS=PASS;CASES=22;PROVIDER_CALLS=0;LAB_REQUESTS=0
```

### Contrôles et empreintes

`git diff --check`, décodage UTF-8 strict des 14 fichiers et analyse syntaxique PowerShell :
**PASS**. Les neuf expressions du scanner de secrets du dépôt sont appliquées aux fichiers
modifiés et nouveaux : **zéro correspondance**. Aucun fichier `.env`, payload, cookie ou état
de session n'entre dans le diff. Le seul jeton de la qualification navigateur est une chaîne
synthétique sans autorité applicative. `server.address=127.0.0.1`, le défaut fournisseur
désactivé et la CSP du tableau de bord restent effectifs.

Une passe supplémentaire de `ci/check-no-secrets.sh c27913a70af00df4e101588ee64ad05213846afe`
a été tentée après commit. Le premier lancement Git Bash ne trouvait pas `mktemp` ; avec
`PATH=/usr/bin:/bin:$PATH`, le script parcourt les 1 752 fichiers de HEAD avant le diff.
Cette passe globale a été **interrompue volontairement**, son avancement n'étant que d'environ
20 % après huit minutes sous Windows. Elle ne constitue donc pas un résultat global PASS.
La preuve de secrets retenue pour cet addendum porte sur les **14 fichiers du correctif**,
analysés intégralement avec les neuf mêmes règles, sans correspondance.

| Preuve locale ignorée par Git | SHA-256 |
|---|---|
| `.tmp/wo060-j3-date-range-targeted.log` | `1b460084bd6530eb5be1c0bbf48eb70aa7aef8f4470e18057f58b7bb3bc8daad` |
| `.tmp/wo060-j3-date-range-clean-verify.log` | `fd6645a8f5ee3bf4c89cb189ceab7086c0949c178ab313446160edf487b534ae` |
| `.tmp/wo060-j3-date-browser.log` | `f92b2ba8ebd04691e8c8af2d5f25007303dc46c1ffe4c2502ce3637f28ce62e2` |
| `.tmp/WO060-J3-DATE-RANGE-CORRECTIF.patch` | `dd1f23decfe0dfa7ddfebc53ecbe01fc1371f1ed2013c002d152b440ac6318bf` |

## 4. Fichiers concernés

- `src/main/java/com/bettingproject/sofascorelocal/domain/scheduledevents/J3DatePolicy.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/DashboardController.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/J3Presentation.java`
- `src/main/resources/templates/dashboard.html`
- `src/test/java/com/bettingproject/sofascorelocal/domain/scheduledevents/J3DatePolicyTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/J3AutomationControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/J3WebApplicationIT.java`
- `scripts/qualification/J3DateFormCheck.java`
- `scripts/Invoke-J3DateFormQualification.ps1`
- `docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md`
- `docs/work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md`
- `CHANGELOG.md`
- le présent rapport.

## 5. Application au worktree Eclipse

Le worktree HUMAN était propre au même commit `c27913a` au début de ce correctif. Le patch
complémentaire est `.tmp/WO060-J3-DATE-RANGE-CORRECTIF.patch`, limité au code, au modèle
HTML, aux tests et à la qualification navigateur. Les documents de preuve restent dans le
worktree WO-060. Le patch contient **10 fichiers**, mesure **40 588 octets** et passe
`git apply --check` dans HUMAN ainsi que `git apply --reverse --check` dans le worktree WO.

```powershell
$correctif = 'C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo060-j3-automation-design/.tmp/WO060-J3-DATE-RANGE-CORRECTIF.patch'
git -C C:/Dev/BettingProject/human/betting-sofascore-local-lab apply --check $correctif
if ($LASTEXITCODE -ne 0) { throw 'Le patch ne correspond pas à cet état du worktree.' }
git -C C:/Dev/BettingProject/human/betting-sofascore-local-lab apply $correctif
if ($LASTEXITCODE -ne 0) { throw 'Application du patch interrompue.' }
```

Vérifier le patch puis l'appliquer dans le worktree HUMAN, faire **Refresh** dans Eclipse,
recompiler, redémarrer le Lab, puis recharger le tableau de bord. Les trois essais annulés
des captures restent dans l'historique et affichent le motif précis. Aucun nettoyage SQL ni
réglage de l'automatisation n'est requis. L'agent n'applique pas de changement concurrent
dans le worktree Eclipse et ne redémarre pas le Lab opérateur pendant ses qualifications.
