package com.example.washlink.ui;

public final class PasswordStrength {
    private PasswordStrength() {}

    public static int score(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = password.length() >= 8 ? 1 : 0;
        if (password.matches("(?s).*\\p{Lower}.*")) score++;
        if (password.matches("(?s).*\\p{Upper}.*")) score++;
        if (password.matches("(?s).*\\d.*")) score++;
        if (password.matches("(?s).*[^\\p{Alnum}].*")) score++;
        return score;
    }
}
