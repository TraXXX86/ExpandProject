# ExpandProject - Data Import Tool for Neo4j

ExpandProject est un outil d'import de données dans Neo4j avec support de modèles de données définis en XML et validation automatique.

## 🎯 Fonctionnalités

- **Modèles de données XML** : Définissez vos types d'objets et de liens
- **Validation automatique** : Validez vos données avant l'import
- **Import Neo4j** : Import direct dans une base Neo4j
- **Types de données** : Support STRING, INTEGER, DOUBLE, BOOLEAN, DATE
- **Attributs optionnels/obligatoires** : Contraintes de validation
- **Liens orientés/non-orientés** : Flexibilité dans la modélisation
- **Workflows des objets** : XML indépendant, versions, transitions contrôlées et migrations prévisualisées

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

## Workflows des objets

Un workflow définit les statuts et les transitions autorisées pour un ou plusieurs types d’objets. Il se charge séparément du modèle XML, dans **Administration du modèle → Workflows**. Le modèle doit déjà contenir les types référencés ; son XML reste inchangé.

### Définir et activer un workflow

Le fichier [example-workflow.xml](importdata/src/main/resources/workflow/example-workflow.xml) fournit un cycle de validation complet ; le [schéma XSD](importdata/src/main/resources/workflow/workflow.xsd) décrit le format. Exemple minimal, à adapter au nom exact d’un type de votre modèle :

```xml
<WORKFLOW ID="validation" VERSION="1" LABEL="Validation" INITIAL_STATE="draft">
  <OBJECT_TYPES>
    <TYPE_REF NAME="PERSONNE" INCLUDE_SUBTYPES="true"/>
  </OBJECT_TYPES>
  <STATES>
    <STATE CODE="draft" LABEL="Brouillon"/>
    <STATE CODE="approved" LABEL="Validé" TERMINAL="true"/>
  </STATES>
  <TRANSITIONS>
    <TRANSITION ID="approve" FROM="draft" TO="approved" LABEL="Valider"/>
  </TRANSITIONS>
</WORKFLOW>
```

1. Chargez le fichier XML. Chaque couple identifiant/version est immuable : une modification nécessite une nouvelle version.
2. Sélectionnez **Activer pour les nouveaux objets**, prévisualisez les affectations, puis confirmez. Un type concret possède au plus une affectation active. `INCLUDE_SUBTYPES` inclut ses sous-types existants lors de l’activation ; réactivez explicitement le workflow après l’ajout de nouveaux sous-types.
3. Les objets créés ensuite, y compris par import, reçoivent le statut initial. Dans une fiche objet, le bouton propose les transitions autorisées ; un statut terminal ne propose plus d’action.

### Objets existants et nouvelles versions

L’activation ou le retrait d’une affectation ne modifie jamais les objets existants. Ceux-ci conservent leur version et leur statut. Pour les changer, choisissez **Initialiser des objets sans workflow** ou **Migrer des objets vers cette version**. Pour une migration, définissez la correspondance des statuts source vers les statuts cible, recherchez les objets, sélectionnez-les explicitement, puis prévisualisez et confirmez.

Un lot contient au maximum 1 000 objets. Une modification du statut, de la version ou de l’identité des objets concernés, du catalogue ou du modèle invalide la prévisualisation : il faut la refaire. Les transitions et migrations sont transactionnelles, vérifient l’identité et la révision des objets et produisent un historique. Une version active ou encore utilisée ne peut pas être supprimée.

### Droits et compatibilité

- L’administration des workflows requiert l’accès au portail d’administration du modèle, la visibilité du modèle et les droits de lecture et de modification des données.
- Le nouveau droit **Faire évoluer le statut** est indépendant de la modification des attributs. Il nécessite également la lecture et l’accès au portail utilisateur. Il est désactivé par défaut pour les permissions existantes ; l’administrateur de plateforme conserve l’accès complet. L’usurpation d’identité applique les droits de l’utilisateur effectif.
- Les modèles sans workflow et leurs objets continuent de fonctionner. Les éditions et imports ordinaires préservent le statut ; les métadonnées `_workflowId`, `_workflowVersion`, `_workflowState`, `_workflowRevision` et `_workflowUpdatedAt` sont réservées au moteur.
- Le statut est disponible dans les fiches, les tableaux, les recherches, les filtres et les vues enregistrées. Le filtre **Sans workflow** retrouve les objets non initialisés. Le contrôle qualité signale les incohérences workflow.
- La migration SQLite ajoute automatiquement `model_permissions.can_transition` à `false`. Neo4j reçoit des contraintes pour le catalogue et les affectations et un index de statut. Une modification du modèle incompatible avec les workflows chargés ou utilisés est refusée.

Les fichiers sont limités à 1 Mio, 1 000 références de types, 500 états et 2 000 transitions. Les références inconnues, états inaccessibles, transitions sortant d’un état terminal et XML avec entités externes sont refusés. Cette première version exécute des transitions manuelles ; elle ne lance pas de scripts, notifications ou tâches planifiées.

### API workflow

Toutes les routes exigent une session et les droits correspondants.

