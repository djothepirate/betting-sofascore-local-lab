# J3 — Collecte directe, automatisation et calendriers conservés

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Cette procédure correspond à [WO-060](../work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md)
et à [ADR-SS-007 v0.2](../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md).
L'application reste accessible uniquement sur `http://127.0.0.1:8087` ou `http://localhost:8087`.

## 1. Première mise en service

La migration V56 initialise la préférence **activée**, en mode **au démarrage / changement de jour**.
Au premier démarrage du serveur Web local entièrement configuré pour J3, le moteur vise le jour
courant en **Europe/Paris**, après la reprise hors réseau des preuves J8. Il vérifie le dernier
succès avant de créer puis d'admettre l'ordre. Un succès manuel, importé ou entièrement en cache
pour cette date évite une nouvelle collecte opportuniste.

L'installation J0/J1 reste sans transport : les paramètres fournisseur et Playwright de
`.env.example` sont désactivés. La préférence automatique ne remplace pas la qualification J3,
l'origine et l'allowlist existantes, le worker local compatible, ni les gardes techniques.
Le tableau de bord indique le motif d'indisponibilité ; cette indisponibilité ne consomme pas
la tentative quotidienne. Une application configurée pour le fournisseur peut ouvrir son worker
J3 automatiquement selon la préférence adoptée dans ce WO. Aucun service ne démarre le Lab à sa place.

Le moteur ne démarre que dans le serveur Web local, après `ApplicationReadyEvent`.
Les commandes de maintenance sans serveur Web n'exécutent aucun horaire. Les profils de test
standards imposent `sofascore.j3.runtime-enabled=false`. L'interrupteur technique
`SOFASCORE_J3_RUNTIME_ENABLED=false` désactive le moteur complet, y compris l'admission manuelle ;
pour désactiver seulement l'automatisation, utiliser le réglage de l'interface.

## 2. Collecte manuelle A ou B

1. Ouvrir le tableau de bord, saisir **Date à collecter**, ou conserver le jour courant.
2. Pour A, cliquer **A. Lancer la collecte paginée — APPELS FOURNISSEUR**.
   Le moteur utilise le cache admissible puis appelle le fournisseur pour les pages absentes.
3. Pour B, sélectionner les fichiers JSON `page-1.json` jusqu'à `page-N.json`, puis cliquer
   **5B. Importer et valider J3 — ZÉRO APPEL**. Leur ordre dans le sélecteur est indifférent.
4. La page de suivi affiche l'ordre ; à son terme, le tableau de bord revient à la date demandée.
   Un succès complet alimente la liste des tournois et son lien de consultation paginée.

Aucune levée d'arrêt global, activation de circuit, phrase ou confirmation d'intention J3 n'est
nécessaire. Les anciennes routes de mutation J3 répondent `410 Gone` : actualiser un ancien onglet.
Les confirmations des autres parcours restent celles de leurs décisions propres.

L'import valide tout le lot avant admission : 1 à 35 fichiers contigus, 5 Mio maximum par fichier,
25 Mio pour le lot, structure J3 de tournois, `hasNextPage=true` jusqu'à l'avant-dernière page puis
`false` sur la dernière. Trou, doublon, page sensible, JSON invalide, 36e fichier ou terminal
incomplet sont refusés. L'import ne consulte ni transport ni cache et ne suspend pas une campagne live.

## 3. Automatisation et horaires

| Réglage ou situation | Comportement |
|---|---|
| Démarrage / changement de jour | Une opportunité pour le jour courant, seulement sans succès connu pour cette date |
| Échec de cette opportunité | Aucun nouvel essai implicite ce jour, même après redémarrage ; le manuel reste disponible |
| Heure quotidienne fixe | Remplace le mode démarrage ; une occurrence par jour à l'heure locale configurée |
| Horaire ponctuel | Date cible et instant d'exécution distincts ; plusieurs horaires possibles |
| Succès déjà présent | Ignore l'opportunité quotidienne ; exécute les heures fixes et ponctuelles explicitement programmées |
| Désactiver | Réglage durable ; bloque les prochains automatiques et arrête les pages suivantes d'un automatique en cours après l'échange borné |
| Modifier / annuler | Révision traçable ; une occurrence déjà admise ne change pas de cible |
| Lab arrêté, en veille ou indisponible à l'échéance | Occurrence manquée ; aucun rattrapage ni démarrage externe |
| File ou autre collecte occupée | Ordre sérialisé, soumis à sa borne de 20 minutes depuis admission ; aucun départ en parallèle |

Les fichiers importés en attente restent en mémoire jusqu'à leur exécution (huit lots au maximum).
Un redémarrage interrompt un ordre admis dont le propriétaire a disparu ; il ne rejoue pas un import
ni une requête fournisseur. L'interface conserve la tentative et son motif. Un état de stockage ou
de nettoyage incertain bloque le transport jusqu'à sa résolution ; aucun succès n'est inventé.

