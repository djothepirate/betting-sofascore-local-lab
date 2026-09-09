# Campagnes live — priorité à la résilience face aux refus fournisseur

Statuts : EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

**État : cadrage proposé le 9 septembre 2026, pas une modification du comportement runtime.**
Le propriétaire demande de prioriser la robustesse face aux blocages d'accès avant
toute nouvelle réduction des délais de rafraîchissement. Cette priorité est retenue.
Les paramètres, états et algorithmes ci-dessous sont à qualifier dans un lot dédié ;
ils ne révisent pas implicitement les décisions de transport et de cadence existantes.

Rattachement : [WO-058](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md).
Base de lecture : `f2b28eca276e49476425ee0a12ed7bdda54d32df`, worktree WO-058.
La copie Eclipse contient en plus l'évolution opérateur du plafond à trente secondes,
confirmée pour `3362612a` par le lanceur et le timeout mesuré à 30,081630 s.

## 1. Ce que les essais démontrent

La qualification synthétique du Lab démontre une capacité technique de planification.
Les campagnes réelles à 19–20 rencontres ont échoué avant cinq minutes, sur un 403
ou des timeouts. Le navigateur opérateur a aussi constaté un 403 sur SofaScore depuis
les connexions utilisées. L'acceptation durable de cette charge par le fournisseur
n'est donc pas établie ; la capacité loopback ne peut pas en tenir lieu.

Ces observations ne déterminent ni un seuil officiel de requêtes ni la règle exacte
de blocage. Le volume, sa distribution temporelle et la réputation du réseau sont
des hypothèses à distinguer. Un timeout ne prouve pas un 403, et un 403 ne renseigne
pas la durée d'une restriction.

Pour vingt rencontres toutes en jeu, le nominal actuel coûte :

`20 × (3 / 100 + 1 / 300) × 60 = 40 appels/minute`.

J4, incidents et statistiques comptent chacun ; les compositions portent aussi
les statistiques individuelles. Initialisation et finalisation s'ajoutent à ce régime.
Ce calcul n'est pas une limite autorisée par SofaScore.

Dernière campagne `3362612a` : 19 cibles, 136 appels autorisés, 135 réceptions,
102 résultats PARSED, 33 HTTP 404 et un timeout. Le dernier résultat arrive à
256,643 s du lancement, puis l'arrêt global est enregistré à 257,578 s.
Les 19 cibles sont arrêtées, sans plafond épuisé. Aucun régime établi à qualifier.

| Mesure hors réseau du dernier essai | Observation |
|---|---|
| Maximum sur 10 secondes glissantes | 18 autorisations, toutes en initialisation, pour cinq matchs |
| Maximum sur 60 secondes glissantes | 62 autorisations : 42 en initialisation et 20 suivantes |
| Répartition du lancement au dernier résultat | 64 appels d'initialisation, 72 autres avant cinq minutes |
| Statistiques de match | 29 HTTP 404 sur 31 appels, dont 17 répétitions après un 404 du même match |
| Corps inchangés dans cette campagne | 45 paires sur 52 réceptions PARSED successives du même match et de la même famille |

Les fenêtres sont définies par `t − W < authorized_at ≤ t`, pour chaque autorisation
observée `t`, à la microseconde. Ce sont des autorisations enregistrées, pas une mesure
du débit sur le réseau. Le pic de 10 secondes se termine le 9 septembre à
13:37:25,963520 Europe/Paris ; celui de 60 secondes à 13:38:12,699912. Ces pics
incluent le chevauchement de l'initialisation avec les tours suivants et ne décrivent
pas un débit permanent. La sélection initiale comprend 13 matchs en jeu et six en
prématch : son nominal ordinaire est de 33,2 appels/minute, hors transitions.

L'annexe sur les cinq campagnes `08c3cd3d`, `e7e7684b`, `857c2cc7`, `ef4f028f`
et `3362612a` totalise 531 autorisations, dont 115 appels de statistiques avec
110 HTTP 404. Aucun de ces essais n'atteint cinq minutes avant son dernier résultat.
La sélection, les états sportifs et les conditions réseau diffèrent : ce corpus ne
constitue pas une comparaison contrôlée du VPN, des timeouts ou d'un seuil de blocage.

Les métadonnées minimisées et diagnostics restent dans les dossiers locaux ignorés
`.tmp/*-cadence` et `.tmp/WO058-*-STOP-DIAGNOSIS-20260909.md`. Le calcul reproductible
est conservé dans `.tmp/resilience-study-20260909/analyze.cjs`, avec résultats,
définition des fenêtres et SHA-256 des captures sources dans `summary.json` du même
dossier. La revue du transport est conservée dans `transport-review.md`.

## 2. Garanties présentes à conserver

- Transport séquentiel, au plus une requête en vol ; contrôle du groupe, de la route,
  de l'identité et des budgets avant départ.
