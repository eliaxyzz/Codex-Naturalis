package it.polimi.ingsw.server.model.card.ObjectiveCardStrategy;

import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.card.PlaceableCard;
import it.polimi.ingsw.util.supportclasses.Resource;
import java.util.HashSet;
import java.util.Set;

/**
 * Two stacked cards of one kingdom with a card of another kingdom diagonally off the end of the stack.
 * Seen from the {@code foot} card, the stack sits at (dx, dy) and (dx, 3*dy).
 */
public record LShapePattern(Resource stack, Resource foot, int dx, int dy) implements ObjectiveStrategy {

    @Override
    public int timesCompleted(GameField gameField) {
        int shapes = 0;
        Set<PlaceableCard> used = new HashSet<>();
        for (PlaceableCard footCard : gameField.getCardsOfKingdom(foot)) {
            PlaceableCard near = gameField.lookAtCoordinates(footCard.getX() + dx, footCard.getY() + dy);
            PlaceableCard far = gameField.lookAtCoordinates(footCard.getX() + dx, footCard.getY() + 3 * dy);
            if (isFreeStackCard(near, used) && isFreeStackCard(far, used)) {
                used.add(near);
                used.add(far);
                shapes++;
            }
        }
        return shapes;
    }

    private boolean isFreeStackCard(PlaceableCard card, Set<PlaceableCard> used) {
        return card != null && card.getCardKingdom() == stack && !used.contains(card);
    }
}
