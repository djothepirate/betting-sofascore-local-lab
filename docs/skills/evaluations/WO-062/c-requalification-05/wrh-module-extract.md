# Extrait borné pour WR-H01 — frontière de l'argument JAR

Cet extrait reproduit seulement la fonction nécessaire pour expliquer la frontière
de l'argument Java JAR dans la recette WR-H01 C5. Il est une source de contexte,
pas un résultat attendu, une réponse antérieure ou un oracle.

- source intégrale : `scripts/wo036/WO036-CampaignTools.psm1` ;
- taille de la source : 176724 octets ;
- SHA-256 de la source :
  `43c7bc8eb242161729f6c18f8b0feb67c46715ce911b27572e4bd13e5956bd74` ;
- plage reproduite : lignes 241 à 264 incluses ;
- raison : le harnais WO-044 importe cette source intégrale sans la modifier ; la
  session C5 utilise cet extrait pour analyser la validation et le guillemet de
  l'argument, sans ouvrir le module volumineux.

```powershell
function ConvertTo-WO036JavaJarStartProcessArgument {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path) `
            -or $Path -match '[\x00-\x1f\x7f"]' `
            -or -not [IO.Path]::IsPathFullyQualified($Path) `
            -or [IO.Path]::GetExtension($Path) -cne '.jar') {
        throw 'WO-036 Java JAR path cannot be serialized as one exact native argument.'
    }
    try {
        $canonical = [IO.Path]::GetFullPath($Path)
    }
    catch {
        throw 'WO-036 Java JAR path cannot be serialized as one exact native argument.'
    }
    if (-not $canonical.Equals($Path, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'WO-036 Java JAR path is not canonical.'
    }

    # Start-Process joins ArgumentList values into one native command line. Keep the
    # registered canonical path explicitly quoted so Java receives one exact value
    # after -jar even when a Windows directory name contains spaces.
    return ('"{0}"' -f $Path)
}
```
