package codechicken.mixin.api;

/**
 * Created by covers1624 on 2/9/20.
 */
public interface MixinDebugger {

    void defineInternal(@AsmName String name, byte[] bytes);

    void defineClass(@AsmName String name, byte[] bytes);

    class NullDebugger implements MixinDebugger {

        @Override
        public void defineInternal(String name, byte[] bytes) {
        }

        @Override
        public void defineClass(String name, byte[] bytes) {
        }
    }
}
