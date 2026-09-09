# J4/J5 V4 — personnes, pays et présentation des compositions

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Complément du WO-058 explicitement demandé par le propriétaire le 9 septembre 2026.

## Contrats et provenance

`event-details-v4` conserve le contrat sportif de V3 et ajoute les noms/pays de
`event.homeTeam.manager`, `event.awayTeam.manager` et `event.referee`. Le texte non vide
de `event.roundInfo.name` est prioritaire sur le numéro `round` ; l’absence du texte
conserve le repli historique. Les personnes/pays facultatifs absents restent inconnus.

`event-lineups-v4` conserve le contrat V3 des capitaines, statistiques individuelles
et indisponibles, et normalise `player.country.name`/`alpha2` pour les titulaires,
remplaçants et joueurs du bloc `missingPlayers`. Le pays ne change pas les
critères sportifs de complétude. Les champs facultatifs malformés sont signalés puis
ignorés selon les bornes du parseur, sans inventer de valeur.

V46 ajoute les colonnes facultatives aux observations J4 et aux joueurs J5. Seuls les
nouveaux contrats peuvent porter ces champs. Les anciennes observations ne sont pas
réinterprétées : leurs références source, empreintes, versions de parseur et heures
restent consultables. Chaque carte live provient de l’observation exacte référencée par
le curseur de famille ; une observation manuelle plus récente ne s’y substitue pas.
Les parcours manuels/imports et nouveaux résultats live activent les parseurs V4.

## Présentation

Les buts viennent de `statistics.goals`, les passes décisives de `statistics.goalAssist`.
Une valeur absente n’est pas remplacée par zéro. L’affichage conserve les nombres,
répète les icônes pour de petites valeurs et emploie un compteur compact lorsque
l’espace manque. La chaussure est un SVG original intégré au composant. Les cartes
sans statistique exploitable n’offrent pas de lien ou de panneau de détails vide.

Les images de drapeaux sont des ressources locales issues de
[flag-icons 7.3.2](https://github.com/lipis/flag-icons), sous licence MIT. Le manifeste
des fichiers et la licence figurent dans `src/main/resources/static/images/flags/`.
L’application construit uniquement des chemins locaux pour les codes ISO connus,
XK et les associations GB nommées Angleterre/Écosse/Pays de Galles. Un code inconnu
reste affiché sous forme textuelle. Le nom du pays accompagne le drapeau décoratif.
Le choix d’images évite de dépendre du rendu des émojis de drapeau sous Windows.

Aucun endpoint d’image SofaScore n’est ajouté. Une ouverture de carte, de période ou
un rafraîchissement de la page ne provoque aucun appel fournisseur. Les valeurs source
restent du texte échappé dans le HTML et sont mises à jour avec `textContent` côté navigateur.

Les incidents sont regroupés dans l’ordre fourni, avec une section globale et des
périodes repliables. Les clés stables permettent de conserver les ouvertures et le focus
au rafraîchissement. La table technique et la provenance restent disponibles.
