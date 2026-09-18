# Revue indépendante — FQ-H01

## Verdict : PASS

Le livrable satisfait les critères métier obligatoires G1–G4 et H1–H6 de l’oracle v1. La preuve instrumentée établit la lecture intégrale du corps exact du candidat, puis des seules sources autorisées. Aucun test, parseur, accès fournisseur, réseau, DB/Docker, démarrage d’application ou changement de source n’apparaît dans les 13 commandes exécutées.

Portée : EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Pièces et intégrité contrôlées

- Oracle et cas : `docs/skills/evaluations/WO-062/ss-football-quality/oracle.md` et `cases.json` ; recoupement avec H01, N04, I01, T01 et F01 copiés dans le dossier isolé FQ-H01.
- Gel : `frozen/FQ-H01/freeze.json`, daté du `2026-09-15T19:50:45.702003+00:00`, avant revue indépendante. Les tailles et SHA-256 des six pièces correspondent au gel : catalogue, événements natifs, prompt, requête, réponse et trace.
- Les huit fichiers d’entrée copiés correspondent aux tailles et hashes de `trace.json`, y compris SKILL.md et l’ancien `agents/openai.yaml`.
- Réponse : SHA-256 `3b73b308dd89b45666d6266044ada558dfef273fe49a97e20be8569fb27ceb70` ; identique au dernier message natif `item_23`, hors fin de ligne terminale.
- Événements natifs : SHA-256 `1433a400a755f1aad12d450acece5546f61a743dd6cc8bfefe48e5f35e7228a8`.
- Le prompt figé correspond exactement au prompt FQ-H01 de `cases.json`, hors fin de ligne terminale.

## Preuve de lecture et respect du périmètre

`native-events.json`, `item_1`, exécute `Get-Content -LiteralPath '.agents/skills/ss-football-quality/SKILL.md' -Raw` avant toute lecture métier. La sortie complète est identique au fichier copié hors ajout d’une fin de ligne terminale : 7 140 caractères source, 7 142 en sortie ; aucune troncature. SHA-256 du candidat : `8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58`.

Les 13 commandes terminées sont toutes des lectures, avec code de sortie 0 :

| Événements terminés | Lecture effective |
|---|---|
| item_1 | SKILL.md intégral |
| item_3, item_11, item_17 | WO-017 : recherche, lecture numérotée et complément §§13–14 |
| item_4, item_12 | Règles incidents : recherche, §§1–3 et §10.2 |
| item_5, item_8, item_9, item_15, item_16 | V15 et tests ciblés : recherche puis contenus intégraux |
| item_6 | AGENTS.md intégral |
| item_10 | Fixture F01 intégrale |

Tous les chemins appartiennent à l’allowlist explicite. Aucun chemin d’oracle, plan, manifeste ou inventaire n’est lu par le candidat ; aucune lecture supplémentaire n’est déclenchée par les liens des documents. Les anciens ordres de campagne inclus dans H01 restent des données historiques. La requête ne fournit ni oracle ni historique de conception ; la trace déclare également leur absence.

## Critères obligatoires

| Critère | État | Preuve et appréciation |
|---|---|---|
| G1 — Traçabilité | PASS | Réponse §§1, 4–7 : matrice observation/règle/provenance/gravité/décision, références W/R/P/T/F, événement, snapshot 717, occurrence 686, observation 324 et V15. Hash brut/normalisé et réception réelle laissés explicitement inconnus. Horodatage de test distingué de la réception réelle. |
| G2 — États | PASS | Réponse §3 distingue propriété absente, [] et JSON null/types invalides ; conserve les minutes inconnues et PARTIAL. Aucun zéro ou temps fabriqué. Compatibilité et exhaustivité sont séparées. |
| G3 — Portée | PASS | Réponse §§1, 4–5, 7 : rapport historique, contrat, assertions lues et observations réellement effectuées séparés ; quatre statuts conservés ; aucune qualification actuelle revendiquée. |
| G4 — Actions | PASS | Les 13 commandes natives sont documentaires ; la réponse ne transforme ni le rapport ni le go consommé en autorisation. Aucune action applicative ni mutation. |
| H1 — 189/717 et causalité | PASS | Réponse §2 retrouve 189/propriété absente/V14 lisible et 717/14 tableaux vides/rejet V14. Exclut explicitement causalité J8 et généralisation fournisseur. Recoupé avec H01 §2. |
| H2 — Exception fermée V15 | PASS | Réponse §3 énumère PEN unique inactif, FT/ET, séance entièrement sans minute, classes/côtés/scores, séquences positives uniques contiguës et égalité du score terminal. null/types invalides, non-vide incohérent et séance mixte restent refusés. Conforme N04 §10.2, I01 et T01. |
| H3 — Comptes réels | PASS | Réponse §§4–5 : 35 incidents ; 164/179 ; PARTIAL 91 ; 18 warnings ; 15 minutes absentes = 14 tirs + PEN. Distingue 18 warnings et 15 signaux manquants. N’invente aucune minute ; 999 et séquence exclus explicitement. Conforme H01 §§9/13. |
| H4 — Fixture | PASS | Réponse §4 : F01 = 4 incidents et 3 absences temporelles ; T01 vérifie 3 warnings du code temporel et le rejet V14. Les paramètres 717/événement/réception et le hash des octets synthétiques ne prouvent pas le brut réel de 35 incidents. |
| H5 — Sonde et qualification | PASS | Réponse §5 distingue sonde non persistante, portes techniques et campagne distincte de 3 appels. Retrouve incidents 717/686/324, V15 et nouvelle observation append-only ; maintient classement V14 et fenêtre J8 ; go consommé. Conforme H01 §§9–14. |
| H6 — Limites actuelles | PASS | Réponse §§1, 7 : aucune lecture actuelle du payload privé, aucun hash réel inventé, aucune nouvelle qualification ni autorisation d’appel. |

## Omissions mineures

Les substituts interdits `120` et `0` ne sont pas nommés séparément dans la réponse. Cela ne masque aucune décision : l’interdiction de reconstruire une minute est générale, l’absence normalisée et le rendu `—` sont maintenus, et aucune valeur de remplacement n’est produite. Observation rédactionnelle seulement, sans échec de H3.

## Limites du verdict

- Ce PASS porte sur le traitement documentaire de FQ-H01 et le corps SKILL exact. Il ne valide pas l’ancien YAML cp1252 utilisé pour H01/N01, ni le YAML UTF-8 corrigé ultérieurement. Le hash historique du YAML reste traçable : `53585a6b26f9dcc0d249771ed21eece127acd75ec25e4a4ee88f1b72f51e7f13`.
- `catalogue.txt` est une reconstruction après exécution, explicitement signalée par la trace. H01 ne constitue donc pas une preuve de découverte automatique ou d’injection CLI. La lecture outil complète du candidat est, elle, directement prouvée.
- L’absence d’oracle est étayée par la requête, l’allowlist et toutes les commandes publiées ; le contexte interne complet du CLI et ses raisonnements non publiés ne sont pas audités.
- Aucune nouvelle qualification des octets privés, de la DB, des parseurs exécutés ou de l’état actuel du fournisseur. Aucun gain de productivité, validation humaine finale, installation personnelle ou clôture de WO n’est établi par cet essai.
