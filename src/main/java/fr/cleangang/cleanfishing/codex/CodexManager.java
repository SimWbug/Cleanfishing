package fr.cleangang.cleanfishing.codex;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.fish.FishType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Codex persistant par joueur : un fichier codex/&lt;uuid&gt;.yml. */
public final class CodexManager {

    private final CleanFishingPlugin plugin;
    private final File dir;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    public CodexManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "codex");
    }

    private YamlConfiguration load(UUID id) {
        return cache.computeIfAbsent(id, key -> {
            File file = new File(dir, key + ".yml");
            return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        });
    }

    /** Enregistre une prise. Renvoie true si c'est la première fois que ce poisson est pêché. */
    public boolean record(UUID id, FishType fish, int size) {
        YamlConfiguration y = load(id);
        String base = "fish." + fish.id();
        boolean isNew = !y.contains(base + ".count");
        y.set(base + ".count", y.getInt(base + ".count", 0) + 1);
        if (size > y.getInt(base + ".best", 0)) {
            y.set(base + ".best", size);
        }
        if (isNew) {
            y.set(base + ".first", System.currentTimeMillis());
        }
        dirty.add(id);
        if (isNew) {
            flush(id);
        }
        return isNew;
    }

    public boolean discovered(UUID id, String fishId) {
        return load(id).contains("fish." + fishId + ".count");
    }

    public int count(UUID id, String fishId) {
        return load(id).getInt("fish." + fishId + ".count", 0);
    }

    public int best(UUID id, String fishId) {
        return load(id).getInt("fish." + fishId + ".best", 0);
    }

    public long first(UUID id, String fishId) {
        return load(id).getLong("fish." + fishId + ".first", 0L);
    }

    public int discoveredCount(UUID id) {
        int n = 0;
        for (FishType f : plugin.fish().all()) {
            if (discovered(id, f.id())) {
                n++;
            }
        }
        return n;
    }

    public void flush(UUID id) {
        if (!dirty.remove(id)) {
            return;
        }
        YamlConfiguration y = cache.get(id);
        if (y == null) {
            return;
        }
        try {
            dir.mkdirs();
            y.save(new File(dir, id + ".yml"));
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible d'écrire le codex de " + id + " : " + ex.getMessage());
            dirty.add(id);
        }
    }

    public void flushAll() {
        for (UUID id : new HashSet<>(dirty)) {
            flush(id);
        }
    }

    /** Libère la mémoire pour un joueur qui quitte (après avoir écrit ses données). */
    public void unload(UUID id) {
        flush(id);
        cache.remove(id);
    }
}
