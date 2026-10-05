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
- **Maven 3.9.9** via le wrapper `./mvnw` (Java 17 requis)
- **Node.js 24** (pour l'IHM)
- **Neo4j** (pour l'import de données)
  - Version de référence: 5.26.31
  - Mode serveur accessible via Bolt (bolt://localhost:7687)

## 🚀 Installation

### 1. Cloner le projet

```bash
git clone https://github.com/TraXXX86/ExpandProject.git
cd ExpandProject
```

### 2. Compiler le projet

```bash
./mvnw clean package
```

Cette commande va :
- Compiler les modules `commons` et `importdata`
- Générer les DTOs à partir des schémas XSD
- Créer les JARs dans les dossiers `target/`

## 🖥️ IHM Vue + Vuetify

Une interface locale Vue 3 + Vuetify est disponible dans le dossier `ui/`.

```bash
cd ui
npm ci
npm run dev
```

L'interface consomme l'API d'import pour charger les modèles et données dans Neo4j, puis afficher uniquement ce qui est stocké en base.

Pour démarrer l'API :

```bash
export NEO4J_BOLT_URI=bolt://localhost:7687
export NEO4J_AUTH='neo4j/<your-neo4j-password>'
export EXPAND_ADMIN_PASSWORD='<your-admin-password>'
export ACCESS_DB_PATH="$PWD/access.sqlite"
./mvnw -pl importdata -am package
java -jar importdata/target/expandproject-importdata.jar --api 8080
```

En développement, le proxy Vite relaie les appels `/api` vers `http://localhost:8080` (ou vers `API_PROXY_TARGET`). En Compose, nginx relaie `/api` vers l'API sur le réseau interne; le navigateur utilise donc la même origine pour l'interface et l'API.

## 🐳 Docker Compose

Pour lancer Neo4j + API + IHM sans installer Java localement :

```bash
cp .env.example .env
# Edit .env and set unique values for both required passwords.
docker compose up --build
```

Accès :
- IHM : http://localhost:5173
- API : http://localhost:8080
- Neo4j : http://localhost:7474 (bolt 7687)

Compose refuses to start until both `NEO4J_AUTH` and `EXPAND_ADMIN_PASSWORD` are set in `.env`. There are no default passwords. Neo4j data and the SQLite access database persist in separate Docker volumes. The published service ports bind to localhost.

The Dockerfiles can import a custom proxy CA from a BuildKit secret without baking it into an image. To use one, build both service images with the same secret file, then start Compose without `--build`:

```bash
export PROXY_CA_FILE=/path/to/proxy-ca.pem
docker build --secret id=proxy_ca,src="$PROXY_CA_FILE" -f importdata/Dockerfile -t expandproject-api:local .
docker build --secret id=proxy_ca,src="$PROXY_CA_FILE" -f ui/Dockerfile -t expandproject-ui:local .
docker compose up
```

If Maven needs a private mirror or credentials, pass its settings file only to the backend build with `--secret id=maven_settings,src="$MAVEN_SETTINGS_FILE"`.

### Vérifier rapidement l'import du modèle

```bash
EXPAND_ADMIN_PASSWORD='<your-admin-password>' ./scripts/smoke-api.sh
```

Vous pouvez surcharger l'URL de l'API et le fichier modèle :

```bash
EXPAND_ADMIN_PASSWORD='<your-admin-password>' API_BASE=http://localhost:8080 MODEL_FILE=chemin/vers/model.xml ./scripts/smoke-api.sh
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
  -p 127.0.0.1:7474:7474 -p 127.0.0.1:7687:7687 \
  -e NEO4J_AUTH='neo4j/<your-password>' \
  neo4j:5.26.31
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

The API requires an explicit Neo4j password and admin bootstrap password. Configure them through environment variables:

- `NEO4J_BOLT_URI` (ex: `bolt://localhost:7687`)
- `NEO4J_AUTH` (required, `username/password` format)
- `EXPAND_ADMIN_PASSWORD` (required when initializing an empty access database; creates the `admin` account)
- `ACCESS_DB_PATH` (SQLite database path; defaults to the user's `.expandproject/access.sqlite` location)
- `EXPAND_ALLOWED_ORIGINS` (comma-separated CORS allowlist; configure your UI origin explicitly for deployments)
- `EXPAND_COOKIE_SECURE` (`true` when serving the API over HTTPS; keep `false` for local HTTP development)

## Migration depuis une version précédente

Cette version change les mots de passe et sessions du portail, la pagination de l'API et les identifiants de démarrage. Prévoyez une interruption pendant la migration et gardez les anciennes versions de sauvegarde jusqu'à validation.

1. Arrêtez l'ancienne application et sauvegardez Neo4j avec `neo4j-admin database dump neo4j --to-path=/chemin/de/sauvegarde`. Copiez également le fichier SQLite existant (souvent `~/.expandproject/access.sqlite`) et son éventuel fichier `-wal`.
2. Choisissez des valeurs uniques pour `NEO4J_AUTH` et `EXPAND_ADMIN_PASSWORD`. Les anciennes valeurs d'exemple `expand123456`, `expand` et `admin/admin` ne sont plus configurées par défaut. L'admin est créé avec `EXPAND_ADMIN_PASSWORD` uniquement si la base d'accès est vide; les comptes et rôles existants restent ceux de SQLite. Réinitialisez les anciens comptes qui utilisent encore un mot de passe d’exemple avant toute exposition réseau.
3. Pointez `ACCESS_DB_PATH` vers la base SQLite sauvegardée si vous souhaitez conserver utilisateurs et rôles, ou vers `/data/access.sqlite` dans Compose. Le nouveau conteneur monte un volume persistant pour cette base.
4. Invalidez les sessions portail après la migration en supprimant les lignes de la table `sessions` dans SQLite; les utilisateurs devront se reconnecter. Lors d'une authentification réussie, les anciens mots de passe hachés sont progressivement convertis au format PBKDF2 actuel.
5. Les grandes tables récupèrent maintenant leurs pages et leurs filtres depuis l'API. Les intégrations clientes doivent consommer les métadonnées de pagination renvoyées par le serveur et demander les pages suivantes au lieu de supposer que toute la table tient dans une réponse.
6. Avant de reconstruire une ancienne copie de travail, supprimez les anciennes classes JAXB générées dans `importdata/src/main/java/fr/expand/project/importdata/dto/generated` et `importdata/src/main/java/fr/expand/project/importdata/model/generated`. La génération se fait maintenant dans `target/generated-sources/jaxb`.

7. Les nouvelles contraintes Neo4j exigent l’unicité des clés de modèles et des triplets `(modelKey, type, dataId)`. Si des doublons historiques existent, le démarrage échoue explicitement : réconciliez ces doublons après sauvegarde, puis redémarrez.
8. La recherche `searchMode=fulltext` utilise un index Neo4j alimenté uniquement par les champs déclarés recherchables, y compris ceux hérités. Le premier accès indexé d’un ancien modèle reconstruit ses valeurs de recherche. Le mode `contains` reste disponible.
9. Les liens exposent un UUID stable pour leur modification et leur suppression; les identifiants numériques restent acceptés pour les anciens clients. Une mise à jour de modèle ne peut plus changer sa clé : créez un nouveau modèle pour un changement de nom/version.

Cette migration modifie les variables obligatoires, les sessions de connexion et le contrat de pagination côté client. Vérifiez vos scripts et intégrations avant de remettre l'interface en service.

## 📖 Utilisation

### Mode 1 : Exemple avec données pré-fournies

Testez rapidement avec les données d'exemple incluses :

```bash
cd importdata
../mvnw exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" -Dexec.args="--example"
```

Ceci va :
1. Charger le modèle "Réseau Social" (PERSONNE, ENTREPRISE, COMPETENCE)
2. Valider les données d'exemple
3. Afficher le résultat de validation

### Mode 2 : Import complet avec vos données

```bash
cd importdata
../mvnw exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="src/main/resources/model/example_social_network_model.xml src/main/resources/datapack/example_social_network_data.xml"
```

Ceci va :
1. Charger votre modèle
2. Valider vos données
3. **Importer** dans Neo4j si la validation réussit

### Mode 3 : Validation seule (sans import)

```bash
cd importdata
../mvnw exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="path/to/model.xml path/to/data.xml --validate-only"
```

### Mode 4 : Utilisation du JAR

```bash
# Compiler le JAR exécutable
cd importdata
../mvnw package

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
└── pom.xml              # Configuration Maven parent
```

## 🧪 Tests

### Lancer les tests unitaires

```bash
./mvnw test
```

### Tester avec des données personnalisées

1. Créez votre modèle XML dans `importdata/src/main/resources/model/my_model.xml`
2. Créez vos données XML dans `importdata/src/main/resources/datapack/my_data.xml`
3. Lancez la validation :

```bash
cd importdata
../mvnw exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="src/main/resources/model/my_model.xml src/main/resources/datapack/my_data.xml --validate-only"
```

## 📊 Visualiser les données dans Neo4j

Après l'import, visualisez vos données :

1. Ouvrez Neo4j Browser : http://localhost:7474
2. Connectez-vous avec les valeurs définies dans `NEO4J_AUTH`.
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

**Solution** : Configurez un nouveau mot de passe Neo4j puis gardez `NEO4J_AUTH` cohérent avec ce compte :
```bash
neo4j-admin dbms set-initial-password '<new-password>'
```

### Erreur de compilation : "package jakarta.xml.bind does not exist"

**Solution** : Assurez-vous d'utiliser Java 17+ et que Maven a bien téléchargé les dépendances :
```bash
./mvnw clean install -U
```

### Les classes générées (DATAS, OBJECT, etc.) n'existent pas

**Solution** : Lancez la génération JAXB :
```bash
cd importdata
../mvnw generate-sources
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

## Vérification des changements

- `./mvnw test` : tests unitaires Java 17.
- `./mvnw verify` : tests unitaires et d’intégration sur une instance Neo4j de test configurée par `NEO4J_BOLT_URI` et `NEO4J_AUTH`.
- `cd ui && npm ci && npm test && npm audit --audit-level=high && npm run build` : interface et dépendances.
- `cd ui && npx playwright install chromium && E2E_ADMIN_PASSWORD='<mot-de-passe-de-test>' npm run test:e2e` : parcours navigateur sur une API de test au port 18080 (`E2E_API_URL` permet de changer cette adresse). Le test crée puis supprime son propre modèle.

La CI exécute ces contrôles, y compris le parcours navigateur réel avec Neo4j. Dependabot regroupe les mises à jour Maven, npm et GitHub Actions.
