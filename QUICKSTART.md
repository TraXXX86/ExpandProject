# 🚀 Guide de Démarrage Rapide - ExpandProject

Ce guide vous permettra de démarrer avec ExpandProject en 5 minutes.

## ⚡ Démarrage Ultra-Rapide (avec Docker)

Si vous avez Docker installé, utilisez cette méthode :

```bash
# 1. Démarrer Neo4j avec Docker
docker run -d \
  --name neo4j-expand \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/expand123456 \
  neo4j:5.17.0

# 2. Compiler le projet
mvn clean install -DskipTests

# 3. Tester avec les données d'exemple
cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" -Dexec.args="--example"

# 4. Importer les données d'exemple dans Neo4j
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="src/main/resources/model/example_social_network_model.xml src/main/resources/datapack/example_social_network_data.xml"

# 5. Visualiser dans Neo4j Browser
# Ouvrez http://localhost:7474 (login: neo4j/expand123456)
# Exécutez: MATCH (n) RETURN n
```

## 📋 Étapes Détaillées

### 1️⃣ Installer les Prérequis

**Java 17+**
```bash
# Vérifier la version
java -version

# Si besoin d'installer (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install openjdk-17-jdk

# macOS
brew install openjdk@17
```

**Maven**
```bash
# Vérifier la version
mvn -version

# Si besoin d'installer (Ubuntu/Debian)
sudo apt-get install maven

# macOS
brew install maven
```

**Neo4j**

Option A - Docker (Recommandé):
```bash
docker run -d --name neo4j-expand \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/expand123456 \
  neo4j:5.17.0
```

Option B - Installation locale:
```bash
# Ubuntu/Debian
wget -O - https://debian.neo4j.com/neotechnology.gpg.key | sudo apt-key add -
echo 'deb https://debian.neo4j.com stable latest' | sudo tee /etc/apt/sources.list.d/neo4j.list
sudo apt-get update
sudo apt-get install neo4j
sudo systemctl start neo4j

# macOS
brew install neo4j
neo4j start
```

### 2️⃣ Compiler le Projet

```bash
cd ExpandProject
mvn clean install
```

### 3️⃣ Premiers Tests

#### Test de validation (sans import)

```bash
cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" -Dexec.args="--example"
```

Vous devriez voir :
```
========================================
  ExpandProject - Data Import Tool
========================================

Running EXAMPLE mode with bundled files
...
========================================
  EXAMPLE VALIDATION SUCCESS
  The bundled example data is valid!
========================================
```

#### Import dans Neo4j

```bash
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="src/main/resources/model/example_social_network_model.xml src/main/resources/datapack/example_social_network_data.xml"
```

### 4️⃣ Visualiser les Résultats

Ouvrez Neo4j Browser : **http://localhost:7474**

Connectez-vous :
- **Utilisateur**: neo4j
- **Mot de passe**: expand

Exécutez cette requête Cypher :
```cypher
MATCH (n) RETURN n LIMIT 50
```

Ou pour voir les relations :
```cypher
MATCH (p:PERSONNE)-[r]->(x) 
RETURN p, r, x
```

### 5️⃣ Créer Votre Premier Modèle

#### Créez `mon_modele.xml` :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<DATA_MODEL NAME="MonModele" VERSION="1.0">
    <OBJECT_TYPES>
        <OBJECT_TYPE NAME="LIVRE">
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="TITRE" TYPE="STRING" REQUIRED="true"/>
                <ATTRIBUTE_DEFINITION NAME="AUTEUR" TYPE="STRING" REQUIRED="true"/>
                <ATTRIBUTE_DEFINITION NAME="ANNEE" TYPE="INTEGER" REQUIRED="false"/>
            </ATTRIBUTE_DEFINITIONS>
        </OBJECT_TYPE>
        <OBJECT_TYPE NAME="LECTEUR">
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true"/>
            </ATTRIBUTE_DEFINITIONS>
        </OBJECT_TYPE>
    </OBJECT_TYPES>
    <LINK_TYPES>
        <LINK_TYPE NAME="A_LU" DIRECTED="true">
            <SOURCE_TYPES><TYPE_REF NAME="LECTEUR"/></SOURCE_TYPES>
            <TARGET_TYPES><TYPE_REF NAME="LIVRE"/></TARGET_TYPES>
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="NOTE" TYPE="INTEGER"/>
            </ATTRIBUTE_DEFINITIONS>
        </LINK_TYPE>
    </LINK_TYPES>
</DATA_MODEL>
```

#### Créez `mes_donnees.xml` :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<DATAS>
    <OBJECTS>
        <OBJECT TYPE="LIVRE" ID="1">
            <ATTRIBUTE KEY="TITRE" VALUE="1984"/>
            <ATTRIBUTE KEY="AUTEUR" VALUE="George Orwell"/>
            <ATTRIBUTE KEY="ANNEE" VALUE="1949"/>
        </OBJECT>
        <OBJECT TYPE="LECTEUR" ID="10">
            <ATTRIBUTE KEY="NOM" VALUE="Alice"/>
        </OBJECT>
    </OBJECTS>
    <LINKS>
        <LINK TYPE="A_LU">
            <ATTRIBUTE KEY="NOTE" VALUE="5"/>
            <OBJ_LINK_A ID="10" TYPE="LECTEUR"/>
            <OBJ_LINK_B ID="1" TYPE="LIVRE"/>
        </LINK>
    </LINKS>
</DATAS>
```

#### Testez :

```bash
# Validation seule
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="mon_modele.xml mes_donnees.xml --validate-only"

# Import complet
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="mon_modele.xml mes_donnees.xml"
```

## 🎓 Requêtes Cypher Utiles

```cypher
// Compter les noeuds par type
MATCH (n) 
RETURN labels(n) as Type, count(*) as Count

// Compter les relations par type
MATCH ()-[r]->() 
RETURN type(r) as Type, count(*) as Count

// Trouver les personnes qui travaillent dans la même entreprise
MATCH (p1:PERSONNE)-[:TRAVAILLE_POUR]->(e:ENTREPRISE)<-[:TRAVAILLE_POUR]-(p2:PERSONNE)
WHERE p1 <> p2
RETURN p1.NOM, e.NOM, p2.NOM

// Supprimer toutes les données
MATCH (n) DETACH DELETE n
```

## ❓ FAQ

**Q: Comment modifier les identifiants de connexion Neo4j ?**

R: Éditez `importdata/src/main/java/fr/expand/project/importdata/dao/connectors/impl/CypherConnector.java` ligne 35.

**Q: Comment voir les logs détaillés ?**

R: Les logs sont dans `logs/expandproject.log`. Pour changer le niveau de log, éditez `importdata/src/main/resources/log4j2.xml`.

**Q: L'application dit "No model loaded" ?**

R: Assurez-vous de fournir le fichier de modèle en premier argument avant le fichier de données.

**Q: Comment exécuter sans Maven ?**

R: Utilisez le JAR exécutable créé:
```bash
cd importdata/target
java -jar expandproject-importdata.jar --example
```

## 🔄 Cycle de Développement Typique

1. **Définir le modèle** → Créer `model.xml`
2. **Créer les données** → Créer `data.xml`
3. **Valider** → `--validate-only`
4. **Corriger les erreurs** si nécessaire
5. **Importer** → Sans `--validate-only`
6. **Visualiser** → Neo4j Browser
7. **Requêter** → Cypher queries

## 📞 Besoin d'Aide ?

Consultez le [README complet](README.md) ou ouvrez une [issue](https://github.com/TraXXX86/ExpandProject/issues).

Bon développement ! 🎉
