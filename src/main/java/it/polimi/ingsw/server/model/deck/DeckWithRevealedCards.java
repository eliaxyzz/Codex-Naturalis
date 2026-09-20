package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.Card;
import it.polimi.ingsw.util.customexceptions.EmptyDeckException;
import java.util.List;

/**
 * This abstract class represents a deck of cards that can have
 * two cards revealed on the table.
 * @param <T> The type of card held by the deck.
 */
public abstract class DeckWithRevealedCards<T extends Card> extends Deck<T>{
    protected T leftRevealedCard;
    protected T rightRevealedCard;

    protected DeckWithRevealedCards(List<T> cards) {
        super(cards);
        leftRevealedCard = topCardOrNull();
        rightRevealedCard = topCardOrNull();
    }

    /**
     * Rebuilds a saved deck: the order and the two revealed cards come back as they were.
     * @param cards What is left in the deck, top card first.
     * @param leftRevealedCard The card revealed on the left, or null if that slot ran dry.
     * @param rightRevealedCard The card revealed on the right, or null if that slot ran dry.
     */
    protected DeckWithRevealedCards(List<T> cards, T leftRevealedCard, T rightRevealedCard) {
        super(cards, false);
        this.leftRevealedCard = leftRevealedCard;
        this.rightRevealedCard = rightRevealedCard;
    }

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
