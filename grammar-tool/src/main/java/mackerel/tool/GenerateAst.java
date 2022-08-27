package mackerel.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        var outputDir = args[0];

        // clean the output dir
        Files.walk(Path.of(outputDir))
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);

        var exprAst = parseAstDef(
            "Association: List>Binding bindings",
            "Binary     : Expr left, Token operator, Expr right",
            "Binding    : Token name, Expr value",
            "Collection : List>Expr elements",
            "Get        : Expr object, Token name",
            "Grouping   : Expr expression",
            "Lambda     : Token param, Token arrow, Expr body",
            "Literal    : Object value",
            "Logical    : Expr left, Token operator, Expr right",
            "Symbol     : Token name",
            "Unary      : Token operator, Expr right",
            "Variable   : Token name"
        );
        writeAstDef(outputDir, "mackerel.lang.Expr", exprAst);

        var declAst = parseAstDef(
            "Definition : Token name, Expr definition"
        );
        writeAstDef(outputDir, "mackerel.lang.Decl", declAst);
    }

    private static record NodeType(String name, NodeField[] fields) {};
    private static record NodeField(String name, String type) {};

    private static NodeType[] parseAstDef(String... lines) {
        var entries = Stream.of(lines).map(line -> {
            var sTokens = line.split(":", 2);
            var cName = sTokens[0].trim();
            var fields = sTokens[1].trim().split(",");
            return new NodeType(cName, parseType(fields));
        });
        return entries.toArray(NodeType[]::new);
    }

    private static NodeField[] parseType(String... fields) {
        var entries = Stream.of(fields).map(field -> {
            var toks = field.trim().split(" ");
            var name = toks[1].trim();
            var type = toks[0].trim();
            var genericType = type.split(">");
            if (genericType.length > 1) {
                var params = Arrays.stream(genericType)
                        .skip(1)
                        .collect(Collectors.joining(", "));
                type = genericType[0] + "<" + params + ">";
            }
            return new NodeField(name, type);
        });
        return entries.toArray(NodeField[]::new);
    }

    private static List<String> splitFQName(String fqName) {
        return Arrays.asList(fqName.split("[.]"));
    }

    private static void writeAstDef(
            String outputDir,
            String fqNameStr,
            NodeType[] astDef) throws IOException {

        var fqName = splitFQName(fqNameStr);
        var baseName = fqName.get(fqName.size() - 1);
        var pkgTokens = fqName.subList(0, fqName.size() - 1);
        var pkgDir = new File(outputDir, String.join("/", pkgTokens));
        var file = new File(pkgDir, baseName + ".java");
        file.getParentFile().mkdirs();
        try (var writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.println("package " + String.join(".", pkgTokens) + ";");
            writer.println();
            writeJava(writer, baseName, astDef);
        }
    }

    private static void writeJava(
            PrintWriter writer,
            String baseName,
            NodeType[] astDef) {

        writer.println("import java.util.*;");
        writer.println();
        writer.println("sealed interface " + baseName + " {");

        // base accept() method
        writer.println();
        writer.println("  <R> R accept(Visitor<R> visitor);");

        // AST impls
        for (var node : astDef) {
            writer.println();
            var className = node.name.trim();
            writeTypeRecord(writer, baseName, className, node.fields);
        }

        // Visitor interface
        writer.println();
        writer.println("  interface Visitor<R> {");
        for (var type : astDef) {
            var typeName = type.name.trim();
            writer.println("    R visit" + typeName + baseName + "("
                    + typeName + " " + baseName.toLowerCase() + ");");
        }
        writer.println("  }");

        writer.println("}");
    }

    private static void writeTypeRecord(PrintWriter writer, String baseName, String className, NodeField[] fields) {

        var fieldList = Arrays.stream(fields)
                .map(f -> f.type + ' ' + f.name)
                .collect(Collectors.joining(", "));
        writer.println("  record " + className  + "(" + fieldList + ") implements " + baseName + " {");

        // visitor pattern
        writer.println();
        writer.println("    public <R> R accept(Visitor<R> visitor) {");
        writer.println("      return visitor.visit" + className + baseName + "(this);");
        writer.println("    }");

        writer.println("  }");
    }
}
