package fr.cleangang.cleanfishing.progress;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.fish.FishType;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classement du serveur (leaderboard.yml) et panneaux de classement flottants placés par les admins.
 * Les panneaux sont des TextDisplay non persistants : rien n'est sauvegardé dans le monde.
 */
public final class StatsManager {

    /** Une ligne du classement. */
    public record Entry(UUID id, String name, int value, String detail) {
    }

    private final CleanFishingPlugin plugin;
    private final File file;
    private final NamespacedKey boardKey;
    private YamlConfiguration yaml = new YamlConfiguration();
    private final Map<String, UUID> displays = new HashMap<>();
    private BukkitTask refresher;

    public StatsManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboard.yml");
        this.boardKey = new NamespacedKey(plugin, "board");
    }

    public void load() {
        yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible d'écrire leaderboard.yml : " + ex.getMessage());
        }
    }

    private String base(UUID id) {
        return "players." + id;
    }

    /**
     * Enregistre une prise. Renvoie true si c'est le nouveau record du serveur (prise la plus chère).
     */
    public boolean recordCatch(UUID id, String name, FishType fish, int size, int value) {
        int serverBest = bestCatchValue();
        String b = base(id);
        yaml.set(b + ".name", name);
        if (value > yaml.getInt(b + ".catch.value", 0)) {
            yaml.set(b + ".catch.value", value);
            yaml.set(b + ".catch.fish", fish.name());
            yaml.set(b + ".catch.size", size);
            save();
        }
        return value > serverBest && serverBest > 0;
    }

    /** Fin de partie : meilleure journée, gains cumulés, nombre de parties. */
    public void recordGame(UUID id, String name, int daysSucceeded, boolean hardcore, int earned) {
        String b = base(id);
        yaml.set(b + ".name", name);
        String dayPath = b + (hardcore ? ".best-day-hardcore" : ".best-day");
        if (daysSucceeded > yaml.getInt(dayPath, 0)) {
            yaml.set(dayPath, daysSucceeded);
        }
        yaml.set(b + ".earned", yaml.getLong(b + ".earned", 0) + earned);
        yaml.set(b + ".games", yaml.getInt(b + ".games", 0) + 1);
        save();
    }

    public int bestCatchValue() {
        int best = 0;
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                best = Math.max(best, players.getInt(key + ".catch.value", 0));
            }
        }
        return best;
    }

    /** Top par meilleure journée (normal ou hardcore). */
    public List<Entry> topDays(boolean hardcore, int limit) {
        return top(hardcore ? "best-day-hardcore" : "best-day", limit, s -> "");
    }

    /** Top des plus grosses prises (valeur d'une seule prise). */
    public List<Entry> topCatches(int limit) {
        return top("catch.value", limit, s -> s.getString("catch.fish", "?") + " (" + s.getInt("catch.size") + " cm)");
    }

    private List<Entry> top(String path, int limit, java.util.function.Function<ConfigurationSection, String> detail) {
        List<Entry> list = new ArrayList<>();
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return list;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection s = players.getConfigurationSection(key);
            if (s == null || s.getInt(path, 0) <= 0) {
                continue;
            }
            try {
                list.add(new Entry(UUID.fromString(key), s.getString("name", "?"), s.getInt(path), detail.apply(s)));
            } catch (IllegalArgumentException ignored) {
                // clé illisible
            }
        }
        list.sort(Comparator.comparingInt(Entry::value).reversed());
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    // ------------------------------------------------------------------ panneaux flottants

    public void startBoards() {
        stopBoards();
        refreshBoards();
        long period = 20L * Math.max(10, plugin.getConfig().getInt("leaderboard.refresh-seconds", 60));
        refresher = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBoards, period, period);
    }

    public void stopBoards() {
        if (refresher != null) {
            refresher.cancel();
            refresher = null;
        }
        for (UUID id : displays.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        displays.clear();
    }

    /** Crée ou met à jour le panneau de chaque arène qui en a un. */
    public void refreshBoards() {
        Component text = boardText();
        List<String> seen = new ArrayList<>();
        for (Arena arena : plugin.arenas().all()) {
            Location loc = arena.board();
            if (loc == null || loc.getWorld() == null || !loc.isChunkLoaded()) {
                continue;
            }
            String key = arena.name().toLowerCase();
            seen.add(key);
            UUID id = displays.get(key);
            Entity existing = id == null ? null : Bukkit.getEntity(id);
            if (existing instanceof TextDisplay display && display.isValid()
                    && loc.getWorld().equals(display.getWorld()) && display.getLocation().distanceSquared(loc) < 0.01) {
                display.text(text);
                continue;
            }
            if (existing != null) {
                existing.remove();
            }
            World world = loc.getWorld();
            TextDisplay display = world.spawn(loc, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(150, 10, 25, 40));
                d.setShadowed(true);
                d.setLineWidth(220);
                d.text(text);
                d.getPersistentDataContainer().set(boardKey, PersistentDataType.STRING, arena.name());
            });
            displays.put(key, display.getUniqueId());
        }
        // panneaux d'arènes supprimées ou déplacées
        for (String key : new ArrayList<>(displays.keySet())) {
            if (!seen.contains(key)) {
                Entity e = Bukkit.getEntity(displays.remove(key));
                if (e != null) {
                    e.remove();
                }
            }
        }
    }

    /** Retire les panneaux restés d'une session précédente (ne devrait pas arriver : ils ne sont pas persistants). */
    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(boardKey, PersistentDataType.STRING);
    }

    private Component boardText() {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        StringBuilder sb = new StringBuilder("<gradient:aqua:blue><bold>CLASSEMENT DE PÊCHE</bold></gradient>\n");
        sb.append("<gold>Meilleures journées\n");
        appendLines(sb, topDays(false, 5), e -> "<white>" + e.value() + " journée(s)");
        List<Entry> hardcore = topDays(true, 3);
        if (!hardcore.isEmpty()) {
            sb.append("<dark_red>Hardcore\n");
            appendLines(sb, hardcore, e -> "<white>" + e.value() + " journée(s)");
        }
        sb.append("<gold>Plus grosses prises\n");
        appendLines(sb, topCatches(5), e -> "<green>" + e.value() + symbol + " <dark_gray>" + Text.esc(e.detail()));
        return Text.mm(sb.toString().stripTrailing());
    }

    private void appendLines(StringBuilder sb, List<Entry> entries, java.util.function.Function<Entry, String> value) {
        if (entries.isEmpty()) {
            sb.append("<dark_gray>  personne pour l'instant\n");
            return;
        }
        int rank = 1;
        for (Entry e : entries) {
            String medal = switch (rank) {
                case 1 -> "<gold>1.";
                case 2 -> "<gray>2.";
                case 3 -> "<#cd7f32>3.";
                default -> "<dark_gray>" + rank + ".";
            };
            sb.append(medal).append(" <yellow>").append(Text.esc(e.name())).append(" <dark_gray>- ")
                    .append(value.apply(e)).append('\n');
            rank++;
        }
    }
}
