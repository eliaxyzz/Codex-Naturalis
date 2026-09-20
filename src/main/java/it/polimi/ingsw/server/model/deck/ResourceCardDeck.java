package it.polimi.ingsw.server.model.deck;

import it.polimi.ingsw.server.model.card.ResourceCard;
import it.polimi.ingsw.server.model.json.JsonCardsReader;

/**
 * This class represents the resource card deck.
 */
public class ResourceCardDeck extends DeckWithRevealedCards<ResourceCard> {
    public ResourceCardDeck() {
        super(JsonCardsReader.cardIds(JsonCardsReader.RESOURCE_CARDS).stream().map(ResourceCard::new).toList());
    }
}
