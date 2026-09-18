# WR-H01 run-04 — revue indépendante du résultat figé

**Verdict global : FAIL pour la conformité complète à la session C4.** Les **huit critères substantiels de l'oracle WR-H01 sont PASS** et la reproduction native est qualifiée. Le seul critère en échec est l'organisation des lectures explicitement demandée par le complément C4 : export intégral du module volumineux et relectures d'entrées. Cet écart ne transforme pas la reproduction réussie en blocage technique et ne justifie aucune relance automatique.

## Identité et intégrité

- HEAD de revue : `cb67d18c66aaa3989bfc6121ed7b51945fd95f9f` ; worktree propre au début de la revue.
- Candidat : `ss-windows-runtime`, `0.1.0-candidate.1`, SHA-256 `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6`.
- Réponse : `11e6ec4e1fac52be3b5b52c739489f3b79e6dda9ebdd29227e3e18edd437e905`.
- Gel : `f0ec32aaeeb2145f0ae3f19b5959e772f8cd66c8b4adf07a864b50e28e2e6dcd`, produit à `2026-09-15T23:37:12.172226Z`, avant cette revue.
- **22 fichiers figés et 13 entrées** vérifiés en taille et SHA-256. Oracle original `213dc98b419c2c6dad5104347a3e422ecb1516d9149967a15b0a8e06c6c6d437`, protocole original `b4748a356b881a8574a78156c0ac00c90f493874efe8303d40a7224c12d91a52`, complément C4 `3658c70cd4a00d2902ecac2e99e79a2daa0fb500ac2f59d8d861b744b97a6c73`.
- Le HEAD de préparation transmis `79d0f99` reste distinct du HEAD de revue et du gel originel des sources métier `6648dd4`.

La commande `item_1`, terminée avec 0, lit le SKILL.md exact. Sa sortie contient le corps intégral, comparé au fichier candidat avec la seule fin de ligne ajoutée par la commande ignorée. Cette preuve est un chargement par outil ; le catalogue reconstruit n'est pas une capture d'injection automatique.

## Résultat réel et critères substantiels

| Critère | Verdict | Preuve contrôlée |
|---|---|---|
| H01 — harnais exact, nouveau lancement, PS7 Windows et deux itérations | PASS | `item_16` crée le conducteur, `item_18` l'exécute une fois. Les trois sources restent exactes dans leur arborescence. PowerShell 7.6.6 Core x64, Windows 10.0.26200 ; `-Iterations 2`. |
| H02 — transcript, code immédiat, temps et retour | PASS | Stdout actuel : split reproduit, deux itérations corrigées, cas sans espace, instance, trois flags, ordre JAR, quatre refus. Conducteur lignes 86–89 : ExitCode copié immédiatement après WaitForExit. `item_19` émet ensuite WR_PROMPT_RETURNED. |
| H03 — confinement enfant, nettoyage et postflight | PASS | TEMP/TMP de ProcessStartInfo sous output/runtime, parent inchangé ; racine UUID observée. Harnais : zéro résidu détenu. `item_23` exécute un postflight séparé : dossier vide, PID 8500 absent ; aucun arrêt ni suppression par ce postflight. |
| H04 — explication de la frontière d'arguments | PASS | Réponse et diagnostic expliquent la recomposition ArgumentList, le chemin canonique protégé immédiatement après -jar et les quatre classes refusées. Les contrôles supplémentaires non tous exercés sont distingués. |
| H05 — portée de la capture | PASS | La réponse précise que le harnais ne lance pas Java et ne qualifie ni application, flags JDK effectifs, PS5.1, CTRL_BREAK ou CI. Aucun mélange avec WR-N01. |
| H06 — histoire WO053 exacte | PASS | Run initial 33972681012, cause NOT_ESTABLISHED ; run 33973934317, SHA 5f3dea2b72c0751174ea389891397e11bd9e08ae, deux jobs Windows, instrumentation et workflow_dispatch distingués du merge PR29. |
| H07 — Linux et durcissement distinct | PASS | Linux 101327214159 échoue avant tests et n'est pas rejoué ; run global non vert. Les contre-épreuves C# sans Win32 et l'ancien CI ne qualifient pas le diff ultérieur ni la cause. |
| H08 — écart historique Maven/Testcontainers | PASS | La réponse restitue l'absence accidentelle de -DskipITs, Ryuk/PostgreSQL isolés, interruption et nettoyage sur identifiants exacts, primaire sain sans redémarrage, puis commande corrigée. Aucun Docker/Maven rejoué. |

La sous-revue indépendante `jm_selection_review` a confronté la réponse et `diagnostic.md` aux trois sources historiques autorisées et aux points H04–H08 : PASS, sans omission concrète relevée. Les omissions H04/H06/H08 du run-03 sont corrigées dans cette réponse ; les anciennes preuves restent inchangées.

