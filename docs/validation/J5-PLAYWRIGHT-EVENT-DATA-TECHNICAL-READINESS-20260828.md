# Readiness technique - transport J5 Playwright des donnees evenement

## 1. Statut

```text
WORK_ORDER=WO-SS-20260827-015
WORK_ORDER_STATUS=VALIDATED
ADR_SS_001_VERSION=1.4
IMPLEMENTATION_STATUS=COMPLETED
TECHNICAL_READINESS=PASS
LOCAL_READINESS=PASS
TECHNICAL_QUALIFICATION_PROVIDER_ACCESS=NO
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_29
LOCAL_CONFIGURATION_RELOCKED=YES_8_KEYS
J4_LOCKED_AFTER_RESTART=PASS
J5_LOCKED_AFTER_RESTART=PASS
CLOSURE=AUTHORIZED_BY_OWNER_2026_08_29
```

Cette readiness enregistre les preuves techniques finales du diff. Les essais Playwright ont vise
uniquement un serveur ephemere lie a `127.0.0.1` et n'ont pas contacte SofaScore. Le Work Order
est complete par la qualification humaine du `2026-08-29` et archive dans
`docs/work_orders/completed`. Les appels operateur restent separes des preuves techniques hors
ligne et n'autorisent aucune campagne supplementaire.

## 2. Contrat implemente

- runtime Playwright commun de WO-013 et WO-014, conforme a l'ADR-SS-001 v1.4 ;
- protocole IPC v5, commandes J5 allowlistees et corps issu de `Response.body()` ;
- un worker, un Chromium, un `BrowserContext` non persistant et une lease uniques par campagne ;
- ordre strict `EVENT_STATISTICS -> EVENT_INCIDENTS -> EVENT_LINEUPS` ;
- `404` conserve raw-first comme `ENDPOINT_UNAVAILABLE`, sans parsing ni retry, puis poursuite dans
  le meme contexte ;
- tout autre incident terminal, sans famille suivante, retry, fallback ou contexte de remplacement ;
- arret de la campagne exacte, acquittement en 500 ms, annulation en 2 s, nettoyage en 5 s et zero
  processus attribue residuel ;
- separation de la demande de fermeture et du nettoyage confirme : meme handle retentable avant
  mutation, aucune nouvelle commande apres la premiere demande et lease liberee seulement apres
  succes ;
- echec de nettoyage persistant fail-closed : lease conservee jusqu'au redemarrage du processus,
  sans chevauchement J3/J4, tandis que l'arret exact reste routable sans reecrire l'issue terminale ;
- latch d'arret global independant de l'historique terminal : etat et code d'un succes conserves,
  mais nouvelle preparation J5 bloquee jusqu'au redemarrage ;
- cache fournisseur J5 `NOT_APPLICABLE`, sans lecture ni ecriture ;
- aucun `RestClient`, FlareSolverr, proxy, profil persistant ou fallback local dans la voie
  fournisseur ;
- imports locaux J5 unitaire et multi-match inchanges et strictement sans worker ;
- migration V26 et parseur `event-incidents-v14` de WO-012 conserves ; aucune migration WO-015.

## 3. Preuves acquises

| Porte | Preuve | Etat |
|---|---|---|
| adaptateur J5 | campagne unique, ordre, identite, limite, fidelite, fermeture idempotente et erreurs | `PASS_20_TESTS` |
| protocole worker | version v5 et commandes J5 fermees compilees | `PASS_TARGETED` |
| sequence J5 loopback | `200/404/200`, trois routes exactes, meme worker/contexte | `PASS` |
| semantique `404` | poursuite vers `LINEUPS`, aucun retry ni nouveau contexte | `PASS` |
| lease partagee | J3 et J4 bloques pendant les trois familles, y compris apres le `404`, jusqu'a la fermeture J5 | `PASS_1_TEST` |
| echec de nettoyage | second passage apres erreur transitoire ; lease retenue apres erreur persistante | `PASS_2_TESTS` |
| arret exact terminal | callback superviseur encore routable, etat et code historiques conserves | `PASS` |
| latch d'arret global | historique `COMPLETED_LOCKED` conserve et rearmement refuse jusqu'au redemarrage | `PASS` |
| build du lanceur J5 | `clean package`, puis absence de la classe retiree dans les sorties Maven et l'archive worker | `PASS` |
| hygiene des rapports Maven | parite avec le scanner applicatif sur texte XML decode, attributs et proprietes | `PASS` |
| fichiers runtime | racines temporaires/profil/local-app-data isolees par run et canari recherche dans tous les fichiers | `PASS` |
| arret en vol | arret pendant `INCIDENTS` lent, zero `LINEUPS`, bornes stop/cleanup, zero residu | `PASS` |
| qualification Chromium ciblee | suite `ProviderPlaywrightLocalQualificationIT` | `PASS_14_TESTS` |
| acces fournisseur | aucune requete SofaScore pendant ces preuves | `NO` |
| cache fournisseur J5 | aucun contrat de cache dans la campagne J5 | `NOT_APPLICABLE` |
| migration | aucun fichier `db/migration` ajoute ou modifie par WO-015 | `PASS` |
| V26 / incidents V14 | prerequis WO-012 conserve | `PASS_NO_WO_015_CHANGE` |

Les executions ciblees ont termine avec zero echec, zero erreur et zero test ignore. Les suites
standard et PostgreSQL ont ensuite valide le diff complet sans charger Playwright.

## 4. Portes finales executees

