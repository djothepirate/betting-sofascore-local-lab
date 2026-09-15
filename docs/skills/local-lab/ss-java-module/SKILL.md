---
name: ss-java-module
description: "Concevoir ou relire une évolution Java du SofaScore Local Lab : responsabilités, ports, adaptateurs, dépendances et activation Spring/Maven. Ne pas utiliser pour un simple build ni une architecture limitée à un autre dépôt."
metadata:
  version: "0.1.0-candidate.2"
---

# Placer une évolution dans l'architecture Java du Lab

Identifier le worktree, le SHA, le parcours et la décision demandés. Résoudre les chemins
depuis cette racine ; lire AGENTS et les décisions applicables au parcours. Conserver
EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY.
Une revue peut rester en lecture seule : une commande proposée n'est pas un test exécuté.
Respecter les autorisations acquises et les sources disponibles ; les références d'un
document n'élargissent pas une liste de fichiers autorisés.

## Partir des responsabilités réelles

- Lire `docs/architecture/ARCHITECTURE.md`, `pom.xml` et les classes du parcours.
  Le Lab est monomodule Maven : un domaine ou un répertoire de sources n'est pas un
  sous-module. Le socle demandé est Java 25, Spring Boot 4.1.0 et le wrapper du dépôt ;
  confronter les versions à AGENTS et au POM courant. Ne pas importer le découpage du
  monolithe Betting Project ni créer une dépendance de celui-ci au Lab.
- Suivre une entrée Web, une commande ou un ordre jusqu'aux effets : objets `domain`,
  orchestration `application`, contrats et interfaces, adaptateurs de persistance,
  fichier ou transport, puis configuration. Relever classes, méthodes, imports,
  constructeurs, appels, types d'erreur propagés et propriétaires des ressources.
  Pour les valeurs de domaine, expliquer aussi les distinctions qui conditionnent
  le parcours : déclencheur de l'ordre, provenance des données, date métier et instants
  d'exécution ne désignent pas la même chose. Dans J3, lire J3AutomationData et
  J3CollectionData pour séparer déclencheur/source de chaque page et date cible/UTC.
  Une interface peut être dans
  `application.network.playwright` ; son rôle ne découle pas du seul nom de package.
- Décrire les dépendances telles qu'elles existent. Une référence de l'application à
  un parseur ou catalogue concret reste visible dans la carte ; ne pas lui substituer
  un port imaginé pour dessiner une séparation parfaite. Distinguer dépendance existante,
  proposition et contrainte effectivement imposée. Examiner aussi les exceptions
  d'adaptateur utilisées dans le cas d'usage : les omettre masquerait une dépendance
  même si aucun transport concret n'y est instancié. Une dette constatée ne déclenche pas
  une refonte générale dans une revue ciblée.
- Pour le changement demandé, placer chaque responsabilité dans l'élément existant
  approprié. Justifier une nouvelle interface par une frontière ou une substitution
  utile. Éviter les appels du domaine à un transport concret et les accès à la persistance
  ajoutés dans une règle métier. Conserver les représentations brutes et normalisées,
  la provenance et les contrats ; ne pas faire dépendre une identité d'un libellé variable.

## Vérifier la composition et les effets

- Séparer profils Maven, profils Spring, propriétés liées et conditions d'exécution.
  Lire `application.yml`, `application-local.yml`, les propriétés typées et les beans
  impliqués. Un nom de propriété ressemblant à celui attendu ne prouve pas son binding.
  Vérifier le constructeur effectivement injecté, `@Primary`, les éventuels décorateurs
  et le contexte réellement chargé par les tests, sans extrapoler un contexte réduit.
- Localiser transaction, thread, admission, publication et nettoyage. Suivre les appels
  qui traversent réellement le bean transactionnel, les effets avant commit et les
  échecs possibles entre opérations. Une annotation ou un retour réussi ne prouve pas
  à lui seul l'atomicité de la publication et de son ledger. Vérifier les ports et
  adaptateurs concernés ; confier une évolution de persistance à `ss-postgres-change`.
- Pour J3 durable, lire `ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md`,
  `docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md` et le code actuel. Les récits J3
  manuel, catalogue en mémoire ou ancien protocole restent datés. L'exception adoptée
  autorise les ordres durables et le clic direct concernés ; ne pas demander de nouveau
  leur autorisation ni étendre cette exception aux autres parcours.
- Examiner `J3RuntimeService` : disponibilité du contexte Web local et adresse loopback,
  profil et propriété d'exécution, propriétaire durable, reconstruction, ticks et
  consommateur sériel sont des conditions distinctes. Un runtime configuré n'arme pas
  tous les transports. Suivre `J3CollectionExecutor`, ses accès fournisseur/import/cache,
  puis `J3CollectionCompletionService` jusqu'à la publication et au terminal J8.

