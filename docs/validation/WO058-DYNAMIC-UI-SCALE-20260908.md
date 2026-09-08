# WO-058 — Actualisation dynamique de l’interface à 20 et 100 rencontres

Date : 2026-09-08. Base Git : `06c7e3d272e8f96873eca822e4bdaa8302cf1b01`, avec les modifications du lot en cours.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Les résultats ci-dessous portent sur la cadence affichée de **100 secondes**. La preuve du candidat précédent à 75 secondes reste conservée dans [le rapport archivé](WO058-DYNAMIC-UI-SCALE-CANDIDATE-75-20260908.md), avec ses journaux et XML distincts.

Les deux qualifications Chromium ont réussi : après mise à disposition d’un nouveau JSON local,
l’actualisation contrôlée de toutes les rencontres a été constatée en moins de cinq secondes
aux deux charges testées. Le critère du test était de dix secondes au maximum.

| Charge affichée | Familles critiques | Métriques statistiques | Incidents | Contenu modifié | Nouvelle réception, contenu identique | Résultat |
|---|---:|---:|---:|---:|---:|---|
| 20 rencontres synthétiques | 60 | 2 700 | 600 | 4 935 ms | 4 955 ms | 1 test, 0 échec, 0 erreur, 0 ignoré |
| 100 rencontres synthétiques | 300 | 13 500 | 3 000 | 4 900 ms | 4 841 ms | 1 test, 0 échec, 0 erreur, 0 ignoré |

Les données structurées, les paramètres et les empreintes des preuves sont conservés dans
[WO058-DYNAMIC-UI-SCALE-20260908.json](WO058-DYNAMIC-UI-SCALE-20260908.json).

## Ce que le scénario exerce

Le harnais `LiveTenMatchRefreshBrowserQualificationIT` conserve son nom historique ; le nombre
de rencontres est maintenant paramétré par `wo058.ui.matches`. Chaque rencontre contient
les familles J4, incidents et statistiques. Les statistiques comportent trois périodes
(`ALL`, `1ST`, `2ND`), chacune avec 45 métriques. Chaque rencontre possède aussi 30 incidents.
La charge de 100 rencontres comprend donc 300 familles, 13 500 métriques et 3 000 incidents.

Un serveur éphémère lié à `127.0.0.1` sert une page HTML synthétique reprenant les sélecteurs
de l’application et un endpoint d’état JSON. Les scripts `live-campaign.js` et `statistics.js`,
les feuilles `app.css` et `statistics.css`, ainsi que la projection Java
`StatisticsPresentation` sont ceux de l’application. Les quatre fichiers statiques compilés
utilisés par le harnais ont la même empreinte que leurs sources documentées.
Le contrôleur MVC et la base PostgreSQL ne sont pas exécutés dans cette qualification.

Le rafraîchissement utilise le script de production : après chaque réponse, il programme
la prochaine lecture locale avec un délai de cinq secondes. Cette lecture d’écran est
distincte de la cadence de collecte. Pour les deux exécutions, `wo058.grouped.v5=true` fait
projeter un intervalle de famille de 100 secondes ; le test vérifie le libellé `100 s` et
l’échéance initiale correspondante pour chacune des familles.

Après un chargement initial, deux publications sont contrôlées :

1. Une publication change le score, un incident représentatif, la possession et les références
   de contenu. Pour chaque rencontre, le test attend le score et le statut attendus, les
   trois réceptions à jour, les familles rendues, le dernier incident et la possession
   rendus et visibles. Il vérifie également les nombres de rencontres, familles, métriques
   et incidents présents dans le document.
2. Une publication avance seulement la réception et l’occurrence, avec le même contenu
   normalisé. Toutes les réceptions et tous les âges sont actualisés ; les âges affichés
   restent au plus à dix secondes, tandis que la date du dernier changement est conservée.
   Les nœuds des métriques et des tableaux d’incidents, ainsi que leurs hashes affichés,
   restent identiques : la réutilisation du contenu ne fige pas sa fraîcheur de réception.

Les contrôles réussis établissent aussi zéro erreur JavaScript, zéro erreur CSP, zéro POST
et zéro demande externe. Aucun appel fournisseur ni accès à la base opérateur n’a lieu.
Les données sont entièrement synthétiques. Le navigateur est Chromium sans interface,
dans un contexte neuf, avec une fenêtre de 1 440 × 1 000 pixels, sous Windows 11 amd64,
Java 25.0.4. Aucun cookie, payload fournisseur, profil persistant ou enregistrement du
navigateur n’est publié avec ces preuves.

