package codechicken.mixin.classes;

import java.util.function.Consumer;

/**
 * Created by covers1624 on 1/9/20.
 */
public class ClassWithField {

    public String someField;

    public static String doThing(Consumer<String> cons) {
        cons.accept("Doot");
        return "Doot";
    }

}
