# Ameliorations possibles pour l'application

## Contexte

Cette note recense des pistes d'amelioration pour l'application **ExpandProject**, a partir de l'etat actuel du depot.  
L'application combine :

- un backend Java/Maven pour l'import et la validation de donnees XML ;
- une API HTTP autour de Neo4j ;
- une interface Vue 3 / Vuetify pour l'administration, la recherche et l'exploration des donnees.

L'objectif de ce document est de proposer des ameliorations **concretes, priorisees et actionnables**.

---

## 1. Securiser les identifiants et secrets par defaut

### Constat
- Le compte administrateur initial affiche `admin / admin` dans l'interface (`ui/src/App.vue`).
- Le backend initialise aussi un mot de passe admin par defaut (`importdata/.../AccessControlStore.java`).
- Le mot de passe Neo4j est renseigne en clair dans `docker-compose.yml`.

### Amelioration proposee
- Remplacer les identifiants par defaut par une configuration via variables d'environnement.
- Imposer un changement de mot de passe au premier demarrage.
- Fournir un mode "developpement local" distinct du mode "production".

### Benefice
- Reduit fortement le risque de compromission.
- Clarifie les pratiques de deploiement.

### Priorite
**Tres haute**

---

## 2. Renforcer la gestion des sessions et de l'authentification

### Constat
- Le token d'authentification est stocke dans `localStorage` (`ui/src/composables/useAppState.js`).
- Ce choix expose davantage l'application aux impacts d'une faille XSS.

### Amelioration proposee
- Preferer un cookie `HttpOnly`, `Secure`, `SameSite`.
- Ajouter expiration, rotation et invalidation plus explicite des sessions.
- Journaliser les connexions et deconnexions sensibles.

### Benefice
- Meilleure securite des comptes.
- Base plus solide pour de futures fonctions d'administration.

### Priorite
**Tres haute**

---

## 3. Rendre la politique CORS configurable

### Constat
- L'API renvoie actuellement `Access-Control-Allow-Origin: *` (`importdata/.../ImportApiServer.java`).

### Amelioration proposee
- Utiliser une liste blanche d'origines autorisees.
- Differencier les valeurs entre dev, recette et production.
- Centraliser cette configuration dans un fichier ou des variables d'environnement.

### Benefice
- Reduction de la surface d'attaque.
- Configuration plus propre entre environnements.

### Priorite
**Haute**

---

## 4. Remplacer le hash de mot de passe actuel par un mecanisme robuste

### Constat
- Le stockage des mots de passe repose sur un hash sale mais simple dans `AccessControlStore.java`.

### Amelioration proposee
- Migrer vers **Argon2**, **bcrypt** ou **PBKDF2**.
- Prevoir une migration progressive des comptes existants.

### Benefice
- Meilleure resistance face aux attaques hors ligne.
- Niveau de securite conforme aux pratiques modernes.

### Priorite
**Haute**

---

## 5. Decouper le backend pour ameliorer la maintenabilite

### Constat
- `ImportApiServer.java` concentre de nombreuses responsabilites : auth, ACL, modeles, donnees, helpers HTTP, CORS, etc.

### Amelioration proposee
- Extraire les routes par domaine :
  - `AuthRoutes`
  - `AccessRoutes`
  - `ModelRoutes`
  - `DataRoutes`
- Introduire des services et mappers plus explicites.
- Mieux separer logique metier, transport HTTP et acces aux donnees.

### Benefice
- Code plus lisible.
- Tests plus faciles a ecrire.
- Evolutions moins risquees.

### Priorite
**Haute**

---

## 6. Decouper l'etat front-end monolithique

### Constat
- `ui/src/composables/useAppState.js` porte une grande partie de l'etat et des actions de l'application.

### Amelioration proposee
- Separer les responsabilites en plusieurs composables ou stores :
  - `useAuthState`
  - `useModelState`
  - `useDataState`
  - `useAdminState`
  - `useExplorerState`
- Envisager l'usage de **Pinia** si l'application continue a grossir.

### Benefice
- Front plus simple a comprendre.
- Moins d'effets de bord.
- Base plus saine pour ajouter de nouvelles ecrans.

### Priorite
**Haute**

---

## 7. Eviter de charger tout le graphe cote client

### Constat
- Une partie importante des usages semble reposer sur le chargement global des objets et des liens avant filtrage dans le front.

### Amelioration proposee
- Ajouter de la pagination cote serveur.
- Ajouter des endpoints de filtrage dedies.
- Charger les voisins de graphe a la demande dans l'explorateur.
- Limiter les colonnes et attributs renvoyes selon le besoin.

### Benefice
- Meilleures performances.
- Temps de chargement reduit.
- Application plus scalable sur des volumes reels.

### Priorite
**Tres haute**

---

## 8. Ajouter une vraie recherche full-text cote backend

### Constat
- L'ecran de recherche existe, mais la logique actuelle semble s'appuyer largement sur des donnees deja chargees cote client.

### Amelioration proposee
- Ajouter une API de recherche dediee.
- S'appuyer sur les index full-text Neo4j.
- Exploiter les attributs metiers marques comme recherchables.

### Benefice
- Recherche plus rapide et plus pertinente.
- Moins de dependance au volume charge dans l'IHM.

### Priorite
**Haute**

---

