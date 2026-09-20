package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.GoldCard;
import it.polimi.ingsw.server.model.json.JsonCardsReader;

/**
 * This class represents the gold card deck.
 */
public class GoldCardDeck extends DeckWithRevealedCards<GoldCard> {
    public GoldCardDeck(){
        super(JsonCardsReader.cardIds(JsonCardsReader.GOLD_CARDS).stream().map(GoldCard::new).toList());
    }

    private GoldCardDeck(java.util.List<GoldCard> cards, GoldCard left, GoldCard right) {
        super(cards, left, right);
    }

    /**
     * Rebuilds a saved deck.
     * @param remainingIds What is left in the deck, top card first.
     * @param leftId The revealed card on the left, or 0 if that slot is empty.
     * @param rightId The revealed card on the right, or 0 if that slot is empty.
     * @return The restored deck.
     */
    public static GoldCardDeck restored(java.util.List<Integer> remainingIds, int leftId, int rightId) {
        return new GoldCardDeck(remainingIds.stream().map(GoldCard::new).toList(),
                leftId == 0 ? null : new GoldCard(leftId),
                rightId == 0 ? null : new GoldCard(rightId));
    }
}
