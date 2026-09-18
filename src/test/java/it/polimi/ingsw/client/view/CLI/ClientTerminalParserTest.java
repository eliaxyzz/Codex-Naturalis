package it.polimi.ingsw.client.view.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class ClientTerminalParserTest {
    private final ClientTerminalParser parser = new ClientTerminalParser();
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
    }

    @Test
    void infoWithoutAnIdIsAnErrorNotACrash() {
        assertDoesNotThrow(() -> parser.parse("info"));
        assertTrue(output.toString().contains("Invalid number of arguments"));
    }

    @Test
    void aBadNumberIsReportedOnce() {
        parser.parse("info abc");
        assertEquals(1, output.toString().split("invalid id", -1).length - 1);
    }

    @Test
    void leadingSpacesDoNotHideTheCommand() {
        parser.parse("   board");
        assertFalse(output.toString().contains("Unknown command"));
    }

    @Test
    void unknownCommandsAreReported() {
        parser.parse("fly");
        assertTrue(output.toString().contains("Unknown command"));
    }
}
