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
}
