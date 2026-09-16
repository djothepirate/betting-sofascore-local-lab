# C5 — préflight de staging Windows détenu par l'hôte

**État : `PASS_DIAGNOSTIC_ONLY`.** Le 16 septembre 2026, le préflight d'accès
sans modèle a réussi dans deux copies de staging nouvelles, créées par le parent
hôte `asusggo2025\geoff`. Il ne constitue ni une exécution de `WR-H01` ou
`WR-N01`, ni une qualification du candidat.

## Objet et frontières

Le préflight reprend exactement la sonde C5 bornée : elle lit le candidat exact,
crée `output/c5-launcher-preflight.txt` avec les octets `C5_PREFLIGHT`, relit ces
octets, supprime ce seul fichier, vérifie son absence, puis écrit le marqueur de
succès. Il est appelé depuis l'hôte par :

```text
codex sandbox --permission-profile :workspace -C <contexte> powershell.exe \
  -NoProfile -NonInteractive -Command <sonde lecture/ecriture/nettoyage>
```

Les deux invocations ne lancent pas `codex exec`, de modèle, de harnais, de JVM,
de Maven, de Docker, de navigateur, de réseau applicatif ou de préflight interne
au conducteur `WR-N01`. Elles ne changent ni `config.toml`, ni le candidat, ni les
sources, ni les contextes C5 gelés.

## Pourquoi un staging séparé

Le préflight hôte initial de `run-05` échouait avant PowerShell avec
`helper_unknown_error: setup refresh had errors`. Le journal Codex hôte établit
ensuite des refus `SetNamedSecurityInfoW: 5` sur chaque racine C5, son `.agents`
et son `.git`.

Les racines C5 gelées sont détenues par `ASUSGGO2025\CodexSandboxOffline`, tandis
que les copies de staging sont détenues par `ASUSGGO2025\geoff`. Une mutation
récursive des propriétaires/ACL de `run-05` n'a pas été appliquée : elle aurait
modifié la frontière de qualification des contextes gelés. Les ACL particulières
observées dans C4 ne sont pas recopiées non plus : le SID sandbox supplémentaire
n'est pas résolu comme un compte réutilisable.

Les copies de staging ont été créées sans métadonnées de sécurité ni propriétaire
des sources. Avant le préflight, leur `preflight.json` était identique à celui de
`run-05`; les 18 fichiers contrôlés de `WR-H01` et les 16 de `WR-N01`, incluant
demande, prompt et entrées déclarées, étaient byte-à-byte égaux aux originaux et
à leurs empreintes attendues. Les deux répertoires `output/` étaient vides.

| Cas | Propriétaire source C5 | Propriétaire staging | Fichiers contrôlés | Intégrité / sortie initiale |
| --- | --- | --- | ---: | --- |
| `WR-H01` | `CodexSandboxOffline` | `geoff` | 18 | PASS / vide |
| `WR-N01` | `CodexSandboxOffline` | `geoff` | 16 | PASS / vide |

La preuve structurée d'intégrité est
[`windows-host-owned-staging-integrity.json`](windows-host-owned-staging-integrity.json).

## Résultat du préflight

| Cas | Durée | Code | Sortie standard | Sortie d'erreur | Candidat | Résidu |
| --- | ---: | ---: | --- | --- | --- | --- |
| `WR-H01` | 0,951 s | 0 | `C5_PREPARED_SANDBOX_READ_WRITE_CLEANUP=PASS` | vide | inchangé | absent |
| `WR-N01` | 0,712 s | 0 | `C5_PREPARED_SANDBOX_READ_WRITE_CLEANUP=PASS` | vide | inchangé | absent |

La sortie brute conservée, comprenant les deux commandes, les horodatages et les
indicateurs `model_invoked=false`, `runtime_probe_executed=false`,
`java_started=false` et `configuration_changed=false`, est
[`windows-host-owned-staging-preflight.raw.json`](windows-host-owned-staging-preflight.raw.json).

## Conclusion et suite bornée

Le sandbox Windows `elevated` fonctionne depuis un parent hôte pour le profil
`:workspace` lorsque les contextes de travail sont détenus par l'hôte. Le défaut
reproduit sur C5 est donc circonscrit aux métadonnées de sécurité de ses contextes
créés sous `CodexSandboxOffline`; il n'établit aucun défaut du candidat
`ss-windows-runtime`, des gabarits ou des sondes.

Ce staging est volontairement **non qualifiable** : il n'est pas lui-même le
contexte C5 destiné à une session modèle et ne consomme aucune des deux recettes
Windows. La suite nécessaire est une nouvelle **racine physique** hôte-propriétaire
qui conserve l'identifiant logique `run-05`, les mêmes entrées canoniques et les
exclusions vérifiées avant lancement. Elle ne crée pas un nouveau numéro de run ni
ne modifie l'autorisation C5. Cette préparation a été réalisée et est documentée
par le [contexte formel hôte](windows-host-formal-context.md). Le candidat `.1`,
le protocole C5, les bornes natives et l'interdiction de tout fallback
`unelevated`, contournement du sandbox ou changement de configuration restent
inchangés.
