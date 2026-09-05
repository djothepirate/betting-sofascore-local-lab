# WO-053 — correction de la revue de l'instrumentation

## Portée

Reprise autorisée par le propriétaire après revue des deux P2. Base de correction : c65a2d85b80d8f17f0bfa3cb3a5924732d3721ca. Le rapport diagnostique initial est conservé sans réécriture. Aucun correctif causal du défaut Windows original n'est revendiqué.

## Matrice de contre-épreuves synthétiques

| Entrée / situation | Résultat attendu | Preuve |
|---|---|---|
| Ligne exactement allowlistée | Restitution immédiate | DIAGNOSTIC_INTERRUPTED_RELAY |
| Ligne avec préfixe, suffixe, valeur inconnue ou exception | Aucune restitution | DIAGNOSTIC_NATIVE_ALLOWLIST |
| Producteur interrompu après un marqueur | Marqueur déjà observé conservé | DIAGNOSTIC_INTERRUPTED_RELAY |
| Sortie C# levant IOException pendant le finally | Suite du nettoyage atteinte et erreur primaire préservée | DIAGNOSTIC_OUTPUT_FAILURE_ISOLATION |
| Assertion échouée contenant un message synthétique sensible | Classe stable, message non divulgué | DIAGNOSTIC_FAILURE_SANITIZATION, 17 portes |

Le test compile la méthode C# effectivement extraite du lanceur, sans exécuter les API Win32. Il injecte une panne d'écrivain ; ce n'est pas une reproduction de la panne CI. Le relais utilise begin/process et une allowlist inchangée, sans collecte globale du stdout. Le code natif de sortie reste lu immédiatement après le pipeline PowerShell. La méthode d'observation intercepte les erreurs d'écriture IO, de disposal et d'état invalide ; elle ne supprime pas les erreurs des opérations de nettoyage.

Les contre-épreuves sont appelées par la qualification J6 existante avant la compilation du lanceur. Aucune deadline augmentée, aucune exclusion ou retry ajouté. Java applicatif, pipeline runtime, persistance, migrations et contrats J7 inchangés. Aucun accès primaire, fournisseur ou receiver réel.

## Vérifications

- Test-J6CtrlBreakDiagnosticContract.ps1 : PASS local, cinq marqueurs de contrat dont les deux nouvelles contre-épreuves.
- Première tentative Maven : redirection impossible avant lancement (répertoire de journal absent), puis création explicite du répertoire ignoré.
- Maven en sandbox : échec de résolution du parent Spring Boot, accès au dépôt Maven refusé avant compilation ; ce n'est pas un résultat de tests.
- Vérification hôte `mvnw.cmd clean verify`, sans exclusion ajoutée ni `skipITs` : BUILD SUCCESS, code 0, 06:23, fin 2026-09-05T15:43:34Z (17:43:34 Europe/Paris). XML Surefire : 1193 tests, 0 échec, 0 erreur, 5 skips conditionnels existants ; XML Failsafe : 106 tests, 0 échec, 0 erreur, 0 skip. PostgreSQL/Testcontainers isolé ; aucune base primaire utilisée.
- Qualification J6 dans ce build : 4 tests, 0 échec, 0 erreur, 1 skip Docker opt-in, 78.34 s ; deadline 180 s inchangée. Aucun profil supplémentaire nécessaire : Failsafe est déjà exécuté par la commande complète, la persistance n'est pas modifiée.
- SHA-256 du journal hôte ignoré : `5831ded631303fa0ea861d6f74bdd56aec79322b9307d496737ae063207d18eb`.
- Seul ajustement du script après son exécution J6 : indentation C# des appels WriteDiagnostic ; méthode et logique inchangées. Contrat ciblé réexécuté ensuite : PASS. SHA-256 final du script : `8248d03251a0d3402ccdaf3523f58a5167fef708d5f90c1a0eb4ac8c5245e992` ; test de contrat : `65f460877b24b43da76f6ddce66022ee1753cfe3dc5ea5a8cd3b9064cd7b24ed`.
- Diff contrôlé : UTF-8 strict, absence de NUL, zéro correspondance aux dix familles de motifs de secrets vérifiées ; lecture du diff sans payload réel ni donnée privée. `git diff --check` : PASS. Le scanner CI historique n'est pas présenté comme rejoué sur ce diff non publié.
- Adresse application `127.0.0.1`, flags bloquants, Java applicatif, workflows, runtime primaire et migrations inchangés.

## Livraison

Correction locale soumise à revue ; aucune intégration à PR #29, publication ou fusion effectuée par cette reprise. Les anciens succès Windows CI ne qualifient pas ce nouveau diff. Cause du défaut initial : NOT_ESTABLISHED. Statuts EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY préservés.
