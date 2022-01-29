/*
 * Copyright (c) 2011 - Georgios Gousios <gousiosg@gmail.com>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package gr.gousiosg.javacg.stat;

import org.apache.bcel.classfile.ClassParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Constructs a callgraph out of a JAR archive. Can combine multiple archives
 * into a single call graph.
 *
 * @author Georgios Gousios <gousiosg@gmail.com>
 */
public class JCallGraph {

    public static void main(String[] args) {
        try (var writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)))) {
            new JCallGraph().constructCallGraph(
                    Arrays.stream(args).map(Path::of).collect(Collectors.toList())
            )
                    .forEachOrdered(writer::println);
        } catch (IOException e) {
            System.err.println("Error while processing jar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Stream<String> constructCallGraph(List<Path> paths) throws IOException {
        return paths.stream()
                .flatMap(this::processJar);
    }

    private Stream<String> processJar(Path jarPath) {
        if (!Files.exists(jarPath)) {
            System.err.println("Jar file " + jarPath + " does not exist");
        }

        JarFile jar;
        try {
            jar = new JarFile(jarPath.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return jar.stream()
                .filter(e -> !e.isDirectory() && e.getName().endsWith(".class"))
                .flatMap(e -> {
                    ClassParser cp = new ClassParser(jarPath.toString(), e.getName());
                    var started = makeClassVisitor(cp).start();
                    return Stream.concat(
                            started.methodCalls().stream(),
                            started.getClassCalls().stream()
                    );
                })
                .onClose(() -> {
                    try {
                        jar.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    private ClassVisitor makeClassVisitor(ClassParser classParser) {
        try {
            return new ClassVisitor(classParser.parse());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
