package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.ResourceCard;
import it.polimi.ingsw.util.customexceptions.EmptyDeckException;
import it.polimi.ingsw.server.model.json.JsonCardsReader;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This class represents the resource card deck.
 */
public class ResourceCardDeck extends DeckWithRevealedCards<ResourceCard> {
    public ResourceCardDeck() {
        cards = new ArrayList<>();
        for (int id : JsonCardsReader.cardIds(JsonCardsReader.RESOURCE_CARDS)) {
            cards.add(new ResourceCard(id));
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
