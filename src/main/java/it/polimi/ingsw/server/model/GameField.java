package it.polimi.ingsw.server.model;

import it.polimi.ingsw.server.model.card.PlaceableCard;
import it.polimi.ingsw.server.model.card.StarterCard;
import it.polimi.ingsw.util.customexceptions.CannotPlaceCardException;
import it.polimi.ingsw.util.supportclasses.Resource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a player's game field, which is the grid where they place their cards during the game.
 * It keeps track of the cards placed on the grid, categorized by type (plant, animal, fungi, insect), as well as resource counts associated with placed cards.
 */
public class GameField {
    private final HashMap<String, PlaceableCard> cardsGrid;
    private final Player player;
    private final Map<Resource, Integer> resourceCounts;
    private final Map<Resource, ArrayList<PlaceableCard>> cardsByKingdom;
    private final ArrayList<PlaceableCard> placementHistory;

    public GameField(Player player) {
        this.player = player;
        cardsGrid = new HashMap<>();
        resourceCounts = new EnumMap<>(Resource.class);
        for (Resource resource : Resource.values()) {
            resourceCounts.put(resource, 0);
        }
        cardsByKingdom = new EnumMap<>(Resource.class);
        cardsByKingdom.put(Resource.animal, new ArrayList<>());
        cardsByKingdom.put(Resource.insect, new ArrayList<>());
        cardsByKingdom.put(Resource.fungi, new ArrayList<>());
        cardsByKingdom.put(Resource.plant, new ArrayList<>());
        placementHistory = new ArrayList<>();
    }

    //GETTERS and SETTERS
    public Player getPlayer() {
        return player;
    }

    public int getFungiCount() {
        return resourceCounts.get(Resource.fungi);
    }

    public ArrayList<PlaceableCard> getPlantCards() {
        return cardsByKingdom.get(Resource.plant);
    }

    public ArrayList<PlaceableCard> getAnimalCards() {
        return cardsByKingdom.get(Resource.animal);
    }

    public ArrayList<PlaceableCard> getFungiCards() {
        return cardsByKingdom.get(Resource.fungi);
    }

    public ArrayList<PlaceableCard> getInsectCards() {
        return cardsByKingdom.get(Resource.insect);
    }

    public ArrayList<PlaceableCard> getPlacementHistory() {
        return placementHistory;
    }

    public void addToPlacementHistory(PlaceableCard card) {
        placementHistory.addLast(card);
    }

    public int getAnimalCount() {
        return resourceCounts.get(Resource.animal);
    }

    public int getPlantCount() {
        return resourceCounts.get(Resource.plant);
    }

    public int getInsectCount() {
        return resourceCounts.get(Resource.insect);
    }

    public int getScrollCount() {
        return resourceCounts.get(Resource.scroll);
    }

    public int getInkPotCount() {
        return resourceCounts.get(Resource.inkPot);
    }

    public int getFeatherCount() {
        return resourceCounts.get(Resource.feather);
    }

    /**
     *  Searches the card in a particular position on the grid.
      * @param x The x coordinate on the grid.
     * @param y The y coordinate on the grid.
     * @return The card at (x,y) coordinates or null if it's not found.
     */
    public PlaceableCard lookAtCoordinates(int x, int y){
        if(x<-40 || y<-40 || x>40 || y>40) return null;
        else return cardsGrid.get(coordinatesToString(x,y));
    }

    /**
     * Places a card to the specified coordinates.
     * @param card The card to place.
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    private void placeCardAtCoordinates(PlaceableCard card, int x, int y){
        cardsGrid.put(coordinatesToString(x,y),card);
    }

    /**
     * Converts coordinates to string.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return The coordinates converted to String like: "x,y"
     */
    private String coordinatesToString(int x, int y){
        return x + "," + y;
    }

    /**
     * Places the starter card on the game field.
     * @param card The starter card to be placed.
     * @param facingUp Value that chooses the side of the card that will be shown. True ro place it on the front side, false otherwise.
     */
    public void place(StarterCard card, boolean facingUp) {
        card.setFacingUp(facingUp);
        this.placeCardAtCoordinates(card,0,0); //places card in the grid
        card.setX(0);
        card.setY(0);
        addResource(card.getTopRightCorner().getResource());
        addResource(card.getTopLeftCorner().getResource());
        addResource(card.getBottomRightCorner().getResource());
        addResource(card.getBottomLeftCorner().getResource());
        if(!card.isFacingUp()) {
            for (Resource res : card.getBackCentralResources()) {
                addResource(res);
            }
        }
        addToPlacementHistory(card);
    }

    /**
     * Places the card on the game field.
     * @param card The card to be placed.
     * @param facingUp Value that chooses the side of the card that will be shown. True ro place it on the front side, false otherwise.
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public void place(PlaceableCard card, boolean facingUp, int x, int y) throws CannotPlaceCardException {
        if (this.lookAtCoordinates(x,y)!=null) throw new CannotPlaceCardException("There's already a card placed there!");
        if (!this.followsPlacementRules(x,y)) throw new CannotPlaceCardException("You can't place a card there!");
        if (!this.followsPlacementRequirements(card, facingUp)) throw new CannotPlaceCardException("You don't have enough resources to place this card!");
        //only touch the card once the move is known to be legal: a rejected card goes back to the hand as it was
        card.setFacingUp(facingUp);
        this.placeCardAtCoordinates(card,x,y);
        card.setX(x);
        card.setY(y);
        updateNeighboursAndResources(card, x,y); //updates the surrounding cards and resource state
        if(card.isFacingUp()) player.setScore(player.getScore()+card.placementPoints(this)); //gets the points earned from placing the card
        addToPlacementHistory(card);
    }

    /**
     * Checks the placing rules between the new card and the neighbour cards.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return true if placing is valid, false otherwise.
     */
    private boolean followsPlacementRules(int x, int y){
        return isInValidCoordinates(x, y) && hasValidNeighbours(x,y);
    }

