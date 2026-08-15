# WO-SS-20260815-005 — J5 Statistiques, incidents, compositions et complétude

- **Statut :** `VALIDATED`
- **Date :** 2026-08-15
- **Date de démarrage :** 2026-08-15
- **Date de clôture :** 2026-08-15
- **Qualification technique hors ligne :** `PASS`
- **Qualification humaine hors ligne :** `PASS`
- **Clôture :** `COMPLETED`
- **Prérequis :** WO-SS-20260815-004 validé et fusionné sur `main`
- **Jalon :** J5 — Statistiques
- **Branche :** `codex/j5-statistics-completeness`
- **Commit de base :** `c26dc2fe5791cde6030a29ad714dacffc99aa777`
- **Familles hors ligne :** `EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS`
- **Contexte de classement :** `TOURNAMENT_STANDINGS` différé, hors preuve de sortie J5
- **Développement hors ligne J5 autorisé :** `YES`
- **Migration append-only autorisée :** `YES`
- **Réseau SofaScore autorisé :** `NO_DISCOVERY_STOPPED_ON_HTTP_403`
- **URI réelle autorisée :** `NO_DISCOVERY_AUTHORIZATION_CONSUMED`
- **Appel SofaScore réel autorisé :** `NO_DISCOVERY_AUTHORIZATION_CONSUMED`
- **Modification de `.env` autorisée :** `NO`
- **Polling, planification ou retry autorisé :** `NO`
- **Déploiement VPS autorisé :** `NO`

## 1. Objectif

Construire le jalon J5 à partir des identités canoniques J4 déjà persistées. Le laboratoire doit
parser, contrôler, normaliser, conserver et afficher localement les statistiques, incidents et
compositions d'une rencontre. Chaque famille doit exposer un contrôle de complétude explicite ;
une donnée absente ou partielle ne doit jamais être inventée, complétée silencieusement ou
confondue avec une rupture de schéma.

Le contexte de classement prévu par le catalogue et repoussé par J4 peut être ajouté dans la même
frontière hors ligne, à condition de rester lié au tournoi et à la saison synthétiques du corpus.
Il ne constitue pas une autorisation de transport ni une condition plus forte que la preuve de
sortie définie par le document de cadrage : statistiques, incidents et compositions avec contrôles
de complétude.

L'amendement opérateur du 2026-08-15 fournit exactement six URLs de référence, trois pour
`16391135` déjà commencé et trois pour `16421052` pas encore commencé. Il autorise uniquement leur
lecture séquentielle pour découvrir et minimiser les formes JSON nécessaires aux fixtures. Cette
campagne de découverte n'autorise pas encore un transport J5 dans l'application.

## 2. Contexte hérité de J4

J4 fournit :

- une identité canonique stable par paire `(provider, providerEventId)` ;
- des observations d'événement et de détail append-only ;
- une provenance exclusive `SYNTHETIC_FIXTURE` ou `PROVIDER_SNAPSHOT` ;
- le corpus synthétique cohérent de l'événement `900001`, du tournoi `9303` et de la saison `9505` ;
- une interface locale de recherche et de détail ;
- les voies J3 et J4 fournisseur de nouveau verrouillées après qualification ;
- 212 tests standards et 16 tests PostgreSQL/Testcontainers validés à la clôture.

J5 doit réutiliser ces identités et cette provenance sans modifier les migrations V1 à V6, sans
réouvrir une voie réseau et sans anticiper la comparaison sémantique complète de J6.

## 3. Périmètre

### 3.1 Inclus

- un contrat synthétique versionné pour `EVENT_STATISTICS` ;
- un contrat synthétique versionné pour `EVENT_INCIDENTS` ;
- un contrat synthétique versionné pour `EVENT_LINEUPS` ;
- des fixtures nominales, partielles et incompatibles, conçues de zéro sous forme minimale et sans
  secret ;
- un parsing JSON strict : propriétés dupliquées et contenu résiduel refusés ;
- des avertissements structurés pour les champs inconnus et les données facultatives absentes ;
- des contrôles de complétude déterministes, avec statut, score et chemins manquants ;
- le rattachement exact des trois familles à l'identité canonique de l'événement ;
- une persistance normalisée append-only séparée des octets sources ;
- une déduplication par famille, source et empreinte normalisée ;
- la conservation obligatoire de la fixture, du SHA-256, du parseur et de l'heure de réception ;
- une lecture locale de la dernière observation de chaque famille ;
- l'affichage des valeurs, de la complétude et de la provenance sur la fiche événement ;
- une action explicite d'import du corpus de démonstration, sans aucun repli réseau ;
- les tests unitaires, Web et PostgreSQL/Testcontainers nécessaires ;
- si elle ne dilue pas la preuve principale, une fixture locale `TOURNAMENT_STANDINGS` liée au
  tournoi `9303` et à la saison `9505`.
