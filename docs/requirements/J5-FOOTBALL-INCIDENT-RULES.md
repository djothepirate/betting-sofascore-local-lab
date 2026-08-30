# Règles de gestion sur les incidents

> Fiche métier de référence pour la normalisation J5 des incidents de football.
> Les exemples JSON reproduisent les formes fournisseur observées ; les précisions de
> normalisation distinguent explicitement la valeur brute de la valeur canonique locale.

## 1. Généralités

- Endpoint cible : https://www.sofascore.com/api/v1/event/<EVENT_ID>/incidents
- Pour chaque type d'incident, les attributs en gras sont les attributs les plus importants à prendre en considération ;
- Chaque incident relevé dans un match de football est inclus dans un élément "incidents" de type tableau (Array) dans la structure JSON.
- L'absence d'un attribut métier attendu mais facultatif n'invalide pas toute la famille : J5 conserve l'incident et mesure une complétude `PARTIAL` en indiquant le chemin manquant ;
- Une contradiction entre attributs atomiques, une valeur située hors du vocabulaire documenté, un type d'incident inconnu ou l'absence d'un champ structurel indispensable (`incidentType` ou minute effective) produit `SCHEMA_INCOMPATIBLE`. La seule exception temporelle est la séance terminale entièrement non minutée décrite aux sections 3 et 10 : elle conserve explicitement la minute absente, sans la déduire de l'ordre de passage ;
- Les structures auxiliaires non affichées en J5, notamment `footballPassingNetworkAction`, restent conservées dans le snapshot brut. Elles ne sont interprétées que lorsqu'une règle ci-dessous le demande explicitement.

## 2. Types d'incident

### 2.1 Alimentation du tableau "incidents"

1. Le tableau "incidents" est vide si le match n'a pas encore commencé.
2. Le tableau "incidents" est alimenté si :
    - le match est en cours (sous réserve qu'au moins un incident ait déjà eu lieu);
    - le match est terminé.

### 2.2 Incidents par type

1. Les types d'incidents sont caractérisés pour chaque bloc d'incident selon la valorisation de l'attribut "incidentType".
2. Les valeurs possibles pour l'attribut "incidentType" sont les suivantes :
    - period : définit une période ;
    - substitution : définit un changement de joueur sur le terrain ;
    - goal : définit un but marqué dans la rencontre ;
    - card : définit l'attribution d'un carton dans la rencontre ;
    - injuryTime : définit le temps additionnel ;
    - varDecision : définit une décision arbitrale prise avec l'aide de la VAR ;
    - inGamePenalty : définit un penalty tiré sans qu'il soit marqué ;
    - penaltyShootout : définit un tir au but (cas particulier d'un match se terminant sur une séance de tirs au but) ;

## 3. Période (period)

### 3.1 Structure JSON d'une période

Ci-dessous quelques exemples de blocs définissant une période d'un match de football :
{
    "text": "FT",
    "homeScore": 3,
    "awayScore": 0,
    "isLive": false,
    "time": 90,
    "addedTime": 999,
    "timeSeconds": 5400,
    "incidentType": "period",
    "reversedPeriodTime": 1,
    "reversedPeriodTimeSeconds": 0,
    "periodTimeSeconds": 2700
}

{
    "text": "HT",
    "homeScore": 0,
    "awayScore": 0,
    "isLive": false,
    "time": 45,
    "addedTime": 999,
    "timeSeconds": 2700,
    "incidentType": "period",
    "reversedPeriodTime": 1,
    "reversedPeriodTimeSeconds": 0,
    "periodTimeSeconds": 2700
}

{
    "text": "Extra time",
    "homeScore": 1,
    "awayScore": 1,
    "isLive": true,
    "time": 120,
    "addedTime": 999,
    "timeSeconds": 7200,
    "incidentType": "period",
    "reversedPeriodTime": 1,
    "reversedPeriodTimeSeconds": 0,
    "periodTimeSeconds": 900
}

### 3.2 Attributs

- **text** : 7 valeurs possibles
    1. "HT" : Mi-temps (Half-Time)
    2. "FT" : Fin du match (Full-Time)
    3. "ET" : Fin d'une période de prolongations (Extra-Time)
    4. "PEN" : Fin d'une séance de tirs au but (Penalties)
    5. "First half" : 1ère mi-temps (si isLive = true)
    6. "Second half" : 2ème mi-temps (si isLive = true)
    7. "Extra time" : prolongation en cours (si isLive = true)
- `"Extra time"` et `"ET"` sont deux valeurs distinctes. `"Extra time"` est un marqueur live
  conservé tel quel ; il ne doit jamais être normalisé en `"ET"` ni être interprété comme une
  preuve de fin de rencontre. Une valeur `isLive=false` explicitement associée à
  `text="Extra time"` est contradictoire et produit `SCHEMA_INCOMPATIBLE`. Si `isLive` est absent,
  l'incident reste conservé avec une complétude `PARTIAL`, conformément à la règle générale des
  métadonnées métier facultatives.
- homeScore : Score de l'équipe à domicile.
- awayScore : Score de l'équipe à l'extérieur.
- isLive : 2 valeurs possibles
    1. true si le match est en cours
    2. false si le match est terminé
- **time** : Temps réglementaire cumulé prévu à la fin de la période concernée en minutes (hors temps additionnel). Sur le marqueur terminal exact `text="PEN"`, `period="penalties"`, `isLive=false`, le fournisseur peut utiliser la sentinelle `999`. J5 conserve `999` uniquement dans le brut. Si tous les `penaltyShootout` de la réponse possèdent une minute effective, le marqueur est normalisé avec leur plus grande minute. Si, au contraire, toutes les tentatives omettent `time` et représentent l'absence d'action auxiliaire soit par une propriété `footballPassingNetworkAction` absente, soit par un tableau exactement vide, J5 V15 conserve une minute normalisée absente uniquement lorsque la séance terminale est cohérente au sens de la section 10.2. Une séance mêlant une tentative réellement minutée et une tentative non minutée, ou toute autre utilisation de `999`, reste incompatible.
- addedTime : Valeur sentinelle généralement alimentée à 999 (elle peut être considérée comme un temps de jeu maximal technique). Cette valeur brute est conservée, mais elle n'est pas affichée comme du temps additionnel.
- timeSeconds : Temps réglementaire cumulé prévu à la fin de la période concernée en secondes (hors temps additionnel).
- **incidentType** : Vaut obligatoirement "period"
- reversedPeriodTime : Valorisé par défaut à 1 pour les matches sans séance de tirs au but
- reversedPeriodTimeSeconds : Valorisé par défaut à 0 pour les matches sans séance de tirs au but
- periodTimeSeconds : Durée de la période en secondes (hors temps additionnel).

La forme fournisseur observée pour la prolongation live porte `time=120`, `timeSeconds=7200`,
`periodTimeSeconds=900` et `addedTime=999`. Ces valeurs sont documentées comme observation et non
comme invariants universels supplémentaires : la règle normative nouvelle porte sur la combinaison
exacte `incidentType="period"`, `text="Extra time"`, `isLive=true`. En particulier, `time=120`
reste une borne de phase annoncée par le fournisseur, pas l'indication que la rencontre est déjà
terminée.

### 3.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type Période (period) :
- time (colonne MINUTE), ou `—` pour le seul marqueur terminal `PEN` d'une séance entièrement non minutée et cohérente ;
- incidentType (colonne TYPE) ;
- text (colonne DÉTAIL) ;
- homeScore et awayScore lorsqu'ils sont fournis (colonne SCORE).

## 4. Remplacement (substitution)

### 4.1 Structure JSON d'un changement de joueur

Ci-dessous un exemple de bloc définissant un changement de joueur au cours d'un match de football :
{
    "playerIn": {
    "name": "Amer Hiroš",
    "firstName": "Amer",
    "lastName": "Hiros",
    "slug": "amer-hiros",
    "shortName": "A. Hiroš",
    "position": "M",
    "jerseyNumber": "12",
    "height": 182,
    "userCount": 138,
    "gender": "M",
    "sofascoreId": "i62r4e",
    "id": 797775,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 834364800,
    "proposedMarketValueRaw": {
        "value": 330000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "أمير هيروش",
        "ru": "Амер Хирош"
        },
        "shortNameTranslation": {
        "ar": "أ. هيروش",
        "ru": "А. Хирош"
        }
    }
    },
    "playerOut": {
    "name": "Ante Roguljić",
    "firstName": "Ante",
    "lastName": "Roguljić",
    "slug": "ante-roguljic",
    "shortName": "A. Roguljić",
    "position": "M",
    "jerseyNumber": "44",
    "height": 179,
    "userCount": 110,
    "gender": "M",
    "sofascoreId": "hv538c",
    "id": 283839,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 826502400,
    "proposedMarketValueRaw": {
        "value": 185000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "أنتي روجوليتش",
        "ru": "Анте Рогульич"
        },
        "shortNameTranslation": {
        "ar": "أ. روجوليتش",
        "ru": "А. Рогульич"
        }
    }
    },
    "id": 126565157,
    "time": 66,
    "injury": false,
    "isHome": false,
    "incidentClass": "regular",
    "incidentType": "substitution",
    "reversedPeriodTime": 25
}

### 4.2 Attributs

1. Attributs globaux :

