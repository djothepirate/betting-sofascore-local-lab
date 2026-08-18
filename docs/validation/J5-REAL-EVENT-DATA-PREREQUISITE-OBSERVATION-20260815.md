# J5 réel — Observation préalable issue du second test J4

- **Date :** 2026-08-15
- **Source :** déclaration opérateur et huit captures locales non versionnées
- **Appel J5 réel :** `NOT_RUN`
- **Payload brut reproduit :** `NO`
- **Fichier `.env` lu ou modifié par l'agent :** `NO`

## 1. Configuration J4 déclarée

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com/
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS
```

Cette configuration correspond à la voie J4 sous-étape 2. Elle n'autorise aucune famille J5.

## 2. Observations minimisées

```text
J4_REFRESH_EVENT_16412917=PASS
J4_REFRESH_EVENT_16412917_LATEST_STATUS=interrupted
J4_REFRESH_EVENT_16412917_LATEST_SNAPSHOT=25
J4_REFRESH_EVENT_16412917_VERSION_COUNT=4
J4_NEW_EVENT_16391135=PASS
J4_NEW_EVENT_16391135_MATCH=Bolton_Wanderers__Preston_North_End
J4_NEW_EVENT_16391135_STATUS=inprogress
J4_NEW_EVENT_16391135_SNAPSHOT=26
J4_NEW_EVENT_16391135_PAYLOAD_BYTES=10068
J4_NEW_EVENT_16391135_SHA256=e860c867fe1d51f6b59777cdb2f23098ee10b36e4a7e4a16f9bc7ca988673723
J4_NEW_EVENT_16391135_PARSER=event-details-v2
J4_NEW_EVENT_16391135_CANONICAL_ID=f7f7a708-0200-38da-8f3f-a00884761072
J5_REAL_EVENT_16391135_LOCAL_DATA=ABSENT_EXPLICIT
J5_REAL_EVENT_16391135_NETWORK_FALLBACK=NO
J5_SYNTHETIC_IMPORT_ON_16391135=REJECTED_EVENT_ID_MISMATCH
J5_PROVIDER_CALLS_DURING_OBSERVATION=0
```

## 3. Conclusion

La frontière existante fonctionne correctement : J4 peut ajouter une identité réelle et son
historique append-only, J5 peut la consulter, mais le corpus synthétique `900001` ne peut pas y
être injecté. La voie de qualification réelle J5 doit donc utiliser une provenance
`PROVIDER_SNAPSHOT`, des parseurs fournisseur distincts et un contrôle réseau dédié.

La phrase de confirmation J4 visible dans une capture était ponctuelle et consommée. Elle n'est
pas reprise dans cette preuve. Les captures temporaires restent hors Git.