Pour les heures ponctuelles de changement d'heure, une heure inexistante est refusée et une heure
ambiguë exige un décalage explicite (`+02:00` ou `+01:00` selon l'occurrence choisie).
Une heure quotidienne inexistante utilise le premier instant local valide ; une heure doublée
utilise la première occurrence. Une date cible admise reste figée même après minuit.
Les anciens jours manqués en mode heure fixe sont consignés par lots bornés de 31 jours par tick.

## 4. Retrouver les données après redémarrage

Choisir une date puis **consulter** ne déclenche aucun appel. La liste déroulante présente les
tournois éligibles du dernier succès de cette date. **Calendriers J3 conservés** ouvre
`/j3/collections` ; les liens d'une collecte portent son identifiant et sa date, avec 25, 50 ou
100 lignes par page. Les tournois exclus restent visibles avec leur motif.

Une page déjà ouverte reste attachée à sa collecte même après une nouvelle réussite. Un lien
explicite permet de passer au succès plus récent. La préparation de découverte reçoit l'identifiant
de collecte, sa date et le tournoi : une collecte effectuée dans un autre onglet ne change pas la source.

Une collecte vide complète est un succès. Une tentative échouée, annulée ou interrompue ne remplace
jamais le dernier succès. La preuve minimisée accessible depuis la collecte conserve l'ordre,
le déclencheur, les pages, les références de snapshot et d'occurrence, hashes, parseur et heures,
ainsi que la pause live éventuelle ; aucun payload complet ni secret n'y est inclus.

La reprise J8 n'utilise que des terminaux complets démontrables et leurs sources exactes, y compris
les anciens plafonds 25/35. Si les octets ou la provenance ont disparu, l'interface indique
**succès attesté, contenu indisponible** ; ce succès évite toujours un doublon opportuniste.
Une heure de cache historique absente reste explicitement absente.

## 5. Pause d'une campagne live

Les nouvelles préparations utilisent **live-v11** et le **protocole worker 10**. Le profil V11
possède sa propre preuve et ne récupère pas automatiquement le profil V10 d'un lanceur existant.
Les manifestes et preuves historiques restent inchangés.

Le worker doit provenir du même code WO-060. Le lanceur existant
`scripts/Start-J3PlaywrightLocal.ps1` le reconstruit avant de démarrer le Lab.
Pour un lancement Eclipse, reconstruire explicitement ce worker puis reporter son chemin
dans le lanceur ; la commande de packaging seule ne démarre ni Lab ni navigateur :

```powershell
.\mvnw.cmd -Pprovider-playwright-runtime -DskipTests package
```

Ce packaging ne remplace pas la qualification et les tests décrits dans le rapport.

Afficher les dix paramètres de qualification à reporter dans le lanceur :

```powershell
.\scripts\Show-LiveGroupedV11LauncherConfiguration.ps1 -OutputFormat Eclipse
# ou : -OutputFormat PowerShell
```

Ce script affiche des valeurs ; il ne modifie ni Eclipse, ni l'environnement, ni un opt-in et
ne lance aucune campagne. Le [profil V11](../validation/WO060-LIVE-V11-PROFILE-20260914.json) lie
les classes de la simulation et du planificateur. Sa capacité de huit rencontres est qualifiée
contre des hypothèses d'enveloppes explicites en replay local. La mesure Chromium prouve
l'isolation des contextes ; elle ne mesure ni la latence ni une capacité soutenue du fournisseur.

Lors d'un ordre J3 fournisseur, la campagne enregistre la demande de pause avant tout nouveau
départ live. L'échange J4 ou J5 déjà en vol achève sa réception et sa publication. Le même thread
propriétaire et le même garde ouvrent ensuite un contexte J3 neuf dans le worker existant.
Le contexte live n'émet rien pendant cette sous-opération. Les pages J3 restent bornées à 35,
à la date admise, au délai de l'ordre et à l'échéance finale de la campagne.

Après fermeture vérifiée du contexte J3 et publication terminale, la campagne conserve son
contexte live initial, ses compteurs, ses arrêts et son échéance. Les groupes J5 abandonnés sont
tracés comme manqués ; un nouveau J4 de revalidation précède tout nouveau J5.
Aucun rattrapage en rafale, budget réinitialisé ni prolongation de campagne n'est permis.
Les plafonds partagés restent 35 départs sur 60 secondes et 2 100 sur une heure ; les clôtures
et les départs restent soumis à leurs délais techniques.

Une erreur de schéma J3 avec nettoyage certain permet la reprise. Refus fournisseur, stockage
incertain, contexte perdu, nettoyage impossible, arrêt opérateur, veille ou fin de fenêtre
empêchent cette reprise. Une campagne live ne redémarre pas après arrêt du Lab.
Les anciennes politiques sans capacité V11 signalent leur incompatibilité ; aucun manifeste
historique n'est converti implicitement.

## 6. Sauvegarde et rétention

Voir le [runbook J6](J6-BACKUP-RESTORE-AND-RETENTION.md). V55–V57 ajoutent les collections,
préférences, ordres et transitions de pause. Les scripts courants exigent V57 et comparent
les dix tables J3 et leur empreinte avant/après restauration. Le Lab doit être arrêté et
aucun ordre J3 admis ne doit subsister pour une preuve quiescente.

La rétention protège toutes les pages des derniers succès par date, même pour un catalogue
vide, sous le verrou de publication partagé. Les anciens octets restent soumis aux règles J6
existantes. Aucune purge ou restauration primaire n'a été exécutée pour WO-060.
