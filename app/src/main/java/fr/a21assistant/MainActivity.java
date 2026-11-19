package fr.a21assistant;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
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
    private TextView status;
    private TextView memoryStatus;
    private TextToSpeech tts;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private PyObject memoryBridge;
    private volatile boolean memoryReady = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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

        Button voice = new Button(this);
        voice.setText("🎙 Parler à A21");
        layout.addView(voice);
        voice.setOnClickListener(v -> startVoiceInput());

        Button accessibility = new Button(this);
        accessibility.setText("Configurer l'accessibilité");
        layout.addView(accessibility);
        accessibility.setOnClickListener(v ->
            startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS")));

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
                runOnUiThread(() -> memoryStatus.setText("Mémoire en cours de démarrage. Réessaie dans un instant."));
                return;
            }
            String normalized = command.trim().toLowerCase(Locale.FRANCE);
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
                String json = memoryBridge.callAttr("remember", command).toString();
                JSONObject obj = new JSONObject(json);
                String response = obj.optString("summary", "Souvenir enregistré.");
                showResponse("Commande : " + command + "\nMémoire : " + response, "J'ai enregistré cette information.");
            }
            refreshMemoryStatus();
        } catch (Exception e) {
            runOnUiThread(() -> memoryStatus.setText("Erreur mémoire : " + e.getMessage()));
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
