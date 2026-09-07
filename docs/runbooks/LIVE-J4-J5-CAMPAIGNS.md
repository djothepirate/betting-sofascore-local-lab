# WO-058 — Utiliser une campagne live locale bornée

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Références : [ADR-SS-005 accepté](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md),
[architecture](../architecture/LIVE-J4-J5-CAMPAIGNS.md),
[qualification](../validation/WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md).

## Qualification hors fournisseur

La réalisation du WO utilise uniquement des données synthétiques et des bases Testcontainers.
Ces commandes sont lancées depuis le worktree du WO avec Java 25 et Docker Desktop disponibles :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Install-J3PlaywrightRuntime.ps1
.\scripts\Invoke-LivePlaywrightLoopbackQualification.ps1
```

L'installation explicite utilise le cache Chromium dédié `.tmp/provider-playwright-browsers`.
Le dernier lanceur construit le worker et exécute uniquement les qualifications de transport
et de vue live sur HTTP loopback. Les tests standards n'ouvrent pas de navigateur. Le test J6
requiert le port 8087 libre ; une application de l'opérateur ne doit pas être arrêtée implicitement.
Lire les XML Surefire/Failsafe effectifs, les skips et le résultat du lanceur. Un packaging avec
`-DskipTests` n'est pas une qualification.

Le replay local est une API Java `LiveReplayRunner.run(ReplayInput)`, utilisée dans ses tests.
L'entrée contient identifiant de fixture, heure initiale, durée, sélection, réponses avec SHA-256,
temps simulé de chaque réponse, arrêts et plafonds. Le résultat porte `SYNTHETIC_REPLAY`, son
empreinte de manifeste, les traces de familles et états finaux. `complete=false` signale notamment
un script de réponses épuisé. Aucun replay ne lance Playwright ni ne modifie la base.

## Préparer l'application de l'opérateur

L'application de V33 à la base utilisée par l'opérateur est une opération distincte de ces tests.
Préparer d'abord la sauvegarde, les empreintes et la restauration isolée selon
[J6](J6-BACKUP-RESTORE-AND-RETENTION.md), avec le conteneur et la base exacts. Ne pas faire pointer
une simple validation Spring/Flyway vers la base de l'opérateur pour obtenir un test vert.

La base préexistante est encore en V32 : le script J6 de cette réalisation exige V33 et ne peut
donc pas produire sa sauvegarde préalable. Utiliser pour cette étape l'outillage J6 V32 et ses
dépendances figés au commit de base `6dfd14286d4f269cbe100bd965257c20298538db`, dans un checkout
distinct, avec manifeste et cible exacts préparés pour cette opération. Vérifier cette sauvegarde
par restauration isolée V32 avant d'appliquer V33. Après migration, utiliser l'outillage J6 courant
V33 pour la nouvelle preuve. Ne pas modifier la version Flyway déclarée ni mélanger les scripts
V32/V33 pour franchir leur garde. Ces opérations n'ont pas été exécutées dans le présent lot.

Une fois cette préparation opérationnelle effectuée, l'application locale utilise les propriétés
suivantes. Le lot ne modifie aucun fichier `.env` et conserve les défauts désactivés.

| Propriété | Valeur ou exigence |
|---|---|
| `server.address` | `127.0.0.1` |
| `sofascore.enabled` | Opt-in fournisseur explicite |
| `sofascore.playwright.enabled` | Opt-in transport explicite |
| `sofascore.live.enabled` | Opt-in live explicite, défaut false |
| `sofascore.playwright.worker-jar` | JAR worker exact construit par le profil runtime |
| `PLAYWRIGHT_BROWSERS_PATH` | Cache dédié installé explicitement |
| `sofascore.live.docker-executable` | Chemin absolu du Docker CLI local |
| `sofascore.live.postgres-container` | Nom exact du conteneur PostgreSQL dont le volume sera mesuré |
| `sofascore.live.qualified-match-capacity` | 1 pour le premier pilote |
| `sofascore.live.duration` | Au plus 4 h, attente avant coup d'envoi comprise |
| `sofascore.live.request-envelope` / `processing-envelope` | 10 s / 1 s, hypothèses d'admission initiales |
| `sofascore.live.qualification-sha256` | Preuve revue obligatoire pour abaisser l'enveloppe ou passer à 2/3 matchs |

L'exécutable Docker et le nom de conteneur servent à lire l'espace du volume PostgreSQL réel,
avec timeout et refus fermé si la mesure échoue. Le pilote à un match réserve 5 242 880 000
octets de réponses possibles ; la marge d'espace exige deux fois cette enveloppe plus 1 Gio.
Les plafonds de 1 000/3 000 tentatives sont locaux et ne décrivent pas un quota fournisseur connu.
Les anciennes propriétés `automatic-refresh-enabled` et `live-polling-enabled` restent désactivées.

## Parcours depuis /events

1. Dans les résultats normalisés, sélectionner le match du pilote puis préparer la campagne.
   Le serveur vérifie UUID, ID fournisseur et snapshot source ; aucune requête fournisseur ne part.
2. Relire la sélection figée, les heures, la fenêtre, les plafonds, le profil de capacité et
   l'empreinte du manifeste. La préparation expire après cinq minutes ; toute modification de
   politique impose une nouvelle préparation.
3. Confirmer explicitement le lancement. Cette action autorise cette seule session et ses cycles.
   Le navigateur est créé une fois ; un double clic ou un formulaire rejoué ne crée pas une seconde session.
4. Observer séparément statut sportif J4, date du score, état de collecte, fraîcheur de chaque
   famille, prochaine échéance, retard, budget et complétude. Les données J5 d'un même cycle
   proviennent de trois instants distincts.
5. Utiliser « Arrêter » pour un match ou pour la campagne. L'arrêt individuel laisse terminer
   son éventuel GET engagé ; l'arrêt global annule le transport partagé. Attendre la preuve de
   nettoyage avant un nouveau lancement.

J4 est interrogé dès le lancement puis à la minute tant que `notstarted`. J5 commence après
`inprogress`. Les incidents déclenchent des contrôles, jamais une conclusion sportive.
Le secours J4 intervient à cinq minutes sans signal ; seule sa réponse `finished` confirme
la fin. Un dernier triplet J5 peut rester incomplet si la fenêtre, le budget ou l'indisponibilité
d'une famille l'empêche. Un 404 J5 est rééchantillonné au cycle normal suivant.

Le rafraîchissement de l'écran est une lecture locale toutes les cinq secondes. Masquer l'onglet
suspend ces lectures, sans interrompre une campagne déjà lancée dans l'application. Fermer le
processus de l'application, perdre le worker ou mettre le poste en veille termine la session.

## Arrêt et reprise fermée

Un schéma métier incompatible admissible arrête le seul match. Un refus HTTP hors 404, timeout,
contenu inattendu, défaut d'identité, exception interne ou erreur de stockage arrête globalement.
La dernière donnée acquise reste visible avec sa date ; aucune erreur n'est transformée en zéro.

Après crash ou nettoyage incertain, `CLEANUP_REQUIRED` conserve l'exclusion fournisseur. Aucun
délai, redémarrage ou nouvelle préparation ne la lève. Consulter les preuves de campagne et faire
vérifier l'identité et la disparition de l'arbre de processus avant toute intervention locale
sur ce garde. Ce lot ne fournit pas de bouton de libération aveugle ; aucun GET n'est rejoué.
Un processus propriétaire encore actif ou d'identité inaccessible demeure protégé.

`LIVE_RAW_PREVIOUSLY_PURGED` signifie qu'une réception identique rencontre une ancienne preuve
dont J6 a purgé le corps. La réception nouvelle est annulée atomiquement ; la campagne s'arrête
pour stockage. Une réhydratation ou une politique de rétention différente exige un travail
distinct ; ne pas réécrire les dates du snapshot historique.

Le premier pilote fournisseur, la migration de la base de l'opérateur et la montée à deux ou
trois matchs restent des opérations à préparer avec leur manifeste exact. Le rapport synthétique
du WO ne vaut ni pilote réel réussi, ni validation de performance sur quatre heures.
