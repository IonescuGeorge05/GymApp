package org.example.model;

public class User {
    private final long id;
    private final String email;
    private final String fullName;
    private final String membershipType;
    private final int sessionsLeft;

    public User(long id, String email, String fullName, String membershipType, int sessionsLeft) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.membershipType = membershipType;
        this.sessionsLeft = sessionsLeft;
    }

    public long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getMembershipType() { return membershipType; }
    public int getSessionsLeft() { return sessionsLeft; }
}
