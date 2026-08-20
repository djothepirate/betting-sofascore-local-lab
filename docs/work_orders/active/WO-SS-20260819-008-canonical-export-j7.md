# WO-SS-20260819-008 — Export canonique local J7

- **Statut :** `READY_FOR_PR_REVIEW`
- **Date :** 2026-08-19
- **Date de démarrage :** 2026-08-19
- **Prérequis :** J6 fusionné et validé (`d24e812a35467297eebf254b36ec5913b29c71cf`)
- **Base locale :** `d24e812a35467297eebf254b36ec5913b29c71cf`
- **Jalon :** J7 — Export canonique
- **Branche :** `codex/j7-canonical-export`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Polling, planification ou retry réseau :** `NOT_AUTHORIZED`
- **Import Betting Project / transport sortant :** `NOT_AUTHORIZED`
- **Déploiement VPS :** `NOT_AUTHORIZED`
- **Purge de la base locale primaire :** `NOT_AUTHORIZED`

## 1. Objectif

Produire, pour un événement canonique à la fois, un export JSON v1 autonome de ses dernières
observations locales J4/J5, avec provenance J6, hashes, avertissements de complétude et décision
humaine explicite. Le parcours local comporte deux gestes : création d'un candidat automatiquement
contrôlé, puis validation ou rejet motivé après inspection. Seul un export `HUMAN_VALIDATED` est
téléchargeable.

Les statuts du laboratoire restent inchangés :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Revue de l'ADR-SS-001

J7 ne crée aucun connecteur, endpoint fournisseur, appel réseau, ordonnanceur, polling, import
externe ni transport sortant. Il assemble exclusivement des observations déjà persistées et ne lit
jamais `payload_raw`.

```text
ADR_SS_001_J7_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
```

### 2.1 Amendement opérateur — instance locale multi-campagnes

Le 2026-08-19, pendant la recette J7, le propriétaire a demandé que les campagnes manuelles J3,
J4 phase 2 et J5 puissent être armées et exécutées séquentiellement dans la même instance que les
parcours locaux J6/J7. Le démarrage combiné échouait auparavant par conception parce que J3 était
exclusif et exigeait que `SCHEDULED_EVENTS` soit le seul endpoint autorisé.

Cet amendement autorise uniquement l'union exacte des endpoints déjà approuvés pour les opt-ins
sélectionnés. Il n'ajoute aucun endpoint ni aucune URI, laisse tous les opt-ins à `false` par
défaut, conserve les confirmations humaines et les limites propres à chaque campagne, interdit J4
phase 1 dans une session combinée, et impose un coordinateur unique à toutes les requêtes J3/J4/J5
pour garantir une concurrence maximale de un et le délai minimal partagé. J6 et J7 restent sans
transport fournisseur. Aucun appel réel n'est autorisé pendant l'implémentation ou les tests.

```text
J7_COMBINED_SESSION_AMENDMENT=AUTHORIZED_BY_OWNER
J7_COMBINED_SESSION_ENDPOINTS=SCHEDULED_EVENTS,EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
J7_COMBINED_SESSION_J4_MODE=PHASE_2_ONLY
J7_COMBINED_SESSION_MAXIMUM_CONCURRENCY=1
J7_COMBINED_SESSION_AUTOMATIC_ACTIVITY=DISABLED
J7_COMBINED_SESSION_NEW_PROVIDER_ENDPOINT=NO
ADR_SS_001_COMBINED_SESSION_REVIEW=COMPATIBLE_EXISTING_MANUAL_PATHS_NO_CHANGE_REQUIRED
```

## 3. Périmètre inclus

