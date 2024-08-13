package org.quiltmc.javagen.test_input;

public abstract sealed class TestSealed permits org.quiltmc.javagen.test_input.TestSealed.InnerClass, AltInnerClass {

    public static final class InnerClass extends TestSealed {

    }
}

final class AltInnerClass extends TestSealed {

}
