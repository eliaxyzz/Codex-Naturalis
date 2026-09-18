package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.ObjectiveCard;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This class represents the objective card deck.
 */
public class ObjectiveCardDeck extends Deck<ObjectiveCard> {
    public ObjectiveCardDeck() {
        cards = new ArrayList<>();
        for (int id : ObjectiveCard.ids()) {
            cards.add(new ObjectiveCard(id));
        }
        Collections.shuffle(cards);
    }
}
