package fr.cleangang.cleanfishing.shop;

import fr.cleangang.cleanfishing.item.GameItem;

/**
 * Une offre de la boutique de Gertrude pour la journée.
 * Une offre peut être en promotion (prix réduit) ou rare (stock limité, partagé par toute l'équipe).
 */
public final class ShopOffer {

    private final GameItem item;
    private final int amount;
    private final int basePrice;
    private final int price;
    private final int discountPercent;
    private final boolean rare;
    private int stock;

    public ShopOffer(GameItem item, int amount, int basePrice, int discountPercent, boolean rare, int stock) {
        this.item = item;
        this.amount = amount;
        this.basePrice = basePrice;
        this.discountPercent = Math.max(0, Math.min(90, discountPercent));
        this.price = Math.max(1, (int) Math.round(basePrice * (100 - this.discountPercent) / 100.0));
        this.rare = rare;
        this.stock = stock;
    }

    public GameItem item() {
        return item;
    }

    public int amount() {
        return amount;
    }

    /** Prix payé (après promotion). */
    public int price() {
        return price;
    }

    public int basePrice() {
        return basePrice;
    }

    public int discountPercent() {
        return discountPercent;
    }

    public boolean discounted() {
        return discountPercent > 0;
    }

    public boolean rare() {
        return rare;
    }

    /** Stock restant, -1 = illimité. */
    public int stock() {
        return stock;
    }

    public boolean soldOut() {
        return stock == 0;
    }

    /** Retire un exemplaire du stock (sans effet si illimité). */
    public void takeOne() {
        if (stock > 0) {
            stock--;
        }
    }
}
