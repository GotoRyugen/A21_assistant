package fr.a21assistant;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int SPEECH_REQUEST = 100;
    private TextView status;
    private TextToSpeech tts;

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
        subtitle.setText("Prototype Android — commandes vocales");
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

        status = new TextView(this);
        status.setText("Prêt. L'écoute démarre uniquement quand tu appuies sur le bouton.");
        status.setTextColor(0xFF55D6BE);
        status.setTextSize(16);
        status.setPadding(0, 28, 0, 0);
        layout.addView(status);
        setContentView(layout);
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
                status.setText("Commande entendue : " + command
                        + "\nLa connexion ChatGPT et l'exécution des commandes seront ajoutées ensuite.");
                if (tts != null) tts.speak("J'ai entendu : " + command,
                        TextToSpeech.QUEUE_FLUSH, null, "a21-confirm");
            }
        }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
