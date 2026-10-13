package fr.cleangang.cleanfishing.build;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Génère le cabanon de pêcheur de Gertrude autour de sa position.
 * <p>
 * Repère local : r = droite, f = devant (la direction dans laquelle regarde Gertrude, côté porte), h = hauteur
 * (0 = sol du cabanon). Gertrude est au centre (r 0, f 0), derrière un comptoir. Les blocs remplacés sont
 * sauvegardés dans {@code huts/<arène>.yml} pour pouvoir retirer la cabane et rendre le terrain.
 */
public final class HutBuilder {

    public record Result(boolean built, String message, Location spot) {
    }

    private record Vec(int x, int y, int z) {
    }

    private static final int MAX_FOUNDATION = 8;

    private final CleanFishingPlugin plugin;

    public HutBuilder(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ API

    public boolean hasHut(String arenaName) {
        return file(arenaName).isFile();
    }

    /**
     * Calcule la position de Gertrude (alignée sur la grille, orientée selon l'un des 4 points cardinaux)
     * et construit la cabane si {@code withHut}. {@code spot} est null en cas d'échec : rien n'a alors été placé.
     */
    public Result placeGertrude(Arena arena, Player player, boolean withHut) {
        Location here = player.getLocation();
        World world = here.getWorld();
        if (world == null) {
            return new Result(false, "Monde introuvable.", null);
        }
        if (!withHut) {
            return new Result(false, "Gertrude placée sans cabane.", here);
        }

        int quarter = Math.floorMod(Math.round(here.getYaw() / 90f), 4);
        int dx;
        int dz;
        switch (quarter) {
            case 1 -> { dx = -1; dz = 0; }
            case 2 -> { dx = 0; dz = -1; }
            case 3 -> { dx = 1; dz = 0; }
            default -> { dx = 0; dz = 1; }
        }
        int cx = here.getBlockX();
        int cz = here.getBlockZ();
        int floorY = here.getBlockY() - 1;

        boolean restored = false;
        if (hasHut(arena.name())) {
            restored = undo(arena.name());
        }

        Frame frame = new Frame(world, cx, floorY, cz, dx, dz);
        Map<Vec, BlockData> plan = new LinkedHashMap<>();
        plan(frame, plan);

        // vérifications avant toute modification
        for (Vec v : plan.keySet()) {
            if (v.y() < world.getMinHeight() || v.y() >= world.getMaxHeight()) {
                return new Result(false, "Trop près de la limite de hauteur du monde pour construire la cabane."
                        + (restored ? " L'ancienne cabane a été retirée." : ""), null);
            }
            Block block = world.getBlockAt(v.x(), v.y(), v.z());
            if (!block.getType().isAir() && block.getState(false) instanceof TileState) {
                return new Result(false, "Un bloc à données (coffre, panneau, fourneau...) gêne en " + v.x() + " "
                        + v.y() + " " + v.z() + ". Déplace-toi ou utilise « nohut »."
                        + (restored ? " L'ancienne cabane a été retirée." : ""), null);
            }
        }

        // sauvegarde de l'existant, puis construction
        List<String> saved = new ArrayList<>(plan.size());
        for (Map.Entry<Vec, BlockData> entry : plan.entrySet()) {
            Vec v = entry.getKey();
            Block block = world.getBlockAt(v.x(), v.y(), v.z());
            saved.add(v.x() + " " + v.y() + " " + v.z() + " " + block.getBlockData().getAsString());
        }
        if (!writeSnapshot(arena.name(), world, saved)) {
            return new Result(false, "Impossible d'écrire la sauvegarde du terrain (huts/). Rien n'a été construit.", null);
        }
        for (Map.Entry<Vec, BlockData> entry : plan.entrySet()) {
            Vec v = entry.getKey();
            world.getBlockAt(v.x(), v.y(), v.z()).setBlockData(entry.getValue(), false);
        }

        Location spot = new Location(world, cx + 0.5, floorY + 1, cz + 0.5, quarter * 90f, 0f);
        return new Result(true, "Cabanon construit (" + plan.size() + " blocs)."
                + (restored ? " L'ancienne cabane a été retirée." : ""), spot);
    }

    /** Remet le terrain d'origine. Faux si aucune cabane n'était enregistrée ou si son monde est absent. */
    public boolean undo(String arenaName) {
        File file = file(arenaName);
        if (!file.isFile()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        World world = Bukkit.getWorld(yaml.getString("world", ""));
        if (world == null) {
            return false;
        }
        for (String line : yaml.getStringList("blocks")) {
            String[] parts = line.split(" ", 4);
            if (parts.length < 4) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(parts[3]), false);
            } catch (IllegalArgumentException ignored) {
                // ligne illisible : on passe
            }
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Impossible de supprimer " + file.getName());
        }
        return true;
    }

