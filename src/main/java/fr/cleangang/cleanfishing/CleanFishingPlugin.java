package fr.cleangang.cleanfishing;

import fr.cleangang.cleanfishing.arena.ArenaManager;
import fr.cleangang.cleanfishing.codex.CodexManager;
import fr.cleangang.cleanfishing.command.FishCommand;
import fr.cleangang.cleanfishing.fish.FishRegistry;
import fr.cleangang.cleanfishing.game.GameManager;
import fr.cleangang.cleanfishing.game.PlayerStateManager;
import fr.cleangang.cleanfishing.item.ItemFactory;
import fr.cleangang.cleanfishing.listener.ConnectionListener;
import fr.cleangang.cleanfishing.listener.FishingListener;
import fr.cleangang.cleanfishing.listener.GuiListener;
import fr.cleangang.cleanfishing.listener.ItemListener;
import fr.cleangang.cleanfishing.listener.NpcListener;
import fr.cleangang.cleanfishing.listener.ProtectionListener;
import fr.cleangang.cleanfishing.npc.NpcManager;
import fr.cleangang.cleanfishing.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Mannequin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * CleanFishing : mini-jeu de pêche coopératif par journées.
 * <p>
 * Isolation vis-à-vis des autres plugins (FindThePoulet...) : nom de plugin, commande, permissions,
 * clés PDC, dossier de données, scoreboard et listeners sont propres à CleanFishing, et chaque listener
 * ne réagit qu'aux joueurs / entités qui appartiennent à une partie CleanFishing.
 */
public final class CleanFishingPlugin extends JavaPlugin {

    private ItemFactory items;
    private FishRegistry fish;
    private ArenaManager arenas;
    private GameManager games;
    private PlayerStateManager states;
    private CodexManager codex;
    private NpcManager npcs;
    private ShopManager shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new ItemFactory(this);
        fish = new FishRegistry(this);
        arenas = new ArenaManager(this);
        games = new GameManager(this);
        states = new PlayerStateManager(this);
        codex = new CodexManager(this);
        npcs = new NpcManager(this);
        shop = new ShopManager(this);

        fish.load();
        arenas.load();

        PluginCommand command = getCommand("cfish");
        if (command != null) {
            FishCommand handler = new FishCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new FishingListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new NpcListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new ConnectionListener(this), this);

        // nettoie d'éventuels PNJ orphelins déjà chargés (reste d'un crash)
        Bukkit.getScheduler().runTask(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                npcs.removeOrphans(world.getEntitiesByClass(Mannequin.class));
            }
        });
        getLogger().info("CleanFishing activé.");
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdown();
        }
        if (codex != null) {
            codex.flushAll();
        }
    }

    /** Recharge config.yml, fish.yml et arenas.yml (les parties en cours gardent leurs arènes). */
    public void reloadAll() {
        reloadConfig();
        fish.load();
        arenas.load();
    }

    /** Palier de la journée donnée : liste de config, puis croissance géométrique. */
    public int targetFor(int day) {
        List<Integer> list = getConfig().getIntegerList("game.targets");
        if (list.isEmpty()) {
            list = List.of(500, 1500, 3000);
        }
        if (day <= list.size()) {
            return Math.max(1, list.get(day - 1));
        }
        double growth = Math.max(1.0, getConfig().getDouble("game.target-growth-after-list", 1.35));
        double value = list.get(list.size() - 1) * Math.pow(growth, day - list.size());
        return (int) (Math.round(value / 50.0) * 50);
    }

    public ItemFactory items() {
        return items;
    }

    public FishRegistry fish() {
        return fish;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public GameManager games() {
        return games;
    }

    public PlayerStateManager states() {
        return states;
    }

    public CodexManager codex() {
        return codex;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public ShopManager shop() {
        return shop;
    }
}
