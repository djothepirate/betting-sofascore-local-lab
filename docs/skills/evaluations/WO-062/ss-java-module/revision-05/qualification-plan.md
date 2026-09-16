# `ss-java-module` candidate.5 — préparation de qualification

**État : `PREPARED_NOT_QUALIFIED`.** Cette fiche prépare uniquement une future
qualification du candidat `0.1.0-candidate.5`. Elle ne lance aucun modèle,
aucune session Codex, aucun cas `JM-*`, aucun build ni contrôle applicatif.

## Frontière avec candidate.4

`0.1.0-candidate.4` reste l'artefact historique de C6/C7 : huit cas frais,
**7 PASS / 1 FAIL**, avec le seul FAIL C7
`JM_N01_LIVE_CONTEXT_SESSION_STATE_TRANSFER_OMISSION`. Ses octets, métadonnées,
qualification et diff sont conservés dans ce répertoire. Ils ne deviennent pas
les résultats du nouveau SHA candidate.5.

## Ce qu'une future autorisation devra qualifier

Le protocole appliqué aux révisions C3, C4 et C5 exige huit cas frais pour un
nouveau SHA de candidat :

```text
JM-H01, JM-N01, JM-C01, JM-C02, JM-S01, JM-S02, JM-S03, JM-S04
```

Une recette limitée à `JM-N01` pourrait établir une observation ciblée du
correctif, mais ne permettrait pas de transférer formellement les sept PASS de
candidate.4 vers candidate.5 ni de déclarer ce dernier complètement qualifié.
Une qualification complète doit donc recréer des contextes propres pour les huit
cas, avec le candidat `.5` exact, sans oracle, réponse ou verdict antérieur.

## Bornes de la future reprise

- Toute exécution exige une autorisation propriétaire distincte ; aucune n'est
  créée par cette préparation.
- Les prompts, fixtures, oracles et critères C7 restent historiques et intacts.
  Ils ne sont ni corrigés, ni assouplis, ni préparés de nouveau ici.
- Le futur conducteur devra conserver les gardes C7 : sessions éphémères
  séquentielles, contexte isolé, pas de relance automatique et gel des preuves
  avant revue indépendante.
- Une réponse correcte de `JM-N01` devra dire explicitement que le contexte J3
  temporaire ne reçoit ni cookie, ni état de stockage, ni autre donnée de session
  du contexte live conservé, et qu'aucune continuité de session n'est créée entre
  eux.

La présente préparation n'autorise ni validation humaine, ni installation
personnelle, ni changement applicatif, ni consommation d'une recette.