- JSON Schema Draft 2020-12 v1, chargé depuis le classpath sans résolution réseau ;
- enveloppe déterministe `{manifest,data}` et empreintes `dataSha256`, `sourceSetSha256` et fichier ;
- état J4 courant, dernier détail et familles J5 courantes : statistiques, incidents, compositions ;
- origines `SYNTHETIC_FIXTURE` et `PROVIDER_SNAPSHOT`, toujours explicites ;
- avertissements stables pour absence, indisponibilité, partiel, synthétique, mélange et purge ;
- migration append-only V23 étendant `export_manifest` uniquement pour les lignes J7 ;
- abstractions testables de manifeste et de stockage de fichiers ;
- cycle `COHERENCE_CHECKED` → `HUMAN_VALIDATED` ou `REJECTED`, terminal et immuable ;
- fichiers locaux bornés à 5 Mio, publication create-new par lien physique atomique sur le même
  système de fichiers, chemins générés et vérification avant lecture ;
- intention de décision write-ahead persistée avant le fichier terminal pour en authentifier les
  retries par statut, heure, motif, chemin, hash et taille ;
- pages HTML locales, jetons à usage unique, confirmations exactes et téléchargement validé seul ;
- session locale combinée optionnelle J3 + J4 phase 2 + J5 avec union exacte des endpoints et
  sérialisation partagée de tous les transports manuels ;
- documentation, tests unitaires/MVC/fichiers/PostgreSQL et recette humaine locale.

## 4. Hors périmètre

- historique complet J6 dans le fichier, export par lot ou par date ;
- nouvel endpoint fournisseur, appel automatique ou activation par défaut de J3/J4/J5 ;
- combinaison de J3 avec J4 phase 1 ;
- endpoint JSON de consultation générale ;
- envoi vers le Betting Project, VPS, cloud ou service tiers ;
- validation automatique présentée comme une approbation humaine ;
- tâche planifiée, polling ou retry réseau ;
- purge primaire J6.

## 5. Contrat et invariants

- identifiant de schéma : `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1` ;
- mode unique : `LATEST_AVAILABLE` ;
- ordre des sources : `EVENT_STATE`, `EVENT_DETAILS`, `EVENT_STATISTICS`, `EVENT_INCIDENTS`,
  `EVENT_LINEUPS` ;
- `dataSha256` ne change jamais lors de la décision ;
- la validation est refusée si `sourceSetSha256` ne correspond plus à l'état courant ; le recalcul
  et la décision tiennent un verrou PostgreSQL partagé avec toutes les écritures d'observation et
  les quatre tables métier filles J5 ; une fille dont le parent n'est pas visible échoue fermée ;
- les identifiants numériques sont strictement positifs et bornés à `Long.MAX_VALUE` ;
- les octets fournisseur, URI, headers, cookies, jetons, sessions, `.env`, sauvegardes et phrases
  secrètes sont interdits par un scanner des octets/textes et des clés JSON ;
- toute page et tout téléchargement est local, non indexable et non mis en cache ;
- aucun statut terminal n'est persisté avant écriture et revérification du fichier terminal.

## 6. Validation prévue

- contrat : schéma, formats, bornes, propriétés supplémentaires, états de décision et déterminisme ;
- métier : origines synthétique/fournisseur/mixte, absence, indisponibilité, vide valide, partiel,
  rétention/purge du brut, provenance incohérente et dérive des sources ;
- fichiers : publication create-new par lien physique, non-écrasement, chemins, liens symboliques,
  altération, intention write-ahead et idempotence terminale ;
- MVC : jeton unique, confirmations exactes, erreurs 400/404/409/422/503, échappement et headers ;
- PostgreSQL : installation V1→V23, upgrade V22→V23, transitions, immutabilité, unicité et relecture ;
- configuration : union exacte J3/J4 phase 2/J5, refus de toute extension et coordination globale
  d'une seule requête fournisseur à la fois avec délai minimal partagé ;
- exploitation : deux suites Maven, audit du diff et recette humaine sans appel fournisseur.

## 7. Portes de clôture

