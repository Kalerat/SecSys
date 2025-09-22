package org.kgames.client;

/**
 * JavaFX Launcher class to work around module path issues when running from a fat JAR.
 * This class serves as the main entry point and launches the actual JavaFX Application.
 * 
 * This is necessary because when JavaFX is bundled in a fat JAR, the module system
 * can have issues detecting JavaFX modules correctly. Using a separate launcher
 * class that doesn't extend Application helps avoid these issues.
 */
public class Launcher {
    
    /**
     * Main entry point for the fat JAR.
     * This method launches the JavaFX application indirectly.
     * 
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        // Launch the JavaFX application
        MainFrame.main(args);
    }
}