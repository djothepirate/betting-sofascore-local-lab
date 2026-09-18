# Revue statique indépendante — ss-windows-runtime

**Verdict : PASS statique.** Aucun écart bloquant relevé dans les choix techniques, le périmètre et les références du candidat `0.1.0-candidate.1`, au commit `9e7b777e3be75f582f2198dfa8cca386b43426c2`.

Le relecteur est l'auteur des revues indépendantes Java JM-H01/JM-N01, mais **ni auteur ni préparateur du candidat ou du corpus Windows**. Aucun oracle Windows ni résultat comportemental Windows n'a été lu.

| Point revu | Constat |
|---|---|
| Périmètre/routage | Incidents Windows du Lab, builds simples et autres dépôts exclus ; relais cohérents vers `ss-verify` et `ss-ci-security`. |
| PS5.1 / PS7 | Runtime réel requis ; API et comportement .NET distingués ; harnais WO-044 correctement décrit comme PS7 capturant les arguments sans Java. |
| Arguments et codes | Helper WO-036 et appel production vérifiés ; chemin JAR protégé, flags/identité/ordre conservés ; code natif immédiat distinct d'ExitCode Process et du code extérieur. |
| Délais/nettoyage | Budgets distincts, horloge monotone, propriété exacte, fenêtre cachée et sorties non bloquantes ; suppression bornée et résultat principal conservé. |
| Histoire et causalité | WO-053 reste `NOT_ESTABLISHED` ; incident B `PSModulePath` limité à l'environnement enfant ; ancien vert jamais assimilé à une qualification actuelle. |
| Docker/Maven/UTF-8 | Présence du CLI distincte du moteur ; écart Testcontainers WO-044 conservé ; `--offline` sans garantie sur les effets des tests ; octets/affichage et PS5.1/PS7 distingués. Format/UTF-8 déjà validés par l'outil officiel du coordinateur. |

## Empreintes exactes

- `SKILL.md` : 9 217 octets, SHA-256 `b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6` ; identique au préflight.
- `agents/openai.yaml` : 300 octets, SHA-256 `3038015980952bc18b21f45f0d4af514b35a8841394e4b80824986b7e30ccd9f`.
- Inventaire Windows : SHA-256 `829309f7af53e58afe2a539044ccd2e5f1303c3a5f22260c44df407ea2f60d74`.

Lectures principales : candidat et métadonnées, inventaire/protocole Windows, rapport WO-044, helper WO-036 et harnais/enfant de capture, préflight et lanceur de vérification, rapports WO-053, rapport/preuve de l'incident B, frontières de propriété/nettoyage J6 et skills de routage.

**Portée : revue statique uniquement.** Aucune session native de recette, aucun test applicatif, Maven, Docker, Pester ou navigateur lancé ; aucun candidat modifié. Les huit évaluations comportementales, les preuves natives H01/N01 et l'acceptation humaine restent distinctes. Gain de temps/qualité non mesuré.
