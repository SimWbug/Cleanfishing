package fr.cleangang.cleanfishing.cards;

import org.bukkit.Material;

/**
 * Pouvoirs proposés sur les cartes de niveau. Les pouvoirs « cumulables » s'additionnent à chaque nouvelle carte
 * (jusqu'à maxStacks) et durent toute la partie ; les pouvoirs « instantanés » agissent tout de suite.
 */
public enum Power {
    LUCK("Œil de lynx", CardTier.RARE, Material.ENDER_EYE, 4, false,
            "+25 % de chances", "de poissons rares"),
    FAST_BITE("Main leste", CardTier.COMMON, Material.FISHING_ROD, 3, false,
            "Ça mord 20 %", "plus vite"),
    DOUBLE_PAY("Double paie", CardTier.EPIC, Material.GOLD_INGOT, 3, false,
            "15 % de chances qu'un", "poisson vaille x2"),
    GOLD_FEVER("Fièvre de l'or", CardTier.EPIC, Material.GOLD_BLOCK, 0, true,
            "Tous tes poissons", "valent x2 pendant 60 s"),
    SCHOLAR("Érudit", CardTier.COMMON, Material.EXPERIENCE_BOTTLE, 3, false,
            "+30 % d'expérience", "pour monter de niveau"),
    DOUBLE_HOOK("Double hameçon", CardTier.RARE, Material.TRIPWIRE_HOOK, 3, false,
            "12 % de chances de", "remonter 2 poissons"),
    TREASURE("Chasseur de trésors", CardTier.COMMON, Material.CHEST, 3, false,
            "+50 % d'objets", "pêchés (dynamite...)"),
    POCKET_TIMER("Sablier de poche", CardTier.COMMON, Material.CLOCK, 0, true,
            "+45 s à la journée", "pour toute l'équipe"),
    TIP("Pourboire", CardTier.COMMON, Material.EMERALD, 0, true,
            "Ajoute 8 % du palier", "à la cagnotte"),
    FRIEND("Ami de Gertrude", CardTier.RARE, Material.CAKE, 3, false,
            "-15 % sur tes achats", "chez Gertrude"),
    QUICK_HANDS("Mains d'artificier", CardTier.COMMON, Material.BLAZE_POWDER, 2, false,
            "Filet et harpon", "rechargés 30 % plus vite"),
    ABYSS_CALL("Appel des abysses", CardTier.RARE, Material.HEART_OF_THE_SEA, 0, true,
            "Tes 2 prochaines prises", "sont épiques ou mieux");

    private final String displayName;
    private final CardTier tier;
    private final Material icon;
    private final int maxStacks;
    private final boolean instant;
    private final String line1;
    private final String line2;

    Power(String displayName, CardTier tier, Material icon, int maxStacks, boolean instant, String line1, String line2) {
        this.displayName = displayName;
        this.tier = tier;
        this.icon = icon;
        this.maxStacks = maxStacks;
        this.instant = instant;
        this.line1 = line1;
        this.line2 = line2;
    }

    public String displayName() {
        return displayName;
    }

    public CardTier tier() {
        return tier;
    }

    public Material icon() {
        return icon;
    }

    /** Nombre maximal de cartes cumulées (0 = illimité, pour les pouvoirs instantanés). */
    public int maxStacks() {
        return maxStacks;
    }

    public boolean instant() {
        return instant;
    }

    /** Description en 2 courtes lignes (texte simple). */
    public String line1() {
        return line1;
    }

    public String line2() {
        return line2;
    }
}
