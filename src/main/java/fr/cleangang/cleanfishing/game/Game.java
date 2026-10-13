package fr.cleangang.cleanfishing.game;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.cards.CardTier;
import fr.cleangang.cleanfishing.cards.Power;
import fr.cleangang.cleanfishing.fish.FishType;
import fr.cleangang.cleanfishing.fish.Rarity;
import fr.cleangang.cleanfishing.gui.FrancisGui;
import fr.cleangang.cleanfishing.gui.ShopGui;
import fr.cleangang.cleanfishing.item.GameItem;
import fr.cleangang.cleanfishing.progress.Achievement;
import fr.cleangang.cleanfishing.shop.ShopOffer;
import fr.cleangang.cleanfishing.util.Fx;
import fr.cleangang.cleanfishing.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Une partie de CleanFishing dans une arène.
 * Cycle : LOBBY -> RUNNING (une journée) -> INTERMISSION (si palier atteint) -> RUNNING ... -> ENDING (défaite).
 */
public final class Game {

    public enum State {LOBBY, RUNNING, INTERMISSION, ENDING}

    /** Données propres à un joueur pendant la partie. */
    private static final class PlayerData {
        final String name;
        int luckCasts;
        int baitCasts;
        int epicCasts;
        int dayEarned;
        int totalEarned;
        int fish;
        int purchases;
        Role role;
        boolean hardcoreVote;
        final EnumMap<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);
        FishType bestFish;
        int bestSize;
        int bestValue;
        int level;
        int xp;
        int nextCardLevel = -1;
        int cardStep;
        long goldFeverUntil;
        final EnumMap<Power, Integer> powers = new EnumMap<>(Power.class);
        final Map<String, Long> cooldowns = new HashMap<>();
        Scoreboard board;
        Team[] lines;

        PlayerData(String name) {
            this.name = name;
        }

        int level(Upgrade upgrade) {
            return upgrades.getOrDefault(upgrade, 0);
        }

        int stacks(Power power) {
            return powers.getOrDefault(power, 0);
        }

        int cooldownLeft(String key) {
            long until = cooldowns.getOrDefault(key, 0L);
            return (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
        }

        void startCooldown(String key, int seconds) {
            cooldowns.put(key, System.currentTimeMillis() + seconds * 1000L);
        }
    }

    private static final int BOARD_LINES = 11;

