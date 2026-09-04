# WO-SS-20260904-048 — Identités mTLS locales liées au run WO-046

- **Statut :** `IMPLEMENTATION_AUTHORIZED`
- **Jalon :** après J9 — préalable distinct à la campagne réelle locale WO-046
- **Préparé le :** 2026-09-04
- **Préparation UTC :** `2026-09-04T17:14:44.3290393Z`
- **Préparation Europe/Paris :** `2026-09-04T19:14:44.3290393+02:00`
- **Branche :** `codex/j9-wo048-wo046-local-mtls-identity-provisioning`
- **Worktree :** `.tmp/w48`
- **Base exacte :** `e771c2a5fedc508fbd0a420ae4596166251d82cd`
- **Work Order consommateur :** `WO-SS-20260904-046-j9-j7-real-local-e2e-campaign`
- **Changement runtime autorisé :** oui, outillage PKI-only et qualification hors ligne uniquement

## 1. Décision propriétaire reçue

Le propriétaire autorise l'ouverture et l'implémentation de ce Work Order distinct :

```text
J9_WO048_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO048_OPEN_WORK_ORDER_AUTHORIZED=YES
J9_WO048_WORK_ORDER=WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning
J9_WO048_BRANCH=codex/j9-wo048-wo046-local-mtls-identity-provisioning
J9_WO048_BASE_COMMIT=e771c2a5fedc508fbd0a420ae4596166251d82cd
J9_WO048_SCOPE=IMPLEMENT_PROVISION_SELECT_AND_QUALIFY_NEW_RUN_BOUND_LOCAL_MTLS_IDENTITIES_FOR_WO046_WITH_PKI_ONLY_FAIL_CLOSED_TOOLING
J9_WO048_PKI_ONLY_TOOLING_IMPLEMENTATION_AUTHORIZED=YES
J9_WO048_OFFLINE_CERTIFICATE_QUALIFICATION_AUTHORIZED=YES
J9_WO048_CERTIFICATE_STORE_MUTATION_AUTHORIZED=YES_EXACT_OWNED_CERTIFICATES_ONLY
J9_WO048_CLIENT_PRIVATE_KEY_EXPORT_AUTHORIZED=NO
J9_WO048_CERTIFICATE_FINGERPRINT_GIT_POLICY=PRIVATE_EXTERNAL_RECORD_ONLY
J9_WO048_DATABASE_START_READ_OR_WRITE_AUTHORIZED=NO
J9_WO048_RECEIVER_OR_LOCAL_LAB_START_AUTHORIZED=NO
J9_WO048_TLS_HANDSHAKE_OR_SOCKET_AUTHORIZED=NO
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```
Cette décision ne vaut ni validation de l'implémentation, ni autorisation d'utiliser la future
identité pour un handshake TLS. Elle n'autorise aucun accès à PostgreSQL, aucun démarrage des
applications et aucun POST.

## 2. Constat initial

La reprise WO-046 a établi, sans ouvrir de clé privée, que le magasin
`Cert:\CurrentUser\My` ne contenait aucun certificat lors de son inventaire autorisé :

```text
CURRENT_USER_MY_CERTIFICATE_COUNT=0
J7_RELATED_PUBLIC_IDENTITY_COUNT=0
MTLS_IDENTITY_SELECTION_STATUS=NOT_SELECTED_NO_EXISTING_CANDIDATE
```

Les scripts historiques WO-036 ne peuvent pas être exécutés tels quels sous la présente portée.
Ils sont liés à WO-036 et préparent également Docker, deux bases, des credentials et un état
d'infrastructure complet. Leur cleanup attend ces mêmes ressources. Réutiliser ce chemin
dépasserait l'autorisation PKI-only et rendrait la preuve de propriété ambiguë.

Une identité synthétique WO-036 ne doit pas être promue. Le nouveau matériel doit être créé pour
WO-046, être lié à un identifiant de run neuf et demeurer local, éphémère, directement approuvé et
inapte à toute présentation comme profil de production.

## 3. Objectif borné

WO-048 doit :

1. implémenter un provisionneur PKI-only WO-046 et son cleanup exact dédié ;
2. créer une identité cliente neuve dans `CurrentUser\My`, avec clé CNG non exportable ;
3. créer une identité serveur receiver locale pour l'origine exacte
   `https://127.0.0.1:8444` ;
4. préparer, exclusivement dans une racine privée hors dépôt, le PKCS#12 serveur, le truststore
   client du receiver, les certificats publics et les secrets associés ;
5. importer uniquement le certificat serveur public exact dans `CurrentUser\Root` ;
6. contrôler l'interopérabilité statique avec `Windows-MY`, `Windows-ROOT`, `SunMSCAPI` et les
   contraintes INT-001, sans effectuer de handshake ;
7. sélectionner les identités par des métadonnées publiques exactes dans l'état privé ;
8. garantir une annulation fail-closed fondée sur la propriété complète et non sur un sujet ou un
   PID isolé ;
9. produire une preuve versionnée expurgée qui ne contient aucune empreinte réelle, aucun chemin
   privé, certificat, secret ou clé ;
