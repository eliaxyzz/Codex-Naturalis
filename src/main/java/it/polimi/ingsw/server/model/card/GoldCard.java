package it.polimi.ingsw.server.model.card;

import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.card.GoldCardStrategy.ConditionStrategy;
import it.polimi.ingsw.server.model.json.JsonCardsReader;
import it.polimi.ingsw.util.customexceptions.CannotOpenJSONException;
import it.polimi.ingsw.util.customexceptions.InvalidIdException;

/**
 * This class represents a Gold card.
 */
public class GoldCard extends PlaceableCard {
    protected ConditionStrategy strategy;

    public GoldCard() {
        // for testing purpose only
    }
    public GoldCard(int id){
        try {
            JsonCardsReader.loadGoldCard(id,this);
        } catch (CannotOpenJSONException e) {
            throw new RuntimeException(e);
        } catch (InvalidIdException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Calculates the points earned with this gold card.
     * @param gameField Reference to the game field the card is placed on.
     * @return Points earned by placing the card.
     */
    public int placementPoints(GameField gameField) {
        return strategy.calculatePoints(this.getPoints(), gameField, this);
    }

    public ConditionStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(ConditionStrategy strategy) {
        this.strategy = strategy;
    }
}
