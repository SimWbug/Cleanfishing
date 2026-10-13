package fr.cleangang.cleanfishing.game;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Améliorations permanentes pour la partie : achetées chez Gertrude avec la cagnotte, propres au joueur qui les
 * achète et conservées d'une journée à l'autre (perdues à la fin de la partie).
 */
public enum Upgrade {
    ROD("rod", "<aqua>Canne renforcée", Material.FISHING_ROD,
            "<gray>Ça mord plus vite et les poissons", "<gray>rares mordent plus souvent."),
    BOOTS("boots", "<yellow>Bottes de marin", Material.LEATHER_BOOTS,
            "<gray>Tu cours plus vite sur l'île", "<gray>(vitesse permanente)."),
    BAG("bag", "<green>Grand sac", Material.BUNDLE,
            "<gray>Chance de remonter un poisson", "<gray>en plus à chaque prise.");

    private final String key;
    private final String name;
    private final Material icon;
    private final List<String> lore;

    Upgrade(String key, String name, Material icon, String... lore) {
        this.key = key;
        this.name = name;
        this.icon = icon;
        this.lore = List.of(lore);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return name;
    }

    public Material icon() {
        return icon;
    }

    public List<String> lore() {
        return lore;
    }

    /** Prix de chaque niveau (la taille de la liste = niveau maximum). */
    public List<Integer> prices(FileConfiguration cfg) {
        List<Integer> prices = cfg.getIntegerList("upgrades." + key + ".prices");
        return prices.isEmpty() ? List.of(400, 900) : prices;
    }

    public int maxLevel(FileConfiguration cfg) {
        return prices(cfg).size();
    }

    /** Prix du niveau suivant, ou -1 si le niveau maximum est atteint. */
    public int nextPrice(FileConfiguration cfg, int currentLevel) {
        List<Integer> prices = prices(cfg);
        return currentLevel >= prices.size() ? -1 : Math.max(0, prices.get(currentLevel));
    }

    /** Effet par niveau (fraction : 0.15 = 15 %). */
    public double perLevel(FileConfiguration cfg, String path, double fallback) {
        return cfg.getDouble("upgrades." + key + "." + path, fallback);
    }
}
