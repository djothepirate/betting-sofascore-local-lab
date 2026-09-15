## Route de la Pull Request

- [ ] Work Order `feature/<TRAIN>-(CODEX|HUMAN)-<WO complet>` vers le train
  `feature/<TRAIN>` identique.
- [ ] Train final `feature/<TRAIN>` vers `main`, avec version Maven finale exacte et merge commit.
- [ ] Bootstrap unique WO-055 vers `main` sur la base exacte `054fa4c`, avec tête descendante et
  Maven `0.1.0-SNAPSHOT`.

Source : `<!-- branche source exacte -->`

Cible : `<!-- branche cible exacte -->`

Version Maven : `<!-- X.Y.Z[-SNAPSHOT], X.Y.Z-rc.N ou X.Y.Z-rc.N-SNAPSHOT selon le train -->`

## Work Orders couverts

<!-- Lister chaque identifiant complet et indiquer si sa clôture devient effective par cette fusion. -->

## Preuves

- [ ] Diff relu et sans secret.
- [ ] Vérification locale proportionnée consignée avec sa commande et son résultat.
- [ ] Checks GitHub Windows et Linux verts sur le HEAD courant.
- [ ] Rapports XML de tests contrôlés et conservés ; toute autre preuve exigée par le WO est disponible.
- [ ] Un éventuel avertissement concerne uniquement un export documentaire facultatif, aucun test ni preuve exigée.
- [ ] Revue humaine terminée et fils actionnables résolus.
- [ ] Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
  `NO_CRITICAL_DEPENDENCY` restent inchangés.
- [ ] Aucun appel fournisseur, payload brut, secret, déploiement VPS ou autorisation de production.

La présence d'un Work Order dans `completed` au sein du diff décrit un candidat. Sa clôture ne
devient effective qu'après fusion de cette PR dans son train feature cible.
