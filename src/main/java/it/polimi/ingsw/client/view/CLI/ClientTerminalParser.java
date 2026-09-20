package it.polimi.ingsw.client.view.CLI;

import it.polimi.ingsw.client.controller.ClientController;
import it.polimi.ingsw.client.model.ClientStateModel;
import it.polimi.ingsw.client.model.GameFieldModel;
import it.polimi.ingsw.client.model.HandModel;
import it.polimi.ingsw.client.model.SelectableCardsModel;
import it.polimi.ingsw.client.view.utility.CardRepresentation;
import it.polimi.ingsw.util.cli.CommandParser;
import it.polimi.ingsw.util.supportclasses.ClientState;
import it.polimi.ingsw.util.supportclasses.ConsoleColor;
import static it.polimi.ingsw.util.supportclasses.Constants.MAX_PLAYERS;
import static it.polimi.ingsw.util.supportclasses.Constants.MIN_PLAYERS;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * This class is responsible for parsing user commands entered the Codex game client's CLI.
 * Each command (and its short alias) maps to a handler that receives the whitespace-split tokens.
 */
public class ClientTerminalParser implements CommandParser {
    private static final Set<ClientState> IN_GAME = EnumSet.of(ClientState.PLACING_STATE, ClientState.DRAWING_STATE,
            ClientState.NOT_PLAYING_STATE, ClientState.LAST_ROUND_STATE);

    private static final int MAX_USERNAME_LENGTH = 15;

    private final Map<String, Consumer<String[]>> commands = new HashMap<>();

    public ClientTerminalParser() {
        register(tokens -> Printer.printHelp(), "help", "h", "?");
        register(this::updateUsername, "setusername", "su");
        register(tokens -> ClientController.getInstance().sendLeaveMessage(), "leave", "l");
        register(tokens -> ClientCLI.getInstance().shutdown(), "quit", "q");
        register(this::createGame, "create", "c");
        register(this::getAvailableGames, "availablegames", "ag");
        register(this::getInfo, "info");
        register(this::joinGame, "join", "j");
        register(this::setReady, "ready", "r");
        register(this::selectStarterCardOrientation, "startercard", "sc");
        register(this::selectSecretObjective, "secretobjective", "so");
        register(this::place, "place", "p");
        register(this::draw, "draw", "d");
        register(inGameOnly(Printer::printGameBoard), "board");
        register(inGameOnly(Printer::printHand), "hand");
        register(inGameOnly(Printer::printScores), "score");
        register(inGameOnly(Printer::printObjectives), "objectives", "obj");
        register(inGameOnly(Printer::printDeckInfo), "decks");
        register(tokens -> Printer.printGuide(), "guide");
    }

    private void register(Consumer<String[]> handler, String... names) {
        for (String name : names) {
            commands.put(name, handler);
        }
    }

    /**
     * Parses a user command entered the CLI.
     * @param command The user-entered command string.
     */
    @Override
    public void parse(String command) {
        String[] tokens = command.trim().split("\\s+");
        Consumer<String[]> handler = commands.get(tokens[0].toLowerCase());
        if (handler == null) {
            System.out.println("Unknown command");
            System.out.println("Type 'help' for more information.");
            System.out.println();
            return;
        }
        handler.accept(tokens);
    }

    private void parseError(String messageError) {
        System.out.println("Unexpected arguments: " + messageError);
        System.out.println();
    }

    private void parseError() {
        System.out.println("Unexpected arguments");
        System.out.println();
    }

    private void unexpected(String reason) {
        System.out.println("Unexpected command");
        System.out.println(reason);
        System.out.println();
    }

    private ClientState state() {
        return ClientStateModel.getInstance().getClientState();
    }

