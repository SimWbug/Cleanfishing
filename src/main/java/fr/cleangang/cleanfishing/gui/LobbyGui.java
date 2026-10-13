package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.game.Role;
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

/** Salle d'attente : choix du rôle et vote pour le mode hardcore. */
public final class LobbyGui {

    private static final int[] ROLE_SLOTS = {10, 12, 14};
    public static final int HARDCORE_SLOT = 16;
    private static final int ROSTER_SLOT = 22;

    private LobbyGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Game game;
        private Inventory inventory;

        Holder(Game game) {
            this.game = game;
        }

        public Game game() {
            return game;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(CleanFishingPlugin plugin, Game game, Player player) {
        if (game.state() != Game.State.LOBBY) {
            player.sendMessage(Text.msg("<red>La partie a déjà commencé."));
            return;
        }
        Holder holder = new Holder(game);
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.mm("<dark_aqua>Rôle & mode de jeu"));
        holder.inventory(inventory);
        render(plugin, game, player, inventory);
        player.openInventory(inventory);
    }

    public static void render(CleanFishingPlugin plugin, Game game, Player player, Inventory inventory) {
        ItemStack filler = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.text(" ")));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        Role mine = game.roleOf(player);
        Role[] roles = Role.values();
        for (int i = 0; i < roles.length && i < ROLE_SLOTS.length; i++) {
            Role role = roles[i];
            boolean selected = role == mine;
            ItemStack stack = new ItemStack(role.icon());
            stack.editMeta(m -> {
                m.displayName(Text.item(role.displayName() + (selected ? " <green>✔" : "")));
                List<Component> lore = new ArrayList<>();
                for (String line : role.lore()) {
                    lore.add(Text.item(line));
                }
                lore.add(Component.empty());
                lore.add(Text.item(selected ? "<green>C'est ton rôle." : "<yellow>Clic pour choisir"));
                m.lore(lore);
                if (selected) {
                    m.setEnchantmentGlintOverride(true);
                }
            });
            inventory.setItem(ROLE_SLOTS[i], stack);
        }

        boolean voted = game.votedHardcore(player);
        boolean allowed = plugin.getConfig().getBoolean("hardcore.enabled", true);
        ItemStack hc = new ItemStack(allowed ? (voted ? Material.WITHER_SKELETON_SKULL : Material.SKELETON_SKULL) : Material.BARRIER);
        hc.editMeta(m -> {
            m.displayName(Text.item(allowed ? "<dark_red><bold>Mode hardcore" : "<red>Mode hardcore désactivé"));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("<gray>Paliers <red>x" + plugin.getConfig().getDouble("hardcore.target-multiplier", 1.5)
                    + "<gray>, journées plus courtes,"));
            lore.add(Text.item("<gray>pas de report de cagnotte."));
            lore.add(Text.item("<gray>Poissons payés <green>x" + plugin.getConfig().getDouble("hardcore.value-multiplier", 1.25) + "<gray>."));
            lore.add(Text.item("<gray>Classement à part."));
            lore.add(Component.empty());
            lore.add(Text.item("<gray>Votes : <white>" + game.hardcoreVotes() + "/" + game.hardcoreVotesNeeded()
                    + " <dark_gray>(majorité)"));
            if (allowed) {
                lore.add(Text.item(voted ? "<green>Tu as voté. <yellow>Clic pour retirer" : "<yellow>Clic pour voter"));
            }
            m.lore(lore);
        });
        inventory.setItem(HARDCORE_SLOT, hc);

        ItemStack roster = new ItemStack(Material.WRITABLE_BOOK);
        roster.editMeta(m -> {
            m.displayName(Text.item("<gold>L'équipage"));
            List<Component> lore = new ArrayList<>();
            for (String line : game.roster()) {
                lore.add(Text.item(line));
            }
            lore.add(Component.empty());
            lore.add(Text.item("<dark_gray>Sans choix, un rôle est tiré au sort."));
            m.lore(lore);
        });
        inventory.setItem(ROSTER_SLOT, roster);
    }

    /** Rôle correspondant à un slot, ou null. */
    public static Role roleAt(int slot) {
        Role[] roles = Role.values();
        for (int i = 0; i < ROLE_SLOTS.length && i < roles.length; i++) {
            if (ROLE_SLOTS[i] == slot) {
                return roles[i];
            }
        }
        return null;
    }
}
