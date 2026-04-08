package org.example.model;

// clasa care reprezinta o sedinta/clasa din sala
public class ClassSession {

    // nume clasa
    private final String name;
    // instructor care tine clasa
    private final String instructor;
    // ora la care se desfasoara
    private final String time;
    // cate locuri mai sunt disponibile
    private final int spotsLeft;

    // constructor cu toate campurile
    public ClassSession(String name, String instructor, String time, int spotsLeft) {
        this.name = name;
        this.instructor = instructor;
        this.time = time;
        this.spotsLeft = spotsLeft;
    }

    // get nume
    public String getName() { return name; }
    // get instructor
    public String getInstructor() { return instructor; }
    // get ora
    public String getTime() { return time; }
    // get locuri ramase
    public int getSpotsLeft() { return spotsLeft; }
}
