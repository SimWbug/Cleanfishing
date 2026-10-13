package fr.cleangang.cleanfishing.codex;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.fish.FishType;
import fr.cleangang.cleanfishing.fish.Rarity;
import fr.cleangang.cleanfishing.progress.Achievement;
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

    // ------------------------------------------------------------------ succès et titres

    public boolean hasAchievement(UUID id, Achievement achievement) {
        return load(id).contains("achievements." + achievement.name());
    }

    /** Débloque un succès. Renvoie true s'il ne l'était pas encore. */
    public boolean unlock(UUID id, Achievement achievement) {
        YamlConfiguration y = load(id);
        String path = "achievements." + achievement.name();
        if (y.contains(path)) {
            return false;
        }
        y.set(path, System.currentTimeMillis());
        dirty.add(id);
        flush(id);
        return true;
    }

    public long unlockedAt(UUID id, Achievement achievement) {
        return load(id).getLong("achievements." + achievement.name(), 0L);
    }

    public int achievementCount(UUID id) {
        int n = 0;
        for (Achievement a : Achievement.values()) {
            if (hasAchievement(id, a)) {
                n++;
            }
        }
        return n;
    }

    /** Titre choisi par le joueur (null = aucun). Un titre non débloqué est ignoré. */
    public Achievement title(UUID id) {
        Achievement a = Achievement.parse(load(id).getString("title"));
        return a != null && hasAchievement(id, a) ? a : null;
    }

    public void title(UUID id, Achievement achievement) {
        load(id).set("title", achievement == null ? null : achievement.name());
        dirty.add(id);
        flush(id);
    }

    /** Vrai si tous les poissons de cette rareté (ou tous les poissons si rarity == null) ont été pêchés. */
    public boolean completed(UUID id, Rarity rarity) {
        boolean any = false;
        for (FishType f : plugin.fish().all()) {
            if (rarity != null && f.rarity() != rarity) {
                continue;
            }
            any = true;
            if (!discovered(id, f.id())) {
                return false;
            }
        }
        return any;
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
