# Diagnostic C5 — préflight du sandbox Windows

**État : `BLOCKS_CURRENT_NESTED_LAUNCH; HOST_EXEC_NOT_PROVEN`.**

Ce document examine uniquement le contrôle d'accès C5. Aucune session de modèle, sonde
Windows WR-H01/WR-N01, JVM, exécution de harnais, ni modification de configuration n'a
été lancée pour établir ce diagnostic.

## Fait observé

Le contrôle C5 appelle le même point d'entrée que C4, sans modèle :

```text
codex sandbox --permission-profile :workspace -C <cas> powershell.exe ...
```

Il doit seulement lire le candidat, écrire puis supprimer un marqueur local. Il ne peut
donc ni démarrer Java ni exécuter les deux modes de WR-N01.

| Préflight | Lanceur consigné | Résultat | Interprétation sûre |
| --- | --- | --- | --- |
| C4, 15 septembre | `asusggo2025\\geoff` | `0` pour WR-H01 et WR-N01, lecture/écriture/nettoyage prouvés | Le profil `:workspace` pouvait alors démarrer le processus enfant depuis le compte hôte. |
| C5, 16 septembre | `asusggo2025\\codexsandboxoffline` | `1` pour les deux cas, aucun stdout ; `windows sandbox failed: CreateRestrictedToken failed: 87` | La création du sandbox enfant échoue avant le démarrage de PowerShell, donc avant la lecture, l'écriture ou le nettoyage demandés. |

Sources conservées :

- C4 : `docs/skills/evaluations/WO-062/c-requalification-04/preparation/prepared-access.py.txt`
  et `prepared-access.json` ;
- C5 : `.tmp/check_c5_prepared_runtime.py` et `.tmp/c5-prepared-access.json` ;
- contextes préparés C5 : `.tmp/wo062-evaluations/ss-windows-runtime/run-05/`.

La différence de propriétaire du lanceur est déterminante : le C5 a été exécuté depuis
un processus déjà sandboxé (`codexsandboxoffline`). Il vérifie donc une **imbrication de
sandboxes**, tandis que C4 était lancé directement par le compte hôte `geoff`. Les
nouveaux chemins C5 et le texte légèrement différent du marqueur existent aussi, mais
l'échec survient avant que ce texte soit donné à PowerShell.

## Ce que cela établit pour `codex exec`

Le binaire utilisé est `codex-cli 0.154.0-alpha.6.2`. Son aide locale indique que :

- `codex exec --sandbox workspace-write` sélectionne le sandbox des commandes émises par
  le modèle ;
- `--ephemeral` évite seulement de persister les fichiers de session ;
- `codex sandbox` exécute une commande sous le sandbox Windows à jeton restreint.

La documentation officielle confirme que `workspace-write` laisse les commandes locales
travailler dans la frontière du workspace, que le sandbox s'applique aux processus enfants,
et que `--ephemeral` porte sur la persistance de session, non sur cette frontière :

- [Sandboxing](https://learn.chatgpt.com/docs/sandboxing)
- [Mode non interactif](https://learn.chatgpt.com/docs/non-interactive-mode)
- [Sandbox Windows](https://learn.chatgpt.com/docs/windows/windows-sandbox)

Par conséquent, **le lancement C5 actuel est bloqué pour une qualification Windows** :
le conducteur doit lancer PowerShell et écrire ses preuves, ce que le préflight imbriqué
ne peut déjà pas faire. Démarrer une session de modèle dans ce même contexte ne serait
pas une recette valable.

Cela ne prouve pas que `codex exec --ephemeral --sandbox workspace-write` échouerait
depuis le compte hôte. Une telle commande n'a pas été lancée car elle invoquerait un
modèle. Le C5 prouve seulement l'échec de son prérequis dans le parent
`codexsandboxoffline`; il ne faut pas transformer ce résultat en défaut démontré de la
machine hôte, du candidat ou des harnais.

## Faits de configuration et limites

Le `config.toml` lu sans le modifier contient actuellement :

```toml
[windows]
sandbox = "elevated"
```

Il contient une confiance explicite pour les contextes Windows C4, mais aucune entrée de
confiance dédiée aux nouveaux chemins `run-05`. Cette corrélation ne permet pas d'attribuer
`CreateRestrictedToken failed: 87` à l'absence de cette entrée : le préflight C5 ne
consigne pas le mode d'implémentation réellement choisi, et l'aide de `codex sandbox`
parle elle-même de jeton restreint.

Le journal local du sandbox montre que les rafraîchissements de setup associés se terminent
avec `errors=[]`; il montre aussi des refus `SetFileAttributesW` sur `C:\\Users\\Default`.
Ces lignes sont des diagnostics de l'infrastructure. Elles ne sont pas une preuve que le
candidate, les répertoires C5 ou WR-H01/WR-N01 sont en cause.

## Suites sûres et documentées

1. **Ne lancer aucune des deux sessions Windows C5 depuis le parent déjà sandboxé.** Elles
   ne satisferaient pas le prérequis d'exécution locale et ne doivent pas être comptées
   comme nouvelles recettes.
2. **Refaire d'abord le même préflight sans modèle depuis un lanceur hôte non imbriqué**,
   avec le même binaire, le profil `:workspace`, les deux contextes gelés et la même
   vérification lecture/écriture/nettoyage. Conserver dans la preuve le compte lanceur et
   l'identité du parent. Cette étape ne change ni la configuration ni les sources.
3. **Si le préflight hôte échoue également**, appliquer la séquence de diagnostic officielle
   pour le sandbox Windows : redémarrer Codex, réessayer la mise en place `elevated`, puis
   collecter `CODEX_HOME/.sandbox/sandbox.log`, la version de Windows, le mode configuré et
   l'erreur exacte pour une revue d'administration ou de support. Ne jamais collecter le
   contenu de `.sandbox-secrets/`.
4. La documentation décrit `unelevated` comme un fallback sandboxé, plus faible, lorsque
   `elevated` ne peut pas être remis en état. **Il n'est pas appliqué ici** : ce serait un
   changement de configuration qui requiert une décision explicite, puis un nouveau
   préflight hôte avant toute recette.
5. Ne pas résoudre ce constat avec `danger-full-access`,
   `--dangerously-bypass-approvals-and-sandbox`, ni `--ignore-user-config`. Ces options
   changeraient la frontière de qualification et ne répondraient pas à la preuve recherchée.

La prochaine décision utile est donc la disponibilité d'un lanceur hôte non imbriqué pour
le préflight sans modèle. Les preuves historiques C4 restent intactes quelle que soit
l'issue de ce contrôle.