- id : Identifiant unique ;
- **time** : Minute de jeu où le fait de jeu s'est déroulé ;
- **addedTime** : Minute de temps additionnel où le remplacement a été effectué (non obligatoire)
    1. Cet attribut est affiché si le changement de joueur s'est déroulé dans le temps additionnel d'une période.
    2. Cet attribut n'est pas affiché si le changement de joueur s'est déroulé hors temps additionnel d'une période.
- **injury** : 2 valeurs possibles
    1. true s'il s'agit d'un changement de joueur sur blessure
    2. false s'il s'agit d'un changement de joueur dans des conditions classiques
- **isHome** : 2 valeurs possibles
    1. true si le fait de jeu concerne l'équipe à domicile
    2. false si le fait de jeu concerne l'équipe à l'extérieur
- **incidentClass** : 2 valeurs observées
    1. "regular" pour un remplacement de classe standard ;
    2. "injury" lorsque le fournisseur classe explicitement le remplacement comme consécutif à
       une blessure.
- Cohérence de la blessure :
    1. `incidentClass="injury"` et `injury=true` définissent un remplacement sur blessure valide ;
    2. `incidentClass="injury"` et `injury=false` se contredisent et rendent le document
       incompatible ;
    3. si `injury` est absent, le remplacement reste conservé avec une complétude partielle et
       aucune valeur n'est inventée ;
    4. la forme historique `incidentClass="regular"` reste acceptée avec la valeur booléenne
       fournie dans `injury`.
- **incidentType** : Vaut obligatoirement "substitution" ;
- reversedPeriodTime : Pas d'utilité identifiée pour ce type d'incident.

2. Bloc playerIn (joueur entrant)
3. Bloc playerOut (joueur sortant)

A noter : Les blocs "playerIn" et "playerOut" possèdent exactement les mêmes attributs :
- **name** : Nom complet du joueur (prénom + nom de famille) ;
- firstName : Prénom du joueur ;
- lastName : Nom de famille du joueur ;
- slug : Nom complet du joueur (en syntaxe kebab-case) ;
- shortName : Nom raccourci du joueur (première lettre du prénom + nom de famille) ;
- position : Poste du joueur (4 valeurs possibles)
    1. "G" : Gardien de but (Goalkeeper)
    2. "D" : Défenseur (Defender)
    3. "M" : Milieu (Midfielder)
    4. "F" : Attaquant (Forward)
- jerseyNumber : Numéro de maillot ;
- height : Taille du joueur (en centimètres) ;
- userCount : Compteur (utilité inconnue) ;
- gender : Genre (2 valeurs possibles)
    1. "M" : Masculin (Male)
    2. "F" : Féminin (Female)
- sofascoreId : Identifiant unique SofaScore du joueur ;
- id : Identifiant unique du joueur ;
- marketValueCurrency : Devise utilisée pour la valeur marchande du joueur (valeur par défaut : "EUR" pour l'euro) ;
- dateOfBirthTimestamp : Date de naissance (en timestamp) ;
- proposedMarketValueRaw : Valeur marchande (en devise définie par marketValueCurrency).

- fieldTranslations : Sous-bloc définissant les traductions littérales du nom d'un joueur
    - nameTranslation : Pour le nom complet (prénom + nom de famille) ;
    - shortNameTranslation : Pour le nom raccourci (1ère lettre du prénom + nom de famille).

Les sous-attributs dans "nameTranslation" et "shortNameTranslation" sont identiques. L'ensemble des attributs listés ci-dessous sont facultatifs :
    - ar : Traduction en arabe ;
    - ru : Traduction en russe ;
    - bn : Traduction en bengali ;
    - hi : Traduction en hindi ;

### 4.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type Remplacement (substitution) :
- time (colonne MINUTE) ;
- addedTime (si l'attribut existe, suffixe de la colonne MINUTE) ;
- incidentType (colonne TYPE) ;
- isHome (colonne CÔTÉ) ;
- incidentClass (colonne CLASSE) ;
- injury (si l'attribut vaut true, colonne DÉTAIL) ;
- playerIn.name (colonne ENTRANT) ;
- playerOut.name (colonne SORTANT).

## 5. But (goal)

### 5.1 Structure JSON d'un but marqué

Ci-dessous des exemples de blocs définissant un but marqué dans un match de football :
{
    "homeScore": 1,
    "awayScore": 0,
    "player": {
    "name": "Nahuel Tenaglia",
    "firstName": "Nahuel",
    "lastName": "Tenaglia",
    "slug": "nahuel-tenaglia",
    "shortName": "N. Tenaglia",
    "position": "D",
    "jerseyNumber": "14",
    "height": 182,
    "userCount": 1126,
    "gender": "M",
    "sofascoreId": "88rd7w",
    "id": 896073,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 824860800,
    "proposedMarketValueRaw": {
        "value": 3200000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "ناهويل تيناجليا",
        "bn": "নাহুয়েল টেনাগলিয়া",
        "hi": "नहुएल तेनाग्लिया",
        "ru": "Нахуэль Тенаглия"
        },
        "shortNameTranslation": {
        "ar": "ن. تيناجليا",
        "bn": "এন. টেনাগলিয়া",
        "hi": "एन. तेनाग्लिया",
        "ru": "Н. Тенаглия"
        }
    }
    },
    "assist1": {
    "name": "Mariano Díaz",
    "slug": "mariano-diaz",
    "shortName": "M. Díaz",
    "position": "F",
    "jerseyNumber": "9",
    "height": 180,
    "userCount": 4610,
    "gender": "M",
    "sofascoreId": "8sggfw",
    "id": 175753,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 744163200,
    "proposedMarketValueRaw": {
        "value": 285000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "ماريانو دياث",
        "bn": "মারিয়ানো দিয়াজ",
        "hi": "मारियानो डिआज़",
        "ru": "Мариано Диас"
        },
        "shortNameTranslation": {
        "ar": "م. دياث",
        "bn": "এম. দিয়াজ",
        "hi": "एम. डिआज़",
        "ru": "М. Диас"
        }
    }
    },
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Nahuel Tenaglia",
        "firstName": "Nahuel",
        "lastName": "Tenaglia",
        "slug": "nahuel-tenaglia",
        "shortName": "N. Tenaglia",
        "position": "D",
        "jerseyNumber": "14",
        "height": 182,
        "userCount": 1126,
        "gender": "M",
        "sofascoreId": "88rd7w",
        "id": 896073,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 824860800,
        "proposedMarketValueRaw": {
            "value": 3200000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ناهويل تيناجليا",
            "bn": "নাহুয়েল টেনাগলিয়া",
            "hi": "नहुएल तेनाग्लिया",
            "ru": "Нахуэль Тенаглия"
            },
            "shortNameTranslation": {
            "ar": "ن. تيناجليا",
            "bn": "এন. টেনাগলিয়া",
            "hi": "एन. तेनाग्लिया",
            "ru": "Н. Тенаглия"
            }
        }
        },
        "eventType": "pass",
        "time": 73,
        "playerCoordinates": {
        "x": 68.6,
        "y": 32.8
        },
        "passEndCoordinates": {
        "x": 89.7,
        "y": 11.5
        },
        "isHome": true
    },
    {
        "player": {
        "name": "Mariano Díaz",
        "slug": "mariano-diaz",
        "shortName": "M. Díaz",
        "position": "F",
        "jerseyNumber": "9",
        "height": 180,
        "userCount": 4610,
        "gender": "M",
        "sofascoreId": "8sggfw",
        "id": 175753,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 744163200,
        "proposedMarketValueRaw": {
            "value": 285000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ماريانو دياث",
            "bn": "মারিয়ানো দিয়াজ",
            "hi": "मारियानो डिआज़",
            "ru": "Мариано Диас"
            },
            "shortNameTranslation": {
            "ar": "م. دياث",
            "bn": "এম. দিয়াজ",
            "hi": "एम. डिआज़",
            "ru": "М. Диас"
            }
        }
        },
        "eventType": "cross",
        "isAssist": true,
        "time": 73,
        "playerCoordinates": {
        "x": 89.7,
        "y": 11.5
        },
        "passEndCoordinates": {
        "x": 93.5,
        "y": 45.5
        },
        "isHome": true
    },
    {
        "player": {
        "name": "Nahuel Tenaglia",
        "firstName": "Nahuel",
        "lastName": "Tenaglia",
        "slug": "nahuel-tenaglia",
        "shortName": "N. Tenaglia",
        "position": "D",
        "jerseyNumber": "14",
        "height": 182,
        "userCount": 1126,
        "gender": "M",
        "sofascoreId": "88rd7w",
        "id": 896073,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 824860800,
        "proposedMarketValueRaw": {
            "value": 3200000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ناهويل تيناجليا",
            "bn": "নাহুয়েল টেনাগলিয়া",
            "hi": "नहुएल तेनाग्लिया",
            "ru": "Нахуэль Тенаглия"
            },
            "shortNameTranslation": {
            "ar": "ن. تيناجليا",
            "bn": "এন. টেনাগলিয়া",
            "hi": "एन. तेनाग्लिया",
            "ru": "Н. Тенаглия"
            }
        }
        },
        "eventType": "goal",
        "bodyPart": "head",
        "time": 73,
        "playerCoordinates": {
        "x": 92.7,
        "y": 47.2
        },
        "gkCoordinates": {
        "x": 99,
        "y": 48.6
        },
        "goalShotCoordinates": {
        "x": 100,
        "y": 47.5
        },
        "goalMouthCoordinates": {
        "x": 63.44,
        "y": 52.5
        },
        "goalkeeper": {
        "name": "David Soria",
        "firstName": "David",
        "lastName": "Soria",
        "slug": "david-soria",
        "shortName": "D. Soria",
        "position": "G",
        "jerseyNumber": "13",
        "height": 192,
        "userCount": 1030,
        "gender": "M",
        "sofascoreId": "r6zitm",
        "id": 604258,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 733881600,
        "proposedMarketValueRaw": {
            "value": 2700000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ديفيد سوريا",
            "bn": "ডেভিড সোরিয়া",
            "hi": "डेविड सोरिया",
            "ru": "Давид Сория"
            },
            "shortNameTranslation": {
            "ar": "د. سوريا",
            "bn": "ডি. সোরিয়া",
            "hi": "डी. सोरिया",
            "ru": "Д. Сория"
            }
        }
        },
        "isHome": true,
        "goalType": "regular",
        "xg": 0.25110077857971,
        "xgot": 0.36747881770134,
        "situation": "assisted",
        "goalMouthLocation": "high-right"
    }
    ],
    "id": 357872339,
    "time": 73,
    "isHome": true,
    "incidentClass": "regular",
    "incidentType": "goal",
    "reversedPeriodTime": 18
}

