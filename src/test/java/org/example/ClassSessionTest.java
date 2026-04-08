package org.example;

import org.example.model.ClassSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassSessionTest {

    @Test
    void testClassSessionGetters() {
        ClassSession c = new ClassSession("Yoga", "Maria", "18:00", 5);

        assertEquals("Yoga", c.getName());
        assertEquals("Maria", c.getInstructor());
        assertEquals("18:00", c.getTime());
        assertEquals(5, c.getSpotsLeft());
    }

    @Test
    void testClassSessionNotNull() {
        ClassSession c = new ClassSession("Box", "Andrei", "19:00", 3);
        assertNotNull(c);
    }
}
