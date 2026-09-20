package it.polimi.ingsw.client.view.utility;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import static it.polimi.ingsw.util.supportclasses.ViewConstants.*;

/**
 * This class manages the visual representation of a card.
 */
public class CardRepresentation {
    private static final double CORNER_ARC = 10;
    private static final double BORDER_WIDTH = 2;

    //card art never changes, and the board reloaded every PNG on each repaint
    private static final Map<String, Image> TEXTURES = new ConcurrentHashMap<>();

    private final String frontCardTexturePath;
    private final String backCardTexturePath;
    private final int id;
    private boolean facingUp;

    //only used in placementHistory array to memorize coordinates
    private int x;
    private int y;

    /**
     * Sets up the CardRepresentation.
     * @param id The ID of the card to be represented.
     * @param isFacingUp Whether the card is facing up or not.
     */
    public CardRepresentation(int id, boolean isFacingUp) {
        this.id = id;
        this.facingUp = isFacingUp;
        this.frontCardTexturePath = "/Images/cards/front/" + id + ".png";
        this.backCardTexturePath = "/Images/cards/back/" + id + ".png";
    }

    public void setFacingUp(boolean facingUp) {
        this.facingUp = facingUp;
    }

    public boolean isFacingUp() {
        return facingUp;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    /**
     * Returns the representation of the current state of the card.
     * @return the Rectangle representing the card.
     */
    public Rectangle getCard() {
        return buildCard(CARD_WIDTH, CARD_HEIGHT);
    }

    /**
     * Returns the card representation with a custom size multiplier.
     * @param size the size multiplier.
     * @return the Rectangle representing the card.
     */
    public Rectangle getCard(double size){
        return buildCard(size * CARD_WIDTH, size * CARD_HEIGHT);
    }

    /**
     * Each call returns a new Rectangle: callers place several of them independently.
     */
    private Rectangle buildCard(double width, double height) {
        Rectangle card = new Rectangle(width, height);
        card.setArcWidth(CORNER_ARC);
        card.setArcHeight(CORNER_ARC);
        card.setStroke(Color.BLACK);
        card.setStrokeWidth(BORDER_WIDTH);
        card.setFill(new ImagePattern(texture(facingUp ? frontCardTexturePath : backCardTexturePath)));
        return card;
    }

    private static Image texture(String path) {
        return TEXTURES.computeIfAbsent(path,
                p -> new Image(Objects.requireNonNull(CardRepresentation.class.getResourceAsStream(p))));
    }

    public int getId() {
        return id;
    }

    /**
     * changes the current facingUp variable to its opposite
     */
    public void flip(){
        facingUp = !facingUp;
    }
}