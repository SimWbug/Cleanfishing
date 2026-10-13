package fr.cleangang.cleanfishing.game;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.fish.FishType;
import fr.cleangang.cleanfishing.fish.Rarity;
import fr.cleangang.cleanfishing.gui.FrancisGui;
import fr.cleangang.cleanfishing.gui.ShopGui;
import fr.cleangang.cleanfishing.item.GameItem;
import fr.cleangang.cleanfishing.shop.ShopOffer;
import fr.cleangang.cleanfishing.util.Fx;
import fr.cleangang.cleanfishing.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
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
        int luckCasts;
        int baitCasts;
        int dayEarned;
        int totalEarned;
        int fish;
        final Map<String, Long> cooldowns = new HashMap<>();
        Scoreboard board;
        Team[] lines;

        int cooldownLeft(String key) {
            long until = cooldowns.getOrDefault(key, 0L);
            return (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
        }

        void startCooldown(String key, int seconds) {
            cooldowns.put(key, System.currentTimeMillis() + seconds * 1000L);
        }
    }

    private static final int BOARD_LINES = 9;

    private final CleanFishingPlugin plugin;
    private final Arena arena;
    private final Random random = new Random();
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Set<UUID> francisVotes = new HashSet<>();
    private final List<UUID> npcIds = new ArrayList<>();
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);

    private State state = State.LOBBY;
    private int day = 0;
    private int target = 0;
    private int pot = 0;
    private int rerollCount = 0;
    private int counter;
    private int dayTotal = 1;
    private int totalFish = 0;
    private int totalEarned = 0;
    private List<ShopOffer> offers = List.of();
    private final BukkitTask ticker;

    public Game(CleanFishingPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.counter = lobbyCountdown();
        npcIds.addAll(plugin.npcs().spawn(arena));
        plugin.purgeHostiles(arena);
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------------ accès

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
        return amount + plugin.getConfig().getString("game.currency-symbol", "$");
    }

    private int lobbyCountdown() {
        return Math.max(3, plugin.getConfig().getInt("game.lobby-countdown-seconds", 20));
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
        if (state != State.LOBBY || players.size() >= plugin.getConfig().getInt("game.max-players", 8)) {
            return false;
        }
        plugin.states().save(player);
        players.add(player.getUniqueId());
        PlayerData d = new PlayerData();
        data.put(player.getUniqueId(), d);
        plugin.games().bind(player.getUniqueId(), this);

        prepare(player);
        player.teleport(arena.lobby());
        player.showBossBar(bossBar);
        setupBoard(player, d);
        broadcast("<yellow>" + Text.esc(player.getName()) + " <gray>a rejoint la partie <dark_gray>(<white>"
                + players.size() + "<dark_gray>/<white>" + plugin.getConfig().getInt("game.max-players", 8) + "<dark_gray>)");
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
        player.hideBossBar(bossBar);
        plugin.states().restore(player);
        plugin.codex().unload(id);
        if (!quitting) {
            player.sendMessage(Text.msg("Tu as quitté la partie."));
        }
        if (players.isEmpty()) {
            cleanup();
            return;
        }
        broadcast("<yellow>" + Text.esc(player.getName()) + " <gray>a quitté la partie.");
        checkFrancisVotes();
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
                if (players.size() >= Math.max(1, plugin.getConfig().getInt("game.min-players", 1))) {
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
                if (counter <= 0) {
                    endDay();
                    return;
                }
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
        startDay();
    }

    private void startDay() {
        double carryPercent = Math.max(0, Math.min(100, plugin.getConfig().getDouble("game.carry-over-percent", 0)));
        pot = (int) Math.floor(pot * carryPercent / 100.0);
        day++;
        target = plugin.targetFor(day);
        state = State.RUNNING;
        dayTotal = Math.max(30, plugin.getConfig().getInt("game.day-duration-seconds", 600));
        counter = dayTotal;
        francisVotes.clear();
        offers = plugin.shop().roll(random);
        rerollCount = 0;
        plugin.purgeHostiles(arena);

        Title title = Title.title(
                Text.mm("<gold><bold>Journée " + day),
                Text.mm("<gray>Palier à atteindre : <yellow>" + money(target)),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(700)));
        for (Player p : online()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d != null) {
                d.dayEarned = 0;
            }
            heal(p);
            plugin.items().stripLobbyItems(p);
            p.teleport(arena.spawn());
            p.showTitle(title);
            Fx.sound(p, "entity.player.levelup", 0.8f, 1.2f);
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
        if (pot >= target) {
            state = State.INTERMISSION;
            counter = Math.max(5, plugin.getConfig().getInt("game.intermission-seconds", 30));
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
            }
        } else {
            state = State.ENDING;
            counter = 12;
            int survived = day - 1;
            broadcast("<red><bold>Défaite !</bold> <gray>La cagnotte (<white>" + money(pot) + "<gray>) n'a pas atteint le palier (<yellow>"
                    + money(target) + "<gray>).");
            broadcast("<gray>Journées réussies : <white>" + survived + " <dark_gray>| <gray>Poissons pêchés : <white>" + totalFish
                    + " <dark_gray>| <gray>Total gagné : <white>" + money(totalEarned));
            Title title = Title.title(
                    Text.mm("<red><bold>Défaite"),
                    Text.mm("<gray>Tu as tenu <white>" + survived + "<gray> journée(s)"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1)));
            for (Player p : online()) {
                p.showTitle(title);
                Fx.sound(p, "entity.wither.death", 0.6f, 1.3f);
            }
        }
        refreshHud();
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
        p.hideBossBar(bossBar);
        plugin.states().restore(p);
        plugin.codex().unload(id);
    }

    private void cleanup() {
        ticker.cancel();
        plugin.npcs().remove(npcIds);
        npcIds.clear();
        for (UUID id : new ArrayList<>(players)) {
            plugin.games().unbind(id);
        }
        players.clear();
        plugin.games().remove(this);
    }

    // ------------------------------------------------------------------ pêche

    public void onCast(Player p, FishHook hook) {
        PlayerData d = data.get(p.getUniqueId());
        if (d != null && d.baitCasts > 0) {
            int max = Math.max(30, plugin.getConfig().getInt("loot.bait-max-wait-ticks", 100));
            hook.setWaitTime(15, max);
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
        double luck = 1.0;
        if (d.luckCasts > 0) {
            d.luckCasts--;
            luck = plugin.getConfig().getDouble("loot.lucky-charm-multiplier", 2.5);
        }
        if (random.nextDouble() < plugin.getConfig().getDouble("loot.item-chance", 0.12)) {
            GameItem item = rollItem();
            if (item != null) {
                give(p, plugin.items().create(item, 1));
                p.sendActionBar(Text.mm("<yellow>Tu as pêché un objet : " + item.displayName()));
                Fx.sound(p, "entity.item.pickup", 1f, 1.1f);
                return;
            }
        }
        FishType fish = plugin.fish().roll(random, luck, Rarity.COMMON);
        completeCatch(p, fish, where, Rarity.RARE);
    }

    private GameItem rollItem() {
        var section = plugin.getConfig().getConfigurationSection("loot.item-pool");
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

    private void completeCatch(Player p, FishType fish, Location where, Rarity showcaseFrom) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        int size = fish.randomSize(random);
        int value = fish.valueFor(size);
        pot += value;
        totalFish++;
        totalEarned += value;
        d.fish++;
        d.dayEarned += value;
        d.totalEarned += value;
        boolean first = plugin.codex().record(p.getUniqueId(), fish, size);

        p.sendActionBar(Text.mm("<green>+" + money(value) + " <gray>- " + fish.coloredName()
                + " <dark_gray>(" + size + " cm)" + (first ? " <yellow><bold>NOUVEAU !" : "")));
        Fx.sound(p, "entity.experience_orb.pickup", 0.8f, 0.8f + fish.rarity().ordinal() * 0.2f);
        Fx.particle(where, "splash", 15, 0.3, 0.2, 0.3, 0.1);

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
        boolean buff = item == GameItem.BAIT || item == GameItem.LUCKY_CHARM;
        if (state != State.RUNNING && !(state == State.INTERMISSION && buff)) {
            p.sendActionBar(Text.mm("<red>Tu ne peux pas utiliser cet objet maintenant."));
            return;
        }
        switch (item) {
            case DYNAMITE -> {
                consume(stack);
                throwDynamite(p);
            }
            case NET -> useNet(p, d, stack);
            case HARPOON -> useHarpoon(p, d, stack);
            case BAIT -> {
                consume(stack);
                int casts = plugin.getConfig().getInt("loot.bait-casts", 5);
                d.baitCasts += casts;
                p.sendActionBar(Text.mm("<green>Appât prêt : tes " + casts + " prochaines prises mordent plus vite."));
                Fx.sound(p, "entity.generic.eat", 0.8f, 1.2f);
            }
            case LUCKY_CHARM -> {
                consume(stack);
                int casts = plugin.getConfig().getInt("loot.lucky-charm-casts", 5);
                d.luckCasts += casts;
                p.sendActionBar(Text.mm("<light_purple>Chance accrue pour tes " + casts + " prochaines prises !"));
                Fx.sound(p, "entity.player.levelup", 0.7f, 1.6f);
            }
            case SAND_TIMER -> {
                consume(stack);
                int bonus = Math.max(1, plugin.getConfig().getInt("items.sand-timer.bonus-seconds", 60));
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
        d.startCooldown("net", plugin.getConfig().getInt("items.net.cooldown-seconds", 20));
        int min = Math.max(1, plugin.getConfig().getInt("items.net.min-fish", 2));
        int max = Math.max(min, plugin.getConfig().getInt("items.net.max-fish", 4));
        Fx.sound(target, "entity.fishing_bobber.splash", 1f, 0.8f);
        Fx.particle(target, "splash", 40, 0.8, 0.2, 0.8, 0.2);
        haul(p, target, min + random.nextInt(max - min + 1), Rarity.COMMON, Rarity.EPIC);
    }

    private void useHarpoon(Player p, PlayerData d, ItemStack stack) {
        int left = d.cooldownLeft("harpoon");
        if (left > 0) {
            p.sendActionBar(Text.mm("<red>Harpon en rechargement : " + left + "s"));
            return;
        }
        Location target = waterTarget(p, Math.max(4, plugin.getConfig().getDouble("items.harpoon.range", 12)));
        if (target == null) {
            p.sendActionBar(Text.mm("<red>Vise la surface de l'eau."));
            return;
        }
        consume(stack);
        d.startCooldown("harpoon", plugin.getConfig().getInt("items.harpoon.cooldown-seconds", 12));
        Rarity min = Rarity.parse(plugin.getConfig().getString("items.harpoon.min-rarity"), Rarity.RARE);
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
                completeCatch(p, fish, spot, showcaseFrom);
            }, i * 3L);
        }
    }

    private void throwDynamite(Player p) {
        int fuse = Math.max(10, plugin.getConfig().getInt("items.dynamite.fuse-ticks", 40));
        Location eye = p.getEyeLocation();
        Item thrown = p.getWorld().dropItem(eye, new ItemStack(Material.TNT), item -> {
            item.setVelocity(eye.getDirection().multiply(0.9));
            item.setPickupDelay(32767);
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setInvulnerable(true);
            item.setPersistent(false);
        });
        Fx.sound(p, "entity.tnt.primed", 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> explode(p, thrown), fuse);
    }

    private void explode(Player p, Item thrown) {
        Location loc = thrown.getLocation();
        thrown.remove();
        Fx.particle(loc, "explosion_emitter", 1, 0, 0, 0, 0);
        Fx.sound(loc, "entity.generic.explode", 1.2f, 1f);
        if (state != State.RUNNING || !players.contains(p.getUniqueId())) {
            return;
        }
        int radius = Math.max(1, (int) Math.ceil(plugin.getConfig().getDouble("items.dynamite.radius", 4.0) / 2));
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
        Fx.particle(loc, "splash", 80, 1.5, 0.5, 1.5, 0.3);
        int min = Math.max(1, plugin.getConfig().getInt("items.dynamite.min-fish", 3));
        int max = Math.max(min, plugin.getConfig().getInt("items.dynamite.max-fish", 8));
        haul(p, loc, min + random.nextInt(max - min + 1), Rarity.COMMON, Rarity.EPIC);
    }

    // ------------------------------------------------------------------ PNJ

    public void openShop(Player p) {
        if (state != State.RUNNING && state != State.INTERMISSION) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: La taverne n'est pas encore ouverte, patience."));
            return;
        }
        ShopGui.open(plugin, this, p);
    }

    /** Achat à Gertrude : le prix est prélevé sur la cagnotte de la journée. */
    public void buy(Player p, int index) {
        if (state != State.RUNNING && state != State.INTERMISSION) {
            return;
        }
        if (index < 0 || index >= offers.size()) {
            return;
        }
        ShopOffer offer = offers.get(index);
        if (pot < offer.price()) {
            p.sendMessage(Text.msg("<gold>Gertrude <gray>: Il manque <red>" + money(offer.price() - pot) + "<gray> dans la cagnotte."));
            Fx.sound(p, "entity.villager.no", 1f, 1f);
            return;
        }
        pot -= offer.price();
        give(p, plugin.items().create(offer.item(), offer.amount()));
        Fx.sound(p, "entity.villager.yes", 1f, 1f);
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>a acheté <white>" + offer.amount() + "x " + offer.item().displayName()
                + " <gray>pour <red>-" + money(offer.price()) + "<gray>. Cagnotte : <green>" + money(pot));
        refreshHud();
        refreshShops();
    }

    /** Prix du prochain changement d'articles. */
    public int rerollPrice() {
        int base = Math.max(0, plugin.getConfig().getInt("shop.reroll.base-price", 40));
        int step = Math.max(0, plugin.getConfig().getInt("shop.reroll.price-step", 40));
        return base + step * rerollCount;
    }

    /** Changements restants aujourd'hui, ou -1 si illimité. */
    public int rerollsLeft() {
        int max = plugin.getConfig().getInt("shop.reroll.max-per-day", 0);
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
        offers = plugin.shop().roll(random);
        Fx.sound(p, "item.bundle.insert", 1f, 1f);
        broadcast("<yellow>" + Text.esc(p.getName()) + " <gray>fait changer l'étal de Gertrude <gray>pour <red>-" + money(price)
                + "<gray>. Cagnotte : <green>" + money(pot));
        refreshHud();
        refreshShops();
        return true;
    }

    /** Remet à jour la boutique ouverte chez tous les joueurs de la partie (l'étal est partagé). */
    private void refreshShops() {
        for (Player viewer : online()) {
            Inventory top = viewer.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof ShopGui.Holder holder && holder.game() == this) {
                ShopGui.render(plugin, this, top);
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
        double ratio = Math.max(0.01, Math.min(1.0, plugin.getConfig().getDouble("game.francis-vote-ratio", 1.0)));
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

    private void refreshHud() {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        Component title;
        float progress;
        BossBar.Color color;
        switch (state) {
            case LOBBY -> {
                int min = Math.max(1, plugin.getConfig().getInt("game.min-players", 1));
                title = players.size() >= min
                        ? Text.mm("<aqua>Départ dans <white>" + counter + "s <dark_gray>(" + players.size() + " joueur(s))")
                        : Text.mm("<aqua>En attente de joueurs <dark_gray>(" + players.size() + "/" + min + ")");
                progress = players.size() >= min ? counter / (float) lobbyCountdown() : 1f;
                color = BossBar.Color.BLUE;
            }
            case RUNNING -> {
                boolean reached = pot >= target;
                title = Text.mm("<gold>Journée " + day + " <dark_gray>| <white>" + Text.clock(counter) + " <dark_gray>| "
                        + (reached ? "<green>" : "<yellow>") + pot + "/" + target + symbol);
                progress = counter / (float) Math.max(1, dayTotal);
                color = reached ? BossBar.Color.GREEN : (counter <= 60 ? BossBar.Color.RED : BossBar.Color.YELLOW);
            }
            case INTERMISSION -> {
                title = Text.mm("<green>Journée " + day + " réussie <dark_gray>| <white>prochaine journée dans " + counter + "s");
                progress = counter / (float) Math.max(5, plugin.getConfig().getInt("game.intermission-seconds", 30));
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
                text[4] = "<gray>Arène <white>" + Text.esc(arena.name());
            } else {
                text[1] = "<gray>Journée <white>" + day;
                text[2] = "<gray>Palier <yellow>" + target + symbol + (pot >= target ? " <green>✔" : " <red>✘");
                text[3] = "<gray>Cagnotte <green>" + pot + symbol;
                text[4] = state == State.RUNNING ? "<gray>Temps <white>" + Text.clock(counter) : "<gray>Pause <white>" + counter + "s";
                text[6] = "<gray>Ta pêche <aqua>" + d.dayEarned + symbol;
                text[7] = "<gray>Poissons <white>" + d.fish;
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
