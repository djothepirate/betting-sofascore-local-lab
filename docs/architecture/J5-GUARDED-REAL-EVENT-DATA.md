# J5 — Qualification réelle gardée des données événement

## 1. Statut

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
PROVIDER_SCHEMA_VALIDATED=NO
```

Cette architecture complète le contrat synthétique J5 V1 sans le remplacer. Elle autorise une
seule campagne humaine active à la fois sur une identité canonique J4 existante. Une campagne
réussie peut être suivie d'une nouvelle campagne explicitement préparée et confirmée ; le
développement, Maven et les fixtures ne contactent jamais SofaScore.

## 2. Frontière d'autorisation

La voie réelle n'est disponible que si les conditions suivantes sont simultanément vraies :

- `SOFASCORE_ENABLED=true` ;
- `SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true` ;
- les opt-ins J3 et J4 sont `false` ;
- l'origine est exactement `https://www.sofascore.com`, avec un slash racine facultatif ;
- les endpoints autorisés sont exactement `EVENT_STATISTICS`, `EVENT_INCIDENTS` et
  `EVENT_LINEUPS` ;
- le stockage brut est actif, la concurrence vaut un, le polling et le rafraîchissement
  automatique sont désactivés.

Les propriétés Spring rejettent également une activation simultanée de plusieurs voies. Le
catalogue général reste `callable=false`, sans URI, et `ConnectorGate` reste bloquant. Le chemin
J5 est une exception spécialisée, temporaire et contrôlée par le Work Order actif.

## 3. Contrôle humain

La préparation est locale et sans réseau. Elle reçoit l'UUID de la page et l'identifiant déjà
persisté ; leur relation déterministe est vérifiée avant de produire une phrase aléatoire valable
cinq minutes. La confirmation exige :

- le même identifiant de requête ;
- la phrase exacte comparée en temps constant ;
- un acquittement explicite ;
- une politique de configuration encore valide.

Une confirmation produit un claim immuable. `COMPLETED_LOCKED` interdit tout rejeu de ce claim,
mais autorise une nouvelle préparation explicite dans le même processus. Cette préparation ne
contacte pas le fournisseur, crée un nouvel identifiant de requête et une nouvelle phrase, remet
la liste des familles terminées à zéro et exige un nouvel acquittement avant tout transport.
`FAILED_LOCKED`, `STOPPED_LOCKED` et `EXPIRED_LOCKED` restent des verrous de processus et exigent
un redémarrage.

## 4. Séquence réseau bornée

```text
identité locale vérifiée
  → EVENT_STATISTICS
  → délai minimal de 3 s
  → EVENT_INCIDENTS
  → délai minimal de 3 s
  → EVENT_LINEUPS
  → COMPLETED_LOCKED
```

Chaque requête est un `GET` vers un chemin construit localement. Le client n'utilise ni proxy,
ni redirection, ni cookie, ni jeton, ni compte, ni en-tête de navigateur. Les délais de connexion
et de lecture sont plafonnés à dix secondes, la réponse à cinq Mio et aucune API de retry n'est
exposée.

Un HTTP `404` sur l'un des trois chemins exacts n'est pas un incident de transport : la famille est
facultative et peut ne pas être publiée pour l'événement ou sa compétition. Le snapshot est
conservé, l'indisponibilité est enregistrée, aucun retry n'est effectué et la séquence continue
vers la famille suivante après le délai normal.

Au premier incident réel, les étapes restantes ne sont pas exécutées. Sont notamment terminaux :
arrêt opérateur, timeout, erreur I/O, réponse trop volumineuse, contenu sensible, HTTP `400`, `401`,
`403`, `408`, `429` ou `5xx`, autre statut non `2xx`, contenu non JSON sur une réponse `2xx`, JSON
ambigu, schéma incompatible et erreur de persistance.

## 5. Pipeline de données

Pour chaque famille :

1. le transport capture les octets et leur SHA-256 ;
2. `provider_snapshot` reçoit la ligne `RAW_ONLY` ;
3. un HTTP `404` produit une observation vide de valeurs, de statut `UNAVAILABLE`, via le
   normaliseur d'indisponibilité de la famille ; le snapshot devient `ENDPOINT_UNAVAILABLE` et la
   campagne continue ;
4. pour une réponse `2xx`, le type de contenu et le JSON sont contrôlés ;
5. le parseur fournisseur versionné produit soit un résultat complet, soit aucun objet normalisé ;
6. la normalisation J5 est liée par `PROVIDER_SNAPSHOT` au snapshot, au hash, au parseur ou
   normaliseur et à l'heure de réception ;
7. un snapshot `2xx` nouvellement inséré est classé `PARSED` après la persistance normalisée ;
8. si les octets sont dédupliqués vers un snapshot historique déjà terminal, sa classification
   reste immuable et le résultat du parseur courant est porté uniquement par l'observation
   normalisée append-only.

Les parseurs courants sont `event-statistics-v2`, `event-incidents-v4` et `event-lineups-v2`.
L'identifiant d'événement vient du claim et non du JSON. Les champs inconnus génèrent au plus 256
avertissements.
Une liste vide structurellement valide reste `EMPTY_VALID`; une absence facultative mesurée reste
`PARTIAL`. Une famille non publiée en HTTP `404` reste `UNAVAILABLE · N/A`, ce qui est distinct
d'une liste présente et vide. Aucune valeur n'est inventée.