10. laisser WO-046 arrêté avant la base primaire, le manifeste, le owner-go et le POST.

## 4. Profil PKI local autorisé

### 4.1 Identité cliente Local Lab

Le profil exact est :

```text
STORE=Cert:\CurrentUser\My
JAVA_KEY_STORE=Windows-MY
JAVA_PROVIDER=SunMSCAPI
SUBJECT=CN=WO046 sender <runId UUID-D>,OU=WO-046,O=Betting Project Local Qualification
PUBLIC_KEY=RSA_3072
SIGNATURE_HASH=SHA256
BASIC_CONSTRAINTS=CA_FALSE
EKU=CRITICAL_CLIENT_AUTH_1.3.6.1.5.5.7.3.2
KEY_USAGE=DIGITAL_SIGNATURE
CLIENT_SAN=ABSENT
PRIVATE_KEY=WINDOWS_CNG_NON_EXPORTABLE
MAXIMUM_VALIDITY_DAYS=7
```

La qualification doit prouver `HasPrivateKey=true`, `CngExportPolicies=None`, une période de
validité courante, une correspondance unique dans `Windows-MY` et une clé opaque côté Java
(`PrivateKey.getEncoded()==null`). Aucune exportation PFX/PKCS#12 de la clé cliente n'est permise.

La sélection runtime utilise `SHA-256(DER leaf)`, normalisé en 64 caractères hexadécimaux
minuscules. Le thumbprint Windows SHA-1 majuscule sur 40 caractères sert uniquement à adresser
l'objet exact dans le magasin et lors du cleanup ; il ne remplace jamais l'identité SHA-256.

### 4.2 Identité serveur receiver

Le profil exact est :

```text
SUBJECT=CN=127.0.0.1,OU=WO-046,O=Betting Project Local Qualification
PUBLIC_KEY=RSA_3072
SIGNATURE=SHA256WITHRSA
BASIC_CONSTRAINTS=CA_FALSE
EKU=SERVER_AUTH_1.3.6.1.5.5.7.3.1
KEY_USAGE=DIGITAL_SIGNATURE_KEY_ENCIPHERMENT
SAN=IP_127.0.0.1_EXACT_ONLY
MAXIMUM_VALIDITY_DAYS=7
KEY_STORE_TYPE=PKCS12
TRUST_STORE_TYPE=PKCS12
```

Le keystore serveur contient une seule entrée privée. Son certificat public exact est importé
dans `CurrentUser\Root`. Le truststore receiver contient uniquement le certificat public client
de ce run. Les mots de passe et les chemins absolus restent dans l'état privé. Aucun magasin JVM
par défaut, trust-all, contournement de hostname, fallback HTTP, redirection ou bundle alternatif
n'est admis.

## 5. Racine privée et propriété

Chaque exécution utilise un enfant GUID immédiat neuf :

```text
%LOCALAPPDATA%\SofaScoreLocalLab\qualifications\WO-SS-20260904-046\<runId UUID-D>
```

Avant toute création de matériel, l'outil doit vérifier :

- chemin absolu canonique et confinement exact sous la racine attendue ;
- enfant GUID immédiat uniquement ;
- absence de reparse point sur la racine et ses descendants ;
- SID Windows courant comme propriétaire ;
- héritage ACL désactivé et `FullControl` réservé à ce SID ;
- verrou exclusif et marqueur propriétaire liés au run.

Après chaque création de certificat, l'ordre obligatoire est :

1. conserver immédiatement l'objet exact retourné ;
2. calculer sujet exact, thumbprint SHA-1 et SHA-256 des octets DER ;
3. enregistrer la propriété en mémoire ;
4. persister l'identité de propriété dans l'état privé ;
5. seulement ensuite inspecter la clé, les extensions et exporter le certificat public.

L'identité de propriété et de suppression est :

```text
STORE_PLUS_SHA1_THUMBPRINT_PLUS_DER_SHA256_PLUS_EXACT_SUBJECT
```

Une suppression fondée uniquement sur le sujet, le thumbprint ou une énumération large est
interdite. En cas d'échec, l'outil doit supprimer exclusivement les certificats et conteneurs de
clés qu'il possède après concordance complète, vérifier leur absence, puis supprimer uniquement
la racine GUID validée. L'état et le verrou sont retirés en dernier.

Après succès, les artefacts restent en place jusqu'à une décision distincte d'abandon ou au
postflight WO-046. Aucun cleanup global de magasin n'est permis.

## 6. Politique de confidentialité des empreintes

La décision propriétaire résout la contradiction entre l'ancienne exigence WO-046 et le runbook
en retenant :

```text
CERTIFICATE_FINGERPRINT_GIT_POLICY=PRIVATE_EXTERNAL_RECORD_ONLY
```

Les empreintes leaf exactes, thumbprints Windows, numéros de série, DN détaillés, fichiers `.cer`,
alias, chemins privés et inventaires de magasins restent dans l'état externe protégé. La preuve
Git ne contient que des statuts expurgés, les propriétés cryptographiques non identifiantes et le
SHA-256 d'un relevé public-métadonnées privé, immuable et sans secret.

