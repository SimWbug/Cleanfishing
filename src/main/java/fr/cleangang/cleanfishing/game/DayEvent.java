package fr.cleangang.cleanfishing.game;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Événements qui surviennent pendant une journée. Les réglages (durée, poids, multiplicateurs) sont dans
 * la section {@code events.<clé>} de config.yml ; les valeurs ci-dessous ne servent que de secours.
 */
public enum DayEvent {
    GOLDEN_SHOAL("golden-shoal", "<gold><bold>Banc de poissons dorés !", "<yellow>Chaque poisson vaut le double, foncez !",
            "<gold>✦ Banc doré", 60, 3),
    STORM("storm", "<dark_aqua><bold>Tempête !", "<gray>Ça mord moins vite... mais les gros poissons remontent.",
            "<dark_aqua>⛈ Tempête", 90, 2),
    FOG("fog", "<gray><bold>Brouillard", "<gray>On n'y voit rien, mais les poissons rares s'approchent.",
            "<gray>☁ Brouillard", 90, 2),
    LOW_TIDE("low-tide", "<aqua><bold>Marée basse", "<gray>Ça mord vite et la mer découvre des trésors !",
            "<aqua>≈ Marée basse", 60, 2);

    private final String key;
    private final String title;
    private final String subtitle;
    private final String badge;
    private final int defaultDuration;
    private final double defaultWeight;

    DayEvent(String key, String title, String subtitle, String badge, int defaultDuration, double defaultWeight) {
        this.key = key;
        this.title = title;
        this.subtitle = subtitle;
        this.badge = badge;
        this.defaultDuration = defaultDuration;
        this.defaultWeight = defaultWeight;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    /** Court libellé pour la barre de boss. */
    public String badge() {
        return badge;
    }

    private ConfigurationSection section(FileConfiguration cfg) {
        return cfg.getConfigurationSection("events." + key);
    }

    public boolean enabled(FileConfiguration cfg) {
        ConfigurationSection s = section(cfg);
        return s == null || s.getBoolean("enabled", true);
    }

    public int duration(FileConfiguration cfg) {
        ConfigurationSection s = section(cfg);
        return Math.max(10, s == null ? defaultDuration : s.getInt("duration-seconds", defaultDuration));
    }

    public double weight(FileConfiguration cfg) {
        ConfigurationSection s = section(cfg);
        return Math.max(0, s == null ? defaultWeight : s.getDouble("weight", defaultWeight));
    }

    /** Lit un réglage numérique de l'événement (multiplicateurs). */
    public double number(FileConfiguration cfg, String path, double fallback) {
        ConfigurationSection s = section(cfg);
        return s == null ? fallback : s.getDouble(path, fallback);
    }
}
