package codechicken.mixin.test.inheritancerenderstatic

/**
 * Created by covers1624 on 3/9/20.
 */
class TileMultipart {

}

trait TileMultipartClient extends TileMultipart {

    def renderStatic(): Boolean = {
        System.out.println("TileMultipartClient.renderStatic")
        true
    }

}
