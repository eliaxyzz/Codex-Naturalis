package it.polimi.ingsw.util.cli;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * This class represents a reader that is always ready to take inputs from the terminal.
 */
public abstract class TerminalInputReader implements Runnable {
    protected CommandParser commandParser;
    protected boolean running = true;
    protected Scanner scanner = new Scanner(System.in);

    /**
     * Reads a new line from the input.
     */
    @Override
    public void run() {
        while (running) {
            try {
                commandParser.parse(scanner.nextLine());
            }
            catch (NoSuchElementException | IllegalStateException e) {
                //stdin is closed (EOF or shutdown): there's nothing left to read, and retrying would spin forever
                running = false;
            }
        }
    }

    /**
     * Terminates the TerminalInputReader execution.
     */
    public void shutdown() {
        running = false;
        scanner.close();
    }
}
