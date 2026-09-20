package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.Card;
import it.polimi.ingsw.util.customexceptions.EmptyDeckException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This abstract class represents a general Deck of cards.
 * @param <T> The type of card held by the deck.
 */
public abstract class Deck<T extends Card> {
    protected final ArrayList<T> cards;

    /**
     * @param cards The cards the deck starts with; they are shuffled and copied, so the
     *              caller's list stays untouched.
     */
    protected Deck(List<T> cards) {
        this(cards, true);
    }

    /**
     * @param cards The cards the deck starts with.
     * @param shuffle false when restoring a saved game: the order it was left in is the point.
     */
    protected Deck(List<T> cards, boolean shuffle) {
        this.cards = new ArrayList<>(cards);
        if (shuffle) Collections.shuffle(this.cards);
    }

    /**
     * @return The ids of the cards still in the deck, top card first.
     */
    public List<Integer> remainingCardIds() {
        return cards.stream().map(Card::getId).toList();
    }

    /**
     * Draws the top card from the deck.
     * @throws EmptyDeckException If the deck is empty when trying to draw a card.
     * @return The drawn card.
     */
    public T directDraw() throws EmptyDeckException {
        if (cards.isEmpty()) throw new EmptyDeckException();
        return cards.removeFirst();
    }

    /**
     * Gets the ID of the card that is currently at the top of the deck.
     * @return The top card ID, or 0 if the deck is empty.
     */
    public int getTopCardID() {
        return cards.isEmpty() ? 0 : cards.getFirst().getId();
    }

    /**
     * Checks if deck is empty.
     * @return true if deck is empty, false otherwise.
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
