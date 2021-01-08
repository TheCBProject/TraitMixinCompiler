package codechicken.mixin.classes;

/**
 * Created by covers1624 on 2/16/20.
 */
public class Mixin1 extends MixinBase {

    private static String wheee = ClassWithField.doThing(e -> {
        System.out.println(e);
    });

    private String stuff;

    @Override
    public void setStuff(String stuff) {
        this.stuff = stuff;
    }

    @Override
    public String append(String otherStuff) {
        System.out.println(wheee);
        ClassWithField classWithField = new ClassWithField();
        System.out.println(classWithField.someField);
        return stuff + otherStuff;
    }

}
