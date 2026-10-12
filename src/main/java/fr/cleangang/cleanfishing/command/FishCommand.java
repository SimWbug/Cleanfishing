package fr.cleangang.cleanfishing.command;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.gui.CodexGui;
import fr.cleangang.cleanfishing.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /cfish : commandes joueur (join, leave, codex) et administrateur (admin ...). */
public final class FishCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUB = List.of(
            "create", "delete", "list", "info", "tp", "setspawn", "setlobby", "setgertrude", "setfrancis",
            "start", "stop", "reload");

    private final CleanFishingPlugin plugin;

    public FishCommand(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "codex" -> codex(sender);
            case "admin" -> {
                if (!sender.hasPermission("cleanfishing.admin")) {
                    sender.sendMessage(Text.msg("<red>Tu n'as pas la permission."));
                } else {
                    admin(sender, args);
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.msg("<yellow>/cfish join [arène]<gray> - rejoindre une partie"));
        sender.sendMessage(Text.msg("<yellow>/cfish leave<gray> - quitter la partie"));
        sender.sendMessage(Text.msg("<yellow>/cfish codex<gray> - ouvrir ton codex des poissons"));
        if (sender.hasPermission("cleanfishing.admin")) {
            sender.sendMessage(Text.msg("<gold>/cfish admin <create|delete|list|info|tp|setspawn|setlobby|setgertrude|setfrancis|start|stop|reload>"));
        }
    }

    // ------------------------------------------------------------------ joueur

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.msg("<red>Commande réservée aux joueurs."));
            return;
        }
        if (!player.hasPermission("cleanfishing.play")) {
            player.sendMessage(Text.msg("<red>Tu n'as pas la permission."));
            return;
        }
        if (plugin.games().of(player) != null) {
            player.sendMessage(Text.msg("<red>Tu es déjà dans une partie. <gray>/cfish leave pour la quitter."));
            return;
        }
        if (plugin.states().hasPending(player.getUniqueId())) {
            player.sendMessage(Text.msg("<red>Tes affaires d'avant-partie sont en cours de restauration, réessaie dans un instant."));
            return;
        }
        Arena arena;
        if (args.length >= 2) {
            arena = plugin.arenas().get(args[1]);
            if (arena == null) {
                player.sendMessage(Text.msg("<red>Arène inconnue : " + Text.esc(args[1])));
                return;
            }
        } else {
            arena = firstJoinable();
            if (arena == null) {
                player.sendMessage(Text.msg("<red>Aucune arène disponible pour le moment."));
                return;
            }
        }
        if (!arena.isReady()) {
            player.sendMessage(Text.msg("<red>Cette arène n'est pas prête <gray>(points manquants ou monde non chargé)."));
            return;
        }
        Game existing = plugin.games().forArena(arena.name());
        if (existing != null && existing.state() != Game.State.LOBBY) {
            player.sendMessage(Text.msg("<red>Une partie est déjà en cours dans cette arène."));
            return;
        }
        Game game = plugin.games().getOrCreate(arena);
        if (!game.join(player)) {
            player.sendMessage(Text.msg("<red>Impossible de rejoindre : la partie est pleine ou a déjà commencé."));
            game.discardIfEmpty();
        }
    }

    private Arena firstJoinable() {
        for (Arena arena : plugin.arenas().all()) {
            if (!arena.isReady()) {
                continue;
            }
            Game game = plugin.games().forArena(arena.name());
            if (game == null || game.state() == Game.State.LOBBY) {
                return arena;
            }
        }
        return null;
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.msg("<red>Commande réservée aux joueurs."));
            return;
        }
        Game game = plugin.games().of(player);
        if (game == null) {
            player.sendMessage(Text.msg("<red>Tu n'es dans aucune partie."));
            return;
        }
        game.leave(player, false);
    }

    private void codex(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.msg("<red>Commande réservée aux joueurs."));
            return;
        }
        if (!player.hasPermission("cleanfishing.play")) {
            player.sendMessage(Text.msg("<red>Tu n'as pas la permission."));
            return;
        }
        CodexGui.open(plugin, player, 0);
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.msg("<gold>Sous-commandes : <white>" + String.join(", ", ADMIN_SUB)));
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (plugin.arenas().all().isEmpty()) {
                    sender.sendMessage(Text.msg("Aucune arène. Crée-en une avec <yellow>/cfish admin create <nom>"));
                    return;
                }
                for (Arena a : plugin.arenas().all()) {
                    Game g = plugin.games().forArena(a.name());
                    String status = g == null ? "libre" : g.state().name().toLowerCase(Locale.ROOT) + ", " + g.playerCount() + " joueur(s)";
                    sender.sendMessage(Text.msg("<yellow>" + Text.esc(a.name()) + " <gray>- " + (a.isReady() ? "<green>prête" : "<red>incomplète")
                            + " <dark_gray>(" + status + ")"));
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(Text.msg("<green>Configuration, catalogue de poissons et arènes rechargés."));
            }
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(Text.msg("<red>Usage : /cfish admin create <nom>"));
                    return;
                }
                if (!args[2].matches("[A-Za-z0-9_-]{1,32}")) {
                    sender.sendMessage(Text.msg("<red>Nom invalide (lettres, chiffres, - et _ uniquement)."));
                    return;
                }
                if (plugin.arenas().get(args[2]) != null) {
                    sender.sendMessage(Text.msg("<red>Cette arène existe déjà."));
                    return;
                }
                plugin.arenas().create(args[2]);
                sender.sendMessage(Text.msg("<green>Arène <white>" + Text.esc(args[2]) + "<green> créée. Définis maintenant :"
                        + " <yellow>setspawn<gray>, <yellow>setgertrude<gray>, <yellow>setfrancis<gray> (et <yellow>setlobby<gray> en option)."));
            }
            default -> adminOnArena(sender, sub, args);
        }
    }

    private void adminOnArena(CommandSender sender, String sub, String[] args) {
        if (!ADMIN_SUB.contains(sub)) {
            sender.sendMessage(Text.msg("<gold>Sous-commandes : <white>" + String.join(", ", ADMIN_SUB)));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.msg("<red>Usage : /cfish admin " + sub + " <arène>"));
            return;
        }
        Arena arena = plugin.arenas().get(args[2]);
        if (arena == null) {
            sender.sendMessage(Text.msg("<red>Arène inconnue : " + Text.esc(args[2])));
            return;
        }
        switch (sub) {
            case "delete" -> {
                Game game = plugin.games().forArena(arena.name());
                if (game != null) {
                    game.stop("L'arène a été supprimée par un administrateur.");
                }
                plugin.arenas().delete(arena.name());
                sender.sendMessage(Text.msg("<green>Arène supprimée."));
            }
            case "info" -> {
                sender.sendMessage(Text.msg("<yellow>" + Text.esc(arena.name()) + "<gray> - " + (arena.isReady() ? "<green>prête" : "<red>incomplète")));
                sender.sendMessage(Text.msg("<gray>spawn: " + mark(arena.spawnPos() != null) + " lobby: " + mark(arena.lobbyPos() != null)
                        + " gertrude: " + mark(arena.gertrudePos() != null) + " francis: " + mark(arena.francisPos() != null)));
                if (!arena.missing().isEmpty()) {
                    sender.sendMessage(Text.msg("<red>Manquant : <white>" + String.join(", ", arena.missing())));
                }
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Text.msg("<red>Commande réservée aux joueurs."));
                    return;
                }
                Location loc = arena.spawn();
                if (loc == null) {
                    sender.sendMessage(Text.msg("<red>Le spawn n'est pas défini (ou son monde n'est pas chargé)."));
                    return;
                }
                player.teleport(loc);
            }
            case "setspawn", "setlobby", "setgertrude", "setfrancis" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Text.msg("<red>Commande réservée aux joueurs."));
                    return;
                }
                Location here = player.getLocation();
                switch (sub) {
                    case "setspawn" -> arena.spawn(here);
                    case "setlobby" -> arena.lobby(here);
                    case "setgertrude" -> arena.gertrude(here);
                    default -> arena.francis(here);
                }
                plugin.arenas().save();
                sender.sendMessage(Text.msg("<green>" + sub.substring(3) + " défini à ta position pour l'arène <white>" + Text.esc(arena.name())
                        + "<green>."
                        + (sub.equals("setgertrude") || sub.equals("setfrancis")
                        ? " <gray>Le PNJ regardera dans la même direction que toi." : "")));
            }
            case "start" -> {
                Game game = plugin.games().forArena(arena.name());
                if (game == null || !game.forceStart()) {
                    sender.sendMessage(Text.msg("<red>Impossible : aucun joueur dans le lobby de cette arène, ou la partie a déjà commencé."));
                    return;
                }
                sender.sendMessage(Text.msg("<green>Partie lancée."));
            }
            case "stop" -> {
                Game game = plugin.games().forArena(arena.name());
                if (game == null) {
                    sender.sendMessage(Text.msg("<red>Aucune partie en cours dans cette arène."));
                    return;
                }
                game.stop("La partie a été arrêtée par un administrateur.");
                sender.sendMessage(Text.msg("<green>Partie arrêtée."));
            }
            default -> {
            }
        }
    }

    private static String mark(boolean ok) {
        return ok ? "<green>✔<gray>" : "<red>✘<gray>";
    }

    // ------------------------------------------------------------------ complétion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("cleanfishing.admin");
        if (args.length == 1) {
            out.addAll(List.of("join", "leave", "codex"));
            if (admin) {
                out.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            out.addAll(plugin.arenas().names());
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                out.addAll(ADMIN_SUB);
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("reload")
                    && !args[1].equalsIgnoreCase("list")) {
                out.addAll(plugin.arenas().names());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
