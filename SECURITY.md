# Politique de sécurité

## Portée

Le SofaScore Local Lab est un prototype personnel exécuté uniquement sur le poste Windows local. Il n’est ni un service public, ni un connecteur de production, ni une dépendance critique du Betting Project.

## Exposition réseau

- l’application écoute sur `127.0.0.1:8087` ;
- PostgreSQL est publié uniquement sur `127.0.0.1` ;
- aucun reverse proxy, tunnel ou port entrant n’est autorisé ;
- `LocalOnlyBindingGuard` arrête l’application si `server.address` n’est pas une adresse de boucle locale.

## Secrets

Les secrets doivent être fournis par `.env` ou variables d’environnement locales. Sont interdits dans Git, les logs, les captures et les fixtures :

- mots de passe PostgreSQL ;
- cookies et jetons de session ;
- en-têtes d’authentification ;
- clés API ;
- contenus de navigateur ;
- secrets du Betting Project principal.

Le script `Initialize-LocalConfig.ps1` génère un mot de passe PostgreSQL aléatoire sans l’afficher.

## Politique d’accès SofaScore

Au J0/J1, aucun appel n’est possible. Pour les jalons ultérieurs :

- arrêt immédiat sur refus, challenge ou blocage ;
- aucun contournement, proxy rotatif ou changement automatique d’adresse ;
- aucun navigateur automatisé permanent ;
- aucune réutilisation de cookie ou jeton intercepté ;
- appels manuels, cache et concurrence maximale de 1 tant qu’un nouvel ADR n’en décide pas autrement.

## Signalement d’un incident

En cas de fuite de secret ou de commit accidentel :

1. arrêter l’application et les conteneurs ;
2. révoquer ou remplacer le secret ;
3. ne pas se limiter à supprimer le fichier dans un nouveau commit ;
4. nettoyer l’historique Git si nécessaire ;
5. consigner l’incident dans `docs/validation/` ;
6. vérifier les logs, exports et sauvegardes ;
7. reprendre uniquement après validation humaine.

## Données

Les payloads bruts restent locaux, avec rétention configurable. Ils ne doivent pas être transmis automatiquement au Betting Project. Tout futur export contient uniquement des données normalisées, une provenance, une version de schéma, un hash et un manifeste.
