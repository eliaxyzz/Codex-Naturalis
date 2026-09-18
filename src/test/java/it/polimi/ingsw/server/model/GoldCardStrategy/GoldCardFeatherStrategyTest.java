package it.polimi.ingsw.server.model.GoldCardStrategy;

import it.polimi.ingsw.server.controller.GameController;
import it.polimi.ingsw.server.model.Game;
import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.Player;
import it.polimi.ingsw.server.model.card.GoldCard;
import it.polimi.ingsw.server.model.card.ObjectiveCard;
import it.polimi.ingsw.server.model.card.ResourceCard;
import it.polimi.ingsw.server.model.card.StarterCard;
import it.polimi.ingsw.util.customexceptions.CannotPlaceCardException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class GoldCardFeatherStrategyTest {

    private static GameController controller;
    private static Game game;
    private GameField gameField;
    private Player player;
    private int score;

    @BeforeAll
    static void setUpBeforeClass() {
        controller = new GameController(null,4,"test", false);
        game = controller.getGame();
    }

    @BeforeEach
    void setUp() {
        player = new Player(game);
        gameField = new GameField();
        score = 0;
    }
    @AfterEach
    void tearDown() {
        game.reinsertToken(player.getToken());
        gameField = null;
    }

    @AfterAll
    static void tearDownAfterClass() {
        controller = null;
    }

    @Test
    void calculatePointsCase1() {
        //carta gold piazzabile e unico punto dato da feather che possiede la gold stessa
        gameField.place(new StarterCard(82),true);
        try {
            score += gameField.place(new ResourceCard(23), true, 1,1);
            score += gameField.place(new ResourceCard(12), true, -1,-1);
            score += gameField.place(new ResourceCard(1), true, 1,-1);
            score += gameField.place(new GoldCard(41),true,-1,1);

        } catch (CannotPlaceCardException e) {
            throw new RuntimeException(e);
        }

        assertEquals(1,score);
    }

    @Test
    void calculatePointsCase2() {
        //carta gold piazzabile e 1 feather già sul tavolo
        gameField.place(new StarterCard(82),true);
        try {
            score += gameField.place(new ResourceCard(23), true, 1,1);
            score += gameField.place(new ResourceCard(12), true, -1,-1);
            score += gameField.place(new ResourceCard(1), true, 1,-1);
            score += gameField.place(new ResourceCard(5), true, 2,0);
            score += gameField.place(new GoldCard(41),true,-1,1);

        } catch (CannotPlaceCardException e) {
            throw new RuntimeException(e);
        }

        assertEquals(2,score);
    }

    @Test
    void calculatePointsCase3() {
        //carta gold piazzabile e copre la feather già sul tavolo
        gameField.place(new StarterCard(82), true);
        try {
            score += gameField.place(new ResourceCard(23), true, 1,1);
            score += gameField.place(new ResourceCard(12), true, -1,-1);
            score += gameField.place(new ResourceCard(1), true, 1,-1);
            score += gameField.place(new ResourceCard(5), true, 2,0);
            score += gameField.place(new GoldCard(41),true,3,1);

        } catch (CannotPlaceCardException e) {
            throw new RuntimeException(e);
        }

        assertEquals(1,score);
    }


    @Test
    void calculatePointsCase4() {
        //starter card girata, carta gold piazzabile e unica feather della goldcard
        gameField.place(new StarterCard(82),true);
        try {
            score += gameField.place(new ResourceCard(23), true, 1,1);
            score += gameField.place(new ResourceCard(12), true, -1,1);
            score += gameField.place(new GoldCard(51),true,2,0);

        } catch (CannotPlaceCardException e) {
            throw new RuntimeException(e);
        }
        assertEquals(1,score);
    }


    @Test
    void calculatePointsCase5() {
        //starter card girata, carta gold piazzabile, 2 gold piazzate e 5 punti attesi
        gameField.place(new StarterCard(85), false);
        try {
            score += gameField.place(new ResourceCard(23), true, 1,1);
            score += gameField.place(new ResourceCard(12), true, -1,1);
            score += gameField.place(new ResourceCard(4), true, -2,2);
            score += gameField.place(new ResourceCard(3), true, -3,1);
            score += gameField.place(new GoldCard(48),true,-1,3);
            score += gameField.place(new GoldCard(51),true,2,0);

        } catch (CannotPlaceCardException e) {
            throw new RuntimeException(e);
        }
        assertEquals(5,score);
    }
}