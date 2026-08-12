# Persistance J3 des snapshots manuels bruts

## 1. Portée

Cette unité prépare la conservation locale de la preuve brute d’un futur appel manuel
`SCHEDULED_EVENTS`. Elle part du commit
`1a9ca0e753cedf1ac4730adea865bd7ea3369660` et reste entièrement hors ligne : elle n’ajoute ni
client HTTP, ni URI SofaScore, ni activation du connecteur.

Le contrat est volontairement indépendant du transport. Un transport futur devra construire un
`RawManualCallSnapshot` à partir des octets reçus, puis appeler le port
`RawManualCallSnapshotStore` avant toute normalisation.

## 2. Preuve conservée

Chaque snapshot contient :

- la famille logique et une clé de requête canonique ;
- les instants de demande et de réception ;
- le statut HTTP, le type de contenu et la latence ;
- les octets exacts, leur taille et leur SHA-256 ;
- la version du parseur, l’état de schéma et un éventuel code d’erreur borné ;
- la provenance fixe `DIRECT_LOCAL_ENDPOINT` et le fournisseur `SOFASCORE`.

`RawPayloadEvidence` effectue une copie défensive des octets. Sa représentation textuelle ne
contient que la taille et le hash, jamais le payload. La taille maximale est fixée à 5 Mio
(`5 × 1024 × 1024` octets).

## 3. Schéma append-only

La migration `V2__raw_manual_call_snapshots.sql` étend `provider_snapshot` sans modifier la
migration V1 :

| Colonne V2 | Type | Rôle |
|---|---|---|
| `acquisition_mode` | `varchar(32)` | provenance limitée à `DIRECT_LOCAL_ENDPOINT` |
| `payload_raw` | `bytea` | octets exacts reçus avant parsing |
| `payload_size_bytes` | `bigint` | longueur exacte et bornée du payload |

Une contrainte lie la présence du brut, sa taille réelle, sa limite de 5 Mio et la présence du
SHA-256. Les lignes historiques V1 sans `payload_raw` restent compatibles. La liste des états de
schéma accepte désormais aussi `UNEXPECTED_CONTENT`, en complément des états déjà prévus.

Le champ V1 `payload_jsonb` reste `NULL` lors de cette unité. Le remplir avec le JSON décodé ferait
perdre la distinction entre preuve brute et donnée normalisée et empêcherait la conservation fidèle
d’un contenu non JSON.

## 4. Déduplication

L’index unique partiel V1 est réutilisé avec la clé :

```text
(provider, logical_endpoint, request_key, payload_sha256)
```

L’adaptateur JDBC exécute un insert avec conflit ignoré, puis résout l’identifiant de la ligne. Le
résultat expose explicitement :

- `INSERTED` pour une nouvelle preuve ;
- `DEDUPLICATED` pour une preuve identique déjà présente.

Une réponse modifiée pour la même requête possède un hash différent et produit donc un nouveau
snapshot. La déduplication ne masque pas l’évolution du contenu.

## 5. Garde-fous

- la clé de requête doit commencer par la famille logique et ne peut contenir ni URI ni caractère de
  contrôle ;
- le statut HTTP est borné à `100..599` et l’ordre temporel est vérifié ;
- le type de contenu, la version de parseur et le code d’erreur sont bornés et filtrés ;
- les motifs de secret, d’en-tête d’autorisation, de cookie, de jeton et de clé privée sont rejetés
  avant l’écriture ;
- aucun payload brut n’est écrit dans les logs, la représentation des objets ou Git ;
- aucune donnée normalisée, aucun header et aucun incident de circuit ne sont persistés dans cette
  unité.

Le contrôle de motifs sensibles est une défense supplémentaire, pas une autorisation de fournir des
identifiants au laboratoire. Les cookies, comptes, sessions et jetons restent interdits.

## 6. Vérification

Les tests unitaires vérifient la copie exacte et défensive, le SHA-256, la limite de taille, le rejet
des motifs sensibles et les contraintes des métadonnées. Le profil PostgreSQL/Testcontainers
vérifie :

- l’application de Flyway jusqu’à V2 et le maintien de `network_enabled=false` ;
- le stockage octet pour octet dans `bytea` ;
- la taille, le hash et la provenance ;
- l’absence de valeur dans `payload_jsonb` ;
- la déduplication stable d’une preuve identique ;
- la conservation d’une réponse modifiée ;
- le rejet par PostgreSQL d’une taille incohérente.

Commandes de qualification :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
```

Ces suites n’effectuent aucun appel SofaScore. Le profil réel, `ConnectorGate`, le catalogue et les
actions réseau de l’interface restent bloqués.

## 7. Unité suivante réalisée

L’unité `feat: add guarded scheduled-events transport` fournit désormais un résultat brut compatible
avec ce contrat, exclusivement depuis un serveur simulé sur `127.0.0.1`. Elle ne raccorde pas encore
automatiquement le transport au magasin JDBC : la composition persistance–parsing et les incidents
restent des étapes distinctes. La confirmation dans l’interface et tout appel réel restent également
bloqués.
