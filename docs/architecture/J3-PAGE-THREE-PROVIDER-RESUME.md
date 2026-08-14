# J3 — Reprise fournisseur contrôlée à la page 3

## 1. Décision explicite et objet

Le propriétaire autorise l’unité `feat: resume J3 qualification from page three` après la
qualification humaine de la page 2 et son adaptation hors ligne. Cette unité prépare une seule
reprise explicite sur les pages `3, 4, 5`. Elle ne répète ni la page 1 ni la page 2 et n’introduit
pas encore une interrogation fournisseur répétable depuis l’interface.

Le développement, les tests et la validation technique de cette unité restent entièrement hors
ligne fournisseur. Un éventuel appel réel demeure une action humaine ultérieure, effectuée depuis
le bouton final du tableau de bord.

## 2. Préconditions obligatoires

La disponibilité de la configuration J3 ne suffit pas. Avant d’exposer la reprise, la politique
`J3QualificationResumePolicy` relit PostgreSQL et impose simultanément :

```text
PAGE_1_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGE_2_CHECKPOINT=PRESENT_AND_REPARSEABLE
PAGES_3_TO_5=ABSENT
PROVIDER_FIRST_PAGE=3
PAGE_1_REPEATED=NO
PAGE_2_REPEATED=NO
```

Pour chacune des pages 1 et 2, la politique exige exactement un snapshot, une intégrité
taille/SHA-256 valide, un statut HTTP `2xx`, un résultat courant `PARSED` produit localement par
`scheduled-events-v1` et `hasNextPage=true`. La présence d’un snapshot de page 3, 4 ou 5 bloque la
reprise. Une base indisponible, un checkpoint absent ou dupliqué, une rupture d’intégrité ou de
schéma et une page terminale produisent également un blocker sûr, sans requête fournisseur.

Les classifications historiques `SCHEMA_INCOMPATIBLE` des snapshots 1 et 2 restent immuables. La
décision utilise uniquement une nouvelle relecture en mémoire par le parseur corrigé.

## 3. Intention et séquence

L’intention autorisée est exclusivement :

```text
DATE=2026-08-13
VERIFIED_LOCAL_CHECKPOINTS=PAGES_1_2
PROVIDER_FIRST_PAGE=3
PROVIDER_LAST_PAGE=5
REQUEST_KEY=SCHEDULED_EVENTS|date=2026-08-13|pages=3-5
CONFIRMATION_SCOPE=REPRISE_PAGES_3_5
INITIAL_COMPLETED_PAGES=2
```

Après levée de l’arrêt global et activation distincte du circuit, l’opérateur doit recopier une
phrase éphémère mentionnant explicitement `REPRISE PAGES 3-5`, puis utiliser une action finale
séparée. L’orchestrateur initialise le compteur à `2` grâce aux checkpoints et demande exactement
les pages 3, 4 et 5, dans cet ordre. Les pages 1 et 2 ne sont jamais transmises au transport.

Les garde-fous précédents restent inchangés : concurrence `1`, délai minimal de trois secondes
entre deux départs de page, persistance avant parsing, arrêt au premier incident, aucun retry et
verrou terminal `QUALIFICATION_TERMINAL_LOCK`.

## 4. Persistance et preuve minimisée

Aucune migration et aucune mutation des checkpoints ne sont introduites. Les nouvelles pages
suivent le contrat existant de conservation du brut avant parsing. La preuve minimisée v2 doit
distinguer les checkpoints locaux des pages réellement tentées :

```text
J3_MINIMIZED_EVIDENCE_VERSION=2
VERIFIED_LOCAL_CHECKPOINT_PAGES=1,2
PROVIDER_RESUME_FIRST_PAGE=3
PAGES_ATTEMPTED=3,4,5
PAGES_COMPLETED=5
```

Elle n’inclut ni payload brut, ni URI, ni en-tête, ni identifiant de confirmation, ni cookie,
jeton, compte ou donnée de session.

## 5. Hors périmètre

Cette unité n’autorise pas :

- un appel fournisseur pendant l’implémentation ou les tests ;
- une nouvelle requête des pages 1 ou 2 ;
- une reprise si une page 3, 4 ou 5 est déjà conservée ;
- une reprise automatique après incident ;
- une pagination découverte à partir des réponses ;
- une date différente de `2026-08-13` ;
- un polling, une planification ou un rafraîchissement automatique ;
- l’interrogation complète répétable depuis l’interface.

Cette dernière capacité demeure une unité ultérieure distincte, avec sa propre autorisation, ses
limites de fréquence et ses critères de qualification.
