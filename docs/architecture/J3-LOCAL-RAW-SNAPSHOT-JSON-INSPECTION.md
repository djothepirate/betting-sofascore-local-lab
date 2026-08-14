# J3 — Inspection JSON locale des snapshots bruts

## 1. Objet

Cette unité ajoute une lecture humaine, locale et explicite des snapshots bruts déjà conservés
dans PostgreSQL. Elle ne collecte aucune donnée, ne déclenche aucun transport, ne modifie aucune
ligne et ne remplace pas la preuve minimisée de qualification.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Parcours opérateur

Le tableau de bord charge au plus 50 descriptions de snapshots récents. Cette première requête ne
sélectionne jamais `payload_raw`. Pour afficher un document, l’opérateur choisit une ligne et
soumet le formulaire local protégé par le jeton de session à usage unique.

Le serveur relit alors le seul identifiant demandé, vérifie le contenu et renvoie une page
distincte. Il n’existe ni chargement automatique, ni navigation séquentielle, ni téléchargement du
brut, ni endpoint JSON exposant directement les octets persistés.

```text
DASHBOARD_METADATA_QUERY
  -> EXPLICIT_LOCAL_POST(snapshotId, one-use token)
  -> EXACT_LOCAL_ROW_READ
  -> SIZE_AND_SHA256_CHECK
  -> SENSITIVE_CONTENT_SCAN
  -> STRICT_JSON_PARSE
  -> IN_MEMORY_PRETTY_PRINT
  -> HTML_ESCAPED_NO_STORE_VIEW
```

## 3. Sélection PostgreSQL

L’adaptateur en lecture seule accepte uniquement une ligne qui respecte toutes les conditions
suivantes :

```text
provider=SOFASCORE
acquisition_mode=DIRECT_LOCAL_ENDPOINT
payload_raw=NOT_NULL
payload_size_bytes=NOT_NULL
payload_sha256=NOT_NULL
received_at=NOT_NULL
http_status=NOT_NULL
content_type=NOT_NULL
parser_version=NOT_NULL
```

La liste contient uniquement l’identifiant, la clé de requête canonique, les métadonnées de
réception, la taille, l’empreinte, le parseur et la classification historique. La lecture du brut
est une seconde requête filtrée par l’identifiant exact. Les transactions de ces deux opérations
sont marquées `readOnly=true`.

## 4. Contrôles avant rendu

L’affichage est bloqué si l’une des conditions suivantes est rencontrée :

- identifiant absent, négatif ou inconnu ;
- base PostgreSQL locale indisponible ;
- taille réelle différente de `payload_size_bytes` ;
- SHA-256 recalculé différent de `payload_sha256` ;
- payload dépassant la limite brute existante de 5 Mio ;
- détection d’un cookie, jeton, secret, identifiant de session, JWT ou bloc de clé privée ;
- JSON invalide, champ dupliqué ou contenu ajouté après la valeur racine.

Le formatage est effectué en mémoire après ces contrôles. Le payload d’origine, sa classification
et le checkpoint de cache restent inchangés. La page Thymeleaf utilise un rendu texte échappé et
porte les en-têtes :

```text
Cache-Control: no-store
Pragma: no-cache
Expires: 0
X-Robots-Tag: noindex, nofollow, noarchive
```

## 5. Frontières explicites

Cette fonctionnalité ne fournit pas :

- de requête HTTP fournisseur ;
- de réutilisation de cookie, jeton, compte ou session ;
- de modification, suppression ou reclassement de snapshot ;
- de contournement du cache J3 ;
- de normalisation vers `payload_jsonb` ;
- de téléchargement ou export du payload brut ;
- de recherche plein texte dans le contenu ;
- de polling, rafraîchissement automatique ou planification.

La preuve minimisée reste le seul artefact destiné à être téléchargé pour la qualification. Un
payload brut affiché ne doit pas être copié dans Git, un rapport, un ticket ou un journal.

## 6. Codes sûrs

La vue peut exposer uniquement les codes bornés suivants, jamais une exception JDBC ou un extrait
de payload :

```text
INVALID_SELECTION
SNAPSHOT_NOT_FOUND
LOCAL_DATABASE_UNAVAILABLE
PAYLOAD_INTEGRITY_FAILURE
SENSITIVE_CONTENT_BLOCKED
INVALID_JSON
```

## 7. Validation attendue

Les tests standards vérifient le bornage du catalogue, l’absence de lecture brute pendant sa
construction, l’intégrité taille/SHA-256, le blocage sensible, le JSON strict, l’échappement HTML
et les en-têtes sans cache. La suite PostgreSQL vérifie la relecture exacte des octets et l’absence
de mutation du nombre de snapshots.

Toutes ces validations sont locales et hors ligne fournisseur.
