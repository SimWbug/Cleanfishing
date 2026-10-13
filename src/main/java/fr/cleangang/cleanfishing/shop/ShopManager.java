package fr.cleangang.cleanfishing.shop;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.item.GameItem;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Tire les offres du jour à partir du pool défini dans config.yml (section shop). */
public final class ShopManager {

    private record Entry(GameItem item, int price, double weight, int amount) {
    }

    private final CleanFishingPlugin plugin;

    public ShopManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    public List<ShopOffer> roll(Random random) {
        List<Entry> pool = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.pool");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                GameItem item = GameItem.parse(key);
                ConfigurationSection s = section.getConfigurationSection(key);
                if (item == null || s == null) {
                    plugin.getLogger().warning("config.yml : article de boutique inconnu '" + key + "'.");
                    continue;
                }
                int amount = Math.max(1, plugin.getConfig().getInt("shop.stack-size." + item.name(), 1));
                pool.add(new Entry(item, Math.max(1, s.getInt("price", 100)), Math.max(0.01, s.getDouble("weight", 1)), amount));
            }
        }
        int wanted = Math.max(1, Math.min(5,plugin.getConfig().getInt("game.shop-offers-per-day", 5)));
        List<ShopOffer> offers = new ArrayList<>();
        while (offers.size() < wanted && !pool.isEmpty()) {
            double total = pool.stream().mapToDouble(Entry::weight).sum();
            double pick = random.nextDouble() * total;
            Entry chosen = pool.get(pool.size() - 1);
            for (Entry e : pool) {
                pick -= e.weight();
                if (pick <= 0) {
                    chosen = e;
                    break;
                }
            }
            pool.remove(chosen);
            offers.add(new ShopOffer(chosen.item(), chosen.amount(), chosen.price()));
        }
        return offers;
    }
}
