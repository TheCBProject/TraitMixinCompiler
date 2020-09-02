package codechicken.mixin.test.inheritancerenderstatic;

/**
 * Created by covers1624 on 3/9/20.
 */
public class TModelRenderTile extends TileMultipart implements TileMultipartClient {

    @Override
    public boolean renderStatic() {
        System.out.println("TModelRenderTile.renderStatic");
        return !TileMultipartClient.super.renderStatic();
    }
}
