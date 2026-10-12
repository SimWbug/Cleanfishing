package fr.cleangang.cleanfishing.fish;

import fr.cleangang.cleanfishing.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** Un poisson du catalogue (fish.yml). */
public record FishType(
        String id,
        String name,
        Rarity rarity,
        Material material,
        String itemModel,
        int value,
        int minSize,
        int maxSize,
        double weight,
        String description) {

    public int randomSize(Random random) {
        if (maxSize <= minSize) {
            return minSize;
        }
        return minSize + random.nextInt(maxSize - minSize + 1);
    }

    /** Valeur finale selon la taille : de 75 % à 125 % de la valeur de base. */
    public int valueFor(int size) {
        double ratio = (size - minSize) / (double) Math.max(1, maxSize - minSize);
        return (int) Math.round(value * (0.75 + 0.5 * Math.max(0, Math.min(1, ratio))));
    }

    /** Nom coloré selon la rareté, prêt pour MiniMessage. */
    public String coloredName() {
        return rarity.tag() + Text.esc(name);
    }

    /** Icône : modèle 3D du resource pack s'il est configuré, sinon l'item vanilla. */
    public ItemStack icon() {
        ItemStack stack = new ItemStack(material);
        if (itemModel != null && !itemModel.isBlank()) {
            try {
                stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(itemModel));
            } catch (RuntimeException ignored) {
                // clé invalide : on garde l'icône vanilla
            }
        }
        return stack;
    }
}
