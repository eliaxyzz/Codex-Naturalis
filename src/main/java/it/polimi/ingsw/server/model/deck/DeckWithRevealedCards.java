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
     * @throws EmptyDeckException If the slot is empty because the deck ran out.
     */
    public T drawLeftRevealedCard() throws EmptyDeckException {
        if (leftRevealedCard == null) throw new EmptyDeckException();
        T selectedCard = leftRevealedCard;
        leftRevealedCard = topCardOrNull();
        return selectedCard;
    }

    /**
     * Draws the right revealed card from the board and replaces it with the top card of the deck.
     * @return The drawn card.
     * @throws EmptyDeckException If the slot is empty because the deck ran out.
     */
    public T drawRightRevealedCard() throws EmptyDeckException {
        if (rightRevealedCard == null) throw new EmptyDeckException();
        T selectedCard = rightRevealedCard;
        rightRevealedCard = topCardOrNull();
        return selectedCard;
    }

    private T topCardOrNull() {
        return cards.isEmpty() ? null : cards.removeFirst();
    }
}
