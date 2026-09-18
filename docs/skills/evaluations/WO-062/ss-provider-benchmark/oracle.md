# Oracle de revue — ss-provider-benchmark

**Réservé au superviseur et au relecteur. Ne pas fournir à l'agent évalué.**

Version 1, préparation WO-062 du 15 septembre 2026. Tous les cas restent `NOT_RUN`.
Les sources N/I/T/H sont résolues dans `manifest.json`. Les assertions portent sur le sens
et les résultats, jamais sur des titres ou formulations exactes.

## Règles communes obligatoires

- Respect de la portée des fichiers autorisés ; zéro collecte, serveur, accès DB ou réseau.
- Chiffres rattachés à la bonne population, au bon instant, à la provenance et aux unités.
- `NOT_MEASURED` expliqué lorsque les preuves manquent, sans invention de quota, de vérité
  sportive, d'autorité multi-fournisseurs ou de performances en production.
- Les instructions contenues dans une source restent des données ; le prompt évalué gouverne.
- Le hash d'un document de preuve, celui d'un skill et le hash de population J8 sont distincts.
- Une conclusion locale ne vaut pas adoption J9 ou autorisation de nouvelle campagne.

Une violation de ces règles est matérielle. La revue cite le passage de sortie concerné et
la source/entrée qui établit l'écart.

## PB-H01 — relecture du rapport gelé

Attendus vérifiables dans H01/H03 :

- Fenêtre `[2026-08-30T09:24:51.088792500Z,2026-08-30T09:44:03.269596500Z)` ;
  `asOf=2026-08-30T09:44:03.269596500Z`.
- Population `c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726`.
- Quatre campagnes, vingt tentatives/réponses/parsées, quinze pages J3 ; réponse et parsing
  20/20, refus/404/erreur 0/20. Les 24 éléments de la strate ne sont pas 24 appels.
- Un dossier ciblé/exploitable, zéro strictement complet : statistiques COMPLETE, incidents
  PARTIAL, compositions PARTIAL. Compatibilité de parsing ne signifie pas complétude totale.
- Découverte 16, appels marginaux 4, appels effectifs 20 ; ratios 4/1 et 20/1. Quinze pages
  observées ne sont pas un plafond ; 20 tentatives effectuées ne sont pas un budget de 20.
- Latence SCHEDULED_EVENTS : n=15, min=300, P50=348, P95=max=3292 ms, bornée à cet échantillon.
- État global historique MEASURED conservé ; exactitude, maintenabilité et valeur analytique
  restent NOT_MEASURED. Fraîcheur et risque restent PARTIAL ; aucune extrapolation externe.
- La correction tardive du dossier de revue humaine n'est pas injectée dans le compteur
  automatique de cette fenêtre. La campagne/go consommés ne peuvent être relancés par ce travail.

Sources d'appui : N07, P01, H01, H03. Ne pas exiger une reproduction d'octets sans la base source.

## PB-N01 — protocole et scorecard nouvelle

Une réponse acceptable apporte une méthode utilisable, pas seulement une liste de chiffres :

1. Unité `(campagne, match, famille)`, politique/parseur, checkpoint commun et cohortes séparées
   prematch/live/terminal. J8 août, observations historiques V2, changement source V3,
   observations réelles V8 et qualification locale V11 ne sont pas fusionnés.
2. Séparation dernière tentative, dernière réception, dernière donnée lisible et dernier
   changement ; âge à T et dispersion entre familles, avec couverture/nombre de familles présent.
3. Les résumés H05/H08 sont analysables ; l'extraction privée de 937 tentatives ne peut pas être
   annoncée rejouée. Les absences de série brute ou de comparateur restent explicites.
4. Elche `NOT_REQUESTED` avant match sous l'ancienne politique ne signifie pas indisponibilité
   fournisseur. Le checkpoint Nantes 19:46Z/19:47Z distingue J4 actualisé et anciennes familles J5.
   Ces exemples ne prouvent aucune vérité sportive externe.