- [x] implémentation et documentation, amendement multi-campagnes inclus, terminées ;
- [x] `mvnw.cmd clean verify` sans échec après amendement ;
- [x] `mvnw.cmd -Pintegration-tests verify` sans échec après amendement ;
- [x] relance finale de la suite standard après la dernière clarification d'interface J3/J4 ;
- [x] recette humaine : rejet puis validation synthétiques ;
- [x] recette humaine : événement fournisseur local `16691018` ;
- [x] contrôle absence de contenu brut, secret ou session sur l'export fournisseur complet ;
- [x] contrôle des états terminaux J3/J4/J5 `LOCKED`, absence d'activité automatique et comptage
  explicite des appels SofaScore humains ;
- [ ] revue finale du diff, branche poussée et PR `CLEAN/MERGEABLE`.

Le Work Order reste dans `active` et ne passe à `VALIDATED` qu'après toutes ces preuves. La recette
humaine ne sera pas remplacée par un test automatisé.

## 8. État initial vérifié

Le 2026-08-19, avant toute modification fonctionnelle J7 :

```text
J7_BASE_COMMIT=d24e812a35467297eebf254b36ec5913b29c71cf
J7_BRANCH=codex/j7-canonical-export
J7_WORKTREE_INITIAL_STATE=CLEAN
J7_PROVIDER_CALLS=0
J7_PRIMARY_DATABASE_PURGE=NO
J7_INITIAL_STATUS=READY_FOR_HUMAN_VALIDATION
```

## 9. Journal de réalisation

Le 2026-08-19, l'implémentation technique couvre désormais :

- contrat JSON v1 Draft 2020-12 fermé, validateur classpath sans résolution réseau et scanner de
  contenu sensible ;
- sélection transactionnelle courante J4/J5/J6 sans lecture du brut, assemblage déterministe des
  cinq composants, complétude, avertissements et trois niveaux d'empreinte ;
- ports de manifeste et de fichiers, stockage local borné publié par lien physique create-new,
  reprise d'un candidat orphelin par reconstruction exacte et orchestration du cycle
  candidat/validation/rejet/téléchargement ;
- migration append-only V23 avec compatibilité des lignes génériques, unicités, immutabilité,
  intention de décision write-ahead, transition terminale authentifiée, verrou de fraîcheur des
  sources et interdiction de suppression J7 ;
- routes HTML locales, jetons à usage unique, confirmations exactes, aperçu échappé, en-têtes
  anti-cache/noindex et téléchargement validé seul ;
- tests unitaires, métier, fichiers et MVC ajoutés pour la portée J7, notamment réutilisation
  strictement identique et en-têtes des erreurs produites avant le contrôleur ;
- tests PostgreSQL d'installation V1→V23, d'upgrade V22→V23 avec corpus J4/J5/J6 préexistant,
  d'export fournisseur complet `PRESENT`/`COMPLETE` et de verrouillage concurrent de l'état, du
  détail, du parent J5, de ses quatre tables filles et du snapshot fournisseur, avec refus fermé
  d'une ligne fille dont le parent n'est pas encore visible ;
- documentation d'architecture, runbook opérateur et rapport de readiness J7 ;
- script J6 de sauvegarde/restauration aligné sur la version courante Flyway V23, sans changer la
  politique ni autoriser une purge primaire ;
- résultat de reparsage J4 enrichi d'un compteur distinct de compétitions pour la forme J3
  `SCHEDULED_TOURNAMENT_LIST`, sans créer de rencontre ni d'observation J4, avec formulation
  opérateur explicite et conservation inchangée de la branche `EVENT_LIST`.

Les preuves techniques et humaines sont acquises ; seule la publication reste ouverte :