Les commandes suivantes ont ete executees sur le diff final, dans cet ordre. Cette liste sert
egalement de procedure de reproduction :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
pwsh -NoProfile -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
git diff --check
```

Etat final :

```text
STANDARD_CLEAN_VERIFY=PASS_765_TESTS_0_FAILURE_0_ERROR_2_SKIPPED
INTEGRATION_VERIFY=PASS_53_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
VERIFY_LOCAL_WITH_INTEGRATION_TESTS=PASS
J5_CLEANUP_AND_LEASE_TARGETED_SUITE=PASS_78_TESTS
J5_PLAYWRIGHT_LOOPBACK_SCRIPT=PASS_11_PROTOCOL_SECURITY_TESTS_14_CHROMIUM_TESTS
FINAL_DIFF_CHECK=PASS
SECRET_AND_PAYLOAD_REVIEW=PASS
SERVER_ADDRESS_127_0_0_1=PASS
DEFAULT_PLAYWRIGHT_INERT=PASS
```

Les trois premieres commandes sont restees sans source Playwright active, sans Chromium et sans
appel fournisseur. Le script loopback a exige une action locale explicite et un cache Chromium
deja installe ; il a vise uniquement `127.0.0.1`.

## 5. Qualification humaine et cloture

Le `2026-08-29`, le proprietaire a execute depuis le lanceur PowerShell 7 une campagne J5 bornee a
Lille - Paris Saint-Germain, identite canonique
`c40066c9-987b-38d9-b415-869a453d2ad6` et identifiant fournisseur `16310930`. Le terminal
`COMPLETED_LOCKED` conserve exactement trois appels fournisseur ordonnes et zero import local :

| Famille | Snapshot | Taille | SHA-256 source | Parseur | Completude | Observation |
|---|---:|---:|---|---|---|---:|
| `EVENT_STATISTICS` | `603` | `25 745` octets | `8945c99bdd2d191737cfc6c82e24d2a94f9493a0165f71f540ae458e115b634b` | `event-statistics-v2` | `COMPLETE - 100 %` | `298` |
| `EVENT_INCIDENTS` | `604` | `55 115` octets | `8b1a1bfca25f259b486a555ad91d62e774725aa3e83d4f04c378053d7f5cf81c` | `event-incidents-v14` | `COMPLETE - 100 %` | `299` |
| `EVENT_LINEUPS` | `605` | `78 828` octets | `4d0fd84ccbe24dff0291efdadc33d742c3ea5a82b753dbd8a57f214734f0e82f` | `event-lineups-v2` | `COMPLETE - 100 %` | `300` |

Les trois vues normalisees restent consultables. Dans la meme session, J4 phase 2 a termine
`COMPLETED_LOCKED` apres un appel `EVENT_DETAILS` et conserve le snapshot `606`.

La verification J3 a d'abord termine 18 pages contigues, sans cache ni import, puis revele une
lacune ancienne du catalogue : deux candidats portaient une tabulation ou un saut de ligne dans
leurs metadonnees d'affichage. Leur rejet par le modele rendait alors tout le catalogue
`SNAPSHOT_PARSE_INCOMPATIBLE`.

Le correctif n'assouplit pas le modele et ne nettoie aucun libelle. Il conserve le brut et exclut
de la projection les deux occurrences distinctes deja parsees dont le nom de phase porte une
tabulation ou le nom du tournoi unique un saut de ligne. Le test de regression confirme
`AVAILABLE`, les pages sources conservees, une option sure et deux exclusions non resolvables. Les
valeurs blanches, categories invalides et conflits restent soumis aux invariants stricts existants.

Le retest proprietaire a ensuite confirme une nouvelle collecte J3 de 18 pages, le catalogue
`AVAILABLE`, `1 428` tournois actionnables, `338` occurrences exclues et la liste deroulante visible.
La selection Ligue 1 a produit le snapshot `643`, retenu `5 / 5` rencontres canoniques et termine
`COMPLETED` apres un appel.

La decision proprietaire valide le Work Order sur ces preuves. Le lanceur n'a pas modifie `.env` ;
les huit cles locales documentees ont ensuite ete reverrouillees explicitement. Un redemarrage de
controle a confirme J4 et J5 `LOCKED` sur `127.0.0.1:8087`. L'application a enfin ete arretee :
aucun listener, aucune JVM applicative et aucun worker attribue au worktree ne subsistent.

```text
TECHNICAL_QUALIFICATION_PROVIDER_ACCESS=NO
HUMAN_PROVIDER_ACCESS_PERFORMED=YES_OWNER_AUTHORIZED_2026_08_29
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_29
J5_REAL_TERMINAL_STATE=COMPLETED_LOCKED
J5_REAL_PROVIDER_CALLS=3
J5_PROVIDER_SCHEMA_VALIDATED=YES_CURRENT_PARSERS
J4_SAME_SESSION_REGRESSION=PASS_COMPLETED_LOCKED_SNAPSHOT_606
J3_CORRECTIVE_RETEST=PASS_AVAILABLE_18_PAGES_1428_OPTIONS_338_EXCLUDED
J3_TOURNAMENT_DISCOVERY_RETEST=PASS_COMPLETED_SNAPSHOT_643_5_OF_5
LOCAL_CONFIGURATION_RELOCKED=YES_8_KEYS
J4_LOCKED_AFTER_RESTART=PASS
J5_LOCKED_AFTER_RESTART=PASS
FINAL_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_OWNED_JAVA_PROCESS_COUNT=0
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
WORK_ORDER_LOCATION=docs/work_orders/completed
CLOSURE=AUTHORIZED_BY_OWNER_2026_08_29
```

Aucun payload, cookie, token, header, URI complete, phrase de confirmation ou donnee de session
n'entre dans cette preuve minimisee. Toute nouvelle campagne exige une nouvelle autorisation
proprietaire explicite.