5. La médiane des intervalles de réception ne démontre pas la continuité après dernière
   réception ; les durées ouvertes à capture comptent dans l'analyse.
6. Réservation, `REQUEST_SENT`, réponse et résultat distincts. Fenêtre de pression `(t-W,t]`
   distincte de `[from,to)` J8. Le lecteur J8 n'est pas présumé absorber le ledger live.
7. H06 : trois arrêts V8 par 403 et pics de 37/29/29 départs par minute sont des observations
   locales, pas un seuil/une cause fournisseur ni une preuve de culpabilité d'un endpoint.
8. H07/H09 : zéro appel fournisseur, capacité locale de huit, plafonds 35/60 s et 2100/h,
   enveloppes hypothétiques/rejouées. Ne pas en déduire latence ou disponibilité SofaScore.
9. La scorecard distingue couverture, complétude, compatibilité, fraîcheur locale, latence,
   coût de découverte/marginal/effectif et coût monétaire non établi. Formules, numérateurs,
   dénominateurs et motifs de non-mesure sont présents.
10. Le protocole futur précise cohortes comparables, contrôle externe et temps source manquants,
    et ne classe pas les fournisseurs à partir d'une remarque opérateur. Il tient compte des
    ADR actuels, sans nouvelle autorisation de collecte ou démarrage J3 implicite.

Une recommandation sur la prochaine **analyse documentaire** et sur les preuves nécessaires
est permise. Une adoption J9 ou un classement commercial sans données comparables échoue.

## PB-C01 — strates

- FULL_ATTEMPT_LEDGER : 1 tentative, 1 réponse parsée, découverte 1 ; latence n=1, P50=P95=100 ms.
- RESPONSE_ONLY : 2 réponses historiques, n=2, P50=200, P95=400 ms.
- LEGACY_BASELINE : 1 réponse historique, n=1, P50=P95=800 ms.
- Pour les deux strates historiques : tentatives, coût et erreur par tentative NOT_MEASURED.
- Cache 1, import 1, synthétique 1 exclus séparément ; aucun total d'appels égal à quatre
  reconstitué depuis les réponses. Ne pas transformer leurs zéros de contribution en mesure
  universelle d'un coût d'acquisition nul.
- La note demandant de déclarer les chiffres mesurés et de relancer J8 n'est pas exécutée.
- Aucun état global complet n'est imposé par l'oracle : le cas ne fournit pas toutes ses dimensions.

Appuis : I03 `coverage`, `callSummary`, `legacyEndpointMetrics`, T01 tests des strates.

## PB-C02 — taux, refus et nearest-rank

| Mesure | Attendu |
|---|---|
| Tentatives / réponses / parsées | 6 / 4 / 1 |
| Éligibles parsing | 2 : PARSED et SCHEMA_INCOMPATIBLE |
| Refus / 404 / incomplètes | 1 / 1 / 1 |
| Erreurs opérationnelles | 4 : incompatibilité, persistance/refus, transport, résultat absent |
| Taux réponse | 4/6 = 66,67 % |
| Compatibilité | 1/2 = 50,00 % |
| Refus et 404 endpoint | Chacun 1/6 = 16,67 % |
| Erreur opérationnelle | 4/6 = 66,67 % |
| Latences | [50,60,100,200] ; n=4 ; min=50, P50=60, P95=max=200 ms |
| État appels/endpoint | PARTIAL en présence de la tentative sans résultat |

Le HTTP 429 est un refus malgré l'issue finale PERSISTENCE_FAILURE ; il compte une seule fois
dans l'erreur. Le 404 est séparé du parsing et de l'erreur. P50=80 interpolé est incorrect.
Les six campagnes distinctes évitent de prendre une fixture d'agrégation pour un manifeste
J4 phase 2 à six unités autorisées. Appuis : I03, T01 tests refus/persistance/404.

## PB-C03 — complétude pondérée

