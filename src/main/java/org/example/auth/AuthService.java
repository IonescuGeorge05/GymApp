package org.example.auth;

import org.example.dao.UserDao;
import org.example.model.User;
import org.mindrot.jbcrypt.BCrypt;

public class AuthService {

    private final UserDao userDao = new UserDao();

    public User register(String email, String fullName, String plainPassword) throws Exception {
        email = email.trim().toLowerCase();
        if (email.isBlank() || fullName.isBlank() || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Completeaza toate campurile.");
        }

        if (userDao.findByEmail(email) != null) {
            throw new IllegalArgumentException("Email deja folosit.");
        }

        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
        return userDao.create(email, fullName.trim(), hash);
    }

    public User login(String email, String plainPassword) throws Exception {
        email = email.trim().toLowerCase();

        String hash = userDao.getPasswordHashByEmail(email);
        if (hash == null) throw new IllegalArgumentException("Email sau parola gresita.");

        if (!BCrypt.checkpw(plainPassword, hash)) {
            throw new IllegalArgumentException("Email sau parola gresita.");
        }

        return userDao.findByEmail(email);
    }
}
