# Runbook J7 — Export canonique local et décision humaine

## 1. Objet et autorisations

Ce runbook qualifie, sans nouvel appel fournisseur, le cycle J7 d'un événement local : candidat,
inspection, rejet ou validation, puis téléchargement du seul fichier validé.

```text
PROVIDER_CALL_REQUIRED=NO
NETWORK_OPT_IN_REQUIRED=NO
POLLING_OR_SCHEDULING=NO
BETTING_PROJECT_IMPORT=NO
PRIMARY_DATABASE_PURGE=NOT_AUTHORIZED
HUMAN_DECISION_REQUIRED=YES
```

La recette requise comprend un rejet puis une validation du corpus synthétique, suivis d'une
validation de l'événement fournisseur déjà persisté `16691018`. Elle ne collecte aucune donnée
supplémentaire. Les statuts du laboratoire restent `EXPERIMENTAL`, `LOCAL_ONLY`,
`NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`.

## 2. Prérequis

- Windows 11, Java 25 et Docker Desktop ;
- branche J7 et Work Order actif ;
- PostgreSQL local sain avec Flyway V23 ;
- application liée exclusivement à `127.0.0.1:8087` ;
- répertoire `sofascore.export-directory` local, accessible en écriture et ignoré par Git ;
- configurations J3/J4/J5 désactivées et états persistants `LOCKED` ;
- corpus synthétique J4/J5 déjà importable hors ligne ;
- événement fournisseur `16691018` déjà présent avec ses observations J4/J5/J6 ;
- aucune application modifiant simultanément les mêmes observations pendant la revue.

La dernière précaution évite une recette difficile à lire, mais la validation possède aussi une
barrière technique : verrou de ligne et verrou consultatif PostgreSQL par événement, partagé avec
les triggers d'écriture J4/J5. Une écriture concurrente finit avant la relecture de fraîcheur ou
attend la fin de la décision ; elle ne peut pas s'intercaler après le recalcul du jeu de sources.

Ne jamais activer une voie fournisseur pour préparer J7. Si `16691018` n'est pas déjà présent,
consigner le prérequis manquant et arrêter cette partie de la recette : ne pas le recollecter.

## 3. Porte technique avant recette

Avec toutes les voies fournisseur verrouillées, exécuter :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Les deux commandes doivent finir sans échec et sans appel fournisseur. Vérifier ensuite :

```powershell
git diff --check
git status --short
git check-ignore .env exports\j7-control.json
Select-String -Path .\src\main\resources\application.yml -Pattern '127\.0\.0\.1'
```

Contrôler que seule V23 est ajoutée après V22, qu'aucun ancien fichier de migration n'a été
modifié, qu'aucun export runtime n'est suivi par Git et que `.env` demeure ignoré. Le rapport de
readiness J7 doit consigner les résultats réels ; une valeur `PENDING` interdit la clôture du Work
Order.

## 4. Démarrer sans réseau

Depuis la racine du dépôt :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Ouvrir `http://127.0.0.1:8087`. Avant toute création :

1. vérifier `127.0.0.1:8087` dans la barre d'adresse ;
2. vérifier les contrôles J3, J4 et J5 à l'état `LOCKED`, sans intention `READY` ou `EXECUTING` ;
   leurs opt-ins peuvent être configurés pour la session multi-campagnes, mais aucune action
   fournisseur ne doit être en cours pendant un geste J7 ;
3. vérifier l'absence de collecte, polling ou tâche planifiée ;
4. importer le corpus synthétique existant seulement si nécessaire, via son action hors ligne ;
5. ouvrir la fiche de l'événement synthétique puis le lien **Exports J7**.

La page J7 affiche l'identité canonique, l'état courant et l'historique des exports. Elle ne doit
pas exposer de bouton ou de lien fournisseur.

## 5. Rejeter un premier candidat synthétique

### 5.1 Créer et inspecter

Utiliser **Créer un candidat J7**. Cette action consomme un jeton local à usage unique, assemble
les cinq emplacements courants et produit un statut `COHERENCE_CHECKED`.

Dans l'aperçu, vérifier :

