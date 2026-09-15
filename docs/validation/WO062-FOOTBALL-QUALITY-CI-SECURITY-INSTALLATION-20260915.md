# WO-062 — Validation humaine et installation personnelle football / CI

- **Date :** 15 septembre 2026 ; installation vérifiée à 20:33 UTC.
- **Résultat :** `ss-football-quality` et `ss-ci-security` humainement validés et installés dans le compte personnel de Geoff.
- **Version conservée :** `0.1.0-candidate.1` pour chacun ; quatre fichiers identiques à ceux acceptés.
- **Statut :** `OWNER_VALIDATED_PERSONALLY_INSTALLED_AND_VERIFIED`.
- **Périmètre :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Décision et identité

Le propriétaire a déclaré :

> J'ai pris connaissance du contenu, du périmètre et des limites documentées de ces deux candidats et j'accepte qu'ils deviennent les versions humaines validées pouvant être installées dans mon environnement personnel.

Cette décision est consignée séparément pour [football](../skills/evaluations/WO-062/ss-football-quality/human-validation.json)
et [CI](../skills/evaluations/WO-062/ss-ci-security/human-validation.json), avec les quatre empreintes approuvées.
Elle couvre leur validation humaine et leur installation personnelle. Le suffixe `candidate.1`
reste l'identité des fichiers qualifiés ; leur état humainement validé est porté par ces décisions
et les qualifications courantes. Aucun changement de contenu ni de version n'a été nécessaire.

La [recette précédente](WO062-FOOTBALL-QUALITY-CI-SECURITY-CANDIDATES-20260915.md) reste limitée
aux huit cas figés par skill. Les seize réponses et revues sont conservées. Les limites sur
le modèle observé, le chargement instrumenté, les catalogues de sélection séparés et la correction
d'encodage des métadonnées football restent applicables. La présente installation ne produit
aucun essai comportemental supplémentaire.

## Paquet et copies personnelles

Le [manifeste approuvé](../skills/evaluations/WO-062/football-quality-ci-security/installation-manifest.json)
déclare exactement les deux noms et quatre chemins du paquet explicite `FootballQualityCiSecurity`.
Le défaut `Lot1` et le paquet `ProviderBenchmark` restent disponibles avec leurs manifestes historiques.
L'installateur contrôle les approbations booléennes, la version, l'allowlist, les tailles et SHA-256,
puis tous les conflits de destination avant copie. Il conserve les fichiers identiques et refuse
les variantes, fichiers supplémentaires et chemins liés selon ses gardes existants.

| Skill | Destination personnelle | Fichiers | Résultat |
|---|---|---:|---|
| ss-football-quality | `C:\Users\geoff\.agents\skills\ss-football-quality` | 2 | Taille et SHA-256 identiques au manifeste. |
| ss-ci-security | `C:\Users\geoff\.agents\skills\ss-ci-security` | 2 | Taille et SHA-256 identiques au manifeste. |

