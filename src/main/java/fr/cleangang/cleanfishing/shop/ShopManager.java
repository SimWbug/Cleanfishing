package fr.cleangang.cleanfishing.shop;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.item.GameItem;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tire l'étal du jour à partir des pools de config.yml (section shop) :
 * des articles courants (dont un en promotion) et, en dernier, un article rare en un seul exemplaire.
 */
public final class ShopManager {

    private record Entry(GameItem item, int price, double weight, int amount) {
    }

    private final CleanFishingPlugin plugin;

    public ShopManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    private List<Entry> pool(String path) {
        List<Entry> pool = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            return pool;
        }
        for (String key : section.getKeys(false)) {
            GameItem item = GameItem.parse(key);
            ConfigurationSection s = section.getConfigurationSection(key);
            if (item == null || s == null) {
                plugin.getLogger().warning("config.yml : article de boutique inconnu '" + key + "' (" + path + ").");
                continue;
            }
            int amount = Math.max(1, s.getInt("amount", plugin.getConfig().getInt("shop.stack-size." + item.name(), 1)));
            pool.add(new Entry(item, Math.max(1, s.getInt("price", 100)), Math.max(0.01, s.getDouble("weight", 1)), amount));
        }
        return pool;
    }

    private static Entry pick(List<Entry> pool, Random random) {
        double total = pool.stream().mapToDouble(Entry::weight).sum();
        double roll = random.nextDouble() * total;
        for (Entry e : pool) {
            roll -= e.weight();
            if (roll <= 0) {
                return e;
            }
        }
        return pool.get(pool.size() - 1);
    }

    public List<ShopOffer> roll(Random random) {
        int wanted = Math.max(1, Math.min(5, plugin.getConfig().getInt("game.shop-offers-per-day", 5)));
        List<Entry> rarePool = pool("shop.rare-pool");
        boolean withRare = !rarePool.isEmpty() && plugin.getConfig().getBoolean("shop.rare.enabled", true) && wanted >= 2;
        int normal = withRare ? wanted - 1 : wanted;

        // Chaque lot ne s'achète qu'une fois (stock 1, partagé par l'équipe). Le même objet peut apparaître
        // plusieurs fois dans la journée, au plus shop.max-same-item fois (2 appâts, 2 lots de TNT...).
        List<Entry> pool = pool("shop.pool");
        int maxSame = Math.max(1, plugin.getConfig().getInt("shop.max-same-item", 2));
        int stock = plugin.getConfig().getBoolean("shop.single-purchase", true) ? 1 : -1;
        java.util.Map<GameItem, Integer> counts = new java.util.EnumMap<>(GameItem.class);
        List<ShopOffer> offers = new ArrayList<>();
        while (offers.size() < normal && !pool.isEmpty()) {
            Entry chosen = pick(pool, random);
            int n = counts.merge(chosen.item(), 1, Integer::sum);
            if (n >= maxSame) {
                pool.remove(chosen);
            }
            offers.add(new ShopOffer(chosen.item(), chosen.amount(), chosen.price(), 0, false, stock));
        }

        // une promotion par jour
        int discount = plugin.getConfig().getInt("shop.deal.percent", 30);
        if (plugin.getConfig().getBoolean("shop.deal.enabled", true) && discount > 0 && !offers.isEmpty()) {
            int index = random.nextInt(offers.size());
            ShopOffer o = offers.get(index);
            offers.set(index, new ShopOffer(o.item(), o.amount(), o.basePrice(), discount, false, o.stock()));
        }

        if (withRare) {
            Entry rare = pick(rarePool, random);
            int rareStock = Math.max(1, plugin.getConfig().getInt("shop.rare.stock", 1));
            offers.add(new ShopOffer(rare.item(), rare.amount(), rare.price(), 0, true, rareStock));
        }
        return offers;
    }
}
