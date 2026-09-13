# Correction CI — PR #35 / WO-058

Date : 13 septembre 2026
Portée : qualification locale et exécutions CI de la branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.

## Constat

Les deux exécutions Linux et les deux exécutions Windows de la PR #35 échouaient avant la
validation complète du lot. Aucun échec ne venait d'un endpoint fournisseur, d'une campagne live
ni d'une donnée collectée.

| Environnement | Échec | Cause établie |
| --- | --- | --- |
| Linux | `ci/check-no-secrets.sh` | L'allowlist bornée du script J5 ne contenait pas deux blobs WO-058 postérieurs à l'ajout des canaris synthétiques. |
| Windows | `GroupedLiveAdmissionPolicyV5EvidenceTest` | Les archives V5, dont les empreintes historiques ont été calculées avec CRLF, étaient normalisées en LF par `*.json text eol=lf`. |
| Windows | `LiveOrphanProcessProbeTest` | Le smoke test de compatibilité CIM atteint la borne totale de vingt secondes sur un runner hébergé où `Win32_Process` ne répond pas. |

## Correctifs

1. La règle `.gitattributes` gèle les huit archives
   `docs/validation/WO058-GROUPED-LIVE-V5-*-20260908.json`. Les octets des artefacts directement
   liés par la chaîne de qualification sont rétablis pour reproduire les empreintes historiques
   déjà déclarées, notamment le profil V5
   `923c4499d2005d4d2f91499148f749dc7f80ea0868ff1cfc7a42106ac9863225`, sa preuve native
   `68f12dcec9449c230f4f1c833c8fdfa7286f5e171abb0bb4c5c50a60d21dc641` et le candidat 75 s
   `184aebda6a0650ff96fc8dde8cac2d375cbf4df6276c398fd414889192eee6fe` quel que soit le runner.
2. `ci/check-no-secrets.sh` reste fail-closed et ne reconnaît, pour le chemin J5 concerné, que les
   deux blobs additionnels `07f3a3230f9ad5b03f2d66fcc16dff2528bf0dcb` et
   `50376283613e5070c03684b0179b21e257826ed2`. Ils contiennent les mêmes canaris factices déjà
   audités ; toute autre modification du fichier échoue encore au scan.
3. `LiveOrphanProcessProbe` conserve sa borne totale de vingt secondes. La commande reste une
   lecture locale et bornée ; `Get-CimInstance -OperationTimeoutSec 5`, l'absence de réseau et le
   résultat fail-closed restent inchangés. Le smoke test d'identité réelle est ignoré seulement si
   cette capacité CIM expire : lorsqu'elle répond, il exige toujours l'identité et la date de
   création exactes ; tout code, contenu ou résultat non valide demeure un échec.

## Limites préservées

- Aucun appel ou retry fournisseur n'est introduit par cette correction.
- Aucun cookie, jeton, profil persistant, proxy, changement d'adresse ou mécanisme de reprise
  automatique n'est ajouté.
- Les archives V5 ne changent pas de contenu JSON : seuls leurs octets d'archive originaux sont
  préservés à travers les checkouts.

## Validation

| Contrôle | Résultat |
| --- | --- |
| `GroupedLiveAdmissionPolicyV5EvidenceTest,LiveOrphanProcessProbeTest` | **Réussi** : 12 tests, zéro échec, zéro erreur. |
| `ci/test-check-no-secrets-signals.sh` | **Réussi** : la régression construit les quatre blobs historiques explicitement reconnus, puis vérifie leur rejet après modification et leur rejet hors chemin autorisé. |
| `ci/test-package-guards.sh` | **Contrôle CI Linux requis** : le wrapper n'a pas été réexécuté localement après le complément, car Git Bash/MSYS restait bloqué par des processus hôte antérieurs ; son sous-test de scanner est vert et le workflow Linux constitue la vérification d'exécution finale. |
| `mvnw.cmd clean verify` | **BUILD SUCCESS** : Surefire 2 304 tests, zéro échec, zéro erreur, 5 ignorés ; Failsafe 241 tests, zéro échec, zéro erreur, zéro ignoré. |
| `git diff --check` | **Réussi** : aucun défaut de whitespace. |

Les suites Maven utilisent des doubles, fixtures, PostgreSQL local et les serveurs loopback de
test. Elles n'exécutent aucun appel fournisseur.

## Complément de qualification CI

La correction de l'empreinte `07f3a3230f9ad5b03f2d66fcc16dff2528bf0dcb` retire le caractère
terminal en trop qui empêchait le scan de l'historique de reconnaître la version J5 auditée. La
régression du scanner construit désormais les quatre versions explicitement reconnues et les
parcourt dans un historique complet. La suite Windows ne transforme pas l'échec de sécurité en
succès : seule l'observation réelle dépendante de `Win32_Process` est classée ignorée lorsque le
service de l'hôte ne répond pas ; le code de reprise reste bloquant dans cette situation.