- jusqu'à six lectures GET séquentielles des seules URLs fournies par l'opérateur, espacées selon
  la politique prudente et arrêtées au premier incident ;
- la transformation locale des réponses observées en fixtures minimisées sans cookie, jeton,
  header sensible ou champ inutile.

### 3.2 Exclus

- tout endpoint ou URI autre que les six références fournies ;
- tout transport J5 dans l'application ou appel non lié à la découverte de schéma ;
- la qualification d'un schéma fournisseur pour les nouvelles familles ;
- toute collecte automatique, polling, live, planification, retry ou rappel implicite ;
- la mutation ou suppression d'une observation J4 ou J5 ;
- la comparaison historique complète et les corrections tardives J6 ;
- l'export canonique et le manifeste J7 ;
- la collecte historique massive, les cotes et toute logique de pari ;
- l'envoi de payload brut ou normalisé vers le VPS ou le Betting Project principal ;
- la modification de l'ADR-SS-001 et des PDF de référence.

## 4. Décisions de complétude

1. Une rupture de structure ou de type est `SCHEMA_INCOMPATIBLE` et ne produit aucune donnée
   normalisée partielle.
2. Une structure valide peut être `COMPLETE`, `PARTIAL` ou `EMPTY_VALID` selon la famille.
3. Une liste d'incidents vide est un résultat valide et complet au sens structurel ; elle ne prouve
   pas qu'un fournisseur réel aurait couvert tous les incidents.
4. Les statistiques sont complètes lorsque les périodes et métriques déclarées possèdent les deux
   valeurs attendues ; une valeur absente rend le lot `PARTIAL` et identifie son chemin.
5. Les compositions sont complètes lorsque les deux côtés possèdent une liste de joueurs non vide ;
   l'absence de formation ou l'état non confirmé est visible mais ne rend pas le schéma incompatible.
6. Le score de complétude est calculé sur des signaux documentés, borné entre 0 et 100 et conservé
   avec les chemins manquants. Il n'est pas une mesure de qualité fournisseur globale.
7. Aucun zéro, texte ou joueur n'est synthétisé pour remplacer une valeur absente.

## 5. Invariants

