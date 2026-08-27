# Runbook J6 — Historique, sauvegarde, restauration et rétention

## 1. Objet et autorisations

Ce runbook couvre deux opérations distinctes :

1. qualifier l'historique J6 avec le corpus synthétique, sans réseau ;
2. préparer puis, seulement après autorisation explicite, purger les octets bruts éligibles.

La consultation, l'import synthétique, l'aperçu de rétention et la sauvegarde/restauration ne
suppriment aucune donnée de la base primaire. Le mode `Execute` de la rétention supprime des octets
et doit faire l'objet d'une décision opérateur distincte. Il n'existe ni bouton Web, ni tâche
planifiée, ni purge automatique.

```text
PROVIDER_CALL_REQUIRED=NO
POLLING_OR_SCHEDULING=NO
PRIMARY_DATABASE_PURGE_DEFAULT=NOT_AUTHORIZED
NORMALIZED_OBSERVATION_DELETION=IMPOSSIBLE_BY_DESIGN
```

## 2. Prérequis

- Windows 11, Java 25 et Docker Desktop ;
- PowerShell 7.4 ou plus récent pour préserver les pipelines binaires natifs ;
- exécutable `age` disponible dans `PATH` ou fourni avec `-AgePath` ;
- PostgreSQL local démarré et sain ;
- Flyway V26 appliqué ; la rétention reste définie par V22, V23 étend seulement
  `export_manifest` pour J7, V24 élargit la portée du cache de découverte tournoi, V25 ajoute
  uniquement la provenance de l'import JSON local et V26 autorise le parseur incidents courant ;
- application liée uniquement à `127.0.0.1` ;
- toutes les voies J3/J4/J5, y compris la découverte tournoi, désactivées et
  `connector_control` à `LOCKED` ;
- destination de sauvegarde absolue, existante et située hors du dépôt ;
- espace disque suffisant pour la sauvegarde chiffrée et la restauration temporaire.

Ne jamais placer la sauvegarde, son manifeste, une phrase secrète ou un dump dans Git. Le script
refuse un chemin de sauvegarde situé dans le dépôt et refuse d'écraser un fichier existant.

## 3. Qualification humaine de l'historique hors ligne

### 3.1 Démarrer localement

Depuis la racine du dépôt :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Ouvrir `http://127.0.0.1:8087`. Vérifier avant l'import :

- l'adresse locale exacte ;
- les contrôles réseau désactivés et verrouillés ;
- le panneau J6 de rétention en lecture seule ;
- l'absence de bouton de purge.

### 3.2 Importer la démonstration

Dans l'interface, utiliser **Charger la démonstration J6**. L'action est un POST protégé par le
jeton de formulaire local ; elle ne contacte aucun fournisseur.

Contrôler ensuite :

1. la présence des cinq flux ;
2. le filtre de flux et la pagination ;
3. la version `BASELINE` de chaque flux ;
4. l'état terminal `finished` ;
5. les versions `SYNTHETIC_CHANGE` placées après cet état terminal, sans les confondre avec des
   réponses fournisseur ;
6. la comparaison de deux versions consécutives ;
7. la comparaison arbitraire de deux versions du même flux ;
8. les valeurs ajoutées, retirées et modifiées ;
9. les informations de provenance, parseur et complétude ;
10. l'absence de JSON brut ou de téléchargement.

Les classifications `LATE_ENRICHMENT` et `LATE_CORRECTION` sont réservées aux versions de provenance
fournisseur. Leur détection, y compris lors d'une comparaison qui saute un état terminal
intermédiaire, est couverte par les tests automatisés et ne doit pas être simulée par un faux
payload fournisseur dans l'interface hors ligne.

Relancer une seconde fois l'import et vérifier qu'aucune version métier supplémentaire n'apparaît.
Arrêter ensuite proprement l'application avec `Ctrl+C` avant toute opération de sauvegarde ou de
rétention.

## 4. Aperçu de rétention sans mutation

