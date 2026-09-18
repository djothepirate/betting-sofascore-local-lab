# Complément opératoire de finalisation WR-H01

La demande, le candidat, les sources et les critères métier restent ceux du cas figé.
Ce complément borne l'organisation de la session ; il ne fournit aucun résultat attendu.

Viser une session complète en moins de six minutes, avec une marge nette avant le
watchdog de quinze minutes. La conduite native conserve strictement ses bornes propres.
Travailler dans cette seule session, sans sous-agent ni tâche secondaire.

1. Lire intégralement le candidat, puis les entrées utiles. Dans les gros fichiers,
   rechercher les fonctions effectivement appelées par le harnais et lire ces sections ;
   éviter les relectures intégrales et les sorties répétées.
2. Construire une conduite compacte qui conserve l'objet Process de son enfant,
   les arguments, le runtime, les sorties, le code immédiat et les durées. Les observations
   auxiliaires ne doivent pas remplacer les preuves de propriété des ressources créées.
   Exécuter le harnais une seule fois ; toute impossibilité produit un constat final.
3. Conserver les preuves nécessaires dans un résultat JSON, les sorties natives et le
   script exécuté. Marquer dans un petit journal UTC les étapes EXECUTION_START,
   EXECUTION_END, EVIDENCE_READY, POSTFLIGHT_COMPLETE et CONCLUSION_READY.
4. Faire le postflight indépendant demandé, lire une fois les résultats pertinents,
   puis conclure. Les preuves absentes restent explicitement absentes.
5. Rendre immédiatement une réponse finale autonome et concise (environ 700 à 1 000
   mots au maximum, moins si suffisant), couvrant résultat, comparaison historique,
   portée, limites et liens aux artefacts. Ne pas créer de README doublonnant cette
   réponse, de manifeste de livraison, de revue du skill ou de checks supplémentaires.

Le lanceur prend en charge le gel, les empreintes et l'enregistrement de la réponse
finale après la session. Il mesure séparément les événements et la collecte. Aucun
verdict positif n'est requis : une réponse finale explicite reste nécessaire en cas
de blocage. Ce complément n'autorise aucun nouveau runtime, réseau ou changement de source.
