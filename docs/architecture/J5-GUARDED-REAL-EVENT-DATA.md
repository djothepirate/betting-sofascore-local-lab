# J5 — Qualification réelle gardée des données événement

## 1. Statut

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
PROVIDER_SCHEMA_VALIDATED=NO
```

Cette architecture complète le contrat synthétique J5 V1 sans le remplacer. Elle prépare une
campagne humaine unique sur une identité canonique J4 existante. Le développement, Maven et les
fixtures ne contactent jamais SofaScore.

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

Une confirmation produit un claim immuable. Les états terminaux
`COMPLETED_LOCKED`, `FAILED_LOCKED`, `STOPPED_LOCKED` et `EXPIRED_LOCKED` ne permettent aucune
nouvelle préparation dans le même processus.

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
5. le parseur fournisseur V2 produit soit un résultat complet, soit aucun objet normalisé ;
6. la normalisation J5 est liée par `PROVIDER_SNAPSHOT` au snapshot, au hash, au parseur ou
   normaliseur et à l'heure de réception ;
7. un snapshot `2xx` est classé `PARSED` après la persistance normalisée.

Les parseurs sont `event-statistics-v2`, `event-incidents-v2` et `event-lineups-v2`. L'identifiant
d'événement vient du claim et non du JSON. Les champs inconnus génèrent au plus 256 avertissements.
Une liste vide structurellement valide reste `EMPTY_VALID`; une absence facultative mesurée reste
`PARTIAL`. Une famille non publiée en HTTP `404` reste `UNAVAILABLE · N/A`, ce qui est distinct
d'une liste présente et vide. Aucune valeur n'est inventée.

La migration V8 étend uniquement la contrainte de versions de parseur créée par V7. Elle ne
modifie ni V7 ni une observation existante.

La migration append-only V9 ajoute `ENDPOINT_UNAVAILABLE`, `UNAVAILABLE` et les normaliseurs
`event-*-unavailable-v1`. Elle corrige uniquement la classification des anciens snapshots J5
HTTP `404` marqués `TRANSPORT_ERROR/HTTP_STATUS_404`; les octets, hashes, heures et identifiants de
snapshot restent inchangés. Elle ne fabrique pas rétroactivement d'observation normalisée.

## 6. Résultat et confidentialité

L'interface affiche uniquement le code terminal, le nombre de tentatives et, pour chaque famille
traitée, l'endpoint logique, l'identifiant du snapshot, la taille, le SHA-256, la complétude ou
`UNAVAILABLE · N/A`, et l'identifiant d'observation. Aucun octet brut, URI complète, en-tête ou
texte de confirmation consommé n'est journalisé ou ajouté aux preuves.

## 7. État de qualification

Les formes V2 ont été qualifiées exclusivement avec des fixtures minimales créées localement.
Elles ne prouvent pas encore la compatibilité du fournisseur. La première campagne humaine a
montré qu'un HTTP `404` de `EVENT_STATISTICS` peut exprimer une famille indisponible, notamment
dans le contexte opérateur d'une compétition non majeure. Ce signal ne valide pas le schéma
nominal de statistiques ; seule une réponse `2xx` effectivement parsée peut faire évoluer
`PROVIDER_SCHEMA_VALIDATED`.
