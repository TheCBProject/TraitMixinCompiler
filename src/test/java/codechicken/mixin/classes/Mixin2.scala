package codechicken.mixin.classes

/**
 * Created by covers1624 on 2/16/20.
 */
trait Mixin2 extends MixinBase {

    private var stuff: String = _

    override def setStuff(stuff: String) {
        this.stuff = stuff
    }

    override def append(otherStuff: String): String = stuff + otherStuff

}
