# Revue indépendante C6 — JM-H01

**Verdict : PASS pour ce seul cas.** ss-java-module candidate.4 a livré une revue
bornée et sourcée de WO-030. Cette décision ne vaut ni qualification complète du
candidat, ni validation humaine, ni installation personnelle.

## Intégrité et périmètre

- La session C6 est la seule autorisée : un thread et un tour se terminent normalement
  (exit_code=0, 306.656 s, 18 commandes), sans expiration ni relance.
- Les douze fichiers gelés avant la revue, leur copie publiée et les quatorze entrées
  de runtime correspondent tous à leurs tailles et SHA-256 déclarés. La réponse finale
  a l’empreinte
  6dbfb8e8c3afba4e7777bc5c9c7f1890b62a28a74753094bb77186391af593b9.
- Le candidat .4 est lu intégralement en première commande locale et correspond à
  f1f6533f678c5cdb4ae6a5bc9c996c062af419005e4f4cd6104a51c91e442d97.
- L’enveloppe restait limitée au candidat, à input.json et aux onze sources autorisées.
  Aucun oracle, réponse antérieure, plan ou preuve C5 n’a été transmis. Les commandes
  achevées sont des lectures locales de cette liste ; aucun build, test, application,
  réseau, navigateur, base, Docker ou mutation n’est observé.

Le client a journalisé une tentative de routage de collaboration échouée. Les événements
natifs attestent toutefois un seul thread et un seul tour, sans enfant créé ni seconde
session consommée.

## Décision sémantique

| Attente JM-H01 | Évaluation |
| --- | --- |
| Défaut 65536 et deux gardes | La réponse explique le port positif mais invalide, nomme isSafeConfiguration() et parseOrigin(), et maintient les deux validations indépendantes. |
| Parent, enfant et matrice | Elle décrit le passage par environnement et la défense enfant avant navigation ; absent (-1), 0 et 65536 sont refusés, 1 et 65535 seulement acceptés structurellement. |
| Périmètre non changé | Elle garde les six familles, le port IPC et ScheduledEventsTransportRequest hors du delta WO-030. |
| Identité historique | Elle distingue précisément la base daf55bf76521f81893f86d04fde3c2903bf22362 du commit qualifié 154349a2fbebe3fd0a43a63c7105f690ff04976b. |
| Contrôles réellement exercés | Elle sépare prédicat direct, Bean Validation, claimExecution() et la configuration worker en mémoire, avec les résultats de claim requis. |
| Historique et POM actuel | Elle conserve les commandes offline comme preuve datée, ne transfère pas leurs totaux, explique la sélection integration-tests actuelle et restitue explicitement que le profil sofascore-live-test reste bloqué. |
| Incident et gouvernance | L’accès sandbox au JAR Maven reste un incident d’environnement avant tests ; la chronologie rapport autonome puis validation propriétaire et ADR-SS-007 courant est cohérente. |
| Suite minimale | Les deux contrôles de bornes et le claim sont proposés pour une future révision via ss-verify, sans les annoncer exécutés. |

La réponse conserve les limites utiles : elle ne déduit pas le câblage Spring complet,
l’ordre d’ouverture actuel du worker, une exécution de navigateur ou une autorisation
réseau à partir du corpus restreint. Les sujets contrat/replay et migration restent
dirigés vers leurs skills spécialisés.

## Limites restantes

Sept cas Java de candidate.4 restent NOT_RUN. Les résultats .3 restent historiques.
Aucun résultat C6 ne déclenche un build applicatif, une recette fournisseur, une validation
humaine ou une installation personnelle.