| Route | Usage |
| --- | --- |
| `GET /api/workflows?modelKey=…` | Catalogue des versions et affectations |
| `POST /api/workflows` | Chargement multipart : `modelKey`, `workflowFile` |
| `POST /api/workflows/activation/preview` puis `/commit` | Affectation : `modelKey`, `id`, `version`, puis `previewHash` |
| `POST /api/workflows/migration/preview` puis `/commit` | `mode` (`initialize` ou `migrate`), cible, sélection `objectIds`, éventuels `objectTypes`, `sourceId`, `sourceVersion`, `stateMapping`, puis `previewHash` |
| `POST /api/workflows/deactivate` | Retrait des affectations : `modelKey`, `objectTypes` |
| `DELETE /api/workflows` | Suppression d’une version inutilisée : `modelKey`, `id`, `version` |
| `GET /api/objects/{id}/workflow?modelKey=…` | Statut courant et transitions accessibles |
| `POST /api/objects/{id}/workflow/transitions` | `modelKey`, `transitionId`, `expectedRevision`, `objectUuid` ; conflit concurrent : HTTP 409 |

La liste des données accepte `workflowId` et `workflowStatus` ; la valeur `__unassigned__` désigne les objets sans workflow.

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

## Parcours métier

### Import CSV, Excel et XML avec prévisualisation

Dans **Import données**, sélectionnez un modèle et un fichier. Pour CSV/Excel, analysez le fichier, choisissez le type d’objet, la colonne d’identifiant stable et la correspondance entre colonnes et attributs. Les identifiants sont des entiers positifs ou nuls, uniques pour chaque type dans un modèle.

L’aperçu distingue ajouts, modifications, données inchangées et conflits, avec les valeurs avant/après et les lignes à corriger. Le mode « Ajouter uniquement » refuse les identités existantes; « Ajouter et mettre à jour » modifie uniquement les attributs présents dans le fichier et conserve les autres. Aucun import ne supprime de données. Les valeurs par défaut du modèle sont prises en compte.

La confirmation réévalue l’aperçu dans une transaction. Si le modèle ou les données concernées ont changé, l’import est refusé et un nouvel aperçu est nécessaire. Une erreur annule l’ensemble du lot. La lecture et la création sont requises; les modifications nécessitent aussi le droit de mise à jour.

- CSV UTF-8 avec en-têtes uniques; virgule, point-virgule ou tabulation détectés automatiquement; guillemets et cellules multilignes acceptés.
- Excel `.xlsx` avec sélection de feuille. Les formules et cellules en erreur sont refusées; les dates Excel sont converties au format ISO. L’ancien format `.xls` n’est pas pris en charge.
- Limites : 10 Mo par fichier, 5 000 lignes et 200 colonnes; décompression XLSX limitée à 40 Mo. XML conserve son schéma d’objets et de liens; CSV/Excel importent des objets d’un type à la fois.

### Vues enregistrées

Les pages **Recherche avancée** et **Recherche** permettent d’enregistrer une vue privée ou de la partager avec les lecteurs du modèle. La vue mémorise les filtres, les colonnes et le tri. Le tri et les filtres d’attributs s’appliquent à la page chargée. Seul son propriétaire peut modifier ou supprimer une vue; son partage ne donne aucun droit supplémentaire sur les données. Les vues persistent dans SQLite, avec une limite de 100 vues par utilisateur et modèle.

### Historique des modifications

**Historique** présente les créations, modifications, suppressions et imports effectués après activation de cette version. Chaque entrée conserve la date, l’auteur réel, l’utilisateur effectif en cas d’impersonation, l’opération et les valeurs avant/après. L’événement et la modification sont enregistrés dans la même transaction Neo4j. L’historique est filtrable par action, type d’élément et identifiant stable.

L’historique ne restaure pas les données et ne reconstitue pas les modifications anciennes. Les instantanés dépassant 1 Mio sont remplacés par une indication explicite de taille et une empreinte. L’historique d’un modèle supprimé est conservé et reste accessible à l’administrateur par l’API. Aucune purge automatique n’est configurée : son volume doit être inclus dans la politique de sauvegarde et de conservation.

### Chemins et qualité

**Chemins** recherche les plus courts chemins entre deux objets, avec un filtre de types de liens, une profondeur maximale de 1 à 6 et le respect optionnel du sens. La recherche est bornée à 2 000 objets visités, 10 000 liens examinés et 20 chemins retournés. L’interface indique si une limite a interrompu la recherche.

**Qualité** identifie les objets isolés, les doublons potentiels et les écarts au modèle courant. Les doublons sont des suggestions fondées sur des attributs comparables, à vérifier manuellement. L’analyse est limitée à 2 000 objets et 10 000 liens, avec 100 exemples d’anomalies; les résultats précisent lorsqu’ils sont partiels. Aucune correction ni suppression automatique n’est appliquée.

Ces pages utilisent les permissions de lecture du modèle. Un changement de modèle, de session ou d’utilisateur effectif invalide les requêtes en cours et efface les résultats précédents.

## Vérification des changements

- `./mvnw test` : tests unitaires Java 17.
- `./mvnw verify` : tests unitaires et d’intégration sur une instance Neo4j de test configurée par `NEO4J_BOLT_URI` et `NEO4J_AUTH`.
- `cd ui && npm ci && npm test && npm audit --audit-level=high && npm run build` : interface et dépendances.
- `cd ui && npx playwright install chromium && E2E_ADMIN_PASSWORD='<mot-de-passe-de-test>' npm run test:e2e` : parcours navigateur sur une API de test au port 18080 (`E2E_API_URL` permet de changer cette adresse). Le test crée puis supprime son propre modèle.

La CI exécute ces contrôles, y compris le parcours navigateur réel avec Neo4j. Dependabot regroupe les mises à jour Maven, npm et GitHub Actions.