{
    "homeScore": 2,
    "awayScore": 0,
    "player": {
    "name": "Mariano Díaz",
    "slug": "mariano-diaz",
    "shortName": "M. Díaz",
    "position": "F",
    "jerseyNumber": "9",
    "height": 180,
    "userCount": 4610,
    "gender": "M",
    "sofascoreId": "8sggfw",
    "id": 175753,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 744163200,
    "proposedMarketValueRaw": {
        "value": 285000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "ماريانو دياث",
        "bn": "মারিয়ানো দিয়াজ",
        "hi": "मारियानो डिआज़",
        "ru": "Мариано Диас"
        },
        "shortNameTranslation": {
        "ar": "م. دياث",
        "bn": "এম. দিয়াজ",
        "hi": "एम. डिआज़",
        "ru": "М. Диас"
        }
    }
    },
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Mariano Díaz",
        "slug": "mariano-diaz",
        "shortName": "M. Díaz",
        "position": "F",
        "jerseyNumber": "9",
        "height": 180,
        "userCount": 4610,
        "gender": "M",
        "sofascoreId": "8sggfw",
        "id": 175753,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 744163200,
        "proposedMarketValueRaw": {
            "value": 285000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ماريانو دياث",
            "bn": "মারিয়ানো দিয়াজ",
            "hi": "मारियानो डिआज़",
            "ru": "Мариано Диас"
            },
            "shortNameTranslation": {
            "ar": "م. دياث",
            "bn": "এম. দিয়াজ",
            "hi": "एम. डिआज़",
            "ru": "М. Диас"
            }
        }
        },
        "eventType": "goal",
        "bodyPart": "right-foot",
        "time": 90,
        "playerCoordinates": {
        "x": 79.2,
        "y": 61.7
        },
        "gkCoordinates": {
        "x": 98.6,
        "y": 51.1
        },
        "goalShotCoordinates": {
        "x": 100,
        "y": 46
        },
        "goalMouthCoordinates": {
        "x": 71.51,
        "y": 50.33
        },
        "goalkeeper": {
        "name": "David Soria",
        "firstName": "David",
        "lastName": "Soria",
        "slug": "david-soria",
        "shortName": "D. Soria",
        "position": "G",
        "jerseyNumber": "13",
        "height": 192,
        "userCount": 1030,
        "gender": "M",
        "sofascoreId": "r6zitm",
        "id": 604258,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 733881600,
        "proposedMarketValueRaw": {
            "value": 2700000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ديفيد سوريا",
            "bn": "ডেভিড সোরিয়া",
            "hi": "डेविड सोरिया",
            "ru": "Давид Сория"
            },
            "shortNameTranslation": {
            "ar": "د. سوريا",
            "bn": "ডি. সোরিয়া",
            "hi": "डी. सोरिया",
            "ru": "Д. Сория"
            }
        }
        },
        "isHome": true,
        "goalType": "regular",
        "xg": 0.057896740734577,
        "xgot": 0.81557601690292,
        "situation": "fast-break",
        "goalMouthLocation": "high-right"
    }
    ],
    "id": 357874316,
    "time": 90,
    "addedTime": 1,
    "isHome": true,
    "incidentClass": "regular",
    "incidentType": "goal",
    "reversedPeriodTime": 1
}

{
    "from": "penalty",
    "homeScore": 2,
    "awayScore": 1,
    "player": {
    "name": "Peque Fernández",
    "slug": "peque-fernandez",
    "shortName": "P. Fernández",
    "position": "M",
    "jerseyNumber": "10",
    "height": 168,
    "userCount": 963,
    "gender": "M",
    "sofascoreId": "8jrfb5",
    "id": 997033,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 1033689600,
    "proposedMarketValueRaw": {
        "value": 3300000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "بيكي فيرنانديز",
        "ru": "Пекке Фернандес"
        },
        "shortNameTranslation": {
        "ar": "ب. فيرنانديز",
        "ru": "П. Фернандес"
        }
    }
    },
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Peque Fernández",
        "slug": "peque-fernandez",
        "shortName": "P. Fernández",
        "position": "M",
        "jerseyNumber": "10",
        "height": 168,
        "userCount": 963,
        "gender": "M",
        "sofascoreId": "8jrfb5",
        "id": 997033,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 1033689600,
        "proposedMarketValueRaw": {
            "value": 3300000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "بيكي فيرنانديز",
            "ru": "Пекке Фернандес"
            },
            "shortNameTranslation": {
            "ar": "ب. فيرنانديز",
            "ru": "П. Фернандес"
            }
        }
        },
        "eventType": "goal",
        "bodyPart": "right-foot",
        "time": 90,
        "playerCoordinates": {
        "x": 88.5,
        "y": 50
        },
        "gkCoordinates": {
        "x": 99.5,
        "y": 50.2
        },
        "goalShotCoordinates": {
        "x": 100,
        "y": 53.6
        },
        "goalMouthCoordinates": {
        "x": 30.65,
        "y": 63
        },
        "goalkeeper": {
        "name": "Augusto Batalla",
        "firstName": "Augusto",
        "lastName": "Batalla",
        "slug": "augusto-batalla",
        "shortName": "A. Batalla",
        "position": "G",
        "jerseyNumber": "13",
        "height": 186,
        "userCount": 1710,
        "gender": "M",
        "sofascoreId": "pw9jt7",
        "id": 358910,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 830822400,
        "proposedMarketValueRaw": {
            "value": 5800000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "أوغوستو باتالا",
            "bn": "অগাস্টো বাটাল্লা",
            "hi": "ऑगस्टो बटाला",
            "ru": "Аугусто Баталья"
            },
            "shortNameTranslation": {
            "ar": "أ. باتالا",
            "bn": "এ. বাটাল্লা",
            "hi": "ए. बटाला",
            "ru": "А. Баталья"
            }
        }
        },
        "isHome": true,
        "goalType": "penalty",
        "xg": 0.7884,
        "xgot": 0.92967838048935,
        "situation": "penalty",
        "goalMouthLocation": "high-left"
    }
    ],
    "id": 357886291,
    "time": 90,
    "addedTime": 7,
    "isHome": true,
    "incidentClass": "penalty",
    "incidentType": "goal",
    "reversedPeriodTime": 1
}

