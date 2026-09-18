# Suivi C5 — préflight Windows depuis l'hôte

**État : `BLOCKED_BEFORE_POWERSHELL`.** Ce suivi complète le diagnostic initial
`sandbox-diagnosis.md`, dont l'étape suivante demandait précisément un préflight sans
modèle depuis un parent hôte non imbriqué. Il ne modifie aucun candidat, contexte gelé,
harnais, fixture ni réglage de sandbox.

## Exécution observée

Le contrôle sans modèle est lancé par `asusggo2025\geoff` avec le même binaire Codex,
le profil `:workspace` et chacun des deux contextes C5 préparés. Il doit seulement lire
le candidat, créer un marqueur sous `output/`, le relire puis le supprimer. Il ne demande
ni session de modèle, ni Java, ni harnais, ni sonde Windows.

| Cas | Résultat | Point atteint |
| --- | --- | --- |
| `WR-H01` | code `1`, `windows sandbox failed: helper_unknown_error: setup refresh had errors` | échec avant PowerShell |
| `WR-N01` | code `1`, même erreur | échec avant PowerShell |

Les deux observations établissent `model_invoked=false`, `runtime_probe_executed=false`,
`java_started=false`, `configuration_changed=false`, candidats inchangés et absence de
marqueur temporaire résiduel. Elles ne sont donc pas des exécutions C5 de `WR-H01` ou
`WR-N01`, et ne consomment pas leurs deux sessions autorisées.

## Conséquence sûre

Un parent hôte non imbriqué conservant `codex exec --ephemeral --sandbox workspace-write`,
les mêmes sources et les bornes C5 appartient au périmètre autorisé. Il faut cependant
un préflight hôte **réussi** avant de lancer une recette Windows : l'échec actuel arrive
avant le programme PowerShell que les sondes doivent qualifier.

Le rétablissement peut suivre la séquence de diagnostic de l'environnement `elevated`
déjà documentée, puis refaire ce même préflight sans modèle. Aucun passage à
`unelevated`, `danger-full-access`, `--dangerously-bypass-approvals-and-sandbox` ou
`--ignore-user-config` n'est autorisé par ce suivi : ces changements demanderaient une
décision explicite du propriétaire et modifieraient la frontière de qualification.

## Preuve source

La preuve structurée est
[`preparation/host-prepared-access.json`](../preparation/host-prepared-access.json) ; son
script de contrôle et le diagnostic initial sont respectivement
[`preparation/check-host-prepared-runtime.py.txt`](../preparation/check-host-prepared-runtime.py.txt)
et [`preparation/sandbox-diagnosis.md`](../preparation/sandbox-diagnosis.md). Cette note
ne remplace pas ces fichiers et n'ajoute aucune mesure de runtime Windows.
