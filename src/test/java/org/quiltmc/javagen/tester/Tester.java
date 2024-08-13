package org.quiltmc.javagen.tester;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.quiltmc.javagen.MultiJavaGen;
import org.quiltmc.javagen.MultiJavaGen.JavaVersion;
import org.quiltmc.javagen.MultiJavaGen.OutputOptions;
import org.quiltmc.javagen.test_input.api.Sealed;

public class Tester {
    public static void main(String[] args) throws IOException {
        MultiJavaGen gen = new MultiJavaGen();
        gen.setSealedAnnotation(Sealed.class);

        Path outputJava17 = Paths.get("src/java17/java/org/quiltmc/javagen/test_input");
        OutputOptions[] output = { new OutputOptions(JavaVersion.JAVA_17, outputJava17) };
        Path input = Paths.get("src/test/java/org/quiltmc/javagen/test_input");
        Path[] classpath = { Paths.get("src/test/java") };
        gen.generate(input, classpath, output);
    }
}
