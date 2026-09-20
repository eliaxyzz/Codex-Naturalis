package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.ObjectiveCard;

/**
 * This class represents the objective card deck.
 */
public class ObjectiveCardDeck extends Deck<ObjectiveCard> {
    public ObjectiveCardDeck() {
        super(ObjectiveCard.ids().stream().map(ObjectiveCard::new).toList());
    }

    private ObjectiveCardDeck(java.util.List<ObjectiveCard> cards, boolean shuffle) {
        super(cards, shuffle);
    }

    /**
     * Rebuilds a saved deck, keeping the order it was left in.
     * @param remainingIds What is left in the deck, top card first.
     * @return The restored deck.
     */
    public static ObjectiveCardDeck restored(java.util.List<Integer> remainingIds) {
        return new ObjectiveCardDeck(remainingIds.stream().map(ObjectiveCard::new).toList(), false);
    }
}
