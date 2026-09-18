# WR-N01 — Qualification locale bloquee au preflight

Statuts conserves : EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED, NO_CRITICAL_DEPENDENCY.

## Conclusion

Une seule tentative du conducteur a ete executee. Les modes Java failure et sleep
n'ont pas ete lances. Aucun code natif Java, READY ou timeout Java n'a donc ete
mesure dans cette session. La qualification des deux modes reste NON REALISEE.

## Blocage exact

L'evaluation de la provenance SHA-256 de la fixture, incorporee a la construction
de l'evenement runtime, a echoue : Get-FileHash n'est pas reconnu dans l'enfant.
Type : System.Management.Automation.CommandNotFoundException.
Etape : preflight. Ligne signalee : Invoke-WRN01.ps1:141 ; pile : ligne 137.
UTC : 2026-09-15T23:43:44.57977Z ; chronometre : 214.4678 ms.
Le message complet, l'identifiant, la position et la pile sont dans observations.json.

Ce blocage vient d'une dependance d'instrumentation du conducteur. Il ne prouve
ni l'absence de Java 25, ni un defaut de NativeProbe.java.
La cause de l'indisponibilite du cmdlet n'est pas etablie.
PSModulePath et l'environnement persistant n'ont pas ete modifies.
Le tableau runtime n'a pas ete ecrit car sa construction contenait cet appel
defaillant : PSVersionTable, edition, CLR et architecture de l'enfant ne sont
donc pas disponibles dans cette preuve. Le chemin de son image reelle, son PID,
son handle, son heure de creation et sa commande sont conserves dans launch.json.
Le nom powershell.exe seul ne suffit pas a qualifier Windows PowerShell 5.1 Desktop.
La decouverte exhaustive des images Java et la verification de version n'ont pas
ete atteintes.

## Mesures de cette session

- PID du conducteur : 12548 ; sortie confirmee par son handle conserve.
- Code du conducteur copie immediatement apres WaitForExit : 1.
- Code outil de la tentative : 1 ; evenement outil 2b5c3d.
- Duree interne jusqu'a conductor_return : 225.287 ms.
- Disponibilite des preuves : 2026-09-15T23:43:44.6674665Z,
  297.2118 ms depuis le debut interne.
- Retour du conducteur observe : 2026-09-15T23:43:44.6955699Z,
  798.1418 ms au chronometre du lanceur ; budget : 45000 ms.
- Commande suivante distincte : WR_PROMPT_RETURNED ; evenement outil 140fe2,
  code outil 0 ; horodatage dans prompt-return.json.
- Postflight independant : 2026-09-15T23:44:15.8634373Z ;
  PID du conducteur absent, racine temporaire absente.
- Racine prevue : output/runtime/WR native probe a27cb0e7-ef81-4d56-852e-9acf74ce7924.
  Elle n'a pas ete creee : le blocage precede cette etape.
- Aucun enfant Java, aucun arret force, aucun fichier temporaire Java cree.
  Le resultat cleanup=CONFIRMED concerne donc l'absence de ressources creees,
  pas un nettoyage exerce apres expiration.

## Sources confrontees et limites

Le candidat exact ss-windows-runtime a ete lu integralement. Le POM declare Java 25
et Spring Boot 4.1.0 ; le wrapper declare Maven 3.9.16. application.yml declare
server.address=127.0.0.1 et sofascore.enabled=false par defaut. Ces constats sont
statiques ; aucune application n'a ete executee. Le SHA _policy_source_commit
d'input.json est une provenance fournie, pas un SHA Git observe dans cette session.

Les scripts de conduite sont conserves tels qu'executes, avec leur defaut.
Leur code non atteint ne constitue aucune preuve de fonctionnement des deux modes.
Les preuves JSON et texte sont ecrites en UTF-8 sans BOM. Aucune fixture ni source
n'a ete modifiee. Aucun Maven, Docker, navigateur, HTTP, application, base,
CI, installation ou processus tiers n'a ete lance ou arrete.

## Suite exploitable

Pour une future tentative distincte, decoupler l'ecriture des observations runtime
de la collecte SHA-256 ; employer une collecte compatible avec le runtime present,
ou diagnostiquer precisement la disponibilite du cmdlet. Cette correction n'a pas
ete appliquee et aucune relance n'a ete faite. Une nouvelle execution des deux modes
reste necessaire pour qualifier code natif, expiration apres READY et nettoyage.

## Fichiers de preuve

Invoke-Session.ps1 : lanceur de la seule tentative.
Invoke-WRN01.ps1 : conducteur construit, conserve tel qu'execute.
launch.json : commande exacte et identite reelle du conducteur apres Process.Start.
observations.json : erreur detaillee, resultat principal et postflight interne.
return.json : code immediat et duree du conducteur observes par le lanceur.
evidence-available.json : disponibilite des preuves en UTC.
conductor-stdout.txt et conductor-stderr.txt : flux captures.
prompt-return.json : commande distincte apres retour.
postflight.json : verification independante des seules ressources de cet essai.
note-qualification.md : presente note.
Conclusion consignée en UTC : 2026-09-15T23:45:15.8889102Z
