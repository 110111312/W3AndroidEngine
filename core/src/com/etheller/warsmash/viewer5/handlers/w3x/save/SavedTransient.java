package com.etheller.warsmash.viewer5.handlers.w3x.save;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field that is a cache of what the rest of the game state works out: a save leaves it out and a loaded game starts
 * with it empty.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SavedTransient {
}