{
    "from": "owngoal",
    "homeScore": 1,
    "awayScore": 2,
    "player": {
    "name": "Kauan",
    "firstName": "Kauan",
    "lastName": "",
    "slug": "kauan",
    "shortName": "Kauan",
    "position": "D",
    "jerseyNumber": "21",
    "height": 191,
    "userCount": 45,
    "gender": "M",
    "sofascoreId": "ut7bip",
    "id": 1198003,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 1049673600,
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "كاوان",
        "ru": "Кауан"
        },
        "shortNameTranslation": {
        "ar": "كاوان"
        }
    }
    },
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Rodrigo Nestor",
        "firstName": "Rodrigo",
        "lastName": "Nestor",
        "slug": "rodrigo-nestor",
        "shortName": "R. Nestor",
        "position": "M",
        "jerseyNumber": "11",
        "height": 174,
        "userCount": 2367,
        "gender": "M",
        "sofascoreId": "ccnrxg",
        "id": 905461,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 965779200,
        "proposedMarketValueRaw": {
            "value": 7400000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "رودريغو نيستور",
            "bn": "রদ্রিগো নেস্টর",
            "hi": "रोड्रिगो नेस्टर",
            "ru": "Родриго Нестор"
            },
            "shortNameTranslation": {
            "ar": "ر. نيستور",
            "bn": "আর. নেস্টর",
            "hi": "आर. नेस्टर",
            "ru": "Р. Нестор"
            }
        }
        },
        "eventType": "pass",
        "time": 42,
        "playerCoordinates": {
        "x": 22.2,
        "y": 30.9
        },
        "passEndCoordinates": {
        "x": 24.7,
        "y": 38.8
        },
        "isHome": false
    },
    {
        "player": {
        "name": "Nicolás Acevedo",
        "firstName": "Nicolas",
        "lastName": "Acevedo",
        "slug": "nicolas-acevedo",
        "shortName": "N. Acevedo",
        "position": "M",
        "jerseyNumber": "5",
        "height": 173,
        "userCount": 1429,
        "gender": "M",
        "sofascoreId": "q446mj",
        "id": 959620,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 924048000,
        "proposedMarketValueRaw": {
            "value": 5600000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "نيكولاس أسيفيدو",
            "ru": "Николас Асеведо"
            },
            "shortNameTranslation": {
            "ar": "ن. أسيفيدو",
            "ru": "Н. Асеведо"
            }
        }
        },
        "eventType": "pass",
        "time": 42,
        "playerCoordinates": {
        "x": 24.7,
        "y": 35.1
        },
        "passEndCoordinates": {
        "x": 15.4,
        "y": 31.7
        },
        "isHome": false
    },
    {
        "player": {
        "name": "Erick Pulga",
        "firstName": "Erick",
        "lastName": "Pulga",
        "slug": "erick-pulga",
        "shortName": "E. Pulga",
        "position": "F",
        "jerseyNumber": "16",
        "height": 169,
        "userCount": 5356,
        "gender": "M",
        "sofascoreId": "fp2p5a",
        "id": 1104070,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 970358400,
        "proposedMarketValueRaw": {
            "value": 11700000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "إريك بولغا",
            "bn": "এরিক পুলগা",
            "hi": "एरिक पुल्गा",
            "ru": "Эрик Пулга"
            },
            "shortNameTranslation": {
            "ar": "إ. بولغا",
            "bn": "ই. পুলগা",
            "hi": "ई. पुल्गा",
            "ru": "Э. Пулга"
            }
        }
        },
        "eventType": "pass",
        "time": 42,
        "playerCoordinates": {
        "x": 15.2,
        "y": 31.7
        },
        "passEndCoordinates": {
        "x": 16.3,
        "y": 36.5
        },
        "isHome": false
    },
    {
        "player": {
        "name": "Rodrigo Nestor",
        "firstName": "Rodrigo",
        "lastName": "Nestor",
        "slug": "rodrigo-nestor",
        "shortName": "R. Nestor",
        "position": "M",
        "jerseyNumber": "11",
        "height": 174,
        "userCount": 2367,
        "gender": "M",
        "sofascoreId": "ccnrxg",
        "id": 905461,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 965779200,
        "proposedMarketValueRaw": {
            "value": 7400000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "رودريغو نيستور",
            "bn": "রদ্রিগো নেস্টর",
            "hi": "रोड्रिगो नेस्टर",
            "ru": "Родриго Нестор"
            },
            "shortNameTranslation": {
            "ar": "ر. نيستور",
            "bn": "আর. নেস্টর",
            "hi": "आर. नेस्टर",
            "ru": "Р. Нестор"
            }
        }
        },
        "eventType": "pass",
        "time": 42,
        "playerCoordinates": {
        "x": 16.3,
        "y": 36.5
        },
        "passEndCoordinates": {
        "x": 4.5,
        "y": 28.5
        },
        "isHome": false
    },
    {
        "player": {
        "name": "Erick Pulga",
        "firstName": "Erick",
        "lastName": "Pulga",
        "slug": "erick-pulga",
        "shortName": "E. Pulga",
        "position": "F",
        "jerseyNumber": "16",
        "height": 169,
        "userCount": 5356,
        "gender": "M",
        "sofascoreId": "fp2p5a",
        "id": 1104070,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 970358400,
        "proposedMarketValueRaw": {
            "value": 11700000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "إريك بولغا",
            "bn": "এরিক পুলগা",
            "hi": "एरिक पुल्गा",
            "ru": "Эрик Пулга"
            },
            "shortNameTranslation": {
            "ar": "إ. بولغا",
            "bn": "ই. পুলগা",
            "hi": "ई. पुल्गा",
            "ru": "Э. Пулга"
            }
        }
        },
        "eventType": "pass",
        "time": 42,
        "playerCoordinates": {
        "x": 3.1,
        "y": 28.9
        },
        "passEndCoordinates": {
        "x": 4.1,
        "y": 46.8
        },
        "isHome": false
    },
    {
        "player": {
        "name": "Kauan",
        "firstName": "Kauan",
        "lastName": "",
        "slug": "kauan",
        "shortName": "Kauan",
        "position": "D",
        "jerseyNumber": "21",
        "height": 191,
        "userCount": 45,
        "gender": "M",
        "sofascoreId": "ut7bip",
        "id": 1198003,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 1049673600,
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "كاوان",
            "ru": "Кауан"
            },
            "shortNameTranslation": {
            "ar": "كاوان"
            }
        }
        },
        "eventType": "goal",
        "bodyPart": "left-foot",
        "time": 42,
        "playerCoordinates": {
        "x": 2.6,
        "y": 44.7
        },
        "gkCoordinates": {
        "x": 98.6,
        "y": 54
        },
        "goalShotCoordinates": {
        "x": 0,
        "y": 51.2
        },
        "goalMouthCoordinates": {
        "x": 56.45,
        "y": 83.17
        },
        "goalkeeper": {
        "name": "Anderson",
        "slug": "anderson",
        "shortName": "Anderson",
        "position": "G",
        "jerseyNumber": "98",
        "height": 190,
        "userCount": 448,
        "gender": "M",
        "sofascoreId": "wrj9tm",
        "id": 942061,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 889056000,
        "proposedMarketValueRaw": {
            "value": 420000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "أندرسون",
            "ru": "Андерсон"
            },
            "shortNameTranslation": {
            "ar": "أندرسون"
            }
        }
        },
        "isHome": true,
        "goalType": "own",
        "situation": "regular",
        "goalMouthLocation": "low-centre"
    }
    ],
    "id": 357948833,
    "time": 42,
    "isHome": false,
    "incidentClass": "ownGoal",
    "incidentType": "goal",
    "reversedPeriodTime": 4
}

### 5.2 Attributs

1. Attributs globaux :

- **from** : Origine du but (non obligatoire. Cet attribut est affiché dans 2 conditions particulières)
    1. Si le but est un but marqué "contre son camp", la valeur brute observée est "owngoal" et la valeur canonique locale est "ownGoal".
    2. Si le but est un but marqué sur penalty, cet attribut vaut "penalty".
    3. La valeur brute "shot" a également été observée sur des buts de classe "regular". Elle est conservée dans le snapshot brut mais n'est pas interprétée comme une origine spéciale ni affichée dans la colonne DÉTAIL.
    4. La valeur brute "regular" a également été observée sur des buts de classe "regular". Elle est redondante avec `incidentClass`, reste conservée dans le snapshot brut et n'est pas interprétée comme une origine spéciale ni affichée dans la colonne DÉTAIL.
    5. Toute autre combinaison entre `from` et `incidentClass` reste incompatible.
- **homeScore** : Score de l'équipe à domicile (au moment du but) ;
- **awayScore** : Score de l'équipe à l'extérieur (au moment du but) ;
- **player** : Bloc concernant le buteur ;
- **assist1** : Bloc concernant le passeur décisif (non obligatoire)
    1. Cet attribut est affiché uniquement si un passeur décisif est impliqué dans l'action du but.
    2. Cet attribut n'est pas affiché en l'absence d'un passeur décisif
- footballPassingNetworkAction : Bloc détaillé concernant l'action du but ;
- id : identifiant unique ;
- **time** : minute de jeu où le but a été marqué ;
- **addedTime** : minute de temps additionnel où le but a été marqué (non obligatoire)
    1. Cet attribut est affiché si le but a été marqué dans le temps additionnel d'une période.
    2. Cet attribut n'est pas affiché si le but a été marqué hors temps additionnel d'une période.
- **isHome** : 2 valeurs possibles
    1. true si le but a été accordé pour l'équipe à domicile
    2. false si le but a été accordé pour l'équipe à l'extérieur
- **incidentClass** : 3 valeurs possibles
    1. "ownGoal" s'il s'agit d'un but "contre son camp".
    2. "penalty" si le but a été marqué sur penalty.
    3. "regular" dans les autres cas.
- **incidentType** : vaut obligatoirement "goal" ;
- reversedPeriodTime : Temps restant à jouer au moment où le but a été marqué.

2. Bloc player :

Les champs inclus dans ce bloc sont identiques aux champs inclus dans les blocs playerIn et playerOut pour les changements de joueurs (se reporter à la partie 2 du paragraphe 4.2).

3. Bloc assist1

Ce bloc, s'il existe donne les détails du joueur auteur de la passe décisive précédant un but marqué.
Les attributs de ce bloc sont identiques aux blocs playerIn/playerOut à l'exception des attributs firstName et lastName qui n'existent pas dans ce bloc.

4. Tableau footballPassingNetworkAction

