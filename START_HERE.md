# 🎯 COMMENCER ICI - ExpandProject

## En 3 Commandes

```bash
# 1. Compiler
mvn clean install

# 2. Tester l'exemple
java -jar importdata/target/expandproject-importdata.jar --example

# 3. (Optionnel) Importer dans Neo4j
# D'abord, démarrer Neo4j (voir ci-dessous)
# Puis:
cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="src/main/resources/model/example_social_network_model.xml src/main/resources/datapack/example_social_network_data.xml"
```

## 🐳 Démarrage Neo4j (Docker - Recommandé)

```bash
docker run -d \
  --name neo4j-expand \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/expand \
  neo4j:5.17.0

# Vérifier que Neo4j est démarré
docker logs neo4j-expand

# Accéder à l'interface web
# http://localhost:7474 (login: neo4j/expand)
```

## 📋 Que fait cette application ?

1. **Définit un modèle de données** en XML
   - Types d'objets (ex: PERSONNE, ENTREPRISE)
   - Types de liens (ex: TRAVAILLE_POUR, CONNAIT)
   - Attributs avec types et contraintes

2. **Valide vos données** contre ce modèle
   - Vérification des types
   - Attributs obligatoires
   - Relations autorisées

3. **Importe dans Neo4j** si valide
   - Création des noeuds
   - Création des relations
   - Conservation des attributs

## 📁 Fichiers Importants

| Fichier | Description |
|---------|-------------|
| `README.md` | Documentation complète |
| `QUICKSTART.md` | Guide de démarrage rapide |
| `ADVANCED_USAGE.md` | Utilisation avancée |
| `importdata/target/expandproject-importdata.jar` | JAR exécutable |
| `run-example.sh` | Script de test rapide |
| `run-import.sh` | Script d'import |

## 🚀 Exemples Rapides

### Valider des données

```bash
java -jar importdata/target/expandproject-importdata.jar \
  model.xml data.xml --validate-only
```

### Importer dans Neo4j

```bash
java -jar importdata/target/expandproject-importdata.jar \
  model.xml data.xml
```

### Utiliser les scripts

```bash
# Test rapide
./run-example.sh

# Import personnalisé
./run-import.sh mon_modele.xml mes_donnees.xml
```

## 🔍 Voir les Résultats

Une fois les données importées :

1. Ouvrez **http://localhost:7474**
2. Connectez-vous avec **neo4j/expand**
3. Exécutez :

```cypher
MATCH (n) RETURN n LIMIT 50
```

## ❓ Besoin d'Aide ?

- **Problème de compilation ?** → Vérifiez que Java 11+ est installé : `java -version`
- **Neo4j ne démarre pas ?** → Vérifiez Docker : `docker ps` ou le service : `neo4j status`
- **Erreurs de validation ?** → Consultez les logs dans `logs/expandproject.log`

## 📖 Pour Aller Plus Loin

1. Lisez le [Guide de Démarrage Rapide](QUICKSTART.md)
2. Consultez le [README Complet](README.md)
3. Explorez les [Cas d'Usage Avancés](ADVANCED_USAGE.md)

## ✨ Prochaines Étapes

1. ✅ Testez avec les données d'exemple
2. 📝 Créez votre propre modèle XML
3. 📊 Importez vos données
4. 🔍 Explorez avec Cypher
5. 🚀 Déployez en production

**Bonne utilisation ! 🎉**
