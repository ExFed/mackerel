package mackerel.lang;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import mackerel.lang.Expr.Binary;
import mackerel.lang.Expr.Binding;
import mackerel.lang.Expr.Builder;
import mackerel.lang.Expr.Grouping;
import mackerel.lang.Expr.Logical;
import mackerel.lang.Expr.Sequence;
import mackerel.lang.Expr.Unary;
import mackerel.lang.Expr.Variable;
import mackerel.lang.Stmt.Declaration;
import mackerel.lang.Token.Type;

final class Interpreter {

    public record Message(String message, Token token) {}

    @Getter
    private final List<Message> errors = new ArrayList<>();

    @Getter
    private final List<Message> warnings = new ArrayList<>();

    private final Map<String, Lazy<Object>> declarations = new LinkedHashMap<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (var statement : statements) {
                execute(statement);
            }
        } catch (InterpreterError ex) {
            errors.add(ex.asError());
        }
    }

    private void execute(Stmt statement) {
        if (statement instanceof Stmt.Expression expr) {
            System.out.println(evaluate(expr.expression()));
        } else if (statement instanceof Stmt.Declaration decl) {
            executeDeclarationStmt(decl);
        } else {
            throw new IllegalArgumentException("unsupported statement: " + statement);
        }
    }

    private void executeDeclarationStmt(Declaration decl) {
        if ("decl".equals(decl.type().lexeme())) {
            if (decl.definition() instanceof Expr.Binding binding && binding.left() instanceof Expr.Variable key) {
                var definition = binding.right();
                declarations.put(key.name().lexeme(), Lazy.lazy(() -> evaluate(definition)));
            } else {
                throw new InterpreterError(decl.type(), "Expect named binding.");
            }
        }
    }

    private Object evaluate(Expr expr) {
        if (expr instanceof Expr.Binary binary) {
            return evaluateBinaryExpr(binary);
        }

        if (expr instanceof Expr.Binding binding) {
            return evaluateBindingExpr(binding);
        }

        if (expr instanceof Expr.Builder builder) {
            return evaluateBuilderExpr(builder);
        }

        if (expr instanceof Expr.Grouping grouping) {
            return evaluateGroupingExpr(grouping);
        }

        if (expr instanceof Expr.Literal literal) {
            return literal.value();
        }

        if (expr instanceof Expr.Logical logical) {
            return evaluateLogicalExpr(logical);
        }

        if (expr instanceof Expr.Sequence seq) {
            return evaluateSequenceExpr(seq);
        }

        if (expr instanceof Expr.Unary unary) {
            return evaluateUnaryExpr(unary);
        }

        if (expr instanceof Expr.Variable variable) {
            return evaluateVariableExpr(variable);
        }

        return "TODO: unsupported expression:\n  " + expr;
    }

    private List<Token> tokensOf(Stmt stmt) {
        throw new IllegalArgumentException("unsupported statement: " + stmt);
    }

    private List<Token> tokensOf(Expr expr) {
        var tokens = new ArrayList<Token>();

        if (expr instanceof Expr.Binary binary) {
            tokens.addAll(tokensOf(binary.left()));
            tokens.add(binary.operator());
            tokens.addAll(tokensOf(binary.right()));
            return tokens;
        }

        if (expr instanceof Expr.Binding binding) {
            tokens.addAll(tokensOf(binding.left()));
            tokens.add(binding.operator());
            tokens.addAll(tokensOf(binding.right()));
            return tokens;
        }

        if (expr instanceof Expr.Builder builder) {
            tokens.add(builder.type());
            for (var stmt : builder.statements()) {
                tokens.addAll(tokensOf(stmt));
            }
            return tokens;
        }

        if (expr instanceof Expr.Grouping grouping) {
            tokens.addAll(tokensOf(grouping.expression()));
            return tokens;
        }

        if (expr instanceof Expr.Literal literal) {
            tokens.add(literal.token());
            return tokens;
        }

        if (expr instanceof Expr.Logical logical) {
            return tokens;
        }

        if (expr instanceof Expr.Sequence seq) {
            return tokens;
        }

        if (expr instanceof Expr.Unary unary) {
            return tokens;
        }

        if (expr instanceof Expr.Variable variable) {
            return tokens;
        }

        throw new IllegalArgumentException("unsupported expression: " + expr);
    }

    private Object evaluateBinaryExpr(Binary expr) {
        var op = expr.operator();
        var left = evaluate(expr.left());
        var right = evaluate(expr.right());

        switch (op.type()) {
            case GREATER:
                if (hasDecimalOperand(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) > 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) > 0;
                }
            case GREATER_EQUAL:
                if (hasDecimalOperand(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) >= 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) >= 0;
                }
            case LESS:
                if (hasDecimalOperand(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) < 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) < 0;
                }
            case LESS_EQUAL:
                if (hasDecimalOperand(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) <= 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) <= 0;
                }
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case MINUS:
                if (hasDecimalOperand(op, left, right)) {
                    return asDecimal(left).subtract(asDecimal(right));
                } else {
                    return asInteger(left).subtract(asInteger(right));
                }
            case SLASH:
                if (hasDecimalOperand(op, left, right)) {
                    return asDecimal(left).divide(asDecimal(right));
                } else {
                    return asInteger(left).divide(asInteger(right));
                }
            case STAR:
                if (hasDecimalOperand(op, left, right)) {
                    return asDecimal(left).multiply(asDecimal(right));
                } else {
                    return asInteger(left).multiply(asInteger(right));
                }
            case PLUS:
                if (left instanceof Number && right instanceof Number) {
                    if (hasDecimalOperand(op, left, right)) {
                        return asDecimal(left).add(asDecimal(right));
                    } else {
                        return asInteger(left).add(asInteger(right));
                    }
                }

                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }

                throw new InterpreterError(op, "No operation applicable for operands: " + left + " " + op.lexeme() + " " + right);

            default:
                throw new UnsupportedOperationException("unsupported operation: " + op);

        }
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

    private Object evaluateLogicalExpr(Logical logical) {
        var left = evaluate(logical.left());
        var operator = logical.operator();
        if (!(left instanceof Boolean)) {
            throw new InterpreterError(operator, "Cannot apply logical operator");
        }

        var isLeftTruthy = (Boolean) left;
        if (operator.type() == Type.AMPERSAND_AMPERSAND) {
            if (!isLeftTruthy) {
                return isLeftTruthy;
            }
        } else if(operator.type() == Type.PIPE_PIPE) {
            if (isLeftTruthy) {
                return isLeftTruthy;
            }
        } else {
            throw new UnsupportedOperationException("unsupported operation: " + operator);
        }

        var right = evaluate(logical.right());
        if (!(right instanceof Boolean)) {
            throw new InterpreterError(operator, "Cannot apply logical operator");
        }

        return (Boolean) right;
    }

    private Object evaluateSequenceExpr(Sequence sequence) {
        var elements = sequence.elements();
        var result = new ArrayList<Object>();
        if (elements.isEmpty()) {
            return result;
        }

        if (elements.get(0) instanceof Expr.Binding) {
            return evaluateTableExpr(sequence);
        }

        for (var element : elements) {
            if (element instanceof Expr.Binding entry) {
                throw new InterpreterError(entry.operator(), "expect sequence element");
            }
            result.add(evaluate(element));
        }
        return result;
    }

    private Object evaluateTableExpr(Sequence sequence) {
        var result = new LinkedHashMap<Object, Object>();
        for (var elem : sequence.elements()) {
            if (elem instanceof Expr.Binding pair) {
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

    private Object evaluateUnaryExpr(Unary unary) {
        var op = unary.operator();
        var value = evaluate(unary.right());

        switch (op.type()) {
            case BANG:
                if (!(value instanceof Boolean)) {
                    var operandClass = value != null ? value.getClass() : null;
                    throw new InterpreterError(op, "Operand must be a boolean, got " + operandClass);
                }
                return !((boolean) value);
            case MINUS:
                if (hasDecimalOperand(op, value)) {
                    return asDecimal(value).negate();
                } else {
                    return asInteger(value).negate();
                }
            case PLUS:
                if (hasDecimalOperand(op, value)) {
                    return asDecimal(value);
                } else {
                    return asInteger(value);
                }
            default:
                throw new UnsupportedOperationException("unsupported operation: " + op);
        }
    }

    private Object evaluateVariableExpr(Variable variable) {
        var key = variable.name().lexeme();
        if (!declarations.containsKey(key)) {
            throw new InterpreterError(variable.name(), "Cannot find variable: " + key);
        }
        return declarations.get(key).get();
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

    private String stringify(@NonNull Object obj) {
        return obj.toString();
    }

    private class InterpreterError extends RuntimeException {
        private final Token token;

        InterpreterError(Token token, String message) {
            super(message);
            this.token = token;
        }

        public Message asError() {
            return new Message(getMessage(), token);
        }
    }
}
