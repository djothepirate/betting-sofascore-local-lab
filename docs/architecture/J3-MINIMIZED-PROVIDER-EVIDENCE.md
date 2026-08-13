# J3 — preuve fournisseur minimisée et verrou terminal

## 1. Objet

Cette unité complète le chemin fournisseur cinq pages sans exécuter elle-même de requête réelle.
Après l’unique action volontaire du propriétaire, l’application rend disponible une preuve copiable
et téléchargeable qui permet de qualifier le résultat sans exposer la réponse fournisseur.

Le brut demeure exclusivement dans `provider_snapshot`. La preuve n’est pas une copie du brut et
ne permet pas de le reconstruire.

## 2. Métadonnées admises

La preuve `J3_MINIMIZED_EVIDENCE_VERSION=1` contient uniquement :

- la date de qualification et les numéros de pages effectivement tentées ;
- l’état terminal, le nombre de pages terminées, la première page en échec et un code sûr ;
- les instants de requête et de réception, le statut HTTP et la latence ;
- l’identifiant local du snapshot, le résultat `INSERTED` ou `DEDUPLICATED`, la taille et le
  SHA-256 des octets conservés ;
- le statut final du parseur ou de la politique d’incident ;
- l’état final de l’arrêt global et du circuit ;
- les déclarations d’absence de retry et de données de session.

Elle exclut par construction :

- les octets ou le JSON du payload ;
- l’URI fournisseur et les en-têtes HTTP ;
- les cookies, jetons, comptes et données de session ;
- l’identifiant interne de confirmation et sa phrase à usage unique ;
- les détails d’exception, journaux ou diagnostics de base de données.

## 3. Verrouillage terminal

Après `COMPLETED` ou `FAILED`, l’orchestrateur réapplique automatiquement l’arrêt global et place
le circuit dans l’état suivant :

```text
state=LOCKED
reason=QUALIFICATION_TERMINAL_LOCK
```

Ce motif est distinct de `OPERATOR_STOP`. L’état terminal de l’intention et son éventuel code
d’incident restent conservés dans la preuve. Le réarmement est refusé dans le même processus car la
qualification a déjà été consommée. Un redémarrage ne constitue jamais une autorisation de refaire
l’appel.

## 4. Collecte locale

Une fois la requête Web terminale revenue, le tableau de bord affiche la preuve en texte brut et
propose `GET /manual-call/evidence` pour télécharger exactement ce texte. La réponse porte
`Cache-Control: no-store`.

La preuve est conservée seulement en mémoire applicative et doit donc être téléchargée avant
l’arrêt du processus. Le payload brut demeure en PostgreSQL et ne doit être copié ni dans Git, ni
dans un chat, ni dans un rapport.

## 5. Validation hors ligne

Les tests emploient exclusivement des transports factices ou `MockRestServiceServer`. Ils
contrôlent les cinq pages, l’arrêt au premier incident, l’échec avant snapshot, le verrou terminal,
l’impossibilité de réarmer, la minimisation du texte et son téléchargement. Aucun test standard ne
contacte SofaScore.

```text
J3_MINIMIZED_EVIDENCE=IMPLEMENTED
J3_RAW_PAYLOAD_IN_EVIDENCE=NO
J3_PROVIDER_URI_IN_EVIDENCE=NO
J3_HEADERS_IN_EVIDENCE=NO
J3_SESSION_DATA_IN_EVIDENCE=NO
J3_TERMINAL_GLOBAL_STOP=AUTOMATIC
J3_TERMINAL_CIRCUIT=LOCKED
J3_TERMINAL_REARM=SAME_PROCESS_FORBIDDEN
J3_IMPLEMENTATION_NETWORK_CALLS=0
```
