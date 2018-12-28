package OOP.Solution;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OOPTest {
    // TODO: need to check if this is the correct thing to do,
    // otherwise the tests from last year do not compile
    int order() default -1;
    String tag() default  "";
}
