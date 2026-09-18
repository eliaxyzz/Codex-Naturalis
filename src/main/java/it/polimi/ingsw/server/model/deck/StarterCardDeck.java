package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.StarterCard;
import it.polimi.ingsw.server.model.json.JsonCardsReader;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This class represents the starter card deck.
 */
public class StarterCardDeck extends Deck<StarterCard> {
    public StarterCardDeck() {
        cards = new ArrayList<>();
        for (int id : JsonCardsReader.cardIds(JsonCardsReader.STARTER_CARDS)) {
            cards.add(new StarterCard(id));
        }
        Collections.shuffle(cards);
    }
}