    /**
     * Checks if the card is in valid coordinates, x and y must be both even or both odd.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return true if placing is correct
     * */
    private boolean isInValidCoordinates(int x, int y) {
        return (Math.abs(x)%2==0 && Math.abs(y)%2==0) || (Math.abs(x)%2==1 && Math.abs(y)%2==1);
    }

    /**
     * Checks if the card has valid corner for placement.
     * @param neighbourCard The neighbourCard.
     * @param offset The offset of the card.
     * @return true if the card has valid corner for placement, false otherwise.
     */
    private boolean hasValidCorner(PlaceableCard neighbourCard, int[] offset) {
         switch (offset[0]) {
            case 1 -> {
                if (offset[1] == 1)
                    return neighbourCard.getBottomLeftCorner().isAttachable();
                if(offset[1] == -1)
                    return neighbourCard.getTopLeftCorner().isAttachable();
            }
            case -1 -> {
                if (offset[1] == 1)
                    return neighbourCard.getBottomRightCorner().isAttachable();
                if (offset[1] == -1)
                    return neighbourCard.getTopRightCorner().isAttachable();
            }
             default -> {
                 return false;
             }
         }
         //shouldn't get here
         return false;
    }

    /**
     * Checks if the card has valid neighbors for placement.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return true if the card has valid neighbors for placement, false otherwise.
     */
    private boolean hasValidNeighbours(int x, int y){
        int[][] offsets = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        int validNeighbourCount = 0;
        for (int[] offset : offsets) {
            PlaceableCard neighbourCard = lookAtCoordinates(x + offset[0], y + offset[1]);
            if (neighbourCard != null) {
                if (!hasValidCorner(neighbourCard, offset)) return false;
                else validNeighbourCount++;
            }
        }
        return validNeighbourCount>0;
    }

    /**
     * Checks if the requirements for placing the card are matched.
     * @param placeableCard Card to check the requirements.
     * @param facingUp The side the card would be placed on: the back never has requirements.
     * @return true if the requirements for placing the card are matched, false otherwise.
     */
    private boolean followsPlacementRequirements (PlaceableCard placeableCard, boolean facingUp){

        if(!facingUp) return true;
        return placeableCard.getRequiredAnimalResourceAmount() <= getAnimalCount() &&
                placeableCard.getRequiredFungiResourceAmount() <= getFungiCount() &&
                placeableCard.getRequiredInsectResourceAmount() <= getInsectCount() &&
                placeableCard.getRequiredPlantResourceAmount() <= getPlantCount();

    }

    /**
     * Updates the state of the neighbour cards corners and resources visible on the game-field after placing a card.
     * @param card The placed card.
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    private void updateNeighboursAndResources(PlaceableCard card, int x, int y){

        PlaceableCard neighbourCard;

        neighbourCard = this.lookAtCoordinates(x+1,y+1);
        if( neighbourCard != null ) {
            this.removeResource(neighbourCard.getBottomLeftCorner().getResource());
            neighbourCard.getBottomLeftCorner().setVisible(false);
            neighbourCard.getBottomLeftCorner().setAttached(true);
            card.getTopRightCorner().setAttached(true);
        }

        neighbourCard = this.lookAtCoordinates(x+1,y-1);
        if( neighbourCard != null ) {
            this.removeResource(neighbourCard.getTopLeftCorner().getResource());
            neighbourCard.getTopLeftCorner().setVisible(false);
            neighbourCard.getTopLeftCorner().setAttached(true);
            card.getBottomRightCorner().setAttached(true);
        }

        neighbourCard = this.lookAtCoordinates(x-1,y-1);
        if( neighbourCard != null ) {
            this.removeResource(neighbourCard.getTopRightCorner().getResource());
            neighbourCard.getTopRightCorner().setVisible(false);
            neighbourCard.getTopRightCorner().setAttached(true);
            card.getBottomLeftCorner().setAttached(true);
        }

        neighbourCard = this.lookAtCoordinates(x-1,y+1);
        if( neighbourCard != null ) {
            this.removeResource(neighbourCard.getBottomRightCorner().getResource());
            neighbourCard.getBottomRightCorner().setVisible(false);
            neighbourCard.getBottomRightCorner().setAttached(true);
            card.getTopLeftCorner().setAttached(true);
        }

        if(card.isFacingUp()){
            this.addResource(card.getTopRightCorner().getResource());
            this.addResource(card.getBottomRightCorner().getResource());
            this.addResource(card.getBottomLeftCorner().getResource());
            this.addResource(card.getTopLeftCorner().getResource());
        }
        else{
            this.addResource(card.getCardKingdom());
        }
        ArrayList<PlaceableCard> kingdomCards = cardsByKingdom.get(card.getCardKingdom());
        if (kingdomCards != null) kingdomCards.add(card);
    }

    /**
     * Adds the given resource to the total resource amount.
     * @param resource The resource to add to the total visible amount.
     */
    private void addResource(Resource resource){
        resourceCounts.merge(resource, 1, Integer::sum);
    }

    /**
     * Removes the given resource to the total resource amount.
     * @param resource The resource to add to the total visible amount.
     */
    private void removeResource(Resource resource){
        resourceCounts.merge(resource, -1, Integer::sum);
    }

}
