package codechicken.mixin.util;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;

/**
 * Created by covers1624 on 2/11/20.
 */
public record FieldMixin(String name, String desc, int access) {

    public String getAccessName(String owner) {
        if ((access & ACC_PRIVATE) != 0) {
            return owner.replace("/", "$") + "$$" + name;
        }
        return name;
    }
}