```text
J7_STANDARD_SUITE=PASS_457_TESTS_0_FAILURES_0_ERRORS_2_SKIPPED_WINDOWS_SYMLINK
J7_INTEGRATION_SUITE=PASS_43_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J7_COMBINED_SESSION_TARGETED_TESTS=PASS_55_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J7_COMBINED_SESSION_ACTUAL_CONFIG_STARTUP=PASS_127_0_0_1_8087
J7_COMBINED_SESSION_BINDING_FAILURE=RESOLVED
J7_COMBINED_SESSION_MANUAL_COORDINATOR=PASS_J3_J4_J5
J7_COMBINED_SESSION_AUTOMATIC_PROVIDER_CALLS=0
J7_COMBINED_SESSION_NEW_PROVIDER_ENDPOINTS=0
J7_COMBINED_HUMAN_SECOND_INSTANCE=PASS_J4_PHASE2_J5_THREE_FAMILIES_J3_SIX_PAGES
J7_COMBINED_HUMAN_THIRD_INSTANCE=PASS_J3_SIX_PAGES_J5_COMPLETE_J6_LOCAL_J4_PHASE2_J7_VALIDATED_DOWNLOADED
J7_COMBINED_HUMAN_SINGLE_INSTANCE_J3_J4_J5_J6_J7=PASS
J7_COMBINED_HUMAN_MANUAL_PROVIDER_ATTEMPTS=28
J7_COMBINED_HUMAN_PERSISTED_PROVIDER_RESPONSES=27
J7_COMBINED_HUMAN_TIMEOUTS=1
J7_COMBINED_HUMAN_AUTOMATIC_PROVIDER_CALLS=0
J7_J3_SCHEDULED_SHAPE=COMPETITIONS_WITHOUT_SCHEDULED_MATCHES
J7_J3_SCHEDULED_J4_OBSERVATIONS_CREATED=0_BY_CONTRACT
J7_POST_CLARIFICATION_TARGETED_TESTS=PASS_13_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J7_POST_CLARIFICATION_FULL_STANDARD_RERUN=PASS_457_TESTS_0_FAILURES_0_ERRORS_2_SKIPPED_WINDOWS_SYMLINK
J7_DIFF_AND_SECRET_REVIEW=PASS
J7_FINAL_OPERATOR_LISTENER_127_0_0_1_8087=ABSENT
J7_MIGRATIONS_V1_TO_V22_UNCHANGED=PASS_ONLY_V23_ADDED
J7_VERSIONED_RUNTIME_EXPORTS=0
J7_FIRST_HUMAN_EXPORT_16707704=PASS_HUMAN_VALIDATED_AND_DOWNLOADED
J7_FIRST_HUMAN_EXPORT_HISTORY=PASS_ONE_HUMAN_VALIDATED
J7_FIRST_HUMAN_EXPORT_DOWNLOAD_SHA256=2f60b37151e81625a96ab9426f5474f4600191d102c398a4be8cfd96c5a230a9
J7_FIRST_HUMAN_EXPORT_SENSITIVE_FINDINGS=0
J7_FIRST_HUMAN_EXPORT_ARTIFACTS_VERSIONED=NO
J7_PROVIDER_16691018_SELECTION_AND_WARNINGS=PASS
J7_PROVIDER_16691018_REJECTION=PASS_REJECTED_RETAINED_NOT_DOWNLOADABLE
J7_PROVIDER_16691018_EXPORT_ID=b6b58dce-a739-4495-b3e5-1e3f3f091b5a
J7_PROVIDER_16691018_DATA_SHA256=345244db14600e2ff7ab5b53ccd40296db53c3cc363e7f3b0622de5774485b6d
J7_PROVIDER_16691018_SOURCE_SET_SHA256=48357cc401d92b51468dd32357f645908b0262b7b33185fc2f9b74f623e036d6
J7_PROVIDER_16691018_REJECTED_SHA256=cb17f359e685aafa7dd24f9a076e7e608575066126ac832b94a72d2089362e8a
J7_PROVIDER_16691018_REJECTED_DOWNLOAD=PASS_404_EMPTY_BODY
J7_PROVIDER_16691018_CANDIDATE_CLEANUP=PASS
J7_PROVIDER_16691018_SENSITIVE_FINDINGS=0
J7_PROVIDER_16421055_EXPORT_ID=696c1965-183e-43ba-ba47-4ee4d2ad5481
J7_PROVIDER_16421055_STATUS=HUMAN_VALIDATED
J7_PROVIDER_16421055_VALIDATED_SIZE_BYTES=37239
J7_PROVIDER_16421055_FILE_SHA256=37784bb4187fb80de06b765d8a9cb54b36f8035ed24eee82f9d6791c5a388880
J7_PROVIDER_16421055_DOWNLOAD_SHA256_MATCH=PASS
J7_PROVIDER_16421055_SENSITIVE_FINDINGS=0
J7_SYNTHETIC_HUMAN_REJECTION=PASS_REJECTED_RETAINED_NOT_DOWNLOADABLE
J7_SYNTHETIC_HUMAN_VALIDATION=PASS_HUMAN_VALIDATED_DOWNLOADED
J7_PROVIDER_16691018_HUMAN_VALIDATION=PASS_HUMAN_VALIDATED_DOWNLOADED
J7_J3_J4_J5_RELOCK_REVIEW=PASS
J7_EXPORT_GESTURE_PROVIDER_CALLS=0
J7_AUTOMATIC_PROVIDER_CALLS_DURING_QUALIFICATION=0
J7_PRIMARY_DATABASE_PURGE=NO
J7_PR_CLEAN_MERGEABLE=PENDING
```

