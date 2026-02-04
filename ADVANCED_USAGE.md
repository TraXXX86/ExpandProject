# Guide d'Utilisation Avancée - ExpandProject

## 📚 Table des Matières

1. [Architecture du Modèle](#architecture-du-modèle)
2. [API Java](#api-java)
3. [Validation Avancée](#validation-avancée)
4. [Intégration Neo4j](#intégration-neo4j)
5. [Personnalisation](#personnalisation)
6. [Requêtes Cypher Avancées](#requêtes-cypher-avancées)

## Architecture du Modèle

### Structure Hiérarchique

```
DATA_MODEL
├── OBJECT_TYPES (1)
│   └── OBJECT_TYPE (1..n)
│       ├── NAME (required)
│       ├── DESCRIPTION (optional)
│       └── ATTRIBUTE_DEFINITIONS (optional)
│           └── ATTRIBUTE_DEFINITION (1..n)
│               ├── NAME (required)
│               ├── TYPE (STRING|INTEGER|DOUBLE|BOOLEAN|DATE)
│               ├── REQUIRED (boolean)
│               ├── DEFAULT_VALUE (optional)
│               └── DESCRIPTION (optional)
└── LINK_TYPES (1)
    └── LINK_TYPE (0..n)
        ├── NAME (required)
        ├── DIRECTED (boolean, default: true)
        ├── DESCRIPTION (optional)
        ├── SOURCE_TYPES (required)
        │   └── TYPE_REF (1..n)
        ├── TARGET_TYPES (required)
        │   └── TYPE_REF (1..n)
        └── ATTRIBUTE_DEFINITIONS (optional)
```

### Bonnes Pratiques de Modélisation

#### 1. Nommage des Types

```xml
<!-- ✅ BON -->
<OBJECT_TYPE NAME="PERSONNE">
<OBJECT_TYPE NAME="COMMANDE_CLIENT">
<LINK_TYPE NAME="APPARTIENT_A">

<!-- ❌ MAUVAIS -->
<OBJECT_TYPE NAME="Personne">  <!-- Pas de minuscules -->
<OBJECT_TYPE NAME="object 1">   <!-- Pas d'espaces -->
```

#### 2. Attributs Obligatoires

Définissez les attributs vraiment nécessaires comme requis :

```xml
<ATTRIBUTE_DEFINITION NAME="ID_UNIQUE" TYPE="STRING" REQUIRED="true"/>
<ATTRIBUTE_DEFINITION NAME="COMMENTAIRE" TYPE="STRING" REQUIRED="false"/>
```

#### 3. Valeurs par Défaut

Utilisez des valeurs par défaut pour simplifier la saisie :

```xml
<ATTRIBUTE_DEFINITION NAME="STATUT" TYPE="STRING" 
    REQUIRED="false" DEFAULT_VALUE="ACTIF">
    <DESCRIPTION>Statut de l'entité</DESCRIPTION>
</ATTRIBUTE_DEFINITION>
```

#### 4. Contraintes de Liens

Limitez les types d'objets autorisés :

```xml
<LINK_TYPE NAME="MANAGE">
    <SOURCE_TYPES>
        <!-- Seuls les managers peuvent "manager" -->
        <TYPE_REF NAME="MANAGER"/>
    </SOURCE_TYPES>
    <TARGET_TYPES>
        <!-- Ils peuvent manager des employés ou projets -->
        <TYPE_REF NAME="EMPLOYE"/>
        <TYPE_REF NAME="PROJET"/>
    </TARGET_TYPES>
</LINK_TYPE>
```

## API Java

### Utilisation Programmatique

#### Charger un Modèle

```java
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.DATAMODEL;

// Via fichier
ModelManager manager = ModelManager.getInstance();
DATAMODEL model = manager.loadModel(new File("model.xml"));

// Via ressource
model = manager.loadModelFromResource("model/my_model.xml");

// Obtenir le modèle courant
DATAMODEL current = manager.getCurrentModel();
```

#### Valider des Données

```java
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;
import fr.expand.project.importdata.dto.generated.DATAS;

DataValidator validator = new DataValidator();
DATAS data = loadDataFromFile("data.xml");

ValidationResult result = validator.validate(data);

if (result.isValid()) {
    System.out.println("✅ Données valides");
} else {
    System.out.println("❌ Erreurs de validation:");
    for (ValidationError error : result.getErrors()) {
        System.out.println("  - " + error);
    }
}

// Afficher le rapport complet
System.out.println(result.getReport());
```

#### Import avec Validation

```java
import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;

ModelBasedImportAPI api = new ModelBasedImportAPI();

// Validation seule
ValidationResult result = api.importData(
    new File("data.xml"), 
    true  // validateOnly = true
);

// Import complet
result = api.importData(
    new File("data.xml"), 
    false  // validateOnly = false
);
```

#### Requêter le Modèle

```java
ModelManager manager = ModelManager.getInstance();

// Vérifier qu'un type existe
if (manager.objectTypeExists("PERSONNE")) {
    OBJECTTYPE personType = manager.getObjectType("PERSONNE");
    System.out.println("Type trouvé: " + personType.getNAME());
}

// Obtenir un type de lien
LINKTYPE linkType = manager.getLinkType("CONNAIT");
if (linkType != null) {
    System.out.println("Lien orienté: " + linkType.isDIRECTED());
}
```

## Validation Avancée

### Types de Validation

1. **Validation de Structure**
   - Vérification XSD automatique
   - Éléments obligatoires présents

2. **Validation de Modèle**
   - Types d'objets existent dans le modèle
   - Types de liens existent dans le modèle

3. **Validation de Contraintes**
   - Attributs obligatoires présents
   - Types de données corrects
   - Contraintes de liens (source/target types)

4. **Validation de Référence**
   - IDs d'objets uniques
   - Objets référencés dans les liens existent

### Exemple de Rapport de Validation

```
Validation Result:
==================
Status: INVALID
Errors: 3
Warnings: 1

ERRORS:
-------
  [OBJECT[5]] Missing required attribute: NOM
  [OBJECT[7]] Unknown object type: VOITURE
  [LINK[CONNAIT]] Target type 'ENTREPRISE' not allowed for this link type

WARNINGS:
---------
  [OBJECT[3]] Attribute 'SURNOM' not defined in model
```

## Intégration Neo4j

### Configuration Avancée

Modifier `CypherConnector.java` pour personnaliser la connexion :

```java
@Override
protected void connectToDb() {
    // Configuration personnalisée
    driver = GraphDatabase.driver(
        "bolt://your-server:7687",
        AuthTokens.basic("username", "password"),
        Config.builder()
            .withMaxConnectionPoolSize(50)
            .withConnectionTimeout(10, TimeUnit.SECONDS)
            .build()
    );
    session = driver.session();
}
```

### Requêtes Cypher Personnalisées

L'application génère des requêtes Cypher comme :

```cypher
// Création d'objet
CREATE (a:PERSONNE {NOM:'Dupont', AGE:'35'}) RETURN ID(a)

// Création de lien
MATCH (a:PERSONNE) WHERE ID(a)=1
MATCH (b:ENTREPRISE) WHERE ID(b)=10
CREATE (a)-[:TRAVAILLE_POUR {POSTE:'Dev'}]->(b)
```

### Stratégies d'Import

#### Import Incrémental

Pour ajouter des données sans supprimer l'existant :

```java
// Ne PAS appeler deleteAll()
connector.writeObject(newObject);
```

#### Import Complet (Remplacement)

Pour remplacer toutes les données :

```java
connector.deleteAll();  // Supprime tout
// Puis importer les nouvelles données
```

## Personnalisation

### Créer un Connecteur Personnalisé

```java
public class MonConnecteur extends IConnectorDb {
    
    @Override
    protected void connectToDb() {
        // Votre logique de connexion
    }
    
    @Override
    protected void closeConnection() {
        // Votre logique de fermeture
    }
    
    @Override
    public int writeObject(DataPackObject object) {
        // Votre logique d'écriture
        return objectId;
    }
    
    @Override
    public int writeLink(DataPackObject objA, DataPackObject objB, boolean isOriented) {
        // Votre logique de création de lien
        return linkId;
    }
    
    // ... autres méthodes abstraites
}

// Utilisation
ModelBasedImportAPI api = new ModelBasedImportAPI();
api.setConnector(new MonConnecteur());
```

### Ajouter des Types de Validation Personnalisés

Étendre `DataValidator` :

```java
public class CustomValidator extends DataValidator {
    
    @Override
    public ValidationResult validate(DATAS data) {
        // Validation standard
        ValidationResult result = super.validate(data);
        
        // Validations personnalisées
        validateBusinessRules(data);
        
        return result;
    }
    
    private void validateBusinessRules(DATAS data) {
        // Vos règles métier spécifiques
    }
}
```

## Requêtes Cypher Avancées

### Analyse de Graphe

```cypher
// Trouver les chemins les plus courts
MATCH path = shortestPath(
  (p1:PERSONNE {NOM: 'Dupont'})-[*]-(p2:PERSONNE {NOM: 'Martin'})
)
RETURN path

// Compter les degrés de séparation
MATCH (p1:PERSONNE {NOM: 'Dupont'})-[r*1..3]-(p2:PERSONNE)
RETURN p2.NOM, length(r) as Degres
ORDER BY Degres

// Trouver les hubs (personnes les plus connectées)
MATCH (p:PERSONNE)-[r]-()
RETURN p.NOM, count(r) as NbConnexions
ORDER BY NbConnexions DESC
LIMIT 10
```

### Requêtes Métier

```cypher
// Trouver les collègues de collègues
MATCH (p1:PERSONNE)-[:TRAVAILLE_POUR]->(e:ENTREPRISE)<-[:TRAVAILLE_POUR]-(p2:PERSONNE)
WHERE p1.NOM = 'Dupont' AND p1 <> p2
RETURN DISTINCT p2.NOM, e.NOM

// Compétences communes entre collègues
MATCH (p1:PERSONNE)-[:TRAVAILLE_POUR]->(e)<-[:TRAVAILLE_POUR]-(p2:PERSONNE),
      (p1)-[:POSSEDE_COMPETENCE]->(c:COMPETENCE)<-[:POSSEDE_COMPETENCE]-(p2)
WHERE p1 <> p2
RETURN p1.NOM, p2.NOM, collect(c.NOM) as CompetencesCommunes

// Recommandations de personnes à connaître
MATCH (p1:PERSONNE {NOM: 'Dupont'})-[:CONNAIT]-(ami)-[:CONNAIT]-(suggestion:PERSONNE)
WHERE NOT (p1)-[:CONNAIT]-(suggestion) AND p1 <> suggestion
RETURN DISTINCT suggestion.NOM, count(*) as AmisCommunus
ORDER BY AmisCommunus DESC
```

### Export de Données

```cypher
// Export en JSON
CALL apoc.export.json.all("export.json", {})

// Export CSV des personnes
MATCH (p:PERSONNE)
RETURN p.NOM, p.PRENOM, p.AGE, p.EMAIL

// Export des relations
MATCH (a)-[r:TRAVAILLE_POUR]->(b)
RETURN a.NOM as Employe, type(r) as Relation, 
       r.POSTE as Poste, b.NOM as Entreprise
```

## Performance et Optimisation

### Index Neo4j

Créez des index pour améliorer les performances :

```cypher
// Index sur les IDs
CREATE INDEX object_id IF NOT EXISTS FOR (n:OBJECT) ON (n.ID)

// Index sur les attributs fréquemment recherchés
CREATE INDEX personne_nom IF NOT EXISTS FOR (p:PERSONNE) ON (p.NOM)
CREATE INDEX personne_email IF NOT EXISTS FOR (p:PERSONNE) ON (p.EMAIL)

// Contraintes d'unicité
CREATE CONSTRAINT unique_personne_email IF NOT EXISTS 
FOR (p:PERSONNE) REQUIRE p.EMAIL IS UNIQUE
```

### Batch Import

Pour de gros volumes de données, utilisez le mode batch :

```java
// À implémenter : importer par lots de N objets
private void batchImport(DATAS data, int batchSize) {
    List<OBJECT> objects = data.getOBJECTS().getOBJECT();
    for (int i = 0; i < objects.size(); i += batchSize) {
        List<OBJECT> batch = objects.subList(
            i, 
            Math.min(i + batchSize, objects.size())
        );
        importBatch(batch);
    }
}
```

## Cas d'Usage Avancés

### Modèle Multi-Domaine

Combinez plusieurs domaines dans un seul modèle :

```xml
<DATA_MODEL NAME="EnterpriseModel" VERSION="2.0">
    <OBJECT_TYPES>
        <!-- Domaine RH -->
        <OBJECT_TYPE NAME="EMPLOYE"/>
        <OBJECT_TYPE NAME="DEPARTEMENT"/>
        
        <!-- Domaine IT -->
        <OBJECT_TYPE NAME="SERVEUR"/>
        <OBJECT_TYPE NAME="APPLICATION"/>
        
        <!-- Domaine Projet -->
        <OBJECT_TYPE NAME="PROJET"/>
        <OBJECT_TYPE NAME="TACHE"/>
    </OBJECT_TYPES>
    <LINK_TYPES>
        <!-- Liens RH -->
        <LINK_TYPE NAME="TRAVAILLE_DANS">...</LINK_TYPE>
        
        <!-- Liens IT -->
        <LINK_TYPE NAME="HEBERGE">...</LINK_TYPE>
        
        <!-- Liens transverses -->
        <LINK_TYPE NAME="RESPONSABLE_DE">
            <SOURCE_TYPES>
                <TYPE_REF NAME="EMPLOYE"/>
            </SOURCE_TYPES>
            <TARGET_TYPES>
                <TYPE_REF NAME="PROJET"/>
                <TYPE_REF NAME="APPLICATION"/>
            </TARGET_TYPES>
        </LINK_TYPE>
    </LINK_TYPES>
</DATA_MODEL>
```

### Hiérarchies et Taxonomies

```xml
<!-- Modèle pour une taxonomie de produits -->
<LINK_TYPE NAME="SOUS_CATEGORIE_DE" DIRECTED="true">
    <SOURCE_TYPES><TYPE_REF NAME="CATEGORIE"/></SOURCE_TYPES>
    <TARGET_TYPES><TYPE_REF NAME="CATEGORIE"/></TARGET_TYPES>
</LINK_TYPE>

<LINK_TYPE NAME="APPARTIENT_A_CATEGORIE" DIRECTED="true">
    <SOURCE_TYPES><TYPE_REF NAME="PRODUIT"/></SOURCE_TYPES>
    <TARGET_TYPES><TYPE_REF NAME="CATEGORIE"/></TARGET_TYPES>
</LINK_TYPE>
```

Requête pour voir la hiérarchie complète :

```cypher
MATCH path = (c1:CATEGORIE)-[:SOUS_CATEGORIE_DE*]->(c2:CATEGORIE)
WHERE NOT (c2)-[:SOUS_CATEGORIE_DE]->()
RETURN path
```

### Graphe Temporel

Modélisez l'évolution dans le temps :

```xml
<OBJECT_TYPE NAME="VERSION_DOCUMENT">
    <ATTRIBUTE_DEFINITIONS>
        <ATTRIBUTE_DEFINITION NAME="CONTENU" TYPE="STRING" REQUIRED="true"/>
        <ATTRIBUTE_DEFINITION NAME="DATE" TYPE="DATE" REQUIRED="true"/>
        <ATTRIBUTE_DEFINITION NAME="VERSION" TYPE="INTEGER" REQUIRED="true"/>
    </ATTRIBUTE_DEFINITIONS>
</OBJECT_TYPE>

<LINK_TYPE NAME="VERSION_PRECEDENTE" DIRECTED="true">
    <SOURCE_TYPES><TYPE_REF NAME="VERSION_DOCUMENT"/></SOURCE_TYPES>
    <TARGET_TYPES><TYPE_REF NAME="VERSION_DOCUMENT"/></TARGET_TYPES>
</LINK_TYPE>
```

## Débogage

### Activer les Logs Détaillés

Modifiez `log4j2.xml` :

```xml
<Logger name="fr.expand.project" level="debug" additivity="false">
    <AppenderRef ref="Console"/>
    <AppenderRef ref="FileLogger"/>
</Logger>

<Logger name="org.neo4j" level="debug" additivity="false">
    <AppenderRef ref="Console"/>
</Logger>
```

### Mode Debug Maven

```bash
mvn exec:java -Dexec.mainClass="..." -Dexec.args="..." -X
```

### Validation Étape par Étape

```java
DataValidator validator = new DataValidator();

// Valider seulement les objets
ValidationResult objResult = validator.validateObjects(data.getOBJECTS());

// Valider seulement les liens
ValidationResult linkResult = validator.validateLinks(data.getLINKS());
```

## Extensibilité

### Ajouter un Nouveau Type de Données

1. Modifiez `model.xsd` :

```xml
<xs:simpleType name="ATTRIBUTE_TYPE">
    <xs:restriction base="xs:string">
        <xs:enumeration value="STRING"/>
        <xs:enumeration value="INTEGER"/>
        <xs:enumeration value="DOUBLE"/>
        <xs:enumeration value="BOOLEAN"/>
        <xs:enumeration value="DATE"/>
        <xs:enumeration value="URL"/>  <!-- Nouveau type -->
    </xs:restriction>
</xs:simpleType>
```

2. Régénérez les DTOs :

```bash
mvn generate-sources
```

3. Mettez à jour le validateur dans `DataValidator.java` :

```java
private boolean validateValueType(String value, ATTRIBUTETYPE type) {
    switch (type) {
        // ... types existants ...
        case URL:
            return value.matches("https?://.*");
        default:
            return true;
    }
}
```

### Plugin d'Export Personnalisé

Créez votre propre format d'export :

```java
public interface DataExporter {
    void export(DATAS data, File outputFile) throws Exception;
}

public class CSVExporter implements DataExporter {
    @Override
    public void export(DATAS data, File outputFile) {
        // Votre logique d'export CSV
    }
}
```

## Monitoring et Métriques

### Statistiques d'Import

```java
public class ImportStats {
    private int objectsImported;
    private int linksImported;
    private long duration;
    
    // Ajoutez des hooks dans ModelBasedImportAPI
}
```

### Health Checks

```java
public boolean checkNeo4jConnection() {
    try {
        IConnectorDb connector = new CypherConnector();
        // Test simple query
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

## Bonnes Pratiques

1. **Toujours valider avant d'importer** (`--validate-only` d'abord)
2. **Versionnez vos modèles** (attribut VERSION)
3. **Documentez vos types** (utilisez DESCRIPTION)
4. **Testez sur des petits jeux de données** d'abord
5. **Sauvegardez Neo4j** avant les gros imports
6. **Utilisez des transactions** pour les imports critiques
7. **Créez des index** sur les attributs recherchés

## Ressources

- [Documentation complète](README.md)
- [Documentation technique](SYSTEM_MODEL_DOCUMENTATION.md)
- [Guide de démarrage rapide](QUICKSTART.md)
- [Neo4j Cypher Manual](https://neo4j.com/docs/cypher-manual/current/)
- [JAXB Tutorial](https://www.baeldung.com/jaxb)