## Sens précis des temps mesurés

Le départ de chaque mesure est un horodatage monotone pris après construction et
sérialisation du JSON synthétique, juste avant sa publication dans l’endpoint local.
La mesure comprend l’attente de la prochaine lecture, le transfert HTTP loopback,
le décodage JSON, l’actualisation par le JavaScript de production et l’observation des
assertions navigateur pour toutes les rencontres.

Le démarrage de Chromium, la construction de la projection synthétique, le SQL, les appels
fournisseur et le délai de publication chez SofaScore sont hors de cette mesure. Les quatre
valeurs du tableau sont des observations de deux actualisations par charge ; elles ne
constituent ni un percentile de latence ni une qualification d’endurance de l’interface.
La visibilité contrôlée signifie que les éléments possèdent un rendu visible dans le
document ; toutes les cartes ne tiennent pas simultanément dans la fenêtre.

## Commandes et preuves conservées

Les exécutions ciblées ont utilisé les classes du profil natif déjà compilées. Les commandes
de reproduction, à lancer explicitement depuis le worktree avec le cache Chromium existant,
sont les suivantes ; aucune commande n’a été relancée pour rédiger ce rapport :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4'
$env:PLAYWRIGHT_BROWSERS_PATH = (Resolve-Path -LiteralPath '.tmp/provider-playwright-browsers').Path
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'

.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-DskipTests=false' '-DskipITs=false' '-Dit.test=LiveTenMatchRefreshBrowserQualificationIT' `
  '-Dwo058.grouped.v5=true' '-Dwo058.ui.matches=20' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  'failsafe:integration-test@provider-playwright-loopback-qualification' `
  'failsafe:verify@provider-playwright-loopback-qualification'

.\mvnw.cmd --offline '-Dmaven.repo.local=C:/Users/geoff/.m2/repository' `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-DskipTests=false' '-DskipITs=false' '-Dit.test=LiveTenMatchRefreshBrowserQualificationIT' `
  '-Dwo058.grouped.v5=true' '-Dwo058.ui.matches=100' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  'failsafe:integration-test@provider-playwright-loopback-qualification' `
  'failsafe:verify@provider-playwright-loopback-qualification'
```

La qualification à 20 rencontres a terminé à `2026-09-08T17:52:06Z` en 16,385 secondes Maven
(suite : 13,867 secondes). Celle à 100 rencontres a terminé à `2026-09-08T17:52:26Z`
en 17,800 secondes Maven (suite : 15,301 secondes). Les deux ont produit `BUILD SUCCESS`.

Les journaux restent locaux dans `.tmp/wo058-v5-ui20.log` et `.tmp/wo058-v5-ui100.log`.
Les XML JUnit correspondants ont été copiés sous `.tmp/wo058-v5-evidence/ui20` et `ui100`.
Ces répertoires contiennent aussi des résultats antérieurs d’autres classes : seul le fichier
`TEST-com.bettingproject.sofascorelocal.adapter.web.LiveTenMatchRefreshBrowserQualificationIT.xml`
de chaque répertoire atteste le résultat UI décrit ici. Les blocs de propriétés complets
des XML ne sont pas reproduits ; le JSON compact conserve uniquement les faits sélectionnés,
les noms de fichiers et leurs SHA-256, ainsi que les empreintes des sources et du harnais compilé.

## Portée pour l’objectif de 50 à 100 rencontres

Ce résultat apporte une première preuve sur la capacité de rendu dynamique d’une charge
synthétique de 100 rencontres avec les composants actuels. Il est pertinent pour l’objectif
de moyen terme de 50 à 100 rencontres affichées. Aucune exécution distincte à 50 rencontres
n’a été faite ; les deux points mesurés ne garantissent pas toutes les charges intermédiaires,
toutes les machines, une durée prolongée, ni la réactivité du défilement sur mobile.

Ce scénario ne qualifie aucune collecte fournisseur de 100 rencontres et ne modifie pas
la capacité admise par la politique live-v5. La collecte à 20 rencontres, le transport,
la persistance et leurs délais font l’objet de qualifications séparées. Aucune garantie
de capacité en production n’est déduite de ces deux tests UI.

Les compositions ne sont pas incluses dans cette charge. Leurs deux tests Chromium et
le correctif de visibilité sont documentés séparément dans
[WO058-LINEUPS-VISIBILITY-20260908.md](WO058-LINEUPS-VISIBILITY-20260908.md).
Leurs résultats ne constituent pas une qualification de compositions pour 100 rencontres.