Ce tableau peut inclure entre 1 et N blocs player dont :
- 1 bloc player concernant le buteur (ou le joueur ayant marqué contre son camp s'il s'agit d'un but classé "ownGoal") ;
- 1 bloc player concernant le passeur décisif, s'il existe.
- Un ou plusieurs autres blocs player, s'il(s) existe(nt), concerne(nt) un/des joueur(s) ayant participé à l'action du but, sans être buteur(s), ni passeur(s) décisif(s)

A noter : ce tableau n'a pas besoin d'être exploité pour J5. Il pourra éventuellement être utilisé pour des futures évolutions selon les besoins.

### 5.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type But (goal) :
- time (colonne MINUTE) ;
- addedTime (si l'attribut existe, suffixe de la colonne MINUTE) ;
- incidentType (colonne TYPE) ;
- isHome (colonne CÔTÉ) ;
- player.name (colonne JOUEUR) ;
- assist1.name (si l'attribut existe, colonne PASSEUR) ;
- incidentClass (colonne CLASSE) ;
- from (si l'attribut existe, colonne DÉTAIL) ;
- homeScore et awayScore (colonne SCORE).

## 6. Carton (card)

### 6.1 Structure JSON d'un carton distribué

Ci-dessous des exemples de blocs définissant un carton distribué au cours d'un match de football :

{
    "player": {
    "name": "Mario Martín",
    "slug": "martin-mario",
    "shortName": "M. Martín",
    "position": "M",
    "jerseyNumber": "6",
    "height": 177,
    "userCount": 22484,
    "gender": "M",
    "sofascoreId": "cem4nz",
    "id": 1154549,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 1078444800,
    "proposedMarketValueRaw": {
        "value": 10700000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "مارتن، ماريو",
        "bn": "মার্টিন, মারিও",
        "hi": "मार्टिन, मारियो",
        "ru": "Марио Мартин"
        },
        "shortNameTranslation": {
        "ar": "م. مارتن",
        "bn": "এম. মার্টিন",
        "hi": "एम. मार्टिन",
        "ru": "М. Мартин"
        }
    }
    },
    "playerName": "Mario Martín",
    "reason": "Foul",
    "rescinded": false,
    "id": 125432774,
    "time": 23,
    "isHome": false,
    "incidentClass": "yellow",
    "incidentType": "card",
    "reversedPeriodTime": 23
}

{
    "player": {
    "name": "Kiko Femenía",
    "slug": "kiko-femenia",
    "shortName": "K. Femenía",
    "position": "D",
    "jerseyNumber": "17",
    "height": 176,
    "userCount": 486,
    "gender": "M",
    "sofascoreId": "vu8r2s",
    "id": 53739,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 665452800,
    "proposedMarketValueRaw": {
        "value": 740000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "كيكو فيمينيا",
        "bn": "কিকো ফেমেনিয়া",
        "hi": "किको फेमेनिया",
        "ru": "Кико Феменя"
        },
        "shortNameTranslation": {
        "ar": "ك. فيمينيا",
        "bn": "কে. ফেমেনিয়া",
        "hi": "के. फेमेनिया",
        "ru": "К. Феменя"
        }
    }
    },
    "playerName": "Kiko Femenía",
    "reason": "Foul",
    "rescinded": false,
    "id": 125432886,
    "time": 42,
    "isHome": false,
    "incidentClass": "red",
    "incidentType": "card",
    "reversedPeriodTime": 4
}

{
    "player": {
    "name": "Xeka",
    "firstName": "Miguel Angelo",
    "lastName": "Xeka",
    "slug": "xeka",
    "shortName": "Xeka",
    "position": "M",
    "jerseyNumber": "8",
    "height": 186,
    "userCount": 260,
    "gender": "M",
    "sofascoreId": "k8g4uj",
    "id": 858705,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 784425600,
    "proposedMarketValueRaw": {
        "value": 750000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "كسيكا",
        "bn": "জেকা",
        "hi": "ज़ेका",
        "ru": "Ксэка"
        },
        "shortNameTranslation": {
        "ar": "كسيكا",
        "bn": "জেকা",
        "hi": "ज़ेका",
        "ru": "Ксэка"
        }
    }
    },
    "playerName": "Xeka",
    "reason": "Foul",
    "rescinded": false,
    "id": 125437126,
    "time": 32,
    "isHome": false,
    "incidentClass": "yellowRed",
    "incidentType": "card",
    "reversedPeriodTime": 14
}

### 6.2 Attributs

- player : Bloc du joueur ayant écopé du carton ;
- **playerName** : Nom du joueur ayant écopé du carton ;
- **reason** : Motif du carton (13 valeurs possibles)
    1. "Argument" : Contestation.
    2. "Foul" : Faute.
    3. "Violent conduct" : Comportement violent.
    4. "Simulation" : Simulation.
    5. "Time wasting" : Perte de temps.
    6. "Professional foul last man" : Faute volontaire du dernier défenseur ou Faute annihilant une occasion nette de but.
    7. "Handball" : Main.
    8. "Persistent fouling" : Fautes répétitives.
    9. "Unsporting behaviour" : Comportement antisportif.
    10. "Unallowed field entering" : Non autorisé à entrer sur le terrain.
    11. "Other reason" : Autre motif déclaré par le fournisseur.
    12. "Off the ball foul" : Obstruction ou faute commise loin du ballon.
    13. "Leaving field" : Joueur quittant le terrain sans autorisation préalable.
A noter : cet attribut peut ne pas être fourni dans certaines compétitions. Son absence ou sa valeur
`null` est acceptée, persistée sans motif et affichée par `—`. Lorsqu'il est présent, il doit rester
dans le vocabulaire ci-dessus ; une valeur inconnue reste `SCHEMA_INCOMPATIBLE`.
- rescinded : 2 valeurs possibles
    1. true si le carton a été révoqué (sans appel à la VAR).
    2. false si le carton n'a pas été révoqué.
- id : Identifiant unique ;
- **time** : Minute de jeu où le carton a été distribué (sa valeur est négative si l'attribut benchTime existe. Sinon, sa valeur est positive);
- **benchTime** : minute de jeu où le carton a été distribué (cas particulier, non obligatoire)
    1. Cet attribut est affiché si la personne concernée par le carton est un entraîneur ou un joueur sur le banc de touche au moment où le carton est distribué.
    2. Cet attribut n'est pas affiché dans les autres cas.
- **benchAddedTime** : temps additionnel associé à `benchTime` (cas particulier, non obligatoire).
    1. Il n'est accepté que pour un carton dont `time` est négatif, avec `benchTime` présent et sans
       `addedTime` concurrent.
    2. Il complète alors la minute affichée sous la forme `benchTime+benchAddedTime` ; il n'est
       jamais interprété pour un autre type d'incident.
- **isHome** : 2 valeurs possibles
    1. true si le joueur concerné joue dans l'équipe évoluant à domicile.
    2. false si le joueur concerné joue dans l'équipe évoluant à l'extérieur.
- **incidentClass** : 3 valeurs possibles
    1. "yellow" s'il s'agit d'un carton jaune.
    2. "red" s'il s'agit d'un carton rouge direct.
    3. "yellowRed" s'il s'agit du second carton jaune reçu par le joueur concerné.
- **incidentType** : Vaut obligatoirement "card" ;
- reversedPeriodTime : Temps restant estimé dans la période au moment où le joueur a reçu le carton.

### 6.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type Carton (card) :
- playerName (colonne JOUEUR) ;
- incidentType (colonne TYPE) ;
- reason (s'il existe, colonne MOTIF) ;
- incidentClass (colonne CLASSE) ;
- isHome (colonne CÔTÉ) ;
- time (si sa valeur est strictement positive et en l'absence de l'attribut benchTime, colonne MINUTE) ;
- benchTime (s'il existe, colonne MINUTE à la place de l'attribut time possédant dans ce cas une valeur strictement négative), complété par `benchAddedTime` lorsqu'il existe ;
- rescinded (si sa valeur vaut true, colonne DÉTAIL).

## 7. Temps additionnel (injuryTime)

### 7.1 Structure JSON du temps additionnel d'une période

Ci-dessous quelques exemples de blocs définissant le temps additionnel dans une période d'un match de football :

{
    "length": 5,
    "time": 90,
    "addedTime": 0,
    "incidentType": "injuryTime",
    "reversedPeriodTime": 1
}

{
    "length": 3,
    "time": 45,
    "addedTime": 0,
    "incidentType": "injuryTime",
    "reversedPeriodTime": 1
}

### 7.2 Attributs

- **length** : Durée du temps additionnel (c'est-à-dire le nombre de minutes annoncé par le 4ème arbitre au début du temps additionnel) ;
- **time** : Nombre de minutes joué depuis le coup d'envoi (hors temps additionnel) ;
- addedTime : Nombre de minutes supplémentaires rajoutées à la fin du temps initial (défini par length) ;
- **incidentType** : Vaut obligatoirement "injuryTime" ;
- reversedPeriodTime : Valorisé par défaut à 1.

### 7.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type Temps additionnel (injuryTime) :
- time (colonne MINUTE) ;
- addedTime (si l'attribut existe, suffixe de la colonne MINUTE) ;
- incidentType (colonne TYPE) ;
- length (colonne DÉTAIL).

## 8. Décisions arbitrales prises avec la VAR (varDecision)

### 8.1 Structure JSON d'une prise de décision arbitrale avec l'aide de la VAR

Ci-dessous quelques exemples de blocs définissant des décisions arbitrales prises avec l'assistance vidéo (autrement dit la VAR) :

{
    "confirmed": false,
    "player": {
    "name": "Lawrence Ennali",
    "firstName": "Lawrence Ennali",
    "slug": "lawrence-ennali",
    "shortName": "L. Ennali",
    "position": "M",
    "jerseyNumber": "11",
    "height": 172,
    "userCount": 455,
    "gender": "M",
    "sofascoreId": "jvzmjz",
    "id": 1192579,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 1015459200,
    "proposedMarketValueRaw": {
        "value": 1800000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "لورنس إنالي",
        "ru": "Лоуренс Эннали"
        },
        "shortNameTranslation": {
        "ar": "ل. إنالي",
        "ru": "Л. Эннали"
        }
    }
    },
    "isHome": true,
    "id": 101615,
    "time": 57,
    "incidentClass": "penaltyNotAwarded",
    "incidentType": "varDecision",
    "reversedPeriodTime": 34
}

{
    "confirmed": false,
    "player": {
    "name": "Morato",
    "firstName": "",
    "lastName": "",
    "slug": "morato",
    "shortName": "Morato",
    "position": "M",
    "jerseyNumber": "12",
    "height": 170,
    "userCount": 91,
    "gender": "M",
    "sofascoreId": "qb9svp",
    "id": 866468,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 715305600,
    "proposedMarketValueRaw": {
        "value": 220000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "موراتو",
        "bn": "মোরাতো",
        "hi": "मोराटो",
        "ru": "Морато"
        },
        "shortNameTranslation": {
        "ar": "موراتو",
        "bn": "মোরাতো",
        "hi": "मोराटो",
        "ru": "Морато"
        }
    }
    },
    "isHome": true,
    "id": 101684,
    "time": 120,
    "addedTime": 6,
    "incidentClass": "redCardGiven",
    "incidentType": "varDecision",
    "reversedPeriodTime": 1
}

{
    "confirmed": true,
    "player": {
    "name": "Yannick Carrasco",
    "firstName": "Yannick",
    "lastName": "Carrasco",
    "slug": "yannick-carrasco",
    "shortName": "Y. Carrasco",
    "position": "M",
    "jerseyNumber": "10",
    "height": 180,
    "userCount": 6620,
    "gender": "M",
    "sofascoreId": "yannickferreiracarrasco",
    "id": 182001,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 747100800,
    "proposedMarketValueRaw": {
        "value": 4800000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "يانيك كاراسكو",
        "bn": "ইয়ানিক ক্যারাস্কো",
        "hi": "यूनिक कैरास्को",
        "ru": "Янник Карраско"
        },
        "shortNameTranslation": {
        "ar": "ي. كاراسكو",
        "bn": "ওয়াই. ক্যারাস্কো",
        "hi": "वाई. कैरास्को",
        "ru": "Я. Карраско"
        }
    }
    },
    "isHome": false,
    "id": 101682,
    "time": 113,
    "incidentClass": "penaltyAwarded",
    "incidentType": "varDecision",
    "reversedPeriodTime": 8
}

{
    "confirmed": true,
    "player": {
    "name": "Mady Camara",
    "firstName": "Mady",
    "lastName": "Camara",
    "slug": "mady-camara",
    "shortName": "M. Camara",
    "position": "M",
    "jerseyNumber": "2",
    "height": 182,
    "userCount": 1868,
    "gender": "M",
    "sofascoreId": "txbmy5",
    "id": 867546,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 857088000,
    "proposedMarketValueRaw": {
        "value": 5200000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "مادي كامارا",
        "bn": "ম্যাডি কামারা",
        "hi": "मैडी कैमारा",
        "ru": "Мади Камара"
        },
        "shortNameTranslation": {
        "ar": "م. كامارا",
        "bn": "এম. এম. কামারা",
        "hi": "एमएम कैमारा",
        "ru": "М. Камара"
        }
    }
    },
    "isHome": false,
    "id": 101457,
    "time": 74,
    "incidentClass": "goalNotAwarded",
    "incidentType": "varDecision",
    "reversedPeriodTime": 17
}

{
    "confirmed": false,
    "player": {
    "name": "Désiré Doué",
    "firstName": "Désiré Doué",
    "lastName": "",
    "slug": "desire-doue",
    "shortName": "D. Doué",
    "position": "F",
    "jerseyNumber": "14",
    "height": 181,
    "userCount": 128716,
    "gender": "M",
    "sofascoreId": "DD14",
    "id": 1154605,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 1117756800,
    "proposedMarketValueRaw": {
        "value": 124000000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "ديزيري دويه",
        "bn": "ডিজায়ার ডুয়ে",
        "hi": "डेसिरे डौए",
        "ru": "Дезире Дуэ"
        },
        "shortNameTranslation": {
        "ar": "د. دويه",
        "bn": "ডি. ডুয়ে",
        "hi": "डी. डौए",
        "ru": "Д. Дуэ"
        }
    }
    },
    "isHome": true,
    "id": 101437,
    "time": 62,
    "incidentClass": "goalNotAwarded",
    "incidentType": "varDecision",
    "reversedPeriodTime": 29
}

