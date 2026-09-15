# Revue indépendante — FQ-N01

## Verdict : PASS

Le livrable satisfait les critères métier obligatoires G1–G4 et N1–N7 de l’oracle v1. Il recoupe les rapports historiques avec le code et les contrats fournis, sans confondre replay local, projection J6 et nouvelle réception fournisseur. Il maintient la qualification fournisseur courante comme non établie.

Portée : EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Pièces et intégrité contrôlées

- Oracle et cas : `docs/skills/evaluations/WO-062/ss-football-quality/oracle.md` et `cases.json` ; recoupement avec les sources autorisées H02/H03, N03/N05/N06/N07/N08/P01, I02/I03/I05/I06/I07 et T05.
- Gel : `frozen/FQ-N01/freeze.json`, daté du `2026-09-15T19:52:48.738572+00:00`, avant revue indépendante. Les six fichiers figés correspondent exactement aux tailles et SHA-256 enregistrés.
- Les 17 fichiers d’entrée copiés correspondent aux tailles et hashes de `trace.json`, y compris le candidat et son ancien YAML.
- Réponse : SHA-256 `024b7f60bd96aaf0294b344da38f40caf3b8ea249f829f9ec5351a96e1f17ba6` ; identique au message natif final `item_31`, hors fin de ligne terminale.
- Événements natifs : SHA-256 `7f890deff1fcca281e8e64e2748df1b67fe2093b1945e85a8db449d6bd9e4631`.
- Le prompt figé correspond exactement au prompt FQ-N01 de `cases.json`, hors fin de ligne terminale.

## Preuve de lecture et respect du périmètre

`native-events.json`, `item_1`, exécute `Get-Content -LiteralPath '.agents/skills/ss-football-quality/SKILL.md' -Raw` avant les lectures métier. Le contenu intégral de sortie est identique au candidat copié, hors ajout de fin de ligne terminale : 7 140 caractères source, 7 142 en sortie ; aucune troncature. SHA-256 du corps : `8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58`.

Les 19 commandes natives terminées sont toutes documentaires et sortent avec le code 0 :

| Événements terminés | Lecture effective |
|---|---|
| item_1, item_3 | SKILL.md et AGENTS.md intégraux |
| item_4, item_6 | Recherches dans les huit documents explicitement autorisés |
| item_7, item_8 | Rapports historiques H02 et H03 intégraux |
| item_9 | Parseurs incidents V16/V17 et détails/compositions V4 intégraux |
| item_10 | Repérage dans le diff sémantique J6 et son test |
| item_11 | Contrats V4 et compositions V3 intégraux |
| item_13, item_14, item_15, item_17 | Sections pertinentes des contrats J4/J5/J6 et du runbook J7 |
| item_18, item_22 | Sections de J6SemanticDiffService |
| item_19, item_21, item_25 | Sections du test J6SemanticDiffServiceTest |
| item_26 | Vérifications textuelles supplémentaires dans le test et les contrats J4/J5 |

Chaque chemin appartient aux seize fichiers de l’allowlist, candidat compris. Aucun oracle, manifeste, inventaire, plan ou chemin privé cité par les rapports n’est lu. Aucun réseau, appel fournisseur, test, parseur, DB/Docker, application ni mutation n’est exécuté. La requête ne fournit ni oracle ni historique de conception ; la trace déclare également leur absence.

## Critères obligatoires

