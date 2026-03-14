# ExpandProject : ImportData module

Le module `importdata/` contient le code applicatif maintenu pour :

- charger un modele XML ;
- valider un datapack contre ce modele ;
- exposer l'API locale ;
- importer les donnees dans Neo4j.

## Reperes rapides

### Code maintenu a la main

- `src/main/java/fr/expand/project/importdata/api/`
- `src/main/java/fr/expand/project/importdata/dao/`
- `src/main/java/fr/expand/project/importdata/model/`
- `src/main/java/fr/expand/project/importdata/validation/`
- `src/main/java/fr/expand/project/importdata/util/`

### Code genere

- `src/main/java/fr/expand/project/importdata/dto/generated/`
- `src/main/java/fr/expand/project/importdata/model/generated/`

Ces packages sont generes par JAXB depuis :

- `src/main/resources/datapack/data.xsd`
- `src/main/resources/model/model.xsd`
- fichiers de binding `*.xjb`

Ne modifiez pas directement les classes `*.generated`; mettez a jour les XSD/XJB puis relancez la generation.

## Commandes utiles

Generer les sources JAXB :

```bash
mvn generate-sources
```

Compiler et executer les tests :

```bash
mvn test
```

Construire le jar executable :

```bash
mvn package
```

## Note de structure

L'ancien dossier racine `model/` n'est plus le module actif du systeme de modele. Toute evolution du modele doit passer par `importdata/`.