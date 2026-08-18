# J4/J5 — Session de qualification combinée

> `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

## 1. Résultat

```text
DATE=2026-08-18
COMBINED_SESSION_SCOPE=J4_PHASE_2_THEN_J5
J3_SIMULTANEOUS_ACTIVATION=REJECTED
J4_PHASE_1_SIMULTANEOUS_ACTIVATION=REJECTED
ALLOWED_ENDPOINTS=EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
ENDPOINT_SELECTION=EXACT_UNION_NO_EXTRA_FAMILY
SHARED_MAXIMUM_CONCURRENCY=1
SHARED_MINIMUM_DELAY=3_SECONDS_BETWEEN_PROVIDER_REQUEST_STARTS
J4_GLOBAL_STOP_EFFECT=J4_ONLY
J5_CONTROL_AFTER_J4_STOP=AVAILABLE
STANDARD_TESTS=358_PASS
INTEGRATION_TESTS=32_PASS
MAVEN_PROVIDER_CALLS=0
COMBINED_SESSION_OFFLINE_QUALIFICATION=PASS
COMBINED_SESSION_HUMAN_QUALIFICATION=PASS_REAL_EVENT_16671566
COMBINED_SESSION_APPLICATION_STARTS=1_OPERATOR_ATTESTED
COMBINED_SESSION_RESTART_BETWEEN_J4_AND_J5=NO_OPERATOR_ATTESTED
COMBINED_SESSION_J4=COMPLETED_LOCKED_ONE_CALL_SNAPSHOT_183
COMBINED_SESSION_J5=COMPLETED_LOCKED_THREE_CALLS_SNAPSHOTS_184_185_186
OFF_THE_BALL_CARD_REAL_QUALIFICATION=PASS_EVENT_16671566_SNAPSHOT_185
PROVIDER_SCHEMA_VALIDATED=YES
PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
CURRENT_INCIDENT_PARSER=event-incidents-v13
CURRENT_FLYWAY_VERSION=20
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
WORK_ORDER_ARCHIVABLE=YES
```

L'application accepte désormais la qualification J4 sous-étape 2 et la qualification J5 au cours
du même démarrage. Le besoin couvert est strictement séquentiel : créer ou actualiser une identité
avec `EVENT_DETAILS`, puis ouvrir cette identité et collecter statistiques, incidents et
compositions sans reconstruire ni redémarrer entre les deux campagnes.

## 2. Configuration exacte

Le propriétaire peut sélectionner temporairement le mode combiné avec les seules valeurs réseau
documentées suivantes :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

L'ordre textuel des quatre valeurs n'accorde aucun droit supplémentaire : Spring les convertit en
ensemble et exige l'union exacte. `SCHEDULED_EVENTS`, une famille future ou l'absence d'une des
quatre familles rend le démarrage invalide. J3 reste exclusif. J4 sous-étape 1 reste incompatible
avec J5 ; le partage de session exige explicitement la sous-étape 2 paramétrable.

Le fichier `.env` reste local et ignoré. Il n'a été ni lu ni modifié par l'agent.

## 3. Garde-fous de configuration

`SofascoreProperties` calcule les endpoints requis depuis les voies effectivement activées. Les
politiques J4 phase 2 et J5 utilisent le même calcul : elles ne peuvent donc pas interpréter
différemment une union partielle ou élargie.

Les cas suivants restent refusés :

- J3 avec J4 ou J5 ;
- J4 phase 1 avec J5 ;
- phase 2 sans l'opt-in principal J4 ;
- connecteur général désactivé ;
- origine différente de `https://www.sofascore.com` ;
- stockage brut désactivé ;
- concurrence différente de un ;
- polling ou rafraîchissement automatique ;
- endpoint manquant ou supplémentaire.

Les modes historiques J4 seul et J5 seul restent valides avec leurs ensembles exacts respectifs.

## 4. Coordination réseau partagée

L'assouplissement des propriétés ne crée pas deux couloirs HTTP indépendants. Le composant
`J4J5ProviderRequestCoordinator` est injecté dans les services réels J4 phase 1, J4 phase 2 et J5.
Avant chaque transport, il :

1. acquiert un verrou équitable commun au processus ;
2. attend si nécessaire pour séparer de trois secondes au minimum deux débuts de requête ;
3. conserve le verrou pendant l'échange HTTP ;
4. le libère systématiquement à la sortie, y compris lors d'une exception.

Deux onglets ou deux sessions HTTP ne peuvent donc pas exécuter simultanément un appel J4 et un
appel J5. L'interruption pendant l'attente conserve le comportement terminal existant
`MINIMUM_DELAY_INTERRUPTED`; aucun retry n'est ajouté.

