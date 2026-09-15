# J3 — Collecte directe, automatisation et calendriers conservés

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Cette procédure correspond à [WO-060](../work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md)
et à [ADR-SS-007 v0.3](../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md).
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

Les dates à collecter vont du **01/01/2000** au **jour courant à Paris + 12 mois**, bornes
incluses. La plage exacte est affichée sous le champ ; elle est vérifiée dans le navigateur
et côté serveur pour A et B. Elle ne limite pas la consultation des collectes déjà conservées.

1. Ouvrir le tableau de bord, saisir **Date à collecter**, ou conserver le jour courant.
2. Pour A, cliquer **A. Lancer la collecte paginée — APPELS FOURNISSEUR**.
   Le moteur utilise le cache admissible puis appelle le fournisseur pour les pages absentes.
3. Pour B, sélectionner les fichiers JSON `page-1.json` jusqu'à `page-N.json`, puis cliquer
   **B. Importer et valider J3 — ZÉRO APPEL**. Leur ordre dans le sélecteur est indifférent.
4. La page de suivi affiche l'ordre ; à son terme, le tableau de bord revient à la date demandée.
   Un succès complet alimente la liste des tournois et son lien de consultation paginée.

Aucune levée d'arrêt global, activation de circuit, phrase ou confirmation d'intention J3 n'est
nécessaire. Les anciennes routes de mutation J3 répondent `410 Gone` : actualiser un ancien onglet.
Le parcours tournoi dispose lui aussi des boutons directs décrits ci-dessous.

L'import valide tout le lot avant admission : 1 à 35 fichiers contigus, 5 Mio maximum par fichier,
25 Mio pour le lot, structure J3 de tournois, `hasNextPage=true` jusqu'à l'avant-dernière page puis
`false` sur la dernière. Trou, doublon, page sensible, JSON invalide, 36e fichier ou terminal
incomplet sont refusés. L'import ne consulte ni transport ni cache et ne suspend pas une campagne live.

### Rencontres du tournoi sélectionné

1. Consulter la date J3 voulue, puis choisir un tournoi dans « Évolution J3 → J5 ».
2. Cliquer **A. Collecter les rencontres — UN GET MAXIMUM** pour utiliser le cache admissible
   ou collecter les rencontres avec un appel au maximum.
3. Pour l'import, choisir le corps JSON correspondant au tournoi et à la date, puis cliquer
   **B. Importer et relier à J5 — ZÉRO APPEL**. Un fichier est obligatoire, limité à 5 Mio.

Les étapes 2 et 3 sont deux possibilités indépendantes. Aucune préparation intermédiaire,
phrase ou case d'acceptation n'est demandée. Les cases amateur et qualification restent
des filtres de la liste. Le résultat revient sur la date consultée et propose les liens J5.
Cliquer un lien J5 ne lance pas une campagne. Les blocages du transport et les arrêts restent
affichés et appliqués. Après mise à jour du logiciel, recharger un ancien onglet : ses formulaires
de confirmation répondent 410 et ne lancent aucune opération.

## 3. Automatisation et horaires

Après une mise à jour du Lab, revenir au tableau de bord puis actualiser la page avant de
soumettre un formulaire. Le message **Préférences J3 enregistrées** confirme l'enregistrement ;
un horaire accepté apparaît dans **Horaires et dernières exécutions**.

Si les boutons de paramètres, de planification, de collecte A ou d'import B conduisent tous à
un HTTP 403 dans Chrome, appliquer le [correctif des formulaires WO-060](../validation/WO060-J3-FORMS-403-FIX-20260914.md),
recompiler et relancer le Lab, puis recharger `/` ou `/dashboard`. La correction conserve
l'origine locale des formulaires ; aucun réglage de sécurité du navigateur n'est nécessaire.
Une simple actualisation de la page d'erreur `/j3/settings` ou `/j3/plans` ne charge pas le
nouveau formulaire. Ce correctif n'ajoute aucune migration ni modification des données J3.

La **date à collecter** désigne le calendrier et peut être passée. La **date et l'heure de
déclenchement à Paris** doivent former un instant futur. Par exemple, à 13 h 31, programmer
12 h 35 le même jour affiche : **La date et l’heure de déclenchement sont déjà passées.
Choisissez un horaire futur à Paris.** Le tableau de bord revient au bloc **Collecte automatique**.
Les champs de programmation sont conservés pour correction ; si un horaire existant était
modifié, son formulaire reste ouvert et l'ancien ordre reste intact. Le bouton d'enregistrement
permet de soumettre l'horaire corrigé. Le refus de saisie ne crée aucun ordre.

Les champs absents ou invalides indiquent la date ou l'heure à corriger. Les heures
inexistantes ou doublées lors des changements d'heure ont une explication spécifique.
Le [correctif de validation](../validation/WO060-J3-DATE-VALIDATION-FIX-20260914.md)
remplace la page Whitelabel précédemment affichée pour un déclenchement passé.

