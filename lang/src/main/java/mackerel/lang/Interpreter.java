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
import mackerel.lang.Expr.Grouping;

final class Interpreter {

    @Getter
    private final List<RuntimeErrorException> errors = new ArrayList<>();

    private final Map<String, Expr> declarations = new LinkedHashMap<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (var statement : statements) {
                execute(statement);
            }
        } catch (RuntimeErrorException ex) {
            errors.add(ex);
        }
    }

    private void execute(Stmt statement) {
        if (statement instanceof Stmt.Expression expr) {
            System.out.println(evaluate(expr.expression()));
        } else if(statement instanceof Stmt.Declaration decl) {
            System.out.println("declaration: " + decl.name() + " : " + decl.definition());
        } else {
            throw new IllegalArgumentException("unsupported statement: " + statement);
        }
    }

    private Object evaluate(Expr expr) {
        if (expr instanceof Expr.Literal literal) {
            return literal.value();
        }

        if (expr instanceof Expr.Binary binary) {
            return evaluateBinaryExpr(binary);
        }

        if (expr instanceof Expr.Grouping grouping) {
            return evaluateGroupingExpr(grouping);
        }

        return "unsupported expression:\n  " + expr;
    }

    private Object evaluateGroupingExpr(Grouping grouping) {
        return evaluate(grouping.expression());
    }

    private Object evaluateBinaryExpr(Binary expr) {
        var op = expr.operator();
        var left = evaluate(expr.left());
        var right = evaluate(expr.right());

        switch (op.type()) {
            case GREATER:
                if (checkNumberOperands(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) > 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) > 0;
                }
            case GREATER_EQUAL:
                if (checkNumberOperands(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) >= 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) >= 0;
                }
            case LESS:
                if (checkNumberOperands(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) < 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) < 0;
                }
            case LESS_EQUAL:
                if (checkNumberOperands(op, left, right)) {
                    return asDecimal(left).compareTo(asDecimal(right)) <= 0;
                } else {
                    return asInteger(left).compareTo(asInteger(right)) <= 0;
                }
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case MINUS:
                if (checkNumberOperands(op, left, right)) {
                    return asDecimal(left).subtract(asDecimal(right));
                } else {
                    return asInteger(left).subtract(asInteger(right));
                }
            case SLASH:
                if (checkNumberOperands(op, left, right)) {
                    return asDecimal(left).divide(asDecimal(right));
                } else {
                    return asInteger(left).divide(asInteger(right));
                }
            case STAR:
                if (checkNumberOperands(op, left, right)) {
                    return asDecimal(left).multiply(asDecimal(right));
                } else {
                    return asInteger(left).multiply(asInteger(right));
                }
            case PLUS:
                if (left instanceof Number && right instanceof Number) {
                    if (checkNumberOperands(op, left, right)) {
                        return asDecimal(left).add(asDecimal(right));
                    } else {
                        return asInteger(left).add(asInteger(right));
                    }
                }

                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }

                throw new RuntimeErrorException(op, "No operation applicable for operands: " + left + " " + op.lexeme() + " " + right);

            default:
                throw new UnsupportedOperationException("unsupported operation: " + op);

        }
    }

    private boolean checkNumberOperands(Token operator, Object... operands) {
        var hasInteger = false;
        var hasDecimal = false;
        for (var operand : operands) {
            if (operand instanceof BigInteger) {
                hasInteger = true;
            } else if (operand instanceof BigDecimal) {
                hasDecimal = true;
            } else {
                var operandClass = operand != null ? operand.getClass() : null;
                throw new RuntimeErrorException(operator, "Operands must be numbers, got " + operandClass);
            }
        }
        return hasInteger && hasDecimal;
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

    private boolean isTruthy(Object object) {
        if (object == null)
            return false;
        if (object instanceof BigInteger num)
            return !BigInteger.ZERO.equals(num);
        if (object instanceof BigDecimal num)
            return !BigDecimal.ZERO.equals(num);
        if (object instanceof Boolean bool)
            return bool;
        return true;
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
}
