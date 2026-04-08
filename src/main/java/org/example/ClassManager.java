package org.example;

import org.example.model.ClassSession;
import java.util.ArrayList;
import java.util.List;

public class ClassManager {

    private final List<ClassSession> classes = new ArrayList<>();

    public void addClass(ClassSession session) {
        classes.add(session);
    }

    public boolean removeClassByName(String name) {
        return classes.removeIf(c -> c.getName().equalsIgnoreCase(name));
    }

    public List<ClassSession> getClasses() {
        return classes;
    }
}
