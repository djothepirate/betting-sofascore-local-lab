# Distribution locale SofaScore Local Lab

Cette archive est une distribution expérimentale strictement locale. Elle n'est ni approuvée
pour la production, ni déployable sur un VPS, et n'autorise aucun appel SofaScore ou receiver réel.

## Prérequis

- Windows avec PowerShell 7.4 (`pwsh`) ;
- Java 25 LTS ;
- Docker Desktop avec `docker compose` et un contexte Windows local (`npipe://`).

## Démarrage

Depuis la racine de l'archive extraite :

```powershell
pwsh -NoProfile -File .\scripts\Initialize-LocalConfig.ps1
pwsh -NoProfile -File .\scripts\Preflight-Local.ps1
pwsh -NoProfile -File .\scripts\Start-Local.ps1 -RunApplication
```

Le lanceur démarre PostgreSQL, attend son état sain, puis exécute directement l'unique JAR livré.
Il ne dépend ni d'un dépôt source, ni de Maven, ni du Maven Wrapper. L'application reste liée à
`127.0.0.1` et les intégrations fournisseur et receiver demeurent désactivées. Les launchers
refusent un daemon Docker distant et neutralisent les configurations Spring, Java, datasource,
SofaScore ou receiver héritées du terminal avant de démarrer l'application.

L'interface locale est ensuite disponible sur <http://127.0.0.1:8087/>.

## Arrêt

```powershell
pwsh -NoProfile -File .\scripts\Stop-Local.ps1
```

Ajouter `-RemoveData` supprime également le volume PostgreSQL local. Vérifier `SHA256SUMS` avant
le premier démarrage permet de contrôler le JAR, le SBOM et la provenance livrés.
