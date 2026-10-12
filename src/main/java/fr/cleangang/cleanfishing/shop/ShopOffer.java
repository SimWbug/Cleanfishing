package fr.cleangang.cleanfishing.shop;

import fr.cleangang.cleanfishing.item.GameItem;

/** Une offre de la boutique de Gertrude pour la journée. */
public record ShopOffer(GameItem item, int amount, int price) {
}
