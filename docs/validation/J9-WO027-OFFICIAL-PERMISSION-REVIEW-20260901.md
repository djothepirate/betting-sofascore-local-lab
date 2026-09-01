# J9 / WO-027 — Revue factuelle des sources officielles et porte de permission

## 1. Identité

```text
REVIEW_ID=J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901
REVIEWED_AT_UTC=2026-09-01T08:03:34.7485241Z
REVIEWED_AT_EUROPE_PARIS=2026-09-01T10:03:34.7485241+02:00
WORK_ORDER=WO-SS-20260901-027-optional-local-push-implementation
LEGAL_ADVICE=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
OFFICIAL_PERMISSION_GATE_SATISFIED=NO
```

Cette revue décrit uniquement ce que les sources officielles accessibles permettent d'établir.
Elle ne constitue pas un avis juridique et ne transforme pas une décision propriétaire interne en
droit d'usage.

## 2. Sources consultées

| Source officielle | Consultation | Accessibilité | Constat factuel borné |
|---|---|---|---|
| <https://www.sofascore.com/terms-and-conditions> | `2026-09-01T08:03:34.7485241Z` | `ACCESSIBLE` | Les conditions indiquent un usage personnel/non commercial et encadrent l'extraction du contenu de base de données, les requêtes automatisées, l'agrégation et le scraping par une exigence de consentement explicite ou par les limites de la loi applicable. Dernière mise à jour affichée : 18 septembre 2024. |
| <https://api.sofascore.com/api/docs/external> | `2026-09-01T08:03:34.7485241Z` | `ACCESSIBLE_DOCUMENTATION_SHELL` | Une documentation technique officielle d'API externe est publiquement accessible. Aucune licence, permission applicable au présent laboratoire, condition d'acquisition/transfert ou limite contractuelle exploitable n'a été extraite de la page lors de cette revue. L'existence de la documentation n'est pas traitée comme un consentement. |
| <https://corporate.sofascore.com/contact> | `2026-09-01T08:03:34.7485241Z` | `ACCESSIBLE` | La page officielle propose notamment des catégories de contact `Product` / `API` et `Partnerships`. Un canal de contact n'est pas une permission obtenue. |

## 3. Qualification déterministe

Les sources établissent des restrictions et un chemin possible pour demander une coopération, mais
aucun accord, licence ou consentement explicite applicable à l'acquisition, la normalisation, la
conservation puis la transmission des données de ce laboratoire n'est versionné.

```text
EXPLICIT_APPLICABLE_PERMISSION_FOUND=NO
EXPLICIT_APPLICABLE_LICENSE_FOUND=NO
EXPLICIT_CONSENT_FOUND=NO
PUBLIC_API_DOCUMENTATION_FOUND=YES
PUBLIC_API_DOCUMENTATION_EQUALS_PERMISSION=NO
OFFICIAL_RESTRICTIONS_FOUND=YES
DETERMINISTIC_RESULT=NOT_EVIDENCED
```

`EVIDENCED_COMPATIBLE` n'est pas attribuable sans texte ou accord positif applicable.
`EVIDENCED_INCOMPATIBLE` n'est pas attribué ici comme conclusion juridique générale : les sources
prévoient notamment la possibilité d'un consentement explicite et des limites issues de la loi,
mais aucune de ces bases n'est établie pour le projet.

## 4. Effet sur WO-027

```text
OFFLINE_CONTRACT_SPECIFICATION=AUTHORIZED
LOCAL_FAIL_CLOSED_SCAFFOLD=AUTHORIZED
SYNTHETIC_LOOPBACK_QUALIFICATION=AUTHORIZED
OPTION_A_REAL_RUNTIME_ACTIVATION=BLOCKED
BETTING_PROJECT_REAL_RECEIVER=SEPARATE_WORK_ORDER_REQUIRED
REAL_DELIVERY=NOT_AUTHORIZED
PROVIDER_NETWORK=NOT_AUTHORIZED
VPS_DEPLOYMENT=NOT_AUTHORIZED
PRODUCTION=NOT_AUTHORIZED
```

Le code produit sous WO-027 doit donc échouer avant toute ouverture de socket lorsque la permission
est `NOT_EVIDENCED`, rester désactivé par défaut et n'utiliser qu'un receiver synthétique loopback
dans ses tests dédiés. Une future activation exige une nouvelle preuve versionnée et un réexamen
explicite d'ADR-SS-003.