L'application Web doit être arrêtée. Le script force les opt-ins réseau à `false`, démarre une
commande Spring non Web, vérifie le verrou persistant puis s'arrête.

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 -Mode Preview
```

Sortie attendue :

```text
J6_RETENTION_MODE=PREVIEW
J6_RETENTION_DAYS=30
J6_RETENTION_CUTOFF_AT=<instant UTC>
J6_RETENTION_TOTAL_ELIGIBLE=<N>
J6_RETENTION_SELECTED=<0 à 500>
J6_RETENTION_PLAN_SHA256=<64 caractères hexadécimaux>
J6_RETENTION_CONFIRMATION=PURGER <N> PAYLOADS J6 <PLAN_SHA256>
J6_RETENTION_MUTATION=NO
```

Si `J6_RETENTION_SELECTED=0`, ne pas poursuivre : aucun payload n'est éligible. Si
`J6_RETENTION_TRUNCATED=true`, un seul lot de 500 au maximum sera exécutable. Un lot ultérieur exige
au minimum un nouvel aperçu et une nouvelle confirmation ; la preuve de sauvegarde ne peut être
réutilisée que si sa couverture qualifiée englobe encore tous les candidats concernés.

Ne jamais recomposer manuellement le cutoff ou le SHA-256. Les quatre valeurs utilisées en mode
`Execute` doivent provenir du même aperçu final.

## 5. Créer et qualifier la sauvegarde chiffrée

Choisir un nouveau nom absolu hors dépôt. Le répertoire parent doit déjà exister. Exemple :

```powershell
pwsh -NoProfile -File .\scripts\Backup-Restore-J6.ps1 `
  -Destination 'D:\SofaScoreBackups\sofascore-j6-20260818.age'
```

Si `age` n'est pas dans `PATH` :

```powershell
pwsh -NoProfile -File .\scripts\Backup-Restore-J6.ps1 `
  -Destination 'D:\SofaScoreBackups\sofascore-j6-20260818.age' `
  -AgePath 'C:\Tools\age\age.exe'
```

`age` demande interactivement la phrase secrète pendant le chiffrement puis le déchiffrement. Ne
pas passer cette phrase sur la ligne de commande, ne pas la coller dans un rapport et ne pas la
conserver dans `.env`.

Le script :

1. refuse une application encore à l'écoute sur le port 8087 ;
2. vérifie Compose, le verrou réseau et la version courante Flyway V26 ;
3. vérifie le SHA-256 réel de chaque payload retenu ;
4. dirige `pg_dump --format=custom` directement vers `age -p` ;
5. crée un manifeste initial non qualifié ;
6. restaure le flux déchiffré directement dans une base temporaire ;
7. compare version Flyway, comptes, couverture et empreintes source/restauration ;
8. inscrit `restoreQualified=true` uniquement après égalité ;
9. supprime la base temporaire dans tous les cas.

Une réussite se termine par :

```text
J6_BACKUP_RESULT=QUALIFIED
J6_BACKUP_CIPHER_SHA256=<hash>
J6_BACKUP_MANIFEST_SHA256=<hash>
J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=<id>
J6_BACKUP_COVERAGE_RECEIVED_AT=<instant UTC>
```

Deux fichiers restent dans le répertoire externe : le fichier `.age` et
`<fichier>.age.manifest.json`. Conserver les deux ensemble. Si la qualification échoue, ils ne
constituent pas une preuve valide et le mode `Execute` doit rester interdit.

## 6. Refaire l'aperçu final

