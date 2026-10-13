package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Outils admin : liste des arènes, puis panneau de gestion d'une arène. */
public final class AdminGui {

    private static final int START = 10;
    private static final int STOP = 12;
    private static final int TP = 14;
    private static final int INFO = 16;
    private static final int SET_SPAWN = 19;
    private static final int SET_LOBBY = 21;
    private static final int SET_GERTRUDE = 23;
    private static final int SET_FRANCIS = 25;
    private static final int REMOVE_HUT = 29;
    private static final int BACK = 27;
    private static final int DELETE = 35;
    private static final int RADIUS = 31;
    private static final int MAX_ARENAS = 45;

    private AdminGui() {
    }

    public static final class Holder implements InventoryHolder {
        /** Arène du panneau, ou null pour la liste des arènes. */
        private final String arena;
        private final List<String> arenas;
        private Inventory inventory;

        Holder(String arena, List<String> arenas) {
            this.arena = arena;
            this.arenas = arenas;
        }

        String arenaAt(int slot) {
            return slot >= 0 && slot < arenas.size() ? arenas.get(slot) : null;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ------------------------------------------------------------------ ouverture

    public static void openArenas(CleanFishingPlugin plugin, Player player) {
        List<Arena> arenas = new ArrayList<>(plugin.arenas().all());
        if (arenas.size() > MAX_ARENAS) {
            arenas = new ArrayList<>(arenas.subList(0, MAX_ARENAS));
        }
        List<String> names = new ArrayList<>();
        for (Arena a : arenas) {
            names.add(a.name());
        }
        int rows = Math.max(1, (arenas.size() + 8) / 9) + 1;
        Holder holder = new Holder(null, names);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.mm("<red>Outils admin <dark_gray>- arènes"));
        holder.inventory(inventory);

        for (int i = 0; i < arenas.size(); i++) {
            Arena arena = arenas.get(i);
            Game game = plugin.games().forArena(arena.name());
            Material material = !arena.isReady() ? Material.RED_CONCRETE
                    : (game != null && game.state() != Game.State.LOBBY ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE);
            inventory.setItem(i, button(material, "<gold>" + Text.esc(arena.name()),
                    statusLine(arena, game), "", "<yellow>Clic : ouvrir le panneau"));
        }
        inventory.setItem(rows * 9 - 5, button(Material.PAPER, "<aqua>Créer une arène",
                "<gray>Un nom est nécessaire, donc par commande :", "<yellow>/cfish admin create <nom>"));
        player.openInventory(inventory);
    }

    public static void openPanel(CleanFishingPlugin plugin, Player player, Arena arena) {
        Holder holder = new Holder(arena.name(), List.of());
        Inventory inventory = Bukkit.createInventory(holder, 36, Text.mm("<red>Arène <white>" + Text.esc(arena.name())));
        holder.inventory(inventory);
        render(plugin, arena, inventory);
        player.openInventory(inventory);
    }

    private static void render(CleanFishingPlugin plugin, Arena arena, Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.empty()));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        Game game = plugin.games().forArena(arena.name());

        inventory.setItem(START, button(Material.EMERALD, "<green>Lancer la partie",
                "<gray>Démarre tout de suite, sans attendre", "<gray>le compte à rebours.", "", "<yellow>Clic"));
        inventory.setItem(STOP, button(Material.REDSTONE_BLOCK, "<red>Arrêter la partie",
                "<gray>Renvoie tous les joueurs avec", "<gray>leurs affaires.", "", "<yellow>Clic"));
        inventory.setItem(TP, button(Material.ENDER_PEARL, "<aqua>Se téléporter au spawn",
                "<gray>Va sur l'île.", "", "<yellow>Clic"));
        inventory.setItem(INFO, button(Material.PAPER, "<gold>État de l'arène",
                statusLine(arena, game),
                "<gray>Spawn : " + mark(arena.spawnPos() != null),
                "<gray>Lobby : " + mark(arena.lobbyPos() != null) + " <dark_gray>(optionnel)",
                "<gray>Gertrude : " + mark(arena.gertrudePos() != null),
                "<gray>Francis : " + mark(arena.francisPos() != null)));

