package com.sqlitegui;

/**
 * Plain entry point for the shaded/fat jar. Launching the JavaFX Application
 * class directly from a jar that bundles the JavaFX runtime triggers the
 * "JavaFX runtime components are missing" check; delegating through a class
 * that does NOT extend Application sidesteps it.
 */
public final class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