    private final CleanFishingPlugin plugin;
    private final Arena arena;
    private final Random random = new Random();
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Set<UUID> francisVotes = new HashSet<>();
    private final List<UUID> npcIds = new ArrayList<>();
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);

    private State state = State.LOBBY;
    private boolean hardcore = false;
    private int day = 0;
    private int target = 0;
    private int pot = 0;
    private int rerollCount = 0;
    private int counter;
    private int dayTotal = 1;
    private int elapsed = 0;
    private int totalFish = 0;
    private int totalEarned = 0;
    private boolean reachedAnnounced = false;
    private DayEvent event;
    private int eventLeft;
    private final List<Integer> eventStarts = new ArrayList<>();
    private int nextAmbient = 20;
    private String bestCatchLine;
    private int bestCatchValue;
    private List<ShopOffer> offers = List.of();
    private final BukkitTask ticker;

    public Game(CleanFishingPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.counter = lobbyCountdown();
        npcIds.addAll(plugin.npcs().spawn(arena));
        plugin.purgeHostiles(arena);
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    // ------------------------------------------------------------------ accès

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public Arena arena() {
        return arena;
    }

    public State state() {
        return state;
    }

    public int day() {
        return day;
    }

    public int pot() {
        return pot;
    }

    public int target() {
        return target;
    }

    public boolean hardcore() {
        return hardcore;
    }

    /** Événement en cours, ou null. */
    public DayEvent event() {
        return event;
    }

    public List<ShopOffer> offers() {
        return offers;
    }

    public int playerCount() {
        return players.size();
    }

    public boolean has(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean canFish() {
        return state == State.RUNNING;
    }

    private String money(int amount) {
        return amount + cfg().getString("game.currency-symbol", "$");
    }

    private int lobbyCountdown() {
        return Math.max(3, cfg().getInt("game.lobby-countdown-seconds", 20));
    }

    private List<Player> online() {
        List<Player> list = new ArrayList<>();
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    private void broadcast(String miniMessage) {
        Component c = Text.msg(miniMessage);
        for (Player p : online()) {
            p.sendMessage(c);
        }
    }

    // ------------------------------------------------------------------ joueurs

    public boolean join(Player player) {
        if (state != State.LOBBY || players.size() >= cfg().getInt("game.max-players", 8)) {
            return false;
        }
        plugin.states().save(player);
        players.add(player.getUniqueId());
        PlayerData d = new PlayerData(player.getName());
        data.put(player.getUniqueId(), d);
        plugin.games().bind(player.getUniqueId(), this);

        prepare(player);
        player.teleport(arena.lobby());
        player.showBossBar(bossBar);
        setupBoard(player, d);
        broadcast("<yellow>" + Text.esc(player.getName()) + " <gray>a rejoint la partie <dark_gray>(<white>"
                + players.size() + "<dark_gray>/<white>" + cfg().getInt("game.max-players", 8) + "<dark_gray>)");
        player.sendMessage(Text.msg("<gray>Choisis ton rôle avec <aqua>l'objet « Rôle & mode »<gray> de ta barre."));
        plugin.pack().onGameJoin(player);
        refreshNametags();
        refreshHud();
        return true;
    }

    public void leave(Player player, boolean quitting) {
        UUID id = player.getUniqueId();
        if (!players.remove(id)) {
            return;
        }
        data.remove(id);
        francisVotes.remove(id);
        plugin.games().unbind(id);
        plugin.cards().cancel(player);
        player.hideBossBar(bossBar);
        resetAmbience(player);
        plugin.states().restore(player);
        plugin.codex().unload(id);
        if (!quitting) {
            plugin.items().scheduleHubItem(player);
            player.sendMessage(Text.msg("Tu as quitté la partie."));
        }
        if (players.isEmpty()) {
            cleanup();
            return;
        }
        broadcast("<yellow>" + Text.esc(player.getName()) + " <gray>a quitté la partie.");
        checkFrancisVotes();
        refreshNametags();
        refreshHud();
    }

    private void prepare(Player p) {
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        heal(p);
        p.setLevel(0);
        p.setExp(0f);
        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }
        plugin.items().giveKit(p, state == State.LOBBY);
    }

    private void heal(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }

    /** Coupe météo, musique et effets propres au jeu (avant de rendre l'inventaire). */
    private void resetAmbience(Player p) {
        p.resetPlayerWeather();
        String music = cfg().getString("ambience.music", "");
        if (music != null && !music.isBlank()) {
            Fx.stopMusic(p, music);
        }
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.removePotionEffect(PotionEffectType.SPEED);
    }

    // ------------------------------------------------------------------ salle d'attente : rôle et mode

    public Role roleOf(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? null : d.role;
    }

    public void setRole(Player p, Role role) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null || state != State.LOBBY) {
            return;
        }
        d.role = role;
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>sera " + role.displayName() + "<gray>.");
        Fx.sound(p, "block.note_block.pling", 1f, 1.4f);
        refreshHud();
    }

    public boolean votedHardcore(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d != null && d.hardcoreVote;
    }

    public void toggleHardcoreVote(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null || state != State.LOBBY) {
            return;
        }
        d.hardcoreVote = !d.hardcoreVote;
        broadcast("<yellow>" + Text.esc(p.getName()) + (d.hardcoreVote ? " <dark_red>vote pour le mode hardcore" : " <gray>retire son vote hardcore")
                + " <dark_gray>(" + hardcoreVotes() + "/" + hardcoreVotesNeeded() + ")");
        refreshHud();
    }

    public int hardcoreVotes() {
        int n = 0;
        for (PlayerData d : data.values()) {
            if (d.hardcoreVote) {
                n++;
            }
        }
        return n;
    }

    /** Majorité stricte des joueurs. */
    public int hardcoreVotesNeeded() {
        return players.size() / 2 + 1;
    }

    /** Liste des rôles choisis, pour l'interface de la salle d'attente. */
    public List<String> roster() {
        List<String> list = new ArrayList<>();
        for (Player p : online()) {
            PlayerData d = data.get(p.getUniqueId());
            String role = d == null || d.role == null ? "<dark_gray>pas encore choisi" : d.role.displayName();
            list.add("<yellow>" + Text.esc(p.getName()) + " <dark_gray>- " + role + (d != null && d.hardcoreVote ? " <dark_red>☠" : ""));
        }
        return list;
    }

    // ------------------------------------------------------------------ cycle

    /** Supprime la partie si personne ne l'a rejointe (utilisé après un join refusé). */
    public void discardIfEmpty() {
        if (players.isEmpty()) {
            cleanup();
        }
    }

    /** Ramène au spawn un joueur tombé hors de l'île (les dégâts sont désactivés pendant la partie). */
    private void rescueFallen() {
        Location spawn = arena.spawn();
        if (spawn == null) {
            return;
        }
        for (Player p : online()) {
            Location at = p.getLocation();
            if (at.getWorld() != spawn.getWorld() || at.getY() < spawn.getY() - 30) {
                p.teleport(spawn);
            }
        }
    }

    private void tick() {
        if (state != State.ENDING) {
            rescueFallen();
        }
        switch (state) {
            case LOBBY -> {
                if (players.isEmpty()) {
                    cleanup();
                    return;
                }
                if (players.size() >= Math.max(1, cfg().getInt("game.min-players", 1))) {
                    counter--;
                    if (counter <= 0) {
                        startGame();
                        return;
                    }
                    if (counter <= 5 || counter == 10) {
                        for (Player p : online()) {
                            Fx.sound(p, "block.note_block.hat", 1f, 1.2f);
                        }
                        broadcast("<gray>Départ dans <yellow>" + counter + "<gray>...");
                    }
                } else {
                    counter = lobbyCountdown();
                }
            }
            case RUNNING -> {
                counter--;
                elapsed++;
                if (counter <= 0) {
                    endDay();
                    return;
                }
                tickEvent();
                tickAmbience();
                if (counter == 60 || counter == 30 || counter == 10) {
                    String warn = pot >= target
                            ? "<green>Palier atteint ! <gray>Il reste <white>" + counter + "s<gray> pour grossir la cagnotte."
                            : "<red>Il reste " + counter + "s ! <gray>Il manque encore <yellow>" + money(target - pot) + "<gray>.";
                    broadcast(warn);
                    for (Player p : online()) {
                        Fx.sound(p, "block.note_block.bell", 1f, pot >= target ? 1.5f : 0.7f);
                    }
                }
            }
            case INTERMISSION -> {
                counter--;
                if (counter <= 0) {
                    startDay();
                    return;
                }
            }
            case ENDING -> {
                counter--;
                if (counter <= 0) {
                    finish();
                    return;
                }
            }
        }
        refreshHud();
    }

    /** Lance la partie tout de suite (compte à rebours ou commande admin). */
    public boolean forceStart() {
        if (state != State.LOBBY || players.isEmpty()) {
            return false;
        }
        startGame();
        return true;
    }

    private void startGame() {
        day = 0;
        pot = 0;
        totalFish = 0;
        totalEarned = 0;
        bestCatchLine = null;
        bestCatchValue = 0;
        hardcore = cfg().getBoolean("hardcore.enabled", true) && hardcoreVotes() >= hardcoreVotesNeeded();
        Role[] roles = Role.values();
        for (Player p : online()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d != null && d.role == null) {
                d.role = roles[random.nextInt(roles.length)];
                p.sendMessage(Text.msg("<gray>Tu n'avais pas choisi de rôle : tu seras " + d.role.displayName() + "<gray>."));
            }
        }
        if (hardcore) {
            broadcast("<dark_red><bold>MODE HARDCORE</bold> <gray>: paliers plus hauts, journées plus courtes, poissons mieux payés.");
        }
        startDay();
    }

    /** Palier d'une journée : liste de config, accélération à partir de difficulty.ramp-from-day, bonus hardcore. */
    private int computeTarget(int forDay) {
        double value = plugin.targetFor(forDay);
        int rampFrom = cfg().getInt("difficulty.ramp-from-day", 5);
        if (rampFrom > 0 && forDay >= rampFrom) {
            double growth = Math.max(1.0, cfg().getDouble("difficulty.extra-target-growth", 1.15));
            value *= Math.pow(growth, forDay - rampFrom + 1);
        }
        if (hardcore) {
            value *= Math.max(1.0, cfg().getDouble("hardcore.target-multiplier", 1.5));
        }
        return (int) Math.max(50, Math.round(value / 50.0) * 50);
    }

    /** Multiplicateur de valeur des poissons : difficulté, hardcore, événement en cours. */
    private double valueMultiplier() {
        double m = 1.0;
        int rampFrom = cfg().getInt("difficulty.ramp-from-day", 5);
        if (rampFrom > 0 && day >= rampFrom) {
            m += Math.max(0, cfg().getDouble("difficulty.value-bonus-per-day", 0.05)) * (day - rampFrom + 1);
        }
        if (hardcore) {
            m *= Math.max(0.1, cfg().getDouble("hardcore.value-multiplier", 1.25));
        }
        if (event == DayEvent.GOLDEN_SHOAL) {
            m *= Math.max(1.0, event.number(cfg(), "value-multiplier", 2.0));
        }
        return m;
    }

    private void startDay() {
        double carryPercent = hardcore ? 0 : Math.max(0, Math.min(100, cfg().getDouble("game.carry-over-percent", 0)));
        pot = (int) Math.floor(pot * carryPercent / 100.0);
        day++;
        target = computeTarget(day);
        state = State.RUNNING;
        int baseDuration = Math.max(30, cfg().getInt("game.day-duration-seconds", 600));
        dayTotal = hardcore
                ? Math.max(30, (int) Math.round(baseDuration * Math.max(0.1, cfg().getDouble("hardcore.day-duration-multiplier", 0.8))))
                : baseDuration;
        counter = dayTotal;
        elapsed = 0;
        reachedAnnounced = false;
        francisVotes.clear();
        offers = plugin.shop().roll(random);
        rerollCount = 0;
        scheduleEvents();
        nextAmbient = 15 + random.nextInt(20);
        plugin.purgeHostiles(arena);

        Title title = Title.title(
                Text.mm((hardcore ? "<dark_red>☠ " : "") + "<gold><bold>Journée " + day),
                Text.mm("<gray>Palier à atteindre : <yellow>" + money(target)),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(700)));
        String music = cfg().getString("ambience.music", "");
        for (Player p : online()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d != null) {
                d.dayEarned = 0;
                if (d.role != null) {
                    give(p, plugin.items().create(d.role.starter(), 1));
                }
                applyBoots(p, d);
            }
            heal(p);
            plugin.items().stripLobbyItems(p);
            p.teleport(arena.spawn());
            p.showTitle(title);
            Fx.sound(p, cfg().getString("ambience.day-start-sound", "item.goat_horn.sound.0"), 0.9f, 1.0f);
            if (cfg().getBoolean("ambience.enabled", true) && music != null && !music.isBlank()) {
                Fx.stopMusic(p, music);
                Fx.music(p, music, (float) cfg().getDouble("ambience.music-volume", 0.6));
            }
        }
        broadcast("<gold>Journée " + day + " ! <gray>Palier : <yellow>" + money(target)
                + "<gray>. Gertrude a renouvelé sa boutique, et Francis t'attend pour finir la journée.");
        refreshHud();
    }

    /** Fin de journée : succès si la cagnotte atteint le palier, défaite sinon. */
    private void endDay() {
        if (state != State.RUNNING) {
            return;
        }
        endEvent(false);
        for (Player p : online()) {
            Fx.sound(p, "block.bell.use", 1f, 0.9f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : online()) {
                Fx.sound(p, "block.bell.use", 1f, 0.9f);
            }
        }, 12L);
        if (pot >= target) {
            state = State.INTERMISSION;
            counter = Math.max(5, cfg().getInt("game.intermission-seconds", 30));
            francisVotes.clear();

            Player mvp = null;
            int best = 0;
            for (Player p : online()) {
                PlayerData d = data.get(p.getUniqueId());
                if (d != null && d.dayEarned > best) {
                    best = d.dayEarned;
                    mvp = p;
                }
            }
            broadcast("<green><bold>Journée " + day + " réussie !</bold> <gray>Cagnotte : <green>" + money(pot)
                    + " <gray>/ palier <yellow>" + money(target) + "<gray> (surplus <green>+" + money(pot - target) + "<gray>)");
            if (mvp != null) {
                broadcast("<gray>Meilleur pêcheur du jour : <aqua>" + Text.esc(mvp.getName()) + " <gray>avec <aqua>" + money(best));
            }
            broadcast("<gray>La boutique de Gertrude reste ouverte <white>" + counter + "s<gray> : dépense ce que tu veux de la cagnotte.");
            Title title = Title.title(
                    Text.mm("<green><bold>Journée réussie !"),
                    Text.mm("<gray>Cagnotte : <green>" + money(pot)),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700)));
            for (Player p : online()) {
                p.showTitle(title);
                Fx.sound(p, "ui.toast.challenge_complete", 1f, 1f);
                if (day >= 5) {
                    unlock(p, Achievement.DAY_5);
                }
                if (day >= 10) {
                    unlock(p, Achievement.DAY_10);
                }
                if (hardcore && day >= 5) {
                    unlock(p, Achievement.HARDCORE_5);
                }
            }
        } else {
            state = State.ENDING;
            counter = 15;
            int survived = day - 1;
            broadcast("<red><bold>Défaite !</bold> <gray>La cagnotte (<white>" + money(pot) + "<gray>) n'a pas atteint le palier (<yellow>"
                    + money(target) + "<gray>).");
            Title title = Title.title(
                    Text.mm("<red><bold>Défaite"),
                    Text.mm("<gray>Vous avez tenu <white>" + survived + "<gray> journée(s)"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1)));
            for (Player p : online()) {
                p.showTitle(title);
                Fx.sound(p, "entity.wither.death", 0.6f, 1.3f);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> recap(survived), 60L);
        }
        refreshHud();
    }

    /** Récapitulatif de fin de partie : classement, MVP, plus grosse prise, feu d'artifice, statistiques. */
    private void recap(int survived) {
        List<Player> list = online();
        if (list.isEmpty()) {
            return;
        }
        list.sort(Comparator.comparingInt((Player p) -> {
            PlayerData d = data.get(p.getUniqueId());
            return d == null ? 0 : d.totalEarned;
        }).reversed());
        broadcast("<gold><bold>━━━ Récapitulatif de la partie ━━━");
        broadcast("<gray>Journées réussies : <white>" + survived + (hardcore ? " <dark_red>(hardcore)" : "")
                + " <dark_gray>| <gray>Poissons : <white>" + totalFish + " <dark_gray>| <gray>Total gagné : <green>" + money(totalEarned));
        int rank = 1;
        for (Player p : list) {
            PlayerData d = data.get(p.getUniqueId());
            if (d == null) {
                continue;
            }
            String medal = rank == 1 ? "<gold>①" : rank == 2 ? "<gray>②" : rank == 3 ? "<#cd7f32>③" : "<dark_gray>" + rank + ".";
            broadcast(medal + " <yellow>" + Text.esc(p.getName()) + " <dark_gray>- <green>" + money(d.totalEarned)
                    + " <dark_gray>(" + d.fish + " poissons" + (d.role != null ? ", " + d.role.displayName() + "<dark_gray>" : "") + ")");
            rank++;
        }
        if (bestCatchLine != null) {
            broadcast("<gray>Plus grosse prise : " + bestCatchLine);
        }
        Player mvp = list.get(0);
        Title title = Title.title(
                Text.mm("<gold><bold>MVP : " + Text.esc(mvp.getName())),
                Text.mm("<gray>avec <green>" + money(data.containsKey(mvp.getUniqueId()) ? data.get(mvp.getUniqueId()).totalEarned : 0)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        for (Player p : list) {
            p.showTitle(title);
            Fx.sound(p, "entity.player.levelup", 1f, 1f);
            PlayerData d = data.get(p.getUniqueId());
            if (d != null) {
                plugin.stats().recordGame(p.getUniqueId(), p.getName(), survived, hardcore, d.totalEarned);
                if (survived >= 3 && d.purchases == 0) {
                    unlock(p, Achievement.NO_SPEND);
                }
            }
        }
        fireworks(list);
        plugin.stats().refreshBoards();
    }

    private void fireworks(List<Player> around) {
        if (!cfg().getBoolean("ambience.fireworks", true)) {
            return;
        }
        Color[] colors = {Color.AQUA, Color.ORANGE, Color.YELLOW, Color.LIME, Color.FUCHSIA, Color.WHITE};
        for (int i = 0; i < 8; i++) {
            Player center = around.get(random.nextInt(around.size()));
            Location loc = center.getLocation().add((random.nextDouble() - 0.5) * 8, 1, (random.nextDouble() - 0.5) * 8);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getWorld() == null) {
                    return;
                }
                loc.getWorld().spawn(loc, Firework.class, fw -> {
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                            .withColor(colors[random.nextInt(colors.length)], colors[random.nextInt(colors.length)])
                            .withFade(Color.WHITE)
                            .flicker(random.nextBoolean())
                            .trail(true)
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                });
            }, i * 6L);
        }
    }

    /** Fin de partie après une défaite : on rend leurs affaires aux joueurs. */
    private void finish() {
        for (Player p : online()) {
            leaveSilently(p);
        }
        cleanup();
    }

    /** Arrêt manuel (admin, arrêt du plugin). */
    public void stop(String reason) {
        broadcast("<red>" + Text.esc(reason));
        for (Player p : online()) {
            leaveSilently(p);
        }
        cleanup();
    }

    private void leaveSilently(Player p) {
        UUID id = p.getUniqueId();
        players.remove(id);
        data.remove(id);
        plugin.games().unbind(id);
        plugin.cards().cancel(p);
        p.hideBossBar(bossBar);
        resetAmbience(p);
        plugin.states().restore(p);
        plugin.codex().unload(id);
        plugin.items().scheduleHubItem(p);
    }

    private void cleanup() {
        ticker.cancel();
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.cards().cancel(p);
            }
        }
        plugin.npcs().remove(npcIds);
        npcIds.clear();
        for (UUID id : new ArrayList<>(players)) {
            plugin.games().unbind(id);
        }
        players.clear();
        plugin.games().remove(this);
    }

    // ------------------------------------------------------------------ événements de journée

    private void scheduleEvents() {
        eventStarts.clear();
        event = null;
        if (!cfg().getBoolean("events.enabled", true) || day < cfg().getInt("events.from-day", 1)) {
            return;
        }
        int count = Math.max(0, cfg().getInt("events.per-day", 2));
        int margin = 45;
        int start = margin;
        int end = dayTotal - margin;
        if (count == 0 || end - start < 60) {
            return;
        }
        int slot = (end - start) / count;
        for (int i = 0; i < count; i++) {
            int from = start + i * slot;
            int span = Math.max(1, slot - 100);
            eventStarts.add(from + random.nextInt(span));
        }
    }

    private void tickEvent() {
        if (event != null) {
            eventLeft--;
            if (event == DayEvent.STORM && eventLeft % 12 == 0) {
                for (Player p : online()) {
                    Fx.sound(p, "entity.lightning_bolt.thunder", 0.5f, 0.8f + random.nextFloat() * 0.3f);
                }
            }
            if (eventLeft <= 0) {
                endEvent(true);
            }
            return;
        }
        if (eventStarts.contains(elapsed)) {
            DayEvent next = pickEvent();
            if (next != null) {
                startEvent(next);
            }
        }
    }

    private DayEvent pickEvent() {
        double total = 0;
        for (DayEvent e : DayEvent.values()) {
            if (e.enabled(cfg())) {
                total += e.weight(cfg());
            }
        }
        if (total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (DayEvent e : DayEvent.values()) {
            if (!e.enabled(cfg())) {
                continue;
            }
            roll -= e.weight(cfg());
            if (roll <= 0) {
                return e;
            }
        }
        return null;
    }

    private void startEvent(DayEvent next) {
        event = next;
        eventLeft = Math.min(next.duration(cfg()), Math.max(10, counter - 5));
        Title title = Title.title(Text.mm(next.title()), Text.mm(next.subtitle()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600)));
        for (Player p : online()) {
            p.showTitle(title);
            switch (next) {
                case GOLDEN_SHOAL -> Fx.sound(p, "entity.player.levelup", 1f, 1.8f);
                case STORM -> {
                    p.setPlayerWeather(WeatherType.DOWNFALL);
                    Fx.sound(p, "entity.lightning_bolt.thunder", 0.8f, 1f);
                }
                case FOG -> {
                    if (next.number(cfg(), "darkness", 1) > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, (eventLeft + 2) * 20, 0, true, false, false));
                    }
                    Fx.sound(p, "ambient.cave", 0.8f, 0.8f);
                }
                case LOW_TIDE -> Fx.sound(p, "ambient.underwater.exit", 1f, 0.8f);
            }
        }
        broadcast(next.title() + " <gray>" + next.subtitle().replaceAll("<[^>]+>", "") + " <dark_gray>(" + eventLeft + "s)");
    }

    private void endEvent(boolean announce) {
        if (event == null) {
            return;
        }
        DayEvent ended = event;
        event = null;
        eventLeft = 0;
        for (Player p : online()) {
            if (ended == DayEvent.STORM) {
                p.resetPlayerWeather();
            }
            if (ended == DayEvent.FOG) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
            }
        }
        if (announce) {
            broadcast("<gray>Fin de l'événement : " + ended.badge() + "<gray>.");
        }
    }

    private void tickAmbience() {
        if (!cfg().getBoolean("ambience.enabled", true)) {
            return;
        }
        nextAmbient--;
        if (nextAmbient > 0) {
            return;
        }
        nextAmbient = 20 + random.nextInt(30);
        String gull = cfg().getString("ambience.seagull-sound", "entity.parrot.ambient");
        String waves = cfg().getString("ambience.waves-sound", "block.water.ambient");
        for (Player p : online()) {
            Location around = p.getLocation().add((random.nextDouble() - 0.5) * 12, 4 + random.nextDouble() * 4, (random.nextDouble() - 0.5) * 12);
            if (gull != null && !gull.isBlank() && event != DayEvent.STORM) {
                Fx.soundFor(p, around, gull, 0.5f, 1.5f + random.nextFloat() * 0.4f);
            }
            if (waves != null && !waves.isBlank()) {
                Fx.soundFor(p, p.getLocation(), waves, 0.6f, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------------ pêche

    public void onCast(Player p, FishHook hook) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        int min = hook.getMinWaitTime();
        int max = hook.getMaxWaitTime();
        if (d.baitCasts > 0) {
            min = 15;
            max = Math.max(30, cfg().getInt("loot.bait-max-wait-ticks", 100));
        }
        double factor = 1.0;
        factor *= Math.max(0.2, 1.0 - Upgrade.ROD.perLevel(cfg(), "bite-faster-per-level", 0.15) * d.level(Upgrade.ROD));
        factor *= Math.max(0.3, 1.0 - 0.20 * d.stacks(Power.FAST_BITE));
        if (event == DayEvent.STORM || event == DayEvent.LOW_TIDE) {
            factor *= Math.max(0.1, event.number(cfg(), "bite-multiplier", event == DayEvent.STORM ? 1.5 : 0.5));
        }
        if (factor != 1.0) {
            int newMin = Math.max(5, (int) Math.round(min * factor));
            int newMax = Math.max(newMin + 1, (int) Math.round(max * factor));
            hook.setWaitTime(newMin, newMax);
        } else if (d.baitCasts > 0) {
            hook.setWaitTime(min, max);
        }
    }

    public void onCatch(Player p, Location where) {
        if (state != State.RUNNING) {
            return;
        }
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        if (d.baitCasts > 0) {
            d.baitCasts--;
        }
        double luck = 1.0 + Upgrade.ROD.perLevel(cfg(), "luck-per-level", 0.25) * d.level(Upgrade.ROD)
                + 0.25 * d.stacks(Power.LUCK);
        if (d.luckCasts > 0) {
            d.luckCasts--;
            luck *= cfg().getDouble("loot.lucky-charm-multiplier", 2.5);
        }
        if (event == DayEvent.STORM || event == DayEvent.FOG) {
            luck *= Math.max(1.0, event.number(cfg(), "luck-multiplier", event == DayEvent.STORM ? 2.0 : 1.6));
        }
        double itemChance = cfg().getDouble("loot.item-chance", 0.12);
        if (event == DayEvent.LOW_TIDE) {
            itemChance *= Math.max(1.0, event.number(cfg(), "item-chance-multiplier", 2.0));
        }
        itemChance *= 1.0 + 0.5 * d.stacks(Power.TREASURE);
        Rarity min = Rarity.COMMON;
        if (d.epicCasts > 0) {
            d.epicCasts--;
            min = Rarity.EPIC;
        } else if (random.nextDouble() < itemChance) {
            GameItem item = rollItem();
            if (item != null) {
                give(p, plugin.items().create(item, 1));
                p.sendActionBar(Text.mm("<yellow>Tu as pêché un objet : " + item.displayName()));
                Fx.sound(p, "entity.item.pickup", 1f, 1.1f);
                addXp(p, d, cfg().getInt("levels.xp.item", 6));
                return;
            }
        }
        FishType fish = plugin.fish().roll(random, luck, min);
        completeCatch(p, fish, where, Rarity.RARE, true);
    }

    private GameItem rollItem() {
        var section = cfg().getConfigurationSection("loot.item-pool");
        if (section == null) {
            return null;
        }
        double total = 0;
        for (String key : section.getKeys(false)) {
            if (GameItem.parse(key) != null) {
                total += Math.max(0, section.getDouble(key));
            }
        }
        if (total <= 0) {
            return null;
        }
        double pick = random.nextDouble() * total;
        for (String key : section.getKeys(false)) {
            GameItem item = GameItem.parse(key);
            if (item == null) {
                continue;
            }
            pick -= Math.max(0, section.getDouble(key));
            if (pick <= 0) {
                return item;
            }
        }
        return null;
    }

    private void completeCatch(Player p, FishType fish, Location where, Rarity showcaseFrom, boolean bagRoll) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        int size = fish.randomSize(random);
        double personal = 1.0;
        boolean jackpot = false;
        if (System.currentTimeMillis() < d.goldFeverUntil) {
            personal *= 2;
        }
        if (d.stacks(Power.DOUBLE_PAY) > 0 && random.nextDouble() < 0.15 * d.stacks(Power.DOUBLE_PAY)) {
            personal *= 2;
            jackpot = true;
        }
        int value = (int) Math.max(1, Math.round(fish.valueFor(size) * valueMultiplier() * personal));
        pot += value;
        totalFish++;
        totalEarned += value;
        d.fish++;
        d.dayEarned += value;
        d.totalEarned += value;
        boolean first = plugin.codex().record(p.getUniqueId(), fish, size);

        String golden = event == DayEvent.GOLDEN_SHOAL ? " <gold>✦x" + trim(event.number(cfg(), "value-multiplier", 2.0)) : "";
        String bonus = (jackpot ? " <gold><bold>JACKPOT x2" : "")
                + (System.currentTimeMillis() < d.goldFeverUntil ? " <yellow>⚡x2" : "");
        p.sendActionBar(Text.mm("<green>+" + money(value) + " <gray>- " + fish.coloredName()
                + " <dark_gray>(" + size + " cm)" + golden + bonus + (first ? " <yellow><bold>NOUVEAU !" : "")));
        if (jackpot) {
            Fx.sound(p, "block.amethyst_block.resonate", 1f, 1.4f);
        }
        addXp(p, d, cfg().getInt("levels.xp." + fish.rarity().name().toLowerCase(), defaultXp(fish.rarity())));
        Fx.sound(p, "entity.experience_orb.pickup", 0.8f, 0.8f + fish.rarity().ordinal() * 0.2f);
        Fx.particle(where, "splash", 15, 0.3, 0.2, 0.3, 0.1);
        if (event == DayEvent.GOLDEN_SHOAL) {
            Fx.particle(where, "wax_on", 12, 0.4, 0.4, 0.4, 0.1);
        }

        if (fish.rarity().atLeast(showcaseFrom)) {
            showcase(where, fish);
        }
        if (fish.rarity().atLeast(Rarity.EPIC)) {
            broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>a pêché " + fish.coloredName() + " <dark_gray>(" + size
                    + " cm) <gray>pour <green>" + money(value) + "<gray> !");
        }
        if (fish.rarity() == Rarity.LEGENDARY) {
            Title title = Title.title(
                    Text.mm("<gold><bold>LÉGENDAIRE !"),
                    Text.mm(fish.coloredName() + " <gray>- <yellow>" + Text.esc(p.getName())),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600)));
            for (Player each : online()) {
                each.showTitle(title);
                Fx.sound(each, "ui.toast.challenge_complete", 1f, 0.8f);
            }
            unlock(p, Achievement.FIRST_LEGENDARY);
        }

        // records et succès
        if (value > d.bestValue) {
            d.bestValue = value;
            d.bestFish = fish;
            d.bestSize = size;
        }
        if (value > bestCatchValue) {
            bestCatchValue = value;
            bestCatchLine = fish.coloredName() + " <dark_gray>(" + size + " cm) <gray>par <yellow>" + Text.esc(p.getName())
                    + " <gray>- <green>" + money(value);
        }
        if (plugin.stats().recordCatch(p.getUniqueId(), p.getName(), fish, size, value)) {
            broadcast("<gold><bold>Nouveau record du serveur !</bold> <yellow>" + Text.esc(p.getName()) + " <gray>: "
                    + fish.coloredName() + " <gray>pour <green>" + money(value));
            unlock(p, Achievement.SERVER_RECORD);
        }
        if (size >= fish.maxSize() && fish.maxSize() > fish.minSize()) {
            unlock(p, Achievement.BIG_CATCH);
        }
        if (first) {
            checkCodexAchievements(p);
        }

        // grand sac : un poisson en plus
        double extraChance = Upgrade.BAG.perLevel(cfg(), "extra-fish-chance-per-level", 0.15) * d.level(Upgrade.BAG)
                + 0.12 * d.stacks(Power.DOUBLE_HOOK);
        if (bagRoll && extraChance > 0 && random.nextDouble() < extraChance) {
            FishType extra = plugin.fish().roll(random, 1.0, Rarity.COMMON);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == State.RUNNING && players.contains(p.getUniqueId())) {
                    p.sendActionBar(Text.mm("<green>Un poisson de plus dans ton sac !"));
                    completeCatch(p, extra, where, showcaseFrom, false);
                }
            }, 6L);
        }
        checkTarget();
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }

    private void checkCodexAchievements(Player p) {
        UUID id = p.getUniqueId();
        for (Achievement a : Achievement.values()) {
            if (a.codexRarity() != null && plugin.codex().completed(id, a.codexRarity())) {
                unlock(p, a);
            }
        }
        if (plugin.codex().completed(id, null)) {
            unlock(p, Achievement.CODEX_ALL);
        }
    }

    /** Débloque un succès et le fête. */
    private void unlock(Player p, Achievement achievement) {
        if (!plugin.codex().unlock(p.getUniqueId(), achievement)) {
            return;
        }
        p.showTitle(Title.title(Text.mm("<gold>Succès débloqué !"), Text.mm(achievement.title()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        Fx.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>a débloqué le titre " + achievement.title()
                + " <dark_gray>(" + Text.esc(achievement.description()) + ")");
        p.sendMessage(Text.msg("<gray>Équipe-le avec <yellow>/cfish titres<gray>."));
    }

    /** Annonce le palier atteint (une fois par journée), ou prévient si la cagnotte repasse dessous. */
    private void checkTarget() {
        if (state != State.RUNNING) {
            return;
        }
        if (!reachedAnnounced && pot >= target) {
            reachedAnnounced = true;
            Title title = Title.title(Text.mm("<green><bold>Palier atteint !"),
                    Text.mm("<gray>Parlez à <gold>Francis<gray> pour rentrer, ou continuez à pêcher"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500)));
            for (Player p : online()) {
                p.showTitle(title);
                Fx.sound(p, "entity.player.levelup", 1f, 1.3f);
            }
            broadcast("<green><bold>Palier atteint !</bold> <gray>Tout ce que vous pêchez maintenant est du bonus.");
        } else if (reachedAnnounced && pot < target) {
            reachedAnnounced = false;
            broadcast("<red>Attention : la cagnotte est repassée sous le palier ! <gray>Il manque <yellow>" + money(target - pot) + "<gray>.");
            for (Player p : online()) {
                Fx.sound(p, "block.note_block.bass", 1f, 0.6f);
            }
        }
    }

    /** Petit effet 3D : l'icône du poisson jaillit au-dessus de l'eau. */
    private void showcase(Location where, FishType fish) {
        if (where.getWorld() == null) {
            return;
        }
        Location start = where.clone().add(0, 0.6, 0);
        ItemDisplay display = where.getWorld().spawn(start, ItemDisplay.class, e -> {
            e.setItemStack(fish.icon());
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(false);
            e.setInterpolationDuration(20);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.3f), new AxisAngle4f()));
        });
        float scale = 1.2f + fish.rarity().ordinal() * 0.35f;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(25);
                display.setTransformation(new Transformation(new Vector3f(0, 2.0f, 0), new AxisAngle4f(),
                        new Vector3f(scale), new AxisAngle4f()));
            }
        }, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> display.remove(), 55L);
    }

    private void give(Player p, ItemStack stack) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ------------------------------------------------------------------ objets

    public void useItem(Player p, GameItem item, ItemStack stack) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        boolean buff = item == GameItem.BAIT || item == GameItem.LUCKY_CHARM || item == GameItem.GOLDEN_LURE;
        if (state != State.RUNNING && !(state == State.INTERMISSION && buff)) {
            p.sendActionBar(Text.mm("<red>Tu ne peux pas utiliser cet objet maintenant."));
            return;
        }
        switch (item) {
            case DYNAMITE -> {
                consume(stack);
                throwDynamite(p, false);
            }
            case DYNAMITE_CRATE -> {
                consume(stack);
                throwDynamite(p, true);
            }
            case NET -> useNet(p, d, stack);
            case HARPOON -> useHarpoon(p, d, stack);
            case BAIT -> {
                consume(stack);
                int casts = cfg().getInt("loot.bait-casts", 5);
                d.baitCasts += casts;
                p.sendActionBar(Text.mm("<green>Appât prêt : tes " + casts + " prochaines prises mordent plus vite."));
                Fx.sound(p, "entity.generic.eat", 0.8f, 1.2f);
            }
            case LUCKY_CHARM -> {
                consume(stack);
                int casts = cfg().getInt("loot.lucky-charm-casts", 5);
                d.luckCasts += casts;
                p.sendActionBar(Text.mm("<light_purple>Chance accrue pour tes " + casts + " prochaines prises !"));
                Fx.sound(p, "entity.player.levelup", 0.7f, 1.6f);
            }
            case GOLDEN_LURE -> {
                consume(stack);
                int casts = Math.max(1, cfg().getInt("items.golden-lure.casts", 3));
                d.epicCasts += casts;
                p.sendActionBar(Text.mm("<gold>Leurre légendaire : tes " + casts + " prochaines prises seront épiques ou mieux !"));
                Fx.sound(p, "block.amethyst_block.chime", 1f, 1.2f);
            }
            case SAND_TIMER -> {
                consume(stack);
                int bonus = Math.max(1, cfg().getInt("items.sand-timer.bonus-seconds", 60));
                counter += bonus;
                dayTotal += bonus;
                broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>a retourné un sablier : <white>+" + bonus + "s<gray> !");
                for (Player each : online()) {
                    Fx.sound(each, "block.beacon.activate", 0.8f, 1.5f);
                }
            }
        }
    }

    private void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }

    private Location waterTarget(Player p, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult result = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.ALWAYS, true);
        if (result == null || result.getHitBlock() == null || result.getHitBlock().getType() != Material.WATER) {
            return null;
        }
        return result.getHitPosition().toLocation(p.getWorld());
    }

    private void useNet(Player p, PlayerData d, ItemStack stack) {
        int left = d.cooldownLeft("net");
        if (left > 0) {
            p.sendActionBar(Text.mm("<red>Filet en cours de séchage : " + left + "s"));
            return;
        }
        Location target = waterTarget(p, 14);
        if (target == null) {
            p.sendActionBar(Text.mm("<red>Vise la surface de l'eau."));
            return;
        }
        consume(stack);
        boolean netter = d.role == Role.NETTER;
        int cooldown = cfg().getInt("items.net.cooldown-seconds", 20);
        double quick = Math.max(0.3, 1.0 - 0.3 * d.stacks(Power.QUICK_HANDS));
        d.startCooldown("net", (int) Math.round((netter ? cooldown * 0.6 : cooldown) * quick));
        int min = Math.max(1, cfg().getInt("items.net.min-fish", 2));
        int max = Math.max(min, cfg().getInt("items.net.max-fish", 4));
        Fx.sound(target, "entity.fishing_bobber.splash", 1f, 0.8f);
        Fx.particle(target, "splash", 40, 0.8, 0.2, 0.8, 0.2);
        haul(p, target, min + random.nextInt(max - min + 1) + (netter ? 1 : 0), Rarity.COMMON, Rarity.EPIC);
    }

    private void useHarpoon(Player p, PlayerData d, ItemStack stack) {
        int left = d.cooldownLeft("harpoon");
        if (left > 0) {
            p.sendActionBar(Text.mm("<red>Harpon en rechargement : " + left + "s"));
            return;
        }
        Location target = waterTarget(p, Math.max(4, cfg().getDouble("items.harpoon.range", 12)));
        if (target == null) {
            p.sendActionBar(Text.mm("<red>Vise la surface de l'eau."));
            return;
        }
        consume(stack);
        int cooldown = cfg().getInt("items.harpoon.cooldown-seconds", 12);
        double quick = Math.max(0.3, 1.0 - 0.3 * d.stacks(Power.QUICK_HANDS));
        d.startCooldown("harpoon", Math.max(1, (int) Math.round((d.role == Role.HARPOONER ? cooldown / 2.0 : cooldown) * quick)));
        Rarity min = Rarity.parse(cfg().getString("items.harpoon.min-rarity"), Rarity.RARE);
        Fx.sound(target, "entity.fishing_bobber.splash", 1f, 1.3f);
        Fx.particle(target, "bubble", 30, 0.4, 0.4, 0.4, 0.05);
        haul(p, target, 1, min, Rarity.RARE);
    }

    /** Remonte plusieurs poissons avec un petit décalage entre chaque. */
    private void haul(Player p, Location at, int count, Rarity min, Rarity showcaseFrom) {
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state != State.RUNNING || !players.contains(p.getUniqueId())) {
                    return;
                }
                FishType fish = plugin.fish().roll(random, 1.0, min);
                Location spot = at.clone().add((random.nextDouble() - 0.5) * 2, 0.3, (random.nextDouble() - 0.5) * 2);
                completeCatch(p, fish, spot, showcaseFrom, false);
            }, i * 3L);
        }
    }

    private void throwDynamite(Player p, boolean crate) {
        int fuse = Math.max(10, cfg().getInt("items.dynamite.fuse-ticks", 40));
        Location eye = p.getEyeLocation();
        Item thrown = p.getWorld().dropItem(eye, new ItemStack(crate ? Material.TNT_MINECART : Material.TNT), item -> {
            item.setVelocity(eye.getDirection().multiply(0.9));
            item.setPickupDelay(32767);
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setInvulnerable(true);
            item.setPersistent(false);
        });
        Fx.sound(p, "entity.tnt.primed", 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> explode(p, thrown, crate), fuse);
    }

    private void explode(Player p, Item thrown, boolean crate) {
        Location loc = thrown.getLocation();
        thrown.remove();
        Fx.particle(loc, "explosion_emitter", crate ? 3 : 1, crate ? 1.5 : 0, 0, crate ? 1.5 : 0, 0);
        Fx.sound(loc, "entity.generic.explode", crate ? 2f : 1.2f, crate ? 0.7f : 1f);
        if (state != State.RUNNING || !players.contains(p.getUniqueId())) {
            return;
        }
        int radius = Math.max(1, (int) Math.ceil(cfg().getDouble("items.dynamite.radius", 4.0) / 2));
        boolean water = false;
        for (int x = -radius; x <= radius && !water; x++) {
            for (int y = -2; y <= 1 && !water; y++) {
                for (int z = -radius; z <= radius && !water; z++) {
                    Block b = loc.getBlock().getRelative(x, y, z);
                    water = b.getType() == Material.WATER;
                }
            }
        }
        if (!water) {
            p.sendActionBar(Text.mm("<red>La dynamite a explosé loin de l'eau... gaspillée !"));
            return;
        }
        Fx.particle(loc, "splash", crate ? 200 : 80, 1.5, 0.5, 1.5, 0.3);
        String path = crate ? "items.dynamite-crate" : "items.dynamite";
        int min = Math.max(1, cfg().getInt(path + ".min-fish", crate ? 8 : 3));
        int max = Math.max(min, cfg().getInt(path + ".max-fish", crate ? 14 : 8));
        PlayerData d = data.get(p.getUniqueId());
        int bonus = d != null && d.role == Role.BLASTER ? 2 : 0;
        haul(p, loc, min + random.nextInt(max - min + 1) + bonus, Rarity.COMMON, Rarity.EPIC);
    }

    // ------------------------------------------------------------------ niveaux et cartes de pouvoir

    private static int defaultXp(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 10;
            case UNCOMMON -> 18;
            case RARE -> 35;
            case EPIC -> 70;
            case LEGENDARY -> 150;
        };
    }

    /** Expérience nécessaire pour passer du niveau donné au suivant. */
    private int xpNeeded(int level) {
        double base = Math.max(10, cfg().getDouble("levels.xp-base", 60));
        double growth = Math.max(1.0, cfg().getDouble("levels.xp-growth", 1.25));
        return (int) Math.round(base * Math.pow(growth, level));
    }

    private void addXp(Player p, PlayerData d, int amount) {
        if (!cfg().getBoolean("levels.enabled", true) || amount <= 0) {
            return;
        }
        d.xp += (int) Math.round(amount * (1.0 + 0.3 * d.stacks(Power.SCHOLAR)));
        while (d.xp >= xpNeeded(d.level)) {
            d.xp -= xpNeeded(d.level);
            d.level++;
            levelUp(p, d);
        }
        showXp(p, d);
    }

    private void showXp(Player p, PlayerData d) {
        p.setLevel(d.level);
        p.setExp((float) Math.max(0.0, Math.min(0.999, d.xp / (double) xpNeeded(d.level))));
    }

    private void levelUp(Player p, PlayerData d) {
        Fx.sound(p, "entity.player.levelup", 0.8f, 1.2f);
        Fx.particle(p.getLocation().add(0, 1, 0), "happy_villager", 15, 0.4, 0.6, 0.4, 0.05);
        if (d.nextCardLevel < 0) {
            d.nextCardLevel = cardStep(d);
        }
        if (d.level >= d.nextCardLevel) {
            d.nextCardLevel = d.level + cardStep(d);
            List<Power> choice = rollPowers(d);
            if (!choice.isEmpty()) {
                p.sendMessage(Text.msg("<gold><bold>Niveau " + d.level + " !</bold> <gray>Choisis une carte de pouvoir."));
                plugin.cards().offer(p, this, choice);
                return;
            }
        }
        p.sendActionBar(Text.mm("<aqua><bold>Niveau " + d.level + " !</bold> <gray>Prochaine carte au niveau <white>" + d.nextCardLevel));
    }

    /** Écart de niveaux entre deux cartes : alterne les valeurs de cards.every (2, 3, 2, 3...). */
    private int cardStep(PlayerData d) {
        List<Integer> steps = cfg().getIntegerList("cards.every");
        if (steps.isEmpty()) {
            steps = List.of(2, 3);
        }
        int step = Math.max(1, steps.get(d.cardStep % steps.size()));
        d.cardStep++;
        return step;
    }

    /** Tire des pouvoirs différents, pondérés par la rareté de leur carte, en écartant ceux déjà au maximum. */
    private List<Power> rollPowers(PlayerData d) {
        int count = Math.max(1, Math.min(4, cfg().getInt("cards.count", 3)));
        List<Power> pool = new ArrayList<>();
        for (Power power : Power.values()) {
            if (power.maxStacks() > 0 && d.stacks(power) >= power.maxStacks()) {
                continue;
            }
            if (!cfg().getBoolean("cards.powers." + power.name().toLowerCase() + ".enabled", true)) {
                continue;
            }
            pool.add(power);
        }
        List<Power> chosen = new ArrayList<>();
        while (chosen.size() < count && !pool.isEmpty()) {
            int total = 0;
            for (Power power : pool) {
                total += power.tier().weight();
            }
            int roll = random.nextInt(Math.max(1, total));
            Power pick = pool.get(pool.size() - 1);
            for (Power power : pool) {
                roll -= power.tier().weight();
                if (roll < 0) {
                    pick = power;
                    break;
                }
            }
            pool.remove(pick);
            chosen.add(pick);
        }
        return chosen;
    }

    /** Applique la carte choisie par le joueur. */
    public void applyPower(Player p, Power power) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        switch (power) {
            case GOLD_FEVER -> d.goldFeverUntil = System.currentTimeMillis() + 60_000L;
            case POCKET_TIMER -> {
                if (state == State.RUNNING) {
                    counter += 45;
                    dayTotal += 45;
                } else {
                    give(p, plugin.items().create(GameItem.SAND_TIMER, 1));
                }
            }
            case TIP -> {
                int tip = Math.max(50, (int) Math.round(target * 0.08));
                pot += tip;
                totalEarned += tip;
                d.dayEarned += tip;
                d.totalEarned += tip;
                checkTarget();
            }
            case ABYSS_CALL -> d.epicCasts += 2;
            default -> d.powers.merge(power, 1, Integer::sum);
        }
        String stacks = power.instant() ? "" : " <dark_gray>(x" + d.stacks(power) + ")";
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>prend la carte " + power.tier().color() + power.displayName() + stacks
                + " <dark_gray>- <gray>" + power.line1() + " " + power.line2());
        refreshHud();
        refreshShops();
    }

    public int levelOf(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? 0 : d.level;
    }

    /** Pouvoirs cumulés du joueur (pour /cfish pouvoirs). */
    public Map<Power, Integer> powersOf(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? Map.of() : new EnumMap<>(d.powers);
    }

    /** Prix réel pour ce joueur (carte « Ami de Gertrude »). */
    public int priceFor(Player p, int basePrice) {
        PlayerData d = data.get(p.getUniqueId());
        double discount = d == null ? 0 : Math.min(0.45, 0.15 * d.stacks(Power.FRIEND));
        return Math.max(1, (int) Math.round(basePrice * (1.0 - discount)));
    }

    // ------------------------------------------------------------------ PNJ

    public void openShop(Player p) {
        if (state != State.RUNNING && state != State.INTERMISSION) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: La taverne n'est pas encore ouverte, patience."));
            return;
        }
        ShopGui.open(plugin, this, p, gertrudeLine());
    }

    /** Réplique de Gertrude selon la situation. */
    public String gertrudeLine() {
        String situation;
        if (state == State.INTERMISSION) {
            situation = "gertrude.intermission";
        } else if (event != null && random.nextBoolean()) {
            situation = "gertrude.event." + event.key();
        } else if (counter <= 90) {
            situation = "gertrude.hurry";
        } else if (pot >= target) {
            situation = "gertrude.reached";
        } else if (hardcore && random.nextInt(3) == 0) {
            situation = "gertrude.hardcore";
        } else if (pot < target / 3 && elapsed > 120) {
            situation = "gertrude.poor";
        } else {
            situation = "gertrude.greet";
        }
        return Dialogue.line(plugin, situation, random);
    }

    /** Réplique de Gertrude pour une situation donnée (remerciements, refus...). */
    public String gertrudeSays(String situation) {
        return Dialogue.line(plugin, "gertrude." + situation, random);
    }

    /** Réplique de Francis pour ce joueur. */
    public String francisLine(Player p) {
        String situation;
        if (hasVoted(p)) {
            situation = "francis.voted";
        } else if (counter <= 90 && state == State.RUNNING) {
            situation = "francis.hurry";
        } else if (pot >= target) {
            situation = "francis.reached";
        } else {
            situation = "francis.not-reached";
        }
        return Dialogue.line(plugin, situation, random);
    }

    /** Achat à Gertrude : le prix est prélevé sur la cagnotte de la journée. Renvoie true si l'achat a eu lieu. */
    public boolean buy(Player p, int index) {
        if (state != State.RUNNING && state != State.INTERMISSION) {
            return false;
        }
        if (index < 0 || index >= offers.size()) {
            return false;
        }
        ShopOffer offer = offers.get(index);
        if (offer.soldOut()) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: " + Text.esc(gertrudeSays("soldout"))));
            Fx.sound(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        int cost = priceFor(p, offer.price());
        if (pot < cost) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: Il manque <red>" + money(cost - pot) + "<gray> dans la cagnotte."));
            Fx.sound(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        pot -= cost;
        offer.takeOne();
        PlayerData d = data.get(p.getUniqueId());
        if (d != null) {
            d.purchases++;
        }
        give(p, plugin.items().create(offer.item(), offer.amount()));
        Fx.sound(p, "entity.villager.yes", 1f, 1f);
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>a acheté <white>" + offer.amount() + "x " + offer.item().displayName()
                + " <gray>pour <red>-" + money(cost) + (offer.discounted() ? " <green>(promo -" + offer.discountPercent() + "%)" : "")
                + "<gray>. Cagnotte : <green>" + money(pot));
        checkTarget();
        refreshHud();
        refreshShops();
        return true;
    }

    public int upgradeLevel(Player p, Upgrade upgrade) {
        PlayerData d = data.get(p.getUniqueId());
        return d == null ? 0 : d.level(upgrade);
    }

    /** Achète le niveau suivant d'une amélioration permanente (propre au joueur). */
    public boolean buyUpgrade(Player p, Upgrade upgrade) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null || (state != State.RUNNING && state != State.INTERMISSION)) {
            return false;
        }
        int level = d.level(upgrade);
        int base = upgrade.nextPrice(cfg(), level);
        int price = base < 0 ? -1 : priceFor(p, base);
        if (price < 0) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: Tu as déjà la meilleure version, mon grand."));
            return false;
        }
        if (pot < price) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: Il manque <red>" + money(price - pot) + "<gray> dans la cagnotte."));
            Fx.sound(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        pot -= price;
        d.purchases++;
        d.upgrades.put(upgrade, level + 1);
        applyBoots(p, d);
        Fx.sound(p, "block.anvil.use", 0.7f, 1.3f);
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>s'offre " + upgrade.displayName() + " <white>niv. " + (level + 1)
                + " <gray>pour <red>-" + money(price) + "<gray>. Cagnotte : <green>" + money(pot));
        checkTarget();
        refreshHud();
        refreshShops();
        return true;
    }

    private void applyBoots(Player p, PlayerData d) {
        int level = d.level(Upgrade.BOOTS);
        p.removePotionEffect(PotionEffectType.SPEED);
        if (level > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, level - 1, true, false, true));
        }
    }

    /** Prix du prochain changement d'articles. */
    public int rerollPrice() {
        int base = Math.max(0, cfg().getInt("shop.reroll.base-price", 40));
        int step = Math.max(0, cfg().getInt("shop.reroll.price-step", 40));
        return base + step * rerollCount;
    }

    /** Changements restants aujourd'hui, ou -1 si illimité. */
    public int rerollsLeft() {
        int max = cfg().getInt("shop.reroll.max-per-day", 0);
        return max <= 0 ? -1 : Math.max(0, max - rerollCount);
    }

    public int rerollCount() {
        return rerollCount;
    }

    /** Gertrude change ses articles contre une part de la cagnotte. */
    public boolean reroll(Player p) {
        if (state != State.RUNNING && state != State.INTERMISSION) {
            return false;
        }
        if (rerollsLeft() == 0) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: Mes réserves sont vides pour aujourd'hui, mon grand."));
            Fx.sound(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        int price = rerollPrice();
        if (pot < price) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: Il manque <red>" + money(price - pot) + "<gray> dans la cagnotte."));
            Fx.sound(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        pot -= price;
        rerollCount++;
        PlayerData d = data.get(p.getUniqueId());
        if (d != null) {
            d.purchases++;
        }
        offers = plugin.shop().roll(random);
        Fx.sound(p, "item.bundle.insert", 1f, 1f);
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>fait changer l'étal de Gertrude <gray>pour <red>-" + money(price)
                + "<gray>. Cagnotte : <green>" + money(pot));
        checkTarget();
        refreshHud();
        refreshShops();
        return true;
    }

    /** Remet à jour la boutique ouverte chez tous les joueurs de la partie (l'étal est partagé). */
    private void refreshShops() {
        for (Player viewer : online()) {
            Inventory top = viewer.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof ShopGui.Holder holder && holder.game() == this) {
                ShopGui.render(plugin, this, viewer, top);
            }
        }
    }

    /** Francis : ouvre l'interface de fin de journée. */
    public void openFrancis(Player p) {
        if (state == State.INTERMISSION) {
            p.sendMessage(Text.msg("<gold>Francis <gray>: La journée est déjà finie, profite de la boutique !"));
            return;
        }
        if (state != State.RUNNING) {
            p.sendMessage(Text.msg("<gold>Francis <gray>: Les poissons ne sont pas encore au rendez-vous."));
            return;
        }
        FrancisGui.open(plugin, this, p);
    }

    /**
     * Vote pour terminer la journée (wantsEnd = true, possible seulement si le palier est atteint)
     * ou pour continuer à pêcher (wantsEnd = false, retire le vote).
     */
    public void setEndVote(Player p, boolean wantsEnd) {
        if (state != State.RUNNING) {
            return;
        }
        UUID id = p.getUniqueId();
        if (!wantsEnd) {
            if (francisVotes.remove(id)) {
                broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>veut continuer à pêcher <dark_gray>(<white>"
                        + francisVotes.size() + "<dark_gray>/<white>" + votesNeeded() + "<dark_gray>)");
            }
            return;
        }
        if (pot < target) {
            p.sendMessage(Text.msg("<gold>Francis <gray>: Pas question de rentrer les mains vides ! Il manque encore <yellow>"
                    + money(target - pot) + "<gray> pour le palier."));
            return;
        }
        if (!francisVotes.add(id)) {
            return; // déjà voté
        }
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>veut finir la journée <dark_gray>(<white>"
                + francisVotes.size() + "<dark_gray>/<white>" + votesNeeded() + "<dark_gray>)");
        checkFrancisVotes();
    }

    public boolean hasVoted(Player p) {
        return francisVotes.contains(p.getUniqueId());
    }

    public int endVotes() {
        return francisVotes.size();
    }

    public int endVotesRequired() {
        return votesNeeded();
    }

    /** Secondes restantes dans la phase en cours (journée, pause, compte à rebours). */
    public int secondsLeft() {
        return counter;
    }

    private int votesNeeded() {
        double ratio = Math.max(0.01, Math.min(1.0, cfg().getDouble("game.francis-vote-ratio", 1.0)));
        return Math.max(1, (int) Math.ceil(players.size() * ratio));
    }

    private void checkFrancisVotes() {
        if (state == State.RUNNING && !francisVotes.isEmpty() && francisVotes.size() >= votesNeeded() && pot >= target) {
            broadcast("<gold>Francis <gray>: Allez, on rentre !");
            endDay();
        }
    }

    // ------------------------------------------------------------------ HUD

    private void setupBoard(Player p, PlayerData d) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("cleanfishing", Criteria.DUMMY,
                Text.mm("<gradient:aqua:blue><bold>PÊCHE</bold></gradient>"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        Team[] lines = new Team[BOARD_LINES];
        for (int i = 0; i < BOARD_LINES; i++) {
            Team team = board.registerNewTeam("l" + i);
            String entry = "§" + Integer.toHexString(i);
            team.addEntry(entry);
            objective.getScore(entry).setScore(BOARD_LINES - i);
            lines[i] = team;
        }
        d.board = board;
        d.lines = lines;
        p.setScoreboard(board);
    }

    /** Titres devant les pseudos (au-dessus des têtes et dans la liste des joueurs), sur le tableau de chaque joueur. */
    public void refreshNametags() {
        List<Player> all = online();
        for (Player viewer : all) {
            PlayerData vd = data.get(viewer.getUniqueId());
            if (vd == null || vd.board == null) {
                continue;
            }
            for (Team team : new ArrayList<>(vd.board.getTeams())) {
                if (team.getName().startsWith("cfn")) {
                    team.unregister();
                }
            }
            int i = 0;
            for (Player shown : all) {
                Team team = vd.board.registerNewTeam("cfn" + i++);
                Achievement title = plugin.codex().title(shown.getUniqueId());
                if (title != null) {
                    team.prefix(Text.mm("<dark_gray>[" + title.title() + "<dark_gray>] "));
                }
                team.addEntry(shown.getName());
            }
        }
    }

    private void refreshHud() {
        String symbol = cfg().getString("game.currency-symbol", "$");
        Component title;
        float progress;
        BossBar.Color color;
        switch (state) {
            case LOBBY -> {
                int min = Math.max(1, cfg().getInt("game.min-players", 1));
                String hc = hardcoreVotes() > 0 ? " <dark_red>☠ " + hardcoreVotes() + "/" + hardcoreVotesNeeded() : "";
                title = players.size() >= min
                        ? Text.mm("<aqua>Départ dans <white>" + counter + "s <dark_gray>(" + players.size() + " joueur(s))" + hc)
                        : Text.mm("<aqua>En attente de joueurs <dark_gray>(" + players.size() + "/" + min + ")" + hc);
                progress = players.size() >= min ? counter / (float) lobbyCountdown() : 1f;
                color = BossBar.Color.BLUE;
            }
            case RUNNING -> {
                boolean reached = pot >= target;
                String goal = reached
                        ? "<green>" + pot + "/" + target + symbol + " ✔ +" + (pot - target) + symbol
                        : "<yellow>" + pot + "/" + target + symbol + " <gray>· encore <white>" + (target - pot) + symbol;
                String ev = event != null ? " <dark_gray>| " + event.badge() + " <white>" + eventLeft + "s" : "";
                title = Text.mm((hardcore ? "<dark_red>☠ " : "") + "<gold>Journée " + day + " <dark_gray>| <white>" + Text.clock(counter)
                        + " <dark_gray>| " + goal + ev);
                progress = counter / (float) Math.max(1, dayTotal);
                color = event == DayEvent.GOLDEN_SHOAL ? BossBar.Color.YELLOW
                        : reached ? BossBar.Color.GREEN : (counter <= 60 ? BossBar.Color.RED : BossBar.Color.BLUE);
            }
            case INTERMISSION -> {
                title = Text.mm("<green>Journée " + day + " réussie <dark_gray>| <white>prochaine journée dans " + counter + "s");
                progress = counter / (float) Math.max(5, cfg().getInt("game.intermission-seconds", 30));
                color = BossBar.Color.GREEN;
            }
            default -> {
                title = Text.mm("<red>Fin de partie");
                progress = 1f;
                color = BossBar.Color.RED;
            }
        }
        bossBar.name(title);
        bossBar.progress(Math.max(0f, Math.min(1f, progress)));
        bossBar.color(color);

        for (Player p : online()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d == null || d.lines == null) {
                continue;
            }
            String[] text = new String[BOARD_LINES];
            java.util.Arrays.fill(text, "");
            if (state == State.LOBBY) {
                text[1] = "<gray>En attente de départ";
                text[2] = "<gray>Joueurs <white>" + players.size();
                text[4] = "<gray>Rôle " + (d.role == null ? "<dark_gray>à choisir" : d.role.displayName());
                text[5] = "<gray>Hardcore <dark_red>" + hardcoreVotes() + "<gray>/" + hardcoreVotesNeeded();
                text[7] = "<gray>Arène <white>" + Text.esc(arena.name());
            } else {
                boolean reached = pot >= target;
                text[1] = "<gray>Journée <white>" + day + (hardcore ? " <dark_red>☠" : "");
                text[2] = "<gray>Palier <yellow>" + target + symbol + (reached ? " <green>✔" : " <red>✘");
                text[3] = "<gray>Cagnotte <green>" + pot + symbol;
                text[4] = reached ? "<green>Bonus +" + (pot - target) + symbol : "<gray>Reste <yellow>" + (target - pot) + symbol;
                text[5] = state == State.RUNNING ? "<gray>Temps <white>" + Text.clock(counter) : "<gray>Pause <white>" + counter + "s";
                text[6] = event != null ? event.badge() + " <white>" + eventLeft + "s" : "";
                text[7] = cfg().getBoolean("levels.enabled", true)
                        ? "<gray>Niveau <aqua>" + d.level + " <dark_gray>(" + d.powers.values().stream().mapToInt(Integer::intValue).sum() + " pouvoirs)"
                        : "";
                text[8] = "<gray>Ta pêche <aqua>" + d.dayEarned + symbol;
                text[9] = "<gray>Rôle " + (d.role == null ? "<dark_gray>aucun" : d.role.displayName());
            }
            for (int i = 0; i < BOARD_LINES; i++) {
                d.lines[i].prefix(text[i].isEmpty() ? Component.empty() : Text.mm(text[i]));
            }
        }
    }

    /** Classement des joueurs par gains (pour d'éventuels affichages). */
    public List<UUID> ranking() {
        List<UUID> ids = new ArrayList<>(data.keySet());
        ids.sort(Comparator.comparingInt((UUID id) -> data.get(id).totalEarned).reversed());
        return ids;
    }
}
