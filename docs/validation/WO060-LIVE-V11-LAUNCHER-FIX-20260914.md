# WO-060 — Correctif de mise en service du profil live V11

Date : **14 septembre 2026**. Statut : **QUALIFIED_LOCAL**.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Constat et cause

Ce correctif prolonge le [WO-060](../work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md)
sur la base locale `2073eabd653293c1cb3ab4fea8ac7c93e52eb98e`. Il conserve la
[qualification initiale](WO060-J3-IMPLEMENTATION-20260914.md) et ses preuves historiques.

Les captures fournies par l'opérateur montrent, pour le 14 septembre :

- une collecte quotidienne J3 réussie, terminée à 07:32:30 à Paris, avec 19 pages et
  299 tournois actionnables ;
- dix événements normalisés consultables ;
- une capacité live calculée à zéro et un message demandant encore un profil V10.

Ces captures attestent ce retour d'utilisation. Elles ne prouvent pas, à elles seules, la
reprise après redémarrage ou la pause d'une campagne fournisseur.

Le code prépare exclusivement `live-v11`. `LiveCampaignService.selectionMaximum()` retourne
zéro lorsque son profil dédié est absent ou invalide. Le fichier du lanceur Eclipse LIVE
contenait déjà `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY=8`, les neuf paramètres V10,
et **aucun paramètre V11**. Le message de `/events` et son assertion de test étaient restés à V10.

## Correction réalisée

- `events.html` indique le lecteur V11 et les dix valeurs nécessaires : capacité,
  `SOFASCORE_LIVE_GROUPED_V11_QUALIFICATION_SHA256` et huit durées.
- Le test MVC existant utilise `LiveCampaignProperties.getPreparationPolicyVersion()` pour
  vérifier que l'indication de profil, le script et le nom de propriété correspondent à la
  politique effectivement préparée. Il conserve les cas capacité nulle et événement bloqué.
- Aucune modification du moteur live, des plafonds, des contrats, des migrations ou du worker.

La configuration locale corrigée est **SofaScore - PLAYWRIGHT J3-J5 (MANUEL - OPT-IN) (LIVE)**,
dans le workspace Eclipse `human-sofascore`. L'historique Eclipse la désigne comme le dernier
lanceur externe utilisé ; l'opérateur a confirmé utiliser une configuration de lancement Eclipse.
Eclipse était fermé lors de l'écriture.

Les neuf entrées V11 manquantes sont insérées ; la capacité reste à huit. Toutes les anciennes
valeurs et tous les autres octets sont conservés. Le remplacement est atomique et la sauvegarde
porte le suffixe `.launch.wo060-v11-20260914.bak` dans le même dossier local. La sauvegarde du
lanceur reste hors Git. L'activation live préexistante reste inchangée ; aucune campagne n'est
lancée par cette opération.

Les dix valeurs proviennent du [profil V11 versionné](WO060-LIVE-V11-PROFILE-20260914.json),
dont le SHA-256 est `80490d1bb1b781006277234a3337ca9fab3e72bda32bc8ec1da699055e2f326d`.
Le profil du worktree opérateur est identique à celui du worktree WO-060.

## Vérification effective

| Contrôle | Résultat |
|---|---|
| `mvnw.cmd clean verify` | PASS — 2 364 Surefire, dont 5 exclusions ; 276 Failsafe, sans exclusion ; aucun échec ou erreur ; 12 min 48 s |
| Test MVC `EventExplorerControllerTest` | PASS — 26 tests, aucun échec, erreur ou exclusion |
| Lecture du lanceur après remplacement | PASS — dix valeurs conformes, neuf ajouts, autres valeurs et sauvegarde vérifiées |
| Lecture de ces dix valeurs par Spring avec `application.yml` | PASS — politique `live-v11`, capacité calculée **8**, sans serveur, base ou fournisseur |
| UTF-8, diff Git, empreinte du profil, revue ciblée de secrets | PASS — six fichiers du correctif, liens locaux et profil contrôlés |

