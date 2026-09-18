**Skill à utiliser : `ss-verify`.**

Son périmètre couvre explicitement le diagnostic des builds du **SofaScore Local Lab** et les « faux verts ». Son `SKILL.md` demande de lire les rapports **Surefire/Failsafe**, de distinguer les tests exécutés, les échecs, les erreurs et les tests ignorés (*skips*), et rappelle qu’un code de sortie zéro ou un JAR produit ne prouve pas la qualification. C’est donc le bon routage pour vérifier la cohérence des XML et expliquer un Maven apparemment vert.

**`ss-provider-benchmark` ne s’applique pas** : aucune mesure ni comparaison fournisseur n’est demandée.

Décision de routage uniquement : seul le `SKILL.md` de `ss-verify` a été lu ; aucune tâche métier ni exécution réseau, application, build, DB ou Docker n’a été lancée.