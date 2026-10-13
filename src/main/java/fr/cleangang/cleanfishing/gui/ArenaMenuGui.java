package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Choix de l'île (arène) : rejoindre, ou changer d'île depuis la salle d'attente. */
public final class ArenaMenuGui {

    private static final int MAX_ARENAS = 54;

    private ArenaMenuGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final List<String> arenas;
        private Inventory inventory;

        Holder(List<String> arenas) {
            this.arenas = arenas;
        }

        /** Nom de l'arène affichée dans ce slot, ou null. */
        public String arenaAt(int slot) {
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

    public static void open(CleanFishingPlugin plugin, Player player) {
        List<Arena> arenas = new ArrayList<>(plugin.arenas().all());
        if (arenas.size() > MAX_ARENAS) {
            arenas = new ArrayList<>(arenas.subList(0, MAX_ARENAS));
        }
        List<String> names = new ArrayList<>();
        for (Arena a : arenas) {
            names.add(a.name());
        }
        int rows = Math.max(1, Math.min(6, (arenas.size() + 8) / 9));
        Holder holder = new Holder(names);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.mm("<gold>Choisis ton île de pêche"));
        holder.inventory(inventory);

        Game current = plugin.games().of(player);
        for (int i = 0; i < arenas.size(); i++) {
            inventory.setItem(i, icon(plugin, arenas.get(i), current));
        }
        if (arenas.isEmpty()) {
            ItemStack none = new ItemStack(Material.BARRIER);
            none.editMeta(m -> {
                m.displayName(Text.item("<red>Aucune île pour le moment"));
                m.lore(List.of(Text.item("<gray>Un admin doit créer une arène :"),
                        Text.item("<yellow>/cfish admin create <nom>")));
            });
            inventory.setItem(4, none);
        }
        player.openInventory(inventory);
    }

    private static ItemStack icon(CleanFishingPlugin plugin, Arena arena, Game current) {
        Game game = plugin.games().forArena(arena.name());
        boolean here = current != null && current.arena().name().equalsIgnoreCase(arena.name());
        int max = plugin.getConfig().getInt("game.max-players", 8);

        Material material;
        List<Component> lore = new ArrayList<>();
        if (!arena.isReady()) {
            material = Material.RED_CONCRETE;
            lore.add(Text.item("<red>Île incomplète"));
            lore.add(Text.item("<dark_gray>Manquant : " + String.join(", ", arena.missing())));
        } else if (game != null && game.state() != Game.State.LOBBY) {
            material = Material.YELLOW_CONCRETE;
            lore.add(Text.item("<yellow>Partie en cours <gray>- journée " + game.day()));
            lore.add(Text.item("<gray>Joueurs : <white>" + game.playerCount() + "/" + max));
        } else {
            material = Material.LIME_CONCRETE;
            int count = game == null ? 0 : game.playerCount();
            lore.add(Text.item("<green>En attente de joueurs"));
            lore.add(Text.item("<gray>Joueurs : <white>" + count + "/" + max));
            lore.add(Component.empty());
            lore.add(Text.item(here ? "<aqua>Tu es ici" : "<yellow>Clic pour rejoindre"));
        }

        ItemStack stack = new ItemStack(material);
        stack.editMeta(m -> {
            m.displayName(Text.item("<gold>" + Text.esc(arena.name())));
            m.lore(lore);
            if (here) {
                m.setEnchantmentGlintOverride(true);
            }
        });
        return stack;
    }

    /**
     * Rejoint l'île choisie. Depuis une salle d'attente, on quitte d'abord l'île actuelle
     * (seulement si la nouvelle est rejoignable, pour ne jamais laisser le joueur sans partie).
     * À appeler hors d'un événement d'inventaire : on manipule l'inventaire du joueur.
     */
    public static void select(CleanFishingPlugin plugin, Player player, Arena arena) {
        player.closeInventory();
        Game current = plugin.games().of(player);
        if (current != null) {
            if (current.arena().name().equalsIgnoreCase(arena.name())) {
                player.sendMessage(Text.msg("<red>Tu es déjà sur cette île."));
                return;
            }
            if (current.state() != Game.State.LOBBY) {
                player.sendMessage(Text.msg("<red>Tu ne peux pas changer d'île en pleine partie. <gray>/cfish leave pour la quitter."));
                return;
            }
            String blocked = plugin.games().joinError(arena);
            if (blocked != null) {
                player.sendMessage(Text.msg("<red>" + blocked));
                return;
            }
            current.leave(player, true);
        }
        String error = plugin.games().tryJoin(player, arena);
        if (error != null) {
            player.sendMessage(Text.msg("<red>" + error));
        }
    }
}
