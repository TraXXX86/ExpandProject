# ExpandProject - Data Import Tool for Neo4j

ExpandProject est un outil d'import de données dans Neo4j avec support de modèles de données définis en XML et validation automatique.

## 🎯 Fonctionnalités

- **Modèles de données XML** : Définissez vos types d'objets et de liens
- **Validation automatique** : Validez vos données avant l'import
- **Import Neo4j** : Import direct dans une base Neo4j
- **Types de données** : Support STRING, INTEGER, DOUBLE, BOOLEAN, DATE
- **Attributs optionnels/obligatoires** : Contraintes de validation
- **Liens orientés/non-orientés** : Flexibilité dans la modélisation

## 📋 Prérequis

- **Java 17+** (OpenJDK 17 ou supérieur)
- **Maven 3.8+** (pour la compilation)
- **Neo4j** (pour l'import de données)
  - Version recommandée: 5.x
  - Mode serveur accessible via Bolt (bolt://localhost:7687)

## 🚀 Installation

### 1. Cloner le projet

```bash
git clone https://github.com/TraXXX86/ExpandProject.git
cd ExpandProject
```

### 2. Compiler le projet

```bash
mvn clean install
```

Cette commande va :
- Compiler tous les modules (commons, importdata, model)
- Générer les DTOs à partir des schémas XSD
- Créer les JARs dans les dossiers `target/`

## 🖥️ IHM Vue + Vuetify

Une interface locale Vue 3 + Vuetify est disponible dans le dossier `ui/`.

```bash
cd ui
npm install
npm run dev
```

L'interface consomme l'API d'import pour charger les modèles et données dans Neo4j, puis afficher uniquement ce qui est stocké en base.

Pour démarrer l'API :

```bash
mvn -pl importdata -am package
java -jar importdata/target/expandproject-importdata.jar --api 8080
```

Par défaut l'IHM cible `http://localhost:8080`. Vous pouvez surcharger via `VITE_API_BASE`.

## 🐳 Docker Compose

Pour lancer Neo4j + API + IHM sans installer Java localement :

```bash
cp .env.example .env
# adaptez les mots de passe si besoin
docker compose up --build
```

Accès :
- IHM : http://localhost:5173
- API : http://localhost:8080
- Neo4j : http://localhost:7474 (bolt 7687)

Les identifiants Neo4j et le mot de passe de bootstrap administrateur sont lus depuis `.env`
(voir `.env.example` pour les valeurs locales de départ).

### Vérifier rapidement l'import du modèle

```bash
./scripts/smoke-api.sh
```

Vous pouvez surcharger l'URL de l'API et le fichier modèle :

```bash
API_BASE=http://localhost:8080 MODEL_FILE=chemin/vers/model.xml ./scripts/smoke-api.sh
```

### 3. Configurer Neo4j

#### Installation de Neo4j (si nécessaire)

**Sur Ubuntu/Debian:**
```bash
wget -O - https://debian.neo4j.com/neotechnology.gpg.key | sudo apt-key add -
echo 'deb https://debian.neo4j.com stable latest' | sudo tee /etc/apt/sources.list.d/neo4j.list
sudo apt-get update
sudo apt-get install neo4j
```

**Sur macOS:**
```bash
brew install neo4j
```

**Avec Docker:**
```bash
docker run -d \
  --name neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/<mot-de-passe-local> \
  neo4j:5.17.0
```

#### Démarrer Neo4j

```bash
# Avec systemd (Linux)
sudo systemctl start neo4j

# Avec Homebrew (macOS)
neo4j start

# Vérifier le statut
neo4j status
```

#### Configuration

Par défaut, l'application cible :
- **URL**: `bolt://localhost:7687`
- **Utilisateur**: `neo4j`
- **Mot de passe**: fourni via l'environnement

Paramètres disponibles via variables d'environnement ou propriétés JVM :
- `NEO4J_BOLT_URI` (ex: `bolt://localhost:7687`)
- `NEO4J_HTTP_URI` (ex: `jdbc:neo4j:http://localhost:7474`)
- `NEO4J_USER` / `NEO4J_PASSWORD`
- `NEO4J_AUTH` (format `utilisateur/motdepasse`, ou `none`)
- `ACCESS_BOOTSTRAP_MODE` (`development` ou `production`)
- `ACCESS_BOOTSTRAP_ADMIN_PASSWORD` (mot de passe initial du compte `admin`)

## 📖 Utilisation

### Mode 1 : Exemple avec données pré-fournies

Testez rapidement avec les données d'exemple incluses :

```bash
cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" -Dexec.args="--example"
```

Ceci va :
1. Charger le modèle "Réseau Social" (PERSONNE, ENTREPRISE, COMPETENCE)
2. Valider les données d'exemple
3. Afficher le résultat de validation

### Mode 2 : Import complet avec vos données

```bash
cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="src/main/resources/model/example_social_network_model.xml src/main/resources/datapack/example_social_network_data.xml"
```

Ceci va :
1. Charger votre modèle
2. Valider vos données
3. **Importer** dans Neo4j si la validation réussit

### Mode 3 : Validation seule (sans import)

```bash
cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="path/to/model.xml path/to/data.xml --validate-only"
```

### Mode 4 : Utilisation du JAR

```bash
# Compiler le JAR exécutable
cd importdata
mvn package

# Exécuter
java -jar target/importpackage-0.0.1-SNAPSHOT.jar --example
java -jar target/importpackage-0.0.1-SNAPSHOT.jar model.xml data.xml
java -jar target/importpackage-0.0.1-SNAPSHOT.jar model.xml data.xml --validate-only
```

## 📝 Structure des fichiers XML

### 1. Fichier de Modèle (model.xml)

Définit les types d'objets et de liens autorisés :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<DATA_MODEL NAME="MonModele" VERSION="1.0">
    <OBJECT_TYPES>
        <OBJECT_TYPE NAME="PERSONNE" ICON="person">
            <DESCRIPTION>Représente une personne</DESCRIPTION>
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true">
                    <DESCRIPTION>Nom de famille</DESCRIPTION>
                </ATTRIBUTE_DEFINITION>
                <ATTRIBUTE_DEFINITION NAME="AGE" TYPE="INTEGER" REQUIRED="false"/>
                <ATTRIBUTE_DEFINITION NAME="EMAIL" TYPE="STRING" REQUIRED="false"/>
            </ATTRIBUTE_DEFINITIONS>
        </OBJECT_TYPE>
        
        <OBJECT_TYPE NAME="ENTREPRISE" ICON="apartment">
            <DESCRIPTION>Représente une entreprise</DESCRIPTION>
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true"/>
                <ATTRIBUTE_DEFINITION NAME="SECTEUR" TYPE="STRING" REQUIRED="false"/>
            </ATTRIBUTE_DEFINITIONS>
        </OBJECT_TYPE>
    </OBJECT_TYPES>
    
    <LINK_TYPES>
        <LINK_TYPE NAME="TRAVAILLE_POUR" DIRECTED="true">
            <DESCRIPTION>Relation d'emploi</DESCRIPTION>
            <SOURCE_TYPES>
                <TYPE_REF NAME="PERSONNE"/>
            </SOURCE_TYPES>
            <TARGET_TYPES>
                <TYPE_REF NAME="ENTREPRISE"/>
            </TARGET_TYPES>
            <ATTRIBUTE_DEFINITIONS>
                <ATTRIBUTE_DEFINITION NAME="POSTE" TYPE="STRING" REQUIRED="false"/>
                <ATTRIBUTE_DEFINITION NAME="DATE_DEBUT" TYPE="DATE" REQUIRED="false"/>
            </ATTRIBUTE_DEFINITIONS>
        </LINK_TYPE>
        
        <LINK_TYPE NAME="CONNAIT" DIRECTED="false">
            <DESCRIPTION>Relation de connaissance</DESCRIPTION>
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

### 2. Fichier de Données (data.xml)

Contient les données à importer :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<DATAS>
    <OBJECTS>
        <OBJECT TYPE="PERSONNE" ID="1">
            <ATTRIBUTE KEY="NOM" VALUE="Dupont"/>
            <ATTRIBUTE KEY="AGE" VALUE="35"/>
            <ATTRIBUTE KEY="EMAIL" VALUE="jean.dupont@email.com"/>
        </OBJECT>
        
        <OBJECT TYPE="PERSONNE" ID="2">
            <ATTRIBUTE KEY="NOM" VALUE="Martin"/>
            <ATTRIBUTE KEY="AGE" VALUE="28"/>
        </OBJECT>
        
        <OBJECT TYPE="ENTREPRISE" ID="10">
            <ATTRIBUTE KEY="NOM" VALUE="TechCorp"/>
            <ATTRIBUTE KEY="SECTEUR" VALUE="Informatique"/>
        </OBJECT>
    </OBJECTS>
    
    <LINKS>
        <LINK TYPE="TRAVAILLE_POUR">
            <ATTRIBUTE KEY="POSTE" VALUE="Développeur"/>
            <ATTRIBUTE KEY="DATE_DEBUT" VALUE="2020-01-15"/>
            <OBJ_LINK_A ID="1" TYPE="PERSONNE"/>
            <OBJ_LINK_B ID="10" TYPE="ENTREPRISE"/>
        </LINK>
        
        <LINK TYPE="CONNAIT">
            <OBJ_LINK_A ID="1" TYPE="PERSONNE"/>
            <OBJ_LINK_B ID="2" TYPE="PERSONNE"/>
        </LINK>
    </LINKS>
</DATAS>
```

## 🔍 Types de Données Supportés

| Type | Description | Exemple |
|------|-------------|---------|
| `STRING` | Chaîne de caractères | "Jean Dupont" |
| `INTEGER` | Nombre entier | 42 |
| `DOUBLE` | Nombre décimal | 3.14 |
| `BOOLEAN` | Booléen | true, false |
| `DATE` | Date au format ISO | 2020-01-15 |

## 🏗️ Architecture

```
ExpandProject/
├── commons/              # Classes communes
│   └── src/main/java/
│       └── fr/expand/project/commons/
│           ├── IConstantUtils.java
│           ├── ObjectTypeEnum.java
│           └── LinkTypeEnum.java
│
├── importdata/           # Module principal d'import
│   ├── src/main/java/
│   │   └── fr/expand/project/importdata/
│   │       ├── api/               # API d'import
│   │       ├── dao/               # Connecteurs DB
│   │       ├── dto/               # DTOs générés
│   │       ├── model/             # Gestion des modèles
│   │       ├── validation/        # Validation des données
│   │       ├── util/              # Utilitaires
│   │       └── Launcher.java      # Point d'entrée
│   │
│   └── src/main/resources/
│       ├── model/                 # Schémas et exemples de modèles
│       │   ├── model.xsd          # Schéma XSD du modèle
│       │   └── example_social_network_model.xml
│       └── datapack/              # Schémas et exemples de données
│           ├── data.xsd           # Schéma XSD des données
│           └── example_social_network_data.xml
│
├── model/                # Module modèle (vide pour l'instant)
└── pom.xml              # Configuration Maven parent
```

## 🧪 Tests

### Lancer les tests unitaires

```bash
mvn test
```

### Tester avec des données personnalisées

1. Créez votre modèle XML dans `importdata/src/main/resources/model/my_model.xml`
2. Créez vos données XML dans `importdata/src/main/resources/datapack/my_data.xml`
3. Lancez la validation :

```bash
cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="src/main/resources/model/my_model.xml src/main/resources/datapack/my_data.xml --validate-only"
```

## 📊 Visualiser les données dans Neo4j

Après l'import, visualisez vos données :

1. Ouvrez Neo4j Browser : http://localhost:7474
2. Connectez-vous avec les identifiants configurés via `NEO4J_AUTH`
3. Exécutez des requêtes Cypher :

```cypher
// Voir tous les noeuds
MATCH (n) RETURN n LIMIT 100

// Voir toutes les relations
MATCH (a)-[r]->(b) RETURN a, r, b LIMIT 100

// Rechercher une personne spécifique
MATCH (p:PERSONNE {NOM: "Dupont"}) RETURN p

// Voir toutes les personnes et leurs entreprises
MATCH (p:PERSONNE)-[r:TRAVAILLE_POUR]->(e:ENTREPRISE) 
RETURN p.NOM, r.POSTE, e.NOM
```

## 🔧 Dépannage

### Erreur : "No model loaded"

**Solution** : Assurez-vous de fournir un fichier de modèle valide en premier argument.

### Erreur : "Connection refused" lors de l'import

**Solution** : Vérifiez que Neo4j est démarré :
```bash
neo4j status
# Si arrêté :
sudo systemctl start neo4j  # Linux
neo4j start                 # macOS
```

### Erreur : Problème d'authentification Neo4j

**Solution** : Vérifiez la valeur de `NEO4J_AUTH` / `NEO4J_PASSWORD` ou réinitialisez Neo4j :
```bash
neo4j-admin set-initial-password <nouveau-mot-de-passe>
```

### Erreur de compilation : "package jakarta.xml.bind does not exist"

**Solution** : Assurez-vous d'utiliser Java 17+ et que Maven a bien téléchargé les dépendances :
```bash
mvn clean install -U
```

### Les classes générées (DATAS, OBJECT, etc.) n'existent pas

**Solution** : Lancez la génération JAXB :
```bash
cd importdata
mvn generate-sources
```

## 📚 Documentation Additionnelle

- [SYSTEM_MODEL_DOCUMENTATION.md](SYSTEM_MODEL_DOCUMENTATION.md) - Documentation technique du système de modèles
- [CHANGELOG_UPDATE.md](CHANGELOG_UPDATE.md) - Historique des mises à jour
- [Neo4j Documentation](https://neo4j.com/docs/) - Documentation officielle Neo4j

## 🤝 Contribution

Les contributions sont les bienvenues ! Pour contribuer :

1. Forkez le projet
2. Créez votre branche (`git checkout -b feature/AmazingFeature`)
3. Committez vos changements (`git commit -m 'Add some AmazingFeature'`)
4. Poussez vers la branche (`git push origin feature/AmazingFeature`)
5. Ouvrez une Pull Request

## 📄 Licence

Ce projet est sous licence MIT - voir le fichier LICENSE pour plus de détails.

## 👥 Auteurs

- **TraXXX86** - *Travail initial* - [TraXXX86](https://github.com/TraXXX86)

## 🔗 Liens Utiles

- [Documentation Neo4j](https://neo4j.com/docs/)
- [JAXB Tutorial](https://www.baeldung.com/jaxb)
- [Maven Guide](https://maven.apache.org/guides/)
- [Cypher Query Language](https://neo4j.com/docs/cypher-manual/current/)

## 📞 Support

Pour toute question ou problème :
- Ouvrez une [Issue](https://github.com/TraXXX86/ExpandProject/issues)
- Consultez la [documentation](https://www.gitbook.com/book/traxxx86/expandproject/welcome)
