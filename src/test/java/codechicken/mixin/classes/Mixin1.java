package codechicken.mixin.classes;

/**
 * Created by covers1624 on 2/16/20.
 */
public class Mixin1 extends MixinBase {

    private String stuff;

    @Override
    public void setStuff(String stuff) {
        this.stuff = stuff;
    }

    @Override
    public String append(String otherStuff) {
        return stuff + otherStuff;
    }

}
