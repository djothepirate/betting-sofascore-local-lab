# J5 — Compositions V3 : capitaines, statistiques individuelles et indisponibles

Complément WO-058 demandé par le propriétaire le 9 septembre 2026, à partir des champs
du JSON `EVENT_LINEUPS` fourni dans la conversation. Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`,
`NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY` restent effectifs.

## Contrat de lecture

Ce contrat historique est complété par [V4 pour les pays et la présentation](J4-J5-PEOPLE-V4.md). Le parseur fournisseur de ce lot historique devient `event-lineups-v3`. Les parseurs V1 synthétique et V2
fournisseur restent disponibles pour leurs interprétations historiques. V3 conserve les critères
de complétude de V2 ; les champs facultatifs ci-dessous n'ajoutent pas de signaux attendus par joueur.
Ce complément utilise le endpoint déjà couvert : il ne crée ni appel, ni endpoint, ni collecte
supplémentaire lors de l'ouverture d'une carte.

| Entrée | Donnée normalisée | Restitution |
|---|---|---|
| `captain: true` | booléen optionnel vrai | « C · Capitaine » |
| `captain: false` | booléen optionnel faux | aucun badge |
| `captain` absent ou null | option vide | aucun badge, sans affirmer faux |
| `statistics` absent ou null | option vide | statistiques non renseignées dans cette observation |
| `statistics: {}` | bloc présent vide | aucune statistique individuelle renseignée |
| métrique numérique égale à zéro | zéro conservé | zéro affiché |
| métrique absente ou null | aucune valeur | aucun zéro inventé |
| `missingPlayers` absent ou null | option vide | liste non renseignée |
| `missingPlayers: []` | liste présente vide | aucun indisponible signalé dans cette observation |
| joueur indisponible sans description/date | identité et attributs reçus | indisponible, sans motif ou retour inventé |

Les nombres sont lus directement en décimal, sans passage par un flottant binaire, puis conservés
en `BigDecimal` canonique. Les métriques simples de `statistics` et les variantes numériques de
`ratingVersions` sont deux ensembles distincts à clés triées. `rating` reste la note principale :
`ratingVersions.alternative` ne la remplace pas. Les métadonnées `statisticsType` ne deviennent pas
des performances. Les noms numériques inconnus sont conservés et signalés ; les objets inconnus
restent des avertissements, sans extraction récursive arbitraire. Une valeur d'un type incorrect
pour un champ pris en charge rend le parsing incompatible, sans observation partielle.

Les blocs sont bornés : 128 métriques simples, 16 variantes de note et 128 indisponibles par
équipe. Les clés statistiques comportent au plus 80 caractères ASCII (`A-Z`, `a-z`, chiffres,
`_`, puis éventuellement `.` ou `-`). Après canonisation, un nombre conserve au plus 64 chiffres
significatifs et une échelle comprise entre -32 et 32. Les objets inconnus ne contournent pas
ces bornes par une extraction récursive.

Les joueurs indisponibles conservent l'ordre source, l'identifiant fournisseur, le nom, le numéro
et le poste éventuels, ainsi que `type`, `reason`, `description`, `externalType` et `expectedEndDate`.
Les codes numériques ne sont pas interprétés comme un diagnostic. La date est une date de retour
estimée fournie par la source, avec son décalage horaire ; elle n'est pas une confirmation de retour.
Ces joueurs ne sont pas ajoutés aux titulaires ni aux remplaçants et n'en modifient pas les compteurs.

## Conservation et compatibilité

Flyway V41 ajoute trois colonnes nullables sans défaut et sans reprise des anciennes lignes :
`j5_event_lineup_player.captain`, `j5_event_lineup_player.statistics` et
`j5_event_lineup_side.missing_players`. Les deux blocs structurés sont du JSONB normalisé, sans
copie des objets fournisseurs complets. Les nombres sont conservés comme nombres décimaux ; le
tableau des indisponibles conserve son ordre. La provenance reste portée par l'observation J5
parente : snapshot/fixture, SHA-256 source, parseur et réception.

Les bornes textuelles du domaine Java comptent les unités UTF-16 ; PostgreSQL compte les
caractères Unicode. Comme pour les colonnes textuelles historiques, une écriture SQL directe
de très longues chaînes hors BMP peut donc satisfaire la borne SQL et être refusée par le
domaine en relecture. Le parcours parseur/JDBC applique la borne Java avant toute insertion.

L'empreinte normalisée conserve exactement sa sérialisation historique lorsque tous les ajouts
sont absents. Dès qu'un capitaine (même faux), un bloc statistiques (même vide) ou une liste
d'indisponibles (même vide) est présent, l'extension `j5-event-data-observation-v5` couvre ces
informations pour les deux équipes. Les métriques et variantes de note sont sérialisées par clé
dans un ordre déterministe. Une provenance V1/V2 ne peut pas attribuer ces nouvelles données.

Le store reste append-only. Le rejeu enrichi d'un même brut produit une autre interprétation et
une autre empreinte ; il ne réécrit pas l'observation V2. Sans information nouvelle, la déduplication
source/empreinte peut légitimement retrouver l'ancienne observation V2. Les anciennes compositions
ne gagnent donc pas automatiquement des capitaines, statistiques ou indisponibles.

J6 compare les capitaines, les métriques individuelles, les variantes de note et les attributs des
indisponibles. Il distingue absence et bloc vide. Les indisponibles sont appariés par identité
fournisseur, sans transformer un simple changement d'ordre en changement de joueur.

Le contrat d'export J7 v1 reste sa projection fermée existante de la composition : cinq champs par
joueur, sans capitaine, statistiques individuelles ni indisponibles. J7 vérifie néanmoins
l'empreinte complète de l'observation source V3 et la conserve dans le manifeste ; son empreinte
du jeu de sources change avec les enrichissements, même si les données exportées restent identiques.
L'export de ces nouveaux attributs demanderait une évolution distincte du contrat J7.

## Présentation et qualification

Les pages live et J5 utilisent le même composant. Le détail natif d'une carte est utilisable à la
souris et au clavier, avec ou sans JavaScript. Les chiffres affichés sont regroupés en français ;
l'arrondi d'affichage ne modifie jamais la donnée persistée. Les champs sans unité explicitement
établie ne reçoivent pas une unité supposée. Un nom de métrique inconnu reste lisible sous sa clé.
Les panneaux ouverts et le focus sont conservés lors d'une nouvelle observation live.

Les motifs connus des joueurs indisponibles sont traduits uniquement dans la projection française,
par exemple `red_card_suspension`, épaule, ménisque, hernie, ligaments, cœur, coup, aine et
`Strain Injury`. La valeur source (`description`, `type`, `reason` et `externalType`) reste
conservée dans l’observation et dans le volet « Informations fournisseur » ; `missing` s’affiche
comme « Indisponible ». Une description inconnue reste lisible telle que reçue, sans diagnostic
inventé.

Voir le [runbook live](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) et le
[rapport de qualification du complément](../validation/WO058-PLAYER-DETAILS-20260909.md).
Les tests utilisent un corpus synthétique et PostgreSQL isolé ; le JSON fourni par l'opérateur
reste une entrée locale ignorée par Git et ne constitue pas une nouvelle collecte fournisseur.
