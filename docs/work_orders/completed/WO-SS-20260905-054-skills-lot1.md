# WO-SS-20260905-054 — Livraison des skills validés du lot 1

- **Statut :** COMPLETED_OWNER_VALIDATED — contenu des cinq skills validé ; livraison Git vers `main` autorisée et suivie dans la PR.
- **Date :** 2026-09-05.
- **Autorité :** le propriétaire confirme que les skills sont validés sur les deux projets et autorise leur livraison intégrale sur les branches `main` des deux applications.
- **Branche :** `codex/ss-20260905-054-skills-lot1`, worktree neuf créé depuis `origin/main` actualisé, base `50043b9` (fusion PR #30).
- **Prédécesseur :** adaptation et installation personnelle SKL-002 ; [preuves historiques conservées](../../skills/evaluations/SKL-002/review.md).

## Objectif et périmètre

Versionner dans le Local Lab les cinq adaptations `ss-*` validées, leurs preuves et un installateur personnel reproductible. Conserver les dix fichiers de skills à l'identique. Rendre la livraison autonome par rapport au worktree Betting Project qui hébergeait initialement les candidats.

Le paquet canonique est `docs/skills/local-lab`. L'installation utilisateur dans `.agents/skills` permet l'usage depuis plusieurs worktrees sans copie homonyme dans le dépôt. L'installation utilisateur existante reste intacte ; les essais d'installation utilisent des destinations isolées.

Les statuts EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY restent applicables. Aucun code applicatif, migration, profil Maven, workflow CI ou ADR modifié. Aucune campagne fournisseur, base primaire, livraison J7 réelle ou dépendance du Betting Project principal au Lab.

## Critères et preuves

- [x] Dix fichiers de skills identiques au manifeste SKL-002 ; métadonnées et validation officielle conformes.
- [x] Preuves historiques conservées sans réécriture de leurs octets ou résultats ; références de tête portables.
- [x] Installateur avec préflight complet des sources/destinations, idempotence et refus des variantes locales.
- [x] Sept scénarios isolés : nominal, idempotent, vérification sans écriture, conflit local, variante de casse du fichier, fichier supplémentaire et source altérée. Variation de casse du préfixe absolu Windows vérifiée également ; correction P2 de la PR #31 qualifiée.
- [x] Vérification standard Windows prescrite exécutée : 1193 tests, zéro échec/erreur, cinq skips. Failsafe explicitement ignoré localement.
- [x] Guide, README, changelog, WO et [rapport de livraison](../../validation/WO054-SKILLS-DELIVERY-20260905.md) cohérents.

## Qualification et livraison

La validation propriétaire couvre le contenu des skills, et l'instruction actuelle autorise commit, push, PR et fusion vers `main` après les contrôles applicables. L'installateur fait l'objet de ses propres contre-épreuves et d'une revue indépendante. La CI existante doit qualifier le candidat publié : Windows et Linux/PostgreSQL restent des preuves distantes distinctes de la vérification locale.

Le classement `completed` constate l'acceptation du lot et la réalisation du paquet ; il ne prétend pas qu'une PR est déjà fusionnée ni que sa CI future est verte. L'état distant, le SHA courant et les checks doivent être vérifiés avant fusion. Aucune décision propriétaire supplémentaire n'est attendue pour cette livraison autorisée.

Les temps et limites des essais de skills restent ceux de SKL-002 : aucune nouvelle mesure de productivité générale et aucune qualification applicative inférée d'une simple installation de fichiers.
