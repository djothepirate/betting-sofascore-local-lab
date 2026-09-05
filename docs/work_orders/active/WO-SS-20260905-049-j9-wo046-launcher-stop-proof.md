# WO-SS-20260905-049 — Lanceur WO-046 et justificatif d'arrêt hors ligne

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Ouvert le :** 2026-09-05
- **Base exacte :** `a4dabd8023e957bee23c84c00fba0088146a5d49`
- **Branche :** `codex/ss-20260905-049-j9-wo046-launcher-stop-proof`
- **Worktree :** `.tmp/w49`, neuf et distinct de WO-046, non ouvert dans Eclipse.
- **Résultat attendu :** `PASS_OFFLINE_FAIL_CLOSED`, puis revue propriétaire distincte.

## 1. Autorité

Le propriétaire autorise l'ouverture d'un Work Order distinct limité à la correction et à la
qualification hors ligne du lanceur et du justificatif d'arrêt, sans nouveau manifeste,
sans nouveau go et sans POST réel. Le numéro 049 et la branche ont été vérifiés disponibles ;
le worktree source WO-046 est propre à la base indiquée. Aucune autorité de reprise n'est inférée.

```text
WO049_IMPLEMENTATION=AUTHORIZED_LAUNCH_PREPARATION_AND_STOP_PROOF_ONLY
WO049_QUALIFICATION=OFFLINE_SYNTHETIC_ONLY
WO049_APPLICATION_OR_RECEIVER_START=NO
WO049_DATABASE_START_OR_ACCESS=NO
WO049_CERTIFICATE_STORE_ACCESS_OR_MUTATION=NO
WO049_PROVIDER_OR_RECEIVER_HTTP=NO
WO049_PRIMARY_DATABASE_TOUCH=NO
WO046_NEW_MANIFEST=NO
WO046_NEW_OWNER_GO=NO
WO046_REAL_POST=NO
WO046_RESUME=REQUIRES_SEPARATE_OWNER_DECISION
PUSH_OR_MERGE=NO
```

## 2. Faits et preuves gelées

Le [rapport R1](../../validation/J9-WO046-J7-REAL-LOCAL-E2E-R1-STOPPED-20260905.md), SHA-256
`819572974a6f43c28da4e4191766932c46c43102895ad58e3e436d4b90a783bd`, constate :

- noms d'environnement erronés dans le lanceur ad hoc Codex : seul `enabled` s'applique,
  tandis que le mode, les qualifications, l'origine, le certificat et le go restent absents
  ou désactivés ; refus Java avant listener, aucun POST d'import ;
- concaténations dans un tableau PowerShell ayant séparé les valeurs de `GO_ID` et
  `STOPPED_AT_UTC` sur des lignes supplémentaires dans le justificatif technique d'arrêt ;
- grant V2 canonique valide, révoqué sans consommation ; preuve SQL indépendante de révocation.

Ni le manifeste R1, ni les preuves R1, ni les scripts ad hoc historiques, ni le grant révoqué ne
sont réécrits. WO-046 reste `STOPPED_PRE_IMPORT_LOCAL_LAB_CONFIGURATION_BINDING_REFUSED`.

## 3. Correction bornée

1. Versionner un module de préparation de lancement : un mapping explicite des noms attendus,
   validation fail-closed de la matrice complète, arguments Java séparés, environnement préparé
   sans mutation de l'environnement du parent. La préparation ne lance aucun processus métier.
2. Refuser les références go/certificat incomplètes, origines non loopback, aliases erronés,
   overrides inattendus et tentatives de modifier les flags fournisseur, retries ou timeouts.
3. Produire et relire strictement le justificatif technique d'arrêt : UTF-8 sans BOM, LF final,
   ordre fixe, une valeur non vide par ligne, références exactes et hash reproductible.
4. Qualifier le mapping réellement retourné par PowerShell avec le Binder Spring et les
   propriétés Java existantes. Les UUID, empreintes, identifiants et secrets des tests sont
   entièrement synthétiques. Aucun grant ou manifeste réel n'est produit.
5. Préserver les responsabilités de WO-046 : vérification fraîche des autorisations, des JAR,
   de la PKI, du schéma, ownership des processus, lancement, persistance et cleanup exécutables
   restent soumis à la campagne future ; ce module n'est pas un orchestrateur de campagne.