Après la sauvegarde qualifiée et sans redémarrer l'application Web :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 -Mode Preview
```

Copier exactement depuis cette sortie :

- `J6_RETENTION_CUTOFF_AT` ;
- `J6_RETENTION_PLAN_SHA256` ;
- `J6_RETENTION_CONFIRMATION` ;
- le nombre sélectionné.

Le cutoff fait partie du hash. Il est donc normal qu'un aperçu relancé produise un autre hash,
même si la liste de candidats semble identique.

## 7. Porte d'autorisation avant exécution

Avant toute purge de la base locale primaire, réunir explicitement :

```text
APPLICATION_STOPPED=YES
CONNECTOR_CONTROL=LOCKED
ALL_NETWORK_OPT_INS=FALSE
BACKUP_ENCRYPTED=YES
BACKUP_RESTORE_QUALIFIED=YES
BACKUP_AND_MANIFEST_STORED_OUTSIDE_REPOSITORY=YES
FINAL_PREVIEW_REVIEWED=YES
PRIMARY_PURGE_AUTHORIZED_BY_OPERATOR=YES
```

Sans ces huit confirmations, s'arrêter. Une sauvegarde qualifiée n'autorise pas à elle seule la
purge.

## 8. Exécuter un lot autorisé

Remplacer les quatre valeurs par celles du manifeste et du même aperçu final :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 `
  -Mode Execute `
  -BackupManifest 'D:\SofaScoreBackups\sofascore-j6-20260818.age.manifest.json' `
  -CutoffAt '<J6_RETENTION_CUTOFF_AT>' `
  -PlanSha256 '<J6_RETENTION_PLAN_SHA256>' `
  -ConfirmationPhrase 'PURGER <N> PAYLOADS J6 <J6_RETENTION_PLAN_SHA256>'
```

Le script revérifie le nom du fichier chiffré, son hash, Flyway V26, l'égalité complète des preuves
source/restauration et la couverture. Le service recalcule ensuite le plan, la phrase et la
couverture avant que l'adaptateur ne les revérifie sous verrou transactionnel.

Une réussite affiche uniquement un bilan minimisé :

```text
J6_RETENTION_RESULT=PURGED
J6_RETENTION_BATCH_ID=<UUID>
J6_RETENTION_PLAN_SHA256=<hash>
J6_RETENTION_PURGED=<N>
J6_RETENTION_PURGED_BYTES=<octets>
J6_RETENTION_EXECUTED_AT=<instant UTC>
```

Le script n'affiche ni payload, ni mot de passe, ni phrase secrète de sauvegarde.

## 9. Contrôles après exécution

Relancer l'aperçu. Les éléments du lot précédent ne doivent plus être candidats :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 -Mode Preview
```

Redémarrer ensuite l'application seulement pour un contrôle en lecture :

- le tableau de bord indique les nouveaux compteurs ;
- les historiques et comparaisons normalisés restent disponibles ;
- les traces concernées affichent `PAYLOAD_PURGED` ;
- l'inspection JSON brute de ces snapshots n'est plus disponible ;
- aucune ligne historique n'a disparu ;
- les contrôles réseau restent verrouillés.

Arrêter proprement l'application après vérification.

## 10. Refus et incidents attendus

| Résultat | Signification | Action |
|---|---|---|
| `EMPTY_PLAN` | aucun candidat dans le plan | ne rien purger |
| `STALE_PLAN` | cutoff, candidats ou hash ont changé | refaire un aperçu et réévaluer |
| `INVALID_CONFIRMATION` | phrase différente | ne pas corriger approximativement ; recopier l'aperçu |
| `BACKUP_NOT_QUALIFIED` | restauration absente ou date invalide | refaire sauvegarde et restauration |
| `BACKUP_COVERAGE_INSUFFICIENT` | preuve antérieure à un candidat | créer une nouvelle sauvegarde |
| `DATABASE_MUTATION_MISMATCH` | nombre de lignes inattendu | transaction annulée ; diagnostiquer avant tout nouvel essai |
| `LOCAL_EXECUTION_FAILURE` | configuration, manifeste ou base invalide | conserver les preuves et diagnostiquer localement |

Ne jamais contourner un refus par une modification SQL manuelle, une désactivation de trigger ou
une édition du manifeste. Une restauration de secours doit être effectuée vers une base distincte
avant toute décision de remplacement de la base primaire.

## 11. Clôture de qualification J6

Pour passer le Work Order à `VALIDATED`, consigner au minimum :

- résultat des tests Maven standard et intégration ;
- contrôle humain des cinq flux et des différences ;
- idempotence de l'import synthétique ;
- sauvegarde chiffrée restaurée et qualifiée ;
- contrôle des états réseau verrouillés ;
- absence de secret et de payload dans Git et les journaux ;
- si une purge primaire a été explicitement autorisée, identifiant du lot et compte minimisé ;
- arrêt final de l'application.

Les fichiers `.age`, manifestes locaux et captures d'écran restent hors Git.
