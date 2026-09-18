# WO-062 — Revue finale du delta de synthèse C

**PASS de cohérence ; aucune correction demandée.** Les verdicts des candidats
restent inchangés et les deux qualifications restent incomplètes.

- Delta examiné : `3f75849` → `e74f1b2ca08af1b11db991ff8a9655510cdc51fd`.
- Périmètre : rapport C, WO, README, guide des skills, changelog et CHECKS JSON,
  rapprochés des résultats et revues Windows run-02 publiés.
- État Git suivi propre ; détail et empreintes dans [le JSON](c-final-summary-review.json).

## Constats

1. **Compteurs cohérents :** 26 tentatives historiques, 25 réponses finales et un
   dossier de session expirée ; historique 18 PASS / 4 FAIL / 4 BLOCKED. Les 16 cas
   courants donnent 13 PASS / 1 FAIL / 2 BLOCKED. Windows reprend six PASS run-01
   et les deux BLOCKED run-02 ; les six copies préparées non exécutées sont exclues.
2. **WR-H01 correctement distingué :** unique harnais réussi, code 0 en
   2 726,6811 ms et nettoyage de cette reproduction établi. La session expire en
   900,594 s sans réponse finale ni fin de tour ; collecteur 1 et verdict BLOCKED.
   Le README produit dans le cas ne remplace pas la réponse manquante. La cause
   historique WO-053 reste non établie.
3. **WR-N01 correctement borné :** code 23 observé au relais javapath détenu
   (PID 34004), différent de la JVM imprimant PID 26528. Arrêt avant `sleep` ;
   aucune expiration de 1 500 ms ni terminaison forcée qualifiée. Le journal
   `JavaLauncher.log`, 4 892 octets, reste conservé ; nettoyage intégral non acquis.
4. **Observation hôte distincte :** la photographie de 22:11:02 UTC confirme les
   quatre PID absents à cet instant, H vide et résidu N conservé. Le rapport la
   présente hors essais, sans réparation rétroactive des handles, de la réponse
   manquante ou des verdicts.
5. **Statuts humains respectés :** qualification incomplète, validation humaine C
   et installation personnelle non réalisées ; consolidation D et livraison restent
   distinctes. Les dix sessions autorisées sont consommées, sans résultat futur anticipé.
6. **CHECKS JSON concordant :** compteurs des quatre runs et codes/durées des deux
   nouvelles sessions Windows identiques aux résultats et traces publiés. Le record
   distingue tests simulés du dispositif, réutilisation des tests applicatifs et limites
   des contrôles locaux ; il n'annonce aucune nouvelle qualification applicative.

## Limites

Cette revue porte sur la fidélité des synthèses aux pièces publiées, sans réexaminer
les oracles ou les anciennes revues et sans changer les verdicts. Les anciens résultats
JSON ont seulement servi au rapprochement des compteurs. Aucune nouvelle session,
sonde, inspection de processus, lecture du journal résiduel, compilation ou exécution
applicative. Seuls ces deux rapports sous `.tmp` sont écrits. L'audit Git des octets et
manifestes finaux reste celui du coordinateur.