Pas de changement Java applicatif, receiver, endpoint, protocole J7, SQL/migration, ADR ou PDF.
Pas de lecture des payloads ou secrets privés R1, pas de nouvelle clé ou identité PKI.

## 4. Qualification et critères d'acceptation

- reproduire le mauvais binding R1 avec des valeurs synthétiques ; mapping corrigé entièrement
  cohérent via les classes Spring réelles, sans contexte applicatif ni socket ;
- noms, valeurs, arguments JAR avec espaces, garde-fous JDK et isolation de l'environnement ;
- refus des champs manquants, supplémentaires, aliases, valeurs incohérentes ou injectées ;
- round-trip canonique du justificatif, hash stable, UUID/time/hash corrélés ;
- refus BOM, CRLF, ligne vide, duplicate, ordre incorrect, valeur vide, lignes séparées R1,
  champ inconnu et données supplémentaires ;
- parseurs PowerShell et Pester ciblé hors ligne ; aucun test Docker/PKI/HTTP ;
- build et tests Maven standards hors ligne après examen de leur périmètre ; s'ils nécessitent
  une capacité exclue, la limite est consignée sans prétendre à un PASS complet ;
- UTF-8, `git diff --check`, secrets, intégrité du manifeste et des preuves gelées ;
- rapport expurgé, commits locaux, Work Order actif jusqu'à validation propriétaire.

## 5. Suite

Même après validation de WO-049, WO-046 ne redémarre pas automatiquement. Un éventuel manifeste
successeur doit traiter explicitement le volume receiver conservé et les prérequis exacts ;
il exige une décision distincte, puis un nouveau go lié aux octets gelés avant l'import unique.
La contrainte d'unicité du manifeste et le grant R1 révoqué restent intacts.

## 6. Résultat soumis à la revue propriétaire

- Ouverture : `abd5e9be541a8f1d30ea0426e674477959ea3893`.
- Implémentation : `22130d16781cce41a370cbcdbf6cd1b7b91546c2`.
- [Rapport de qualification](../../validation/J9-WO049-LAUNCHER-STOP-PROOF-OFFLINE-QUALIFICATION-20260905.md),
  13960 octets, SHA-256 `e273d917ca07b3a9a1cd303d93868ee22d2ab35e0b3b5a253cad271dbdf11ad8`.
- Résultat technique : `PASS_OFFLINE_FAIL_CLOSED` ; 20 tests Pester et 7 tests Java ciblés
  réussis ; build final borné : 1185 tests, zéro échec, zéro erreur, quatre tests ignorés.
- Mapping PowerShell qualifié par le Binder Spring réel ; arguments Java séparés et
  justificatif technique strictement canonique. Aucun changement Java applicatif ou SQL.
- La préparation reste sans lancement : elle ne remplace pas la vérification fraîche des
  autorisations, des fichiers, de la PKI ou de l'état persistant dans une future campagne.

**Écart d'exécution à reconnaître explicitement :** la première vérification Maven hôte,
sans `-DskipITs=true`, a hérité des exécutions Failsafe du parent Spring Boot et lancé des
tests PostgreSQL isolés, hors du périmètre hors ligne autorisé. L'agent a interrompu cette
exécution après constat ; le rapport conserve les preuves partielles, sans les transformer
en qualification autorisée. La vérification finale exclut explicitement les IT, le test
J6 natif et les profils de qualification réseau. Le contrôle final constate zéro conteneur
Testcontainers, zéro processus Java WO-049, zéro listener 8087/8444/5433 ; les métadonnées
Docker du primaire indiquent `healthy`, sans requête SQL de contrôle sur celui-ci.

```text
QUALIFICATION_RESULT=PASS_OFFLINE_FAIL_CLOSED
OWNER_REVIEW_REQUIRED=YES
EXECUTION_DEVIATION_ACKNOWLEDGEMENT_REQUIRED=YES
WORK_ORDER_MOVE_TO_COMPLETED=NO
WO046_RESUME_AUTHORIZED=NO
WO046_NEW_MANIFEST_AUTHORIZED=NO
WO046_NEW_OWNER_GO_GRANTED=NO
WO046_REAL_POST_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PUSH_OR_MERGE=NO
```
