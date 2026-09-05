# WO-053 — preuve diagnostique CTRL_BREAK

## Invariants

Base bbce3c5e643c4cc0840e2654a30142ea9ba136f4, outillage synthétique uniquement. Délais globaux 180 s, readiness 10 s, attente native 10 s et nettoyage 3/5 s inchangés. Aucun retry ni exclusion supplémentaire. J6-NativeBinaryPipeline.psm1, Backup-Restore-J6.ps1, Java, pom, workflows, contrats et migrations byte-identiques à la base.

## Contrat expurgé

Compilation : marqueurs START/PASS et code numérique de sortie, trois portes COMPILATION_GATE_1..3 (compilateur présent, sortie zéro, exécutable présent).

Lanceur natif : phases PROCESS_CREATE, READINESS_WAIT, SIGNAL_SEND, CHILD_WAIT, CLEANUP ; failures ARGUMENT_COUNT, PROCESS_CREATE, HANDLER_INSTALL, EARLY_EXIT, READINESS_TIMEOUT, SIGNAL_SEND, CHILD_TIMEOUT, CHILD_WAIT, EXIT_CODE_READ. Seules les lignes exactes du vocabulaire fermé sont relayées ; chemins, stderr et texte libre ne le sont pas. NATIVE_CLEANUP PASS/FAIL est séparé du résultat primaire ; FALLBACK_CLEANUP COMPLETED signifie retour sans exception du teardown existant, pas preuve autonome de zéro processus.

Portes CHILD_GATE_01..14 :

| Porte | Assertion |
|---|---|
| 01 | sortie lanceur zéro |
| 02 | ready présent |
| 03 | résultat présent |
| 04 | identité du processus de test capturée |
| 05 | preuve de sortie par handle exact présente |
| 06/07 | identité producteur/consommateur présente |
| 08 | preuve de sortie exacte qualifiée |
| 09 | aucune identité exacte résiduelle avant fallback |
| 10 | token annulé |
| 11 | résultat CANCELLED |
| 12 | nettoyage pipeline PASS |
| 13/14 | wrappers producteur/consommateur vivants au début du nettoyage supervisé |

Le dernier stade est conservé pour les exceptions inattendues. Les données privées et le contrat J7 ne sont ni lus ni modifiés. Les contre-épreuves du parseur PostgreSQL restent synthétiques.

## Exécutions locales

Commande de forme CI : powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1. Variables CI=true, TZ=UTC, MAVEN_ARGS=-ntp, flags fournisseur/intégration désactivés et java.io.tmpdir fixé à un chemin canonique local. Le wrapper existant appelle mvnw.cmd -DskipITs clean verify comme le job Windows ; aucune exclusion ajoutée. Pas de qualification primaire ou -WithDocker.

- R1 : échec propre à l'instrumentation, pas reproduction de l'incident original. Le vocabulaire HARNESS violait la garde historique interdisant la sous-chaîne HAR. La garde a détecté cette collision ; elle reste inchangée.
- R2 : vocabulaire CHILD remplaçant HARNESS dans les nouveaux marqueurs seulement. BUILD SUCCESS, 02:17, fin 2026-09-05T15:05:30Z ; XML Surefire 1193 tests, zéro échec/erreur, cinq skips natifs ; intégration omise par la commande Windows existante.
- SHA-256 canonique R1 : 2131b5e415bcd8384941acb01a9687821c18ae9d80fff7c78b95b2caeaaad076.
- SHA-256 R2 : 2f56c3c3b44f6d245845306104a0fe4ac5065d22036128af16a96b0c9f4f0c41.
- Script instrumenté testé : SHA-256 73412a3ddeb281e7e2fbeb76569392a75b612e4d92e1faefc6f4749072b0865b.
- Test-J6CtrlBreakDiagnosticContract.ps1 : PASS, 17 portes, filtrage nominal/hostile et non-divulgation du message d'assertion vérifiés.

Journaux bruts ignorés hors Git. Les totaux ne prouvent pas une reproduction Windows Server 2022 : le poste utilise Java Oracle 25.0.4 et un Windows client, le runner Temurin et windows-2022. Aucun timeout relevé pour obtenir ce succès.

## Reproduction distante

Le workflow CI existant sera déclenché sur la branche diagnostique pour reproduire sur windows-2022 avec Temurin 25 et la même chaîne de lancement que le run fautif. Son job Linux associé effectue ses tests PostgreSQL isolés habituels, sans accès primaire. Aucune modification de PR #29 ni fusion. Résultat distant à consigner après observation, aucune cause précise du run initial encore prouvée.
