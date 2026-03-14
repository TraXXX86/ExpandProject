#!/bin/bash

# ExpandProject - Script d'import dans Neo4j
# Ce script importe les données d'exemple dans Neo4j

echo "========================================="
echo "  ExpandProject - Import to Neo4j"
echo "========================================="
echo ""

if [ -f ".env" ]; then
    set -a
    . ".env"
    set +a
fi

# Vérifier les arguments
if [ "$#" -lt 2 ]; then
    echo "Usage: ./run-import.sh <model.xml> <data.xml> [--validate-only]"
    echo ""
    echo "Exemples:"
    echo "  # Import complet"
    echo "  ./run-import.sh importdata/src/main/resources/model/example_social_network_model.xml \\"
    echo "                  importdata/src/main/resources/datapack/example_social_network_data.xml"
    echo ""
    echo "  # Validation seule"
    echo "  ./run-import.sh model.xml data.xml --validate-only"
    exit 1
fi

MODEL_FILE=$1
DATA_FILE=$2
VALIDATE_ONLY=${3:-""}

# Vérifier que les fichiers existent
if [ ! -f "$MODEL_FILE" ]; then
    echo "❌ Erreur: Fichier modèle non trouvé: $MODEL_FILE"
    exit 1
fi

if [ ! -f "$DATA_FILE" ]; then
    echo "❌ Erreur: Fichier données non trouvé: $DATA_FILE"
    exit 1
fi

# Vérifier que Neo4j est accessible (si pas en mode validation seule)
if [ "$VALIDATE_ONLY" != "--validate-only" ]; then
    echo "🔍 Vérification de la connexion Neo4j..."
    
    # Tester la connexion Neo4j
    if command -v nc &> /dev/null; then
        nc -z localhost 7687 2>/dev/null
        if [ $? -ne 0 ]; then
            echo "⚠️  Avertissement: Neo4j ne semble pas accessible sur localhost:7687"
            echo "   Assurez-vous que Neo4j est démarré:"
            echo "     - Docker: docker start neo4j-expand"
            echo "     - Service: sudo systemctl start neo4j"
            echo "     - macOS: neo4j start"
            echo ""
            read -p "Continuer quand même ? (y/N) " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                exit 1
            fi
        else
            echo "✅ Neo4j accessible"
        fi
    fi
fi

echo ""
echo "📋 Configuration:"
echo "   Modèle: $MODEL_FILE"
echo "   Données: $DATA_FILE"
echo "   Mode: $([ "$VALIDATE_ONLY" == "--validate-only" ] && echo "VALIDATION SEULE" || echo "IMPORT COMPLET")"
echo ""

# Compilation rapide si nécessaire
if [ ! -d "importdata/target" ]; then
    echo "📦 Compilation du projet..."
    mvn clean install -DskipTests -q
    if [ $? -ne 0 ]; then
        echo "❌ Erreur lors de la compilation"
        exit 1
    fi
fi

# Lancement
echo "🚀 Lancement de l'import..."
echo ""

cd importdata
mvn exec:java -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="$MODEL_FILE $DATA_FILE $VALIDATE_ONLY"

EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ Succès!"
    
    if [ "$VALIDATE_ONLY" != "--validate-only" ]; then
        echo ""
        echo "📊 Pour visualiser les données:"
        echo "   1. Ouvrez http://localhost:7474"
        echo "   2. Connectez-vous avec les identifiants Neo4j configurés (NEO4J_AUTH)"
        echo "   3. Exécutez: MATCH (n) RETURN n"
    fi
else
    echo "❌ Échec - Voir les erreurs ci-dessus"
    exit $EXIT_CODE
fi
