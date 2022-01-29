package edu.utdallas.seers.lasso.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import edu.utdallas.seers.file.JavaFileWalker;
import edu.utdallas.seers.lasso.ast.matcher.PatternInstance;
import edu.utdallas.seers.lasso.ast.matcher.PatternMatcher;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.data.entity.PatternType;
import edu.utdallas.seers.stream.PairSeq;
import org.jooq.lambda.Unchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ASTPatternDetector {

    private static final Set<String> SOURCE_ROOT_NAMES = new HashSet<>(Arrays.asList(
            "src", "main", "java", "java-deprecated", "sources", "toolsrc"
    ));

    final JavaParser parser;
    final ASTPatternDetector.AggregatedPatternMatcher matcher = new ASTPatternDetector.AggregatedPatternMatcher();
    private final Logger logger = LoggerFactory.getLogger(ASTPatternDetector.class);
    private final Path projectPath;
    private final Set<Path> excludedPaths;
    private int counter = 0;

    private ASTPatternDetector(Path projectPath, JavaParser parser, Set<Path> excludedPaths) {
        this.projectPath = projectPath;
        this.parser = parser;
        this.excludedPaths = excludedPaths;
    }

    public static PairSeq<Path, Stream<PatternInstance>> detect(Path sourcesDir, String projectName, Predicate<Path> pathFilter) {
        var projectDir = sourcesDir.resolve(projectName).resolve("sources");
        var excludedPaths = ConstraintLoader.loadExclusions(projectDir);
        Stream<Path> directories;
        try {
            directories = Files.find(projectDir, Integer.MAX_VALUE, (p, a) -> Files.isDirectory(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<TypeSolver> javaParserSolvers;
        if (projectDir.toString().contains("jedit")) {
            // jEdit puts sources at the top level
            // TODO standardize process of finding source folders for all projects
            javaParserSolvers = Collections.singletonList(
                    new JavaParserTypeSolver(projectDir));
        } else {
        /* TODO there might be a smarter way to do this, e.g. ArgoUML/sources/src/argouml-app/src
            both src are in selected but only the lower level one is valid */
            // Find all source dirs and create solvers for type resolution within the project
            javaParserSolvers = directories.filter(p -> SOURCE_ROOT_NAMES.contains(p.getFileName().toString()) &&
                    excludedPaths.stream().noneMatch(p::endsWith))
                    .map(JavaParserTypeSolver::new)
                    .collect(Collectors.toList());
        }

        if (javaParserSolvers.isEmpty()) {
            throw new RuntimeException("No source roots found for project " + projectDir);
        }

        List<TypeSolver> typeSolvers = new ArrayList<>(javaParserSolvers);
        // Add JRE solver
        typeSolvers.add(0, new ReflectionTypeSolver(true));

        // TODO missing type resolution from libraries. Could replace src resolution with the project jar + dependency jars
        CombinedTypeSolver solver = new CombinedTypeSolver(typeSolvers.toArray(new TypeSolver[0]));
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(solver))
        );

        ASTPatternDetector astPatternDetector = new ASTPatternDetector(projectDir, parser, excludedPaths);
        var walk =
                JavaFileWalker.walk(astPatternDetector.projectPath, astPatternDetector.excludedPaths)
                        .filter(pathFilter);

        return PairSeq.seq(
                walk,
                f -> f,
                Unchecked.function(astPatternDetector::extractPatterns)
        );
    }

    private ClassLocation extractClassLocation(CompilationUnit compilationUnit) {
        var storage = compilationUnit.getStorage().orElseThrow();
        String fileName = storage.getFileName();

        var packagePath = compilationUnit.getPackageDeclaration()
                .map(d -> d.getName().toString().replace('.', '/') + "/" + fileName)
                .orElse(fileName);

        return new ClassLocation(storage.getPath(), packagePath);
    }

    public Stream<PatternInstance> extractPatterns(Path file) throws IOException {
        ParseResult<CompilationUnit> result = parser.parse(file);

        if (result.getResult().isEmpty()) {
            logger.warn("Invalid Java file: {}", file);
            return Stream.empty();
        }

        CompilationUnit compilationUnit = result.getResult().get();
        if (++counter % 200 == 0) {
            logger.info("Processed {} Java files...", counter);
        }

        ClassLocation location = extractClassLocation(compilationUnit);
        if (location.packagePath.endsWith("log4j/core/tools/picocli/CommandLine.java") ||
                location.packagePath.endsWith("org/apache/ibatis/io/JBoss6VFS.java")) {
            // FIXME find why it causes stackoverflow
            return Stream.empty();
        }

        return matcher.match(compilationUnit, location);
    }

    /**
     * Aggregates all available pattern matchers for efficient matching on the AST.
     */
    private static class AggregatedPatternMatcher {

        /**
         * We could compile a list of valid node types by querying each detector and this would
         * allow for discarding certain nodes quickly (e.g. CompilationUnit), however, since each
         * candidate node would have to be compared with Class.instanceOf, this would probably not
         * significantly increase efficiency. Instead, each detector should be able to quickly
         * discard invalid nodes.
         */
        private final Map<PatternType, PatternMatcher> matchers = Arrays.stream(PatternType.values())
                .collect(Collectors.toMap(
                        t -> t,
                        PatternType::getMatcher
                ));

        public Stream<PatternInstance> match(CompilationUnit unit, ClassLocation fileName) {
            // TODO must make sure only one pattern matches each node
            return unit.stream()
                    .flatMap(n -> matchers.values().stream()
                            .flatMap(m -> n.accept(m, fileName).stream())
                    );
        }
    }
}
