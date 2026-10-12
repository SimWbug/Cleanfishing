package fr.cleangang.cleanfishing.fish;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class FishRegistry {

    private final CleanFishingPlugin plugin;
    private final Map<String, FishType> fish = new LinkedHashMap<>();

    public FishRegistry(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        fish.clear();
        File file = new File(plugin.getDataFolder(), "fish.yml");
        if (!file.exists()) {
            plugin.saveResource("fish.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("fish");
        if (section == null) {
            plugin.getLogger().warning("fish.yml : section 'fish' manquante.");
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Material material = Material.matchMaterial(s.getString("material", "COD"));
            if (material == null) {
                plugin.getLogger().warning("fish.yml : matériau inconnu pour " + id + ", COD utilisé.");
                material = Material.COD;
            }
            int min = Math.max(1, s.getInt("min-size", 10));
            int max = Math.max(min, s.getInt("max-size", min));
            fish.put(id, new FishType(
                    id,
                    s.getString("name", id),
                    Rarity.parse(s.getString("rarity"), Rarity.COMMON),
                    material,
                    s.getString("item-model", ""),
                    Math.max(1, s.getInt("value", 10)),
                    min,
                    max,
                    Math.max(0.01, s.getDouble("weight", 1.0)),
                    s.getString("description", "")));
        }
        plugin.getLogger().info(fish.size() + " poissons chargés.");
    }

    public Collection<FishType> all() {
        return Collections.unmodifiableCollection(fish.values());
    }

    public FishType get(String id) {
        return fish.get(id);
    }

    /** Liste triée par rareté puis par nom, pour le codex. */
    public List<FishType> sorted() {
        List<FishType> list = new ArrayList<>(fish.values());
        list.sort(Comparator.comparing(FishType::rarity).thenComparing(FishType::name));
        return list;
    }

    /**
     * Tire un poisson.
     *
     * @param highMultiplier multiplicateur de poids pour les raretés RARE et plus (porte-bonheur)
     * @param min            rareté minimale autorisée
     */
    public FishType roll(Random random, double highMultiplier, Rarity min) {
        double total = 0;
        for (FishType f : fish.values()) {
            if (f.rarity().atLeast(min)) {
                total += weightOf(f, highMultiplier);
            }
        }
        if (total <= 0) {
            return fish.values().stream().findFirst().orElseThrow();
        }
        double pick = random.nextDouble() * total;
        FishType last = null;
        for (FishType f : fish.values()) {
            if (!f.rarity().atLeast(min)) {
                continue;
            }
            last = f;
            pick -= weightOf(f, highMultiplier);
            if (pick <= 0) {
                return f;
            }
        }
        return last;
    }

    private double weightOf(FishType f, double highMultiplier) {
        return f.rarity().atLeast(Rarity.RARE) ? f.weight() * highMultiplier : f.weight();
    }
}
