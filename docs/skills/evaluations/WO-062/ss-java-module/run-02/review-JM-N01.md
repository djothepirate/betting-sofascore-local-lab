# Revue indépendante — JM-N01 — run-02

## Verdict : FAIL — contenu obligatoire incomplet

La réponse fournit une carte utile, des dépendances exactes, des preuves bien délimitées et deux constats supplémentaires fondés dans le code. Trois précisions obligatoires de l'oracle inchangé restent absentes. Le verdict porte sur cette complétude ; aucune décision technique inverse, preuve inventée, action hors périmètre ou anomalie du collecteur n'a été observée.

### Omissions bloquantes

1. **Instants UTC.** Les lignes 30–38 séparent correctement le déclencheur de la provenance des pages, puis la date `LocalDate` des champs d'exécution. Elles décrivent aussi les horaires `Europe/Paris`. Elles n'indiquent pas que les instants sont UTC ou indépendants du fuseau civil. L'oracle exige cette dimension ; l'ADR autorisé, lignes 99–102 et 165–168, ainsi que les classes de données l'exposent. La distinction date/temps est donc présente mais incomplète sur ce point précis.
2. **Perte du contexte live.** Les lignes 129–156 décrivent le même worker, le contexte J3 neuf, l'exclusion des émissions et le retour au contexte live initial après fermeture. Elles ne restituent pas la terminaison sans reprise ni recréation après perte du navigateur ou du contexte live. La règle de panne est distincte du retour nominal ; elle est disponible dans l'ADR, lignes 74, 348 et 385. Aucune recréation n'est recommandée : il s'agit d'une omission.
3. **Cible exacte du garde textuel.** La ligne 230 reconnaît correctement un contrôle non exhaustif, mais la formule « certains usages Playwright » ne restitue ni l'interdiction `com.microsoft.playwright` ni sa portée `src/main`. Le script distingue ce contrôle, lignes 17–28, d'autres motifs dans `src/provider-playwright`, lignes 115–131. L'oracle demande cette portée précise. La réponse n'invente cependant ni ArchUnit ni couverture globale.

Les preuves lues pendant la session ne complètent pas automatiquement la réponse finale. Aucune réponse, source ou instruction candidate n'a été corrigée par le relecteur.

## Identité et preuve native

- Candidat `0.1.0-candidate.2`, commit `2d62f12c2d2ad4571c630aea0f98128927aa24ff`.
- SHA-256 candidat : `255655544771ae6cf2a71b967aba8efeb7e53e64acc41ca31dc784e16a719118`.
- SHA-256 réponse : `795fd863934d2eae50d0c30706167d842d3c5aaeaf3d30ec696a1580255f311c`.
- SHA-256 événements : `3076f535441cd250c59bb8aaefe84fb70b834eee25f1dfe3f5a0008fe06ead1b`.
- Oracle inchangé : `d13dc8c5ec833f30dc9025c3c22a22c00e60656eae3a89dc30361861ea390bd1`.
- Source métier : `6648dd423e556b5248b8793a539ae85a7680f9bc`, distincte du commit candidat.
- Gel observé avant lecture de la réponse : `2026-09-15T21:45:26.359275+00:00`. Neuf pièces figées conformes en taille et empreinte ; 39 entrées conformes au preflight, dont 36 sources autorisées.
- `item_1` : lecture native `Get-Content -Raw`, code 0, dont la sortie contient exactement le texte intégral du candidat `.2`. Les 25 commandes terminées consultent exclusivement le corpus autorisé.
- CLI et collecteur : code 0, diagnostics vides. Aucun incident d'infrastructure ne justifie un BLOCKED.

## Matrice des critères

Les lignes de réponse renvoient à `frozen/JM-N01/response.md`. Les exigences et limites individuelles complètes figurent dans `review-JM-N01.json`.

