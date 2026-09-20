package it.polimi.ingsw.client.controller;

import it.polimi.ingsw.client.model.ClientStateModel;
import it.polimi.ingsw.client.model.PlayerModel;
import it.polimi.ingsw.util.supportclasses.ClientState;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The resume payload is an ordinary startGame message, so these pin how the client comes
 * back out of the otherwise-final LOST_CONNECTION_STATE.
 */
class ResumeStateTest {
    private final ClientMessageHandler handler = new ClientMessageHandler();

    @BeforeEach
    void setUp() {
        PlayerModel.getInstance().setUsername("alice");
        ClientStateModel.getInstance().recoverFromLostConnection(ClientState.LOBBY_STATE);
    }

    @AfterEach
    void tearDown() {
        ClientStateModel.getInstance().recoverFromLostConnection(ClientState.LOBBY_STATE);
    }

    @SuppressWarnings("unchecked")
    private JSONObject resumePayload(String turnPlayer, boolean alreadyPlaced) {
        JSONObject decks = new JSONObject();
        for (String key : new String[]{"topDeckResourceCardID", "leftRevealedResourceCardID", "rightRevealedResourceCardID",
                "topDeckGoldCardID", "leftRevealedGoldCardID", "rightRevealedGoldCardID"}) {
            decks.put(key, "0");
        }
        JSONObject resources = new JSONObject();
        for (String key : new String[]{"animalResources", "insectResources", "fungiResources",
                "plantResources", "featherCount", "scrollCount", "inkPotCount"}) {
            resources.put(key, "0");
        }
        JSONObject message = new JSONObject();
        message.put("message", "startGame");
        message.put("hand", new JSONArray());
        message.put("placementHistory", new JSONArray());
        message.put("decks", decks);
        message.put("resources", resources);
        message.put("secretObjectiveID", "87");
        message.put("commonObjective1", "88");
        message.put("commonObjective2", "89");
        message.put("token", "red");
        message.put("firstPlayer", turnPlayer);
        message.put("alreadyPlaced", String.valueOf(alreadyPlaced));
        return message;
    }

    @Test
    void resumingOnYourTurnPutsYouBackInPlacing() {
        ClientStateModel.getInstance().setClientState(ClientState.LOST_CONNECTION_STATE);
        handler.execute(resumePayload("alice", false));
        assertEquals(ClientState.PLACING_STATE, ClientStateModel.getInstance().getClientState());
    }

    @Test
    void resumingAfterAlreadyPlacingPutsYouInDrawing() {
        //dropping between placing and drawing must not send you back to placing: the server would refuse
        ClientStateModel.getInstance().setClientState(ClientState.LOST_CONNECTION_STATE);
        handler.execute(resumePayload("alice", true));
        assertEquals(ClientState.DRAWING_STATE, ClientStateModel.getInstance().getClientState());
    }

    @Test
    void resumingOnSomeoneElsesTurnJustWaits() {
        ClientStateModel.getInstance().setClientState(ClientState.LOST_CONNECTION_STATE);
        handler.execute(resumePayload("bob", false));
        assertEquals(ClientState.NOT_PLAYING_STATE, ClientStateModel.getInstance().getClientState());
    }

    @Test
    void lostConnectionStaysFinalWithoutAResume() {
        ClientStateModel.getInstance().setClientState(ClientState.LOST_CONNECTION_STATE);
        ClientStateModel.getInstance().setClientState(ClientState.PLACING_STATE);
        assertEquals(ClientState.LOST_CONNECTION_STATE, ClientStateModel.getInstance().getClientState(),
                "only the reconnection flow may leave this state");
    }

    @Test
    void aNormalGameStartStillWorks() {
        ClientStateModel.getInstance().setClientState(ClientState.GAME_SETUP_STATE);
        handler.execute(resumePayload("alice", false));
        assertEquals(ClientState.PLACING_STATE, ClientStateModel.getInstance().getClientState());
    }
}
