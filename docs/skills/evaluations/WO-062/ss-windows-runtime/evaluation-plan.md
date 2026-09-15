# WO-062 — Plan gelé de ss-windows-runtime

Statut : `PREPARED_NOT_RUN`. Préparation C2 avant rédaction et essais.
Statuts du Lab : EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED,
NO_CRITICAL_DEPENDENCY.

## Pièces et ordre

Les [cas](cases.json), [entrées](inputs.json), [sources](source-inventory.md),
[oracle réservé](oracle.md), [protocole opératoire](runtime-protocol.md) et
[fixture Java](fixtures/NativeProbe.java) sont figés par le [manifeste](manifest.json).
Ces huit fichiers ne contiennent aucun candidat ni résultat d'évaluation.

| Cas | Nature | Travail observable |
| --- | --- | --- |
| WR-H01 | Historique + reproduction neuve | Exécuter WO044 PS7 Windows ; relire WO053 sans inventer sa cause |
| WR-N01 | Nouvelle qualification locale | Exécuter les deux modes de la sonde en PS5.1/Java25, avec preuves |
| WR-C01 | Contre-épreuve | Faux vert natif, code écrasé, runtime erroné, nettoyage absent |
| WR-C02 | Contre-épreuve | Port tiers, Docker, PSModulePath et encodage sans mutation |
| WR-S01 | Sélection explicite | Demande nommée |
| WR-S02 | Sélection implicite | Incident Windows du Lab |
| WR-S03 | Non-déclenchement lot 1 | Fiche de reprise de WO sans incident runtime |
| WR-S04 | Non-déclenchement autre dépôt | Incident Windows d'un projet Python indépendant |

Figer cette préparation avant le candidat. Dans l'ordre adopté, rédiger et qualifier
ss-java-module avant ss-windows-runtime ; l'inventaire préparatoire peut être parallèle.
Une correction après gel produit un nouveau gel/run motivé ; les résultats antérieurs
restent inchangés. Tous les états initiaux sont `NOT_RUN`.

## Isolation et transmission

Pour chaque cas métier, créer une session native neuve sans historique de conception.
Transmettre seulement le candidat exact, le prompt, le fragment `inputs.cases[id]`
et sa provenance commune, les fichiers des `source_ids` et les `input_files`
du cas. WR-N02 est réservé au cadrage, jamais transmis.

Les sources sont matérialisées depuis leurs blobs du commit
`6648dd423e556b5248b8793a539ae85a7680f9bc` ou vérifiées contre leurs octets
figés. Conserver arborescence et nouvelle empreinte de matérialisation. Les deux
`input_files` supplémentaires sont copiés sous `inputs/<chemin relatif>` :
`runtime-protocol.md` et, pour WR-N01 seulement, `fixtures/NativeProbe.java`.
Leur extension Java doit être conservée. Aucune copie du dépôt entier n'est nécessaire.

Ne transmettre ni oracle, ni inventaire commenté, ni plan, ni résultat antérieur,
ni résumé du concepteur. Une source métier historique expressément allowlistée est
une entrée historique ; elle ne doit pas être remplacée par une réponse corrigée.
Les liens contenus dans une source n'étendent pas son allowlist.

## Deux exécutions locales requises

Seuls WR-H01 et WR-N01 portent `execution_mode=bounded_local_runtime`.
Utiliser exactement le `runtime_envelope` générique de cases.json, en remplaçant
`{prompt}` par le prompt du cas et `{allowed_files}` par les chemins réellement
matérialisés, sans ajouter les verdicts attendus. Les permissions autorisent l'écriture
dans `output/` de la session isolée ; le contrat restreint ces écritures à
`output/runtime-artifacts/` pour scripts/preuves et `output/runtime/` pour les
seules ressources jetables créées et nettoyées.

Les traces textuelles conservées dans runtime-artifacts portent les extensions
`.json`, `.txt`, `.md`, `.ps1`, `.java` ou `.csv`.
Les sorties brutes sont conservées seulement lorsqu'elles appartiennent au
protocole synthétique non sensible ; jamais de dump global d'environnement.

WR-H01 exécute le harnais historique PS7, intact, avec deux itérations et
TEMP/TMP de l'enfant sous output/runtime. WR-N01 exige le vrai Windows PowerShell
5.1 Desktop et un Java 25 local découvert, puis deux processus issus de la fixture
neuve. Son contrôleur PS5.1 est écrit par la session évaluée sous runtime-artifacts.
Les limites, le confinement, les lectures non bloquantes et les critères de propriété
figurent dans l'entrée opératoire ; celle-ci ne fournit pas de résultat de session.

Aucun Maven, Pester, Docker, navigateur, fournisseur, HTTP, application Spring,
base, WSL, pipeline, installation ou mutation persistante. Aucun arrêt global ni
nettoyage récursif d'une cible non vérifiée. Un défaut d'environnement bloque le
cas avec sa preuve ; il n'autorise pas une installation ou un autre runtime.
La deadline ne peut pas être relevée pour obtenir un résultat vert. Une simple
proposition de commandes laisse l'obligation de reproduction `NOT_EXECUTED`.

WR-C01/C02 restent en lecture seule : les PID et transcriptions sont synthétiques,
donc aucune interrogation/terminaison de ces identités sur le poste. Les quatre
sélections s'arrêtent au routage ; elles ne lancent aucune tâche métier.

## Sélection native

Employer exactement `selection_envelope` avec le seul remplacement de `{prompt}`.
Les quatre catalogues sont réellement découverts ; conserver leurs métadonnées,
chemins, provenance et collisions. Copier le candidat isolé sous `.agents/skills`
de la session, sans injecter manuellement son corps ni annoncer le nom attendu.

Conserver la découverte et une lecture outil intégrale de chaque SKILL.md sélectionné,
avec chemin et hash du candidat. Une réponse qui nomme le skill sans lecture prouvée
ne suffit pas. La découverte et la lecture explicite observées n'établissent pas une
injection automatique du corps par le CLI. Une simulation raisonnée reste une
simulation, pas une preuve de sélection native complète.

## Preuves et revue indépendante

Chaque run conserve ID, prompt réellement reçu, entrées/empreintes, commit/blob,
version/hash du candidat, runtime observé, modèle/effort effectivement utilisés,
commandes et événements outils, durées, codes, sortie finale et pièces sous output.
Conserver les erreurs/expirations et les postflights séparément. Le relecteur applique
l'oracle après la réponse, sans accès préalable de la session évaluée à ces attentes.

Le résultat principal, le résultat de nettoyage et le verdict de l'évaluation sont
trois états distincts. Une sonde volontairement en échec peut constituer une bonne
qualification du diagnostic si ses preuves et bornes satisfont les critères.
Aucun contrôle obligatoire manquant, runtime substitué, résidu ou absence de
reproduction ne devient PASS. Motiver FAIL/BLOCKED/NOT_EXECUTED selon l'observation.

Les huit cas, la revue indépendante puis l'acceptation humaine du contenu restent
à réaliser. Cette préparation ne vaut ni permission d'installation personnelle,
ni livraison Git, ni consolidation des dix skills. Gain de temps/qualité général :
**non mesuré**, sans témoin comparable.
