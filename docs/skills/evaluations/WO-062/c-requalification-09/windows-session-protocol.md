# C9 — protocole des sessions Windows fraîches

Ce protocole complète seulement le fragment de cas reçu. Il ne fournit aucun
oracle, résultat, réponse antérieure, revue C4/C5 ni historique de conception.
Toutes les écritures de la session restent sous `output/runtime-artifacts/` du
contexte C9. Les gabarits sont des copies réparées et doivent être copiés sans
modification avant leur exécution.

## Lecture et discipline commune

- Lire le candidat exact, `request.txt`, ce protocole et chaque entrée lisible
  explicitement déclarée une fois au plus, par sections nécessaires. Les liens
  rencontrés ne donnent pas accès à d'autres sources.
- Ne pas créer de sous-agent ou session annexe. Ne pas démarrer Maven,
  l'application, un navigateur, Docker, un réseau ou une collecte fournisseur.
- Préparer une conduite courte. Après le lancement réel, conserver les preuves,
  faire le postflight séparé demandé par le cas, relire les résultats une seule
  fois, conclure et répondre. Viser moins de six minutes ; le watchdog hôte de
  quinze minutes ne change aucune borne native.
- Les erreurs conservent l'étape, le type, le message, l'identifiant, la ligne
  et la pile disponibles, sans inventaire global de l'environnement. Ne pas
  rédiger de README, manifeste, revue ou nouveau protocole dans la session.

## WR-H01 — frontière du module et chaîne de fin

`scripts/wo036/WO036-CampaignTools.psm1` est une dépendance de runtime du
harnais, jamais une source à lire manuellement. Son extrait autorisé est
`inputs/wrh-module-extract.md`. Il est interdit d'appliquer au module complet
`Get-Content`, `-Raw`, `cat`, `type`, `ReadAllText`, glob, recherche ou une
seconde commande textuelle. Son import par le harnais réparé est permis.

Copier sans modification `inputs/c9/wrh-harness-template.ps1` et
`inputs/c9/wrh-postflight-template.ps1` vers `output/runtime-artifacts/`.
Exécuter le harnais une seule fois avec PowerShell 7 Windows et `-Iterations 2`.
Les variables `TEMP` et `TMP` de l'enfant restent sous `output/runtime/`.
Le gabarit a déjà la garde de confinement réparée : ne pas l'altérer ni ajouter
un contournement. Garder handle, PID, heure de création, image et arguments de
l'enfant dès son lancement effectif. La chaîne obligatoire est : lancement,
preuves de résultat du harnais, postflight distinct, commande distincte
`WR_PROMPT_RETURNED`, conclusion et réponse finale.

## WR-N01 — préflight puis deux JVM détenues

Copier sans modification `inputs/c9/wrn-conductor-template.ps1` vers
`output/runtime-artifacts/Invoke-WRN01.ps1`. Lancer d'abord cette copie sous
Windows PowerShell 5.1 avec `-PreflightOnly`; elle ne démarre aucune JVM. Si ce
préflight passe, lancer exactement une fois la même copie sans ce commutateur.

Le conducteur inscrit son runtime avant tout calcul d'empreinte, vérifie les
dépendances dans son propre processus, observe `Get-FileHash` sans en dépendre
et emploie le repli .NET SHA-256. Il trouve un Java 25 direct unique, exécute
exactement `failure` puis `sleep` avec UUID distincts, et conserve l'identité de
chaque JVM avant l'ouverture des flux. Une image absente, vide, divergente ou
inaccessible bloque l'affirmation d'identité et tout arrêt forcé : aucune
recherche par nom ni arrêt tiers n'est permis.

`failure` attend au plus 10 secondes et conserve son code natif. `sleep` attend
READY au plus 10 secondes, attend 1 500 ms depuis READY, puis ne force l'arrêt
que si handle, PID, UUID, image et commande concordent. L'attente après arrêt
reste au plus cinq secondes et la conduite totale au plus 45 secondes. La
preuve doit montrer les deux modes, le retour et le nettoyage ; un parent
`output/runtime` vide n'est pas à supprimer et ne constitue pas un résidu.
