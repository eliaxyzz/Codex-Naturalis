package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.ResourceCard;
import it.polimi.ingsw.server.model.json.JsonCardsReader;

/**
 * This class represents the resource card deck.
 */
public class ResourceCardDeck extends DeckWithRevealedCards<ResourceCard> {
    public ResourceCardDeck() {
        super(JsonCardsReader.cardIds(JsonCardsReader.RESOURCE_CARDS).stream().map(ResourceCard::new).toList());
    }

    private ResourceCardDeck(java.util.List<ResourceCard> cards, ResourceCard left, ResourceCard right) {
        super(cards, left, right);
    }

    /**
     * Rebuilds a saved deck.
     * @param remainingIds What is left in the deck, top card first.
     * @param leftId The revealed card on the left, or 0 if that slot is empty.
     * @param rightId The revealed card on the right, or 0 if that slot is empty.
     * @return The restored deck.
     */
    public static ResourceCardDeck restored(java.util.List<Integer> remainingIds, int leftId, int rightId) {
        return new ResourceCardDeck(remainingIds.stream().map(ResourceCard::new).toList(),
                leftId == 0 ? null : new ResourceCard(leftId),
                rightId == 0 ? null : new ResourceCard(rightId));
    }
}