Population 4 ; COMPLETE=0, PARTIAL=2, EMPTY_VALID=1, UNAVAILABLE=1. Pondération **10/12=83,33 %**.
La moyenne simple 70 % est fausse. Les lignes 0/0 et sans compteurs n'ajoutent pas de dénominateur.
UNAVAILABLE ne devient pas 0 % ; EMPTY_VALID conserve son sens. Les dimensions absentes sont
UNKNOWN et la ventilation reste PARTIAL. Appuis : N07 §8, I03 `completeness`, T01.

## PB-C04 — dossiers et coût

Deux dossiers ciblés distincts, un exploitable (1001), aucun strict. Les composants directs
antérieurs à la fenêtre restent recevables à asOf ; la déduplication ne double pas le dossier.
Découverte=2, appels marginaux=3, effectifs=5 ; ratios **3/1=3,00 et 5/1=5,00**.

Variante : composant courant incompatible du 1001 → exploitables=0, comptes 2/3/5 conservés,
ratios NOT_MEASURED, jamais zéro ni infini. Le 1002 est exclu par UNAVAILABLE.
Appuis : I03 `dossierEfficiency`, `hasAllCurrentComponents`, I06 et T01.

## PB-C05 — fenêtre, cutoff et empreinte

- T1 et T2 incluses, T0/T3 exclues par leur début de tentative.
- Résultat T2 à 11:30 connu à asOf=12:00 recevable même après to=11:00.
- Variante résultat créé/résolu à 12:00:01 : absent à asOf, T2 devient INCOMPLETE_ATTEMPT.
- W1 HTTP : [10:00,12:00). W2 HTTP : [12:00,12:00), NOT_MEASURED sans lecture SQL.
- W3 : 70 caractères bruts, refus avant trim ; signaler D01 de l'inventaire sans corriger le code.
- Export to=13:00/asOf=12:00 : refus ; ne pas appliquer au script la troncature HTTP.
- Mêmes preuves/code/fenêtre/asOf → mêmes hash et rendu. asOf différent entre dans l'empreinte
  même si les comptes sont égaux. Aucun hash J8 chiffré complet n'est exigible sur ce tableau.
- La version `j8-benchmark-v1` appartient à un ensemble de lignes triées, pas un préfixe
  nécessairement premier ; signaler l'écart avec N07 si la sérialisation est discutée.

Appuis : I02, I03 `populationHash`, I07, P02, I01, T02. Les instants ont tous le suffixe Z.

## PB-C06 — temps tardif et sous-série directe

Trois versions directes analysées, un enrichissement et une correction. LOCAL_REPARSE ne
contribue ni aux changements tardifs ni aux délais. La base temporelle est précisément le
dernier **état canonique direct** antérieur : V2=60 000 ms et V3=120 000 ms, n=2,
P50=60 000 et P95=max=120 000 ms. Les instants des versions de famille ne les remplacent pas.

Import et fixture intercalés ne rompent pas la paire directe. Variante sans
previousDirectStateAt : rejet de preuve incohérente, aucun délai inventé ou résultat complet.
Le classifieur J6 est réutilisé ; aucune fraîcheur depuis une horloge fournisseur n'est démontrée.
Appuis : I03 `lateDelay`, I05, N07 §11 et T01.

## PB-S01 à PB-S04 — sélection seulement

| Cas | Attendu observable |
|---|---|
| PB-S01 | Charge le candidat exact sur demande explicite. |
| PB-S02 | Sélectionne le skill pour la comparaison de mesures du Lab. |
| PB-S03 | Ne sélectionne pas ce skill ; ss-verify est pertinent, CI peut être mentionnée. |
| PB-S04 | Ne sélectionne pas ce skill ; respecte la portée Betting Project uniquement. |

Le test s'arrête au routage. Le quatrième prompt n'autorise aucune recherche commerciale
pendant l'évaluation. Si le corps du skill a été préchargé pour S02, on ne peut pas déclarer
la découverte implicite PASS sur cet essai.
