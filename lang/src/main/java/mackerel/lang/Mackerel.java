package mackerel.lang;

import static lombok.AccessLevel.PRIVATE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = PRIVATE)
public class Mackerel {
    public static void main(String[] args) throws IOException {
        int exitCode;
        if (args.length > 1) {
            System.out.println("Usage: mackerel [script]");
            exitCode = 64;
        } else if (args.length == 1) {
            exitCode = runFile(args[0]);
        } else {
            exitCode = runPrompt();
        }
        System.exit(exitCode);
    }

    // private static final Parser parser = new Parser();
    // private static final Interpreter interpreter = new Interpreter();

    private static int runFile(String path) throws IOException {
        byte[] bytes;
        if ("-".equals(path)) {
            bytes = System.in.readAllBytes();
        } else {
            bytes = Files.readAllBytes(Paths.get(path));
        }

        return run(new String(bytes, Charset.defaultCharset()));
    }

    private static int runPrompt() throws IOException {
        var reader = new BufferedReader(new InputStreamReader(System.in));

        var lineBuffer = new ArrayList<String>();
        for (;;) {
            var prompt = String.format(":%02d> ", lineBuffer.size());
            System.out.print(prompt);
            var line = reader.readLine();

            // TODO flags
            if (line == null || ":q".equals(line)) {
                break;
            } else if (!line.isEmpty()) {
                lineBuffer.add(line);

                // TODO unmatched braces

                var source = String.join("\n", lineBuffer);
                run(source);
                lineBuffer.clear();
            }
        }
        return 0;
    }

    private static int run(String source) {
        var scanner = new Scanner(source);
        var tokens = scanner.scanTokens();

        var parser = new Parser(tokens);
        var parsed = parser.parse();

        parsed.forEach(System.out::println);

        if (scanner.hasErrors()) {
            scanner.getErrors().forEach(Mackerel::report);
            return 1;
        }

        if (parser.hasErrors()) {
            parser.getErrors().forEach(Mackerel::report);
        }

        // if (interpreter.hadError()) {
        //     return 70;
        // }

        return 0;
    }

    private static void report(Scanner.Error error) {
        System.err.println("scanner: " + error.message() + " [line " + error.line() + "]");
    }

    private static void report(Parser.Error error) {
        System.err.println("parser: " + error);
    }
}
