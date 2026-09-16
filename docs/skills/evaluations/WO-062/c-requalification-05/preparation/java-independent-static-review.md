# WO-062 — revue statique indépendante Java C5

**Verdict statique : PASS.** Le candidat `ss-java-module`
`0.1.0-candidate.4` introduit deux obligations distinctes qui couvrent l'omission
constatée dans JM-N01 : une obligation d'analyse du profil Maven et une obligation
de conserver son statut dans la restitution finale. Le périmètre et les sources du
cas restent inchangés ; le POM figé rend le constat directement disponible.

Cette revue est statique. Elle ne préjuge pas de la réponse d'une session neuve, de
la lecture effective du candidat, ni de la qualification comportementale C5.

## Objet examiné

- Candidat actuel : `docs/skills/local-lab/ss-java-module/SKILL.md`, version
  `0.1.0-candidate.4`, 10 901 octets, SHA-256
  `f1f6533f678c5cdb4ae6a5bc9c996c062af419005e4f4cd6104a51c91e442d97`.
- Révision : `docs/skills/evaluations/WO-062/ss-java-module/revision-04/revision.json`.
- Cas ciblé : `JM-N01` dans `cases.json`, avec `JM-I01` (`pom.xml`) parmi ses
  36 sources autorisées.
- Source revue : le `pom.xml` courant est inchangé depuis le commit source
  `6648dd423e556b5248b8793a539ae85a7680f9bc`; son entrée manifestée `JM-I01`
  est le blob `b6746484877941bc9b5fed47b9878ba3426b28ac`, 16 856 octets,
  SHA-256 de worktree
  `e01e011e360fa847842d826a5bcf2960c34eae91da037e2a6f002e71a3513a46`.

## Preuves de la correction doublée

1. **Obligation pendant l'analyse.** Aux lignes 52 à 56 du candidat, la rubrique
   « Vérifier la composition et les effets » impose, dans une revue J3 qui examine
   les profils de test, de relever explicitement que le POM maintient
   `sofascore-live-test` volontairement bloqué par son garde. Elle impose aussi de
   ne pas le présenter comme une activation de collecte ni comme une alternative au
   profil J7 de test.

2. **Obligation avant remise.** Aux lignes 145 à 149, la vérification finale
   impose que la synthèse conserve le statut des profils J3 effectivement examinés,
   en citant expressément le blocage de `sofascore-live-test` lorsqu'il relève de la
   revue. Cette seconde barrière vise précisément l'écart C4 : la source avait été
   lue, mais son constat avait disparu de la réponse.

3. **Fait source disponible.** `pom.xml:426-443` définit le profil
   `sofascore-live-test`, l'exécution Enforcer `block-live-tests-before-j3` et la
   règle `alwaysFail` dont le message dit que le profil reste volontairement bloqué
   au jalon J1. Ce fichier est dans l'allowlist JM-N01 et la demande exige l'examen
   de l'activation Maven. Le candidat n'invente donc ni activation ni statut.

4. **Non-confusion J7.** L'ajout mentionne explicitement que ce profil ne remplace
   pas le profil J7 de test. Cela couvre la distinction demandée par l'attente
   réservée, sans déduire qu'une compilation, un navigateur ou une collecte a été
   exécuté.

## Intégrité de la recette C5

- La révision-04 déclare les deux ajouts ci-dessus, le passage de `.3` à `.4`, les
  8 cas prévus et `behavioral_qualification: NOT_RUN`.
- Son SHA et sa taille du candidat correspondent au fichier réellement présent.
- `cases.json`, `inputs.json`, `manifest.json` et `oracle.md` sont byte-identiques
  à leur état au commit de base `e24d0b0`; aucune attente, entrée métier ou source
  du cas n'a été modifiée pour faciliter le résultat.
- L'autorisation C5 référence le même candidat et SHA, le run `run-05` et les huit
  cas, dont JM-N01. Elle déclare que l'oracle et les réponses antérieures ne sont
  pas fournis aux sessions évaluées.
- La qualification courante est correctement marquée
  `CORRECTED_PENDING_EVALUATION`, avec huit cas non exécutés et les résultats de
  `.3` conservés comme historiques non transférables.

## Limite restante

Le libellé est conditionné à une revue J3 qui examine les profils de test. JM-N01
demande l'examen de l'activation Spring/Maven, autorise le POM et inclut des sources
de test ; le déclencheur est donc présent pour ce cas. Une session pourrait malgré
tout omettre la restitution : seule une réponse C5 indépendante permettra de
vérifier le respect effectif des deux consignes. Le présent PASS ne constitue ni un
PASS de JM-N01, ni une validation humaine, ni une installation personnelle.

## Méthode et indépendance

La vérification a comparé les octets, la liste de sources et les instructions du
candidat. Aucun modèle, navigateur, application, build, test, base de données,
runtime Windows ou session évaluée n'a été lancé. L'attente réservée a été consultée
uniquement pour vérifier que la correction couvre le critère déclaré ; elle n'a été
transmise à aucune session d'évaluation.