- exactement `manifest` et `data` à la racine ;
- schéma `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1`, version `1.0.0` ;
- mode `LATEST_AVAILABLE` ;
- cinq sources dans l'ordre état, détail, statistiques, incidents, compositions ;
- identité canonique égale à la fiche ouverte ;
- `dataSha256`, `sourceSetSha256`, taille et SHA-256 du fichier affichés ;
- avertissement `SYNTHETIC_SOURCE` ;
- données et complétude conformes aux écrans J4/J5 ;
- aucun objet brut, URI, header, cookie, jeton, session, `.env`, sauvegarde ou phrase secrète.

Les identifiants numériques exportés doivent être des entiers positifs dans la plage Java `long`
(`1` à `9223372036854775807`). Le contrôle de sécurité impose aussi un UTF-8 strict et bloque les
en-têtes d'autorisation, cookies, champs de credential, JWT, clés privées et les variantes de clés
ou textes sensibles reconnues par le garde J7.

Le candidat `.candidate.json` ne doit pas être téléchargeable. Un accès direct à son URL de
téléchargement doit répondre `409`.

### 5.2 Rejeter

Copier exactement la phrase présentée par l'interface :

```text
REJETER EXPORT J7 <exportId>
```

Saisir un motif explicite de 1 à 500 caractères, puis confirmer. Vérifier :

- statut `REJECTED` et date de décision ;
- `dataSha256` inchangé ;
- hash candidat initial toujours visible dans les preuves persistées ;
- fichier terminal `j7-<canonicalEventId>-<exportId>.rejected.json` sous la racine configurée ;
- absence du candidat physique initial après succès ;
- téléchargement refusé, avec `404` pour le rejet.

Ne pas copier le motif intégral dans les logs ou dans une capture contenant d'autres données
sensibles.

Si la création répond `DATABASE_UNAVAILABLE` après avoir écrit un fichier candidat, rétablir
PostgreSQL puis répéter le même geste depuis l'historique. J7 recherche alors uniquement les noms
candidats stricts de cet événement et ne réinsère un manifeste que si le document correspond à
l'état courant et si le générateur courant le reproduit octet pour octet. Ne jamais renommer ou
modifier ce fichier : toute divergence est refusée comme altération.

## 6. Valider un nouveau candidat synthétique

Revenir à l'historique J7 et créer un nouveau candidat. Il doit recevoir un nouvel UUID. Inspecter
de nouveau l'enveloppe, puis copier exactement la phrase affichée :

```text
VALIDER EXPORT J7 <exportId> <dataSha256>
```

La validation relit d'abord les observations courantes. Si `SOURCE_SET_CHANGED` apparaît, ne pas
forcer : le candidat n'est plus courant. Le rejeter, inspecter le nouvel état local, puis créer un
nouveau candidat.

La relecture et la décision sont protégées par le verrou PostgreSQL de l'événement. Avant la
publication du fichier terminal, une intention write-ahead enregistre statut, heure, motif
éventuel, chemin, hash et taille attendus. Si une indisponibilité disque ou PostgreSQL interrompt
l'opération, répéter exactement la même décision : la reprise régénère les mêmes octets et refuse
tout fichier existant qui ne correspond pas intégralement à l'intention persistée. Ne jamais
renommer, remplacer ou corriger manuellement un fichier J7.

Après validation réussie, vérifier :

- statut `HUMAN_VALIDATED` et date de décision ;
- `dataSha256` identique à celui du candidat ;
- fichier `j7-<canonicalEventId>-<exportId>.validated.json` ;
- téléchargement disponible en `application/json` et pièce jointe ;
- hash et taille téléchargés identiques aux valeurs de l'interface.

