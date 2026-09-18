package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.GoldCard;
import it.polimi.ingsw.util.customexceptions.EmptyDeckException;
import it.polimi.ingsw.server.model.json.JsonCardsReader;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This class represents the gold card deck.
 */
public class GoldCardDeck extends DeckWithRevealedCards<GoldCard> {
    public GoldCardDeck(){
        cards = new ArrayList<>();
        for (int id : JsonCardsReader.cardIds(JsonCardsReader.GOLD_CARDS)) {
            cards.add(new GoldCard(id));
        }
        Collections.shuffle(cards);
        try {
            leftRevealedCard = this.directDraw();
            rightRevealedCard = this.directDraw();
        } catch (EmptyDeckException e) {
            throw new RuntimeException(e);
        }
    }
}
