package it.polimi.ingsw.server.model.card.ObjectiveCardStrategy;

import it.polimi.ingsw.server.model.GameField;

/**
 * How an objective card is scored. Implementations only count, they never touch the player:
 * the caller decides what a completion is worth and keeps the tally.
 */
@FunctionalInterface
public interface ObjectiveStrategy {

    /**
     * @param gameField The game field to look at.
     * @return How many times the objective is met on it, each card counted at most once.
     */
    int timesCompleted(GameField gameField);
}
