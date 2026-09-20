package it.polimi.ingsw.client.controller;

import it.polimi.ingsw.client.model.ChatModel;
import it.polimi.ingsw.client.model.PlayerModel;
import it.polimi.ingsw.util.supportclasses.ChatLine;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static it.polimi.ingsw.util.supportclasses.Constants.CHAT_HISTORY_SIZE;
import static org.junit.jupiter.api.Assertions.*;

class ChatClientTest {
    private final ClientMessageHandler handler = new ClientMessageHandler();

    @BeforeEach
    void setUp() {
        ChatModel.getInstance().clear();
        PlayerModel.getInstance().setUsername("alice");
    }

    @AfterEach
    void tearDown() {
        ChatModel.getInstance().clear();
    }

    @SuppressWarnings("unchecked")
    private JSONObject incoming(String sender, String recipient, String text) {
        JSONObject message = new JSONObject();
        message.put("message", "chatMessage");
        message.put("sender", sender);
        message.put("text", text);
        if (recipient != null) message.put("recipient", recipient);
        return message;
    }

    @Test
    void aPublicLineLandsInTheModel() {
        handler.execute(incoming("bob", null, "hello"));
        ChatLine line = ChatModel.getInstance().getLastLine();
        assertNotNull(line);
        assertEquals("bob", line.sender());
        assertFalse(line.isPrivate());
        assertEquals("bob: hello", line.format("alice"));
    }

    @Test
    void aWhisperIsMarkedAsOne() {
        handler.execute(incoming("bob", "alice", "just for you"));
        ChatLine line = ChatModel.getInstance().getLastLine();
        assertTrue(line.isPrivate());
        assertEquals("[bob -> you] just for you", line.format("alice"));
    }

    @Test
    void yourOwnLinesReadAsYours() {
        handler.execute(incoming("alice", "bob", "psst"));
        assertEquals("[you -> bob] psst", ChatModel.getInstance().getLastLine().format("alice"));
    }

    @Test
    void theChatStopsGrowingOnceItIsFull() {
        for (int i = 0; i < CHAT_HISTORY_SIZE + 25; i++) {
            ChatModel.getInstance().addLine(new ChatLine("bob", null, "line " + i));
        }
        assertEquals(CHAT_HISTORY_SIZE, ChatModel.getInstance().getLines().size());
        assertEquals("line " + (CHAT_HISTORY_SIZE + 24), ChatModel.getInstance().getLastLine().text());
        assertEquals("line 25", ChatModel.getInstance().getLines().getFirst().text(), "the oldest lines drop off");
    }

    @Test
    void theChatIsNotHandedOutForEditing() {
        ChatModel.getInstance().addLine(new ChatLine("bob", null, "hi"));
        assertThrows(UnsupportedOperationException.class,
                () -> ChatModel.getInstance().getLines().add(new ChatLine("mallory", null, "sneaky")));
    }
}
