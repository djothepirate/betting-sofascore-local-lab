# WO-062 — Revue statique du candidat ss-java-module

- **Verdict : PASS — aucun défaut matériel relevé.**
- Version : `0.1.0-candidate.1`.
- Révision relue : `d89f9f2f3da136faf00976266202a4ff9b8c8c1f`.
- Date : 15 septembre 2026.
- Relecteur : `java_prepare`, auteur de la préparation mais pas du candidat.
- Portée : revue statique ciblée avant essais, distincte d’une session comportementale
  isolée de l’oracle.

## Constats

La description vise les évolutions Java du Lab et distingue le simple build et les autres
dépôts. Les métadonnées sont cohérentes ; le prompt d’exemple invoque le nom exact.
Le texte conserve le monomodule et décrit les interfaces selon leur rôle réel ; il rend
visibles les dépendances concrètes sans imposer une architecture idéale ou un découpage
en cinq modules.

Les règles du parcours J3 sont cohérentes avec l’exception adoptée : ordres durables,
contexte temporaire dans le worker live et contexte live conservé. Elles ne créent pas
d’autorisation de nouveau transport, de fallback, de réseau fournisseur ou de lancement
dans les tests standards. Aucune nouvelle confirmation n’est ajoutée à une autorisation
J3 déjà acquise.

Les distinctions de preuve sont utiles et correctes : composition @Primary versus test
Spring réduit, source de test versus inclusion effective, suites standards versus recette
native, historique versus révision courante. Le garde textuel ne devient pas un contrôle
exhaustif du graphe et ArchUnit n’est pas présumé installé.

Les chemins du dépôt et les noms de classes sont retrouvables depuis le worktree. Les
consignes laissent le choix de la proposition minimale et relaient contrat/replay et
persistance vers les compétences appropriées. Aucun défaut concret justifiant une
correction du candidat avant les essais n’a été relevé.

## Identité des fichiers

| Fichier | Octets | SHA-256 |
| --- | ---: | --- |
| SKILL.md | 8306 | `d20c8b838a9c4b9713e27c576e22272427d3f64fef50a670f5a09364cf849eb4` |
| agents/openai.yaml | 309 | `ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae` |

Manifeste de préparation inchangé :
`a0907baba5a15606b6a735873a3e8ab9cc4043dfe8d852f3b843e1ec61b68290`.

## Limites

Cette revue s’appuie sur le corpus figé, le contrat C1 du WO et les principes de
skill-creator, avec lecture des deux fichiers candidats. Elle ne relance pas l’inventaire
complet. Aucun cas comportemental, sélection native, test applicatif, Maven, navigateur,
worker ou accès à la base n’a été exécuté. Le PASS statique ne vaut pas qualification
du skill, validation humaine ou autorisation d’installation.

Seuls ce compte rendu et son fichier JSON sont écrits dans le répertoire de run
préalablement créé par le lanceur. Candidat et préparation restent inchangés.