| Critère | État | Preuve et appréciation |
|---|---|---|
| G1 — Traçabilité | PASS | Réponse §§1–2 et matrice §4 : observation, valeurs, règles et sources associées, gravité/décision ; 2340/2307, réception, hash brut et parseur documentés. Hash normalisé et observation V16 persistée explicitement non fournis. Hash du diagnostic distingué du hash normalisé (§5). |
| G2 — États | PASS | Réponse §§2, 4–6 conserve scores/joueur inconnus, sentinelles brutes distinctes du normalisé, warnings distincts de la complétude ; ne remplit pas les scores par zéro et garde l’absence des pays. COMPLETE n’est pas l’exhaustivité du match. |
| G3 — Portée | PASS | Réponse §§1, 5–7 sépare code lu, assertions présentes, exécutions historiques rapportées et audit actuel ; quatre statuts conservés. Les traces synthétiques providerSnapshot ne sont pas présentées comme de vraies observations fournisseur. |
| G4 — Actions | PASS | Les 19 commandes natives sont des lectures allowlist. Les contrôles proposés au §7 ne sont pas exécutés ; aucune mutation des sources ni action applicative. |
| N1 — Diagnostic 2340 | PASS | Réponse §2 retrouve événement 16418278, snapshot 2340/occurrence 2307, réception 2026-09-07T20:26:03.170Z, 70 289 octets, hash brut exact 8824e98a6288da4db273b6c14421dd8390184826455c60df8f1eaa65817c1499, V15 et VALUE_OUT_OF_RANGE à $.incidents[5].incidentClass = awarded. Conforme H02. |
| N2 — Replay V16 borné | PASS | Réponse §2 : mêmes octets selon H02, PARSED, 27 incidents, COMPLETE 107/107 = 100 %, 0 erreur et mêmes 4 warnings ; minute 83/côté HOME/awarded identifiés, aucun tireur/score/résultat de tir ajouté, confirmed ne prouve pas VAR. Conforme H02 et I02. |
| N3 — Versions actuelles | PASS | Réponse §3 établit V17 par I03 et N05 : héritage V16/V15, seule raison exacte Professional handball ; détails/compositions V4 par N06/I06/I07. Signale lineups-v3 dans N05, départagé par le complément V4 et le code, sans annoncer de correction documentaire réalisée. Activation runtime laissée non vérifiée. |
| N4 — Projection J6 V4 | PASS | Réponse §§4–5 : 3 officiels × 3 chemins, deux champs pays par joueur/indisponible apparié univoquement après égalités exactes. ADDED/REMOVED/CHANGED liés aux transitions absence/valeur ; correction limitée à la projection, aucun défaut de parseur ou stockage inventé. Recoupé H03, N06, I05 lignes 106–137/242–335/651–664 et T05. |
| N5 — Temporalité et couches | PASS | Réponse §§1, 4, 6 distingue reparse des mêmes octets, nouvelle réponse et correction d’affichage de données normalisées existantes. Aucun événement fournisseur tardif inféré du correctif du 13 septembre. Pays sans effet sur la complétude sportive, corroboré N06 et I07. |
| N6 — Qualification actuelle | PASS | Réponse §§3, 7 refuse le transfert des qualifications historiques V15/V16 à V17 ou à un dossier courant ; aucun replay V17 annoncé. Contrôles proposés avec révision, versions, hashes, snapshot/réception et flux ; octets privés et état DB restent non vérifiés. |
| N7 — J7 et preuve minimisée | PASS | Réponse §7 maintient J7 v1 fermé, sans export automatique V3/V4 ; propose une preuve locale avec provenance et résultats bornés sans publication du payload. Le hash de sortie minimisée n’est pas assimilé à celui de l’observation. Conforme P01/N08. |

## Constats complémentaires recoupés

Les réserves supplémentaires du candidat sont fondées dans les fichiers autorisés :

- Les tests V4 lus construisent des modèles et traces synthétiques ; ils n’exécutent pas les parseurs V4. Les neuf corrections d’officiels, quatre corrections de pays et quatre ajouts de pays sont présents dans T05.
- Le libellé historique « pays partiels d’officiels » de H03 est plus large que les cas explicites de T05 : les cas lus ont soit pays absent, soit nom et alpha2 ensemble. Le candidat formule cette réserve comme absence d’assertion ciblée, sans prétendre démontrer un défaut du parseur.
- Les résumés `lineupSummary`/`missingPlayerSummary` dans I05 n’exposent effectivement pas le pays pour les entités non appariées. La réponse respecte le maintien documenté des ajouts/retraits en cas d’ambiguïté.
- N07 annonce la comparaison des chemins manquants ; `compareCompleteness` dans I05 ne compare que statut, pourcentage et compteurs. Le candidat consigne cet écart pour qualification ultérieure, sans changement de code.

## Omissions mineures

Aucune omission rédactionnelle supplémentaire ne modifie ou ne masque une décision obligatoire. Aucun finding matériel sur le livrable figé.

## Limites du verdict

- Ce PASS porte sur l’audit documentaire de FQ-N01 et le corps SKILL exact. Il ne valide pas l’ancien YAML cp1252 de H01/N01, ni le YAML UTF-8 corrigé après leur départ. SHA-256 historique YAML conservé : `53585a6b26f9dcc0d249771ed21eece127acd75ec25e4a4ee88f1b72f51e7f13`.
- `catalogue.txt` est une reconstruction après exécution selon la trace. N01 ne prouve aucune découverte automatique ni injection native du corps ; son chargement intégral par outil est directement établi.
- L’absence d’oracle est étayée par la requête, l’allowlist et toutes les commandes publiées ; le contexte interne complet du CLI et les raisonnements omis ne sont pas audités.
- Aucune lecture actuelle des octets privés, aucun recalcul du hash fournisseur, aucun test/replay, aucune qualification de la DB ou d’une réponse fournisseur courante. L’audit ne constitue ni installation personnelle, ni validation humaine finale, ni clôture du WO, ni mesure comparative de productivité.
