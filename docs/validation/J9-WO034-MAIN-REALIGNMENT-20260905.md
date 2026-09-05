# WO-034 — Réalignement local sur main, 2026-09-05

Autorisation propriétaire : « WO-032, WO-033 et WO-034 sont à réaligner avec main ».
HEAD antérieur : `1b571a687e420d27dcbdca86e0f8bf64eb38663a`. Main distant relu : `dfe34b3ab6881823a8e8601764cb99a63727810b`.

Merge local sans rebase, squash ou suppression de commit. Conflits de synthèses résolus en conservant les deux historiques ; pour WO-034, union des règles LF explicites .gitattributes de WO-034 et WO-046. Aucun script, template, fixture ou preuve qualifiée propre au WO n'est changé par la résolution. Le commit de merge conserve les deux parents exacts.

Les états de README/CHANGELOG et livrables historiques restent datés. Main Lab intègre WO-046 R2 et WO-051 ; INT-001 est fusionné dans Betting Project (7f1f3aab430e046a1f5b18321bead22641f3a11f). ADR-SS-003 v0.2 régit le transfert local : NOT_EVIDENCED est un audit non bloquant à lui seul en V2 ; EVIDENCED_INCOMPATIBLE reste bloquant. Aucun droit fournisseur ni nouveau POST n'en découle.

Réaligné ne signifie ni publié ni fusionné vers main. WO-032/033 restent validés localement ; WO-034 reste actif, sans préflight privé ou renderer exécuté. Aucune nouvelle permission ni qualification fonctionnelle des anciens scripts n'est revendiquée par ce merge.

Contrôles de réalignement : absence de marqueur de conflit, UTF-8, diff --check, invariance des ajouts qualifiés hors README/CHANGELOG/.gitattributes. Les tests Java communs sont qualifiés depuis le worktree WO-052 avec sources/pom identiques à main ; intégration et suites natives J6/ChildJvm omises explicitement. Résultat de cette vérification commune référencé dans le suivi de clôture WO-052, pas un nouveau test du renderer WO-034.
