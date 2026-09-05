# J9 — Abandon propriétaire des pistes WO-032/033/034

## Décision actuelle

Le propriétaire déclare : « Les conditions de poursuite de WO-034 sont obsolètes. WO-032 WO-033 et WO-034 peuvent donc être abandonnés. »

Les trois lots sont ABANDONED_BY_OWNER. WO-032/033 conservent leurs validations historiques sans nouvelle livraison prévue. WO-034 quitte active pour completed à titre administratif, sans PASS de rendu ni clôture par fusion. La dernière décision supersède la précédente demande de poursuite jusqu'à fusion : aucune saisie complémentaire n'est requise pour ces lots abandonnés.

La [synthèse validée](J9-CONSOLIDATION-20260905.md), le [suivi de réalignement](J9-WO052-CLOSEOUT-REALIGNMENT-20260905.md) et les preuves de préflight restent inchangés et datés. Leurs prochaines étapes sont supersédées par ce suivi. Les commits de réalignement restent conservés ; ni branches, ni scripts, ni index, ni fixtures, ni rapports gelés ne sont supprimés. Aucun push, PR ou merge exécuté pour matérialiser l'abandon.

Périmètre exact : WO-SS-20260901-032-reference-index-refresh, WO-SS-20260901-033-j9-provider-permission-request-preparation, WO-SS-20260901-034-j9-permission-request-final-render. Le WO Maven du 20260902 portant aussi 032 et le WO-031 CI sont exclus.

## Rétention privée distincte

WO-034 prévoit historiquement un nettoyage lors d'un abandon. Aucun fichier privé n'est lu ou supprimé dans cette clôture administrative. Cette obligation n'est pas déclarée satisfaite : PRIVATE_CLEANUP=NOT_PERFORMED, PRIVATE_ARTIFACT_ABSENCE=NOT_ATTESTED. Une décision explicite de suppression et des contrôles de propriété/confinement sont nécessaires avant toute suppression. L'abandon ne vaut ni prolongation illimitée de rétention ni preuve d'absence.

## Contrôles et limites

Modifications documentaires seulement ; UTF-8, diff --check, état Git et liens courants contrôlés. Aucun nouveau build applicatif pour cette décision seule : les qualifications antérieures restent historiques, aucun nouveau PASS runtime/CI revendiqué. Aucun rendu, envoi SofaScore, POST J7, acquisition, base, certificat ou VPS exécuté. NOT_EVIDENCED et ADR-SS-003 v0.2 inchangés. J9, INT-001 et les transferts déjà qualifiés ne sont pas abandonnés.
