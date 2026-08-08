# Fixtures — réservé au jalon J2

Les répertoires sont créés au J1 mais ne contiennent volontairement aucun payload SofaScore.

```text
fixtures/
├── scheduled-events/
├── event-details/
└── schema-breaks/
```

Chaque fixture future devra contenir ou être accompagnée de :

- endpoint logique ;
- date d’observation ;
- statut HTTP et type de contenu ;
- SHA-256 du contenu original ;
- version du parseur ;
- origine et conditions de conservation ;
- liste des champs supprimés lors de la minimisation ;
- absence vérifiée de cookie, jeton, identifiant de session et secret.

Les tests doivent rester reproductibles sans connexion à la source externe.
