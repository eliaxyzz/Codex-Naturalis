package it.polimi.ingsw.server.model.card.GoldCardStrategy;

import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.card.GoldCard;

/**
 * This class is used to apply the strategy pattern on the gold-card's method for the score calculation.
 */
public class GoldCardContext {
    private final ConditionStrategy strategy;

    public GoldCardContext(ConditionStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Used to calculate the amount of points given by the specific gold-card.
     * @param pointsOnTheCard Points given by the gold-card.
     * @param gamefield Reference to the player's game-field.
     * @param goldcard Used for the calculation.
     * @return The amount of calculated points.
     */
    public int executePointsCalculation(int pointsOnTheCard, GameField gamefield, GoldCard goldcard) {
        return strategy.calculatePoints(pointsOnTheCard, gamefield,goldcard );
    }

    /**
     * Two contexts are equal when they wrap the same kind of strategy (the strategies
     * themselves are stateless, so comparing their class is enough).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoldCardContext other)) return false;
        return this.strategy.getClass() == other.strategy.getClass();
    }
}
