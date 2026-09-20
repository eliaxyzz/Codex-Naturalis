package it.polimi.ingsw.client.view.CLI;

import it.polimi.ingsw.client.controller.ClientMessageHandler;
import it.polimi.ingsw.client.model.DeckModel;
import it.polimi.ingsw.client.model.PlayerModel;
import it.polimi.ingsw.client.model.ScoreBoardModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrinterTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void captureOutput() {
        originalOut = System.out;
        System.setOut(new PrintStream(output, true));
    }

    @AfterEach
    void restoreOutput() {
        System.setOut(originalOut);
        DeckModel.getInstance().clear();
        ScoreBoardModel.getInstance().clear();
    }

    @Test
    void exhaustedDecksArePrintedAsEmptyInsteadOfCrashing() {
        //an empty deck or revealed slot reports id 0, which is not a card
        DeckModel.getInstance().clear();
        assertDoesNotThrow(Printer::printDeckInfo);
        assertTrue(output.toString().contains("empty"));
    }

    @Test
    void deckInfoStillShowsTheRevealedCardsItHas() {
        DeckModel.getInstance().updateDecks(3, 1, 2, 0, 41, 42);
        Printer.printDeckInfo();
        String printed = output.toString();
        assertTrue(printed.contains("#1"));
        assertTrue(printed.contains("#41"));
        //the top of a deck stays hidden, but an exhausted gold deck is reported
        assertFalse(printed.contains("#3"));
        assertTrue(printed.contains("empty"));
    }

    @Test
    void scoresKeepTheTurnOrderTheServerSentThemIn() {
        //alice/bob/carol come out of a HashMap in the reverse order, so this pins the ordering
        JSONArray sent = new JSONArray();
        sent.add(scoreEntry("alice", 1));
        sent.add(scoreEntry("bob", 2));
        sent.add(scoreEntry("carol", 3));
        JSONObject message = new JSONObject();
        message.put("message", "updatedScores");
        message.put("updatedScores", sent);

        new ClientMessageHandler().execute(message);

        assertEquals(List.of("alice", "bob", "carol"),
                new ArrayList<>(ScoreBoardModel.getInstance().getScore().keySet()));

        PlayerModel.getInstance().setUsername("nobody");
        Printer.printScores();
        String printed = output.toString();
        assertTrue(printed.indexOf("alice") < printed.indexOf("bob"));
        assertTrue(printed.indexOf("bob") < printed.indexOf("carol"));
    }

    @SuppressWarnings("unchecked")
    private static JSONObject scoreEntry(String username, int score) {
        JSONObject entry = new JSONObject();
        entry.put("username", username);
        entry.put("score", String.valueOf(score));
        entry.put("token", "red");
        return entry;
    }
}