        inventory.setItem(SET_SPAWN, button(Material.COMPASS, "<yellow>Définir le spawn",
                "<gray>À ta position et ton orientation.", "", "<yellow>Clic"));
        inventory.setItem(SET_LOBBY, button(Material.OAK_SIGN, "<yellow>Définir le lobby",
                "<gray>Salle d'attente (optionnel).", "<gray>À ta position et ton orientation.", "", "<yellow>Clic"));
        inventory.setItem(SET_GERTRUDE, button(Material.GOLD_INGOT, "<yellow>Placer Gertrude <gray>+ cabanon",
                "<gray>Construit un petit cabanon de pêcheur",
                "<gray>autour de toi : Gertrude derrière son comptoir,",
                "<gray>porte dans la direction où tu regardes.",
                "<gray>Place-toi sur un sol plat, ~7x7 blocs dégagés.",
                "",
                "<yellow>Clic : cabanon + Gertrude",
                "<yellow>Shift + clic : Gertrude seule"));
        inventory.setItem(REMOVE_HUT, button(Material.IRON_PICKAXE, "<yellow>Retirer le cabanon",
                plugin.huts().hasHut(arena.name()) ? "<green>Cabanon enregistré." : "<gray>Aucun cabanon enregistré.",
                "<gray>Rend le terrain d'origine.", "", "<yellow>Clic"));
        inventory.setItem(SET_FRANCIS, button(Material.FISHING_ROD, "<yellow>Placer Francis",
                "<gray>Le pêcheur apparaît ici,", "<gray>dans ta direction.", "", "<yellow>Clic"));

        int radius = arena.effectiveRadius(plugin.getConfig().getInt("zone.default-radius", 60));
        inventory.setItem(RADIUS, button(Material.ZOMBIE_HEAD, "<yellow>Zone sans monstres : <white>" + radius + " blocs",
                "<gray>Aucun monstre hostile n'apparaît à moins de",
                "<gray>ce rayon du spawn de l'arène.",
                "",
                "<yellow>Clic gauche : +5   <yellow>Clic droit : -5",
                "<gray>Avec Shift : 1 bloc à la fois"));

