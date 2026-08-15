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

Au premier incident, les étapes restantes ne sont pas exécutées. Sont notamment terminaux : arrêt
opérateur, timeout, erreur I/O, réponse trop volumineuse, contenu sensible, HTTP hors 2xx, contenu
non JSON, JSON ambigu, schéma incompatible et erreur de persistance.

## 5. Pipeline de données

Pour chaque famille :

1. le transport capture les octets et leur SHA-256 ;
2. `provider_snapshot` reçoit la ligne `RAW_ONLY` ;
3. le type HTTP et le JSON sont contrôlés ;
4. le parseur fournisseur V2 produit soit un résultat complet, soit aucun objet normalisé ;
5. la normalisation J5 est liée par `PROVIDER_SNAPSHOT` au snapshot, au hash, au parseur et à
   l'heure de réception ;
6. le snapshot est classé `PARSED` après la persistance normalisée.

Les parseurs sont `event-statistics-v2`, `event-incidents-v2` et `event-lineups-v2`. L'identifiant
d'événement vient du claim et non du JSON. Les champs inconnus génèrent au plus 256 avertissements.
Une liste vide structurellement valide reste `EMPTY_VALID`; une absence facultative mesurée reste
`PARTIAL`; aucune valeur n'est inventée.

La migration V8 étend uniquement la contrainte de versions de parseur créée par V7. Elle ne
modifie ni V7 ni une observation existante.

## 6. Résultat et confidentialité

L'interface affiche uniquement le code terminal, le nombre de tentatives et, pour chaque famille
réussie, l'endpoint logique, l'identifiant du snapshot, la taille, le SHA-256, la complétude et
l'identifiant d'observation. Aucun octet brut, URI complète, en-tête ou texte de confirmation
consommé n'est journalisé ou ajouté aux preuves.

## 7. État de qualification

Les formes V2 ont été qualifiées exclusivement avec des fixtures minimales créées localement.
Elles ne prouvent pas encore la compatibilité du fournisseur. Seule la campagne humaine définie
par `WO-SS-20260815-006` peut faire évoluer `PROVIDER_SCHEMA_VALIDATED`.