## Préserver la frontière du worker

- La bibliothèque Playwright et ses classes d'exécution résident sous
  `src/provider-playwright/java`, ajoutées par le profil Maven
  `provider-playwright-runtime`. Le parent standard emploie ses contrats et la
  supervision IPC ; la présence du nom Playwright dans une interface ne signifie pas
  que la bibliothèque navigateur est dans son classpath. Refuser son ajout implicite
  aux dépendances/sources standard. Vérifier aussi les répertoires de sortie séparés
  pour ne pas prendre un résidu d'un build de profil pour une preuve standard.
- Distinguer compilation du runtime, production du JAR worker et lancement effectif.
  Activer un profil Maven n'est pas lancer Chromium. Suivre la factory résiliente,
  le superviseur de JVM enfant, le protocole et les validations des deux côtés.
  Une validation parente ne remplace pas une validation à l'entrée du worker.
  Pour une origine loopback, une URI structurée ne prouve pas un port TCP utilisable :
  vérifier schéma, hôte, borne de port, chemin et champs autorisés avant l'effet réseau.
- Dans une pause live autorisée, suivre le transfert du droit d'émettre, la création
  du contexte J3 temporaire et son nettoyage, puis la restitution au contexte live
  conservé. Un même worker peut porter des contextes distincts ; un tick ne recrée
  pas le contexte live. Une perte de contexte ou un nettoyage non établi ne justifie
  ni reprise automatique ni lancement d'un navigateur de remplacement.
- Relire les ADR du transport pour toute modification de cette frontière. Un défaut
  local ou une ancienne qualification n'autorise pas un nouvel endpoint, fallback,
  état de session conservé, réseau fournisseur ou activation dans les tests standards.

## Relier les contrôles à ce qu'ils couvrent

- Rechercher les contrôles présents : `scripts/Verify-Local.ps1`, tests de propriétés,
  policy, contexte Spring, persistance et worker. Le garde textuel du lanceur n'est
  pas une analyse exhaustive des dépendances. Ne pas annoncer ArchUnit ou une règle
  architecturale exécutée sans dépendance, test et preuve correspondants.
- Pour chaque test utile, relever fichier, méthode, source Maven, motif d'inclusion,
  profil, exécution Surefire/Failsafe et commande effective. Une IT présente peut
  exiger `-Dit.test` et une exécution de plugin précise ; une commande historique
  explicite ne démontre pas son inclusion dans le cycle standard actuel.
- Distinguer test pur de configuration/protocole, Spring simulé, PostgreSQL réel,
  threads avec transport simulé et qualification native loopback. Les tests sous
  `src/provider-playwright-test` et `src/provider-playwright-qualification-test`
  n'ont pas la même portée ; aucun ne vaut collecte fournisseur autorisée.
- Employer `ss-verify` pour les commandes et rapports : lire le POM et le lanceur
  avant de choisir `-DskipITs` ou `integration-tests`. `--offline` concerne la
  résolution Maven et n'empêche pas les tests de démarrer une base ou un navigateur.
  Sur une revue documentaire, lister les contrôles requis et leurs limites sans les
  exécuter implicitement ni reprendre les anciens totaux comme un succès courant.

- Dans une preuve historique, restituer les incidents qui conditionnent son verdict,
  leur phase et la suite attestée : résolution/accès aux dépendances avant tests,
  compilation, exécution ou conservation des rapports. Un refus d'accès du sandbox
  à un JAR Maven n'établit pas une régression applicative ; une relance réussie ne
  doit pas effacer cet incident du bilan. Conserver la portée de chaque tentative.

## Livrer une proposition vérifiable

Rendre la carte classes/responsabilités/dépendances, les écarts observés, la modification
minimale proposée et les risques. Relier chaque contrôle à son comportement, sa commande
et sa preuve, ou à son absence d'exécution. Séparer constat, hypothèse et couverture
manquante ; une preuve sur une borne TCP ne démontre pas toute l'architecture des ports.
Utiliser `ss-data-contract-replay` si le contrat, parseur ou replay évolue et
`ss-postgres-change` pour la persistance. Les corrections hors périmètre restent des
travaux identifiés ; la revue ne les applique pas de sa propre initiative.

Pour chaque prochaine action proposée, indiquer explicitement son relais applicable :
commandes/qualification → `ss-verify`, contrat ou parsing/replay →
`ss-data-contract-replay`, transaction/schéma/ledger → `ss-postgres-change`.
Ce routage appartient à la restitution, même si l'action reste future et qu'aucun
autre skill n'est exécuté pendant la revue. Avant remise, vérifier que la synthèse
conserve les dépendances et distinctions métier relevées, les incidents qui limitent
les preuves et ces relais ; la longueur d'une carte ne garantit pas sa complétude.