1. `server.address=127.0.0.1` reste obligatoire.
2. `sofascore.enabled=false` reste la valeur par défaut.
3. `ConnectorGate` reste bloquant.
4. Le profil Maven `sofascore-live-test` reste bloqué.
5. Toutes les définitions du catalogue restent `callable=false` et sans URI.
6. Aucun test standard ou d'intégration ne contacte Internet ou SofaScore.
7. Les données sources et normalisées restent séparées.
8. Toute donnée normalisée conserve identité, source, hash, parseur et heure de réception.
9. Les observations et leurs lignes normalisées sont append-only.
10. Une fixture J5 ne peut être rattachée qu'à l'identifiant fournisseur qu'elle contient.
11. Aucun payload complet, cookie, jeton ou secret n'entre dans les logs ou les preuves.
12. Le laboratoire reste `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
    `NO_CRITICAL_DEPENDENCY`.
13. La découverte réelle est séquentielle, sans retry et s'arrête sur `403`, `429`, `5xx`, timeout,
    HTML inattendu ou contenu non JSON.
14. Les réponses complètes de découverte restent locales ; seuls des sous-ensembles minimisés et
    leurs hashes peuvent entrer dans Git après scan de contenu sensible.

## 6. Unités de livraison prévues

1. `docs: start J5 statistics completeness milestone`
   - ouvrir le Work Order depuis le merge J4 validé ;
   - figer le périmètre hors ligne, la complétude et les exclusions réseau.
2. `feat: add J5 offline statistics family contracts`
   - ajouter modèles, parseurs, fixtures et ruptures de schéma ;
   - calculer les rapports de complétude sans inventer de données.
3. `feat: persist and display J5 event data`
   - ajouter la migration append-only, le port et l'adaptateur JDBC ;
   - rattacher et afficher les dernières familles sur la fiche locale.
4. `test: qualify J5 offline completeness and traceability`
   - couvrir parsing, complétude, déduplication, provenance, Web et PostgreSQL ;
   - prouver l'absence d'appel fournisseur.
5. `docs: close J5 statistics milestone`
   - mettre à jour architecture, README, changelog et rapport de validation ;
   - clôturer uniquement après validation Maven et revue humaine locale.

## 7. Matrice de tests minimale

| Scénario | Résultat attendu |
|---|---|
| fixture statistique nominale | métriques parsées, complétude `COMPLETE` |
| valeur statistique facultative absente | parsing réussi, complétude `PARTIAL`, chemin visible |
| fixture incidents vide | parsing réussi, complétude `EMPTY_VALID` |
| fixture incidents nominale | ordre, minute, équipe, joueur et score conservés |
| fixture compositions nominale | deux côtés, titulaires, bancs et formations conservés |
| composition non confirmée ou formation absente | parsing réussi, complétude explicite |
| champ obligatoire absent ou type modifié | `SCHEMA_INCOMPATIBLE`, aucune écriture |
| champ inconnu | parsing tolérant avec avertissement structuré |
| identifiant événement incohérent | rejet transactionnel avant persistance |
| même fixture réimportée | aucune nouvelle observation ni ligne dupliquée |
| nouvelle version valide | nouvelle observation append-only |
| tentative `UPDATE` ou `DELETE` | refus PostgreSQL |
| page de détail sans données J5 | absence explicite, aucun transport |
| page de détail après import | familles, complétude, provenance et hash visibles |
| suite Maven standard | aucun accès Internet |
| migration PostgreSQL | V7, contraintes, déduplication et append-only validés |

## 8. Critères d'acceptation

- [x] contrats J5 hors ligne documentés et versionnés ;
- [x] statistiques, incidents et compositions nominales parsées ;
- [x] états partiels et vides distingués d'une incompatibilité de schéma ;
- [x] contrôles de complétude affichés avec score et chemins manquants ;
- [x] rattachement exact à l'identité canonique J4 ;
- [x] persistance append-only et déduplication PostgreSQL qualifiées ;
- [x] provenance complète fixture, hash, parseur et heure ;
- [x] interface locale consultable sans réseau ;
- [x] catalogue toujours `callable=false` et sans URI ;
- [x] `ConnectorGate`, profil live et valeurs par défaut inchangés ;
- [x] `mvnw.cmd clean verify` réussi ;
- [x] `mvnw.cmd -Pintegration-tests verify` réussi ;
- [x] validation humaine locale consignée ;
- [x] README, changelog, architecture et rapport de validation mis à jour.

## 9. État initial

```text
J5_STATUS=IN_DEVELOPMENT
J5_BASE_COMMIT=c26dc2fe5791cde6030a29ad714dacffc99aa777
J5_BRANCH=codex/j5-statistics-completeness
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_NETWORK_AUTHORIZED=NO_DISCOVERY_STOPPED_ON_HTTP_403
J5_REAL_ENDPOINT_URI_AUTHORIZED=NO_DISCOVERY_AUTHORIZATION_CONSUMED
J5_REAL_PROVIDER_CALL_AUTHORIZED=NO_DISCOVERY_AUTHORIZATION_CONSUMED
J5_APPLICATION_TRANSPORT_AUTHORIZED=NO
J5_POLLING_AUTHORIZED=NO
J5_PRODUCTION_AUTHORIZED=NO
J5_VPS_DEPLOYMENT_AUTHORIZED=NO
```

## 10. Campagne de découverte réelle arrêtée le 2026-08-15

La première et unique lecture réelle a ciblé l'URL exacte de statistiques fournie pour l'événement
`16391135`. Les lecteurs Web et navigateur l'ont d'abord bloquée localement sans atteindre le
fournisseur. Le GET HTTP direct, sans cookie, jeton, header personnalisé ou retry, a ensuite reçu
un refus explicite. La règle d'arrêt de l'ADR-SS-001 a été appliquée immédiatement : aucun des cinq
autres exemples n'a été appelé et aucune variation de client, d'identité ou d'adresse n'a été
tentée.

Le payload d'erreur complet n'est pas reproduit dans le Work Order. Seules les métadonnées
minimisées nécessaires à la preuve sont conservées :

```text
J5_DISCOVERY_STARTED_EVENT_ID=16391135
J5_DISCOVERY_STARTED_ENDPOINT=EVENT_STATISTICS
J5_DISCOVERY_RECEIVED_AT_UTC=2026-08-15T11:36:13.0955125Z
J5_DISCOVERY_HTTP_STATUS=403
J5_DISCOVERY_CONTENT_TYPE=application/json
J5_DISCOVERY_RESPONSE_BYTES=48
J5_DISCOVERY_RESPONSE_SHA256=6b771bca0fe4271cc0baf29954f10c68be99102048e4df524b6d7d6bf38dad5d
J5_DISCOVERY_LATENCY_SECONDS=0.553132
J5_DISCOVERY_RETRIES=0
J5_DISCOVERY_PROVIDER_CALL_ATTEMPTS=1
J5_DISCOVERY_PROVIDER_CALL_SUCCESSES=0
J5_DISCOVERY_REMAINING_URLS_CALLED=0
J5_DISCOVERY_STOP_REASON=HTTP_403
J5_DISCOVERY_CAMPAIGN_STATE=STOPPED_LOCKED
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_NETWORK_AUTHORIZED_AFTER_INCIDENT=NO
J5_APPLICATION_TRANSPORT_AUTHORIZED=NO
```

Le jalon se poursuit exclusivement avec des fixtures synthétiques portant
`providerSchemaValidated=false`. Ces contrats servent à qualifier l'architecture, la complétude,
la provenance et la persistance ; ils ne sont pas présentés comme une preuve de compatibilité avec
les réponses réelles actuelles.

## 11. Résultat technique hors ligne

Les trois familles principales sont implémentées. L'extension facultative
`TOURNAMENT_STANDINGS` n'a pas été retenue : elle n'est pas nécessaire à la preuve de sortie du
cadrage et aurait élargi le corpus alors que les trois schémas réels principaux restent non
validés.

```text
J5_IMPLEMENTATION_STATUS=VALIDATED_OFFLINE
J5_PARSERS=event-statistics-v1,event-incidents-v1,event-lineups-v1
J5_FIXTURES=9_SYNTHETIC
J5_COMPLETENESS_STATUSES=COMPLETE,PARTIAL,EMPTY_VALID
J5_FLYWAY_VERSION=7
J5_STANDARD_TESTS=232
J5_INTEGRATION_TESTS=17
J5_MAVEN_PROVIDER_CALLS=0
J5_APPLICATION_PROVIDER_CALLS=0
J5_HUMAN_OFFLINE_QUALIFICATION=PASS
J5_HUMAN_EVIDENCE=8_SCREENSHOTS_REVIEWED_NOT_VERSIONED
J5_REAL_CONDITIONS_CAMPAIGN_STATUS=NOT_RUN_SEPARATE_WORK_ORDER_REQUIRED
J5_WORK_ORDER_STATUS=VALIDATED
J5_CAN_BE_ARCHIVED=YES
```

Les preuves automatisées et humaines sont consignées dans
`docs/validation/J5-WINDOWS-TECHNICAL-QUALIFICATION-20260815.md`. Le parcours exécuté est celui de
la section **3.10 bis** du runbook local.

## 12. Qualification humaine hors ligne et clôture — 2026-08-15

Le propriétaire a déclaré les tests hors ligne concluants et a fourni huit captures d'écran de
l'application locale. Elles ont été utilisées comme preuves visuelles, sans être copiées dans le
dépôt : les fichiers temporaires et l'habillage du navigateur ne constituent pas des artefacts de
qualification versionnés.

La revue confirme :

- la navigation depuis une identité J4 persistée vers la page J5 ;
- l'affichage explicite des trois absences avant import, sans repli réseau ;
- la réutilisation de l'identité synthétique canonique `900001` ;
- l'import explicite du corpus avec le message de rattachement des trois familles ;
- les statistiques `event-statistics-v1` à `COMPLETE · 100%`, avec `6/6` signaux ;
- les incidents `event-incidents-v1` à `COMPLETE · 100%`, avec `3/3` signaux et trois lignes ;
- les compositions `event-lineups-v1` à `COMPLETE · 100%`, avec `13/13` signaux, deux côtés et
  leurs formations ;
- la présence des références de source, heures de réception, SHA-256 source et SHA-256 normalisé ;
- l'indication persistante `PROVIDER_SCHEMA_VALIDATED=NO` ;
- l'absence de bouton fournisseur J5 et le maintien des bloqueurs réseau J4 visibles ;
- le comportement d'import déclaré concluant par l'opérateur, complété par la preuve automatisée
  PostgreSQL d'idempotence et de déduplication.

Une fiche J4 issue de `PROVIDER_SNAPSHOT` pour l'événement `16412917`, alors `inprogress`, montre
également que le lien J5 est disponible sur une identité réelle déjà persistée. Cette consultation
reste strictement locale : elle ne crée aucune observation J5 réelle et ne qualifie aucun des
trois schémas fournisseur.

La prochaine campagne annoncée « en conditions réelles » ne fait pas partie de ce Work Order.
L'autorisation de découverte initiale a été consommée par le premier `HTTP 403` et demeure
`STOPPED_LOCKED`. Un Work Order ou amendement distinct devra préciser les identifiants, les routes,
les opt-ins, le nombre maximal d'appels, les confirmations, les règles d'arrêt et le
reverrouillage avant tout nouveau transport.

```text
J5_TECHNICAL_OFFLINE_QUALIFICATION=PASS
J5_HUMAN_OFFLINE_QUALIFICATION=PASS
J5_OFFLINE_MILESTONE=PASS
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_APPLICATION_TRANSPORT=NOT_IMPLEMENTED
J5_REAL_CONDITIONS_CAMPAIGN=NOT_RUN
J5_REAL_CONDITIONS_AUTHORIZED_BY_THIS_WORK_ORDER=NO
J5_STATUS=VALIDATED
J5_WORK_ORDER=COMPLETED
J5_CAN_BE_CLOSED=YES
J5_CLOSED=YES
POLLING_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
```
