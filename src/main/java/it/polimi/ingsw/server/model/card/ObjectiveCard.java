package it.polimi.ingsw.server.model.card;

import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.card.ObjectiveCardStrategy.DiagonalPattern;
import it.polimi.ingsw.server.model.card.ObjectiveCardStrategy.LShapePattern;
import it.polimi.ingsw.server.model.card.ObjectiveCardStrategy.ObjectiveStrategy;
import it.polimi.ingsw.server.model.card.ObjectiveCardStrategy.ResourceGroups;
import java.util.List;
import java.util.Map;
import static it.polimi.ingsw.util.supportclasses.Resource.*;

/**
 * This class represents an Objective card.
 */
public class ObjectiveCard extends Card {
    private record Definition(int points, ObjectiveStrategy strategy) {}

    private static final Map<Integer, Definition> DEFINITIONS = Map.ofEntries(
            Map.entry(87, new Definition(2, new DiagonalPattern(fungi, 1))),
            Map.entry(88, new Definition(2, new DiagonalPattern(plant, -1))),
            Map.entry(89, new Definition(2, new DiagonalPattern(animal, 1))),
            Map.entry(90, new Definition(2, new DiagonalPattern(insect, -1))),
            Map.entry(91, new Definition(3, new LShapePattern(fungi, plant, -1, 1))),
            Map.entry(92, new Definition(3, new LShapePattern(plant, insect, 1, 1))),
            Map.entry(93, new Definition(3, new LShapePattern(animal, fungi, -1, -1))),
            Map.entry(94, new Definition(3, new LShapePattern(insect, animal, 1, -1))),
            Map.entry(95, new Definition(2, new ResourceGroups(Map.of(fungi, 3)))),
            Map.entry(96, new Definition(2, new ResourceGroups(Map.of(plant, 3)))),
            Map.entry(97, new Definition(2, new ResourceGroups(Map.of(animal, 3)))),
            Map.entry(98, new Definition(2, new ResourceGroups(Map.of(insect, 3)))),
            Map.entry(99, new Definition(3, new ResourceGroups(Map.of(feather, 1, inkPot, 1, scroll, 1)))),
            Map.entry(100, new Definition(2, new ResourceGroups(Map.of(scroll, 2)))),
            Map.entry(101, new Definition(2, new ResourceGroups(Map.of(inkPot, 2)))),
            Map.entry(102, new Definition(2, new ResourceGroups(Map.of(feather, 2))))
    );

    private final Definition definition;

    public ObjectiveCard(int id) {
        definition = DEFINITIONS.get(id);
        if (definition == null) throw new IllegalArgumentException("invalid id: " + id);
        setId(id);
    }

    /**
     * @return The ids of every objective card, in ascending order.
     */
    public static List<Integer> ids() {
        return DEFINITIONS.keySet().stream().sorted().toList();
    }

    public int getPoints() {
        return definition.points();
    }

    /**
     * @param gameField The game field the objective is checked against.
     * @return How many times the objective is met on the game field.
     */
    public int timesCompleted(GameField gameField) {
        return definition.strategy().timesCompleted(gameField);
    }

    /**
     * Calculates the points earned with this objective card.
     * @param gameField Reference to the game field on which the objective has to be checked.
     * @return Points earned on that game field.
     */
    public int getEarnedPoints(GameField gameField) {
        return getPoints() * timesCompleted(gameField);
    }
}