- Un contexte navigateur neuf par campagne manuellement lancée, sans conservation
  de session. Le worker vise un GET document exact ; il ne charge pas une page complète
  du site avec toutes ses ressources secondaires.
- Pas de retry automatique ; arrêt global sur 403, 429, timeout ou erreur technique,
  conservation des preuves et fermeture contrôlée de la session.
- La perte de preuve temporelle verrouille déjà la garde de départ dans l'instance
  du superviseur. Le verrou de propriété SQL reste acquis si le nettoyage échoue.
- Snapshots, occurrences, observations normalisées et consultation locale séparés.
  Un rafraîchissement de l'interface ne déclenche pas une nouvelle collecte fournisseur.

L'arrêt après refus n'est pas une anomalie à supprimer. Il faut améliorer la prévention,
la précision du diagnostic, la conservation du motif et le comportement entre campagnes.
La déduplication effectuée après réception économise du stockage, pas des requêtes réseau.

## 3. Ordre de réalisation proposé

### Lot A — rendre chaque arrêt explicable et durable

Étendre les diagnostics bornés du worker et du protocole IPC : attente de navigation,
réception des en-têtes, lecture du corps, fin de transport, publication locale.
Reporter le statut HTTP dès qu'il est connu et corrélé à la requête autorisée, même
si le corps ne finit pas d'arriver. Aujourd'hui le parent ne reçoit ce statut qu'après
`response.body()` ; un timeout peut effacer cette distinction.

Enregistrer seulement des identifiants, enums, heures, durées, statut numérique connu
et le `Retry-After` éventuellement reçu après validation stricte. Ne pas journaliser
de payload, URL complète, cookies, jetons, en-têtes génériques ou message d'exception brut.
Conserver séparément « statut reçu » et « réponse complète sauvegardée » : aucune
création de snapshot fictif ou normalisation d'un corps partiel.

Persister la cause primaire, l'étape, la tentative et le timeout effectif, puis les
afficher après nettoyage et redémarrage. La configuration de lancement doit être
lisible sans confondre timeout de démarrage, timeout de requête et plafond accepté.
Distinguer également le timeout du worker de celui de l'attente IPC du parent :
les IOException du parent convergent actuellement vers PROTOCOL_ERROR, tandis que
le worker transmet TIMEOUT. Les historiques non renseignés restent explicitement inconnus.

### Lot B — réduire et plafonner la pression commune au fournisseur

Ajouter des budgets temporels de départ partagés par les parcours autorisés J3/J4/J5,
en complément des plafonds de volume existants : fenêtres courtes, minute et heure.
Appliquer la limite au départ effectif autorisé, avec contrôle de concurrence et
preuve persistée ; changer de campagne ou redémarrer ne doit pas remettre le budget à zéro.

Étaler les initialisations et les requêtes d'un groupe selon ce budget. Absorber une
file retardée sans rafale de rattrapage : reporter ou supprimer une échéance obsolète,
en gardant la dette et la perte de fraîcheur visibles. Mesurer départs, attente de file,
latence réseau et durée totale séparément. Ne pas augmenter le parallélisme pour
compenser une indisponibilité fournisseur.

Les nouvelles limites chiffrées doivent être choisies et testées comme paramètres
locaux conservateurs ; les erreurs observées ne révèlent aucun seuil fournisseur.
Si la charge ne tient plus dans ce budget, réduire la sélection admise ou espacer
les familles moins prioritaires explicitement, avec une nouvelle politique versionnée.
Ne pas continuer à annoncer 100 s si l'ordonnancement choisi ne peut plus les tenir.

### Lot C — éviter les interrogations sans information disponible

Pour les 404 J5 répétés, porter une indisponibilité temporaire par événement/famille,
avec délai croissant borné avant le prochain échantillonnage et réévaluation aux
transitions sportives. Une réponse disponible restaure progressivement le rythme
consenti. J4 continue de suivre sa propre règle ; son 404 conserve la revue requise.
Tester aussi l'apparition tardive des données pour ne pas figer une absence.

Préserver les distinctions absent, indisponible, vide valide et zéro. Une famille
différée n'est pas fraîche ; son âge et sa prochaine échéance restent affichés.
Les compositions confirmées ne sont pas immuables : le bloc lineups alimente les
statistiques des joueurs pendant le match, donc sa collecte ne peut pas être supprimée
sur le seul critère confirmed=true.

L'utilisation des observations locales peut éviter une collecte initiale redondante
si une politique de réutilisation explicite et bornée l'autorise. Les données réutilisées
gardent leur réception d'origine ; aucune nouvelle occurrence fournisseur n'est fabriquée.
Un mécanisme HTTP conditionnel éventuel devra être examiné séparément : il peut réduire
les octets, mais chaque 304 reste un appel réseau et aucun support fournisseur n'est supposé.

### Lot D — protéger durablement après refus et conserver une consultation utile

