package fr.a21assistant;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Valide le format des futures commandes distantes.
 *
 * Cette classe n'exécute rien et ne constitue pas un transport réseau.
 * Elle sert de garde-fou avant l'ajout ultérieur d'un relais authentifié.
 */
final class RemoteCommandPolicy {
    static final long MAX_VALIDITY_MS = 5L * 60L * 1000L;
    static final long MAX_CLOCK_SKEW_MS = 60L * 1000L;

    enum Action {
        STATUS,
        LIST_CAPABILITIES,
        OPEN_WIFI,
        OPEN_BLUETOOTH,
        OPEN_BATTERY,
        OPEN_APPLICATIONS,
        OPEN_SETTINGS
    }

    static final class ValidatedCommand {
        final String id;
        final Action action;
        final boolean requiresLocalConfirmation;
        final long expiresAt;

        ValidatedCommand(String id, Action action, boolean requiresLocalConfirmation,
                         long expiresAt) {
            this.id = id;
            this.action = action;
            this.requiresLocalConfirmation = requiresLocalConfirmation;
            this.expiresAt = expiresAt;
        }
    }

    private RemoteCommandPolicy() { }

    /**
     * Expected envelope:
     * {"version":1,"id":"UUID","action":"OPEN_WIFI",
     *  "issued_at":epochMillis,"expires_at":epochMillis,
     *  "requires_confirmation":true}
     */
    static ValidatedCommand validate(String rawJson, long nowMillis)
            throws JSONException, IllegalArgumentException {
        if (rawJson == null || rawJson.length() > 4096) {
            throw new IllegalArgumentException("Commande absente ou trop volumineuse.");
        }

        JSONObject json = new JSONObject(rawJson);
        if (json.optInt("version", -1) != 1) {
            throw new IllegalArgumentException("Version de commande inconnue.");
        }

        String id = json.optString("id", "");
        try {
            UUID.fromString(id);
        } catch (Exception invalidId) {
            throw new IllegalArgumentException("Identifiant de commande invalide.");
        }

        String actionName = json.optString("action", "");
        final Action action;
        try {
            action = Action.valueOf(actionName);
        } catch (Exception invalidAction) {
            throw new IllegalArgumentException("Action non autorisée.");
        }

        long issuedAt = json.optLong("issued_at", -1L);
        long expiresAt = json.optLong("expires_at", -1L);
        if (issuedAt <= 0L || expiresAt <= issuedAt) {
            throw new IllegalArgumentException("Dates de commande invalides.");
        }
        if (issuedAt > nowMillis + MAX_CLOCK_SKEW_MS) {
            throw new IllegalArgumentException("Commande datée dans le futur.");
        }
        if (expiresAt <= nowMillis) {
            throw new IllegalArgumentException("Commande expirée.");
        }
        if (expiresAt - issuedAt > MAX_VALIDITY_MS) {
            throw new IllegalArgumentException("Durée de validité trop longue.");
        }

        boolean confirmation = json.optBoolean("requires_confirmation", true);
        if (action != Action.STATUS && action != Action.LIST_CAPABILITIES && !confirmation) {
            throw new IllegalArgumentException(
                    "Une confirmation locale est obligatoire pour cette action.");
        }

        return new ValidatedCommand(id, action, confirmation, expiresAt);
    }
}
