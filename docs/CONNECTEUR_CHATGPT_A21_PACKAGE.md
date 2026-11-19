# A21 Assistant — connecteur ChatGPT

## Ce dossier n'est pas un fichier .plugin installable

ChatGPT n'installe pas un fichier arbitraire portant l'extension `.plugin`. Un connecteur utilisable doit être publié comme service distant sécurisé, avec une URL HTTPS et un protocole compatible. Le dépôt GitHub, à lui seul, ne fournit pas cet hébergement.

## Objectif

Relier la conversation ChatGPT à l'application Android A21, sans clé API OpenAI ni coût d'API OpenAI supplémentaire.

## Fonctions prévues

- `a21_status` : état de l'agent Android et de sa connexion.
- `a21_list_capabilities` : liste des fonctions réellement activées sur le téléphone.
- `a21_open_setting` : ouvrir un réglage Android autorisé.
- `a21_cancel_command` : annuler une commande en attente.

## Architecture nécessaire

1. Un serveur MCP distant accessible en HTTPS.
2. Une authentification forte entre le serveur et l'application A21.
3. Une file de commandes avec expiration et protection contre les rejeux.
4. Une connexion sortante HTTPS depuis Android vers le serveur (aucun port entrant public sur le téléphone).
5. Une validation locale par A21 avant toute action sensible.
6. Une interface ChatGPT compatible avec les connecteurs MCP personnalisés.

## Limites actuelles

- Le service distant n'est pas encore déployé.
- L'application Android ne dialogue pas encore avec ce serveur.
- La politique `RemoteCommandPolicy` valide une enveloppe de commande mais n'exécute rien et n'est pas encore branchée à l'application.
- L'accessibilité Android actuelle est un squelette : elle ne lit pas le contenu de l'écran.
- La mémoire MemoryAICM reste locale au téléphone.
- La disponibilité d'un connecteur MCP personnalisé dépend du forfait et de l'interface ChatGPT utilisés. Un fichier local ne peut pas contourner cette restriction.

## Sécurité

Aucune lecture générale de l'écran, aucun accès aux messages, contacts ou fichiers, et aucun contrôle arbitraire du téléphone ne doivent être activés par défaut. Chaque capacité devra être explicitement autorisée par l'utilisateur. Les données privées de MemoryAICM ne doivent pas être envoyées au serveur sans demande et consentement explicites.

## Prochaine étape technique

Implémenter le transport Android ↔ serveur, puis héberger le serveur MCP sur une infrastructure HTTPS. Tester le cycle complet avec des commandes non sensibles avant d'ajouter d'autres capacités.
