package org.quiltmc.javagen.test_input;

import org.quiltmc.javagen.test_input.api.Sealed;

@Sealed({ org.quiltmc.javagen.test_input.TestSealed.InnerClass.class, AltInnerClass.class })
public abstract class TestSealed {

    public static final class InnerClass extends TestSealed {

    }
}

final class AltInnerClass extends TestSealed {

}
