package fr.cleangang.cleanfishing.item;

import org.bukkit.Material;

import java.util.List;

/** Objets utilisables pendant une partie (boutique de Gertrude ou pêche). */
public enum GameItem {
    DYNAMITE("<red>Dynamite", Material.TNT, List.of(
            "<gray>Clic droit : la lancer dans l'eau.",
            "<gray>Explose et remonte plusieurs poissons d'un coup.",
            "<dark_gray>Sans eau à proximité, elle est gaspillée.")),
    NET("<aqua>Filet de pêche", Material.COBWEB, List.of(
            "<gray>Clic droit en visant l'eau :",
            "<gray>remonte quelques poissons.")),
    HARPOON("<gold>Harpon", Material.BLAZE_ROD, List.of(
            "<gray>Clic droit en visant l'eau :",
            "<gray>remonte un poisson <aqua>rare</aqua> ou mieux.")),
    BAIT("<green>Appât", Material.SPIDER_EYE, List.of(
            "<gray>Clic droit : tes prochaines prises",
            "<gray>mordent beaucoup plus vite.")),
    LUCKY_CHARM("<light_purple>Porte-bonheur", Material.RABBIT_FOOT, List.of(
            "<gray>Clic droit : tes prochaines prises",
            "<gray>ont plus de chances d'être rares.")),
    SAND_TIMER("<yellow>Sablier", Material.CLOCK, List.of(
            "<gray>Clic droit : ajoute du temps",
            "<gray>à la journée en cours pour toute l'équipe.")),
    GOLDEN_LURE("<gold>Leurre légendaire", Material.NAUTILUS_SHELL, List.of(
            "<gray>Clic droit : tes 3 prochaines prises",
            "<gray>sont au moins <light_purple>épiques</light_purple>.",
            "<dark_gray>Article rare de Gertrude.")),
    DYNAMITE_CRATE("<dark_red>Caisse de dynamite", Material.TNT_MINECART, List.of(
            "<gray>Clic droit : la lancer dans l'eau.",
            "<gray>Énorme explosion : remonte 8 à 14 poissons.",
            "<dark_gray>Article rare de Gertrude."));

    private final String displayName;
    private final Material material;
    private final List<String> lore;

    GameItem(String displayName, Material material, List<String> lore) {
        this.displayName = displayName;
        this.material = material;
        this.lore = lore;
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public List<String> lore() {
        return lore;
    }

    public static GameItem parse(String name) {
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
