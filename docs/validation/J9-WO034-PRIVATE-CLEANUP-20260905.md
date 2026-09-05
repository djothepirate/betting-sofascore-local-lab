# WO-034 — nettoyage privé après abandon

## Décision et exécution

Le propriétaire autorise explicitement le nettoyage des artefacts privés afin de terminer l'abandon de WO-034. Exécution achevée le 2026-09-05 à 14:11:08 UTC (16:11:08 Europe/Paris).

Le périmètre est exclusivement le conteneur privé WO-SS-20260901-034 sous Documents/SofaScoreLocalLab-private/permission-requests du profil propriétaire. La chaîne canonique et ses ancêtres sont sans reparse point. Le bootstrap a été vérifié contre le SHA-256 historique 3741fa3fdf4097f155c0d58891cca10bb9951561e996edff5f75dba78b75e731 ; ses clés, sa racine et son identité propriétaire concordent avec la session. Les propriétaires ACL des cibles concordent également. Aucun SID ni valeur de saisie n'est restitué.

L'inventaire exact a été contrôlé avant suppression. Un verrou exclusif du renderer a été acquis. Suppression native par chemins littéraux, fichier par fichier, puis des seuls répertoires vides ; aucune suppression récursive.

## Résultat observé

- Six fichiers supprimés : owner-input.properties, bootstrap-manifest.properties, .wo034-render.lock et les trois copies de qualified-runtime (launcher, renderer, template).
- Deux répertoires supprimés : qualified-runtime puis le conteneur privé WO-034.
- Aucun message final, enveloppe de formulaire ou preuve de rendu présent dans cet inventaire.
- Répertoire cible absent après exécution ; parent conservé et inventaire des éléments voisins inchangé.
- Contenu de owner-input.properties non lu. Seul le bootstrap de propriété a été lu pour vérification, sans restitution de ses valeurs.

```text
PRIVATE_CLEANUP=PASS
FILES_REMOVED=6
DIRECTORIES_REMOVED=2
EXACT_ROOT_ABSENT=YES
PARENT_PRESERVED=YES
SIBLING_INVENTORY_UNCHANGED=YES
WO034_STATUS=ABANDONED_BY_OWNER
WO034_PRIVATE_CLEANUP_OBLIGATION=SATISFIED_FOR_EXACT_OWNED_ROOT
```

Suppression directe, sans passage par la Corbeille ; ce n'est pas une attestation d'effacement forensique ni d'absence de copies dans d'éventuelles sauvegardes, historiques d'éditeur ou autres emplacements non inspectés. Aucune suppression hors de cette racine.

## Clôture et contrôles

L'obligation de nettoyage du conteneur identifié est satisfaite ; aucune reprise, saisie ou production de rendu attendue. Les preuves versionnées et les branches sont conservées. Aucune demande envoyée, aucun POST, aucune base ou identité mTLS touchée. NOT_EVIDENCED reste inchangé.

Contrôles documentaires : UTF-8 strict, diff --check et revue du diff expurgé. Aucun changement applicatif ou nouvelle qualification runtime/CI ; suites Maven non relancées pour ce suivi administratif.
