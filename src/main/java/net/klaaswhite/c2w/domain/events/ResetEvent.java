package net.klaaswhite.c2w.domain.events;

/**
 * Pushed by {@code GameManager.reset()} to signal that all game state should
 * be torn down. Managers that hold per-game state (e.g. {@code MarkerManager}
 * and its wools) subscribe to this event and clear themselves.
 */
public class ResetEvent implements C2WEvent {
}
