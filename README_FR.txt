A21 ASSISTANT — prototype 0.1.0
================================

Ce dossier contient le squelette source d'une application Android native.

CE QUE FAIT CE PROTOTYPE
- Affiche une première interface Android.
- Lance la reconnaissance vocale quand l'utilisateur appuie sur le bouton.
- Lit une confirmation à voix haute.
- Propose un raccourci vers les paramètres d'accessibilité.
- Déclare un service d'accessibilité, mais celui-ci n'effectue encore aucune action.

CE QUI N'EST PAS ENCORE FAIT
- Connexion à ChatGPT.
- Exécution de commandes sur les autres applications.
- Écoute permanente du micro.
- Service en arrière-plan permanent.
- Génération d'un APK installable.

LIMITES IMPORTANTES
Un assistant qui écoute en permanence doit respecter les restrictions Android sur le microphone en arrière-plan,
les notifications persistantes, l'optimisation de batterie et les règles de confidentialité. Ce prototype n'écoute
donc que lorsque l'utilisateur appuie sur le bouton vocal.
Le service d'accessibilité doit être activé manuellement dans les paramètres Android. Il ne doit pas servir à
collecter des mots de passe, contourner des protections ou agir sans autorisation.
Pour relier ChatGPT, il faudra choisir une architecture compatible (par exemple une passerelle sécurisée et un
outil/connexion ChatGPT). Ne place jamais une clé API secrète directement dans l'application.

COMPILATION
Ce projet doit être ouvert dans Android Studio ou compilé avec un environnement Android/Gradle compatible.
Acode est un éditeur de fichiers : il ne compile pas à lui seul un projet Android natif en APK.
Le téléphone seul peut éditer le code, mais la compilation nécessitera un service de build distant ou un ordinateur.

PROCHAINE ÉTAPE
Faire compiler ce squelette pour obtenir un APK de test, puis ajouter les commandes Android une par une avec
confirmation utilisateur.


COMPILATION CLOUD (GitHub Actions)
----------------------------------
Le dossier .github/workflows/android-apk.yml permet une compilation cloud via GitHub Actions.
1. Créer un dépôt GitHub (privé conseillé).
2. Téléverser le contenu complet du dossier A21_Assistant à la racine du dépôt.
3. Ouvrir l'onglet Actions et activer les workflows si GitHub le demande.
4. Lancer "Build A21 Assistant APK" avec Run workflow.
5. Quand le workflow réussit, télécharger l'artefact "A21-Assistant-debug-apk".
Ne publiez pas de clés API, mots de passe ou données personnelles dans le dépôt.
