package yalter.mousetweaks.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time stub of Mouse Tweaks' API annotation (same name and retention as in
 * mousetweaks-1.12.2-3.x). Excluded from the built jar (see build.gradle): at runtime the real class from the
 * Mouse Tweaks mod is used; without Mouse Tweaks installed the JVM simply ignores the annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MouseTweaksDisableWheelTweak
{
}