| Critère | Verdict | Preuve / limite |
|---|---|---|
| Gel, identité, lecture native et corpus | PASS | Neuf preuves et 39 entrées conformes ; lecture exacte du candidat ; analyse seule et allowlist respectées. |
| Contrôleurs et consultation | PASS | Lignes 52 et 61 : commandes/jeton/validation, ports réels, aucun GET d'acquisition ni détour obligatoire inventé. |
| Valeurs et dimensions métier | FAIL | Déclencheur/source et date/temps distingués ; précision UTC omise. |
| Runtime et stockage | PASS | Lignes 53–60, 89–102, 123–136 : leadership, réconciliation, sériel, ports/Jdbc, standalone/live. |
| Exécuteur et publication | PASS | Lignes 106–119 et 164–190 : pages 1–35, preuves, fermeture préalable, transaction réelle, dernier succès et limites J8. |
| Factory, superviseur et IPC | PASS | Lignes 69–77, 127–156, 208 : interfaces réelles, concret du décorateur, ownership, worker séparé. |
| Socle, profils et démarrage | PASS | Lignes 18, 91–97, 197–210 : monomodule, Java25, Boot4.1, wrapper, loopback, runtime/flags/profils distincts, outputs et worker isolés. |
| Autorité J3 et cycle de vie | FAIL | ADR actuel et retour nominal corrects ; règle d'arrêt après perte du contexte non restituée. |
| Dépendances concrètes | PASS | Ligne 81 : catalogue, parser et exception d'adaptateur tous explicitement nommés ; aucun port inventé ni refonte générale. |
| IT et modes d'exécution | PASS | Lignes 223–236 : quatre IT, PostgreSQL réel, doubles fournisseur et threads distingués d'une qualification navigateur. |
| Protocole et qualification native | PASS | Lignes 222, 229, 238–250 : mémoire/Chromium distingués ; motif d'inclusion exact, sélection explicite et goals Failsafe ; SQL/admission hors preuve native. |
| Spring et ArchUnit | PASS | Lignes 208–213, 221, 252 : contexte réduit distinct du graphe complet ; aucune couverture globale inventée. |
| Garde Verify-Local | FAIL | Limite textuelle reconnue, mais cible `com.microsoft.playwright` et périmètre `src/main` omis. |
| Risques, propositions et relais | PASS | Lignes 266–325 : corrections ciblées futures, preuves à établir, trois relais spécialisés exacts. |
| Faux vert et gouvernance de l'essai | PASS | Statique déclaré, anciens résultats datés, aucun système ni test métier exécuté, aucune qualification actuelle attribuée. |

## Vérification des constats supplémentaires

**Import en attente.** Le constat des lignes 270–277 découle réellement de `J3RuntimeService.manual` : `imports.put` remplace avant la vérification durable ; `JdbcJ3AutomationStore.manual` rejette un hash différent ; le `catch` retire la clé. Si l'ordre original n'a pas encore récupéré ses octets, il rencontre ensuite `J3_IMPORT_BYTES_UNAVAILABLE`. Cette déduction conditionnelle est fondée sur les sources, sans reproduction nouvelle. La proposition reste locale et demande un scénario dédié.

**Terminal J8 après panne.** Les lignes 183–190 reflètent le repli avec `audit=null`, la portée de `interrupt` et de `committedProof`. Le test existant `terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether`, lignes 145–157, attend effectivement un ordre `FAILED`, le catalogue antérieur conservé et zéro terminal J8. La réponse distingue ce fait d'une éventuelle réconciliation ultérieure hors corpus. Elle ne transforme pas une simple couverture manquante en défaut certain.

## Limites

Les modèles et efforts effectifs restent non exposés. Le catalogue est une reconstruction déclarée après exécution ; seule la lecture réelle par outil est attestée. Les champs bruts de la trace portent `+00:00` : début `2026-09-15T21:35:25.700954+00:00`, fin `2026-09-15T21:45:25.245892+00:00`, durée 599,547 s. Une conversion locale PowerShell ne change pas ces données brutes.

Ce FAIL ne qualifie ni une régression applicative ni un runtime exécuté. Le premier run et les sorties figées demeurent intacts. Aucune correction du candidat ou de la réponse, aucune exécution applicative par le relecteur. Gain global de temps/qualité non mesuré.