Le futur manifeste WO-046 ne publiera pas l'empreinte réelle. Il pourra uniquement lier la
référence logique et le SHA-256 du relevé privé. Le futur owner-go V2, lui-même externe à Git,
devra contenir le `CLIENT_CERTIFICATE_SHA256` exact issu de ce relevé. Cette harmonisation bornée
ne modifie ni le protocole mTLS, ni la sélection runtime, ni l'allowlist receiver.

## 7. Outillage attendu

Les scripts dédiés doivent au minimum offrir :

- une commande de provisionnement sans Docker ni base ;
- une commande de qualification hors ligne et expurgée ;
- une commande de cleanup exact, inactive par défaut et liée à l'état propriétaire ;
- un schéma d'état privé versionné, fail-closed et protégé contre l'injection de chemins ;
- un verrou exclusif empêchant la concurrence ou la réutilisation d'un run ;
- des timeouts bornés pour chaque processus natif ;
- capture bornée et expurgée de stdout/stderr ;
- contrôle des codes de sortie et des fichiers attendus ;
- nettoyage des fichiers partiels et processus natifs possédés après échec ;
- sortie publique sans secret, chemin privé ou empreinte réelle.

L'outillage ne doit jamais démarrer Docker, Java, PostgreSQL, le Local Lab ou INT-001. Il ne doit
ouvrir aucune socket et ne doit jamais solliciter SofaScore ou une autre origine réseau.

## 8. Cas de qualification obligatoires

### 8.1 Tests hors ligne sans mutation réelle

- rejet d'un run ID invalide ou non canonique ;
- rejet d'une racine hors confinement ou contenant un reparse point ;
- rejet d'un état absent, incomplet, corrompu ou d'une version inconnue ;
- rejet d'une ACL ou d'un propriétaire divergents ;
- rejet d'une collision de racine/run ;
- rejet d'une identité cliente sans EKU, KU, clé privée ou non-exportabilité attendus ;
- rejet d'un certificat serveur sans SAN IP exact ou avec un SAN supplémentaire ;
- rejet d'un PKCS#12 avec zéro ou plusieurs entrées privées ;
- rejet d'une empreinte mal formée, non unique ou divergente ;
- simulation d'échec avant et après persistance de propriété ;
- preuve que le rollback ne cible jamais un objet non possédé ;
- preuve que Docker, la base, Java, un listener et le réseau ne sont jamais sollicités.

### 8.2 Qualification hôte bornée

Après réussite des tests et revue du script :

- exécution unique du provisionneur sur le poste Windows courant ;
- attestation privée des deux identités et des stores ;
- vérification publique expurgée de toutes les propriétés exigées ;
- vérification `Windows-MY`/`Windows-ROOT` via Java sans handshake ;
- absence de listener avant et après ;
- absence de processus natif résiduel ;
- absence de fichier privé dans le dépôt ou le diff ;
- conservation contrôlée du run réussi pour la future campagne WO-046.

La qualification hôte ne doit ni démarrer le receiver ou le sender, ni lire la base primaire, ni
envoyer un octet sur une socket.

## 9. Critères d'acceptation

WO-048 pourra être soumis à validation propriétaire uniquement si :

1. le worktree est propre hors modifications du Work Order ;
2. `mvnw.cmd clean verify` reste vert ;
3. les tests PowerShell dédiés passent de façon reproductible ;
4. les propriétés PKI attendues sont toutes attestées ;
5. aucun secret, certificat, chemin privé ou fingerprint réel n'apparaît dans Git ou les sorties
   documentaires ;
6. le cleanup exact est qualifié sans suppression fondée sur un identifiant incomplet ;
7. les contrôles loopback, flags réseau et absence de démarrage restent inchangés ;
8. un rapport expurgé consigne les commandes, comptes, résultats et hashes admissibles ;
9. une revue adversariale ne laisse aucun finding P0/P1/P2 ouvert ;
10. le propriétaire valide séparément le résultat avant classement.

## 10. État initial effectif

```text
J9_WO048_STATUS=IMPLEMENTATION_AUTHORIZED
J9_WO048_PKI_ONLY_TOOLING_STATUS=NOT_IMPLEMENTED
J9_WO048_HOST_PROVISIONING_STATUS=NOT_STARTED
J9_WO048_MTLS_IDENTITY_STATUS=NOT_SELECTED
J9_WO048_PRIVATE_METADATA_RECORD_STATUS=NOT_CREATED
J9_WO048_QUALIFICATION_STATUS=DRAFT
J9_WO048_OWNER_REVIEW_REQUIRED=YES
J9_WO048_WORK_ORDER_MOVE_TO_COMPLETED=NO

J9_WO046_STATUS=PAUSED_PENDING_WO048
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_MANIFEST_CREATED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_DIRECT_IMPORT_ATTEMPTS=0

J9_WO048_DATABASE_START_READ_OR_WRITE_AUTHORIZED=NO
J9_WO048_RECEIVER_OR_LOCAL_LAB_START_AUTHORIZED=NO
J9_WO048_TLS_HANDSHAKE_OR_SOCKET_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```
