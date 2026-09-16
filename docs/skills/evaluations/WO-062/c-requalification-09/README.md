# WO-062 — C9, qualification Windows fraîche après réparation opérationnelle

## Objet

Cette campagne C9 qualifie seulement `ss-windows-runtime`
`0.1.0-candidate.1` pour les deux cas encore ouverts `WR-H01` et `WR-N01`.
Elle fait suite à la réparation technique C5, conservée dans
`c-windows-operational-repair-06/`, sans altérer le candidat, les fixtures,
les oracles, les réponses ni les verdicts historiques C4/C5.

L'autorisation propriétaire reçue le 16 septembre 2026 couvre explicitement
une recette fraîche. Elle impose deux nouvelles sessions éphémères, des
contextes Windows nouveaux détenus par l'hôte, un conducteur hôte C9 qui
intègre `c5_runtime_root_collector.py` et une revue postfreeze distincte.

## Bornes

- Deux sessions au plus : `WR-H01`, puis `WR-N01`, une seule fois chacune.
- Aucun oracle, résultat, revue, réponse antérieure ou historique de conception
  n'est fourni aux sessions évaluées.
- Les gabarits opérationnels sont reconstruits depuis C5 par le constructeur de
  réparation validé. Les sources C5 restent lues seulement comme origine des
  copies réparées et ne sont jamais modifiées.
- Les sondes locales sont bornées : 60 secondes pour le harnais H01, 45 secondes
  pour le conducteur N01, et 900 secondes pour chaque processus Codex externe.
- Aucun build Maven, application, navigateur, Docker, réseau, installation
  personnelle, validation humaine, publication, push ou fusion n'est lancé.
- Une absence d'identité de processus, une fuite, un écart d'intégrité ou une
  expiration est un résultat visible. Le conducteur ne relance pas un cas et ne
  force jamais l'arrêt d'un processus tiers.

## Séparation des preuves

`run-05` reste la preuve C5 gelée : ses deux BLOCKED ne sont ni réécrits ni
recyclés. `run-06` est une nouvelle observation formelle portant son propre
préflight, ses contextes, ses traces, ses artefacts, son gel et sa revue. Une
observation C9 positive ne valide pas humainement le skill et n'autorise aucune
installation.

## Étapes attendues

1. Préparer les contextes hôte C9 depuis les trois copies réparées et produire
   leur préflight byte-à-byte.
2. Vérifier statiquement l'autorisation, le candidat, les fichiers autorisés,
   les copies et le conducteur avant tout lancement modèle.
3. Lancer H01 puis N01 une fois dans de nouveaux processus éphémères.
4. Geler les résultats en lecture seule de fait et exécuter une revue
   postfreeze distincte de l'exécution.
