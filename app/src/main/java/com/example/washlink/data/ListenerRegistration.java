package com.example.washlink.data;

/**
 * Minimal listener handle used in place of Firebase's ListenerRegistration so
 * the project can compile without the Firestore SDK present.
 */
public interface ListenerRegistration {
    void remove();
}
