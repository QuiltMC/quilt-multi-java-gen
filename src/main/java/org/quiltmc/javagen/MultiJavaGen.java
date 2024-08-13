package org.quiltmc.javagen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

/** Main entry point for generating */
public class MultiJavaGen {

    // ###########
    // # Options #
    // ###########

    //
    // Specific java feature options
    //

    // Sealed

    /** Class name for a 'Sealed' annotation, which should have a single property: "Class[] values()", which is for the
     * permitted classes of this actual class. Only used if {@link JavaFeature#SEALED_CLASSES} is enabled. */
    public String sealedAnnotation;

    /** Class name for a 'Non Sealed' annotation, which should have no properties. if present this will generate a
     * "non-sealed" keyword. Only used if {@link JavaFeature#SEALED_CLASSES} is enabled. */
    public String nonSealedAnnotation;

    public void setSealedAnnotation(Class<?> cls) {
        sealedAnnotation = cls.getName();
    }

    public void setNonSealedAnnotation(Class<?> cls) {
        nonSealedAnnotation = cls.getName();
    }

    // ##################
    // # End of Options #
    // ##################
    public static class OutputOptions {
        public final FeatureSet features;
        public final Path outputDirectory;

        public OutputOptions(FeatureSet features, Path outputDirectory) {
            this.features = features;
            this.outputDirectory = outputDirectory;
        }
    }

    /** Controls which features will be added to the source code. */
    public enum JavaVersion implements FeatureSet {

        /** {@link JavaFeature#ENHANCED_DEPRECATION}. */
        JAVA_9(JavaFeature.ENHANCED_DEPRECATION),

        /** {@link JavaFeature#ENHANCED_DEPRECATION} and {@link JavaFeature#RECORDS}. */
        JAVA_16(JavaFeature.RECORDS),

        /** {@link JavaFeature#ENHANCED_DEPRECATION}, {@link JavaFeature#RECORDS}, and
         * {@link JavaFeature#SEALED_CLASSES}. */
        JAVA_17(JavaFeature.SEALED_CLASSES),;

        private final JavaFeature lastFeature;

        private JavaVersion(JavaFeature lastFeature) {
            this.lastFeature = lastFeature;
        }

        @Override
        public boolean generate(JavaFeature feature) {
            return feature.ordinal() <= lastFeature.ordinal();
        }

    }

    /** Most of the time you should use {@link JavaVersion} */
    public interface FeatureSet {
        boolean generate(JavaFeature feature);
    }

    /**
     * 
     */
    public enum JavaFeature {

        // Java 9 features
        /**
         * 
         */
        ENHANCED_DEPRECATION,
        // No Java 10 API Language features
        // No Java 11 API Language features
        // No java 12 API Language features
        // No Java 13 API Language features
        // No Java 14 API Language features
        // No Java 15 API Language features
        // Java 16 features
        RECORDS,
        // Java 17 features
        /** If enabled then {@link MultiJavaGen#sealedAnnotation} will be used to generate 'sealed' and 'permits'
         * modifiers on classes and interfaces, and {@link MultiJavaGen#nonSealedAnnotation} will be used to generated
         * the 'non-sealed' modifier. */
        SEALED_CLASSES,
        // No Java 18 API Language features
    }

    public void generate(Path input, Path[] classpath, OutputOptions[] output) throws IOException {
        for (OutputOptions out : output) {
            gen(input, classpath, out);
        }
    }

