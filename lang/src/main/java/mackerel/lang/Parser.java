package mackerel.lang;

import static mackerel.lang.Token.Type.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class Parser {

    public static record Message(Token token, String message) {}

    private static class ParseError extends RuntimeException {}

    private final @NonNull List<Token> tokens;

    @Getter
    private final List<Message> errors = new ArrayList<>();

    @Getter
    private final List<Message> warnings = new ArrayList<>();

    private int current = 0;

    public List<Stmt> parse() {
        var statements = new ArrayList<Stmt>();
        while (!isAtEnd()) {
            statements.add(statement());
        }
        return statements;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    //// grammar rules ////

    private Expr primary() {
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }
        if (match(TRUE)) {
            return new Expr.Literal(true);
        }
        if (match(STRING)) {
            var lexeme = previous().lexeme();
            var string = lexeme.substring(1, lexeme.length() - 1); // strip quotes
            return new Expr.Literal(string);
        }
        if (match(DECIMAL)) {
            var value = new BigDecimal(previous().lexeme());
            return new Expr.Literal(value);
        }
        if (match(INTEGER)) {
            var value = new BigInteger(previous().lexeme());
            return new Expr.Literal(value);
        }
        if (match(IDENTIFIER)) {
            var identifier = previous();
            if (match(BRACE_LEFT)) {
                var statements = new ArrayList<Stmt>();
                while (!check(BRACE_RIGHT) && !isAtEnd()) {
                    statements.add(statement());
                }
                consume(BRACE_RIGHT, "Expect '}' after block.");
                return new Expr.Builder(identifier, statements);
            }
            return new Expr.Variable(identifier);
        }
        if (match(PAREN_LEFT)) {
            var expr = expression();
            consume(PAREN_RIGHT, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        if (match(BRACKET_LEFT)) {
        }
        throw error(peek(), "Expect expression.");
    }

    private Expr binding() {
        var left = primary();
        if (check(COLON)) {
            if (left instanceof Expr.Variable variable) {
                left = new Expr.Literal(variable.name().lexeme());
            }
            var colon = advance();
            var right = expression();
            return new Expr.Binding(left, colon, right);
        }
        return left;
    }

    private Expr unary() {
        if (match(BANG, MINUS, PLUS, TILDE)) {
            var operator = previous();
            var right = unary();
            return new Expr.Unary(operator, right);
        }
        return binding();
    }

    private Expr factor() {
        var expr = unary();

        while (match(SLASH, STAR)) {
            var operator = previous();
            var right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        var expr = factor();

        while (match(MINUS, PLUS)) {
            var operator = previous();
            var right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        var expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            var operator = previous();
            var right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        var expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            var operator = previous();
            var right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        var expr = equality();

        while (match(AMPERSAND_AMPERSAND)) {
            var operator = previous();
            var right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr or() {
        var expr = and();

        while (match(PIPE_PIPE)) {
            var operator = previous();
            var right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr sequence() {
        var elements = new ArrayList<Expr>();

        if (match(COLON)) { // empty table
            consume(BRACKET_RIGHT, "Expect ']' after table.");
            return new Expr.Table(new ArrayList<>());
        }

        if (match(BRACKET_RIGHT)) { // empty sequence
            return new Expr.Sequence(new ArrayList<>());
        }

        ignoreEOL();

        Boolean isTable = null;
        do {
            var element = expression();
            if (isTable == null) {
                isTable = element instanceof Expr.Binding;
            } else if (!isTable && element instanceof Expr.Binding) {
                throw error(previous(), "Expect sequence element.");
            } else if (isTable && !(element instanceof Expr.Binding)) {
                throw error(previous(), "Expect table binding.");
            }
            elements.add(element);
        } while (match(EOL) && !check(BRACKET_RIGHT));

        if (isTable) {
            consume(BRACKET_RIGHT, "Expect ']' after table.");
            var pairs = new ArrayList<Expr.Binding>();
            for (var element : elements) {
                pairs.add((Expr.Binding) element);
            }
            return new Expr.Table(pairs);
        }

        consume(BRACKET_RIGHT, "Expect ']' after table.");
        return new Expr.Sequence(elements);
    }

    private Expr expression() {
        if (match(BRACKET_LEFT)) {
            return sequence();
        }
        return or();
    }

    private Stmt declaration() {
        var type = advance();
        var definition = expression();
        ignoreEOL();
        return new Stmt.Declaration(type, definition);
    }

    private Stmt expressionStatement() {
        var expression = expression();
        if (!isAtEnd() && !check(BRACE_RIGHT)) {
            consume(EOL, "Expect ';' or newline after expression.");
        }
        return new Stmt.Expression(expression);
    }

    private Stmt statement() {
        Stmt stmt;
        try {
            if (check(IDENTIFIER) && peekNext().type() != EOF && peekNext().type() != EOL) {
                stmt = declaration();
            } else {
                stmt = expressionStatement();
            }
        } catch (ParseError ex) {
            synchronize();
            return null;
        }
        ignoreEOL();
        return stmt;
    }

    //// utility methods ////

    private Token consume(Token.Type type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        errors.add(new Message(token, message));
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type() == EOL) {
                return;
            }
        }
    }

    private boolean match(Token.Type... types) {
        for (var type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(Token.Type type) {
        return !isAtEnd() && peek().type() == type;
    }

    private boolean checkNext(Token.Type type) {
        var nextType = peekNext().type();
        return nextType != EOF && nextType == type;
    }

    private boolean checkNext(Token.Type first, Token.Type... rest) {
        if (checkNext(first)) {
            return true;
        }

        for (var type : rest) {
            if (checkNext(type)) {
                return true;
            }
        }
        return false;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        var token = previous();
        return token;
    }

    private void ignoreEOL() {
        while (check(EOL)) {
            advance();
        }
    }

    private boolean isAtEnd() {
        return peek().type() == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekNext() {
        if (isAtEnd()) {
            return peek();
        }
        return tokens.get(current + 1);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }
}
