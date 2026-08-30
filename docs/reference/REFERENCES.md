# Références de cadrage

## Documents versionnés dans le dépôt

1. `ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md` — décision acceptée pour expérimentation locale contrôlée.
2. `ADR-SS-002-bounded-multi-dossier-provider-robustness.md` — proposition non active pour une preuve J9 unique, bornée à 38 appels ; décision propriétaire requise.
3. `Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf` — cadrage complémentaire, 8 août 2026.

## Document global conservé dans la Library ChatGPT

- `Projet_Betting_Cadrage_Architecture_v0.5.1.pdf`, version 0.5.1, 7 août 2026.

Sections structurantes utilisées :

- non-objectifs et non-dépendance au poste Windows ;
- environnement Windows / WSL2 / Docker / Eclipse ;
- abstraction des fournisseurs ;
- position SofaScore et Observation Bridge ;
- maintenabilité, idempotence, tests et sécurité ;
- décisions immédiates et critères d’acceptation.

## Hiérarchie de décision

```text
Cadrage Betting Project v0.5.1
        │
        ├─ règle globale : aucun appel SofaScore depuis le VPS
        │
        ▼
ADR-SS-001
        │
        ├─ exception bornée : dépôt Windows local séparé
        ├─ proposition non active : ADR-SS-002, preuve J9 unique
        │
        ▼
Cadrage SofaScore Local Lab v0.1.0
        │
        └─ feuille de route J0 à J9
```

En cas de conflit, l’ADR-SS-001 qualifie l’exception locale, mais n’annule pas la non-dépendance du
projet principal. ADR-SS-002 n'a aucun effet tant que son statut reste `PROPOSED` ; son existence ne
vaut ni acceptation, ni go fournisseur, ni approbation de production VPS.
