package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.Card;
import it.polimi.ingsw.util.customexceptions.EmptyDeckException;

/**
 * This abstract class represents a deck of cards that can have
 * two cards revealed on the table.
 * @param <T> The type of card held by the deck.
 */
public abstract class DeckWithRevealedCards<T extends Card> extends Deck<T>{
    protected T leftRevealedCard;
    protected T rightRevealedCard;

    public int getLeftRevealedCardID() {
        if (leftRevealedCard == null) { return 0;}
        return leftRevealedCard.getId();
    }

    public int getRightRevealedCardID() {
        if (rightRevealedCard == null) { return 0;}
        return rightRevealedCard.getId();
    }

    /**
     * Draws the left revealed card from the board and replaces it with the top card of the deck.
     * @return The drawn card.
     */
    public T drawLeftRevealedCard() {
        T selectedCard;
        selectedCard = leftRevealedCard;
        try {
            leftRevealedCard = this.directDraw();
        } catch (EmptyDeckException e) {
            leftRevealedCard = null; //no cards left
        }
        return selectedCard;
    }

    /**
     * Draws the right revealed card from the board and replaces it with the top card of the deck.
     * @return The drawn card.
     */
    public T drawRightRevealedCard() {
        T selectedCard;
        selectedCard = rightRevealedCard;
        try {
            rightRevealedCard = this.directDraw();
        } catch (EmptyDeckException e) {
            rightRevealedCard = null; //no cards left
        }
        return selectedCard;
    }
}
