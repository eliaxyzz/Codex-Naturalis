package it.polimi.ingsw.server.model.card;

import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.json.JsonCardsReader;
import it.polimi.ingsw.util.customexceptions.CannotOpenJSONException;
import it.polimi.ingsw.util.customexceptions.InvalidIdException;
import it.polimi.ingsw.util.supportclasses.*;
import java.util.ArrayList;

/**
 * This class represents a Starter card.
 */
public class StarterCard extends PlaceableCard {
    protected ArrayList<Resource> backCentralResources;

    public StarterCard() {
        // for testing purpose only
    }
    public StarterCard(int id) {
        try {
            JsonCardsReader.loadStarterCard(id,this);
        } catch (CannotOpenJSONException e) {
            throw new RuntimeException(e);
        } catch (InvalidIdException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * The starter card doesn't give any points when placed, so it returns always 0.
     * @param gameField Reference to the game field the card is placed on.
     * @return Points earned by placing the card.
     */
    @Override
    public int placementPoints(GameField gameField) {
        return 0;
    }

    public void setBackCentralResources(ArrayList<Resource> backCentralResources) {
        this.backCentralResources = backCentralResources;
    }

    public ArrayList<Resource> getBackCentralResources() {
        return backCentralResources;
    }

}