## 9. Completer la validation metier des liens

### Constat
- La validation couvre deja bien les objets, mais la validation des attributs de liens parait moins complete.

### Amelioration proposee
- Valider les attributs obligatoires des liens.
- Controler plus strictement les types, valeurs par defaut et attributs inconnus.
- Ajouter des messages d'erreur plus explicites pour faciliter l'import.

### Benefice
- Imports plus fiables.
- Moins d'incoherences en base.

### Priorite
**Moyenne a haute**

---

## 10. Completer le CRUD des relations

### Constat
- La creation de liens est exposee, mais la mise a jour et la suppression des relations paraissent incompletes.

### Amelioration proposee
- Ajouter `PUT` / `DELETE` sur les liens.
- Definir un identifiant stable pour les relations.
- Permettre l'edition des attributs d'un lien depuis l'IHM.

### Benefice
- Couverture fonctionnelle plus complete.
- Meilleure experience d'administration des donnees.

### Priorite
**Moyenne**

---

## 11. Corriger la fragilite potentielle liee au modele courant global

### Constat
- La gestion du modele courant semble reposer sur un etat mutable partage dans le backend.

### Amelioration proposee
- Eviter un `currentModel` global mutable.
- Charger explicitement le modele cible par requete ou par contexte.
- Rendre le comportement plus robuste en cas d'appels concurrents.

### Benefice
- Moins de risques d'erreurs difficiles a reproduire.
- Meilleure tenue en charge et en multi-utilisateur.

### Priorite
**Haute**

---

## 12. Centraliser la configuration Neo4j

### Constat
- La configuration d'acces a Neo4j semble repetee dans plusieurs classes.

### Amelioration proposee
- Introduire une couche de configuration unique.
- Factoriser la creation des clients/connecteurs.
- Uniformiser la lecture des variables d'environnement.

### Benefice
- Moins de duplication.
- Parametrage plus simple.
- Reduction des ecarts entre composants.

### Priorite
**Moyenne**

---

## 13. Renforcer la strategie de tests

### Constat
- Le backend dispose de quelques tests, mais l'ensemble reste limite.
- Aucun dispositif clair de tests front n'apparait dans le depot.
- La CI front semble se limiter au build.

### Amelioration proposee
- Ajouter des tests unitaires backend sur les cas de validation et permissions.
- Ajouter des tests d'integration API.
- Ajouter des tests composants Vue.
- Ajouter quelques scenarios E2E critiques :
  - connexion ;
  - import de modele ;
  - import de donnees ;
  - recherche ;
  - creation d'objet.

### Benefice
- Regressions detectees plus tot.
- Evolutions plus sereines.

### Priorite
**Tres haute**

---

## 14. Nettoyer les modules incomplets ou peu exploites

### Constat
- Le module `model` semble encore embryonnaire.
- Certaines classes paraissent etre des placeholders ou des vestiges d'une architecture precedente.

### Amelioration proposee
- Supprimer ou finaliser les modules incomplets.
- Documenter clairement ce qui est experimental, obsolete ou a venir.
- Mieux separer le code genere du code maintenu a la main.

### Benefice
- Architecture plus lisible.
- Onboarding plus simple pour les nouveaux contributeurs.

### Priorite
**Moyenne**

---

## 15. Ameliorer l'experience utilisateur de l'IHM

### Constat
- L'application propose deja plusieurs ecrans utiles, mais elle peut gagner en guidage et en ergonomie.

### Amelioration proposee
- Ajouter plus de feedback apres les actions longues (import, chargement, suppression).
- Mieux guider l'utilisateur en cas d'erreur de validation.
- Ajouter des vues de synthese :
  - nombre d'objets importes ;
  - repartition par type ;
  - erreurs et avertissements recents ;
  - etat de sante des services.
- Introduire un vrai routage URL pour partager facilement un ecran ou un contexte.

### Benefice
- Meilleure prise en main.
- Moins de friction pour les utilisateurs metier.

### Priorite
**Moyenne a haute**

---

## Recommandation de feuille de route

### Lot 1 - Quick wins a forte valeur
1. Supprimer les secrets et comptes par defaut.
2. Renforcer auth/session/CORS.
3. Ajouter une base minimale de tests backend et front.
4. Ameliorer les messages d'erreur et retours utilisateur.

### Lot 2 - Stabilisation technique
5. Decouper `ImportApiServer`.
6. Decouper `useAppState`.
7. Centraliser la configuration Neo4j.
8. Nettoyer les modules incomplets.

### Lot 3 - Passage a l'echelle fonctionnel
9. Pagination et filtres serveur.
10. Recherche full-text backend.
11. CRUD complet des liens.
12. Gestion multi-modele plus robuste.

---

## Resume executif

Si je devais retenir les **5 priorites principales** pour cette application :

1. **Securite** : retirer les credentials par defaut et renforcer l'authentification.
2. **Performance** : ne plus faire reposer la recherche et l'exploration sur un chargement massif cote client.
3. **Maintenabilite backend** : decouper `ImportApiServer`.
4. **Maintenabilite front** : decouper `useAppState.js`.
5. **Qualite** : ajouter une vraie strategie de tests.

Ces evolutions apporteraient un gain rapide en **fiabilite**, **securite**, **lisibilite du code** et **capacite a faire evoluer l'application**.
