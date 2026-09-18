## Skills retenus

J’ai retenu **deux skills** du catalogue disponible et lu **intégralement leur fichier `SKILL.md` avec un outil**.

### 1. `ss-java-module` — skill principal

**Chemin effectivement lu :**

[C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-S02/.agents/skills/ss-java-module/SKILL.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-S02/.agents/skills/ss-java-module/SKILL.md)

**Raison de la sélection :** la demande porte directement sur l’architecture Java du SofaScore Local Lab : répartition des responsabilités, cartographie des classes et dépendances, composition Spring/Maven et proposition d’un ajustement minimal. Ce skill couvre explicitement l’admission J3 durable, les conditions d’activation du runtime, les relations avec la persistance et la frontière du worker. Il distingue également compilation du runtime, production du JAR worker et lancement effectif, ce qui correspond à l’analyse demandée des profils et du démarrage.

### 2. `ss-postgres-change` — complément ciblé sur la persistance durable

**Chemin effectivement lu :**

[C:/Users/geoff/.agents/skills/ss-postgres-change/SKILL.md](C:/Users/geoff/.agents/skills/ss-postgres-change/SKILL.md)

**Raison de la sélection :** la demande inclut explicitement la responsabilité de la persistance et de l’admission durable. Ce skill apporte les critères utiles à cette partie de la revue : état durable, frontières transactionnelles, idempotence, concurrence, rollback et reprise. Sa sélection ne présuppose pas une migration SQL ; son apport porte ici sur la revue des responsabilités de persistance et des garanties à préserver. `ss-java-module` le désigne aussi explicitement comme relais pour la persistance.

## Limites du routage

- **`ss-verify` n’est pas retenu à ce stade** : aucune exécution de build, de tests ou de qualification n’est demandée ; l’analyse des profils et de la composition relève de `ss-java-module`.
- **`ss-data-contract-replay` n’est pas retenu** : aucune évolution de contrat, de parseur ou de replay n’est demandée.
- Les autres skills du catalogue ne correspondent pas au périmètre annoncé.

**Arrêt au routage.** Je n’ai exécuté aucune analyse métier, cartographié aucune classe ni proposé de modification. Aucun oracle, résultat antérieur, inventaire commenté ou plan d’évaluation n’a été lu.