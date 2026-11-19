package fr.a21assistant;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Client HTTPS minimal du relais A21.
 * Les appels sont synchrones : les effectuer exclusivement depuis un thread de travail.
 * Aucune commande n'est exécutée ici.
 */
final class A21RelayClient {
    private final String baseUrl;
    private final String deviceToken;

    A21RelayClient(String baseUrl, String deviceToken) {
        if (baseUrl == null || !baseUrl.trim().startsWith("https://")) {
            throw new IllegalArgumentException("L'adresse du relais doit commencer par https://");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        this.baseUrl = normalized;
        this.deviceToken = deviceToken == null ? "" : deviceToken.trim();
        if (this.deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Le jeton de l'appareil est manquant.");
        }
    }

    JSONObject poll() throws Exception {
        return request("/device/poll", null);
    }

    JSONObject sendResult(String commandId, String resultStatus, String message) throws Exception {
        JSONObject body = new JSONObject();
        body.put("command_id", commandId);
        body.put("status", resultStatus);
        body.put("result", message == null ? "" : message);
        return request("/device/result", body);
    }

    private JSONObject request(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Authorization", "Bearer " + deviceToken);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder response = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Le relais a répondu HTTP " + code + ".");
            }
            return new JSONObject(response.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
