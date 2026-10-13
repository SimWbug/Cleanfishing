package fr.cleangang.cleanfishing.cards;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.util.Fx;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Cartes de pouvoir en 3D : au passage de certains niveaux, des cartes holographiques apparaissent devant le joueur,
 * flottent et le suivent. Il vise une carte (elle grossit et son icône brille) et clique pour la prendre.
 * <p>
 * Tout est fait d'entités d'affichage (TextDisplay / ItemDisplay) visibles uniquement par ce joueur : aucune
 * interface d'inventaire, le jeu continue pendant le choix. Avec le resource pack, les cartes ont un cadre illustré.
 */
public final class CardManager implements Listener {

    private static final double DISTANCE = 2.3;
    private static final double SPACING_DEG = 30;
    /** Taille du texte des cartes (1 pixel de texte = 0,025 bloc x SCALE). */
    private static final float SCALE = 0.3f;
    /** Hauteur des cartes en blocs : 18 lignes (cadre illustré, 180 px) ou 12 lignes (fond coloré). */
    private static final double PACK_HEIGHT = 180 * 0.025 * SCALE;
    private static final double PLAIN_HEIGHT = 120 * 0.025 * SCALE;
    private static final double BOTTOM_BELOW_EYE = 0.95;
    private static final double SELECT_COS = Math.cos(Math.toRadians(13));
    private static final Key CARD_FONT = Key.key("cleanfishing", "card");

    /** Un choix de cartes en cours pour un joueur. */
    private static final class Choice {
        final Game game;
        final List<Power> powers;
        final boolean pack;
        final int level;
        final List<TextDisplay> cards = new ArrayList<>();
        final List<ItemDisplay> icons = new ArrayList<>();
        TextDisplay header;
        TextDisplay hint;
        float anchorYaw;
        int ticks;
        int highlighted = -1;
        float spin;
        boolean picked;

        Choice(Game game, List<Power> powers, boolean pack, int level) {
            this.game = game;
            this.powers = powers;
            this.pack = pack;
            this.level = level;
        }

        List<Entity> all() {
            List<Entity> list = new ArrayList<>(cards);
            list.addAll(icons);
            if (header != null) {
                list.add(header);
            }
            if (hint != null) {
                list.add(hint);
            }
            return list;
        }
    }

    private final CleanFishingPlugin plugin;
    private final Map<UUID, Choice> active = new HashMap<>();
    private final Map<UUID, Deque<List<Power>>> queued = new HashMap<>();
    private final Map<UUID, Long> lastPick = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask task;

