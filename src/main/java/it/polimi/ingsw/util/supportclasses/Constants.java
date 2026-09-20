package it.polimi.ingsw.util.supportclasses;

public final class Constants {
    public static final int MAX_HAND_SIZE = 3;
    public static final int SCORE_GOAL = 20;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4; //one per token colour

    //card ids are laid out in contiguous ranges by type
    public static final int LAST_RESOURCE_CARD_ID = 40;
    public static final int LAST_GOLD_CARD_ID = 80;
    public static final int LAST_STARTER_CARD_ID = 86;
    public static final int LAST_OBJECTIVE_CARD_ID = 102;

    //in-game chat
    public static final int CHAT_HISTORY_SIZE = 100;
    public static final int MAX_CHAT_LENGTH = 200;

    //how long a game waits with a single player left before awarding it to them
    public static final int RECONNECT_TIMEOUT = 60000;
    //how the client retries after a mid-game drop
    public static final int RECONNECT_RETRY_INTERVAL = 3000;
    public static final int RECONNECT_RETRY_ATTEMPTS = 20;

    public static final int PING_INTERVAL = 2000; //defines the time between ping messages in milliseconds
    public static final int PING_TRIES = 3; //defines the number of ping that have no response before determining the connection is lost
}
