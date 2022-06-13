package mackerel.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        var outputDir = args[0];
        var exprAst = parseAstDef(
            "Assign     : Token name, Expr value",
            "Binary     : Expr left, Token operator, Expr right",
            "Call       : Expr callee, Token paren, List<Expr> arguments",
            "Get        : Expr object, Token name",
            "Grouping   : Expr expression",
            "Lambda     : List<Token> params, List<Stmt> body",
            "Literal    : Object value",
            "Logical    : Expr left, Token operator, Expr right",
            "Set        : Expr object, Token name, Expr value",
            "This       : Token keyword",
            "Ternary    : Expr left, Token leftOp, Expr middle, Token rightOp, Expr right",
            "Unary      : Token operator, Expr right",
            "Variable   : Token name"
        );
        writeAstDef(outputDir, "mackerel.lang.Expr", exprAst);
        var stmtAst = parseAstDef(
            "Block      : List<Stmt> statements",
            "Class      : Token name, List<Token> params, Expr.Call superclass, List<Stmt> init, List<Stmt.Function> methods",
            "Expression : Expr expression",
            "Function   : Token name, List<Token> params, List<Stmt> body",
            "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
            "Print      : Expr expression",
            "Return     : Token keyword, Expr value",
            "Var        : Token name, Expr initializer",
            "While      : Expr condition, Stmt body"
        );
        writeAstDef(outputDir, "mackerel.lang.Stmt", stmtAst);
    }

    private static Map<String, Map<String, String>> parseAstDef(String... lines) {
        var entries = Stream.of(lines).map(line -> {
            var sTokens = line.split(":", 2);
            var cName = sTokens[0].trim();
            var fields = sTokens[1].trim().split(",");
            return Map.entry(cName, parseType(fields));
        });
        return tbl(entries::iterator);
    }

    private static Map<String, String> parseType(String... fields) {
        var entries = Stream.of(fields).map(field -> {
            var toks = field.trim().split(" ");
            var name = toks[1].trim();
            var type = toks[0].trim();
            return Map.entry(name, type);
        });
        return tbl(entries::iterator);
    }

    private static <K, V> Map<K, V> tbl(Iterable<Map.Entry<K, V>> entries) {
        var map = new LinkedHashMap<K, V>();
        for (Map.Entry<K,V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private static List<String> splitFQName(String fqName) {
        return Arrays.asList(fqName.split("[.]"));
    }

    private static void writeAstDef(
            String outputDir,
            String fqNameStr,
            Map<String, Map<String, String>> astDef) throws IOException {

        var fqName = splitFQName(fqNameStr);
        var baseName = fqName.get(fqName.size() - 1);
        var pkgTokens = fqName.subList(0, fqName.size() - 1);
        var pkgDir = new File(outputDir, String.join("/", pkgTokens));
        var file = new File(pkgDir, baseName + ".java");
        file.getParentFile().mkdirs();
        try (var writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.println("package " + String.join(".", pkgTokens) + ";");
            writer.println();
            writer.println("import java.util.List;");
            writer.println();
            writer.println("interface " + baseName + " {");

            defineVisitor(writer, baseName, astDef);

            // AST classes
            for (var type : astDef.entrySet()) {
                writer.println();
                var className = type.getKey().trim();
                var fields = type.getValue();
                defineType(writer, baseName, className, fields);
            }

            // base accept() method
            writer.println();
            writer.println("  <R> R accept(Visitor<R> visitor);");

            writer.println("}");
        }
    }

    private static void defineVisitor(PrintWriter writer, String baseName, Map<String, Map<String, String>> types) {
        writer.println("  interface Visitor<R> {");

        for (var type : types.entrySet()) {
            var typeName = type.getKey().trim();
            writer.println("    R visit" + typeName + baseName + "("
                    + typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("  }");
    }

    private static void defineType(PrintWriter writer, String baseName, String className, Map<String, String> fields) {
        writer.println("  static class " + className + " implements " + baseName + " {");

        // constructor
        var fieldList = fields.entrySet()
            .stream()
            .map(e -> e.getValue() + ' ' + e.getKey())
            .collect(Collectors.joining(", "));
        writer.println("    " + className + "(" + fieldList + ") {");

        // store params in fields
        for (var field : fields.entrySet()) {
            var id = field.getKey();
            writer.println("      this." + id + " = " + id + ";");
        }

        writer.println("    }");

        // visitor pattern
        writer.println();
        writer.println("    public <R> R accept(Visitor<R> visitor) {");
        writer.println("      return visitor.visit" + className + baseName + "(this);");
        writer.println("    }");

        // fields
        writer.println();
        for (var field : fields.entrySet()) {
            var type = field.getValue();
            var id = field.getKey();
            var capitalizedId = id.substring(0, 1).toUpperCase() + id.substring(1);
            writer.println("    private final " + type + " " +id + ";");
            writer.println("    public " + type + " get" + capitalizedId + "() { return " + id + "; }");
        }

        // toString
        writer.println();
        writer.println("    public String toString() {");
        writer.println("      return \"" + baseName + "." + className + "(\"");
        var fieldsString = fields.keySet()
            .stream()
            .map(id -> "        + \"" + id + "=\" + " + id)
            .collect(Collectors.joining(" + \", \"\n"));
        writer.println(fieldsString + " + \")\";");
        writer.println("    }");

        writer.println("  }");
    }
}
