# WO-052 — reprise documentaire après publication des abandons

## Autorité et effet

Le propriétaire demande de rendre effectif l'abandon de WO-032/033/034, pousser leurs branches et reprendre WO-052 avec ces informations. Les abandons sont administratifs et définitifs pour ces pistes ; les qualifications antérieures restent historiques. WO-052 conserve sa validation et sa clôture, complétées par ce suivi de publication. Aucun nouveau lot fonctionnel n'est ouvert.

## État Git vérifié

Destination : https://github.com/djothepirate/betting-sofascore-local-lab.
Push atomique non forcé des trois références, puis lecture distante par git ls-remote réussie le 2026-09-05 :

| WO | Branche publiée | HEAD distant exact | Statut |
|---|---|---|---|
| WO-SS-20260901-032-reference-index-refresh | codex/j9-wo032-reference-index-refresh | be2b3437803a9ca61eae8e2a0f269eb669c51053 | ABANDONED_BY_OWNER |
| WO-SS-20260901-033-j9-provider-permission-request-preparation | codex/j9-wo033-provider-permission-request | 0f13bdd22dc45146c70f06bf8e8fb0e8fc303c00 | ABANDONED_BY_OWNER |
| WO-SS-20260901-034-j9-permission-request-final-render | codex/j9-wo034-permission-final-render | 3b9eeda46af51c97e82b0b1489b37ab7e0c8b349 | ABANDONED_BY_OWNER |

Les trois WO sont dans completed sur leurs branches respectives. Aucun import de leurs scripts, index ou brouillons dans WO-052 : cette consolidation référence leur sort exact au lieu de réintroduire les travaux abandonnés. Les commits qualifiés sont préservés sans squash/rebase ni suppression de branche.

Main distant demeure dfe34b3ab6881823a8e8601764cb99a63727810b. Les branches sont publiées, pas fusionnées vers main. Aucune PR créée ni fusion effectuée pour cette demande. La publication de WO-052 porte ce suivi et ses synthèses ; son SHA final est communiqué après commit et contrôle distant, sans autoréférence.

## Acquis, limites et portes actuelles

- WO-019 : ABANDONED_BY_OWNER ; STOPPED et 20 tentatives restent sa preuve historique, sans nouveau go.
- WO-032/033 : abandon enregistré ; index et brouillons conservés seulement comme historique, aucune suite attendue.
- WO-034 : abandon enregistré et nettoyage privé achevé ; six fichiers et deux répertoires supprimés, seule racine exacte attestée absente. Preuve au commit 3b9eeda, fichier docs/validation/J9-WO034-PRIVATE-CLEANUP-20260905.md, SHA-256 5310cd3fe798c9cf28b10bd2ab4adb869de906cd4cd7a6cad1034486ed97b0f8.
- J9, INT-001 et le transfert local réel WO-046 R2 déjà qualifié ne sont pas abandonnés.
- NOT_EVIDENCED demeure un statut d'audit selon ADR-SS-003 v0.2 pour le transfert local V2 ; aucune nouvelle permission fournisseur n'est établie.
- Aucun envoi de demande, nouveau POST, acquisition fournisseur, accès receiver distant, déploiement VPS ou production autorisé par ces abandons ou pushes.

## Vérifications et lecture des preuves

Les mentions « non publié », « WO-034 actif » et « nettoyage non effectué » des preuves datées antérieures sont historiques, supersédées par ce suivi et la décision d'abandon. La synthèse validée et les rapports gelés ne sont pas réécrits.

Diff documentaire courant, UTF-8 strict sans NUL et git diff --check contrôlés ; pas de changement runtime, contrat ou migration. Aucun nouveau build ni PASS CI revendiqué pour ce suivi administratif. Les branches historiques conservent leur nom : publication pour audit ne vaut pas conformité à une future règle de nommage de PR.
