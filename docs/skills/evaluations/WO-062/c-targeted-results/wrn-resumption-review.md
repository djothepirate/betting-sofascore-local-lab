# Revue indépendante des conditions WR-N01

## Verdict : PASS — portée statique uniquement

Conditions cohérentes avec la preuve préalable limitée et le protocole original. Identité créée/mesurée distinguée, mesures anciennes non réutilisées, runtime et limites inchangés, arrêt strictement conditionné à la propriété. Ce PASS porte seulement sur le document préparatoire ; WR-N01 complet reste non relancé et non qualifié.

Document relu intégralement : `.tmp/wrn-resumption-conditions.md`, 4399 octets, SHA-256 `b24482a22d8e71d4e6ecd6d3643a7c2ab337d6a6fa25e48cbacb5d99a186b4ec`.

Le relecteur a relu les résultats Java/Windows et la preuve préalable de propriété. Il n'est ni l'auteur de ces conditions ni le conducteur de la sonde. Aucun modèle, probe ou nouvel essai n'a été lancé pendant cette revue.

## Contrôles

### P01 — PASS — Fixture et protocole inchangés

Lignes 5–8 et 30–35 : NativeProbe originale conservée ; aucune addition de parent/image/heure dans son stdout. Les octets du fichier courant (1009) et le protocole (10630) correspondent exactement au manifeste d'origine.

Limite : La nouvelle session devra à son tour figer et vérifier ces entrées ; aucune session complète n'est exécutée par ce document.

### P02 — PASS — Découverte directe et preuve de propriété

Lignes 17–29 et 36–43 : découverte de toutes les applications Java, rejet d'un choix implicite de relais, candidat direct unique, version effective ; objet Process et handle retenus, PID/UUID publiés concordants, image observée durant la vie et commande exacte.

Limite : OriginalFilename et version de fichier seuls ne prouvent pas l'image exécutée. Si le mode court sort avant la lecture de MainModule, l'absence doit rester bloquante, sans reconstruction.

### P03 — PASS — Parent de création distinct d'un parent mesuré

Lignes 30–35 : relation créée par Process.Start explicitement distinguée d'une mesure du parent par la JVM ; aucun champ parent n'est inventé dans NativeProbe.

Limite : Le parent du nouvel essai n'est pas mesuré par la fixture originale. Le handle du processus créé et la concordance PID/UUID/image doivent être établis dans le contexte concerné.

### P04 — PASS — Bornes originales, arrêt exact et codes séparés

Lignes 12–15 et 36–49 : PS5.1 Desktop, Java25, failure puis sleep, UUID distincts, 10 s, READY 10 s, 1500 ms, sortie après arrêt 5 s, total 45 s. Aucun retry/allongement ; code réel copié immédiatement, issues principale/nettoyage séparées ; aucun arrêt si identité absente.

Limite : Les bornes valent aussi pour la branche d'identité inconnue : une sortie naturelle ne dispense pas du budget global de 45 s et ne prouve pas le timeout traité. Le protocole original garde ses exigences de chronométrage, Kill() PS5.1 et absence avant Dispose.

### P05 — PASS — Confinement et nettoyage

Lignes 44–49 : ressources neuves UUID sous output/runtime, streams UTF-8, fichiers créés et identifiés seuls supprimés après sortie par handle, puis répertoires vides ; résidu inattendu conservé, postflight séparé et marqueur de retour.

Limite : Il s'agit d'un complément au protocole, qui conserve ses gardes canoniques/reparse, copie d'environnement enfant et neutralisation des variables d'injection. Aucun ancien résidu à nettoyer.

### P06 — PASS — Séparation des preuves et absence de corrigé

Lignes 53–66 : nouveau préflight et gel avant essai, aucune transmission des mesures préalables, revues, sorties anciennes ou oracle ; aucune mesure copiée. Aucun résultat natif attendu, marqueur de réussite ou verdict du futur essai fourni.

Limite : Les constantes de délais et les contraintes de conduite proviennent du protocole autorisé. La qualification finale restera indépendante de ce PASS statique.

### P07 — PASS — Format et portée

Document UTF-8 strict sans BOM, NUL ou CR ; 4399 octets. Statut préparatoire explicite et aucun appel exécutable lancé par le relecteur.

Limite : Ce document ne remplace ni un script instrumenté, ni sa revue, ni une nouvelle preuve native dans le sandbox cible.

## Identités et limites finales

- Fixture NativeProbe : `4e29c5867eba0fb76518de2307391449c83450449bea0393179738505c69bc8b`, 1009 octets, identique au manifeste d'origine ; UTF-8 LF.
- Protocole original : `b4748a356b881a8574a78156c0ac00c90f493874efe8303d40a7224c12d91a52`, 10630 octets, identique au manifeste.
- Preuve de propriété distincte : `70be6ee6153269c440944fdb2b15b21fef065ce5b0e5ad4f5c61f535b18bacef`.

- La preuve préalable de propriété appartient à un autre contexte ; elle ne démontre pas l'accès MainModule ni la concordance d'identité dans la future session sandboxée.
- Le protocole original reste normatif : le complément n'assouplit aucune borne, aucune preuve de nettoyage ou d'environnement.
- Si l'identité ne peut pas être établie à temps, constater BLOCKED ; ne jamais convertir la sortie naturelle en réussite de traitement de délai.
- Le gel d'une future entrée doit exclure les mesures, revues et anciens résultats ; aucune copie de résultats attendus n'est autorisée par cette revue.

Le présent PASS ne qualifie ni failure, ni sleep, ni arrêt forcé, ni nettoyage de ces deux modes. Il ne produit aucune réponse de session évaluée et ne transforme pas la preuve préalable en mesure d'un futur processus.

