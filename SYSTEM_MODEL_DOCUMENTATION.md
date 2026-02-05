# Système de Modèle de Données XML - Documentation

## Vue d'ensemble

L'application ExpandProject a été étendue pour supporter la définition de modèles de données via des fichiers XML. Ces modèles définissent les types d'objets et les types de liens autorisés avant l'import des données dans Neo4j.

## Architecture

### 1. Schémas XSD

#### model.xsd
Définit la structure d'un modèle de données:

- **DATA_MODEL** (racine)
  - Attributs: NAME, VERSION
  - **OBJECT_TYPES**: Collection de types d'objets
    - **OBJECT_TYPE**: Définition d'un type d'objet
      - NAME: Nom du type
      - PARENT: (optionnel) Nom du type parent pour l'héritage
      - DESCRIPTION: Description textuelle
      - **ATTRIBUTE_DEFINITIONS**: Attributs possibles
        - **ATTRIBUTE_DEFINITION**: Définition d'un attribut
          - NAME: Nom de l'attribut
          - TYPE: Type de données (STRING, INTEGER, DOUBLE, BOOLEAN, DATE)
          - REQUIRED: Attribut obligatoire (true/false)
          - DEFAULT_VALUE: Valeur par défaut
          
  - **LINK_TYPES**: Collection de types de liens
    - **LINK_TYPE**: Définition d'un type de lien
      - NAME: Nom du lien
      - DIRECTED: Lien orienté (true/false)
      - DESCRIPTION: Description textuelle
      - **SOURCE_TYPES**: Types d'objets source autorisés
      - **TARGET_TYPES**: Types d'objets cible autorisés
      - **ATTRIBUTE_DEFINITIONS**: Attributs possibles sur le lien

#### data.xsd  
Définit la structure des données à importer:

- **DATAS** (racine)
  - **OBJECTS**: Collection d'objets
    - **OBJECT**: Instance d'objet
      - ID: Identifiant unique
      - TYPE: Type de l'objet
      - **ATTRIBUTE**: Paires clé-valeur
      
  - **LINKS**: Collection de liens
    - **LINK**: Instance de lien
      - TYPE: Type du lien
      - **OBJ_LINK_A**: Objet source
      - **OBJ_LINK_B**: Objet cible
      - **ATTRIBUTE**: Attributs du lien

### 2. Génération des DTOs

Les DTOs sont générés automatiquement via JAXB 3.x (Jakarta) à partir des XSD:

- **Package datapack**: `fr.expand.project.importdata.dto.generated`
  - Classes générées: DATAS, OBJECT, OBJECTS, LINK, LINKS, ATTRIBUTE, etc.
  - Classes wrapper pour compatibilité: DataPackObject, DataPackAttribute, DataPackObjectLink

- **Package model**: `fr.expand.project.importdata.model.generated`
  - Classes générées: DATAMODEL, OBJECTTYPE, LINKTYPE, ATTRIBUTEDEFINITION, etc.

### 3. Héritage entre types d'objets

Un `OBJECT_TYPE` peut déclarer un parent via l'attribut `PARENT`. Le type enfant hérite de tous les attributs du parent et peut en ajouter de nouveaux.

- La validation des attributs prend en compte l'ensemble des attributs hérités.
- Pour les `LINK_TYPE`, si un `TYPE_REF` autorise un type parent, alors tous ses sous-types sont acceptés.

### 4. Exemples

#### Exemple de Modèle: Réseau Social

Fichier: `importdata/src/main/resources/model/example_social_network_model.xml`

Définit:
- **Types d'objets**: PERSONNE, ENTREPRISE, COMPETENCE
- **Types de liens**: CONNAIT, TRAVAILLE_POUR, POSSEDE_COMPETENCE

#### Exemple de Données

Fichier: `importdata/src/main/resources/datapack/example_social_network_data.xml`

Contient des instances conformes au modèle:
- 3 personnes (Jean, Marie, Pierre)
- 2 entreprises (TechCorp, DataSolutions)
- 3 compétences (Java, Python, Neo4j)
- 9 liens entre ces entités

## Configuration Maven

### Plugin JAXB

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>jaxb2-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>generate-datapack</id>
            ...
        </execution>
        <execution>
            <id>generate-model</id>
            ...
        </execution>
    </executions>