Le statut est `READY_FOR_PR_REVIEW`. La recette humaine est complète ; seule une branche poussée et
une PR revue `CLEAN/MERGEABLE` permettent désormais `VALIDATED` et le déplacement dans `completed`.

Le premier parcours opérateur a réussi le 2026-08-19 sur l'événement fournisseur complet
`16707704` : candidat inspecté, confirmation exacte acceptée, statut `HUMAN_VALIDATED`, puis
téléchargement sans erreur. Le retour à l'historique relit une ligne unique avec le même statut,
la même taille et les mêmes hashes. Le fichier de 34 739 octets porte le SHA-256
`2f60b37151e81625a96ab9426f5474f4600191d102c398a4be8cfd96c5a230a9`, identique au terminal
local ; ses hashes de données et de sources ont été recalculés à l'identique et le scan sensible
est vide. Cette preuve supplémentaire précédait les trois gestes obligatoires désormais acquis.

Le second parcours opérateur a inspecté puis rejeté le candidat de l'événement fournisseur ciblé
`16691018`. Les avertissements `UNAVAILABLE_COMPONENT` sur les statistiques et
`PARTIAL_COMPLETENESS` sur les incidents et compositions sont conformes au corpus persisté. Le
terminal `.rejected.json` est seul conservé, son SHA-256 vaut
`cb17f359e685aafa7dd24f9a076e7e608575066126ac832b94a72d2089362e8a`, son scan sensible est vide,
et sa route de téléchargement renvoie `404` avec un corps vide. Cette preuve ferme le rejet
fournisseur ciblé ; sa validation/téléchargement distincte et les gestes synthétiques ont ensuite
fermé les cases de recette correspondantes.

La recette multi-campagnes a ensuite fourni deux séquences complémentaires. La première a terminé
J3 sur six pages et J4 phase 2, puis J5 s'est arrêté sur `TRANSPORT_TIMEOUT` avant persistance de sa
première réponse, sans retry. Après redémarrage explicite, la seconde instance a terminé J4 phase 2
sur le snapshot 315, les trois familles J5 sur 316/317/318, puis J3 sur les six pages 319 à 324.
Cela qualifie le fonctionnement successif J4/J5/J3 dans une même instance et l'arrêt sûr du premier
incident ; la preuve unique regroupant aussi les parcours locaux J6/J7 restait ouverte à ce stade.

