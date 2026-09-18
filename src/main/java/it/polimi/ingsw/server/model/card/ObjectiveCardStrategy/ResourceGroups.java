package it.polimi.ingsw.server.model.card.ObjectiveCardStrategy;

import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.util.supportclasses.Resource;
import java.util.Map;

/**
 * Sets of visible resources, e.g. 3 animals, 2 scrolls, or one each of feather, ink pot and scroll.
 */
public record ResourceGroups(Map<Resource, Integer> perGroup) implements ObjectiveStrategy {

    @Override
    public int timesCompleted(GameField gameField) {
        int groups = Integer.MAX_VALUE;
        for (Map.Entry<Resource, Integer> needed : perGroup.entrySet()) {
            groups = Math.min(groups, gameField.getResourceCount(needed.getKey()) / needed.getValue());
        }
        return groups;
    }
}
