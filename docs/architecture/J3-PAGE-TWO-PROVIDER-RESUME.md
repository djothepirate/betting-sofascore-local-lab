# J3 — Reprise fournisseur contrôlée à la page 2

## 1. Objet

Cette unité prolonge la qualification réelle du 2026-08-13 après son arrêt sûr sur la page 1.
Elle permet une seule reprise explicite sur les pages `2, 3, 4, 5`. Elle ne répète pas la page 1 et
n’introduit pas encore une interrogation fournisseur répétable depuis l’interface.

Le développement et les tests de cette unité restent entièrement hors ligne. L’appel réel demeure
une action humaine ultérieure, exécutée depuis le bouton final du tableau de bord.

## 2. Checkpoint local obligatoire

La disponibilité du transport configuré ne suffit plus. Avant d’exposer la reprise, la politique
`J3QualificationResumePolicy` relit PostgreSQL et impose toutes les conditions suivantes :

1. la configuration J3 spécialisée est explicitement activée ;
2. un seul snapshot existe pour `SCHEDULED_EVENTS|date=2026-08-13|page=1` ;
3. aucun snapshot n’existe encore pour les pages 2 à 5 de cette qualification ;
4. les octets bruts, leur taille et leur SHA-256 sont cohérents ;
5. le statut HTTP conservé est un succès `2xx` ;
6. `scheduled-events-v1` reparcourt localement ces octets avec le résultat `PARSED` ;
7. la page 1 annonce `hasNextPage=true`.

Une base indisponible, une page absente ou dupliquée, une incohérence d’intégrité, une incompatibilité
de parsing ou une page 2 déjà conservée produit un blocker sûr. Aucun de ces cas ne déclenche une
requête fournisseur.

Le statut historique `SCHEMA_INCOMPATIBLE` du snapshot 1 n’est pas réécrit : la décision de reprise
repose sur une nouvelle relecture locale par le parseur corrigé, sans altérer la preuve d’origine.

## 3. Intention et séquence

L’intention autorisée est exclusivement :

```text
DATE=2026-08-13
VERIFIED_LOCAL_CHECKPOINT=PAGE_1
PROVIDER_FIRST_PAGE=2
PROVIDER_LAST_PAGE=5
REQUEST_KEY=SCHEDULED_EVENTS|date=2026-08-13|pages=2-5
CONFIRMATION_SCOPE=REPRISE_PAGES_2_5
```

Après levée de l’arrêt global et activation distincte du circuit, l’opérateur doit recopier une
phrase éphémère mentionnant explicitement `REPRISE PAGES 2-5`, puis utiliser une action finale
séparée. L’orchestrateur initialise le compteur à `1` grâce au checkpoint, demande exactement les
pages 2 à 5 dans l’ordre, maintient la concurrence à `1` et attend au moins trois secondes entre
deux départs de page.

Il s’arrête au premier incident, n’effectue aucun retry et réapplique le verrou terminal. Une page 2
présente après cette action interdit une nouvelle reprise au redémarrage, que l’action ait réussi ou
qu’elle se soit arrêtée sur incident.

## 4. Persistance et preuve

Aucune migration n’est ajoutée. Le port de checkpoint ne fait que relire les snapshots bruts déjà
conservés par Flyway V2. Les pages 2 à 5 suivent le contrat existant : persistance des octets avant
parsing, classification déterministe et arrêt sûr.

La preuve minimisée passe en version `2` et distingue :

```text
VERIFIED_LOCAL_CHECKPOINT_PAGES=1
PROVIDER_RESUME_FIRST_PAGE=2
PAGES_ATTEMPTED=2,3,4,5
PAGES_COMPLETED=5
```

Elle n’inclut toujours ni payload brut, ni URI, ni en-tête, ni identifiant de confirmation, ni donnée
de session.

## 5. Hors périmètre

Cette unité n’autorise pas :

- une nouvelle requête de page 1 ;
- une répétition de la reprise après présence de la page 2 ;
- une reprise automatique après incident ;
- une pagination découverte depuis les réponses ;
- une date différente de `2026-08-13` ;
- un polling, une planification ou un rafraîchissement automatique ;
- la future interrogation complète répétable depuis l’interface.

Cette dernière capacité constituera une unité distincte, avec son propre modèle d’intention, sa
politique de fréquence et ses critères de qualification.