### Processus et ressources

Le conducteur détient l'objet Process et son SafeHandle **2132**, PID **8500**, création `2026-09-15T23:33:07.5421609Z`, image `C:\Program Files\PowerShell\7\pwsh.exe`, conducteur PID **31404**. Le marqueur de lancement est émis après Start réussi. La commande et les arguments exacts sont conservés dans `execution.json`. L'UUID de la fiche conducteur est explicitement un identifiant de sa fiche, sans prétendre être injecté au harnais.

L'objet observé sort naturellement avec **0** ; `outerProcessExited=true`, aucun arrêt forcé. Les captures brutes sont supprimées par le harnais original. Son transcript et ses assertions attestent les enfants de capture ; leurs PID, codes et durées individuels ne sont pas exportés. Le postflight extérieur qualifie séparément le PID surveillé et le dossier runtime, sans inventaire mondial.

## Temps : trois portées distinctes

| Mesure | Observation |
|---|---|
| Harnais | 2 686,1568 ms |
| Conducteur, préflight et vérifications internes compris | 2 872,3827 ms ; borne de 60 s respectée |
| Lancement confirmé | 23:33:07.5540213 UTC |
| Sortie native | 23:33:10.2245221 UTC |
| Retour de commande distinct | 23:33:15.3798568 UTC |
| Postflight extérieur | 23:33:48.1453075 UTC ; 83,3556 ms |
| Conclusion écrite | 23:35:55.8088423 UTC |
| Réponse finale reçue | 23:37:09.097026 UTC ; 426,031 s après le début de mesure de réception CLI |
| Session, trace du lanceur | 428,25 s, soit 7 min 8,25 s |
| Watchdog extérieur | 900 s ; aucune expiration |

La **cible de réponse avant six minutes est manquée** : réponse reçue après 426,031 s, session terminée après 428,25 s. Cette cible est formulée « viser » ; elle n'est pas le watchdog ni la borne native de conduite. Aucun échec de délai natif ne peut en être déduit. Le temps de réception des événements ne mesure pas le temps de raisonnement du modèle. La conduite et le postflight extérieur sont effectivement terminés dans la minute suivant le démarrage de la conduite.

## Écart opératoire F01 — FAIL

Le complément C4, lignes 10–12, demande de lire une seule fois les entrées courtes et, pour un module volumineux, de rechercher les fonctions puis leurs sections sans exporter le fichier entier.

- `item_4` exécute `Get-Content -Raw` sur le module entier de **176 724 octets**, avec les deux scripts WO044 et le test Pester ; sa sortie agrégée contient 193 561 caractères.
- `item_6` relit le complément déjà chargé par `item_2`.
- `item_8` relit le protocole, `item_9` la capture/Pester/YAML et `item_10` le harnais, après leurs lectures précédentes.
- La recherche et les lectures ciblées `item_8`/`item_13` surviennent après l'export intégral.

L'écart est observable et répète celui relevé au run-03. Il reste distinct des critères substantiels tous satisfaits, du dépassement de la cible de six minutes et de l'absence d'incident collecteur. Aucun lien causal chiffré entre ces lectures et la durée totale n'est affirmé.

## Limites et conclusion de revue

Les 15 commandes natives, les trois événements de création de fichiers et les scripts exécutés ont été examinés. Ils utilisent les entrées autorisées et output ; aucune lecture d'oracle/réponse ancienne, aucun Java/Maven/Docker/réseau/application/base, aucune mutation persistante, aucun arrêt global observé. L'intégrité des entrées est conservée. Les scripts et preuves ont été produits dans la même session ; un conducteur, un lancement, un postflight, une réponse finale, sans relance.

CLI et collecteur valent 0 ; la session et la collecte sont terminées sans diagnostic d'erreur. Ces zéros sont corroborés par le transcript et le postflight, et ne servent pas seuls de verdict. Les branches d'expiration, de garde CIM interne et d'arrêt forcé n'ont pas été exercées ; leurs garanties générales ne sont pas déduites de ce parcours réussi. Les informations détaillées PS proviennent du conducteur utilisant la même image observée que le harnais, dont le script n'a pas été instrumenté.

**Qualification substantielle WR-H01 : PASS. Conformité complète au complément C4 : FAIL sur F01.** Cette distinction conserve le succès natif et les corrections historiques démontrés, tout en laissant l'instruction de lecture non respectée visible. Aucun changement, nouvelle tentative ou installation n'a été effectué par le relecteur. Seuls les présents fichiers `review-WR-H01.md/json` sont écrits hors du dossier frozen.
