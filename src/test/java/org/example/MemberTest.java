package org.example;

import org.example.Member;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    void testMemberGetters() {
        Member m = new Member(1, "George Popescu", "Premium", 12);

        assertEquals(1, m.getId());
        assertEquals("George Popescu", m.getName());
        assertEquals("Premium", m.getMembershipType());
        assertEquals(12, m.getSessionsLeft());
    }

    @Test
    void testMemberNotNull() {
        Member m = new Member(2, "Ana Ionescu", "Standard", 8);
        assertNotNull(m);
    }
}
