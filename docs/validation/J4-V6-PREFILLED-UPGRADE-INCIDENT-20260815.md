# J4 — Incident et correction de l’upgrade V5 préremplie vers V6

- **Date de l’incident :** 2026-08-15
- **Heure observée :** `08:03:29+02:00`
- **Work Order :** `WO-SS-20260815-004`
- **Branche corrective :** `codex/j4-real-event-details-phase1`
- **Nature :** migration locale avant campagne fournisseur
- **Appel fournisseur exécuté :** `NO`

## 1. Observation

L’application locale a trouvé la base PostgreSQL en version V5 et a commencé
`V6__guarded_real_event_details.sql`. La base contenait déjà au moins une observation synthétique
`event_detail_observation`, créée pendant la qualification hors ligne. Le backfill des nouvelles
colonnes de provenance a déclenché le trigger append-only installé par V5 et la migration a été
refusée avec le code PostgreSQL `P0001`.

Flyway a journalisé `Changes successfully rolled back`. Le contexte Spring n’a jamais terminé son
démarrage, puis Hikari et Tomcat se sont arrêtés. Le formulaire de campagne n’était donc pas
accessible et aucun transport `EVENT_DETAILS` n’a pu partir.

```text
INCIDENT_TYPE=LOCAL_DATABASE_MIGRATION
INCIDENT_CODE=V6_BACKFILL_BLOCKED_BY_APPEND_ONLY_TRIGGER
DATABASE_VERSION_BEFORE=V5
DATABASE_VERSION_AFTER_FAILURE=V5_ROLLBACK_EXPECTED
PROVIDER_CALL_ATTEMPTS=0
RAW_PROVIDER_SNAPSHOT_CREATED_BY_INCIDENT=NO
REAL_PROVIDER_QUALIFICATION=NOT_RUN
```

## 2. Cause racine

V5 avait déjà créé `event_detail_observation_append_only`. La première version de V6 ajoutait
`source_kind`, `source_reference` et `source_snapshot_id`, puis exécutait immédiatement un
`UPDATE` pour renseigner la provenance des lignes V5. Une installation vide passait car cet
`UPDATE` ne touchait aucune ligne ; le scénario réel V5 prérempli n’était pas couvert.

Le message « canonical_event_observation is append-only » provient de la fonction de trigger
commune créée en V4. La table réellement protégée et mise à niveau était bien
`event_detail_observation`.

## 3. Correction

V6 n’avait pas été poussée, publiée ou appliquée avec succès dans un environnement partagé. Son
checksum peut donc être corrigé avant publication. La migration suit maintenant cet ordre dans une
seule transaction PostgreSQL :

1. ajouter les trois colonnes de provenance ;
2. désactiver uniquement `event_detail_observation_append_only` ;
3. renseigner `source_kind='SYNTHETIC_FIXTURE'` et
   `source_reference=source_fixture_id` ;
4. réactiver immédiatement le trigger ;
5. rendre les colonnes obligatoires et installer les contraintes V6.

Un échec ultérieur annulerait l’ensemble de la transaction, y compris l’état du trigger. Aucun
champ métier historique n’est modifié par ce backfill.

## 4. Couverture corrective

Le test `upgradesAPrepopulatedV5EventDetailToV6WithoutRewritingHistory` :

- applique Flyway jusqu’à V5 dans un schéma PostgreSQL isolé ;
- insère une identité canonique, une observation canonique et un détail synthétique V5 ;
- mémorise tous les champs historiques ;
- applique uniquement V6 ;
- vérifie la version finale 6 et la provenance complétée ;
- compare tous les champs antérieurs et les nombres de lignes ;
- confirme que le trigger vaut de nouveau `ENABLED` ;
- confirme que `UPDATE` et `DELETE` restent refusés.

```text
STANDARD_TESTS=198
INTEGRATION_TESTS=16
FAILURES=0
ERRORS=0
SKIPPED=0
V5_PREFILLED_TO_V6_UPGRADE=PASS
APPEND_ONLY_TRIGGER_REENABLED=PASS
REAL_PROVIDER_CALLS=0
```

## 5. Reprise humaine sûre

La reprise corrective a été exécutée le 2026-08-15 avec les cinq clés réseau à l’état bloqué. À
`08:27:12+02:00`, Flyway a appliqué une migration de V5 vers V6 sur la base persistante, puis
l’application a terminé son démarrage. L’interface a affiché l’état `LOCKED` et les quatre
bloqueurs attendus ; aucune action de préparation ou de confirmation fournisseur n’était
disponible.

La recherche locale du `2026-08-12` en zone `Europe/Paris` a ensuite retrouvé l’identité
`9740bb59-0207-31a3-a6ae-5c8463255887`, le match `Synthetic Home FC — Synthetic Away FC`, la
provenance `SYNTHETIC_FIXTURE`, le détail `event-details-nominal` et les deux versions append-only.
Le détail affiché conserve le parseur `event-details-v1`, les métadonnées de fixture et son hash.

À `08:34:52+02:00`, l’arrêt demandé par l’opérateur s’est terminé proprement : Tomcat a confirmé
son arrêt gracieux, puis JPA et Hikari ont fermé leurs ressources. Aucun appel fournisseur ni
snapshot fournisseur n’a été produit pendant cette reprise. La campagne réelle pourra être
réactivée uniquement lors d’un démarrage distinct, selon le runbook.

Ne pas exécuter `flyway repair`, ne pas supprimer la base ou ses observations et ne pas modifier le
schéma manuellement.

```text
DATABASE_VERSION_AFTER_CORRECTIVE_RETRY=V6
LOCAL_CONFIGURATION_RELOCKED_AFTER_INCIDENT=YES
LOCAL_V5_TO_V6_CORRECTIVE_RETRY=PASS
SYNTHETIC_HISTORY_PRESERVED=YES
APPLICATION_STARTED_AFTER_CORRECTIVE_MIGRATION=YES
APPLICATION_STOPPED=YES
PROVIDER_CALL_ATTEMPTS=0
RAW_PROVIDER_SNAPSHOT_CREATED_DURING_RETRY=NO
J4_REAL_EVENT_16386245_HUMAN_QUALIFICATION=NOT_RUN
J4_REAL_EVENT_16421052_HUMAN_QUALIFICATION=NOT_RUN
J4_REAL_PHASE_1_HUMAN_QUALIFICATION=PENDING
J4_WORK_ORDER=ACTIVE
J4_CAN_BE_CLOSED=NO
```
