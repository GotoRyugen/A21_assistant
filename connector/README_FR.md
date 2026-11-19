# Relais A21 — prototype MCP

Ce service est le relais distant entre un client MCP compatible et l'application Android. Il ne donne pas un accès général au téléphone.

## Outils MCP
- `a21_status`
- `a21_list_capabilities`
- `a21_open_setting(setting)`
- `a21_cancel_command(command_id)`

## Sécurité
- Deux secrets distincts : `A21_MCP_TOKEN` et `A21_DEVICE_TOKEN`.
- HTTPS obligatoire en production.
- File limitée à 20 commandes, expiration après 5 minutes.
- Liste blanche stricte. Pas de shell, de code arbitraire, de lecture d'écran ou de synchronisation automatique de MemoryAICM.
- L'application Android doit valider chaque commande et demander une confirmation locale avant l'ouverture d'un réglage.

## Lancement local
1. Copier `.env.example` en `.env` et créer deux secrets aléatoires distincts.
2. Installer les dépendances de `requirements.txt`.
3. Exporter les variables d'environnement, puis lancer `uvicorn server:app --host 127.0.0.1 --port 8000`.
4. Pour un déploiement distant, utiliser un hébergement HTTPS et un volume persistant pour `A21_DB_PATH`.

Le serveur ne constitue pas à lui seul une liaison fonctionnelle : l'agent Android doit encore appeler `POST /device/poll` et `POST /device/result`, avec le jeton de l'appareil. Le client MCP doit également être configuré avec l'URL HTTPS et le jeton MCP.

## État
Prototype de relais ajouté au dépôt. Pas encore déployé, pas encore connecté à l'APK, et pas testé de bout en bout. La route MCP est exposée par le SDK MCP; vérifier la compatibilité de transport et d'authentification auprès de l'environnement ChatGPT cible avant publication.

## Côté Android — état du client

L'application contient maintenant un client HTTPS et deux commandes dans l'écran principal :
- **Configurer le relais ChatGPT** : enregistrer l'URL HTTPS et le jeton propre à l'appareil dans les préférences privées de l'application.
- **Vérifier les commandes du relais** : effectuer une vérification manuelle lorsque l'application est ouverte.

Chaque commande reçue est validée (version, identifiant UUID, dates, liste blanche). Pour les actions d'ouverture des réglages, Android affiche une demande d'autorisation locale. Le téléphone renvoie ensuite le résultat au relais.

Cette première intégration ne vérifie pas les commandes en arrière-plan et ne maintient pas une connexion permanente. Le relais n'est pas déployé : aucune URL ni aucun jeton réel ne sont encore disponibles. Ne saisis pas de secrets tant qu'un déploiement HTTPS de confiance n'a pas été préparé.