### 8.2 Attributs

- **confirmed** : 2 valeurs possibles
    1. true si la décision de la VAR est confirmée par l'arbitre.
    2. false si la décision de la VAR est rejetée par l'arbitre.
- **player** : Bloc du joueur concerné par la décision de la VAR ;
- **isHome** : 2 valeurs possibles
    1. true si la décision de la VAR concerne l'équipe à domicile.
    2. false si la décision de la VAR concerne l'équipe à l'extérieur.
- id : Identifiant unique ;
- **time** : Minute de jeu où la VAR a été sollicitée par l'arbitre ;
- **incidentClass** : type de décision prise via la VAR (plusieurs valeurs possibles)
    1. "goalNotAwarded" : But refusé confirmé (si confirmed = true) ou But accordé (si confirmed = false).
    2. "goalAwarded" : But confirmé (si confirmed = true) ou But annulé (si confirmed = false).
    3. "penaltyAwarded" : Penalty confirmé (si confirmed = true) ou Penalty non confirmé (si confirmed = false).
    4. "penaltyNotAwarded" : Pas de penalty confirmé (si confirmed = true) ou Penalty accordé (si confirmed = false).
    5. "redCardGiven" : Carton rouge annulé (si confirmed = false) ou Carton rouge confirmé (si confirmed = true).
    6. "cardUpgrade" : Carton changé (si confirmed = false) ou Carton inchangé (si confirmed = true).
    7. "review" : Revue sans catégorie plus précise (confirmed = true ou confirmed = false).
- **incidentType** : Vaut obligatoirement "varDecision" ;
- reversedPeriodTime : Temps restant estimé avant la fin de la période.

### 8.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type Décision VAR (varDecision) :
- time (colonne MINUTE) ;
- addedTime (si l'attribut existe, suffixe de la colonne MINUTE) ;
- incidentType (colonne TYPE) ;
- player.name (colonne JOUEUR) ;
- isHome (colonne CÔTÉ) ;
- incidentClass (colonne CLASSE, conditionné par la valeur de confirmed) ;
- confirmed (colonne DÉTAIL).

## 9. Penalty tiré sans qu'il soit marqué (inGamePenalty)

### 9.1 Structure JSON d'un penalty tiré sans qu'il soit marqué

Ci-dessous quelques exemples de blocs définissant un penalty tiré non transformé :

{
    "time": 28,
    "player": {
    "name": "Kylian Mbappé",
    "slug": "kylian-mbappe",
    "shortName": "K. Mbappé",
    "position": "F",
    "jerseyNumber": "10",
    "height": 180,
    "userCount": 2449223,
    "gender": "M",
    "sofascoreId": "KMbappe",
    "id": 826643,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 914112000,
    "proposedMarketValueRaw": {
        "value": 212000000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "كيليان مبابي",
        "bn": "কিলিয়ান এমবাপ্পে",
        "hi": "किलियन एमबाप्पे",
        "ru": "Килиан Мбаппе"
        },
        "shortNameTranslation": {
        "ar": "ك. مبابي",
        "bn": "কে. এমবাপ্পে",
        "hi": "के. एमबाप्पे",
        "ru": "К. Мбаппе"
        }
    }
    },
    "description": "Goalkeeper save",
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Kylian Mbappé",
        "slug": "kylian-mbappe",
        "shortName": "K. Mbappé",
        "position": "F",
        "jerseyNumber": "10",
        "height": 180,
        "userCount": 2449223,
        "gender": "M",
        "sofascoreId": "KMbappe",
        "id": 826643,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 914112000,
        "proposedMarketValueRaw": {
            "value": 212000000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "كيليان مبابي",
            "bn": "কিলিয়ান এমবাপ্পে",
            "hi": "किलियन एमबाप्पे",
            "ru": "Килиан Мбаппе"
            },
            "shortNameTranslation": {
            "ar": "ك. مبابي",
            "bn": "কে. এমবাপ্পে",
            "hi": "के. एमबाप्पे",
            "ru": "К. Мбаппе"
            }
        }
        },
        "eventType": "penalty-save",
        "time": 28,
        "playerCoordinates": {
        "x": 88.5,
        "y": 50
        },
        "goalShotCoordinates": {
        "x": 100,
        "y": 47.6
        },
        "goalMouthCoordinates": {
        "x": 62.9,
        "y": 95.83
        },
        "goalkeeper": {
        "name": "Yassine Bounou",
        "firstName": "Yassine",
        "lastName": "Bounou",
        "slug": "bono",
        "shortName": "Bono",
        "position": "G",
        "jerseyNumber": "1",
        "height": 195,
        "userCount": 82618,
        "gender": "M",
        "sofascoreId": "BonoY",
        "id": 360938,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 670809600,
        "proposedMarketValueRaw": {
            "value": 2900000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ياسين بونو",
            "bn": "বোনো",
            "hi": "बोनो",
            "ru": "Яссин Буну"
            },
            "shortNameTranslation": {
            "ar": "ي. بونو",
            "bn": "বোনো",
            "hi": "बोनो",
            "ru": "Я. Буну"
            }
        }
        },
        "isHome": true,
        "xg": 0.7884,
        "xgot": 0.74667394161224,
        "situation": "penalty",
        "goalMouthLocation": "low-right"
    }
    ],
    "id": 118645777,
    "incidentType": "inGamePenalty",
    "isHome": true,
    "incidentClass": "missed",
    "reason": "goalkeeperSave",
    "reversedPeriodTime": 18
}

