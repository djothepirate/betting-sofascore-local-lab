# Revue indépendante — JM-N01 — run-01

## Verdict : FAIL

La réponse livre une revue d'architecture substantielle et utile du code J3 fourni. Elle cartographie correctement le monomodule, les ports, les adaptateurs, les transactions, la composition Spring et la portée des qualifications. Elle n'invente aucun test réussi et n'applique aucun changement.

Le verdict sanctionne trois **omissions de contenu obligatoire**, sans établir de décision technique incorrecte :

1. Les deux distinctions du domaine ne sont pas explicitées : déclencheur de l'ordre indépendant de la source de chaque page ; date civile cible distincte des instants UTC d'exécution et d'échéance.
2. La carte des frictions nomme le catalogue et le parseur concrets, mais omet `ScheduledEventsTransportException`, autre dépendance effective de l'exécuteur à l'adaptateur.
3. Les relais `ss-data-contract-replay`, `ss-postgres-change` et `ss-verify` sont absents, même lorsque la réponse propose d'instruire une garantie de persistance/J8 et présente les futures commandes.

Tous les critères obligatoires de l'oracle sont nécessaires. La qualité des autres sections ne remplace pas ces éléments. Ni la réponse ni le candidat n'ont été corrigés.

## Identité et intégrité

- Candidat : `ss-java-module` `0.1.0-candidate.1`.
- SHA-256 candidat : `d20c8b838a9c4b9713e27c576e22272427d3f64fef50a670f5a09364cf849eb4`.
- SHA-256 YAML : `ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae`.
- SHA-256 réponse : `e27b5bda703cfc0ad20f09a2f74ccdd1c5600d0545badb1832bc857668306524`.
- SHA-256 événements natifs : `49a03ce3f746dff1abd16757f3cc7f313f4ea0f9f9c13e0367cbaaddb611c3ea`.
- Source métier : `6648dd423e556b5248b8793a539ae85a7680f9bc` ; préparation : `d89f9f2f3da136faf00976266202a4ff9b8c8c1f`.
- Gel lu avant la réponse : `2026-09-15T21:14:43.023201+00:00`.
- Neuf pièces figées sur neuf ont des tailles et empreintes conformes ; 39 entrées intactes, dont 36 sources métier conformes à l'allowlist et au manifeste d'origine.
- `native-events.json`, `item_1` terminé, code 0 : la sortie `Get-Content -Raw` contient exactement tout le texte du candidat. Les 33 commandes terminées emploient seulement `Get-Content` et `rg` sur les fichiers autorisés. Les occurrences `truncatedTo` sont des méthodes Java lues, pas une troncature du journal.

## Matrice des critères

Les lignes renvoient à `frozen/JM-N01/response.md`.

| Critère | Verdict | Preuve / limite |
|---|---|---|
| Identité et revue au bon commit | PASS | Lignes 5–16 : commit attribué à l'entrée, racine et absence d'exécution ; historique contextualisé. |
| Contrôleurs exacts | PASS | Lignes 69, 77 et 100 : jeton, validations HTTP/import, runtime, lectures directes des ports sans acquisition. |
| Données et identités | **FAIL** | Lignes 70–71 et 127–137 : valeurs et modes décrits, mais indépendance trigger/source de page et date cible/instants UTC non formulées. Aucune confusion explicitement affirmée. |
| Runtime, stores et adaptateurs | PASS | Lignes 72–76 et 98–123 : leadership, ordre durable, consommateur sériel, qualification, choix autonome/live et classes exactes. |
| Exécution commune | PASS | Lignes 74 et 125–150 : `ProviderAccess`, import/cache/fournisseur, borne 35, preuves et fermeture avant publication. |
| Transaction et dernier succès | PASS | Lignes 139–175 et 279 : publication via bean distinct, JDBC et `j3_last_success`, conservation/rollback par date ; limites des échecs J8 correctement posées comme questions à instruire. |
| Interfaces / décorateur / parent IPC | PASS | Lignes 79–81 et 205–230 : paquet réel, `@Primary`, constructeur concret et deux interfaces du superviseur. |
| Transfert live et contextes | PASS | Lignes 177–249 : même propriétaire, lease conservée, travail J3 dans le live, contexte temporaire neuf, contexte live original et contrôle de l'émetteur ; nettoyage avant reprise. |
| Socle et activation | PASS | Lignes 104–123 et 251–268 : Java 25, Boot 4.1.0, wrapper 3.9.16, monomodule, Web/Servlet/loopback puis local/runtime, flags séparés. |
| Maven et tests standards | PASS | Lignes 262–266 : propriétés de test désactivées, simulations explicites, sources/artefact/sorties worker séparés, compilation sans lancement, live-test bloqué. Le profil J7 n'est pas développé mais n'est pas confondu avec le runtime. |
| ADR et gouvernance J3 | PASS | Lignes 14–16 et 179–245 : ADR v0.3, récits anciens contextualisés, exception adoptée et limites live ; aucune confirmation redemandée ni extension générale J4/J5. |
| Frictions concrètes | **FAIL** | Lignes 83–92 : catalogue et parseur bien relevés ; dépendance à l'exception d'adaptateur omise. Aucun faux port de parsing inventé. |
| Quatre IT pertinentes | PASS | Lignes 278–281 : noms, admission/concurrence, publication/rétention/reprise, threads/maintenance, PostgreSQL et doubles, Failsafe. |
| Protocole / qualification native | PASS | Lignes 282–301 : protocole pur, vraie qualification loopback, motif non correspondant, `-Dit.test` et goals Failsafe explicites, limites autorité/contexte. |
| Contexte Spring et garde textuel | PASS | Lignes 277 et 303–307 : test réduit, `@Primary` non qualifié globalement, scan textuel sans preuve du graphe, aucun ArchUnit ou total courant inventé. |
| Proposition minimale / risques | PASS | Lignes 311–342 : découpage conservé, contrôles de sélection/composition proposés, garantie J8 à instruire avec sources supplémentaires. Aucun défaut global affirmé par simple lacune de couverture. |
| Relais spécialisés | **FAIL** | Aucun des trois skills spécialisés requis n'est nommé ni sollicité dans la réponse. Le futur lot distinct est annoncé, mais le routage obligatoire manque. |

## Limites de la preuve

La lecture réelle du candidat est établie ; son injection automatique par le CLI ne l'est pas. Le catalogue conservé est déclaré reconstruit après exécution. La trace ne donne pas le modèle et l'effort effectifs (`null`) ; aucune identité n'est inférée. Les champs `started_at_utc` et `ended_at_utc` figés portent l'offset UTC `+00:00`.

Aucun test applicatif n'a été lancé par le relecteur. Le gain global de temps et de qualité reste non mesuré. La matrice complète exploitable par le publisher est dans `review-JM-N01.json`, avec les champs `case_id` et `verdict`.
