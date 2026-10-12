package fr.cleangang.cleanfishing.fish;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Rarity {
    COMMON("Commun", "<white>", NamedTextColor.WHITE),
    UNCOMMON("Peu commun", "<green>", NamedTextColor.GREEN),
    RARE("Rare", "<aqua>", NamedTextColor.AQUA),
    EPIC("Épique", "<light_purple>", NamedTextColor.LIGHT_PURPLE),
    LEGENDARY("Légendaire", "<gold>", NamedTextColor.GOLD);

    private final String label;
    private final String tag;
    private final NamedTextColor color;

    Rarity(String label, String tag, NamedTextColor color) {
        this.label = label;
        this.tag = tag;
        this.color = color;
    }

    public String label() {
        return label;
    }

    /** Balise MiniMessage ouvrante, ex. "&lt;aqua&gt;". */
    public String tag() {
        return tag;
    }

    public NamedTextColor color() {
        return color;
    }

    public boolean atLeast(Rarity other) {
        return ordinal() >= other.ordinal();
    }

    public static Rarity parse(String name, Rarity fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