        inventory.setItem(BACK, button(Material.ARROW, "<yellow>Retour", "<gray>Liste des arènes."));
        inventory.setItem(DELETE, button(Material.TNT, "<dark_red>Supprimer l'arène",
                "<gray>Arrête la partie et efface l'arène.", "", "<red>Shift + clic pour confirmer"));
    }

    // ------------------------------------------------------------------ clics

    /** Traite un clic. À appeler hors de l'événement d'inventaire (certaines actions touchent à l'inventaire du joueur). */
    public static void handleClick(CleanFishingPlugin plugin, Player player, Holder holder, int slot, ClickType click) {
        if (!player.isOnline() || !player.hasPermission("cleanfishing.admin")) {
            return;
        }
        boolean shift = click.isShiftClick();
        if (holder.arena == null) {
            String name = holder.arenaAt(slot);
            Arena target = name == null ? null : plugin.arenas().get(name);
            if (target != null) {
                openPanel(plugin, player, target);
            }
            return;
        }
        Arena arena = plugin.arenas().get(holder.arena);
        if (arena == null) {
            player.sendMessage(Text.msg("<red>Cette arène n'existe plus."));
            openArenas(plugin, player);
            return;
        }
        Game game = plugin.games().forArena(arena.name());
        switch (slot) {
            case START -> {
                if (game != null && game.forceStart()) {
                    player.sendMessage(Text.msg("<green>Partie lancée."));
                } else {
                    player.sendMessage(Text.msg("<red>Impossible : aucun joueur dans la salle d'attente, ou la partie a déjà commencé."));
                }
                refresh(plugin, player, holder, arena);
            }
            case STOP -> {
                if (game == null) {
                    player.sendMessage(Text.msg("<red>Aucune partie en cours dans cette arène."));
                    return;
                }
                player.closeInventory();
                game.stop("La partie a été arrêtée par un administrateur.");
                player.sendMessage(Text.msg("<green>Partie arrêtée."));
            }
            case TP -> {
                Location spawn = arena.spawn();
                if (spawn == null) {
                    player.sendMessage(Text.msg("<red>Le spawn n'est pas défini (ou son monde n'est pas chargé)."));
                    return;
                }
                player.closeInventory();
                player.teleport(spawn);
            }
            case SET_SPAWN -> setPoint(plugin, player, holder, arena, "Le spawn", arena::spawn);
            case SET_LOBBY -> setPoint(plugin, player, holder, arena, "Le lobby", arena::lobby);
            case SET_GERTRUDE -> {
                boolean withHut = plugin.getConfig().getBoolean("hut.auto-build", true) && !shift;
                if (withHut && game != null) {
                    player.sendMessage(Text.msg("<red>Une partie est en cours : arrête-la avant de construire le cabanon."));
                    return;
                }
                var result = plugin.huts().placeGertrude(arena, player, withHut);
                if (result.spot() == null) {
                    player.sendMessage(Text.msg("<red>" + Text.esc(result.message())));
                    return;
                }
                arena.gertrude(result.spot());
                plugin.arenas().save();
                player.sendMessage(Text.msg("<green>" + Text.esc(result.message()) + " Gertrude est placée pour l'arène <white>"
                        + Text.esc(arena.name()) + "<green>."));
                refresh(plugin, player, holder, arena);
            }
            case REMOVE_HUT -> {
                if (game != null) {
                    player.sendMessage(Text.msg("<red>Une partie est en cours sur cette arène."));
                    return;
                }
                player.sendMessage(plugin.huts().undo(arena.name())
                        ? Text.msg("<green>Cabanon retiré, terrain d'origine rétabli.")
                        : Text.msg("<red>Aucun cabanon enregistré pour cette arène."));
                refresh(plugin, player, holder, arena);
            }
            case SET_FRANCIS -> setPoint(plugin, player, holder, arena, "Francis", arena::francis);
            case RADIUS -> {
                int current = arena.effectiveRadius(plugin.getConfig().getInt("zone.default-radius", 60));
                int step = shift ? 1 : 5;
                int next = click.isRightClick() ? current - step : current + step;
                arena.radius(Math.max(5, Math.min(500, next)));
                plugin.arenas().save();
                plugin.purgeHostiles(arena);
                refresh(plugin, player, holder, arena);
            }
            case BACK -> openArenas(plugin, player);
            case DELETE -> {
                if (!shift) {
                    player.sendMessage(Text.msg("<red>Fais Shift + clic pour confirmer la suppression."));
                    return;
                }
                player.closeInventory();
                if (game != null) {
                    game.stop("L'arène a été supprimée par un administrateur.");
                }
                plugin.arenas().delete(arena.name());
                plugin.huts().forget(arena.name());
                player.sendMessage(Text.msg("<green>Arène <white>" + Text.esc(arena.name()) + "<green> supprimée."));
                openArenas(plugin, player);
            }
            default -> {
            }
        }
    }

    private static void setPoint(CleanFishingPlugin plugin, Player player, Holder holder, Arena arena,
                                 String label, Consumer<Location> setter) {
        setter.accept(player.getLocation());
        plugin.arenas().save();
        player.sendMessage(Text.msg("<green>" + label + " défini à ta position pour l'arène <white>" + Text.esc(arena.name()) + "<green>."));
        refresh(plugin, player, holder, arena);
    }

    /** Remet à jour le panneau ouvert, seulement si c'est toujours celui de ce joueur. */
    private static void refresh(CleanFishingPlugin plugin, Player player, Holder holder, Arena arena) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() == holder) {
            render(plugin, arena, top);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String statusLine(Arena arena, Game game) {
        String ready = arena.isReady() ? "<green>prête" : "<red>incomplète";
        String state = game == null
                ? "libre"
                : game.state().name().toLowerCase(Locale.ROOT) + ", " + game.playerCount() + " joueur(s)";
        return "<gray>" + ready + "<gray> - " + state;
    }

    private static String mark(boolean ok) {
        return ok ? "<green>✔" : "<red>✘";
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(Arrays.stream(lore).map(Text::item).toList());
        });
        return stack;
    }
}
