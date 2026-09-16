# WO-062 — réparation opérationnelle Windows après C5

## Statut

`PREPARED_AND_TESTED_NOT_A_QUALIFICATION`

Cette révision traite uniquement les trois défauts observés dans les gabarits et le
collecteur de l'exécution C5 `run-05`. Elle ne modifie ni le candidat
`ss-windows-runtime`, ni les fixtures, ni l'oracle, ni les contextes C5 gelés, ni leurs
réponses, ni leurs verdicts historiques. Elle ne crée aucune session de modèle et ne
constitue pas une reprise de `WR-H01` ou `WR-N01`.

## Correctifs proposés

1. **WR-H01 — garde de confinement.** Le chemin de base est désormais joint à un seul
   séparateur de répertoire natif. Les chemins internes, dont
   `output/runtime-artifacts`, restent acceptés ; une sortie réelle du case root reste
   refusée. Un mode `-PreflightOnly` borné ne démarre aucun enfant et sert uniquement à
   vérifier cette garde et le chaînage de postflight.
2. **WR-H01 — postflight absent.** La liste des entrées runtime est toujours initialisée
   comme tableau vide. Sous `Set-StrictMode`, une racine runtime absente produit donc un
   résultat vide vérifiable au lieu d'une lecture de propriété `Count` sur `$null`.
3. **WR-N01 — image du processus.** L'image est lue, normalisée, vérifiée contre le
   Java direct retenu et marquée comme observée avant le démarrage des lectures de flux.
   Une image absente, vide, divergente ou inaccessible arrête toujours la conduite avant
   toute affirmation d'identité ou arrêt forcé.
4. **Collecteur C5.** La présence seule du répertoire parent `output/runtime` n'est plus
   un résidu. Le collecteur échoue seulement sur une entrée réellement présente sous cette
   racine, un lien symbolique ou une forme de racine non sûre. Il ne supprime rien.

## Tests autorisés par cette révision

Le test local construit des copies réparées sous une racine temporaire isolée, puis :

- exécute seulement le préflight WR-H01 sans enfant WO-044, puis son postflight ;
- vérifie que la même garde refuse un chemin d'artefacts placé hors de son case root ;
- exécute la conduite WR-N01 directement sous Windows PowerShell 5.1 avec la fixture
  synthétique exacte, sans Maven, application, navigateur, Docker, fournisseur ou réseau ;
- vérifie le collecteur sur un parent runtime vide et sur un vrai résidu enfant.

Le test ne lance ni Codex éphémère ni recette de qualification. Ses sorties ne sont pas
réutilisables comme verdicts WR-H01/WR-N01 ; seule une nouvelle autorisation peut créer des
contextes, sessions et preuves de qualification frais.

## Artefacts

- `build_repaired_templates.py` produit des copies réparées depuis les sources C5 exactes
  et vérifie chaque ancre remplacée une seule fois.
- `c5_runtime_root_collector.py` est la règle postflight stable à intégrer au prochain
  conducteur hôte.
- `test_operational_repair.py` est le test local borné ; il nettoie sa racine temporaire.
- `validation.json` et `static-review.md` seront produits après les contrôles, sans
  réécrire les preuves historiques.