    public CardManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 2L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Choice choice : active.values()) {
            removeAll(choice);
        }
        active.clear();
        queued.clear();
    }

    public boolean choosing(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ ouverture

    /** Propose des cartes au joueur (mises en file s'il est déjà en train de choisir). */
    public void offer(Player player, Game game, List<Power> powers) {
        if (powers.isEmpty()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (active.containsKey(id)) {
            queued.computeIfAbsent(id, k -> new ArrayDeque<>()).add(powers);
            player.sendActionBar(Text.mm("<gold>Une autre carte t'attend après celle-ci !"));
            return;
        }
        open(player, game, powers);
    }

    private void open(Player player, Game game, List<Power> powers) {
        boolean pack = plugin.pack().uses(player);
        Choice choice = new Choice(game, powers, pack, game.levelOf(player));
        choice.anchorYaw = player.getLocation().getYaw();
        World world = player.getWorld();
        Location eye = player.getEyeLocation();

        for (int i = 0; i < powers.size(); i++) {
            Power power = powers.get(i);
            Location at = cardBottom(eye, choice, i);
            TextDisplay card = world.spawn(at, TextDisplay.class, d -> {
                prepare(d);
                d.text(cardText(power, pack, choice.game, player));
                d.setLineWidth(160);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBackgroundColor(pack ? Color.fromARGB(0, 0, 0, 0) : power.tier().background());
                d.setShadowed(!pack);
                d.setTransformation(hidden(scale(pack)));
            });
            ItemDisplay icon = world.spawn(at.clone().add(0, height(choice) + 0.32, 0), ItemDisplay.class, d -> {
                prepare(d);
                d.setItemStack(new ItemStack(power.icon()));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                d.setTransformation(hidden(0.55f));
                d.setGlowColorOverride(glow(power.tier()));
            });
            show(player, card);
            show(player, icon);
            choice.cards.add(card);
            choice.icons.add(icon);
        }
        choice.header = world.spawn(headerLocation(eye, choice), TextDisplay.class, d -> {
            prepare(d);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setBackgroundColor(Color.fromARGB(150, 8, 18, 30));
            d.setShadowed(true);
            d.text(headerText(choice, timeout()));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f), new AxisAngle4f()));
        });
        choice.hint = world.spawn(eye, TextDisplay.class, d -> {
            prepare(d);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.text(Text.mm("<yellow><bold>▲ CLIC ▲"));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0f), new AxisAngle4f()));
        });
        show(player, choice.header);
        show(player, choice.hint);
        active.put(player.getUniqueId(), choice);

        player.showTitle(Title.title(Text.mm("<gold><bold>✦ Niveau " + choice.level + " ✦"),
                Text.mm("<gray>Vise une carte et clique pour la prendre"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(400))));
        Fx.sound(player, "block.enchantment_table.use", 1f, 1.2f);
    }

    private void prepare(Display d) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.setBillboard(Display.Billboard.VERTICAL);
        d.setTeleportDuration(3);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(0.5f);
    }

    private void show(Player player, Entity entity) {
        player.showEntity(plugin, entity);
    }

    private static float scale(boolean pack) {
        return SCALE;
    }

    private static double height(Choice choice) {
        return choice.pack ? PACK_HEIGHT : PLAIN_HEIGHT;
    }

    /** Carte retournée et minuscule : point de départ de l'animation d'apparition. */
    private static Transformation hidden(float scale) {
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.PI, 0, 1, 0),
                new Vector3f(scale * 0.05f), new AxisAngle4f());
    }

    private static Transformation shown(float scale, float spin) {
        return new Transformation(new Vector3f(), new AxisAngle4f(spin, 0, 1, 0), new Vector3f(scale), new AxisAngle4f());
    }

    private static Color glow(CardTier tier) {
        return switch (tier) {
            case COMMON -> Color.fromRGB(0x9FD8FF);
            case RARE -> Color.fromRGB(0xC58CFF);
            case EPIC -> Color.fromRGB(0xFFCC4D);
        };
    }

    private Component cardText(Power power, boolean pack, Game game, Player player) {
        int stacks = game.powersOf(player).getOrDefault(power, 0);
        String level = power.instant() ? "<dark_gray>effet immédiat"
                : stacks > 0 ? "<dark_gray>tu l'as déjà : x" + stacks + " → x" + (stacks + 1) : "<dark_gray>cumulable";
        if (pack) {
            // ligne 0 : le cadre illustré (180 px de haut) ; le texte tombe sur le parchemin (lignes 6 à 11) et le bandeau (16)
            String ink = "<#3b2a1a>";
            // deux composants frères : le texte ne doit pas hériter de la police du cadre
            return Component.text().append(Component.text(String.valueOf(power.tier().frame())).font(CARD_FONT))
                    .append(Text.mm("\n\n\n\n\n\n<#2a1a0a><bold>" + Text.esc(power.displayName()) + "</bold>\n\n"
                            + ink + Text.esc(power.line1()) + "\n" + ink + Text.esc(power.line2()) + "\n\n"
                            + level + "\n\n\n\n\n<white><bold>" + power.tier().label().toUpperCase())).build();
        }
        return Text.mm("\n" + power.tier().color() + "<bold>✦ " + power.tier().label().toUpperCase() + " ✦</bold>\n\n"
                + "<white><bold>" + Text.esc(power.displayName()) + "</bold>\n\n"
                + "<gray>" + Text.esc(power.line1()) + "\n<gray>" + Text.esc(power.line2()) + "\n\n" + level + "\n");
    }

    private static Component headerText(Choice choice, int secondsLeft) {
        return Text.mm("<gold><bold>✦ NIVEAU " + choice.level + " ✦</bold>\n<gray>Choisis un pouvoir <dark_gray>- <white>" + secondsLeft + "s");
    }

    private int timeout() {
        return Math.max(5, plugin.getConfig().getInt("cards.timeout-seconds", 30));
    }

    // ------------------------------------------------------------------ positions

    private static Vector forward(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static double angleOf(Choice choice, int index) {
        return (index - (choice.powers.size() - 1) / 2.0) * SPACING_DEG;
    }

    private static Location cardBottom(Location eye, Choice choice, int index) {
        Vector dir = forward((float) (choice.anchorYaw + angleOf(choice, index)));
        return eye.clone().add(dir.multiply(DISTANCE)).add(0, -BOTTOM_BELOW_EYE, 0);
    }

    private static Location headerLocation(Location eye, Choice choice) {
        return eye.clone().add(forward(choice.anchorYaw).multiply(DISTANCE + 0.2)).add(0, height(choice) - BOTTOM_BELOW_EYE + 0.75, 0);
    }

    private void place(Player player, Choice choice) {
        Location eye = player.getEyeLocation();
        float yaw = eye.getYaw();
        float diff = Math.abs(((yaw - choice.anchorYaw) % 360 + 540) % 360 - 180);
        if (diff > 70) {
            choice.anchorYaw = yaw; // le joueur s'est retourné : les cartes reviennent devant lui
        }
        for (int i = 0; i < choice.cards.size(); i++) {
            Location bottom = cardBottom(eye, choice, i);
            choice.cards.get(i).teleport(bottom);
            choice.icons.get(i).teleport(bottom.clone().add(0, height(choice) + 0.32, 0));
        }
        choice.header.teleport(headerLocation(eye, choice));
        if (choice.highlighted >= 0) {
            choice.hint.teleport(cardBottom(eye, choice, choice.highlighted).add(0, -0.3, 0));
        }
    }

    /** Carte visée (dans un cône de 13°), ou -1. */
    private int aimed(Player player, Choice choice) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        int best = -1;
        double bestDot = SELECT_COS;
        for (int i = 0; i < choice.cards.size(); i++) {
            Location center = cardBottom(eye, choice, i).add(0, height(choice) / 2, 0);
            Vector to = center.toVector().subtract(eye.toVector()).normalize();
            double dot = to.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ animation

    private void tick() {
        for (Map.Entry<UUID, Choice> entry : new ArrayList<>(active.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Choice choice = entry.getValue();
            if (player == null || plugin.games().of(player) != choice.game || choice.game.state() == Game.State.ENDING) {
                removeAll(choice);
                active.remove(entry.getKey());
                queued.remove(entry.getKey());
                continue;
            }
            if (choice.picked) {
                continue;
            }
            choice.ticks += 2;
            int n = choice.cards.size();
            // apparition en cascade : chaque carte se retourne et grandit
            for (int i = 0; i < n; i++) {
                if (choice.ticks == 4 + i * 4) {
                    flipIn(player, choice, i);
                }
            }
            place(player, choice);
            boolean ready = choice.ticks >= 4 + n * 4 + 8;
            if (ready) {
                int target = aimed(player, choice);
                if (target != choice.highlighted) {
                    highlight(player, choice, target);
                }
            }
            if (choice.ticks % 10 == 0) {
                spinIcons(choice);
            }
            int left = timeout() - choice.ticks / 20;
            if (choice.ticks % 20 == 0) {
                choice.header.text(headerText(choice, Math.max(0, left)));
                if (left <= 5 && left > 0) {
                    Fx.sound(player, "block.note_block.hat", 0.6f, 1.6f);
                }
            }
            if (left <= 0) {
                int index = choice.highlighted >= 0 ? choice.highlighted : random.nextInt(n);
                player.sendMessage(Text.msg("<gray>Temps écoulé : la carte <white>" + choice.powers.get(index).displayName()
                        + "<gray> est choisie pour toi."));
                pick(player, choice, index);
            }
        }
    }

    private void flipIn(Player player, Choice choice, int i) {
        TextDisplay card = choice.cards.get(i);
        card.setInterpolationDelay(0);
        card.setInterpolationDuration(8);
        card.setTransformation(shown(scale(choice.pack), 0f));
        ItemDisplay icon = choice.icons.get(i);
        icon.setInterpolationDelay(0);
        icon.setInterpolationDuration(8);
        icon.setTransformation(shown(0.55f, 0f));
        Fx.sound(player, "item.book.page_turn", 1f, 0.9f + i * 0.15f);
        Fx.particle(card.getLocation().add(0, height(choice) / 2, 0), "enchant", 25, 0.3, 0.5, 0.3, 0.5);
    }

    private void highlight(Player player, Choice choice, int target) {
        int previous = choice.highlighted;
        choice.highlighted = target;
        float base = scale(choice.pack);
        if (previous >= 0) {
            TextDisplay old = choice.cards.get(previous);
            old.setInterpolationDelay(0);
            old.setInterpolationDuration(3);
            old.setTransformation(shown(base, 0f));
            if (!choice.pack) {
                old.setBackgroundColor(choice.powers.get(previous).tier().background());
            }
            choice.icons.get(previous).setGlowing(false);
        }
        if (target >= 0) {
            TextDisplay card = choice.cards.get(target);
            card.setInterpolationDelay(0);
            card.setInterpolationDuration(3);
            card.setTransformation(new Transformation(new Vector3f(0, 0.06f, 0), new AxisAngle4f(),
                    new Vector3f(base * 1.18f), new AxisAngle4f()));
            if (!choice.pack) {
                Color bg = choice.powers.get(target).tier().background();
                card.setBackgroundColor(Color.fromARGB(245, Math.min(255, bg.getRed() + 45),
                        Math.min(255, bg.getGreen() + 45), Math.min(255, bg.getBlue() + 45)));
            }
            choice.icons.get(target).setGlowing(true);
            choice.hint.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f), new AxisAngle4f()));
            Fx.sound(player, "block.amethyst_block.chime", 0.8f, 1.3f + target * 0.1f);
            Power power = choice.powers.get(target);
            player.sendActionBar(Text.mm(power.tier().color() + "<bold>" + power.displayName() + "</bold> <dark_gray>- <gray>"
                    + power.line1() + " " + power.line2() + "  <yellow>[clic]"));
        } else {
            choice.hint.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0f), new AxisAngle4f()));
        }
    }

    private void spinIcons(Choice choice) {
        choice.spin += (float) (Math.PI / 2);
        for (int i = 0; i < choice.icons.size(); i++) {
            ItemDisplay icon = choice.icons.get(i);
            float extra = i == choice.highlighted ? (float) Math.PI / 2 : 0f;
            icon.setInterpolationDelay(0);
            icon.setInterpolationDuration(10);
            icon.setTransformation(shown(i == choice.highlighted ? 0.7f : 0.55f, choice.spin + extra));
        }
    }

    // ------------------------------------------------------------------ choix

    /**
     * Clic du joueur : prend la carte visée. Renvoie true si le clic a servi à choisir une carte (ou vient juste de
     * le faire) : l'appelant annule alors l'action normale (lancer de canne, objet...).
     */
    public boolean consumeClick(Player player) {
        Long last = lastPick.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 300) {
            return true;
        }
        Choice choice = active.get(player.getUniqueId());
        if (choice == null || choice.picked || choice.highlighted < 0) {
            return false;
        }
        pick(player, choice, choice.highlighted);
        return true;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            consumeClick(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
        lastPick.remove(event.getPlayer().getUniqueId());
    }

    private void pick(Player player, Choice choice, int index) {
        choice.picked = true;
        lastPick.put(player.getUniqueId(), System.currentTimeMillis());
        Power power = choice.powers.get(index);
        choice.game.applyPower(player, power);

        // la carte choisie s'envole vers le joueur, les autres disparaissent
        float base = scale(choice.pack);
        for (int i = 0; i < choice.cards.size(); i++) {
            TextDisplay card = choice.cards.get(i);
            ItemDisplay icon = choice.icons.get(i);
            card.setInterpolationDelay(0);
            icon.setInterpolationDelay(0);
            if (i == index) {
                card.setInterpolationDuration(6);
                card.setTransformation(new Transformation(new Vector3f(0, 0.35f, 0), new AxisAngle4f(), new Vector3f(base * 1.45f), new AxisAngle4f()));
                icon.setInterpolationDuration(6);
                icon.setTransformation(shown(1.1f, choice.spin + (float) Math.PI));
                Fx.particle(card.getLocation().add(0, height(choice) / 2, 0), "totem_of_undying", 40, 0.4, 0.6, 0.4, 0.3);
            } else {
                card.setInterpolationDuration(5);
                card.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.PI / 2, 0, 1, 0), new Vector3f(0.001f), new AxisAngle4f()));
                icon.setInterpolationDuration(5);
                icon.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.001f), new AxisAngle4f()));
            }
        }
        choice.header.text(Text.mm(power.tier().color() + "<bold>" + power.displayName() + " !"));
        choice.hint.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0f), new AxisAngle4f()));
        Fx.sound(player, "entity.player.levelup", 1f, 1.4f);
        Fx.sound(player, "item.trident.return", 1f, 1.2f);
        player.showTitle(Title.title(Text.mm(power.tier().color() + "<bold>" + power.displayName()),
                Text.mm("<gray>" + power.line1() + " " + power.line2()),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(400))));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TextDisplay chosen = choice.cards.get(index);
            if (chosen.isValid() && player.isOnline()) {
                chosen.setTeleportDuration(6);
                chosen.teleport(player.getLocation().add(0, 0.8, 0));
                chosen.setInterpolationDelay(0);
                chosen.setInterpolationDuration(6);
                chosen.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.02f), new AxisAngle4f()));
            }
        }, 12L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeAll(choice);
            if (active.get(player.getUniqueId()) == choice) {
                active.remove(player.getUniqueId());
            }
            Deque<List<Power>> next = queued.get(player.getUniqueId());
            if (next != null && !next.isEmpty() && player.isOnline() && plugin.games().of(player) == choice.game) {
                List<Power> powers = next.poll();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.games().of(player) == choice.game && !active.containsKey(player.getUniqueId())) {
                        open(player, choice.game, powers);
                    }
                }, 10L);
            }
        }, 20L);
    }

    /** Retire les cartes de ce joueur (il quitte la partie, la partie s'arrête...). */
    public void cancel(Player player) {
        Choice choice = active.remove(player.getUniqueId());
        if (choice != null) {
            removeAll(choice);
        }
        queued.remove(player.getUniqueId());
    }

    private void removeAll(Choice choice) {
        for (Entity e : choice.all()) {
            if (e.isValid()) {
                e.remove();
            }
        }
    }
}
