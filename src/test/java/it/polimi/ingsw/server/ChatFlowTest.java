package it.polimi.ingsw.server;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * In-game chat over real sockets: who gets what, and what a returning player is allowed to read.
 */
class ChatFlowTest {

    @Test
    void aPublicMessageReachesEveryone() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            alice.send("chat", "text", "hello table");

            for (TestClient player : List.of(alice, bob)) {
                JSONObject line = player.await("chatMessage");
                assertEquals("alice", line.get("sender"));
                assertEquals("hello table", line.get("text"));
                assertNull(line.get("recipient"), "a public line has no recipient");
            }
        }
    }

    @Test
    void aWhisperReachesOnlyTheRecipientAndTheSender() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            TestClient carol = harness.connect("carol");
            harness.startGame("g", alice, bob, carol);

            alice.send("chat", "text", "just for you", "recipient", "bob");

            assertEquals("bob", alice.await("chatMessage").get("recipient"), "the sender sees their own whisper");
            assertEquals("just for you", bob.await("chatMessage").get("text"));
            assertEquals(0, carol.countWithin("chatMessage", 400), "a whisper must not reach anyone else");
        }
    }

    @Test
    void whisperingToSomeoneWhoIsNotAtTheTableIsRefused() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            alice.send("chat", "text", "hi", "recipient", "nobody");
            assertTrue(alice.await("cannotChat").get("reason").toString().contains("nobody"));
        }
    }

    @Test
    void emptyAndOversizedMessagesAreRefused() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            alice.send("chat", "text", "   ");
            assertNotNull(alice.await("cannotChat"));

            alice.send("chat", "text", "x".repeat(500));
            assertNotNull(alice.await("cannotChat"));
            assertEquals(0, bob.countWithin("chatMessage", 300), "nothing refused may be delivered");
        }
    }

    @Test
    void aReturningPlayerReadsWhatTheyMissedButNotOtherPeoplesWhispers() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            TestClient carol = harness.connect("carol");
            harness.startGame("g", alice, bob, carol);

            //carol drops, then things are said while she is away
            carol.close();
            alice.await("playerSuspended");
            alice.send("chat", "text", "public while carol is away");
            alice.send("chat", "text", "secret between alice and bob", "recipient", "bob");
            alice.send("chat", "text", "carol will read this later", "recipient", "carol");
            bob.await("chatMessage");

            TestClient carolAgain = harness.connect("carol-returning");
            carolAgain.send("reconnect", "username", "carol", "gameName", "g");
            carolAgain.await("startGame");

            List<String> replayed = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                replayed.add(carolAgain.await("chatMessage").get("text").toString());
            }
            assertTrue(replayed.contains("public while carol is away"));
            assertTrue(replayed.contains("carol will read this later"));
            assertEquals(0, carolAgain.countWithin("chatMessage", 400),
                    "the whisper between alice and bob must never reach carol");
        }
    }

    @Test
    void chattingIsPossibleDuringSetupToo() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            alice.send("setUp", "gameName", "g", "numOfPlayers", "2");
            alice.await("gameCreated");
            bob.send("join", "gameName", "g");
            bob.await("joinGame");

            bob.send("chat", "text", "ready when you are");
            assertEquals("ready when you are", alice.await("chatMessage").get("text"));
        }
    }
}
