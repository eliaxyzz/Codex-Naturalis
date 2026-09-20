package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.ObjectiveCard;

/**
 * This class represents the objective card deck.
 */
public class ObjectiveCardDeck extends Deck<ObjectiveCard> {
    public ObjectiveCardDeck() {
        super(ObjectiveCard.ids().stream().map(ObjectiveCard::new).toList());
    }
}
