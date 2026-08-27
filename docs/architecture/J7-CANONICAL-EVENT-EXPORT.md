# J7 — Export canonique local, audité et soumis à décision humaine

## 1. Frontière du jalon

J7 assemble un fichier JSON autonome pour une identité canonique locale et pour ses seules versions
courantes. Il sélectionne la dernière observation d'état J4, le dernier détail disponible et la
dernière observation de chacune des trois familles J5. L'historique complet J6 n'est pas recopié.

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
J7_SELECTION_MODE=LATEST_AVAILABLE
J7_PROVIDER_TRANSPORT=ABSENT
J7_AUTOMATIC_EXPORT=ABSENT
J7_BETTING_PROJECT_IMPORT=ABSENT
```

Les provenances `SYNTHETIC_FIXTURE` et `PROVIDER_SNAPSHOT` sont acceptées, mais restent visibles
pour chaque composant. Un export qui contient une fixture synthétique porte un avertissement et ne
constitue jamais une qualification fournisseur. Les données manquantes ou partielles ne sont pas
inventées.

## 2. Contrat JSON v1

Le schéma Draft 2020-12 est versionné dans le classpath sous l'identifiant stable :

```text
urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
```

Sa version métier est `1.0.0`. La racine accepte exactement deux propriétés :

- `manifest` : identité de l'export, schéma, date UTC, générateur, mode de sélection, empreintes,
  décision, sources et avertissements ;
- `data` : identité canonique, état courant, détail éventuel, statistiques, incidents et
  compositions.

Le validateur NetworkNT charge exclusivement le schéma depuis le classpath, utilise le dialecte
Draft 2020-12 avec assertions de format et refuse la résolution de ressources distantes. Le
contrat ferme les objets avec `additionalProperties: false`, borne textes, tableaux, identifiants,
dates et hashes, et interdit les combinaisons incohérentes de statut et de décision. Tous les
identifiants numériques persistés comme `long` — observation, snapshot, événement, équipe,
compétition, saison, stade et joueur — sont des entiers strictement positifs bornés à
`9223372036854775807` (`Long.MAX_VALUE`) ; un entier hors de cette plage est refusé au lieu d'être
tronqué.

La sérialisation est UTF-8, compacte, déterministe, terminée par un seul LF et limitée à 5 Mio.
Les tableaux métier conservent l'ordre déjà persisté.

## 3. Les cinq emplacements de composants

Le tableau `manifest.sources` contient exactement cinq entrées, toujours dans cet ordre :

1. `EVENT_STATE` ;
2. `EVENT_DETAILS` ;
3. `EVENT_STATISTICS` ;
4. `EVENT_INCIDENTS` ;
5. `EVENT_LINEUPS`.

Une entrée disponible conserve l'identifiant de l'observation normalisée, le type et la référence
de source, l'identifiant de snapshot ou de fixture, le SHA-256 source, le SHA-256 normalisé, la
version du parseur, l'heure de réception et l'état du payload brut. Les familles J5 conservent en
plus leur statut, score, compteurs et chemins manquants de complétude.

Chaque famille de données expose explicitement son état :

| État | Sens |
|---|---|
| `PRESENT` | une observation existe et ses données normalisées sont exportées |
| `UNAVAILABLE` | le fournisseur a explicitement signalé la famille indisponible |
| `MISSING` | aucune observation locale courante n'existe |
| `EMPTY_VALID` | une liste vide est un résultat métier valide |

L'état de l'événement est obligatoire. Le détail peut être `MISSING`. Les trois familles J5
peuvent être présentes, indisponibles, manquantes ou vides valides selon leur observation locale.

## 4. Avertissements et refus bloquants

Les avertissements sont structurés et n'empêchent pas la création :

- `MISSING_COMPONENT` ;
- `UNAVAILABLE_COMPONENT` ;
- `PARTIAL_COMPLETENESS` ;
- `SYNTHETIC_SOURCE` ;
- `MIXED_SOURCE_KINDS` ;
- `RAW_PAYLOAD_PURGED` ;
- `RAW_PAYLOAD_LEGACY_ABSENT`.

En revanche, J7 refuse une provenance incohérente, un hash invalide, une identité différente, une
enveloppe hors schéma, un fichier trop grand, une altération ou un contenu sensible. Avant parsing,
le contrôle impose un UTF-8 strict, refuse CR et applique le scanner commun aux en-têtes
`Authorization`, cookies, champs d'identification, jetons de forme JWT et blocs de clé privée. Le
garde J7 ajoute une recherche insensible à la casse dans tout le texte et récursivement dans les
clés JSON : payload brut, schéma d'URL, URI de requête, headers, cookies, bearer, jetons, mots de
passe, clés API, secrets client, sessions, `.env`, identifiants de sauvegarde et phrases secrètes
sont bloqués.

L'assemblage de l'état courant s'exécute dans une transaction PostgreSQL en lecture seule avec
isolation `REPEATABLE_READ`. Il réutilise les stores J4/J5/J6 et ne sélectionne jamais
`payload_raw`. Lors d'une décision, la transaction externe verrouille aussi la ligne de l'événement
et prend un verrou consultatif transactionnel dérivé de son UUID. Les triggers V23 imposent ce même
verrou à toute insertion, mise à jour ou suppression dans les trois tables d'observations J4/J5,
ainsi qu'aux insertions dans les quatre tables métier filles J5. Une insertion fille dont
l'observation parente n'est pas encore visible est refusée en mode fail-closed et doit être
retentée après publication du parent.
La relecture `REPEATABLE_READ`, le calcul de `sourceSetSha256` et la transition terminale forment
ainsi une fenêtre de fraîcheur fermée : une observation concurrente termine avant la relecture ou
attend la fin de la décision, mais ne peut pas s'intercaler entre les deux.

## 5. Trois empreintes distinctes

`dataSha256` couvre la sérialisation JSON compacte et déterministe du seul objet `data`. Il est
embarqué dans le manifeste et doit rester identique entre le candidat et sa forme terminale.

`sourceSetSha256` couvre les cinq emplacements, y compris les marqueurs d'absence, identifiants
d'observation, références et hashes. Avant validation humaine, la sélection courante est relue sous
le verrou d'événement et l'empreinte recalculée. Une nouvelle observation locale produit
`SOURCE_SET_CHANGED` : le candidat doit être rejeté ou remplacé par un nouveau candidat, jamais
approuvé comme s'il était encore courant. Les écritures d'observation utilisant le même verrou
PostgreSQL ne peuvent pas rendre ce contrôle obsolète avant la validation terminale.

Le SHA-256 du fichier complet n'est pas embarqué afin d'éviter l'autoréférence. Le hash initial du
candidat et le hash du fichier courant sont persistés. Le hash courant et la taille sont revérifiés
avant aperçu et téléchargement.

## 6. Cycle de décision

La création produit uniquement `COHERENCE_CHECKED`. Ce statut signifie que le schéma, la taille,
l'identité, les provenances, les hashes et le contenu sensible ont été contrôlés ; il ne remplace
pas une approbation humaine.

Après inspection HTML, l'opérateur choisit une transition terminale unique :

- `HUMAN_VALIDATED`, avec la confirmation exacte
  `VALIDER EXPORT J7 <exportId> <dataSha256>` ;
- `REJECTED`, avec la confirmation exacte `REJETER EXPORT J7 <exportId>` et un motif sûr de 1 à
  500 caractères.

La décision régénère l'enveloppe en ne modifiant que `manifest.validation`, puis revérifie le
schéma, le scanner, l'identité, les sources et l'invariance de `dataSha256`. Avant d'écrire le
fichier terminal, J7 persiste en write-ahead une intention de décision contenant le statut,
l'heure, le motif éventuel, le chemin terminal, son SHA-256 et sa taille attendus. Un retry retrouve
cette intention, régénère les mêmes octets et n'accepte un fichier terminal déjà présent que si
statut, heure, motif, chemin, hash et taille coïncident exactement. La transition de statut ne peut
ensuite reprendre que cette intention authentifiée. Seul `HUMAN_VALIDATED` est téléchargeable. Une
validation humaine locale ne déclenche ni transfert ni import.

## 7. Persistance V23

`V23__j7_canonical_event_exports.sql` étend la table générique `export_manifest` sans modifier V1
à V22. Les anciennes lignes restent valides avec les colonnes J7 nulles. Une ligne J7 conserve au
minimum : UUID d'export et d'événement, identifiant/version de schéma, dates, trois hashes,
taille/chemin courants, cinq sources structurées, snapshots sources, avertissements, statut,
décision éventuelle et intention terminale write-ahead. Cette dernière conserve statut, heure,
motif éventuel, chemin, SHA-256 et taille du fichier terminal attendu.

Les contraintes, index partiels et trigger PostgreSQL imposent :

- un seul candidat en attente par événement, schéma et version ;
- aucun second export validé portant le même `dataSha256` ;
- insertion obligatoire en `COHERENCE_CHECKED` ;
- une seule transition vers `HUMAN_VALIDATED` ou `REJECTED` ;
- immutabilité de l'événement, du schéma, de la génération, des sources et hashes de données ;
- enregistrement ou retrait atomique d'une intention terminale complète tant que le candidat reste
  `COHERENCE_CHECKED`, puis décision finale obligatoirement identique à cette intention ;
- cohérence entre suffixe de fichier et statut ;
- existence locale de chaque snapshot référencé ;
- interdiction de supprimer une ligne J7.

V23 ajoute aussi un verrou consultatif transactionnel par événement aux écritures des tables
`canonical_event_observation`, `event_detail_observation` et `j5_event_data_observation`, puis aux
insertions de `j5_event_metric`, `j5_event_incident`, `j5_event_lineup_side` et
`j5_event_lineup_player`. Ces quatre triggers résolvent l'événement par le parent J5 et refusent
l'écriture si ce parent n'est pas encore visible, au lieu de poursuivre sans verrou. Le service de
décision prend le même verrou avec la ligne `canonical_event` avant la relecture des sources. Une
mutation autorisée de `provider_snapshot` prend également, dans un ordre stable, les verrous des
événements qui référencent ce snapshot ; l'état de rétention du brut ne peut donc pas changer au
milieu d'une décision. Cela n'autorise ni n'exécute la purge primaire J6.

Les scripts J6 de sauvegarde/restauration attendent désormais Flyway V26. Cette compatibilité ne
constitue pas une autorisation de purge : la purge de la base primaire reste non exécutée et hors
du Work Order J7.

## 8. Stockage de fichiers

Les fichiers sont écrits uniquement sous `sofascore.export-directory`, `./exports` par défaut,
avec un nom construit côté serveur :

```text
j7-<canonicalEventId>-<exportId>.candidate.json
j7-<canonicalEventId>-<exportId>.validated.json
j7-<canonicalEventId>-<exportId>.rejected.json
```

L'adaptateur refuse chemin absolu ou fourni par l'utilisateur, traversée de répertoire, sortie de
racine, lien symbolique, fichier non régulier et écrasement. Une écriture crée et synchronise
d'abord un fichier temporaire dans la racine, puis publie son inode par création atomique d'un lien
physique vers la destination nouvelle sur le même système de fichiers. La création du lien a une
sémantique create-new sans remplacement ; le nom temporaire est ensuite supprimé. J7 n'utilise pas
`ATOMIC_MOVE` et n'effectue aucun repli écrasant si les liens physiques ne sont pas pris en charge.
Un retry n'accepte un fichier portant l'UUID attendu que si ses octets, sa taille et son SHA-256
correspondent exactement.

Si l'insertion d'un candidat et sa relecture PostgreSQL ont toutes deux échoué, le fichier est
conservé. Au prochain geste de création, J7 n'énumère que les noms candidats stricts de
l'événement, revalide leur schéma, identité, date, hashes et état de sources, puis reconstruit
l'enveloppe avec le générateur courant. La ligne manquante n'est recréée que si les octets sont
exactement identiques. Un candidat obsolète est ignoré ; un candidat non reproductible est traité
comme altéré. Un fichier produit par une autre version n'est donc repris que si la version courante
reproduit rigoureusement les mêmes octets.

Le candidat physique est remplacé après une décision réussie ; le hash initial reste en base.
L'intention write-ahead est persistée avant l'écriture, puis le fichier terminal est écrit et
revérifié avant la transition PostgreSQL. Un échec laisse une preuve de reprise bornée : le retry
doit reproduire exactement l'intention persistée et le fichier éventuel avant de terminer la
transition. Un rejet conserve donc un fichier `.rejected.json`, clairement terminal mais non
téléchargeable depuis l'interface.

## 9. Interface HTML locale

J7 expose uniquement les routes locales suivantes :

```text
GET  /events/{canonicalEventId}/exports
POST /events/{canonicalEventId}/exports/candidates
GET  /events/{canonicalEventId}/exports/{exportId}
POST /events/{canonicalEventId}/exports/{exportId}/validate
POST /events/{canonicalEventId}/exports/{exportId}/reject
GET  /events/{canonicalEventId}/exports/{exportId}/download
```

Les trois POST consomment le jeton local à usage unique existant. L'aperçu est rendu par
Thymeleaf avec échappement. Toutes les réponses portent `no-store`, `no-cache`, une expiration
immédiate et `X-Robots-Tag: noindex, nofollow, noarchive`.

Le téléchargement exige `HUMAN_VALIDATED`, résout de nouveau le chemin sous la racine sans suivre
de lien, recalcule taille et SHA-256 et revalide le schéma avant de servir `application/json` en
pièce jointe. Un candidat répond `409`; un rejet ou un export inconnu n'est pas téléchargeable.

## 10. Journalisation et absence de réseau

Les journaux J7 sont limités aux UUID canonique/export, statut, tailles et hashes. Ils ne doivent
jamais contenir le document JSON ni le motif intégral du rejet.

J7 n'ajoute aucun client HTTP, endpoint fournisseur, scheduler, polling, retry réseau, transport
sortant ou appel SofaScore. Les configurations et verrous J3/J4/J5 ne sont ni ouverts ni modifiés
par l'export. Il n'existe aucun endpoint JSON de consultation générale et aucune intégration au
Betting Project.

## 11. Limites et décisions différées

- export de l'historique complet J6 ;
- lots par date ou multi-événements ;
- import ou transport vers le Betting Project ;
- signature externe, publication, VPS ou production ;
- génération planifiée ou automatique ;
- purge primaire J6.

Ces évolutions exigeraient un Work Order distinct. `HUMAN_VALIDATED` signifie seulement « fichier
local inspecté et éligible à un usage externe ultérieur » ; il ne donne aucune autorisation de
transfert.
