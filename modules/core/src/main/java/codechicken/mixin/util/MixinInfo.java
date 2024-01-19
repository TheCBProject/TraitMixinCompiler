package codechicken.mixin.util;

import net.covers1624.quack.collection.FastStream;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Created by covers1624 on 2/11/20.
 */
public record MixinInfo(String name, String parent, List<MixinInfo> parentTraits, List<FieldMixin> fields, List<MethodNode> methods, List<String> supers) {

    public FastStream<MixinInfo> linearize() {
        return FastStream.concat(
                FastStream.of(parentTraits).flatMap(MixinInfo::linearize),
                FastStream.of(this)
        );
    }
}
