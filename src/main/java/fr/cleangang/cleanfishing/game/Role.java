package fr.cleangang.cleanfishing.game;

import fr.cleangang.cleanfishing.item.GameItem;
import org.bukkit.Material;

import java.util.List;

/** Spécialités choisies dans la salle d'attente : un petit bonus et un objet offert au début de chaque journée. */
public enum Role {
    HARPOONER("<gold>Harponneur", Material.BLAZE_ROD, GameItem.HARPOON,
            "<gray>Reçoit <white>1 harpon<gray> chaque journée.",
            "<gray>Harpon rechargé <white>2x plus vite<gray>."),
    NETTER("<aqua>Fileteur", Material.COBWEB, GameItem.NET,
            "<gray>Reçoit <white>1 filet<gray> chaque journée.",
            "<gray>Ses filets remontent <white>+1 poisson<gray>",
            "<gray>et sèchent plus vite."),
    BLASTER("<red>Artificier", Material.TNT, GameItem.DYNAMITE,
            "<gray>Reçoit <white>1 dynamite<gray> chaque journée.",
            "<gray>Ses explosions remontent <white>+2 poissons<gray>.");

    private final String displayName;
    private final Material icon;
    private final GameItem starter;
    private final List<String> lore;

    Role(String displayName, Material icon, GameItem starter, String... lore) {
        this.displayName = displayName;
        this.icon = icon;
        this.starter = starter;
        this.lore = List.of(lore);
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /** Objet offert au début de chaque journée. */
    public GameItem starter() {
        return starter;
    }

    public List<String> lore() {
        return lore;
    }

    public static Role parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
