package mackerel.lang;

import static lombok.AccessLevel.PRIVATE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    private static final Interpreter interpreter = new Interpreter();

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

        var unmatchedBraces = 0;
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
                unmatchedBraces += countUnmatchedBraces(line);

                // if braces are at least balanced, flush the buffer
                if (unmatchedBraces <= 0) {
                    var source = String.join("\n", lineBuffer);
                    run(source);
                    lineBuffer.clear();
                    unmatchedBraces = 0;
                }
            }
        }
        return 0;
    }

    private static int run(String source) {
        var scanner = new Scanner(source);
        var tokens = scanner.scanTokens();

        scanner.getWarnings().forEach(Mackerel::report);

        if (scanner.hasErrors()) {
            scanner.getErrors().forEach(Mackerel::report);
            return 1;
        }

        var parser = new Parser(tokens);
        var parsed = parser.parse();

        parser.getWarnings().forEach(Mackerel::report);

        if (parser.hasErrors()) {
            parser.getErrors().forEach(Mackerel::report);
            return 1;
        }

        interpreter.interpret(parsed);

        interpreter.getWarnings().forEach(Mackerel::report);
        interpreter.getWarnings().clear();

        if (interpreter.hasErrors()) {
            interpreter.getErrors().forEach(Mackerel::report);
            interpreter.getErrors().clear();
            return 1;
        }

        return 0;
    }

    private static void report(Scanner.Message error) {
        System.err.println("scanner: " + error.message() + " [line " + error.line() + "]");
    }

    private static void report(Parser.Message error) {
        System.err.println("parser: " + error.message() + " [line " + error.token().line() + "]");
    }

    private static void report(Interpreter.Message error) {
        System.err.println("interpreter: " + error.message() + " [line " + error.token().line() + "]");
    }

    private static final List<Token.Type> OPEN = List.of(
        Token.Type.BRACE_LEFT,
        Token.Type.BRACKET_LEFT,
        Token.Type.PAREN_LEFT
    );
    private static final List<Token.Type> CLOSE = List.of(
        Token.Type.BRACE_RIGHT,
        Token.Type.BRACKET_RIGHT,
        Token.Type.PAREN_RIGHT
    );

    private static int countUnmatchedBraces(String line) {
        // scan as tokens
        var scanner = new Scanner(line);
        var tokens = scanner.scanTokens();

        // count number of unmatched braces
        int count = 0;
        for (var token : tokens) {
            var type = token.type();
            if (OPEN.contains(type)) {
                count++;
            }
            if (CLOSE.contains(type)) {
                count--;
            }
        }
        return count;
    }
}