La vérification complète s'est terminée le **14 septembre 2026 à 05:57:37 UTC**.
Le journal porte le SHA-256 `09eeb5c871de473ae6c8bf9fd759ccaa34aca8aa7e9c542340d6b3874961948e`.
Les cinq exclusions Surefire sont celles documentées dans la qualification initiale : quatre
cas de liens symboliques selon les capacités Windows et le mode natif J6 à activation distincte.
Les sept empreintes de classes liées au profil V11 restent conformes après ce build.

Le contrôle ponctuel de configuration utilise le programme local
`.tmp/CheckWo060LiveLauncher.java`, les classes/dépendances du classpath Surefire, les dix seules
valeurs de qualification du lanceur et le YAML de l'application. Il appelle le binder Spring,
valide `LiveCampaignProperties` puis le même calcul `LiveAdmissionPolicyV11.qualifiedCapacity`
que la sélection. Il ne démarre aucun contexte applicatif ou navigateur et ne lit aucun payload.
Les autres variables du lanceur ne sont ni importées dans ce processus, ni affichées.

Commandes et traces locales du worktree :

```text
mvnw.cmd clean verify
  .tmp/wo060-live-v11-guidance-clean-verify.log
Show-LiveGroupedV11LauncherConfiguration.ps1 -OutputFormat Eclipse
java --class-path <classpath Surefire> .tmp/CheckWo060LiveLauncher.java <lanceur> src/main/resources/application.yml <profil V11> .tmp/wo060-v11-spring-binding-result.json
  .tmp/wo060-v11-eclipse-result.json
  .tmp/wo060-v11-spring-binding-result.json
```

Le résultat local du remplacement conserve les empreintes avant/après et les neuf noms de
paramètres ajoutés, sans secret. Les tests PostgreSQL de la commande de vérification utilisent
des bases Testcontainers jetables. La persistance et le worker étant inchangés, les qualifications
explicites d'intégration et Chromium précédentes restent celles de la réalisation initiale.

Empreintes du correctif et de l'opération locale :

| Fichier ou état | SHA-256 |
|---|---|
| `src/main/resources/templates/events.html` | `d23a8a20f6ff4416865b67ac93f65cd667fa5e5f20071e988c979a760dbb4816` |
| `src/test/java/com/bettingproject/sofascorelocal/adapter/web/EventExplorerControllerTest.java` | `267dca5b4dac1e22c1abc0014ec12fe0d07af25cb50488e6ead225bad4c7d483` |
| Lanceur Eclipse avant modification, identique à la sauvegarde | `1ec0d972b49761d6aa7228f469c8fd5fbbc0f4a34717b238a49cfc2475e252b0` |
| Lanceur Eclipse après ajout V11 | `cb1628c8be71b900762a85681e35e7b46aeb1fda0c3dce0d62170d14e23ee9c6` |

Les six fichiers versionnés du correctif sont les deux sources ci-dessus, `CHANGELOG.md`,
le guide `docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md`, le Work Order actif et cet addendum.
Le lanceur Eclipse et sa sauvegarde restent des fichiers locaux hors dépôt.

## Prise en compte et limites

Les paramètres Eclipse sont persistés ; ils seront pris en compte au prochain lancement du Lab
avec cette configuration. Le résultat **8** est vérifié dans le calcul d'admission isolé, pas
dans une nouvelle campagne réelle. L'application opérateur n'est pas redémarrée par l'agent.
La base opérateur reste intacte et aucun nouvel appel fournisseur n'est exécuté par l'agent.

Le correctif du message est livré sur la branche WO-060. Le worktree opérateur reste au commit
de réalisation initial `2073eab` ; la configuration V11 corrige déjà sa cause de capacité nulle.
La revue et la fusion vers le train restent nécessaires pour clôturer le WO.
