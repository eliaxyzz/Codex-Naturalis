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
}
