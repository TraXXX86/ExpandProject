# Syntaxe du modele de donnees (XML)

Ce document decrit uniquement la syntaxe attendue pour definir un modele de donnees via XML.

## Fichiers et schema

Le modele doit respecter le schema XSD suivant :
- importdata/src/main/resources/model/model.xsd

Un exemple complet est disponible ici :
- importdata/src/main/resources/model/example_social_network_model.xml

## Structure generale

Racine :
- DATA_MODEL

Elements obligatoires a la racine :
- OBJECT_TYPES
- LINK_TYPES

Attributs de DATA_MODEL :
- NAME (obligatoire)
- VERSION (optionnel)
- DEFAULT_LANGUAGE (optionnel)

Elements optionnels a la racine :
- LANGUAGES

## Types d'objets

OBJECT_TYPES contient une ou plusieurs balises OBJECT_TYPE.

Attributs de OBJECT_TYPE :
- NAME (obligatoire)
- PARENT (optionnel, heritage)
- ICON (optionnel, nom d'une icone Google Material Symbols/Material Icons)

Elements possibles dans OBJECT_TYPE :
- DESCRIPTION (optionnel)
- ATTRIBUTE_DEFINITIONS (optionnel)
- REPRESENTATIVE_ATTRIBUTES (optionnel)
- ATTRIBUTE_GROUPS (optionnel)

### Definitions d'attributs

ATTRIBUTE_DEFINITIONS contient une ou plusieurs balises ATTRIBUTE_DEFINITION.

Attributs de ATTRIBUTE_DEFINITION :
- NAME (obligatoire)
- TYPE (optionnel, defaut STRING)
- REQUIRED (optionnel, defaut false)
- DEFAULT_VALUE (optionnel)
- SEARCHABLE (optionnel, defaut false)

Element possible dans ATTRIBUTE_DEFINITION :
- DESCRIPTION (optionnel)
- LABELS (optionnel)

### Langues

LANGUAGES contient une ou plusieurs balises LANGUAGE.

Attributs de LANGUAGE :
- CODE (obligatoire, exemple FR_fr, FR_ca, EN_uk)
- LABEL (optionnel, texte d'affichage)

DEFAULT_LANGUAGE indique la langue par defaut a utiliser si un libelle n'existe pas dans la langue demandee.

### Libelles multilingues

LABELS contient une ou plusieurs balises LABEL.

Attributs de LABEL :
- LANGUAGE (obligatoire, code de langue)
- VALUE (obligatoire, libelle affiche dans l'IHM)

### Groupes d'attributs

ATTRIBUTE_GROUPS contient une ou plusieurs balises ATTRIBUTE_GROUP.

Attributs de ATTRIBUTE_GROUP :
- NAME (obligatoire)
- ORDER (optionnel, ordre d'affichage des onglets)

ATTRIBUTE_GROUP contient une ou plusieurs balises ATTRIBUTE_REF.

Attributs de ATTRIBUTE_REF :
- NAME (obligatoire, fait reference a ATTRIBUTE_DEFINITION)
- ORDER (optionnel, ordre dans le groupe)

Notes :
- Un attribut peut etre defini sans etre place dans un groupe.
- Un attribut reference dans un groupe doit exister dans ATTRIBUTE_DEFINITIONS.

### Attributs representatifs

REPRESENTATIVE_ATTRIBUTES contient une ou plusieurs balises ATTRIBUTE_REF.

Objectif :
- definir les attributs principaux a afficher en priorite pour identifier un objet
- ces attributs sont utilises dans l'IHM pour l'aperçu, les listes et les resultats de recherche

Notes :
- les references doivent exister dans ATTRIBUTE_DEFINITIONS
- l'ordre est determine par ORDER (sinon par l'ordre de declaration)

## Types de liens

LINK_TYPES contient zero ou plusieurs balises LINK_TYPE.

Attributs de LINK_TYPE :
- NAME (obligatoire)
- DIRECTED (optionnel, defaut true)

Elements dans LINK_TYPE :
- DESCRIPTION (optionnel)
- SOURCE_TYPES (obligatoire)
- TARGET_TYPES (obligatoire)
- ATTRIBUTE_DEFINITIONS (optionnel)

SOURCE_TYPES et TARGET_TYPES contiennent une ou plusieurs balises TYPE_REF.

Attributs de TYPE_REF :
- NAME (obligatoire)

## Types de donnees

Les types supportes pour TYPE :
- STRING
- INTEGER
- DOUBLE
- BOOLEAN
- DATE

## Exemple minimal

```xml
<DATA_MODEL NAME="MonModele" VERSION="1.0" DEFAULT_LANGUAGE="FR_fr">
  <LANGUAGES>
    <LANGUAGE CODE="FR_fr" LABEL="Francais (France)"/>
    <LANGUAGE CODE="FR_ca" LABEL="Francais (Canada)"/>
    <LANGUAGE CODE="EN_uk" LABEL="English (UK)"/>
  </LANGUAGES>
  <OBJECT_TYPES>
    <OBJECT_TYPE NAME="PERSONNE" ICON="person">
      <ATTRIBUTE_DEFINITIONS>
        <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true">
          <LABELS>
            <LABEL LANGUAGE="FR_fr" VALUE="Nom"/>
            <LABEL LANGUAGE="FR_ca" VALUE="Nom"/>
            <LABEL LANGUAGE="EN_uk" VALUE="Last name"/>
          </LABELS>
        </ATTRIBUTE_DEFINITION>
        <ATTRIBUTE_DEFINITION NAME="AGE" TYPE="INTEGER" />
      </ATTRIBUTE_DEFINITIONS>
      <REPRESENTATIVE_ATTRIBUTES>
        <ATTRIBUTE_REF NAME="NOM" ORDER="1" />
      </REPRESENTATIVE_ATTRIBUTES>
    </OBJECT_TYPE>
  </OBJECT_TYPES>
  <LINK_TYPES>
    <LINK_TYPE NAME="CONNAIT" DIRECTED="false">
      <SOURCE_TYPES>
        <TYPE_REF NAME="PERSONNE" />
      </SOURCE_TYPES>
      <TARGET_TYPES>
        <TYPE_REF NAME="PERSONNE" />
      </TARGET_TYPES>
    </LINK_TYPE>
  </LINK_TYPES>
</DATA_MODEL>
```

## Exemple avec groupes d'attributs

```xml
<OBJECT_TYPE NAME="PERSONNE" ICON="person">
  <ATTRIBUTE_DEFINITIONS>
    <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true" />
    <ATTRIBUTE_DEFINITION NAME="PRENOM" TYPE="STRING" REQUIRED="true" />
    <ATTRIBUTE_DEFINITION NAME="EMAIL" TYPE="STRING" />
    <ATTRIBUTE_DEFINITION NAME="TELEPHONE" TYPE="STRING" />
  </ATTRIBUTE_DEFINITIONS>
  <REPRESENTATIVE_ATTRIBUTES>
    <ATTRIBUTE_REF NAME="PRENOM" ORDER="1" />
    <ATTRIBUTE_REF NAME="NOM" ORDER="2" />
  </REPRESENTATIVE_ATTRIBUTES>
  <ATTRIBUTE_GROUPS>
    <ATTRIBUTE_GROUP NAME="Identite" ORDER="1">
      <ATTRIBUTE_REF NAME="NOM" ORDER="1" />
      <ATTRIBUTE_REF NAME="PRENOM" ORDER="2" />
    </ATTRIBUTE_GROUP>
    <ATTRIBUTE_GROUP NAME="Contact" ORDER="2">
      <ATTRIBUTE_REF NAME="EMAIL" ORDER="1" />
      <ATTRIBUTE_REF NAME="TELEPHONE" ORDER="2" />
    </ATTRIBUTE_GROUP>
  </ATTRIBUTE_GROUPS>
</OBJECT_TYPE>
```