Les contrôles métier restent indépendants. **Arrêt global J4** verrouille les deux contrôles J4,
mais ne modifie pas le contrôle J5. Réciproquement, **Arrêt global J5** ne réarme pas J4. Une preuve
unitaire prépare J5 après un arrêt J4 dans la même instance et vérifie que J4 reste
`STOPPED_LOCKED`.

## 5. Parcours opérateur attendu

1. effectuer le build et démarrer l'application une seule fois avec la configuration combinée ;
2. dans `/events`, saisir l'identifiant choisi et préparer puis confirmer la campagne J4 phase 2 ;
3. attendre son état terminal ;
4. sélectionner éventuellement **Arrêt global J4** — cette action n'est pas requise pour ouvrir J5
   après un succès, mais elle reste autorisée et circonscrite à J4 ;
5. ouvrir la fiche de l'identité canonique créée, puis son écran J5 ;
6. préparer localement et confirmer séparément la campagne J5 ;
7. attendre les trois familles ordonnées sans rechargement ni resoumission ;
8. au premier incident réel, ne pas réessayer et ne pas poursuivre manuellement une famille
   restante ;
9. à la fin de la session, arrêter l'application, remettre toutes les voies fournisseur à l'état
   bloqué, redémarrer une fois pour vérifier les bloqueurs, puis arrêter gracieusement.

Chaque campagne conserve sa phrase, son acquittement, son identifiant de requête, son état terminal
et son bouton d'arrêt propres. La nouvelle configuration n'autorise ni une confirmation commune,
ni une campagne automatique J4→J5, ni un polling.

## 6. Qualification automatisée

La sélection ciblée couvre le binding, les politiques, l'indépendance des contrôles, la
sérialisation réseau et les trois services réels :

```text
.\mvnw.cmd --offline -q "-Dtest=SofascorePropertiesTest,J4EventDetailsPhase2QualificationPolicyTest,J5RealQualificationPolicyTest,J4J5CombinedQualificationSessionTest,J4J5ProviderRequestCoordinatorTest,J4RealEventDetailsPhase1ServiceTest,J4RealEventDetailsPhase2ServiceTest,J5RealEventDataServiceTest" test
PASS — 38 tests

.\mvnw.cmd --offline clean verify
PASS — 358 tests standards

.\mvnw.cmd --offline -q -Pintegration-tests verify
PASS — 358 tests standards + 32 tests d'intégration
```

Tous les totaux portent zéro échec et zéro erreur. PostgreSQL 18.4 applique les 18 migrations sur
un schéma neuf et qualifie notamment V17 → V18. Aucun test ne résout ni n'appelle une URI
fournisseur ; les transports unitaires restent interceptés localement. Le seul avertissement est
l'auto-attachement Mockito/Byte Buddy sous Java 25.

Le contrôle en lecture seule effectué après l'implémentation ne trouve ni listener sur le port
`8087`, ni processus Java dont la ligne de commande correspond au laboratoire. Il atteste l'arrêt
de l'application à cet instant sans attester le contenu de `.env`.

## 7. Qualification humaine réelle

Le propriétaire atteste le 2026-08-18 avoir exécuté J4 phase 2 puis J5 dans le même démarrage, sans
rebuild, sans redémarrage intermédiaire et sans retry. Les onze captures transmises suivent la même
identité **Barracas Central — Rosario Central** :

```text
COMBINED_SESSION_APPLICATION_STARTS=1
COMBINED_SESSION_J4_EVENT_ID=16671566
COMBINED_SESSION_CANONICAL_UUID=da075869-34d4-3d42-83d2-613583691845
COMBINED_SESSION_J4_TERMINAL=COMPLETED_LOCKED
COMBINED_SESSION_J4_PROVIDER_CALLS=1
COMBINED_SESSION_J4_SNAPSHOT=183
COMBINED_SESSION_J5_PREPARATION_AFTER_J4=AVAILABLE
COMBINED_SESSION_J5_TERMINAL=COMPLETED_LOCKED
COMBINED_SESSION_J5_PROVIDER_CALLS=3
COMBINED_SESSION_RESTART_BETWEEN_J4_AND_J5=NO
COMBINED_SESSION_RETRY=0
COMBINED_SESSION_J4_GLOBAL_STOP_APPLIED=YES_AFTER_CAMPAIGNS
COMBINED_SESSION_J5_GLOBAL_STOP_APPLIED=YES_AFTER_SUCCESS
```

La preuve J4 montre un appel fournisseur, un état `COMPLETED_LOCKED`, le snapshot 183 de 10 074
octets, `event-details-v2`, une nouvelle version append-only et le SHA-256 brut
`08a3f4236690a468b65018239ba77ebdedb456adfa17ceb20c1019fd68d61d37`. La fiche locale conserve
l'identifiant fournisseur, l'UUID canonique et la provenance `PROVIDER_SNAPSHOT`.

La préparation J5 devient ensuite effectivement `AWAITING_CONFIRMATION` sur cette identité. La
phrase visible dans la capture n'est volontairement pas retranscrite. La campagne termine
`COMPLETED_LOCKED` après exactement trois appels :

