A21 ASSISTANT — prototype local 0.4.0
======================================

OBJECTIF
A21 est un assistant Android local, conçu pour fonctionner sans abonnement API
supplémentaire. La mémoire est fournie par le projet memoryaicm intégré à
l'application et stockée dans l'espace privé de l'application sur le téléphone.

CE QUI EST DISPONIBLE DANS CETTE VERSION
- Interface Android native.
- Reconnaissance vocale à la demande (l'utilisateur appuie sur le bouton).
- Réponse vocale en français.
- Mémoire locale memoryaicm : mémoriser, rappeler et oublier.
- Commande « aide » pour afficher les commandes disponibles.
- Ouverture de certains réglages Android par la voix :
  Wi-Fi, Bluetooth, batterie, applications et paramètres généraux.
- Les actions d'ouverture sont lancées par Android et restent visibles.

COMMANDES VOCALES
- « Aide »
- « Souviens-toi que ... »
- « Mémorise que ... »
- « Rappelle-moi ... »
- « Que sais-tu sur ... »
- « Oublie ... »
- « Ouvre le Wi-Fi »
- « Ouvre le Bluetooth »
- « Ouvre la batterie »
- « Ouvre les applications »
- « Ouvre les paramètres »

CONFIDENTIALITÉ ET LIMITES
- La mémoire fonctionne localement, dans le stockage privé de l'application.
- Cette version n'envoie pas les souvenirs vers un serveur.
- Aucun micro n'est écouté en permanence : la reconnaissance démarre après
  une action explicite sur le bouton.
- Le service d'accessibilité est uniquement un point d'extension : il ne lit
  pas l'écran et n'effectue aucune action pour le moment.
- Aucune connexion directe à la conversation ChatGPT actuelle n'est présente.
  Une application Android ne peut pas recevoir automatiquement les messages
  de cette conversation sans intégration autorisée dédiée.
- Pas d'API OpenAI ni de clé API dans cette version.
- Les commandes disponibles sont locales et limitées ; ce n'est pas encore
  un agent capable de comprendre et d'exécuter librement toutes les demandes.

PLAN DU FUTUR CONNECTEUR CHATGPT
- Le plan technique et les exigences de sécurité sont décrits dans
  docs/CONNECTEUR_CHATGPT_A21.md.
- Cette documentation ne signifie pas que le connecteur est déjà opérationnel.
- La liaison nécessitera un service MCP HTTPS et un agent Android qui récupère
  les commandes par connexion sortante ; la compatibilité dépendra aussi des
  fonctions de connecteurs disponibles sur le compte ChatGPT utilisé.

PROCHAINES ÉTAPES POSSIBLES
1. Tester l'installation sur le Samsung et vérifier les commandes vocales.
2. Ajouter des actions Android simples, une par une, avec confirmation visible
   avant toute action qui modifie des données ou envoie un message.
3. Étudier une passerelle gratuite et autorisée entre ChatGPT et A21, sans
   promettre un accès direct à cette conversation tant qu'une intégration
   compatible n'est pas réellement disponible.

COMPILATION
Le workflow GitHub Actions du dépôt peut produire un APK de test.
Après un commit, ouvrir l'onglet Actions, attendre la fin de « Build A21
Assistant APK », puis télécharger l'artefact « A21-Assistant-debug-apk ».
Ne publiez jamais de clé API, mot de passe ou donnée personnelle dans GitHub.