    /** Oublie la sauvegarde sans toucher au terrain (l'arène est supprimée, la cabane reste en place). */
    public void forget(String arenaName) {
        File file = file(arenaName);
        if (file.isFile() && !file.delete()) {
            plugin.getLogger().warning("Impossible de supprimer " + file.getName());
        }
    }

    // ------------------------------------------------------------------ plan

    /** Repère local -> monde. */
    private record Frame(World world, int cx, int floorY, int cz, int dx, int dz) {
        int rx() {
            return -dz;
        }

        int rz() {
            return dx;
        }

        Vec at(int r, int f, int h) {
            return new Vec(cx + r * rx() + f * dx, floorY + h, cz + r * rz() + f * dz);
        }

        BlockFace forward() {
            return face(dx, dz);
        }

        BlockFace right() {
            return face(rx(), rz());
        }

        private static BlockFace face(int x, int z) {
            if (x > 0) {
                return BlockFace.EAST;
            }
            if (x < 0) {
                return BlockFace.WEST;
            }
            return z > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
    }

    private Material material(String key, Material fallback) {
        Material m = Material.matchMaterial(plugin.getConfig().getString("hut." + key, fallback.name()));
        return m != null && m.isBlock() ? m : fallback;
    }

    private void plan(Frame fr, Map<Vec, BlockData> plan) {
        Material wall = material("wall", Material.SPRUCE_PLANKS);
        Material floor = material("floor", Material.OAK_PLANKS);
        Material pillar = material("pillar", Material.OAK_LOG);
        Material counter = material("counter", Material.SPRUCE_PLANKS);
        String roofWood = plugin.getConfig().getString("hut.roof-wood", "DARK_OAK").toUpperCase();
        Material roofBlock = Material.matchMaterial(roofWood + "_PLANKS");
        Material roofStairs = Material.matchMaterial(roofWood + "_STAIRS");
        if (roofBlock == null || roofStairs == null) {
            roofBlock = Material.DARK_OAK_PLANKS;
            roofStairs = Material.DARK_OAK_STAIRS;
        }

        BlockData air = Material.AIR.createBlockData();

        // 1. volume dégagé (au-dessus du sol du cabanon)
        for (int r = -4; r <= 4; r++) {
            for (int f = -3; f <= 5; f++) {
                for (int h = 1; h <= 8; h++) {
                    plan.put(fr.at(r, f, h), air);
                }
            }
        }

        // 2. sol + fondations
        for (int r = -3; r <= 3; r++) {
            for (int f = -2; f <= 4; f++) {
                plan.put(fr.at(r, f, 0), floor.createBlockData());
                foundation(fr, plan, r, f);
            }
        }
        for (int r = -1; r <= 1; r++) {
            plan.put(fr.at(r, 5, 0), floor.createBlockData());
            foundation(fr, plan, r, 5);
        }

        // 3. murs et piliers
        BlockData wallData = wall.createBlockData();
        BlockData pillarData = pillar.createBlockData();
        for (int r = -3; r <= 3; r++) {
            for (int f = -2; f <= 4; f++) {
                boolean edge = Math.abs(r) == 3 || f == -2 || f == 4;
                if (!edge) {
                    continue;
                }
                boolean corner = Math.abs(r) == 3 && (f == -2 || f == 4);
                for (int h = 1; h <= 3; h++) {
                    plan.put(fr.at(r, f, h), corner ? pillarData : wallData);
                }
            }
        }

        // porte (ouverture) et fenêtres
        plan.put(fr.at(0, 4, 1), air);
        plan.put(fr.at(0, 4, 2), air);
        BlockData alongF = pane(fr.forward(), fr.forward().getOppositeFace());
        BlockData alongR = pane(fr.right(), fr.right().getOppositeFace());
        for (int f : new int[]{0, 2}) {
            plan.put(fr.at(-3, f, 2), alongF);
            plan.put(fr.at(3, f, 2), alongF);
        }
        plan.put(fr.at(0, -2, 2), alongR);
        plan.put(fr.at(-2, 4, 2), alongR);
        plan.put(fr.at(2, 4, 2), alongR);

        // 4. pignons
        for (int f : new int[]{-2, 4}) {
            for (int r = -2; r <= 2; r++) {
                int roofH = 4 + (3 - Math.abs(r));
                int top = r == 0 ? 6 : roofH - 1;
                for (int h = 4; h <= top; h++) {
                    plan.put(fr.at(r, f, h), wallData);
                }
            }
        }

        // 5. toit à deux pans (les marches montent vers le faîte)
        for (int f = -2; f <= 4; f++) {
            for (int r = -3; r <= 3; r++) {
                int s = Math.abs(r);
                if (s == 0) {
                    plan.put(fr.at(0, f, 7), roofBlock.createBlockData());
                    continue;
                }
                Stairs stairs = (Stairs) roofStairs.createBlockData();
                stairs.setFacing(r < 0 ? fr.right() : fr.right().getOppositeFace());
                plan.put(fr.at(r, f, 4 + (3 - s)), stairs);
            }
        }

        // 6. intérieur : comptoir, lanternes, tonneaux, tapis
        BlockData counterData = counter.createBlockData();
        for (int r = -2; r <= 2; r++) {
            plan.put(fr.at(r, 1, 1), counterData);
        }
        plan.put(fr.at(-2, 1, 2), Material.LANTERN.createBlockData());
        plan.put(fr.at(2, 1, 2), Material.LANTERN.createBlockData());
        for (int r : new int[]{-2, 2}) {
            Directional barrel = (Directional) Material.BARREL.createBlockData();
            barrel.setFacing(BlockFace.UP);
            plan.put(fr.at(r, -1, 1), barrel);
        }
        for (int f = 2; f <= 4; f++) {
            plan.put(fr.at(0, f, 1), Material.RED_CARPET.createBlockData());
        }
    }

