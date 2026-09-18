package it.polimi.ingsw.server.model.card;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CardIdentityTest {

    @Test
    void cardsWithTheSameIdAreEqualAndHashAlike() {
        ResourceCard a = new ResourceCard(1);
        ResourceCard b = new ResourceCard(1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new ResourceCard(2));
    }

    @Test
    void placingACardDoesNotChangeItsIdentity() {
        GoldCard card = new GoldCard(41);
        Set<PlaceableCard> set = new HashSet<>();
        set.add(card);
        card.setX(3);
        card.setY(-1);
        card.setFacingUp(false);
        assertTrue(set.contains(card));
    }

    @Test
    void starterCardsAreComparedByIdToo() {
        assertNotEquals(new StarterCard(81), new StarterCard(82));
    }

    @Test
    void unknownIdsFailInsteadOfBuildingHalfACard() {
        assertThrows(IllegalArgumentException.class, () -> new GoldCard(5));
        assertThrows(IllegalArgumentException.class, () -> new ResourceCard(99));
        assertThrows(IllegalArgumentException.class, () -> new StarterCard(1));
    }
}
