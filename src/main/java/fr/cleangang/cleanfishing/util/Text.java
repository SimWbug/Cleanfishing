package fr.cleangang.cleanfishing.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Petits helpers MiniMessage. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** Texte d'item : pas d'italique par défaut. */
    public static Component item(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Message préfixé. */
    public static Component msg(String s) {
        return MM.deserialize("<dark_aqua>[<aqua>Pêche<dark_aqua>] <gray>" + s);
    }

    /** Échappe un texte libre (pseudo...) avant de l'insérer dans une chaîne MiniMessage. */
    public static String esc(String s) {
        return MM.escapeTags(s);
    }

    public static String clock(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
