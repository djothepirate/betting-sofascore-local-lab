# Revue statique — réparation opérationnelle Windows C5

## Verdict : PASS_TECHNICAL_REPAIR_ONLY

La révision conserve les trois sources C5 utilisées par `run-05` avec leurs SHA-256
d'origine. Elle construit des copies séparées depuis des ancres textuelles uniques : un écart
de source, une ancre dupliquée ou une destination déjà existante bloque la construction. Les
copies ne deviennent pas des contextes de qualification et le constructeur ne possède aucune
commande Codex, Java, PowerShell ou réseau.

### WR-H01 : garde et postflight

La garde ne concatène plus deux antislashs littéraux. Elle normalise la base en retirant les
séparateurs terminaux, puis compare le chemin complet à cette base suivie d'un unique
séparateur de répertoire natif. Cette forme accepte `output/runtime-artifacts` lorsque ce
répertoire appartient au case root et conserve le refus des chemins qui le quittent. Le test
exerce les deux résultats sans lancer le harnais WO-044.

Le nouveau `-PreflightOnly` ne peut pas démarrer l'enfant : il produit
`PREFLIGHT_OK_NO_HARNESS`, conserve `NO_CHILD_CREATED`, puis passe par le même `finally` et
la même écriture de preuve que l'exécution normale. Le chemin normal reste inchangé lorsque le
switch est absent.

Le postflight initialise toujours `$entries` comme tableau. L'absence de la racine runtime
donne donc `runtime_empty=true` sous `Set-StrictMode`, plutôt qu'une propriété `Count` lue sur
`$null`. Une entrée réelle continuerait à être reportée par le postflight ; elle n'est jamais
supprimée par ce correctif.

### WR-N01 : image avant assertion

Le conducteur relevait auparavant l'image après le démarrage des lectures asynchrones et
acceptait une valeur nulle sans erreur, pour échouer ensuite seulement dans
`Assert-ProbeIdentity`. La copie réparée lit et normalise l'image immédiatement après
`Process.Start`, avant toute lecture de flux. Elle exige une valeur non vide et l'égalité avec
le Java direct résolu ; les deux marqueurs de capture sont eux-mêmes requis par
`Assert-ProbeIdentity`.

Cette modification n'assouplit pas le comportement en cas d'incertitude : une image absente,
vide, divergente ou inaccessible provoque un blocage explicite avant de pouvoir affirmer une
identité ou forcer un arrêt. Le test local sous Windows PowerShell 5.1 confirme le parcours
rapide `failure`, le code 23 et l'image établie avant lecture, puis observe `sleep` et son
arrêt possédé dans les bornes existantes.

### Collecteur C5

`c5_runtime_root_collector.py` sépare maintenant :

- parent `output/runtime` absent ou vide : `PASS` ;
- enfant réellement présent sous ce parent : `FAIL`, entrée conservée ;
- lien ou racine non sûre : `FAIL`, chemin conservé ;
- aucune suppression : `delete_attempted=false`.

Cette règle évite le faux blocage déclenché par un parent vide sans transformer un vrai résidu
en succès. Elle doit être intégrée au prochain conducteur hôte ; le brouillon qui a exécuté
`run-05` et les preuves gelées ne sont pas modifiés.

## Limites de revue

Cette revue est statique et technique, réalisée avant toute nouvelle recette modèle. Elle n'est
ni une revue indépendante, ni une validation humaine, ni une qualification du skill. Le statut
de `ss-windows-runtime` reste `INCOMPLETE_QUALIFICATION` à 6 PASS / 0 FAIL / 2 BLOCKED.
