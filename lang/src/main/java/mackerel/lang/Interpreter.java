package mackerel.lang;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import mackerel.lang.Ast.Binding;
import mackerel.lang.Ast.Builder;
import mackerel.lang.Ast.Grouping;
import mackerel.lang.Ast.Sequence;
import mackerel.lang.Ast.Variable;

final class Interpreter {

    public record Message(String message, Token token) {}

    @Getter
    private final List<Message> errors = new ArrayList<>();

    @Getter
    private final List<Message> warnings = new ArrayList<>();

    private final Map<String, Object> declarations = new LinkedHashMap<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public void interpret(Ast parsed) {
        try {
            if (parsed instanceof Ast.Source source) {
                interpretSource(source);
            } else if (parsed instanceof Ast.Repl repl) {
                interpretRepl(repl);
            } else {
                var astClazz = parsed != null ? parsed.getClass() : null;
                throw new IllegalArgumentException("unsupported syntax tree: " + astClazz);
            }
        } catch (InterpreterError ex) {
            errors.add(ex.asError());
        }
    }

    private void interpretSource(Ast.Source source) {
        for (var stmt : source.statements()) {
            interpretTopStmt(stmt);
        }
    }

    private void interpretRepl(Ast.Repl repl) {
        for (var element : repl.nodes()) {
            if (element instanceof Ast.Stmt stmt) {
                interpretTopStmt(stmt);
            } else {
                print(evaluate(element));
            }
        }
    }

    private void interpretTopStmt(Ast.Stmt stmt) {
        switch (stmt.type().lexeme()) {
            case "decl":
                interpretDecl(stmt);
                break;
            default:
                throw new InterpreterError(stmt.type(), "unexpected statement");
        }
    }

    private void interpretDecl(Ast.Stmt decl) {
        if (decl.value() instanceof Ast.Binding binding
                && binding.left() instanceof Ast.Variable key) {
            var name = key.name().lexeme();
            var definition = evaluate(binding.right());
            declarations.put(name, definition);
        } else {
            throw new InterpreterError(decl.type(), "Expect named binding.");
        }
    }

    private Object evaluate(Ast node) {
        if (node instanceof Ast.Binding binding) {
            return evaluateBindingExpr(binding);
        }

        if (node instanceof Ast.Builder builder) {
            return evaluateBuilderExpr(builder);
        }

        if (node instanceof Ast.Grouping grouping) {
            return evaluateGroupingExpr(grouping);
        }

        if (node instanceof Ast.Literal literal) {
            return literal.value();
        }

        if (node instanceof Ast.Sequence seq) {
            return evaluateSequenceExpr(seq);
        }

        if (node instanceof Ast.Variable variable) {
            return evaluateVariableExpr(variable);
        }

        return "TODO: unsupported syntax:\n  " + node;
    }

    private List<Token> tokensOf(Ast expr) {
        var tokens = new ArrayList<Token>();

        if (expr instanceof Ast.Binding binding) {
            tokens.addAll(tokensOf(binding.left()));
            tokens.add(binding.operator());
            tokens.addAll(tokensOf(binding.right()));
            return tokens;
        }

        if (expr instanceof Ast.Builder builder) {
            tokens.add(builder.type());
            for (var stmt : builder.statements()) {
                tokens.addAll(tokensOf(stmt));
            }
            return tokens;
        }

        if (expr instanceof Ast.Grouping grouping) {
            tokens.addAll(tokensOf(grouping.expression()));
            return tokens;
        }

        if (expr instanceof Ast.Literal literal) {
            tokens.add(literal.token());
            return tokens;
        }

        if (expr instanceof Ast.Sequence seq) {
            return tokens;
        }

        if (expr instanceof Ast.Variable variable) {
            return tokens;
        }

        throw new IllegalArgumentException("unsupported expression: " + expr);
    }

    private Object evaluateBindingExpr(Binding binding) {
        return Map.entry(evaluate(binding.left()), evaluate(binding.right()));
    }

    private Object evaluateBuilderExpr(Builder builder) {
        // TODO
        return builder.type().lexeme();
    }

    private Object evaluateGroupingExpr(Grouping grouping) {
        return evaluate(grouping.expression());
    }

    private Object evaluateSequenceExpr(Sequence sequence) {
        var elements = sequence.elements();
        var result = new ArrayList<Object>();
        if (elements.isEmpty()) {
            return result;
        }

        if (elements.get(0) instanceof Ast.Binding) {
            return evaluateTableExpr(sequence);
        }

        for (var element : elements) {
            if (element instanceof Ast.Binding entry) {
                throw new InterpreterError(entry.operator(), "expect sequence element");
            }
            result.add(evaluate(element));
        }
        return result;
    }

    private Object evaluateTableExpr(Sequence sequence) {
        var result = new LinkedHashMap<Object, Object>();
        for (var elem : sequence.elements()) {
            if (elem instanceof Ast.Binding pair) {
                var key = (pair.left() instanceof Variable vt)
                    ? vt.name().lexeme()
                    : evaluate(pair.left());
                var value = evaluate(pair.right());
                if (result.containsKey(key)) {
                    errors.add(new Message("Duplicate key in table: " + key, pair.operator()));
                }
                result.put(key, value);
            } else {
                var tokens = tokensOf(sequence);
                throw new InterpreterError(tokens.get(0), "expect table entry");
            }
        }
        return result;
    }

    private Object evaluateVariableExpr(Variable variable) {
        var key = variable.name().lexeme();
        if (declarations.containsKey(key)) {
            return new Ref(key);
        }
        throw new InterpreterError(variable.name(), "Cannot find variable: " + key);
    }

    // **** UTILITIES ****

    private void checkNumberOperands(Token operator, Object... operands) {
        for (var operand : operands) {
            if (!(operand instanceof BigInteger || operand instanceof BigDecimal)) {
                var operandClass = operand != null ? operand.getClass() : null;
                throw new InterpreterError(operator, "Operand must be a number, got " + operandClass);
            }
        }
    }

    private boolean hasDecimalOperand(Token operator, Object... operands) {
        checkNumberOperands(operator, operands);
        for (var operand : operands) {
            if (operand instanceof BigDecimal) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal asDecimal(Object number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }

        if (number instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }

        throw new IllegalArgumentException("Cannot coerce to decimal: " + number);
    }

    private BigInteger asInteger(Object number) {
        if (number instanceof BigDecimal decimal) {
            return decimal.toBigInteger();
        }

        if (number instanceof BigInteger integer) {
            return integer;
        }

        throw new IllegalArgumentException("Cannot coerce to integer: " + number);
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;
        return a.equals(b);
    }

    private String stringify(Object obj) {
        return obj.toString();
    }

    private void print(Object value) {
        if (value instanceof Ref ref) {
            value = ref.get();
        }
        System.out.println(value);
    }

    static class InterpreterError extends RuntimeException {
        private final Token token;

        InterpreterError(Token token, String message) {
            super(message);
            this.token = token;
        }

        public Message asError() {
            return new Message(getMessage(), token);
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    class Ref {

        private final String identifier;

        public Object get() {
            return declarations.get(identifier);
        }

        @Override
        public String toString() {
            return identifier;
        }
    }
}
