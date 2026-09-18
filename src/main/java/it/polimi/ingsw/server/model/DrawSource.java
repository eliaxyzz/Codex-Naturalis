package it.polimi.ingsw.server.model;

/**
 * The six places a player can draw from at the end of their turn.
 */
public enum DrawSource {
    RESOURCE_DECK("a resource card from the deck"),
    GOLD_DECK("a gold card from the deck"),
    LEFT_RESOURCE("the left revealed resource card"),
    RIGHT_RESOURCE("the right revealed resource card"),
    LEFT_GOLD("the left revealed gold card"),
    RIGHT_GOLD("the right revealed gold card");

    private final String description;

    DrawSource(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
