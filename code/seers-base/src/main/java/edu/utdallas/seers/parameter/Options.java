package edu.utdallas.seers.parameter;

import edu.utdallas.seers.file.Files;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Common program options stored statically for convenience.
 */
public class Options {
    private static final Options options = new Options();

    private boolean ignoreCache = false;
    private Path cachePath = Files.getTempFilePath("edu.utdallas.seers.cache");
    private List<Integer> hitsAtKRanks = Arrays.asList(1, 5, 10, 15, 20);

    private Options() {
    }

    public static Options getInstance() {
        return options;
    }

    public boolean isIgnoreCache() {
        return ignoreCache;
    }

    private void setIgnoreCache(boolean ignoreCache) {
        this.ignoreCache = ignoreCache;
    }

    public List<Integer> getHitsAtKRanks() {
        return hitsAtKRanks;
    }

    private void setHitsAtKRanks(List<Integer> hitsAtKRanks) {
        this.hitsAtKRanks = hitsAtKRanks;
    }

    public Path getCachePath() {
        return cachePath;
    }

    private void setCachePath(Path cachePath) {
        this.cachePath = cachePath;
    }

    /**
     * Provides an interface to modify the Options object through command line parameters parsed
     * by Argparse4j.
     * Use the builder pattern to add common options that will be added to the parser and then
     * use it normally.
     */
    public static class ArgumentBuilder {
        private final ArgumentParser parser;

        public ArgumentBuilder(String name) {
            parser = ArgumentParsers.newFor(name)
                    .build()
                    .defaultHelp(true);
        }

        public ArgumentBuilder addIgnoreCacheOption() {
            return addAction(
                    new StoreTrueSilent(options::setIgnoreCache),
                    "Ignores and rebuilds any existing caches",
                    "-C", "--ignore-cache"
            );
        }

        public ArgumentBuilder addCachePathOption() {
            return addAction(
                    new StorePath(options::setCachePath),
                    "The path where the cache should be stored",
                    "-c", "--cache-path"
            );
        }

        public ArgumentBuilder addHitsAtKRanksOption() {
            var storeAction = new StoreMultipleSilent<>(options::setHitsAtKRanks, Integer.class);

            return addAction(
                    "*",
                    new StoreValidatingMultiple<Integer>(
                            storeAction,
                            i -> i > 0,
                            "Hits at k ranks must be > 0"
                    ),
                    "Ranks for the %HITS@N metric that will be used for the evaluation",
                    "-k", "--hits-at-k-ranks"
            );
        }

        public ArgumentParser build() {
            return parser;
        }

        private ArgumentBuilder addAction(String nargs, ArgumentAction action, String help, String... flags) {
            parser.addArgument(flags)
                    .action(action)
                    .nargs(nargs)
                    .help(help);

            return this;
        }

        private ArgumentBuilder addAction(ArgumentAction action, String help, String... flags) {
            parser.addArgument(flags)
                    .action(action)
                    .help(help);

            return this;
        }
    }

}