    /** Comble sous le sol du cabanon jusqu'à retrouver un sol solide (au plus 8 blocs). */
    private void foundation(Frame fr, Map<Vec, BlockData> plan, int r, int f) {
        BlockData cobble = Material.COBBLESTONE.createBlockData();
        for (int d = 1; d <= MAX_FOUNDATION; d++) {
            Vec v = fr.at(r, f, -d);
            Block block = fr.world().getBlockAt(v.x(), v.y(), v.z());
            Material type = block.getType();
            if (type.isSolid() && !Tag.LEAVES.isTagged(type)) {
                return;
            }
            plan.put(v, cobble);
        }
    }

    private static BlockData pane(BlockFace a, BlockFace b) {
        MultipleFacing pane = (MultipleFacing) Material.GLASS_PANE.createBlockData();
        pane.setFace(a, true);
        pane.setFace(b, true);
        return pane;
    }

    // ------------------------------------------------------------------ fichiers

    private File file(String arenaName) {
        String safe = arenaName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return new File(new File(plugin.getDataFolder(), "huts"), safe + ".yml");
    }

    private boolean writeSnapshot(String arenaName, World world, List<String> lines) {
        File file = file(arenaName);
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", world.getName());
        yaml.set("blocks", lines);
        try {
            yaml.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Sauvegarde de la cabane impossible : " + e.getMessage());
            return false;
        }
    }
}