La migration V8 étend uniquement la contrainte de versions de parseur créée par V7. Elle ne
modifie ni V7 ni une observation existante.

La migration append-only V9 ajoute `ENDPOINT_UNAVAILABLE`, `UNAVAILABLE` et les normaliseurs
`event-*-unavailable-v1`. Elle corrige uniquement la classification des anciens snapshots J5
HTTP `404` marqués `TRANSPORT_ERROR/HTTP_STATUS_404`; les octets, hashes, heures et identifiants de
snapshot restent inchangés. Elle ne fabrique pas rétroactivement d'observation normalisée.

Le fournisseur peut porter `addedTime=999` sur un objet technique `incidentType=period`. Cette
valeur est une sentinelle et non une durée littérale à afficher ou à additionner. Le parseur
`event-incidents-v3` la conserve exclusivement dans le snapshot brut, omet le temps additionnel
normalisé et ajoute l'avertissement `PROVIDER_SENTINEL_NORMALIZED`. Le traitement est strict :
`999` reste une incompatibilité de schéma sur un carton, un but, un remplacement ou tout autre
incident. Les marqueurs globaux `period` et `injuryTime` participent à la complétude sans exiger
`isHome`. La migration append-only V10 autorise cette nouvelle provenance sans modifier V8/V9 ni
reclasser les snapshots historiques V2.

Le fournisseur représente un remplacement par deux objets distincts : `playerIn` et
`playerOut`. Le parseur `event-incidents-v4` reprend sans élargissement la règle de sentinelle V3,
puis conserve pour chaque incident `substitution` l'identifiant et le nom du joueur entrant et du
joueur sortant. Chaque identité est atomique : identifiant et nom sont présents ensemble. Si le
fournisseur omet l'un des deux objets, l'incident reste compatible mais la complétude devient
`PARTIAL` avec le chemin manquant ; aucune identité n'est inventée. La migration append-only V11
ajoute ces quatre colonnes facultatives et autorise la provenance V4 sans modifier les
observations historiques.

La déduplication brute est indépendante du parseur courant. Une réponse incidents identique peut
donc résoudre un snapshot V2 historiquement `SCHEMA_INCOMPATIBLE` alors que V4 la parse avec
succès. Dans ce cas, la campagne ne tente ni `UPDATE` ni reclassification du snapshot : elle ajoute
une observation V4 liée à la même preuve et poursuit vers `EVENT_LINEUPS`. Seul un brut
nouvellement inséré reçoit la classification de la campagne courante.

## 6. Résultat et confidentialité

L'interface affiche uniquement le code terminal, le nombre de tentatives et, pour chaque famille
traitée, l'endpoint logique, l'identifiant du snapshot, la taille, le SHA-256, la complétude ou
`UNAVAILABLE · N/A`, et l'identifiant d'observation. Aucun octet brut, URI complète, en-tête ou
texte de confirmation consommé n'est journalisé ou ajouté aux preuves.

La vue locale des incidents affiche séparément le joueur générique, le joueur entrant et le
joueur sortant. Ces valeurs proviennent exclusivement de l'observation normalisée ; un tiret
signifie que le fournisseur n'a pas fourni l'identité correspondante.

## 7. État de qualification

Les formes fournisseur ont d'abord été qualifiées avec des fixtures minimales créées localement.
La première campagne humaine a
montré qu'un HTTP `404` de `EVENT_STATISTICS` peut exprimer une famille indisponible, notamment
dans le contexte opérateur d'une compétition non majeure. Ce signal ne valide pas le schéma
nominal de statistiques ; seule une réponse `2xx` effectivement parsée peut faire évoluer
`PROVIDER_SCHEMA_VALIDATED`. Une campagne ultérieure a effectivement parsé les statistiques de
`16391135`, puis deux réponses incidents réelles distinctes ont révélé la sentinelle de période
rejetée par V2. Un retest humain a ensuite confirmé V3 sur la réponse réelle dédupliquée du
snapshot 32 : 20 incidents sur 20 sont visibles. Il a aussi révélé que la tentative de reclasser
ce snapshot historique arrêtait la campagne avec `RAW_CLASSIFICATION_ERROR` avant
`EVENT_LINEUPS`, ainsi que l'absence de conservation de `playerIn` et `playerOut`. V4, V11 et la
politique de déduplication ont corrigé ces deux défauts. Le retest humain suivant a confirmé le
parcours complet sur `16412917` : statistiques HTTP `404` normalisées en indisponibilité,
incidents parsés par V4 avec joueurs entrant et sortant, puis compositions parsées par V2, après
exactement trois appels sans retry. Il a aussi révélé que `COMPLETED_LOCKED` empêchait à tort de
préparer une campagne distincte pour `16391135` dans la même instance. Le contrôle autorise
désormais ce nouveau départ explicite après un succès, sans ouvrir les verrous d'échec, d'arrêt ou
d'expiration. Le statut global reste `PROVIDER_SCHEMA_VALIDATED=NO` jusqu'au retest humain de ce
cycle de réarmement, au reverrouillage local et à la clôture du Work Order.
