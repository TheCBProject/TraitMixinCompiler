package codechicken.mixin.api;

/**
 * Created by covers1624 on 2/9/20.
 */
public interface MixinDebugger {

    void defineClass(@AsmName String name, byte[] bytes);

    class NullDebugger implements MixinDebugger {

        @Override
        public void defineClass(String name, byte[] bytes) {
        }
    }
}