{
    "time": 9,
    "player": {
    "name": "Lionel Messi",
    "slug": "lionel-messi",
    "shortName": "L. Messi",
    "position": "F",
    "jerseyNumber": "10",
    "height": 169,
    "userCount": 3003409,
    "gender": "M",
    "sofascoreId": "LM10",
    "id": 12994,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 551491200,
    "proposedMarketValueRaw": {
        "value": 16300000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "ليونيل ميسي",
        "bn": "লিওনেল মেসি",
        "hi": "लियोनेल मेसी",
        "ru": "Лионель Месси"
        },
        "shortNameTranslation": {
        "ar": "ل. ميسي",
        "bn": "এল. মেসি",
        "hi": "एल. मेसी",
        "ru": "Л. Месси"
        }
    }
    },
    "description": "Off target",
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Lionel Messi",
        "slug": "lionel-messi",
        "shortName": "L. Messi",
        "position": "F",
        "jerseyNumber": "10",
        "height": 169,
        "userCount": 3003409,
        "gender": "M",
        "sofascoreId": "LM10",
        "id": 12994,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 551491200,
        "proposedMarketValueRaw": {
            "value": 16300000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "ليونيل ميسي",
            "bn": "লিওনেল মেসি",
            "hi": "लियोनेल मेसी",
            "ru": "Лионель Месси"
            },
            "shortNameTranslation": {
            "ar": "ل. ميسي",
            "bn": "এল. মেসি",
            "hi": "एल. मेसी",
            "ru": "Л. Месси"
            }
        }
        },
        "eventType": "penalty-miss",
        "time": 9,
        "playerCoordinates": {
        "x": 88.5,
        "y": 50
        },
        "gkCoordinates": {
        "x": 99.5,
        "y": 50
        },
        "goalShotCoordinates": {
        "x": 100,
        "y": 43.4
        },
        "goalMouthCoordinates": {
        "x": 85.48,
        "y": 81.5
        },
        "goalkeeper": {
        "name": "Alexander Schlager",
        "firstName": "Alexander",
        "lastName": "Schlager",
        "slug": "alexander-schlager",
        "shortName": "A. Schlager",
        "position": "G",
        "jerseyNumber": "1",
        "height": 184,
        "userCount": 739,
        "gender": "M",
        "sofascoreId": "bcwjaq",
        "id": 282073,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 823132800,
        "proposedMarketValueRaw": {
            "value": 1900000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "الكسندر شلاغر",
            "bn": "আলেকজান্ডার শ্ল্যাগার",
            "hi": "अलेक्जेंडर श्लेगर",
            "ru": "Александр Шлагер"
            },
            "shortNameTranslation": {
            "ar": "ا. شلاغر",
            "bn": "এ. শ্ল্যাগার",
            "hi": "ए. श्लेगर",
            "ru": "А. Шлагер"
            }
        }
        },
        "isHome": true,
        "xg": 0.7884,
        "xgot": 0,
        "situation": "penalty",
        "goalMouthLocation": "close-right"
    }
    ],
    "id": 118642696,
    "incidentType": "inGamePenalty",
    "isHome": true,
    "incidentClass": "missed",
    "reason": "offTarget",
    "reversedPeriodTime": 37
}

### 9.2 Attributs

1. Attributs globaux

- **time** : Minute de jeu où le penalty a été tiré ;
- **player** : Bloc du joueur ayant tiré le penalty. Données du bloc identiques à playerIn/playerOut ;
- **description** : (3 valeurs possibles)
    1. "Off target" : Tir hors cadre.
    2. "Goalkeeper save" : Arrêt du gardien.
    3. "Woodwork" : Tir ayant touché le poteau ou la barre transversale sans entrer dans le but.
- footballPassingNetworkAction : Bloc détaillé concernant l'action du penalty ;
-  id : identifiant unique ;
- **incidentType** : Vaut obligatoirement "inGamePenalty" ;
- **isHome** : 2 valeurs possibles
    1. true si le joueur ayant tiré le penalty joue dans l'équipe évoluant à domicile.
    2. false si le joueur ayant tiré le penalty joue dans l'équipe évoluant à l'extérieur
- **incidentClass** : Vaut obligatoirement "missed" (penalty raté) ;
- **reason** : 3 valeurs possibles
    1. "offTarget" si le tireur n'a pas cadré son penalty.
    2. "goalkeeperSave" si le gardien de but a arrêté le penalty.
    3. "woodwork" si le tir a touché le poteau ou la barre transversale sans entrer dans le but.
Le résultat sur le poteau ou la barre transversale n'est accepté que sous le triplet cohérent
`incidentClass="missed"`, `description="Woodwork"` et `reason="woodwork"`.
Pour un penalty raté, `description` et `reason` peuvent aussi être absents simultanément. Le fait de
jeu est alors conservé avec une complétude `PARTIAL`, un motif affiché par `—` et aucune cause
inventée. Si l'un des deux champs est présent, son couple classe/valeur doit rester cohérent avec le
vocabulaire ci-dessus. Cette tolérance métier ne rend pas `time` facultatif pour `inGamePenalty`.
- reversedPeriodTime : Temps restant estimé avant la fin de la période au moment où le penalty a été tiré (en minutes).

2. Bloc player

Les attributs de ce bloc sont identiques aux blocs playerIn/playerOut à l'exception des attributs firstName et lastName qui n'existent pas dans ce bloc.

3. Tableau footballPassingNetworkAction

Ce tableau inclut obligatoirement :
- 1 bloc player concernant le tireur du penalty;
- 1 bloc goalkeeper concernant le gardien de but impliqué dans le penalty.

### 9.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type Penalty raté (inGamePenalty) :
- time (colonne MINUTE) ;
- incidentType (colonne TYPE) ;
- player.name (colonne JOUEUR) ;
- description (colonne MOTIF) ;
- isHome (colonne CÔTÉ) ;
- incidentClass (colonne CLASSE).

## 10. Séance de tirs au but (penaltyShootout)

### 10.1 Structure JSON d'un penalty tiré dans le cadre d'une séance de tirs au but

Ci-dessous quelques exemples de blocs définissant un penalty tiré dans le cadre d'une séance de tirs au but :

{
    "player": {
    "name": "Mattia Tirelli",
    "firstName": "",
    "lastName": "",
    "slug": "tirelli-mattia",
    "shortName": "M. Tirelli",
    "position": "F",
    "jerseyNumber": "17",
    "height": 183,
    "userCount": 46,
    "gender": "M",
    "sofascoreId": "uttv2f",
    "id": 979667,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 1025395200,
    "proposedMarketValueRaw": {
        "value": 235000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "تيريللي، ماتيا",
        "ru": "Тирелли, Маттиа"
        },
        "shortNameTranslation": {
        "ar": "م. تيريللي",
        "ru": "М. Тирелли"
        }
    }
    },
    "homeScore": 3,
    "awayScore": 2,
    "sequence": 9,
    "description": "Off target",
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Mattia Tirelli",
        "firstName": "",
        "lastName": "",
        "slug": "tirelli-mattia",
        "shortName": "M. Tirelli",
        "position": "F",
        "jerseyNumber": "17",
        "height": 183,
        "userCount": 46,
        "gender": "M",
        "sofascoreId": "uttv2f",
        "id": 979667,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 1025395200,
        "proposedMarketValueRaw": {
            "value": 235000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "تيريللي، ماتيا",
            "ru": "Тирелли, Маттиа"
            },
            "shortNameTranslation": {
            "ar": "م. تيريللي",
            "ru": "М. Тирелли"
            }
        }
        },
        "eventType": "penalty-miss",
        "time": 98,
        "playerCoordinates": {
        "x": 11.5,
        "y": 50
        },
        "gkCoordinates": {
        "x": 0.3,
        "y": 50.2
        },
        "goalShotCoordinates": {
        "x": 0,
        "y": 44.6
        },
        "goalMouthCoordinates": {
        "x": 20.97,
        "y": 76.83
        },
        "goalkeeper": {
        "name": "Nicola Leali",
        "slug": "nicola-leali",
        "shortName": "N. Leali",
        "position": "G",
        "jerseyNumber": "1",
        "height": 193,
        "userCount": 535,
        "gender": "M",
        "sofascoreId": "vxbuzs",
        "id": 98059,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 729907200,
        "proposedMarketValueRaw": {
            "value": 920000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "نيكولا ليالي",
            "bn": "নিকোলা লেইলি",
            "hi": "निकोला लीली",
            "ru": "Никола Леали"
            },
            "shortNameTranslation": {
            "ar": "ن. ليالي",
            "bn": "এন. লেইলি",
            "hi": "एन. लीली",
            "ru": "Н. Леали"
            }
        }
        },
        "isHome": false,
        "situation": "shootout",
        "goalMouthLocation": "left"
    }
    ],
    "id": 118656775,
    "incidentType": "penaltyShootout",
    "isHome": false,
    "incidentClass": "missed",
    "reason": "offTarget"
}