| Famille | Snapshot | Taille | Observation | Complétude | SHA-256 brut | SHA-256 normalisé |
|---|---:|---:|---:|---|---|---|
| statistiques | 184 | 25 408 | 95 | `COMPLETE · 262/262` | `1ae0a26646233893b2928bc1303d4d34479b63b1eb20a2a936dd2755e5a4011e` | `45032dc445f8d76df16ea84a21b21b5b9cce3b612e1ed3aa20f5d033174ba8ea` |
| incidents | 185 | 27 796 | 96 | `COMPLETE · 69/69` | `159ede40ec5d7c23ffdd0fd3ca60becc47944de2395e92c04794a846f2939150` | `89a987e0ff9980698b07c822be98d27beef247b18fedc2dc74fc891840ed3bfc` |
| compositions | 186 | 74 159 | 97 | `COMPLETE · 95/95` | `55f4b2d38443a5d21483c3364c169b9fbeee694eeecaef4e7f96acf13112c195d` | `7b910203bd8e43b0e23f8886ad2e2ef96281c9212724cced7bca984e5b11cd03` |

Le snapshot incidents 185 est traité par `event-incidents-v11`. Il affiche 18 incidents, dont le
carton `yellow` extérieur de **Facundo Mallo** à la minute 77 avec le motif exact
`Off the ball foul`. Les compositions confirmées atteignent ensuite les deux équipes, ce qui prouve
la poursuite jusqu'à la troisième famille.

Les arrêts globaux J4 et J5 sont enfin visibles à `STOPPED_LOCKED`. Ils sont appliqués après les
campagnes ; l'indépendance permettant de préparer J5 après un arrêt J4 préalable reste couverte par
la régression automatisée dédiée.

## 8. Inventaire minimisé des captures

Les PNG restent dans le répertoire temporaire opérateur et ne sont pas copiés dans Git. Le lot
totalise 1 128 540 octets :

| Preuve | Taille | SHA-256 du PNG |
|---|---:|---|
| résultat J4 phase 2 | 80 618 | `26ef19d0a4f2e579241ef2e311274e249b590a58bdda48cdb0ac801092bba13e` |
| identité et détail J4 | 179 449 | `96456a7ee23f0636e9165ce0232e33166c019e3acfc0b58207efd076153cc362` |
| disponibilité J5 sur l'identité J4 | 130 323 | `ece62760f9f78c842847c5ab786b9e4d8891e92cea000982506702cd858873f1` |
| préparation et confirmation J5, contenu non retranscrit | 123 243 | `470993acd80a0c31f33d24e055dc400d5a92767018690ccd9cef1ae423616516` |
| résultat des trois familles J5 | 136 290 | `fa47a0a4770131c2b8b9e6fad1033288dad425b7fb9bc875538da56866494e3f` |
| statistiques complètes | 90 248 | `b9f0413962af80530b8b17ab72af2fbf9ea7e18901d561c45a343eed10dd0c36` |
| incidents V11 et motif `Off the ball foul` | 97 045 | `17e6cee3b7849405ee0179a2695f6588d5fc55a0032f8d05881b21ca479ba0c5` |
| composition domicile | 74 503 | `2323079bfb27c2aa1a74d721d6bec3191daa01db921b6109da1598e6490579c6` |
| composition extérieur | 51 521 | `c26b2f3d920bde057820e31c0d108a66154cda37effdde3ed46d35c8edb760c9` |
| arrêt global J4 | 94 540 | `5e931e66d4db23218bdb48e34eafe48a5d9e6a469cc174bd06ab7545684d5884` |
| arrêt global J5 | 70 760 | `b3fe2000e726edf24f7e6c4dfa7ba4c6aef41611983103df7b6535481f4648b5` |

## 9. Décision

La session combinée et le motif de carton V11 sont qualifiés en conditions réelles. Le propriétaire
a depuis attesté le reverrouillage local sans publier `.env`, puis un redémarrage a montré J4 et J5
`LOCKED`. Le contrôle final ne trouve aucun listener sur `127.0.0.1:8087` : l'application est
arrêtée et la clôture est acquise.

## 10. Évolution postérieure V12

La preuve combinée J4 phase 2 + J5 reste concluante pour l'événement `16671566`. Une campagne J5
ultérieure sur `16691018` a toutefois rejeté sous V11 une séance terminale entièrement non minutée.
Cette dérive indépendante du mode combiné ramène le statut fournisseur courant à
`PROVIDER_SCHEMA_VALIDATED=NO` jusqu'au retest de `event-incidents-v12`.

Le rapport `J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md` décrit la correction. Le parseur courant V13
a depuis réussi le retest jusqu'aux compositions, la présentation finale, le reverrouillage et
l'arrêt final.