Exemple de vérification locale du téléchargement :

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath '<fichier téléchargé>'
(Get-Item -LiteralPath '<fichier téléchargé>').Length
```

Comparer le hash en minuscules au SHA-256 courant affiché. Ne pas placer une copie téléchargée dans
le dépôt.

## 7. Qualifier l'événement fournisseur local `16691018`

Rechercher l'événement dont `providerEventId=16691018` dans les données déjà persistées, ouvrir sa
fiche puis **Exports J7**. Ne pas activer J4 ou J5 et ne lancer aucune collecte.

Créer un candidat et contrôler en particulier :

- `sourceKind=PROVIDER_SNAPSHOT` pour les composants issus des snapshots locaux ;
- références `snapshot:<id>`, hashes source et normalisés, parseurs et heures de réception ;
- statistiques explicitement `UNAVAILABLE` lorsque l'observation courante locale le signale ;
- incidents et compositions `PARTIAL` avec leur score, compteurs et chemins manquants ;
- snapshots et identifiants d'observations cohérents avec les écrans J5/J6 ;
- état du brut `RETAINED`, `PAYLOAD_PURGED` ou `LEGACY_ABSENT` conforme à J6 ;
- avertissements `UNAVAILABLE_COMPONENT`, `PARTIAL_COMPLETENESS` et, si applicable,
  `RAW_PAYLOAD_PURGED`/`RAW_PAYLOAD_LEGACY_ABSENT` ;
- absence totale de `payload_raw` et de contenu fournisseur brut.

Valider avec la phrase exacte affichée, télécharger le fichier et revérifier SHA-256 et taille.
Cette qualification porte sur la sélection locale déjà persistée. Elle n'atteste aucune nouvelle
réponse SofaScore et ne doit pas être présentée comme telle.

## 8. Contrôles HTTP et sécurité

Pour chaque page, aperçu, erreur et téléchargement, vérifier les en-têtes :

```text
Cache-Control: no-store, no-cache, must-revalidate, max-age=0
Pragma: no-cache
Expires: 0
X-Robots-Tag: noindex, nofollow, noarchive
```

Vérifier aussi :

- un jeton déjà consommé ne peut pas être rejoué ;
- une phrase différente d'un seul caractère est refusée en `400` ;
- un motif vide, supérieur à 500 caractères ou contenant un contrôle est refusé en `400` ;
- un export/événement inconnu répond `404` ;
- un candidat concurrent ou une source devenue courante répond `409` ;
- un schéma, hash, contenu sensible ou fichier altéré répond `422` ;
- une indisponibilité disque ou PostgreSQL répond `503` ;
- l'aperçu HTML affiche les caractères du JSON comme texte et n'exécute aucun balisage injecté.

Pour un contrôle technique du stockage, vérifier qu'une publication réussie ne laisse que le nom
généré : J7 synchronise un temporaire dans la racine, crée atomiquement un lien physique
create-new vers le nom final sur le même système de fichiers, puis retire le nom temporaire. Il ne
s'appuie pas sur `ATOMIC_MOVE`, n'écrase jamais une destination et doit répondre `503` si le système
de fichiers ne permet pas cette publication sûre.

Avant la conclusion, rechercher dans chacun des fichiers téléchargés les termes et structures liés
au brut, secrets et sessions. Une occurrence métier ambiguë doit être examinée ; toute donnée
interdite réelle produit un échec de recette, pas une dérogation.

## 9. Preuve minimisée à consigner

Le rapport de qualification doit noter sans recopier de JSON :

```text
J7_SYNTHETIC_REJECTION=PASS|FAIL
J7_SYNTHETIC_VALIDATION=PASS|FAIL
J7_SYNTHETIC_DOWNLOAD_SHA256_MATCH=PASS|FAIL
J7_PROVIDER_EVENT_ID=16691018
J7_PROVIDER_LOCAL_SELECTION=PASS|FAIL
J7_PROVIDER_WARNINGS_REVIEW=PASS|FAIL
J7_PROVIDER_DOWNLOAD_SHA256_MATCH=PASS|FAIL
J7_SENSITIVE_CONTENT_REVIEW=PASS|FAIL
J7_J3_LOCKED=PASS|FAIL
J7_J4_LOCKED=PASS|FAIL
J7_J5_LOCKED=PASS|FAIL
J7_PROVIDER_CALLS_DURING_QUALIFICATION=0
J7_PRIMARY_DATABASE_PURGE=NO
J7_HUMAN_QUALIFICATION=PASS|FAIL
```

Conserver seulement les UUID, statuts, tailles, hashes, codes d'avertissement et résultats de
contrôle. Ne jamais versionner le JSON exporté, une capture contenant du contenu sensible, `.env`
ou un motif de rejet intégral.

## 10. Arrêt et porte de clôture

Arrêter l'application avec `Ctrl+C`, puis :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1
```

Vérifier l'absence de listener sur `127.0.0.1:8087` et la conservation des verrous J3/J4/J5. Ne
pas supprimer les lignes J7 ni exécuter la rétention J6.

Le Work Order peut passer à `VALIDATED` et rejoindre `completed` seulement lorsque les deux suites
Maven, la recette humaine complète, la revue des secrets/diff, le push et l'état de PR
`CLEAN/MERGEABLE` sont tous prouvés. La seule implémentation technique, même verte, s'arrête à
`READY_FOR_HUMAN_VALIDATION`.
