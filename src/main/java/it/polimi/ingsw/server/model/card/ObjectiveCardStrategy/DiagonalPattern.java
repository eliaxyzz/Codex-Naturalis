package it.polimi.ingsw.server.model.card.ObjectiveCardStrategy;

import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.card.PlaceableCard;
import it.polimi.ingsw.util.supportclasses.Resource;
import java.util.HashSet;
import java.util.Set;

/**
 * Three cards of the same kingdom on a diagonal. {@code dx} is the horizontal step when going up
 * the diagonal: +1 for the "/" objectives, -1 for the "\" ones.
 */
public record DiagonalPattern(Resource kingdom, int dx) implements ObjectiveStrategy {

    @Override
    public int timesCompleted(GameField gameField) {
        int triplets = 0;
        Set<PlaceableCard> counted = new HashSet<>();
        for (PlaceableCard card : gameField.getCardsOfKingdom(kingdom)) {
            if (counted.contains(card)) continue;
            //climb to the top end of this diagonal, then walk down it closing a triplet every three cards,
            //so a run of five scores once and leaves the last two unused
            PlaceableCard top = card;
            PlaceableCard above;
            while ((above = sameKingdomAt(gameField, top.getX() + dx, top.getY() + 1)) != null) {
                top = above;
            }
            int run = 0;
            for (PlaceableCard current = top; current != null; current = sameKingdomAt(gameField, current.getX() - dx, current.getY() - 1)) {
                counted.add(current);
                if (++run == 3) {
                    triplets++;
                    run = 0;
                }
            }
        }
        return triplets;
    }

    private PlaceableCard sameKingdomAt(GameField gameField, int x, int y) {
        PlaceableCard card = gameField.lookAtCoordinates(x, y);
        return card != null && card.getCardKingdom() == kingdom ? card : null;
    }
}
