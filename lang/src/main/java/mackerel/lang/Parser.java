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

    public static record Error(Token token, String message) {}

    private static class ParseError extends RuntimeException {}

    private final @NonNull List<Token> tokens;

    @Getter
    private final List<Error> errors = new ArrayList<>();

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

    //// grammar rules ////

    private Expr association() {
        var bindings = new ArrayList<Expr.Binding>();

        // empty association?
        if (!match(COLON)) {
            while (!check(BRACE_RIGHT)) {
                var name = consume(IDENTIFIER, "Expect identifier.");
                consume(COLON, "Expect ':' after binding name.");
                var expr = expression();
                bindings.add(new Expr.Binding(name, expr));
            }
        }

        consume(BRACE_RIGHT, "Expect '}' after bindings.");
        return new Expr.Association(bindings);
    }

    private Expr collection() {
        if (check(IDENTIFIER) && checkNext(COLON) || check(COLON)) {
            return association();
        }

        var elements = new ArrayList<Expr>();
        while (!check(BRACE_RIGHT)) {
            elements.add(expression());
        }
        consume(BRACE_RIGHT, "Expect '}' after elements.");
        return new Expr.Collection(elements);
    }

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
            return new Expr.Variable(previous());
        }
        if (match(PAREN_LEFT)) {
            var expr = expression();
            consume(PAREN_RIGHT, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        if (match(BRACE_LEFT)) {
            return collection();
        }
        throw error(peek(), "Expect expression.");
    }

    private Expr unary() {
        if (match(BANG, MINUS, PLUS, TILDE)) {
            var operator = previous();
            var right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
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

    private Expr expression() {
        return or();
    }

    private Stmt declaration() {
        var name = consume(IDENTIFIER, "Expect declaration name.");
        consume(COLON, "Expect colon after declaration name.");
        return new Stmt.Declaration(name, expression());
    }

    private Stmt expressionStatement() {
        var expression = expression();
        if (!isAtEnd()) {
            consume(EOL, "Expect EOL after expression.");
        }
        return new Stmt.Expression(expression);
    }

    private Stmt statement() {
        try {
            if (match(DECL)) {
                return declaration();
            }
            return expressionStatement();
        } catch (ParseError ex) {
            synchronize();
            return null;
        }
    }

    //// utility methods ////

    private Token consume(Token.Type type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        errors.add(new Error(token, message));
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd() && EOL != peek().type()) {
            advance();
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
