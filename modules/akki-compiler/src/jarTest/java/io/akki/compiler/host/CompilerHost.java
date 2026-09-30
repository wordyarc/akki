package io.akki.compiler.host;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompilerHost {
    private CompilerHost() {
    }

    public static void main(String[] compilations) throws Exception {
        Class<?> version = Class.forName("org.jetbrains.kotlin.config.KotlinCompilerVersion");
        System.out.println("host " + version.getMethod("getVersion").invoke(null));
        Class<?> compiler = Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler");
        Method exec = compiler.getMethod("exec", PrintStream.class, String[].class);
        for (String compilation : compilations) {
            Path directory = Path.of(compilation);
            String[] arguments = Files.readAllLines(directory.resolve("arguments")).toArray(String[]::new);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Object exitCode;
            try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
                exitCode = exec.invoke(compiler.getConstructor().newInstance(), stream, arguments);
            }
            Files.write(directory.resolve("output"), output.toByteArray());
            Files.writeString(directory.resolve("exit"), exitCode.toString());
        }
    }
}
