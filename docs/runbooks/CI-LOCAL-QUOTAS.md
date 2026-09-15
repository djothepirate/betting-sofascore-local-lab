# CI du Local Lab — tests, preuves et quotas

Décision propriétaire du 15 septembre 2026, [WO-061](../work_orders/active/WO-SS-20260915-061-ci-local-quotas.md)
et [ADR-SS-004](../../ADR-SS-004-integration-continue-et-distribution-locale-uniquement.md).

Le Lab est développé et utilisé sur Windows, depuis son checkout local. GitHub Desktop sert
aux opérations Git et Eclipse au développement. Java 25, Maven Wrapper et PostgreSQL dans
Docker Desktop restent le socle. Aucune distribution ou installation VPS n'est prévue.

## Déclenchements

| Forge et événement | Travail automatique |
| --- | --- |
| GitHub : PR de WO ou PR finale du train | Tests Windows, Linux/PostgreSQL, gardes Git/secrets/localité et preuves XML, tous bloquants |
| GitHub : demande manuelle sur branche admise | Même qualification complète ; aucun bundle |
| GitHub : push de branche ou tag | Aucun run de la CI générale ; une PR ouverte est qualifiée par son événement PR |
| GitLab : MR de promotion | Gardes de promotion, secrets et tests Linux/PostgreSQL bloquants |
| GitLab : tag de release | Tests et preuves obligatoires, puis distribution finale sous garde du tag protégé et des références exactes |
| GitLab : demande manuelle | Qualification obligatoire de la référence admise |
| GitLab : push de branche, scheduler, trigger ordinaire | Aucun pipeline automatique |

Les deux checks GitHub gardent leurs noms pour préserver les références de revue existantes.
Il n'y a aucun filtre par chemin qui pourrait masquer un test requis. Les runs remplacés par
une nouvelle révision sont annulables ; un run annulé n'est pas une qualification réussie.
Une modification de la branche cible d'une PR relance sa vérification.

## Ce qui reste bloquant

| Catégorie | Exemples | Échec ou indisponibilité |
| --- | --- | --- |
| Tests obligatoires | Maven Windows ; Maven Linux avec PostgreSQL/Testcontainers ; gardes et tests des lanceurs | Job en échec, fusion non qualifiée |
| Preuves des tests et contrôles | XML Surefire ; XML Failsafe et résumé d'intégration ; rapport Secret Detection GitLab | Absence ou rapport vide : validation refusée ; les XML sont aussi contrôlés pour intégrité et cohérence des résultats |
| Preuve supplémentaire exigée par un WO | Rapport de qualification, couverture exigée, preuve PostgreSQL | Obligatoire dans le parcours de qualification du WO ; jamais couverte par une tolérance documentaire |
| Distribution finale demandée | SBOM, provenance, SHA-256, tag/main/feature/release identiques | Distribution refusée si une preuve manque |
| Export réellement documentaire facultatif | Copie Javadoc HTML à la demande | Avertissement possible, sans changer le verdict des tests déjà réussis |

`java ci/VerifyTestReports.java standard` contrôle les rapports Windows ; le mode `integration`
contrôle aussi Failsafe. Ces gardes complètent le code de sortie Maven, sans le remplacer.
Les commandes Maven et les gardes ne portent ni `continue-on-error`, ni `allow_failure`.
Sur GitHub, l'upload XML est lui aussi obligatoire : un quota épuisé peut donc encore bloquer
la **conservation de la preuve**, mais aucun bundle intermédiaire ne peut plus causer ce blocage.
Sur GitLab, la présence et le contenu sont vérifiés avant publication des rapports JUnit ; la
revue doit également constater leur disponibilité effective dans le pipeline qualifié.

Le seul job tolérant est l'export Javadoc manuel. Il n'exécute aucune suite de tests et ne promet
aucune preuve de validation. Si un WO exige Javadoc comme preuve, ce job facultatif ne suffit
pas : l'export doit être contrôlé dans le parcours bloquant de ce WO.

## Conservation

- **Bundles intermédiaires : aucun archivage automatique**, sur aucune forge et aucune branche.
- **Rapports XML de tests : trois jours**, chemins précis ; aucun JAR, ZIP, dossier `target/`
  complet ni rapport texte doublonné.
- **Javadoc facultative : un jour**, uniquement après lancement manuel.
- **Distribution finale taguée GitLab : conservation distincte**, avec toutes ses preuves.
- **Cache Actions : aucun nouveau cache Maven** dans cette CI ; les anciens caches ne sont pas
  effacés implicitement par ce changement.

Avant expiration, reporter dans `docs/validation` la commande, le SHA, l'environnement, les
totaux et les liens de run nécessaires au WO. Si les XML doivent être conservés au-delà de la
revue, les conserver explicitement dans le dossier local de qualification du WO ou définir
une rétention adaptée avant le run. Un export facultatif ne remplace jamais ces preuves.

GitLab peut conserver les artefacts du dernier pipeline réussi malgré `expire_in` lorsque
**Keep artifacts from most recent successful jobs** est activé. Le réglage projet
`keep_latest_artifact=false` est nécessaire pour appliquer strictement les expirations des
preuves courtes. Les distributions taguées déclarées sans expiration restent distinctes.
Voir la [documentation GitLab](https://docs.gitlab.com/ci/jobs/job_artifacts/#keep-artifacts-from-most-recent-successful-jobs).

La suppression de l'upload futur ne supprime aucun ancien bundle. Une purge historique est
une opération séparée : inventorier identifiants, tailles et nature des fichiers, distinguer
bundles intermédiaires et preuves de validation, puis appliquer seulement la purge autorisée.
GitHub indique que le quota peut être recalculé avec retard ; ne pas présenter une suppression
comme une libération instantanée du quota. Voir la [facturation Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

## Vérification depuis Windows

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1
java ci/VerifyTestReports.java standard
.\mvnw.cmd -Pintegration-tests verify
java ci/VerifyTestReports.java integration
pwsh -NoProfile -File ci/test-distribution-launchers.ps1
pwsh -NoProfile -File scripts/wo056/Test-WO056WorkerArtifact.ps1
```

Le profil d'intégration demande le moteur Docker Desktop disponible. Les tests utilisent des
ressources jetables, pas la base opérateur. Aucun de ces contrôles ne lance un navigateur
fournisseur. Les gardes shell et leurs fixtures sont aussi exécutables dans WSL.

Les politiques héritées d'observation automatique JaCoCo/PMD/CPD/Dependency-Check/SAST et le
ratchet commun au Betting Project ne constituent plus une exigence générale du Local Lab.
Le script Dependency-Check reste disponible pour une analyse explicite ; son ancienne
configuration d'observation ne constitue pas une approbation de sécurité.
