package fr.cleangang.cleanfishing.pack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Titres d'interface pour le resource pack : un « caractère » qui dessine tout le fond de l'interface,
 * puis du texte placé au pixel près (bulle de dialogue, tableau de Francis) grâce à des polices décalées.
 * La géométrie doit correspondre à pack-src/build_pack.py.
 */
public final class PackUi {

    public static final char SHOP_BACKGROUND = '';
    public static final char FRANCIS_BACKGROUND = '';

    /** Zone de texte de la bulle (x en pixels d'interface). */
    public static final int BUBBLE_X1 = 68;
    public static final int BUBBLE_X2 = 166;
    /** Lignes 1 à 3 : bulle ; lignes 4 à 6 : tableau de Francis. */
    public static final int LINES = 6;

    private static final Key GUI_FONT = Key.key("cleanfishing", "gui");
    /** Avance du caractère de fond : 176 pixels + 1. */
    private static final int BACKGROUND_ADVANCE = 177;
    /** Le titre commence à 8 pixels du bord gauche de l'interface. */
    private static final int TITLE_ORIGIN = 8;
    private static final int UNKNOWN_WIDTH = 6;
    private static final Map<Integer, Integer> WIDTHS = loadWidths();

    private PackUi() {
    }

    /** Une ligne de texte à afficher (police lineN). */
    public record Line(int index, String text, TextColor color, int x1, int x2, boolean centered) {
    }

    private static Map<Integer, Integer> loadWidths() {
        Map<Integer, Integer> map = new HashMap<>();
        try (InputStream in = PackUi.class.getResourceAsStream("/font-widths.txt")) {
            if (in == null) {
                return map;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.trim().split(" ");
                if (parts.length == 2) {
                    map.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // table absente : largeur par défaut
        }
        return map;
    }

    /** Remplace les caractères que la police du pack ne sait pas afficher. */
    public static String sanitize(String text) {
        if (WIDTHS.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (cp == ' ' || WIDTHS.containsKey(cp)) {
                sb.appendCodePoint(cp);
            } else if (cp == '’') {
                sb.append('\'');
            } else {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    public static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += cp == ' ' ? 4 : WIDTHS.getOrDefault(cp, UNKNOWN_WIDTH);
            i += Character.charCount(cp);
        }
        return w;
    }

    /** Coupe un texte en lignes de largeur maximale (pixels), avec « ... » si tout ne tient pas. */
    public static List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] words = sanitize(text).split(" ");
        int i = 0;
        for (; i < words.length; i++) {
            String word = words[i];
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (lines.size() == maxLines) {
                break;
            }
            current.append(word);
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        if (i < words.length && !lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            while (!last.isEmpty() && width(last + "...") > maxWidth) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(lines.size() - 1, last.stripTrailing() + "...");
        }
        return lines;
    }

    /** Décalage horizontal (positif ou négatif) composé avec les caractères d'espacement de la police gui. */
    public static String offset(int pixels) {
        StringBuilder sb = new StringBuilder();
        int value = Math.abs(pixels);
        char base = pixels < 0 ? '' : '';
        for (int bit = 8; bit >= 0; bit--) {
            int step = 1 << bit;
            while (value >= step) {
                sb.append((char) (base + bit));
                value -= step;
            }
        }
        return sb.toString();
    }

    /**
     * Titre complet : fond de l'interface puis les lignes de texte.
     * Le fond est blanc (sinon la couleur du titre le teinterait).
     */
    public static Component title(char background, List<Line> lines) {
        TextComponent.Builder title = Component.text();
        title.append(Component.text(offset(-TITLE_ORIGIN)).font(GUI_FONT));
        title.append(Component.text(String.valueOf(background)).font(GUI_FONT).color(NamedTextColor.WHITE));
        int cursor = BACKGROUND_ADVANCE;
        for (Line line : lines) {
            if (line.text() == null || line.text().isEmpty() || line.index() < 1 || line.index() > LINES) {
                continue;
            }
            String text = sanitize(line.text());
            int w = width(text);
            int x = line.centered() ? (line.x1() + line.x2()) / 2 - w / 2 : line.x1();
            title.append(Component.text(offset(x - cursor)).font(GUI_FONT));
            title.append(Component.text(text).font(Key.key("cleanfishing", "line" + line.index())).color(line.color()));
            cursor = x + w;
        }
        return title.build();
    }

    /** Bulle de dialogue : jusqu'à 3 lignes centrées. */
    public static List<Line> bubble(String text, TextColor color) {
        List<String> wrapped = wrap(text, BUBBLE_X2 - BUBBLE_X1, 3);
        List<Line> lines = new ArrayList<>();
        int first = wrapped.size() >= 3 ? 1 : wrapped.size() == 2 ? 1 : 2;
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(new Line(first + i, wrapped.get(i), color, BUBBLE_X1, BUBBLE_X2, true));
        }
        return lines;
    }
}
