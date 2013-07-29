package ds.mods.CCLights2.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)

public @interface LuaMethod {
	public enum Type { STRING, INT, FLOAT, DOUBLE, MAP, BOOLEAN, OBJECT, OBJECTS, LuaObject, NULL };
	Type[] args() default {};
	String name();
	Type ret();
	String description() default "No description";
	boolean networked() default false;
}
