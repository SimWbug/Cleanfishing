package fr.cleangang.cleanfishing.arena;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stockage des arènes dans arenas.yml. */
public final class ArenaManager {

    private final CleanFishingPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            Arena arena = new Arena(name);
            arena.setPositions(
                    read(s.getConfigurationSection("spawn")),
                    read(s.getConfigurationSection("lobby")),
                    read(s.getConfigurationSection("gertrude")),
                    read(s.getConfigurationSection("francis")));
            arena.radius(s.getInt("radius", 0));
            arenas.put(key(name), arena);
        }
        plugin.getLogger().info(arenas.size() + " arène(s) chargée(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.name();
            yaml.createSection(base);
            write(yaml, base + ".spawn", arena.spawnPos());
            write(yaml, base + ".lobby", arena.lobbyPos());
            write(yaml, base + ".gertrude", arena.gertrudePos());
            write(yaml, base + ".francis", arena.francisPos());
            if (arena.radius() > 0) {
                yaml.set(base + ".radius", arena.radius());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible d'écrire arenas.yml : " + ex.getMessage());
        }
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(key(name));
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arenas.put(key(name), arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        boolean removed = arenas.remove(key(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Arena a : arenas.values()) {
            names.add(a.name());
        }
        return names;
    }

    private static String key(String name) {
        return name.toLowerCase();
    }

    private static void write(YamlConfiguration yaml, String path, Arena.Pos pos) {
        if (pos == null) {
            return;
        }
        yaml.set(path + ".world", pos.world());
        yaml.set(path + ".x", pos.x());
        yaml.set(path + ".y", pos.y());
        yaml.set(path + ".z", pos.z());
        yaml.set(path + ".yaw", (double) pos.yaw());
        yaml.set(path + ".pitch", (double) pos.pitch());
    }

    private static Arena.Pos read(ConfigurationSection s) {
        if (s == null || !s.contains("world")) {
            return null;
        }
        return new Arena.Pos(s.getString("world", ""),
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }
}
