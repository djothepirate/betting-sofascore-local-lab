# C5 — contexte Windows formel détenu par l'hôte

**État : READY_NO_MODEL_SESSION_STARTED.** Le 16 septembre 2026, une nouvelle racine physique détenue par asusggo2025\geoff a été préparée pour les deux cas Windows déjà nommés par l'autorisation C5. Elle préserve l'identifiant logique run-05 ; elle ne crée ni un run-06 ni une nouvelle autorisation logique.

Cette étape reste une préparation et un préflight d'accès. Aucun modèle, harnais WR-H01, préflight interne ou conducteur WR-N01, JVM, Maven, Docker, navigateur ou collecte n'a été lancé. Les deux cas restent non consommés.

## Provenance et intégrité avant accès sandbox

La racine physique neuve est une copie des contextes Windows run-05 gelés, créée depuis le parent hôte sans recopier les propriétaires ou ACL source. Son preflight.json conserve l'identifiant logique run-05 et son SHA-256 :

~~~text
269ba6e1ce7e5f540a79e788035c2323442b410f389dc13a2921aaa8519b988b
~~~

L'autorisation C5 inchangée porte l'empreinte :

~~~text
a4c35ea2bd91920a520eb889dfdeca60d4ab89d8eb0ce2463c28881543173d41
~~~

Elle limite ce contexte aux deux seuls cas WR-H01 et WR-N01, au candidat ss-windows-runtime 0.1.0-candidate.1 de SHA-256 :

~~~text
b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6
~~~

| Cas | Fichiers vérifiés | Propriétaire racine / .agents / .git | Entrées et candidat | output/ | Artefact antérieur interdit |
| --- | ---: | --- | --- | --- | --- |
| WR-H01 | 18 | geoff / geoff / geoff | conformes | vide | absent |
| WR-N01 | 16 | geoff / geoff / geoff | conformes | vide | absent |

Les contrôles rapprochent chaque demande, prompt et entrée déclarée de run-05 ; ils excluent les répertoires frozen, oracle, results et review, ainsi que les événements, traces, réponses et catalogues racine. Le reçu machine est [windows-host-formal-context-integrity.json](windows-host-formal-context-integrity.json).

## Préflight d'accès du contexte formel

Dans chacune des deux racines, la sonde C5 sans modèle a lu le candidat, écrit, relu puis supprimé le marqueur de test. Le profil reste :workspace et le sandbox reste elevated.

| Cas | Durée | Code | Marqueur | stderr | Candidat | Résidu |
| --- | ---: | ---: | --- | --- | --- | --- |
| WR-H01 | 0,730 s | 0 | PASS | vide | inchangé | absent |
| WR-N01 | 0,680 s | 0 | PASS | vide | inchangé | absent |

La sortie brute, y compris la commande de la sonde et les indicateurs model_invoked=false, runtime_probe_executed=false, java_started=false et configuration_changed=false, est [windows-host-formal-preflight.raw.json](windows-host-formal-preflight.raw.json).

## Frontière de la recette à venir

Cette disponibilité d'accès ne qualifie aucun comportement du skill. Lors de la prochaine recette effectivement consommée, le conducteur hôte devra rester limité à l'autorisation C5 : run-05 logique, ss-windows-runtime, WR-H01 puis WR-N01, un worker séquentiel, une session éphémère par cas, --sandbox workspace-write, watchdog externe de 900 secondes et automatic_retry=false.

Le conducteur devra revérifier les octets avant chaque lancement, refuser tout écrasement de sortie ou de gel, puis geler événements, réponse, timings, audit de lecture et artefacts runtime avant revue. Le préflight interne WR-N01 et les deux modes Java restent obligatoirement dans sa future session fraîche : les exécuter maintenant retirerait leur valeur de preuve. Aucune règle ne permet un fallback unelevated, un bypass du sandbox, --ignore-user-config ou une modification de configuration.