</plugin>
```

### Dépendances

- **jakarta.xml.bind-api** 3.0.1: API JAXB pour Java 11+
- **jaxb-impl** 3.0.2: Implémentation JAXB

## Utilisation

### 1. Définir un Modèle

Créer un fichier XML basé sur `model.xsd`:

```xml
<DATA_MODEL NAME="MonModele" VERSION="1.0">
    <OBJECT_TYPES>
        <OBJECT_TYPE NAME="PERSONNE">
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true"/>
                <ATTRIBUTE_DEFINITION NAME="AGE" TYPE="INTEGER" REQUIRED="false"/>
            </ATTRIBUTE_DEFINITIONS>
        </OBJECT_TYPE>
        <OBJECT_TYPE NAME="EMPLOYE" PARENT="PERSONNE">
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="MATRICULE" TYPE="STRING" REQUIRED="true"/>
            </ATTRIBUTE_DEFINITIONS>
        </OBJECT_TYPE>
    </OBJECT_TYPES>
    <LINK_TYPES>
        <LINK_TYPE NAME="CONNAIT" DIRECTED="false">
            <SOURCE_TYPES>
                <TYPE_REF NAME="PERSONNE"/>
            </SOURCE_TYPES>
            <TARGET_TYPES>
                <TYPE_REF NAME="PERSONNE"/>
            </TARGET_TYPES>
        </LINK_TYPE>
    </LINK_TYPES>
</DATA_MODEL>
```

### 2. Créer des Données

Créer un fichier XML basé sur `data.xsd`:

```xml
<DATAS>
    <OBJECTS>
        <OBJECT TYPE="PERSONNE" ID="1">
            <ATTRIBUTE KEY="NOM" VALUE="Dupont"/>
            <ATTRIBUTE KEY="AGE" VALUE="35"/>
        </OBJECT>
    </OBJECTS>
    <LINKS>
        <LINK TYPE="CONNAIT">
            <OBJ_LINK_A ID="1" TYPE="PERSONNE"/>
            <OBJ_LINK_B ID="2" TYPE="PERSONNE"/>
        </LINK>
    </LINKS>
</DATAS>
```

### 3. Validation et Import

L'API d'import pourra:
1. Charger le modèle de données
2. Valider les données contre le modèle
3. Importer dans Neo4j si valide

## Prochaines Étapes

1. **API de gestion du modèle**
   - Lecture et chargement des modèles
   - Cache des modèles

2. **Validation des données**
   - Vérifier que les types d'objets existent
   - Vérifier que les types de liens sont valides
   - Valider les attributs (types, obligatoires, etc.)

3. **Stockage Neo4j**
   - Stocker le modèle dans Neo4j
   - Créer des contraintes basées sur le modèle
   - Enrichir l'import avec les métadonnées du modèle

4. **Tests**
   - Tests unitaires pour la validation
   - Tests d'intégration avec Neo4j
   - Tests de conformité modèle/données

## État Actuel

✅ **Complété**:
- Schémas XSD créés (model.xsd, data.xsd mis à jour)
- Génération automatique des DTOs via JAXB 3.x
- Exemples de modèle et données
- Migration vers Jakarta JAXB (Java 11+)
- Compilation du code principal réussie

⚠️ **En cours/À faire**:
- Finaliser les classes wrapper pour compatibilité totale
- Mettre à jour les tests unitaires
- Créer l'API de gestion du modèle
- Implémenter la validation
- Documenter l'API

## Notes Techniques

### Migration JAXB 2.x → 3.x

- Namespace: `javax.xml.bind` → `jakarta.xml.bind`
- Package Maven: `javax.xml.bind:jaxb-api` → `jakarta.xml.bind:jakarta.xml.bind-api`
- Les noms de classes générées suivent les noms XML (en majuscules)
- Classes wrapper créées pour la compatibilité avec le code existant

### Conventions de Nommage

- XSD: Noms en MAJUSCULES (OBJECT, ATTRIBUTE, LINK)
- Java généré: Classes avec noms en MAJUSCULES (OBJECT.java, ATTRIBUTE.java)
- Wrapper: Classes avec noms descriptifs (DataPackObject, DataPackAttribute)
