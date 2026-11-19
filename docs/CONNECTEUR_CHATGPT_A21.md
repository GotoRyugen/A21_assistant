# Connecteur ChatGPT ↔ A21 — plan technique

## Objectif

Permettre, lorsque l'environnement ChatGPT de l'utilisateur autorise les connecteurs MCP personnalisés, de transmettre des commandes explicites à A21 sur son téléphone Android. A21 reste le composant qui décide si une action locale est autorisée et qui l'exécute.

Ce document décrit une architecture cible. **Le connecteur n'est pas encore implémenté et cette version de l'application ne reçoit aucune commande depuis ChatGPT.**

## Architecture cible

1. **ChatGPT** appelle un outil MCP exposé par un service de connecteur.
2. **Service MCP** authentifie l'utilisateur, valide le schéma de commande et place une commande courte dans une file d'attente.
3. **A21 sur Android** récupère la commande par une connexion HTTPS sortante initiée par le téléphone. Aucun port entrant du téléphone n'est ouvert sur Internet.
4. **A21** vérifie l'autorisation locale, la fraîcheur de la commande et son identifiant anti-rejeu.
5. **A21** exécute uniquement une action explicitement prise en charge, puis renvoie un résultat minimal.
6. **ChatGPT** reçoit le résultat par le service MCP.

Le téléphone doit rester maître de ses autorisations. Le connecteur ne doit jamais contourner les protections Android.

## Contraintes non négociables

- Pas de clé API OpenAI dans l'application Android, le dépôt Git ou les journaux.
- Pas de promesse de compatibilité avec un compte ChatGPT donné avant un test réel des connecteurs MCP sur ce compte.
- Le service MCP et son hébergement peuvent nécessiter un service distant. Une offre gratuite ne peut être considérée comme garantie sans vérifier ses limites et sa pérennité.
- Le téléphone ne doit pas exposer directement un serveur HTTP public.
- Authentification par jetons révocables, stockage Android Keystore, TLS, expiration courte et protection anti-rejeu.
- Liste blanche d'actions. Pas d'exécution de shell, de code arbitraire, de commandes libres ni d'accès général aux fichiers.
- Confirmation locale requise pour toute action sensible (messages, appels, achats, suppression, partage de données, changements de sécurité).
- Journal local des commandes et possibilité de désactiver immédiatement la liaison.
- Ne transmettre à ChatGPT que le minimum de données nécessaire. La mémoire MemoryAICM reste locale par défaut.

## Première liste d'outils MCP envisagée

- `a21_status` : vérifier si le téléphone est joignable et si A21 est prêt.
- `a21_list_capabilities` : afficher les actions réellement disponibles sur l'appareil.
- `a21_open_setting` : ouvrir une page de réglages autorisée parmi une liste fermée (Wi-Fi, Bluetooth, batterie, applications, paramètres).
- `a21_cancel_command` : annuler une commande en attente.

Les outils de mémoire (rappeler, mémoriser, oublier) ne seront ajoutés qu'après définition d'un consentement clair et d'une politique de données. La mémoire locale ne sera pas synchronisée automatiquement.

## Phasage

### Phase 1 — contrat et sécurité
- Définir les formats JSON stricts des commandes et réponses.
- Définir authentification, révocation, expiration, anti-rejeu et confirmation.
- Conserver le fonctionnement actuel hors ligne si le connecteur est indisponible.

### Phase 2 — agent Android
- Ajouter un composant de liaison Android avec connexion HTTPS sortante.
- Afficher l'état de liaison et un bouton d'arrêt.
- Implémenter uniquement les commandes de diagnostic et l'ouverture de réglages déjà prises en charge.
- Tester les restrictions Android en veille et après redémarrage.

### Phase 3 — service MCP
- Déployer le serveur MCP sur un hébergement HTTPS compatible.
- Ajouter authentification et file d'attente.
- Tester les outils depuis un environnement ChatGPT qui prend effectivement en charge les connecteurs MCP personnalisés.

### Phase 4 — validation
- Tester les commandes, les refus, les expirations, la révocation, les pertes réseau et les redémarrages.
- Ne déclarer la fonction disponible qu'après un test réel de bout en bout.

## État actuel du dépôt

A21 est une application Android locale avec reconnaissance vocale à la demande, quelques raccourcis vers les réglages Android et MemoryAICM embarqué. Le service d'accessibilité est un squelette inactif. Il n'existe actuellement ni service MCP, ni serveur relais, ni communication réseau avec ChatGPT, ni réception de commandes à distance.