Une troisième séquence commencée avant minuit le 19 août et terminée après minuit le 20 août en
heure de Paris ferme cette preuve unique. Dans une même instance, l'opérateur a terminé J3 sur les
snapshots 325 à 330, J5 sur 331/332/333, consulté la comparaison locale J6, rafraîchi J4 phase 2 sur
334, puis créé, validé et téléchargé l'export J7
`696c1965-183e-43ba-ba47-4ee4d2ad5481`. Les dix transports de cette séquence sont exclusivement les
six appels J3, les trois appels J5 et l'appel J4 ; J6 et J7 n'ont déclenché aucun transport. Les
trois campagnes réseau sont revenues à leur statut terminal verrouillé. Le fichier J7 téléchargé
mesure 37 239 octets, son SHA-256 recalculé est
`37784bb4187fb80de06b765d8a9cb54b36f8035ed24eee82f9d6791c5a388880`, et le scan indépendant ne
relève aucune clé interdite ni donnée sensible. Les scénarios synthétiques et la
validation/téléchargement requis pour `16691018` ont ensuite été exécutés séparément.

L'opérateur a enfin précisé, après inspection du JSON J3, que la racine `scheduled` expose les
compétitions trouvées pour une date et non des rencontres programmées. Le parseur séparait déjà
cette forme sans inventer de match. Le retour J4 affiche désormais le nombre de compétitions et
explique explicitement pourquoi zéro événement et zéro observation J4 sont attendus. Les treize
tests ciblés du service et de l'écran passent ; la suite standard complète a ensuite été relancée
avec 457 tests sans échec ni erreur.

La recette s'est terminée par trois gestes terminaux supplémentaires. Le cas fournisseur ciblé
`16691018` a été recréé, validé et téléchargé sous l'export
`af9735bc-ccfa-419e-bfea-fb6e500bc5bb`. Son téléchargement de 28 701 octets est identique au
terminal et porte le SHA-256
`87ed3d0e17a3150bf3a7aee1834da35e40a8dbcafecb037032cdf893ec543063`, avec les mêmes avertissements
attendus et zéro constat sensible.

L'événement synthétique `900001` a d'abord produit le terminal rejeté
`e6bd61ab-80e2-4a9d-854c-015ea37521a1`, conservé localement et non téléchargeable, puis le terminal
validé et téléchargé `563dbeb8-8660-422b-b214-e3b1b306aa6d`. Les deux décisions conservent le même
`dataSha256` `1a30148f03efce117bbe1cb8b30308eeab338741bbec392a4b5c58de488984a5` et le même
`sourceSetSha256` `0021ac5425c1fed41c489f81d7346cbcfbaa45fa667dc581aad0d832b123edc5`.
Le fichier validé de 8 707 octets porte le SHA-256
`fb637b4c87fc6a8cba230c6d4ad0928af3d52c1a793d379ae6ac9b9d1888b1f4` et correspond octet par octet
au téléchargement. Les cinq avertissements synthétiques sont explicites et les scans sensibles des
trois nouveaux terminaux sont vides.

```text
J7_HUMAN_QUALIFICATION=PASS
J7_PROVIDER_16691018_VALIDATED_EXPORT_ID=af9735bc-ccfa-419e-bfea-fb6e500bc5bb
J7_PROVIDER_16691018_VALIDATED_FILE_SHA256=87ed3d0e17a3150bf3a7aee1834da35e40a8dbcafecb037032cdf893ec543063
J7_SYNTHETIC_REJECTED_EXPORT_ID=e6bd61ab-80e2-4a9d-854c-015ea37521a1
J7_SYNTHETIC_REJECTED_FILE_SHA256=b83e0cc6f05b0d934ab4f48d6bbc959e9624935cd3bbd4095701b03ccc44ca09
J7_SYNTHETIC_VALIDATED_EXPORT_ID=563dbeb8-8660-422b-b214-e3b1b306aa6d
J7_SYNTHETIC_VALIDATED_FILE_SHA256=fb637b4c87fc6a8cba230c6d4ad0928af3d52c1a793d379ae6ac9b9d1888b1f4
J7_HUMAN_QUALIFICATION_SENSITIVE_FINDINGS=0
J7_HUMAN_QUALIFICATION_J7_TRANSPORTS=0
J7_STATUS=READY_FOR_PR_REVIEW
```