    private void gen(Path input, Path[] classpath, OutputOptions out) throws IOException {
        // Generate individually
        Files.walkFileTree(input, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                if (!file.getFileName().toString().endsWith(".java") || Files.isHidden(file)) {
                    return FileVisitResult.CONTINUE;
                }

                Path outFile = out.outputDirectory.resolve(input.relativize(file));
                Files.createDirectories(outFile.getParent());

                // Read the output file to see if we can auto generate it
                Files.deleteIfExists(outFile);

                ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                parser.setSource(source.toCharArray());
                Map<String, String> options = new HashMap<>(JavaCore.getOptions());
                options.put(JavaCore.COMPILER_SOURCE, "1.8");
                options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "1.8");
                options.put(JavaCore.COMPILER_COMPLIANCE, "1.8");
                parser.setCompilerOptions(options);
                String[] sources = new String[classpath.length];
                for (int i = 0; i < classpath.length; i++) {
                    sources[i] = classpath[i].toAbsolutePath().toString();
                }
                parser.setEnvironment(new String[0], sources, null, false);
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                parser.setResolveBindings(true);
                for (Path entry : classpath) {
                    if (file.toAbsolutePath().startsWith(entry.toAbsolutePath())) {
                        parser.setUnitName(entry.toAbsolutePath().relativize(file.toAbsolutePath()).toString());
                    }
                }

                // TODO: Change this to use parser.createASTs, as that should be more efficient!

                CompilationUnit comp = (CompilationUnit) parser.createAST(null);
                AST ast = comp.getAST();
                comp.recordModifications();

                boolean[] anyChanges = { false };

                comp.accept(new SealedProcessor(ast, anyChanges));

                for (Iterator<Object> iterator = comp.imports().iterator(); iterator.hasNext();) {
                    ImportDeclaration in = (ImportDeclaration) iterator.next();
                    Name name = in.getName();
                    if (sealedAnnotation.equals(name.toString())) {
                        iterator.remove();
                        anyChanges[0] = true;
                    }
                }

                if (!anyChanges[0]) {
                    return FileVisitResult.CONTINUE;
                }

                Document document = new Document(source);
                TextEdit edit = comp.rewrite(document, options);
                edit.toString();
                try {
                    edit.apply(document);
                } catch (MalformedTreeException | BadLocationException e) {
                    throw new Error(e);
                }
                Files.write(outFile, document.get().getBytes(StandardCharsets.UTF_8));

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private final class SealedProcessor extends ASTVisitor {
        private final AST ast;
        private final boolean[] anyChanges;

        private SealedProcessor(AST ast, boolean[] anyChanges) {
            this.ast = ast;
            this.anyChanges = anyChanges;
        }

        @Override
        public boolean visit(TypeDeclaration declr) {

            for (Object modifier : declr.modifiers().toArray()) {
                if (modifier instanceof Annotation) {
                    Annotation annot = (Annotation) modifier;
                    Name typeName = annot.getTypeName();
                    IBinding binding = typeName.resolveBinding();
                    if (binding instanceof ITypeBinding) {
                        String binaryName = ((ITypeBinding) binding).getBinaryName();
                        if (sealedAnnotation.equals(binaryName)) {
                            annot.delete();
                            declr.modifiers().add(ast.newModifier(ModifierKeyword.SEALED_KEYWORD));
                            anyChanges[0] = true;

                            List<TypeLiteral> subtypes = new ArrayList<>();

                            if (annot instanceof SingleMemberAnnotation) {
                                SingleMemberAnnotation annotValued = (SingleMemberAnnotation) annot;
                                Expression value = annotValued.getValue();
                                if (value instanceof ArrayInitializer) {
                                    for (Object entry : ((ArrayInitializer) value).expressions()) {
                                        if (entry instanceof TypeLiteral) {
                                            subtypes.add((TypeLiteral) entry);
                                        } else {
                                            throw new Error(
                                                "Unknown value in annotation expression: " + value.getClass()
                                            );
                                        }
                                    }
                                } else if (value instanceof TypeLiteral) {
                                    subtypes.add((TypeLiteral) value);
                                } else {
                                    throw new Error("Unknown value in annotation expression: " + value.getClass());
                                }
                            }

                            for (TypeLiteral typeLit : subtypes) {
                                Type type = typeLit.getType();
                                type = (Type) ASTNode.copySubtree(ast, type);
                                declr.permittedTypes().add(type);
                            }

                        } else if (nonSealedAnnotation.equals(binaryName)) {
                            annot.delete();
                            declr.modifiers().add(ast.newModifier(ModifierKeyword.NON_SEALED_KEYWORD));
                            anyChanges[0] = true;
                        }
                    }
                }
            }

            return super.visit(declr);
        }
    }

}
