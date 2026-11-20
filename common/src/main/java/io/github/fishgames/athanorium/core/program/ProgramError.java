package io.github.fishgames.athanorium.core.program;

/**
 * A reason why a program fails the program check. {@code key} is a translation key, {@code detail} names the
 * part, slot or position (not translated, shown after the message).
 */
public record ProgramError(String key, String detail) {
}
