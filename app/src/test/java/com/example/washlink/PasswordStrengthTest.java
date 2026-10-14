package com.example.washlink;

import com.example.washlink.ui.PasswordStrength;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PasswordStrengthTest {
    @Test
    public void emptyPasswordHasNoStrength() {
        assertEquals(0, PasswordStrength.score(""));
    }

    @Test
    public void scoreCountsLengthAndCharacterVariety() {
        assertEquals(2, PasswordStrength.score("abcdefgh"));
        assertEquals(4, PasswordStrength.score("Abcdefg1"));
        assertEquals(5, PasswordStrength.score("Abcdefg1!"));
    }
}
