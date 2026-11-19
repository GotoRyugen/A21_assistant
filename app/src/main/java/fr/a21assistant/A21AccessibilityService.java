package fr.a21assistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Point d'entrée du futur contrôle d'écran.
 * Aucune action automatique n'est exécutée dans ce prototype.
 * Les commandes devront être limitées, visibles et confirmées par l'utilisateur.
 */
public class A21AccessibilityService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Prototype : aucun contenu d'écran n'est collecté ni transmis.
    }

    @Override public void onInterrupt() {
        // Appelé par Android lorsque le service est interrompu.
    }
}
