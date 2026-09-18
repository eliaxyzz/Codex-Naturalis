package it.polimi.ingsw.server.model.card;

/**
 * This class represents a general card.
 */
public abstract class Card {
    protected int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * A card is its id: the same id always means the same printed card, whatever side
     * it's showing or where it has been placed. Position and orientation are game state,
     * so they deliberately stay out of equality (and out of hashCode).
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        return id == ((Card) obj).id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
