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

    public Ast.Source parse() {
        return source();
    }

    public List<Ast> replParse() {
        var nodes = new ArrayList<Ast>();
        while (!isAtEnd()) {
            nodes.add(replParseNode());
        }
        return nodes;
    }

    private Ast replParseNode() {
        Ast node;
        try {
            var nextType = peekNextHidden().type();
            if (check(IDENTIFIER) && nextType != SEMICOLON && nextType != EOL && nextType != EOF) {
                node = statement();
            } else {
                node = expression();
            }

            if (!isAtEndHidden()) {
                if (!matchHidden(SEMICOLON)) {
                    consumeHidden(EOL, "Expect ';' or newline.");
                }
            }
        } catch (ParseError ex) {
            synchronize();
            return null;
        }
        return node;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    //// grammar rules ////

    /**
     * <pre>
     * source      :: statement* EOF
     * </pre>
     */
    private Ast.Source source() {
        var statements = new ArrayList<Ast.Stmt>();
        while (!isAtEnd()) {
            statements.add(statement());
        }
        return new Ast.Source(statements);
    }


    /**
     * <pre>
     * statement   :: ( "@[ annotation ( EOL annotation )* EOL? "]" )? ID expression EOL
     * </pre>
     */
    private Ast.Stmt statement() {
        var type = advance();
        var value = expression();
        return new Ast.Stmt(type, value);
    }

    // TODO
    /**
     * <pre>
     * annotation  :: ID ":" expression
     * </pre>
     */
    private Ast annotation() {
        throw new UnsupportedOperationException("todo");
    }

    /**
     * <pre>
     * expression  :: lambda
     * </pre>
     */
    private Ast expression() {
        return or();
    }

    // TODO
    /**
     * <pre>
     * lambda      :: ( ID ( "," ID )* "->" expression ) | or
     * </pre>
     */
    private Ast lambda() {
        throw new UnsupportedOperationException("todo");
    }

    /**
     * <pre>
     * or          :: and ( "||" and )*
     * </pre>
     */
    private Ast or() {
        var expr = and();

        while (match(PIPE_PIPE)) {
            var operator = previous();
            var right = and();
            expr = new Ast.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * <pre>
     * and         :: equality ( "&&" equality )*
     * </pre>
     */
    private Ast and() {
        var expr = equality();

        while (match(AMPERSAND_AMPERSAND)) {
            var operator = previous();
            var right = equality();
            expr = new Ast.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * <pre>
     * equality    :: comparison ( ( "==" | "!=" ) comparison )*
     * </pre>
     */
    private Ast equality() {
        var expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            var operator = previous();
            var right = comparison();
            expr = new Ast.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * <pre>
     * comparison  :: term ( ( ">" | ">=" | "<" | "<=" ) term )*
     * </pre>
     */
    private Ast comparison() {
        var expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            var operator = previous();
            var right = term();
            expr = new Ast.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * <pre>
     * term        :: factor ( ( "-" | "+" ) factor )*
     * </pre>
     */
    private Ast term() {
        var expr = factor();

        while (match(MINUS, PLUS)) {
            var operator = previous();
            var right = factor();
            expr = new Ast.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * <pre>
     * factor      :: unary ( ( "/" | "*" ) unary )*
     * </pre>
     */
    private Ast factor() {
        var expr = unary();

        while (match(SLASH, STAR)) {
            var operator = previous();
            var right = unary();
            expr = new Ast.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * <pre>
     * unary       :: ( ( "!" | "-" | "+" ) unary ) | binding
     * </pre>
     */
    private Ast unary() {
        if (match(BANG, MINUS, PLUS, TILDE)) {
            var operator = previous();
            var right = unary();
            return new Ast.Unary(operator, right);
        }
        return binding();
    }

    /**
     * <pre>
     * binding     :: call ( ":" expression )?
     * </pre>
     */
    private Ast binding() {
        var left = primary();
        if (check(COLON)) {
            var colon = advance();
            var right = expression();
            return new Ast.Binding(left, colon, right);
        }
        return left;
    }

    // TODO
    /**
     * <pre>
     * call        :: primary ( "(" expression ( "," expression )* ")" )?
     * </pre>
     */
    private Ast call() {
        throw new UnsupportedOperationException("todo");
    }

    /**
     * <pre>
     * primary     :: STRING | INTEGER | DECIMAL | TRUE | FALSE
     *             | ( ID ( "{" builder "}" )? )
     *             | "[]" | ( "[" sequence "]" )
     *             | ( "(" expression ")" )
     * </pre>
     */
    private Ast primary() {
        if (match(FALSE)) {
            return new Ast.Literal(false, previous());
        }
        if (match(TRUE)) {
            return new Ast.Literal(true, previous());
        }
        if (match(STRING)) {
            var lexeme = previous().lexeme();
            var string = lexeme.substring(1, lexeme.length() - 1); // strip quotes
            return new Ast.Literal(string, previous());
        }
        if (match(DECIMAL)) {
            var value = new BigDecimal(previous().lexeme());
            return new Ast.Literal(value, previous());
        }
        if (match(INTEGER)) {
            var value = new BigInteger(previous().lexeme());
            return new Ast.Literal(value, previous());
        }
        if (match(IDENTIFIER)) {
            if (check(BRACE_LEFT)) {
                return builder();
            }
            return new Ast.Variable(previous());
        }
        if (match(PAREN_LEFT)) {
            var expr = expression();
            consume(PAREN_RIGHT, "Expect ')' after expression.");
            return new Ast.Grouping(expr);
        }
        if (match(BRACKET_LEFT)) {
            return sequence();
        }
        throw error(peek(), "Expect primary.");
    }

    /**
     * <pre>
     * builder     :: statement*
     * </pre>
     */
    private Ast builder() {
        var identifier = previous();
        advance(); // BRACE_LEFT
        var statements = new ArrayList<Ast.Stmt>();
        while (!check(BRACE_RIGHT) && !isAtEnd()) {
            statements.add(statement());
        }
        consume(BRACE_RIGHT, "Expect '}' after builder.");
        return new Ast.Builder(identifier, statements);
    }

    /**
     * <pre>
     * sequence    :: expression ( EOL expression )* EOL?
     * </pre>
     */
    private Ast sequence() {
        var elements = new ArrayList<Ast>();

        do {
            elements.add(expression());
        } while (matchHidden(EOL, SEMICOLON) && !check(BRACKET_RIGHT));

        consume(BRACKET_RIGHT, "Expect ']' after sequence.");
        return new Ast.Sequence(elements);
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
