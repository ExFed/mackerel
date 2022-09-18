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

    private final @NonNull TokenStream tokens;

    @Getter
    private final List<Message> errors = new ArrayList<>();

    @Getter
    private final List<Message> warnings = new ArrayList<>();

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
        throw error(peek(), "Expect expression.");
    }

    private Expr binding() {
        var left = primary();
        if (check(COLON)) {
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
            return new Expr.Table(List.of());
        }

        if (match(BRACKET_RIGHT)) { // empty sequence
            return new Expr.Tuple(List.of());
        }

        Boolean isTable = null;
        do {
            var element = expression();
            if (isTable == null) {
                isTable = element instanceof Expr.Binding;
            } else if (!isTable && element instanceof Expr.Binding) {
                throw error(previous(), "Expect tuple element.");
            } else if (isTable && !(element instanceof Expr.Binding)) {
                throw error(previous(), "Expect table binding.");
            }
            elements.add(element);
        } while (matchHidden(EOL, SEMICOLON) && !check(BRACKET_RIGHT));

        if (isTable) {
            consume(BRACKET_RIGHT, "Expect ']' after table.");
            var pairs = new ArrayList<Expr.Binding>();
            for (var element : elements) {
                pairs.add((Expr.Binding) element);
            }
            return new Expr.Table(pairs);
        }

        consume(BRACKET_RIGHT, "Expect ']' after tuple.");
        return new Expr.Tuple(elements);
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
        return new Stmt.Declaration(type, definition);
    }

    private Stmt expressionStatement() {
        var expression = expression();
        return new Stmt.Expression(expression);
    }

    private Stmt statement() {
        Stmt stmt;
        try {
            var nextType = peekNextHidden().type();
            if (check(IDENTIFIER) && nextType != SEMICOLON && nextType != EOL && nextType != EOF) {
                stmt = declaration();
            } else {
                stmt = expressionStatement();
            }

            if (!isAtEndHidden()) {
                if (!matchHidden(SEMICOLON)) {
                    consumeHidden(EOL, "Expect ';' or newline after statement.");
                }
            }
        } catch (ParseError ex) {
            synchronize();
            return null;
        }
        return stmt;
    }

    //// utility methods ////

    private Token consume(Token.Type type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }

    private Token consumeHidden(Token.Type type, String message) {
        if (checkHidden(type)) {
            return advanceHidden();
        }

        throw error(peekHidden(), message);
    }

    private ParseError error(Token token, String message) {
        errors.add(new Message(token, message));
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEndHidden()) {
            var token = advanceHidden();
            if (token.type() == EOL || token.type() == SEMICOLON) {
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

    private boolean matchHidden(Token.Type... types) {
        for (var type : types) {
            if (checkHidden(type)) {
                advanceHidden();
                return true;
            }
        }

        return false;
    }

    private boolean check(Token.Type type) {
        return !isAtEnd() && peek().type() == type;
    }

    private boolean checkHidden(Token.Type type) {
        return !isAtEndHidden() && peekHidden().type() == type;
    }

    private Token advance() {
        return tokens.advance();
    }

    private Token advanceHidden() {
        return tokens.advance(true);
    }

    private boolean isAtEnd() {
        return tokens.isAtEnd();
    }

    private boolean isAtEndHidden() {
        return tokens.isAtEnd(true);
    }

    private Token peek() {
        return tokens.peek();
    }

    private Token peekHidden() {
        return tokens.peek(true);
    }

    private Token peekNext() {
        return tokens.peekNext();
    }

    private Token peekNextHidden() {
        return tokens.peekNext(true);
    }

    private Token previous() {
        return tokens.previous();
    }
}
