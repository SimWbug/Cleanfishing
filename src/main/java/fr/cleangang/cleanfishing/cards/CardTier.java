package fr.cleangang.cleanfishing.cards;

import org.bukkit.Color;

/** Rareté d'une carte de pouvoir : couleur, poids de tirage et cadre du resource pack. */
public enum CardTier {
    COMMON("Commune", "<#9fd8ff>", Color.fromARGB(215, 24, 48, 72), 55, ''),
    RARE("Rare", "<#c58cff>", Color.fromARGB(215, 52, 26, 82), 30, ''),
    EPIC("Épique", "<#ffcc4d>", Color.fromARGB(220, 82, 52, 10), 15, '');

    private final String label;
    private final String color;
    private final Color background;
    private final int weight;
    private final char frame;

    CardTier(String label, String color, Color background, int weight, char frame) {
        this.label = label;
        this.color = color;
        this.background = background;
        this.weight = weight;
        this.frame = frame;
    }

    public String label() {
        return label;
    }

    /** Couleur MiniMessage (balise ouvrante). */
    public String color() {
        return color;
    }

    /** Fond de la carte sans resource pack. */
    public Color background() {
        return background;
    }

    public int weight() {
        return weight;
    }

    /** Caractère du cadre illustré (police cleanfishing:card). */
    public char frame() {
        return frame;
    }
}