Depuis le [correctif des bornes](../validation/WO060-J3-DATE-RANGE-FIX-20260914.md), un nouvel
horaire ou sa modification doit aussi respecter l'horizon de **12 mois calendaires** à partir
du jour courant à Paris, jusqu'à **23:59 inclus** le dernier jour. Au 14/09/2026, la date à
collecter va donc du 01/01/2000 au 14/09/2027 et le déclenchement futur peut aller jusqu'au
14/09/2027 à 23:59. La borne est recalculée lors de chaque affichage et de chaque soumission ;
un ancien onglet ne contourne pas le contrôle serveur. Un refus ne crée pas d'ordre et ne
remplace pas une ancienne révision. Les heures doublées gardent leur contrôle par décalage.

Une date impossible comme le **31/02/2028** est invalide. Le contrôle Chromium isolé bloque
son envoi et le serveur refuse aussi cette chaîne si elle lui est envoyée directement.
Le **29/02/2028** est une date calendaire valide ; son admissibilité dépend séparément de
l'horizon courant. La qualification utilise le 01/03/2027 comme horloge synthétique pour
vérifier cette distinction.

Une annulation volontaire affiche **Annulée à votre demande**. Une ancienne révision remplacée
affiche **Remplacée par une nouvelle version de cet horaire**. Ces motifs ne signalent pas une
panne de transport. Les lignes historiques restent conservées, y compris les essais déjà annulés.

La qualification navigateur est manuelle et strictement hors réseau : après compilation,
exécuter `scripts/Invoke-J3DateFormQualification.ps1 -BrowserCachePath <cache-local-existant>`.
Elle rend le vrai fragment du tableau de bord avec des données synthétiques, n'accède pas
au Lab et ne démarre ni base ni fournisseur. Elle ne fait pas partie des tests Maven standards.

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
| Fenêtre de réservation réseau expirée après prise en charge | État **Annulée**, motif `ADMISSION_DEADLINE_EXPIRED`, sans ouverture fournisseur. La réservation exige plus de 130 secondes restantes pour les opérations bornées ; le dernier succès est conservé |

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

Si `/events` affiche une capacité de zéro après passage au WO-060, vérifier les **dix paramètres
V11** de la configuration Eclipse réellement utilisée : la capacité, l'empreinte de qualification
et les huit durées. La présence des paramètres V10 et d'une capacité déclarée de huit ne renseigne
pas le profil V11 indépendant. Reporter les valeurs du lecteur V11 et relancer le Lab avec cette
configuration. Le calcul du profil V11 fourni admet alors huit rencontres, sous réserve des
autres conditions de préparation. Un ancien message mentionnant V10 est corrigé par
l'[addendum de mise en service du WO-060](../validation/WO060-LIVE-V11-LAUNCHER-FIX-20260914.md).

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

## 6. Menu des compétitions : priorité, traduction et amateurs

Le menu J3 → J5 place d'abord les catégories dont `tournament.category.priority` est
strictement positif, par priorité numérique croissante puis par nom de catégorie affiché.
Les priorités nulles, absentes ou invalides suivent, par nom de catégorie. Les égalités sont
départagées par nom de tournoi puis identité de phase. Le tri alphabétique utilise la
collation française, sans distinction de casse ou d'accent.

Les libellés utilisent une traduction fournisseur française non vide si elle existe.
Sinon, les noms internationaux des pays reconnus sont traduits avec le dictionnaire local
Java, complété pour les associations britanniques et quelques alias explicites. Le suffixe
`Amateur` est conservé pour les pays reconnus. Un nom inconnu reste tel quel : la traduction
de toutes les catégories n'est pas garantie. Aucun service de traduction réseau n'est appelé.

Les cases **Afficher les compétitions amateurs** et **Afficher les phases qualificatives**
sont décochées par défaut. Chaque clic recharge immédiatement le menu par GET local,
sans bouton de confirmation. La date consultée et l'état des deux cases sont conservés.
JavaScript doit être activé ; le script servi localement ne soumet que ce formulaire de lecture.
La détection du mot `Amateur`, insensible à la casse, examine les noms sources
`tournament.category.name` et `tournament.uniqueTournament.category.name`, avant traduction.
Le compteur « Tournois actionnables » reste celui du catalogue complet. Un filtre vide
affiche un message et permet toujours de réafficher les amateurs.

Une phase qualificative est identifiée uniquement par le booléen JSON
`tournament.qualificationOrPreliminary: true`. Champ absent, `null`, `false` ou chaîne
`"true"` ne répondent pas à ce critère. Les filtres se cumulent : un tournoi amateur et
qualificatif exige que les deux cases soient cochées pour apparaître.

Ces métadonnées sont relues dans les pages locales conservées avec contrôle de taille et
SHA-256. Les anciennes collectes bénéficient donc du menu sans recollecte ni migration.
Les identités, les observations historiques et les contrôles de préparation restent inchangés.

## 7. Sauvegarde et rétention

Voir le [runbook J6](J6-BACKUP-RESTORE-AND-RETENTION.md). V55–V57 ajoutent les collections,
préférences, ordres et transitions de pause. Les scripts courants exigent V57 et comparent
les dix tables J3 et leur empreinte avant/après restauration. Le Lab doit être arrêté et
aucun ordre J3 admis ne doit subsister pour une preuve quiescente.

La rétention protège toutes les pages des derniers succès par date, même pour un catalogue
vide, sous le verrou de publication partagé. Les anciens octets restent soumis aux règles J6
existantes. Aucune purge ou restauration primaire n'a été exécutée pour WO-060.