Les [résultats d'installation](../skills/evaluations/WO-062/football-quality-ci-security/installation/result.json)
et les journaux [copie](../skills/evaluations/WO-062/football-quality-ci-security/installation/install.log),
[vérification](../skills/evaluations/WO-062/football-quality-ci-security/installation/verify.log) et
[seconde installation](../skills/evaluations/WO-062/football-quality-ci-security/installation/idempotence.log)
établissent quatre fichiers copiés, puis zéro réécriture ; les trois codes de sortie sont nuls.
Les douze fichiers personnels des six skills précédents ont conservé contenu et date de modification.

La [découverte locale](../skills/evaluations/WO-062/football-quality-ci-security/installation/discovery.json)
par `codex-cli 0.154.0-alpha.6.2` retrouve huit skills personnels, une entrée par nom et les chemins
exacts des deux nouveaux. Cette reconstruction du catalogue n'appelle aucun modèle et ne démontre
pas une sélection implicite commune des huit skills. Les qualifications antérieures restent la
preuve comportementale disponible.

## Tests de l'installateur et revue

Les suites ont été exécutées sous Windows PowerShell 5.1, dans des destinations isolées avec espaces :

| Paquet | Cas réussis | Journal |
|---|---:|---|
| Lot1 | 7 | [Résultats lot 1](../skills/evaluations/WO-062/football-quality-ci-security/installation/tests-lot1.txt) |
| ProviderBenchmark | 11 | [Résultats PB](../skills/evaluations/WO-062/football-quality-ci-security/installation/tests-provider.txt) |
| FootballQualityCiSecurity | 14 | [Résultats FQ/CS](../skills/evaluations/WO-062/football-quality-ci-security/installation/tests-b.txt) |
| **Total** | **32** | **Aucun échec.** |

Commandes exécutées depuis le worktree WO-062 :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package Lot1 -TestRoot '.tmp/b-installation/lot1 cases'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package ProviderBenchmark -TestRoot '.tmp/b-installation/provider cases'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package FootballQualityCiSecurity -TestRoot '.tmp/b-installation/b cases'

powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package FootballQualityCiSecurity -Destination 'C:\Users\geoff\.agents\skills'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package FootballQualityCiSecurity -Destination 'C:\Users\geoff\.agents\skills' -VerifyOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package FootballQualityCiSecurity -Destination 'C:\Users\geoff\.agents\skills'
```

Les cas couvrent présence complète, idempotence, vérification sans écriture, coexistence avec les
douze fichiers précédents, installation partielle, conflit sur la dernière entrée, casse,
sources altérées et refus d'un manifeste non approuvé ou d'une autre version.
La [revue indépendante avant installation](../skills/evaluations/WO-062/football-quality-ci-security/installation/review.md)
ne relève aucun défaut matériel. Elle a recalculé l'identité des candidats, 50 blobs sources,
10 fichiers de préparation, 161 artefacts d'évaluation et 102 pièces d'entrée figées.
Cette revue est statique ; les essais d'installation sont les exécutions distinctes ci-dessus.

## Incident initial et portée des preuves

Le [premier lancement](../skills/evaluations/WO-062/football-quality-ci-security/installation/install-failed-01.log)
a échoué avant toute copie : le processus Windows PowerShell démarré depuis Python héritait de
chemins de modules PowerShell 7 et ne trouvait pas `Get-FileHash`. Les deux destinations étaient
encore absentes. La première découverte, lancée après cet échec, ne trouvait donc aucun nouveau
skill et n'est pas comptée comme une réussite.

Le [diagnostic conservé](../skills/evaluations/WO-062/football-quality-ci-security/installation/initial-runtime-failure.json)
montre l'échec avec cet héritage et la réussite avec les chemins natifs de Windows PowerShell.
La correction omet `PSModulePath` uniquement dans l'environnement du processus enfant lancé
depuis Python, en traitant le nom de variable sans distinction de casse. Aucun paramètre
persistant n'a changé. Les trois exécutions personnelles réussies emploient ce contexte corrigé.

Les empreintes des qualifications présentes dans le préflight et la revue identifient leur état
approuvé **avant** installation. Les copies exactes [football](../skills/evaluations/WO-062/football-quality-ci-security/installation/ss-football-quality-qualification-before-installation.json)
et [CI](../skills/evaluations/WO-062/football-quality-ci-security/installation/ss-ci-security-qualification-before-installation.json)
permettent de les vérifier. Les qualifications courantes enregistrent ensuite l'installation achevée.
Le [manifeste des preuves](../skills/evaluations/WO-062/football-quality-ci-security/installation/artifact-manifest.json)
porte les octets publiés, journaux texte normalisés en UTF-8 LF, et s'exclut lui-même.

Les [contrôles de livraison](../skills/evaluations/WO-062/football-quality-ci-security/installation/checks.json)
couvrent empreintes, conservation des preuves antérieures, liens, UTF-8 et recherche locale des
neuf familles de secrets du contrôle du dépôt. Cette recherche locale n'est pas une CI distante.
Le code applicatif, les tests, la persistance et les workflows n'ont pas changé depuis la recette
B : sa vérification Windows terminée à 15:58:40 UTC reste la référence (2 440 tests déclarés,
2 435 exécutés, 5 ignorés, aucun échec ni erreur). Maven n'a pas été relancé pour cet incrément
d'installation ; les tests des scripts modifiés sont ceux des 32 cas ci-dessus.

## Utilisation et suite

Le [guide des skills](../skills/README.md) fournit les commandes et exemples d'utilisation.
L'installation personnelle suit la portée utilisateur de la [documentation officielle Codex](https://learn.chatgpt.com/docs/build-skills).
Si une tâche conserve un ancien catalogue, l'actualiser ou redémarrer Codex.

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md) reste actif. Les étapes A et B
ont terminé leur installation personnelle ; `ss-java-module`, puis `ss-windows-runtime` (C),
la consolidation D et la livraison Git restent à réaliser selon le workflow du WO.
