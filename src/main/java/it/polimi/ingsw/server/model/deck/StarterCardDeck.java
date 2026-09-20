package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.StarterCard;
import it.polimi.ingsw.server.model.json.JsonCardsReader;

/**
 * This class represents the starter card deck.
 */
public class StarterCardDeck extends Deck<StarterCard> {
    public StarterCardDeck() {
        super(JsonCardsReader.cardIds(JsonCardsReader.STARTER_CARDS).stream().map(StarterCard::new).toList());
    }

    private StarterCardDeck(java.util.List<StarterCard> cards, boolean shuffle) {
        super(cards, shuffle);
    }

    /**
     * Rebuilds a saved deck, keeping the order it was left in.
     * @param remainingIds What is left in the deck, top card first.
     * @return The restored deck.
     */
    public static StarterCardDeck restored(java.util.List<Integer> remainingIds) {
        return new StarterCardDeck(remainingIds.stream().map(StarterCard::new).toList(), false);
    }
}
