# WO-SS-20260905-050 — Isolation owner-go des lanceurs sans livraison fournisseur

- Statut : `IMPLEMENTED_PENDING_OWNER_REVIEW`.
- Ouverture : 2026-09-05.
- Base : `facb4b51f23b09b07e46653ca8a40dac6f543f59` (PR #27).
- Branche : `codex/wo-050-non-provider-launcher-owner-go-isolation`.
- Worktree distinct : `.tmp/w50`, neuf, sans configuration privée copiée ni ouverture Eclipse.

## Autorité et limites

Le propriétaire autorise l'ouverture et la réalisation strictement hors ligne de WO-050,
avec soumission du correctif à sa validation avant mise à jour de la PR #27.
Le worktree source était propre et le numéro et la branche disponibles à l'ouverture.

```text
IMPLEMENTATION=AUTHORIZED_LAUNCHERS_TESTS_DOCUMENTATION_ONLY
QUALIFICATION=OFFLINE_SYNTHETIC_ONLY
APPLICATION_DATABASE_PLAYWRIGHT_RECEIVER_START=NO
PROVIDER_OR_RECEIVER_HTTP=NO
PRIMARY_DATABASE_TOUCH=NO
CERTIFICATE_STORE_ACCESS=NO
WO046_NEW_MANIFEST_OR_GO_OR_POST=NO
PR27_UPDATE_OR_PUSH=REQUIRES_OWNER_VALIDATION
PR27_MERGE=NO
WORK_ORDER_MOVE_TO_COMPLETED=REQUIRES_OWNER_VALIDATION
```

## Constat P2

Revue PR #27 : https://github.com/djothepirate/betting-sofascore-local-lab/pull/27#discussion_r3940237642.
Les six lanceurs Start-Local, Start-J3/J4/J5PlaywrightLocal, Export-J8Benchmark et
Invoke-J6Retention forcent le mode d'intégration DISABLED sans sauvegarder ni neutraliser
OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID et
OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256.
La validation Java exige pourtant une référence absente en mode désactivé : l'héritage
d'une seule de ces valeurs peut faire échouer un démarrage légitime.

## Correction et qualification prévues

- Ajouter les deux noms aux sauvegardes d'environnement existantes des six scripts,
  neutraliser leurs valeurs pendant le parcours, restaurer les valeurs initiales dans finally.
- Garder intacte la validation Java, le lanceur de livraison PROVIDER_DERIVED, les migrations,
  les contrats, les ADR, les PDF et les preuves gelées WO-046.
- Tester valeurs absentes, GO seul, hash seul, paire présente ; succès et échecs simulés ;
  restauration exacte de l'environnement parent. N'utiliser que des données synthétiques.
- Qualifier les sections originales d'isolation PowerShell avec commandes métier remplacées
  par des doublures, puis le binding/validation Spring sans contexte applicatif ni socket.
- Build Maven hors ligne borné au périmètre autorisé, contrôles UTF-8, secrets, diff et invariants.
  Aucun test natif J6, Testcontainers ou campagne réseau n'est autorisé par ce WO.
- Commits locaux distincts, rapport expurgé, revue propriétaire avant toute publication.

Les statuts EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY
restent inchangés. La permission officielle reste NOT_EVIDENCED, le go WO-046 consommé.

## Résultat local

- Six lanceurs corrigés, aucune modification Java applicative.
- Avant correctif : échec attendu `WO050_AMBIENT_GO_LEAK:Start-Local.ps1:1`.
- Après correctif : 72 scénarios PowerShell réussis (6 x 4 héritages x 3 issues),
  avec neutralisation pendant l'appel simulé et restauration exacte après succès/échec.
- 19 tests Java ciblés réussis, dont les 72 bindings Spring issus des observations PowerShell,
  les 7 tests de non-régression WO-049 et les 9 tests OptionalLocalPushProperties.
- `clean verify` hors ligne borné réussi : 1148 tests, zéro échec/erreur, 4 ignorés.
  Tests natifs J6, superviseur JVM/sockets et tests d'intégration explicitement exclus.
- Politique locale, convention de branche, UTF-8, diff et neuf règles de secrets : PASS.
- Première compilation sandbox interrompue par un défaut d'accès à une dépendance du cache ;
  relance hôte hors ligne réussie. Aucun téléchargement ni service métier lancé.
- Le rapport R2 WO-046 conserve son SHA-256 validé, aucune preuve gelée réécrite.

Implémentation : `87deb475007c853ed5f06752756183078f2e404f`.
[Rapport détaillé](../../validation/J9-WO050-NON-PROVIDER-LAUNCHER-OWNER-GO-ISOLATION-20260905.md),
SHA-256 `c6b1acd4f0b3866fe6db1b7fce6c68986c6007844a63a348c4f8dcacca5a7ccd`.
Résultat soumis : `PASS_OFFLINE_FAIL_CLOSED`.
WO-050 reste actif ; aucun push, aucune mise à jour/résolution GitHub, aucune fusion ni
nouvelle campagne. Le correctif ne sera publié qu'après validation propriétaire.