Porter un état de santé fournisseur persistant, distinct de la garde de propriété des
processus. Le retour de cette garde à FREE après nettoyage ne signifie pas que l'accès
fournisseur est rétabli. Un 403 confirmé ouvre une suspension commune aux campagnes
et parcours manuels ; un nouvel UUID ou un redémarrage ne doit pas effacer le motif.

Un 429 peut imposer une échéance minimale à partir de `Retry-After`. Son expiration
ne réarme pas automatiquement une campagne. Un timeout sans statut connu reste un
incident technique incertain, pas un bannissement affirmé. Le constat 403 effectué
dans le navigateur peut être consigné comme déclaration opérateur distincte.

La réactivation reste une décision opérateur traçable ; pas de sonde périodique cachée,
recréation automatique du navigateur ou répétition automatique de la requête fautive.
Conserver le comportement conservateur de fermeture après timeout jusqu'à une étude
distincte démontrant qu'une continuation est sûre. Changer seulement la portée
CAMPAIGN en EVENT laisserait le transport partagé dans un état non démontré.

L'interface continue de présenter les dernières données disponibles, avec indication
claire du refus, de la suspension et de l'ancienneté. Une nouvelle heure d'affichage
ne remplace pas l'heure de réception ; une panne fournisseur ne supprime pas l'historique.

**Séquencement :** A d'abord ; protection durable de D avant le prochain essai réel ;
B et C ensemble pour obtenir une baisse mesurable des départs ; complément d'affichage D.

## 4. Critères de validation du lot

| Scénario local/replay | Résultat vérifiable |
|---|---|
| 403 aux en-têtes, corps retardé ou interrompu | Statut connu conservé, corps incomplet explicite, aucune normalisation ni requête suivante |
| Timeout avant tout en-tête | Étape connue, statut inconnu, pas de 403 inventé |
| 200 avec corps lent/incomplet | Attente distinguée du parsing ; même délai global borné, nettoyage vérifié |
| 429 avec Retry-After valide, absent ou invalide | Borne minimale interprétée sans réarmement automatique ; valeur invalide sans extrapolation |
| Nouvelle campagne, redémarrage, deux demandes concurrentes | Budget global et suspension fournisseur persistants ; aucun départ non admis |
| Dix-neuf/vingt cibles, initialisation, reprise de file, fin de match | Bornes de départ respectées sur toutes les fenêtres ; pas de rafale compensatrice |
| 404 J5 répétés puis données disponibles | Moins d'appels sur la famille absente, apparition tardive détectée dans la borne choisie |
| Joueur capitaine et statistiques dans lineups | Aucune perte de données dynamiques causée par une composition déjà confirmée |
| Lecture UI et réutilisation d'une observation | Aucun départ fournisseur, provenance/âge d'origine conservés |
| Panne SQL, crash worker, annulation et reprise locale | Cause primaire durable, issue inconnue conservée si nécessaire, aucune réémission automatique |

Les tests standards restent sans fournisseur. Toute migration nouvelle est append-only,
qualifiée sur PostgreSQL réel avec montée de version sur données préremplies et concurrence.
Les essais natifs utilisent le serveur loopback et le worker de production.

Une future observation fournisseur nécessite un accès redevenu disponible et un lancement
opérateur. Définir à l'avance une charge et une durée bornées, puis évaluer appels utiles,
refus, latences, âge des données et complétude. Une durée technique réussie en loopback
ne valide pas l'acceptation fournisseur ; une seule campagne sans erreur ne promet pas
l'absence de blocage à une autre charge ou une autre heure.

## 5. Décisions et limites

Le [cadre actuel](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) fixe la cadence,
le rééchantillonnage 404 à chaque cycle et l'absence de cache J4/J5. Les lots B/C et
les nouvelles règles de suspension devront être formalisés avant activation avec
version de politique et preuves propres ; les anciens manifestes restent inchangés.
La présente proposition n'ouvre aucun endpoint, transport, polling ou essai réel.

L'objectif est de limiter les causes évitables de refus et leur impact. L'architecture
ne peut pas garantir un accès que le fournisseur refuse. Si la charge nécessaire reste
incompatible avec cet accès, il faudra examiner une source dont l'accès automatisé est
explicitement permis, dans un cadrage séparé ; aucune offre/API SofaScore n'est présumée.

## 6. Références techniques consultées

- [RFC 9110 §15.5.4 — 403](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.4) : refus, sans explication obligatoire de sa cause.
- [RFC 6585 §4 et §7.2 — 429](https://www.rfc-editor.org/rfc/rfc6585.html#section-4) : limitation de débit, Retry-After éventuel ; un serveur peut aussi interrompre des connexions.
- [RFC 9110 §10.2.3 — Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3) : attente exprimée comme délai ou date.
- [Playwright Java —Request](https://playwright.dev/java/docs/api/class-request) : événements requête, réception des en-têtes, fin ou échec de la requête.

Revue documentaire et calculs hors réseau fournisseur uniquement. Aucun changement de
code, migration, lanceur Eclipse, cadence ou état de campagne au titre de ce cadrage.
