package codechicken.mixin.test.inheritancerenderstatic;

import codechicken.mixin.api.MixinFactory;
import codechicken.mixin.test.BaseTest;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

/**
 * Created by covers1624 on 3/9/20.
 */
public class InheritanceTests extends BaseTest {

    @Test
    public void doTest() {
        MixinFactory<TileMultipart> factory = setup(TileMultipart.class, "tile_multipart_inheritance_test");
        MixinFactory.TraitKey clientTrait = factory.registerTrait("codechicken/mixin/test/inheritancerenderstatic/TileMultipartClient");
        MixinFactory.TraitKey modelRenderTrait = factory.registerTrait("codechicken/mixin/test/inheritancerenderstatic/TModelRenderTile");

        TileMultipart tile = factory.construct(ImmutableSet.of(clientTrait, modelRenderTrait));

        ((TileMultipartClient) tile).renderStatic();

    }

}