{
    "player": {
    "name": "Andrias Edmundsson",
    "firstName": "Andrias",
    "lastName": "Edmundsson",
    "slug": "andrias-edmundsson",
    "shortName": "A. Edmundsson",
    "position": "D",
    "jerseyNumber": "5",
    "height": 193,
    "userCount": 402,
    "gender": "M",
    "sofascoreId": "uu2kvn",
    "id": 906963,
    "marketValueCurrency": "EUR",
    "dateOfBirthTimestamp": 977097600,
    "proposedMarketValueRaw": {
        "value": 3100000,
        "currency": "EUR"
    },
    "fieldTranslations": {
        "nameTranslation": {
        "ar": "أندرياس إدموندسون",
        "bn": "আন্দ্রিয়াস এডমন্ডসন",
        "hi": "एंड्रियास एडमंडसन",
        "ru": "Андриас Эдмундссон"
        },
        "shortNameTranslation": {
        "ar": "أ. إدموندسون",
        "bn": "এ. এডমন্ডসন",
        "hi": "ए. एडमंडसन",
        "ru": "А. Эдмундссон"
        }
    }
    },
    "homeScore": 3,
    "awayScore": 2,
    "sequence": 8,
    "description": "Scored",
    "footballPassingNetworkAction": [
    {
        "player": {
        "name": "Andrias Edmundsson",
        "firstName": "Andrias",
        "lastName": "Edmundsson",
        "slug": "andrias-edmundsson",
        "shortName": "A. Edmundsson",
        "position": "D",
        "jerseyNumber": "5",
        "height": 193,
        "userCount": 402,
        "gender": "M",
        "sofascoreId": "uu2kvn",
        "id": 906963,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 977097600,
        "proposedMarketValueRaw": {
            "value": 3100000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "أندرياس إدموندسون",
            "bn": "আন্দ্রিয়াস এডমন্ডসন",
            "hi": "एंड्रियास एडमंडसन",
            "ru": "Андриас Эдмундссон"
            },
            "shortNameTranslation": {
            "ar": "أ. إدموندسون",
            "bn": "এ. এডমন্ডসন",
            "hi": "ए. एडमंडसन",
            "ru": "А. Эдмундссон"
            }
        }
        },
        "eventType": "goal",
        "bodyPart": "right-foot",
        "time": 98,
        "playerCoordinates": {
        "x": 88.5,
        "y": 50
        },
        "gkCoordinates": {
        "x": 99.5,
        "y": 50
        },
        "goalShotCoordinates": {
        "x": 100,
        "y": 53.7
        },
        "goalMouthCoordinates": {
        "x": 30.11,
        "y": 93.67
        },
        "goalkeeper": {
        "name": "Federico Del Frate",
        "firstName": "Federico",
        "lastName": "Del Frate",
        "slug": "federico-del-frate",
        "shortName": "F. Del Frate",
        "position": "G",
        "jerseyNumber": "22",
        "height": 192,
        "userCount": 14,
        "gender": "M",
        "sofascoreId": "pq4537",
        "id": 864702,
        "marketValueCurrency": "EUR",
        "dateOfBirthTimestamp": 819504000,
        "proposedMarketValueRaw": {
            "value": 195000,
            "currency": "EUR"
        },
        "fieldTranslations": {
            "nameTranslation": {
            "ar": "فيديريكو ديل فراتي",
            "ru": "Федерико Дель Фрате"
            },
            "shortNameTranslation": {
            "ar": "ف. فراتي",
            "ru": "Ф. Фрате"
            }
        }
        },
        "isHome": true,
        "goalType": "penalty",
        "situation": "shootout",
        "goalMouthLocation": "low-left"
    }
    ],
    "id": 118656773,
    "incidentType": "penaltyShootout",
    "isHome": true,
    "incidentClass": "scored",
    "reason": "scored"
}

### 10.2 Attributs

1. Attributs globaux

- **time** : Minute de jeu où le tir au but a été effectué. Si l'attribut global est absent, J5 utilise le champ `time` de la première entrée non vide de `footballPassingNetworkAction` comme minute effective, tout en conservant le brut inchangé. Une minute peut rester absente uniquement pour une réponse où toutes les tentatives de la séance omettent `time` et où `footballPassingNetworkAction` est soit absent, soit un tableau exactement vide, et seulement si les conditions terminales ci-dessous sont toutes satisfaites ;
- **player** : Bloc du joueur ayant effectué le tir au but. Données du bloc identiques à playerIn/playerOut ;
- homeScore : Score de l'équipe à domicile (à la fin de la séance de tirs au but si cette dernière est terminée) ;
- awayScore : Score de l'équipe à l'extérieur (à la fin de la séance de tirs au but si cette dernière est terminée) ;
- **sequence** : Ordre de passage du tireur (à partir du début de la séance de tirs au but) ;
- **description** : 4 valeurs possibles
    1. "Scored" : Tir au but réussi.
    2. "Off target" : Tir hors cadre.
    3. "Goalkeeper save" : Arrêt du gardien.
    4. "Woodwork" : Tir ayant touché le poteau ou la barre transversale sans entrer dans le but.
- footballPassingNetworkAction : Bloc détaillé concernant l'action du penalty ;
- id : identifiant unique ;
- **incidentType** : Vaut obligatoirement "penaltyShootout" ;
- **isHome** : 2 valeurs possibles
    1. true si le joueur ayant tiré le penalty joue dans l'équipe évoluant à domicile.
    2. false si le joueur ayant tiré le penalty joue dans l'équipe évoluant à l'extérieur
- **incidentClass** : 2 valeurs possibles
    1. "scored" si le penalty a été marqué.
    2. "missed" si le penalty a été raté.
- **reason** : 4 valeurs possibles
    1. "scored" si le penalty a été marqué.
    2. "offTarget" si le tireur n'a pas cadré son penalty.
    3. "goalkeeperSave" si le gardien de but a arrêté le penalty.
    4. "woodwork" si le tir a touché le poteau ou la barre transversale sans entrer dans le but.
Comme pour `inGamePenalty`, `woodwork` exige le triplet cohérent `incidentClass="missed"`,
`description="Woodwork"` et `reason="woodwork"` ; les couples croisés restent incompatibles.
Un tir raté peut également omettre simultanément `description` et `reason`. Il reste alors
`PARTIAL`, sans motif normalisé ni cause déduite. L'absence d'un seul champ lorsque l'autre porte
une valeur, ou une valeur croisée avec `incidentClass`, reste incompatible.
- reversedPeriodTime : Temps restant estimé avant la fin de la période au moment où le penalty a été tiré (en minutes).

#### Séance terminale entièrement non minutée

L'absence de minute n'est compatible que pour le document complet formant une séance terminale
cohérente :

1. un unique marqueur `period` porte exactement `text="PEN"`, `period="penalties"`,
   `isLive=false`, `time=999`, `addedTime=999` et les deux composantes du score final ;
2. un marqueur de fin de temps réglementaire ou de prolongation (`FT` ou `ET`) possède une minute
   effective valide ;
3. chaque `penaltyShootout` omet le champ global `time` et représente l'absence de source
   auxiliaire soit par une propriété `footballPassingNetworkAction` absente, soit par un tableau
   exactement vide ; ces deux sérialisations peuvent coexister, mais aucune tentative réellement
   minutée ne peut être mélangée à cette forme ;
4. chaque tentative porte une classe `scored` ou `missed`, un côté, les deux composantes du score
   courant et une `sequence` strictement positive ;
5. les séquences sont uniques et contiguës de `1` à `N` ;
6. le score de la tentative de séquence maximale est identique au score du marqueur `PEN`.

Dans ce seul contexte, J5 persiste `NULL` comme minute normalisée pour le marqueur `PEN` et les
tentatives, et émet un avertissement de provenance par chemin temporel absent. L'ordre de séance
n'est jamais converti en minute. Une séance isolée, active, mixte, lacunaire, contradictoire ou sans
marqueur terminal reste `SCHEMA_INCOMPATIBLE`.

Cette équivalence est propre à `event-incidents-v15` et ne s'applique jamais à une valeur JSON
`null`, un objet, une chaîne, un nombre, un booléen ou un tableau non vide. Un tableau non vide
continue d'être évalué par les règles historiques de minute imbriquée ; s'il est mal typé,
malformé ou incohérent avec le reste de la séance, la famille entière reste
`SCHEMA_INCOMPATIBLE`. Les parseurs V12 à V14 conservent leur règle historique « propriété
absente seulement ».

2. Bloc player

Les attributs de ce bloc sont identiques aux blocs playerIn/playerOut à l'exception des attributs firstName et lastName qui n'existent pas dans ce bloc.

3. Tableau footballPassingNetworkAction

Ce tableau inclut obligatoirement :
- 1 bloc player concernant le tireur du penalty;
- 1 bloc goalkeeper concernant le gardien de but impliqué dans le penalty.

### 10.3 Valeurs à afficher pour J5

Ci-dessous les valeurs à afficher impérativement pour les incidents de type Tir au but (penaltyShootout) :
- time (colonne MINUTE), ou `—` pour une séance terminale entièrement non minutée et cohérente ;
- incidentType (colonne TYPE) ;
- player.name (colonne JOUEUR) ;
- description (colonne MOTIF) ;
- isHome (colonne CÔTÉ) ;
- incidentClass (colonne CLASSE) ;
- sequence (colonne ORDRE TAB) ;
- homeScore et awayScore lorsqu'ils sont fournis (colonne SCORE).
