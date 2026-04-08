package org.example;

import java.time.LocalDate;

// clasa care reprezinta un membru al salii
public class Member {
    // id unic membru
    private int id;
    // nume membru
    private String name;
    // tipul abonamentului
    private String membershipType;
    // cand expira abonamentu
    private LocalDate membershipExpiresAt;

    // constructor cu toate campurile
    public Member(int id, String name, String membershipType, LocalDate membershipExpiresAt) {
        this.id = id;
        this.name = name;
        this.membershipType = membershipType;
        this.membershipExpiresAt = membershipExpiresAt;
    }

    // get id
    public int getId() { return id; }
    // get nume
    public String getName() { return name; }
    // get tip abonament
    public String getMembershipType() { return membershipType; }
    // get cand expira abonamentu
    public LocalDate getMembershipExpiresAt() { return membershipExpiresAt; }
}
