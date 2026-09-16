# C5 — plan d’exécution du contexte Windows formel

**État : `PREPARED_NOT_INVOKED`.** Ce plan décrit uniquement l’ordre et les
gardes de la prochaine consommation de `WR-H01`, puis de `WR-N01`. Il ne lance
ni session de modèle, ni sandbox, ni PowerShell cible, ni JVM, ni harnais. Il ne
crée pas une nouvelle autorisation : il se rattache exclusivement à
[`authorization.json`](../authorization.json), au run logique `run-05` et au
candidat `ss-windows-runtime` `0.1.0-candidate.1`.

## Préalables immuables

- employer la racine physique locale `run-05-host-formal-20260916`, détenue par
  le parent hôte `asusggo2025\geoff`, jamais les racines C5 gelées détenues par
  `CodexSandboxOffline` ;
- relire et rapprocher l’autorisation, le candidat SHA-256
  `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`,
  `preflight.json`, les demandes, prompts et entrées déclarées avant **chaque**
  lancement ;
- refuser un `output/` ou un dossier `frozen/` déjà rempli, tout écart d’octets,
  toute modification de configuration et tout fallback `unelevated` ou bypass du
  sandbox ;
- conserver `codex exec --ephemeral --sandbox workspace-write`, un watchdog
  externe de 900 secondes, une session fraîche par cas et
  `automatic_retry=false`.

Le [reçu d’intégrité formel](windows-host-formal-context-integrity.json) et le
[préflight d’accès sans modèle](windows-host-formal-preflight.raw.json) établissent
ces préalables. Ils ne constituent pas une recette du skill.

## Ordre séquentiel obligatoire

1. Exécuter **seulement** `WR-H01` dans une session fraîche. La fin normale doit
   contenir, avant l’expiration : exécution du harnais, preuves, postflight,
   conclusion et réponse finale. Si le lancement, la collecte, le postflight ou
   la réponse est incomplet, geler ce qui existe et arrêter : `WR-N01` ne démarre
   pas.
2. Exécuter `WR-N01` seulement après une collecte `WR-H01` complète. Il conserve
   sa propre session fraîche ; il ne réutilise aucun état de la première session.

Chaque écriture de preuve doit créer un nouveau fichier sans écraser de sortie
existante. Avant la revue indépendante, geler la réponse, les événements utiles,
`stderr`, chronométrages, contrôles avant/après, audit de lecture et les seuls
artefacts runtime textuels prévus.

## Garde particulière de `WR-H01`

Le module `scripts/wo036/WO036-CampaignTools.psm1` est une dépendance runtime
du harnais, pas une source de lecture manuelle. La session ne doit donc ni le
lire intégralement ni le relire ; l’extrait autorisé
`inputs/c5/wrh-module-extract.md` est la seule source manuelle de cette frontière.
L’audit de lecture doit signaler un accès direct au module runtime-only et toute
lecture répétée d’une entrée autorisée. Le harnais et son postflight restent
bornés aux ressources temporaires qu’il a créées.

## Garde particulière de `WR-N01`

Le préflight `-PreflightOnly` de `WR-N01` doit d’abord enregistrer le runtime
réel et vérifier les dépendances du conducteur, sans découverte Java, JVM ou
répertoire temporaire. Il doit séparer cet enregistrement du calcul d’empreinte
de fixture et employer le SHA-256 .NET direct, avec son auto-test `abc`, sans
dépendre de `Get-FileHash`.

Le conducteur complet répète son contrôle de dépendances dans le processus
Windows PowerShell 5.1 qui lance effectivement les deux modes, avant toute
découverte Java. Il doit ensuite prouver, pour `failure` et `sleep`, l’identité
et la propriété des processus réellement créés, les délais bornés, le code natif
`23` du mode failure, `READY` avant l’attente du mode sleep, puis le nettoyage
des seules ressources détenues. Aucun arrêt global de Java, Docker ou Eclipse
n’est admissible.

## Résultat attendu de la future recette

Le résultat devra distinguer une collecte complète de son verdict sémantique.
Une preuve manquante, un timeout ou un nettoyage non établi laisse le cas
`BLOCKED` ou `INCOMPLETE`; il ne peut pas être converti en `PASS` par les
préflights présents dans ce dossier.
