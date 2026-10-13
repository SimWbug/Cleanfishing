package fr.cleangang.cleanfishing.progress;

import fr.cleangang.cleanfishing.fish.Rarity;
import org.bukkit.Material;

/** Succès débloquables ; chacun donne un titre affiché devant le pseudo en partie. */
public enum Achievement {
    CODEX_COMMON("<white>Pêcheur du dimanche", "Pêcher tous les poissons communs", Material.COD, Rarity.COMMON),
    CODEX_UNCOMMON("<green>Amateur éclairé", "Pêcher tous les poissons peu communs", Material.SALMON, Rarity.UNCOMMON),
    CODEX_RARE("<aqua>Fin connaisseur", "Pêcher tous les poissons rares", Material.TROPICAL_FISH, Rarity.RARE),
    CODEX_EPIC("<light_purple>Chasseur d'exception", "Pêcher tous les poissons épiques", Material.PUFFERFISH, Rarity.EPIC),
    CODEX_LEGENDARY("<gold>Légende des mers", "Pêcher tous les poissons légendaires", Material.HEART_OF_THE_SEA, Rarity.LEGENDARY),
    CODEX_ALL("<gradient:gold:aqua>Maître du Codex</gradient>", "Compléter tout le codex", Material.ENCHANTED_BOOK, null),
    FIRST_LEGENDARY("<gold>Chanceux", "Pêcher un poisson légendaire", Material.GOLD_INGOT, null),
    BIG_CATCH("<yellow>Gros calibre", "Pêcher un poisson à sa taille maximale", Material.SCAFFOLDING, null),
    SERVER_RECORD("<red>Recordman", "Battre le record de la plus grosse prise du serveur", Material.NETHER_STAR, null),
    NO_SPEND("<gray>Radin des mers", "Réussir 3 journées dans une partie sans rien acheter", Material.IRON_NUGGET, null),
    DAY_5("<aqua>Matelot", "Réussir 5 journées dans une partie", Material.OAK_BOAT, null),
    DAY_10("<dark_aqua>Vieux loup de mer", "Réussir 10 journées dans une partie", Material.SPRUCE_BOAT, null),
    HARDCORE_5("<dark_red>Tête brûlée", "Réussir 5 journées en mode hardcore", Material.FIRE_CHARGE, null);

    private final String title;
    private final String description;
    private final Material icon;
    private final Rarity codexRarity;

    Achievement(String title, String description, Material icon, Rarity codexRarity) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.codexRarity = codexRarity;
    }

    /** Titre en MiniMessage (couleurs comprises). */
    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Material icon() {
        return icon;
    }

    /** Rareté à compléter dans le codex, ou null si ce succès n'est pas un succès de codex. */
    public Rarity codexRarity() {
        return codexRarity;
    }

    public static Achievement parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
