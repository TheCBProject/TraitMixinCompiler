package codechicken.mixin.classes;

/**
 * Created by covers1624 on 2/16/20.
 */
public class Mixin1 extends MixinBase {

    private static String wheee = "doot";

    private String stuff;

    @Override
    public void setStuff(String stuff) {
        this.stuff = stuff;
    }

    @Override
    public String append(String otherStuff) {
        ClassWithField classWithField = new ClassWithField();
        System.out.println(classWithField.someField);
        return stuff + otherStuff;
    }

}
