package fr.a21assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chaquo.python.android.AndroidPlatform;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int SPEECH_REQUEST = 100;
    private static final int MODEL_PICK_REQUEST = 101;
    private TextView status;
    private TextView memoryStatus;
    private TextToSpeech tts;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private PyObject memoryBridge;
    private volatile boolean memoryReady = false;
    private android.content.SharedPreferences relayPrefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        relayPrefs = getSharedPreferences("a21_relay", MODE_PRIVATE);
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) tts.setLanguage(Locale.FRANCE);
        });

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 60, 36, 36);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(0xFF101827);

        TextView title = new TextView(this);
        title.setText("🤖 A21 Assistant");
        title.setTextSize(28);
        title.setTextColor(0xFF55D6BE);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Assistant vocal — mémoire locale memoryaicm");
        subtitle.setTextSize(16);
        subtitle.setTextColor(0xFFFFFFFF);
        subtitle.setPadding(0, 18, 0, 30);
        layout.addView(subtitle);

        Button model = new Button(this);
        model.setText("Importer un modèle GGUF");
        layout.addView(model);
        model.setOnClickListener(v -> chooseModelFile());

        Button voice = new Button(this);
        voice.setText("🎙 Parler à A21");
        layout.addView(voice);
        voice.setOnClickListener(v -> startVoiceInput());

        Button accessibility = new Button(this);
        accessibility.setText("Configurer l'accessibilité");
        layout.addView(accessibility);
        accessibility.setOnClickListener(v ->
            startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS")));

        Button relayConfig = new Button(this);
        relayConfig.setText("Configurer le relais ChatGPT");
        layout.addView(relayConfig);
        relayConfig.setOnClickListener(v -> configureRelay());

        Button relayPoll = new Button(this);
        relayPoll.setText("Vérifier les commandes du relais");
        layout.addView(relayPoll);
        relayPoll.setOnClickListener(v -> pollRelay());

        Button memory = new Button(this);
        memory.setText("Vérifier la mémoire");
        layout.addView(memory);
        memory.setOnClickListener(v -> refreshMemoryStatus());

        status = new TextView(this);
        status.setText("Prêt. Appuie sur Parler à A21 pour donner une commande.");
        status.setTextColor(0xFF55D6BE);
        status.setTextSize(16);
        status.setPadding(0, 28, 0, 0);
        layout.addView(status);

        memoryStatus = new TextView(this);
        memoryStatus.setText("Initialisation de la mémoire locale...");
        memoryStatus.setTextColor(0xFFFFFFFF);
        memoryStatus.setTextSize(14);
        memoryStatus.setPadding(0, 18, 0, 0);
        layout.addView(memoryStatus);

        setContentView(layout);
        worker.execute(this::initializeMemory);
    }


    private void configureRelay() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(32, 8, 32, 0);
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://adresse-du-relais");
        url.setText(relayPrefs.getString("url", ""));
        fields.addView(url);
        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("Jeton secret de l'appareil");
        token.setText(relayPrefs.getString("device_token", ""));
        fields.addView(token);
        new AlertDialog.Builder(this)
                .setTitle("Connexion au relais A21")
                .setMessage("Ces informations seront conservées dans le stockage privé de l'application. Utilise uniquement une adresse HTTPS.")
                .setView(fields)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String endpoint = url.getText().toString().trim();
                    String secret = token.getText().toString().trim();
                    if (!endpoint.startsWith("https://") || secret.isEmpty()) {
                        status.setText("Adresse HTTPS et jeton obligatoires.");
                        return;
                    }
                    relayPrefs.edit().putString("url", endpoint)
                            .putString("device_token", secret).apply();
                    status.setText("Configuration du relais enregistrée sur ce téléphone.");
                }).show();
    }

    private void pollRelay() {
        String endpoint = relayPrefs.getString("url", "");
        String secret = relayPrefs.getString("device_token", "");
        if (endpoint.isEmpty() || secret.isEmpty()) {
            status.setText("Configure d'abord le relais A21.");
            configureRelay();
            return;
        }
        status.setText("Vérification du relais…");
        worker.execute(() -> {
            try {
                A21RelayClient client = new A21RelayClient(endpoint, secret);
                JSONObject response = client.poll();
                JSONObject command = response.optJSONObject("command");
                if (command == null) {
                    runOnUiThread(() -> status.setText("Relais joignable : aucune commande en attente."));
                    return;
                }
                RemoteCommandPolicy.ValidatedCommand validated =
                        RemoteCommandPolicy.validate(command.toString(), System.currentTimeMillis());
                runOnUiThread(() -> handleRemoteCommand(client, validated));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Relais indisponible ou commande refusée : " + e.getMessage()));
            }
        });
    }

    private void handleRemoteCommand(A21RelayClient client,
                                     RemoteCommandPolicy.ValidatedCommand command) {
        if (command.action == RemoteCommandPolicy.Action.STATUS ||
                command.action == RemoteCommandPolicy.Action.LIST_CAPABILITIES) {
            worker.execute(() -> reportRemoteResult(client, command.id, "completed",
                    command.action == RemoteCommandPolicy.Action.STATUS
                            ? "A21 est ouvert et prêt." : "Réglages autorisés : Wi-Fi, Bluetooth, batterie, applications, paramètres."));
            status.setText("Demande d'information reçue.");
            return;
        }
        String label;
        switch (command.action) {
            case OPEN_WIFI: label = "les réglages Wi-Fi"; break;
            case OPEN_BLUETOOTH: label = "les réglages Bluetooth"; break;
            case OPEN_BATTERY: label = "les réglages de batterie"; break;
            case OPEN_APPLICATIONS: label = "les réglages des applications"; break;
            case OPEN_SETTINGS: label = "les paramètres Android"; break;
            default:
                worker.execute(() -> reportRemoteResult(client, command.id, "rejected", "Action non prise en charge."));
                return;
        }
        if (command.expiresAt <= System.currentTimeMillis()) {
            worker.execute(() -> reportRemoteResult(client, command.id, "rejected", "Commande expirée."));
            status.setText("Commande expirée.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Autoriser une action distante ?")
                .setMessage("ChatGPT demande à A21 d'ouvrir " + label + ".\n\nRien ne sera fait sans ton accord.")
                .setNegativeButton("Refuser", (dialog, which) -> {
                    worker.execute(() -> reportRemoteResult(client, command.id, "rejected", "Refus local."));
                    status.setText("Commande refusée.");
                })
                .setPositiveButton("Autoriser", (dialog, which) -> {
                    if (command.expiresAt <= System.currentTimeMillis()) {
                        worker.execute(() -> reportRemoteResult(client, command.id, "rejected", "Commande expirée avant confirmation."));
                        status.setText("Commande expirée.");
                        return;
                    }
                    String action = androidAction(command.action);
                    try {
                        startActivity(new Intent(action));
                        status.setText("Ouverture de " + label + ".");
                        worker.execute(() -> reportRemoteResult(client, command.id, "completed", "Réglage ouvert après confirmation locale."));
                    } catch (Exception e) {
                        worker.execute(() -> reportRemoteResult(client, command.id, "failed", "Impossible d'ouvrir ce réglage."));
                        status.setText("Impossible d'ouvrir " + label + ".");
                    }
                }).show();
    }

    private String androidAction(RemoteCommandPolicy.Action action) {
        switch (action) {
            case OPEN_WIFI: return Settings.ACTION_WIFI_SETTINGS;
            case OPEN_BLUETOOTH: return Settings.ACTION_BLUETOOTH_SETTINGS;
            case OPEN_BATTERY: return Settings.ACTION_BATTERY_SAVER_SETTINGS;
            case OPEN_APPLICATIONS: return Settings.ACTION_APPLICATION_SETTINGS;
            case OPEN_SETTINGS: return Settings.ACTION_SETTINGS;
            default: throw new IllegalArgumentException("Action non autorisée.");
        }
    }

    private void reportRemoteResult(A21RelayClient client, String id, String result, String message) {
        try {
            client.sendResult(id, result, message);
        } catch (Exception e) {
            runOnUiThread(() -> status.setText("Action traitée localement, mais résultat non transmis au relais."));
        }
    }

    private void initializeMemory() {
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(getApplicationContext()));
            }
            memoryBridge = Python.getInstance().getModule("a21_memory_bridge");
            String home = getFilesDir().getAbsolutePath() + "/memoryaicm";
            String result = memoryBridge.callAttr("initialize", home).toString();
            memoryReady = true;
            runOnUiThread(() -> memoryStatus.setText("Mémoire locale active. " + statusSummary(result)));
        } catch (Exception e) {
            memoryReady = false;
            runOnUiThread(() -> memoryStatus.setText("Mémoire indisponible : " + e.getMessage()));
        }
    }

    private String statusSummary(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optInt("facts_active", 0) + " souvenir(s) actif(s).";
        } catch (Exception e) {
            return "Prête.";
        }
    }

    private void refreshMemoryStatus() {
        worker.execute(() -> {
            try {
                if (!memoryReady || memoryBridge == null) {
                    runOnUiThread(() -> memoryStatus.setText("La mémoire n'est pas encore prête."));
                    return;
                }
                String result = memoryBridge.callAttr("status").toString();
                String summary = statusSummary(result);
                runOnUiThread(() -> memoryStatus.setText("Mémoire locale active. " + summary));
            } catch (Exception e) {
                runOnUiThread(() -> memoryStatus.setText("Erreur mémoire : " + e.getMessage()));
            }
        });
    }

    private void chooseModelFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-gguf"});
        startActivityForResult(intent, MODEL_PICK_REQUEST);
    }

    private void importModel(Uri uri) {
        worker.execute(() -> {
            String name = "modele.gguf";
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) name = cursor.getString(index);
                }
            } catch (Exception ignored) { }
            if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
                runOnUiThread(() -> status.setText("Fichier refusé : sélectionne un fichier .gguf."));
                return;
            }
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (name.equals(".gguf") || name.equals("..gguf")) name = "modele.gguf";
            File destination = new File(getFilesDir(), "models/" + name);
            try {
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Impossible de créer le dossier des modèles.");
                }
                try (InputStream input = getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(destination)) {
                    if (input == null) throw new IllegalStateException("Fichier illisible.");
                    byte[] buffer = new byte[1024 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                final String importedName = name;
                final long bytes = destination.length();
                runOnUiThread(() -> status.setText("Modèle importé : " + importedName +
                        " (" + android.text.format.Formatter.formatFileSize(this, bytes) +
                        "). Le moteur IA GGUF n'est pas encore intégré."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Échec de l'import : " + e.getMessage()));
            }
        });
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Dis ta commande");
        try {
            startActivityForResult(intent, SPEECH_REQUEST);
        } catch (Exception e) {
            status.setText("Reconnaissance vocale indisponible sur cet appareil.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MODEL_PICK_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            importModel(data.getData());
            return;
        }
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String command = results.get(0);
                status.setText("Commande entendue : " + command + "\nTraitement...");
                worker.execute(() -> processVoiceCommand(command));
            }
        }
    }

    private void processVoiceCommand(String command) {
        try {
            if (!memoryReady || memoryBridge == null) {
                runOnUiThread(() -> memoryStatus.setText("La mémoire démarre encore. Réessaie dans un instant."));
                return;
            }
            String normalized = command.trim().toLowerCase(Locale.FRANCE);

            if (normalized.equals("aide") || normalized.equals("que peux-tu faire") ||
                    normalized.equals("qu'est-ce que tu peux faire")) {
                String help = "Je peux mémoriser une information, retrouver un souvenir, " +
                        "ou ouvrir certains réglages Android. Essaie : « souviens-toi que… », " +
                        "« rappelle-moi… », « ouvre le Wi-Fi », « ouvre le Bluetooth », " +
                        "« ouvre les paramètres » ou « ouvre la batterie ».";
                showResponse(help, help);
                return;
            }

            String settingAction = null;
            String settingLabel = null;
            if (normalized.contains("ouvre") || normalized.contains("ouvrir") ||
                    normalized.startsWith("va dans")) {
                if (normalized.contains("wifi") || normalized.contains("wi-fi")) {
                    settingAction = Settings.ACTION_WIFI_SETTINGS;
                    settingLabel = "les réglages Wi-Fi";
                } else if (normalized.contains("bluetooth")) {
                    settingAction = Settings.ACTION_BLUETOOTH_SETTINGS;
                    settingLabel = "les réglages Bluetooth";
                } else if (normalized.contains("batterie")) {
                    settingAction = Settings.ACTION_BATTERY_SAVER_SETTINGS;
                    settingLabel = "les réglages de batterie";
                } else if (normalized.contains("applications") || normalized.contains(" applis")) {
                    settingAction = Settings.ACTION_APPLICATION_SETTINGS;
                    settingLabel = "les réglages des applications";
                } else if (normalized.contains("paramètres") || normalized.contains("parametres") ||
                        normalized.contains("réglages") || normalized.contains("reglages")) {
                    settingAction = Settings.ACTION_SETTINGS;
                    settingLabel = "les paramètres Android";
                }
            }
            if (settingAction != null) {
                final String action = settingAction;
                final String label = settingLabel;
                runOnUiThread(() -> {
                    try {
                        Intent intent = new Intent(action);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        status.setText("Ouverture de " + label + ".");
                        if (tts != null) tts.speak("J'ouvre " + label + ".", TextToSpeech.QUEUE_FLUSH, null, "a21-action");
                    } catch (Exception e) {
                        status.setText("Impossible d'ouvrir " + label + " sur cet appareil.");
                    }
                });
                return;
            }

            if (normalized.startsWith("rappelle") ||
                    normalized.startsWith("que sais-tu sur") ||
                    normalized.startsWith("qu'est-ce que tu sais sur")) {
                String query = command.replaceFirst("(?i)^\\s*(rappelle-moi|rappelle|que sais-tu sur|qu'est-ce que tu sais sur)\\s*", "").trim();
                if (query.isEmpty()) query = "souvenirs récents";
                String json = memoryBridge.callAttr("recall", query).toString();
                JSONObject obj = new JSONObject(json);
                StringBuilder answer = new StringBuilder();
                appendMemories(answer, obj.optJSONArray("notes"));
                appendMemories(answer, obj.optJSONArray("preferences"));
                String response = answer.length() == 0
                        ? "Je n'ai pas trouvé de souvenir correspondant."
                        : answer.toString();
                showResponse("Souvenirs : " + response, response);
            } else if (normalized.startsWith("oublie")) {
                String query = command.replaceFirst("(?i)^\\s*oublie(-moi)?\\s*", "").trim();
                if (query.isEmpty()) {
                    showResponse("Dis : oublie suivi du souvenir à effacer.", "Précise le souvenir à oublier.");
                    return;
                }
                String json = memoryBridge.callAttr("forget", query).toString();
                JSONObject obj = new JSONObject(json);
                String response = obj.optString("summary", "Demande d'oubli traitée.");
                showResponse("Oubli : " + response, response);
            } else {
                String memoryText = command.replaceFirst("(?i)^\\s*(souviens-toi que|souviens-toi|mémorise que|memorise que)\\s*", "").trim();
                String json = memoryBridge.callAttr("remember", memoryText).toString();
                JSONObject obj = new JSONObject(json);
                String response = obj.optString("summary", "Souvenir enregistré.");
                showResponse("Souvenir : " + response, "J'ai enregistré cette information.");
            }
            refreshMemoryStatus();
        } catch (Exception e) {
            runOnUiThread(() -> memoryStatus.setText("Erreur : " + e.getMessage()));
        }
    }

    private void appendMemories(StringBuilder output, JSONArray items) {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String text = item.optString("text", "").trim();
            if (text.isEmpty()) continue;
            if (output.length() > 0) output.append("\n");
            output.append("• ").append(text);
        }
    }

    private void showResponse(String visibleText, String spokenText) {
        runOnUiThread(() -> {
            status.setText(visibleText);
            if (tts != null) tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "a21-response");
        });
    }

    @Override protected void onDestroy() {
        worker.execute(() -> {
            try {
                if (memoryBridge != null) memoryBridge.callAttr("close");
            } catch (Exception ignored) { }
        });
        worker.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