    /**
     * @return the parsed number, or null after telling the user what was wrong with it.
     */
    private Integer parseNumber(String token, String whatItIs) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            parseError("invalid " + whatItIs);
            return null;
        }
    }

    private boolean requireLobby() {
        if (state() == ClientState.LOBBY_STATE) return true;
        unexpected("You're not in the lobby");
        return false;
    }

    private boolean requireGameSetup() {
        if (state() == ClientState.GAME_SETUP_STATE) return true;
        unexpected("You're not in a game");
        return false;
    }

    private Consumer<String[]> inGameOnly(Runnable action) {
        return tokens -> {
            if (IN_GAME.contains(state())) action.run();
            else unexpected("you're not in a game");
        };
    }

    private void updateUsername(String[] tokens) {
        if (!requireLobby()) return;
        if (tokens.length != 2) {
            parseError();
            return;
        }
        String username = tokens[1].trim();
        if (username.length() > MAX_USERNAME_LENGTH) {
            Printer.printMessage("The username must be at most " + MAX_USERNAME_LENGTH + " characters", ConsoleColor.RED);
        } else if (!username.matches("^[a-zA-Z0-9_]*$")) {
            parseError("invalid username");
        } else {
            ClientController.getInstance().sendSetUsernameMessage(username);
        }
    }

    private void createGame(String[] tokens) {
        if (!requireLobby()) return;
        if (tokens.length != 3) {
            parseError();
            return;
        }
        Integer numberOfPlayers = parseNumber(tokens[2], "number of players");
        if (numberOfPlayers == null) return;
        if (numberOfPlayers < MIN_PLAYERS || numberOfPlayers > MAX_PLAYERS) {
            parseError("number of players must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS);
            return;
        }
        ClientController.getInstance().sendSetUpGameMessage(tokens[1].trim(), numberOfPlayers);
    }

    private void getAvailableGames(String[] tokens) {
        if (requireLobby()) ClientController.getInstance().sendGetAvailableGamesMessage();
    }

    private void joinGame(String[] tokens) {
        if (!requireLobby()) return;
        if (tokens.length == 2) ClientController.getInstance().sendJoinGameMessage(tokens[1]);
        else parseError();
    }

    private void setReady(String[] tokens) {
        if (!requireGameSetup()) return;
        Printer.printMessage("You are ready! - waiting for all the players to get ready...");
        ClientController.getInstance().sendReadyMessage();
    }

    private void selectStarterCardOrientation(String[] tokens) {
        if (!requireGameSetup()) return;
        if (tokens.length != 2) {
            parseError();
            return;
        }
        int starterCardID = SelectableCardsModel.getInstance().getStarterCardId();
        switch (tokens[1]) {
            case "front" -> ClientController.getInstance().sendChosenStarterCardSideMessage(starterCardID, true);
            case "back" -> ClientController.getInstance().sendChosenStarterCardSideMessage(starterCardID, false);
            default -> parseError("invalid parameters");
        }
    }

    private void selectSecretObjective(String[] tokens) {
        if (!requireGameSetup()) return;
        if (tokens.length != 2) {
            parseError();
            return;
        }
        Integer objectiveId = parseNumber(tokens[1], "objective card id");
        if (objectiveId == null) return;
        int[] offered = SelectableCardsModel.getInstance().getSelectableObjectiveCardsId();
        if (objectiveId == offered[0] || objectiveId == offered[1]) {
            ClientController.getInstance().sendChosenSecretObjectiveMessage(objectiveId);
        } else {
            parseError("This ID is not valid");
        }
    }

    /**
     * place &lt;card id&gt; &lt;front|back&gt; &lt;target card id&gt; &lt;tl|tr|bl|br&gt;
     */
    private void place(String[] tokens) {
        if (state() != ClientState.PLACING_STATE) {
            unexpected(switch (state()) {
                case NOT_PLAYING_STATE -> "Not your turn";
                case DRAWING_STATE -> "You have already placed, draw a card";
                default -> "You're not playing right now";
            });
            return;
        }
        if (tokens.length != 5) {
            parseError();
            return;
        }
        Integer cardId = parseNumber(tokens[1], "card id");
        Integer targetId = parseNumber(tokens[3], "target card id");
        if (cardId == null || targetId == null) return;
        if (findCard(HandModel.getInstance().getCardsInHand(), cardId) == null) {
            parseError("You don't have the #" + cardId + " card in your hand");
            return;
        }
        boolean facingUp;
        switch (tokens[2]) {
            case "front" -> facingUp = true;
            case "back" -> facingUp = false;
            default -> {
                parseError();
                return;
            }
        }
        CardRepresentation target = findCard(GameFieldModel.getInstance().getPlacementHistory(), targetId);
        if (target == null) {
            parseError("the target card is not on your field");
            return;
        }
        int[] offset = switch (tokens[4]) {
            case "topleft", "tl" -> new int[]{-1, 1};
            case "topright", "tr" -> new int[]{1, 1};
            case "bottomleft", "bl" -> new int[]{-1, -1};
            case "bottomright", "br" -> new int[]{1, -1};
            default -> null;
        };
        if (offset == null) {
            parseError("the position argument is not correct");
            return;
        }
        ClientController.getInstance().sendPlaceMessage(cardId, target.getX() + offset[0], target.getY() + offset[1], facingUp);
    }

    private CardRepresentation findCard(List<CardRepresentation> cards, int id) {
        for (CardRepresentation card : cards) {
            if (card.getId() == id) return card;
        }
        return null;
    }

    /**
     * draw &lt;1-6&gt;: resource deck, left resource, right resource, gold deck, left gold, right gold.
     */
    private void draw(String[] tokens) {
        if (state() != ClientState.DRAWING_STATE) {
            unexpected(switch (state()) {
                case NOT_PLAYING_STATE -> "Not your turn";
                case PLACING_STATE -> "You have to place a card first";
                default -> "You're not playing right now";
            });
            return;
        }
        if (tokens.length != 2) {
            parseError();
            return;
        }
        ClientController controller = ClientController.getInstance();
        switch (tokens[1]) {
            case "1" -> controller.sendDirectDrawResourceCardMessage();
            case "2" -> controller.sendDrawLeftResourceCardMessage();
            case "3" -> controller.sendDrawRightResourceCardMessage();
            case "4" -> controller.sendDirectDrawGoldCardMessage();
            case "5" -> controller.sendDrawLeftGoldCardMessage();
            case "6" -> controller.sendDrawRightGoldCardMessage();
            default -> parseError("the deck selection must be between 1 and 6");
        }
    }

    /**
     * info &lt;card id&gt; [front|back]
     */
    private void getInfo(String[] tokens) {
        if (tokens.length < 2 || tokens.length > 3) {
            System.out.println("Error: Invalid number of arguments");
            return;
        }
        Integer id = parseNumber(tokens[1], "id");
        if (id == null) return;
        if (tokens.length == 2 || tokens[2].equals("front")) {
            Printer.printCardInfo(id, true);
        } else if (tokens[2].equals("back")) {
            Printer.printCardInfo(id, false);
        } else {
            parseError("the side must be front or back");
        }
    }
}
