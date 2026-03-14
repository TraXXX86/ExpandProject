# ExpandProject : archive de l'ancien module `model`

Ce dossier n'est plus un module Maven actif.

## Statut

- **Archivé** : retiré du build racine pour ne plus exposer un module vide/incomplet.
- **Non maintenu** : aucun code runtime ne doit etre ajoute ici.
- **Conserve pour contexte historique** : la logique de modele maintenue se trouve dans `importdata/`.

## Ou se trouve le code actif

- Chargement et cache des modeles : `importdata/src/main/java/fr/expand/project/importdata/model/`
- Schema XML du modele : `importdata/src/main/resources/model/model.xsd`
- Classes JAXB generees : `fr.expand.project.importdata.model.generated`

Si vous devez faire evoluer le systeme de modele, modifiez le module `importdata` et la documentation associee plutot que ce dossier.