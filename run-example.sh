#!/bin/bash

# ExpandProject - Script de lancement rapide
# Ce script lance l'exemple avec les données pré-fournies

echo "========================================="
echo "  ExpandProject - Quick Start Script"
echo "========================================="
echo ""

# Vérifier que Java est installé
if ! command -v java &> /dev/null; then
    echo "❌ Erreur: Java n'est pas installé"
    echo "   Installez Java 17+: sudo apt-get install openjdk-17-jdk"
    exit 1
fi

echo "✅ Java version:"
java -version
echo ""

echo "📦 Compilation du projet..."
./mvnw clean install -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ Erreur lors de la compilation"
    exit 1
fi

echo "✅ Compilation réussie"
echo ""
echo "🧪 Lancement de l'exemple de validation..."
echo ""

cd importdata
../mvnw exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="--example" -q

echo ""
echo "========================================="
echo "  Script terminé"
echo "========================================="
echo ""
echo "Pour importer dans Neo4j, utilisez:"
echo "  cd importdata"
echo "  ../mvnw exec:java -Dexec.mainClass=\"fr.expand.project.importdata.Launcher\" \\"
echo "    -Dexec.args=\"src/main/resources/model/example_social_network_model.xml src/main/resources/datapack/example_social_network_data.xml\""
