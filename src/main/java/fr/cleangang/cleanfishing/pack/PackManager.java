package fr.cleangang.cleanfishing.pack;

import com.sun.net.httpserver.HttpServer;
import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Resource pack des interfaces (boutique de Gertrude, Francis).
 * <p>
 * Le pack est rangé, modifiable, dans {@code plugins/CleanFishing/pack/} (extrait du plugin au premier démarrage).
 * Au démarrage et à chaque {@code /cfish admin reload}, ce dossier est zippé ; le zip est soit servi par le plugin
 * lui-même (petit serveur HTTP intégré), soit téléversé par l'admin ailleurs (resource-pack.url).
 * Le pack est envoyé EN PLUS des autres packs du serveur (il ne les remplace pas). Les interfaces n'utilisent
 * le style du pack que pour les joueurs qui l'ont réellement chargé ; les autres gardent les interfaces classiques.
 */
public final class PackManager implements Listener {

    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("cleanfishing:resource-pack".getBytes(StandardCharsets.UTF_8));
    private static final String EMBEDDED = "CleanFishing-pack.zip";

    private final CleanFishingPlugin plugin;
    private final Set<UUID> loaded = new HashSet<>();
    private final Set<UUID> sent = new HashSet<>();
    private byte[] zip;
    private String sha1 = "";
    private HttpServer server;
    private boolean warnedNoUrl;

    public PackManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("resource-pack.enabled", false);
    }

    private File folder() {
        return new File(plugin.getDataFolder(), "pack");
    }

    /** (Re)construit le zip à partir du dossier pack/ et (re)démarre le serveur intégré si besoin. */
    public void load() {
        stopServer();
        zip = null;
        sha1 = "";
        if (!enabled()) {
            return;
        }
        try {
            extractDefaultIfMissing();
            zip = zipFolder(folder().toPath());
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
            Files.write(new File(plugin.getDataFolder(), EMBEDDED).toPath(), zip);
        } catch (IOException | NoSuchAlgorithmException ex) {
            plugin.getLogger().warning("Resource pack : impossible de préparer le pack (" + ex.getMessage() + ").");
            zip = null;
            return;
        }
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if ((url == null || url.isBlank()) && plugin.getConfig().getBoolean("resource-pack.self-host.enabled", true)) {
            startServer();
        }
        plugin.getLogger().info("Resource pack prêt (" + zip.length / 1024 + " Ko, sha1 " + sha1 + ") : " + url());
    }

    public void shutdown() {
        stopServer();
    }

    /** Copie le pack livré avec le plugin dans plugins/CleanFishing/pack/ s'il n'y est pas encore. */
    private void extractDefaultIfMissing() throws IOException {
        File dir = folder();
        if (new File(dir, "pack.mcmeta").exists()) {
            return;
        }
        try (InputStream in = plugin.getResource(EMBEDDED)) {
            if (in == null) {
                throw new IOException(EMBEDDED + " absent du plugin");
            }
            try (ZipInputStream zin = new ZipInputStream(in)) {
                ZipEntry entry;
                Path root = dir.toPath().toAbsolutePath().normalize();
                while ((entry = zin.getNextEntry()) != null) {
                    Path target = root.resolve(entry.getName()).normalize();
                    if (!target.startsWith(root)) {
                        continue; // chemin suspect
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zin, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        plugin.getLogger().info("Resource pack extrait dans " + dir.getPath() + " (modifiable).");
    }

    /** Zip reproductible (ordre trié, dates fixes) : le même dossier donne toujours le même sha1. */
    private static byte[] zipFolder(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).sorted().forEach(files::add);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Path file : files) {
                String name = root.relativize(file).toString().replace(File.separatorChar, '/');
                if (name.startsWith(".") || name.contains("/.")) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                out.putNextEntry(entry);
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private void startServer() {
        int port = plugin.getConfig().getInt("resource-pack.self-host.port", 8164);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                byte[] body = zip;
                if (body == null || !exchange.getRequestURI().getPath().endsWith(".zip")) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.start();
            plugin.getLogger().info("Resource pack servi sur le port " + port + ".");
        } catch (IOException ex) {
            server = null;
            plugin.getLogger().warning("Resource pack : impossible d'ouvrir le port " + port + " (" + ex.getMessage()
                    + "). Change resource-pack.self-host.port ou renseigne resource-pack.url.");
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** URL envoyée aux joueurs, ou chaîne vide si rien n'est configuré. */
    public String url() {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url != null && !url.isBlank()) {
            return url.trim();
        }
        String address = plugin.getConfig().getString("resource-pack.self-host.public-address", "");
        if (address == null || address.isBlank()) {
            return "";
        }
        int port = plugin.getConfig().getInt("resource-pack.self-host.port", 8164);
        // le sha1 dans le nom force les joueurs à retélécharger après une modification
        return "http://" + address.trim() + ":" + port + "/CleanFishing-" + sha1.substring(0, Math.min(8, sha1.length())) + ".zip";
    }

    private String hash() {
        String configured = plugin.getConfig().getString("resource-pack.sha1", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim().toLowerCase();
        }
        return sha1;
    }

    /** Envoie le pack à ce joueur (s'il est activé et pas déjà chargé). */
    public void send(Player player) {
        if (!enabled() || zip == null || loaded.contains(player.getUniqueId())) {
            return;
        }
        String url = url();
        if (url.isEmpty()) {
            if (!warnedNoUrl) {
                warnedNoUrl = true;
                plugin.getLogger().warning("Resource pack activé mais ni resource-pack.url ni resource-pack.self-host.public-address"
                        + " ne sont renseignés : rien n'est envoyé.");
            }
            return;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Resource pack : URL invalide " + url);
            return;
        }
        sent.add(player.getUniqueId());
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, hash()))
                .replace(false)
                .required(plugin.getConfig().getBoolean("resource-pack.required", false))
                .prompt(Text.mm(plugin.getConfig().getString("resource-pack.prompt",
                        "<aqua>CleanFishing <gray>: interfaces illustrées de Gertrude et Francis")))
                .build());
    }

    /** Appelé quand un joueur rejoint une partie (mode d'envoi « game »). */
    public void onGameJoin(Player player) {
        if ("game".equalsIgnoreCase(plugin.getConfig().getString("resource-pack.send-on", "game")) && !sent.contains(player.getUniqueId())) {
            send(player);
        }
    }

    /** Vrai si ce joueur voit les interfaces du pack. */
    public boolean uses(Player player) {
        if (!enabled()) {
            return false;
        }
        return plugin.getConfig().getBoolean("resource-pack.assume-loaded", false) || loaded.contains(player.getUniqueId());
    }

    /** Envoie à nouveau le pack aux joueurs qui l'avaient (après un reload qui l'a modifié). */
    public void resendToLoaded() {
        for (UUID id : new ArrayList<>(loaded)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                loaded.remove(id);
                send(p);
            }
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(id);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> loaded.remove(id);
            default -> {
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if ("join".equalsIgnoreCase(plugin.getConfig().getString("resource-pack.send-on", "game"))) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    send(player);
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
        sent.remove(event.getPlayer().getUniqueId());
    }
}
