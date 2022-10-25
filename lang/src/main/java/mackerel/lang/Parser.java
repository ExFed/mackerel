package mackerel.lang;

import static mackerel.lang.Token.Type.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class Parser {

    public static record Message(Token token, String message) {}

    @AllArgsConstructor
    @Getter
    private static class ParseError extends RuntimeException {
        private Message parserMessage;
    }

    private final @NonNull TokenStream tokens;

    @Getter
    private final List<Message> errors = new ArrayList<>();

    @Getter
    private final List<Message> warnings = new ArrayList<>();

    public Ast.Source parse() {
        return source();
    }

    public Ast.Repl parseRepl() {
        return repl();
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
            try {
                statements.add(statement());
            } catch (ParseError ex) {
                errors.add(ex.getParserMessage());
                synchronize();
            }
        }
        return new Ast.Source(statements);
    }

    /**
     * <pre>
     *  statement   :: ( "@[ annotations "]" )? ID expression EOL
     * </pre>
     */
    private Ast.Stmt statement() {
        var type = consume(IDENTIFIER, "Expect statement type identifier.");
        var value = expression();
        if (!isAtEndHidden() && !matchHidden(SEMICOLON)) {
            consumeHidden(EOL, "Expect ';' or newline after statement.");
        }
        return new Ast.Stmt(type, value);
    }

    /**
     * <pre>
     *  repl        :: ( ( "@[ annotations "]" )? ID )? expression EOL
     * </pre>
     */
    private Ast.Repl repl() {
        var nodes = new ArrayList<Ast>();
        while (!isAtEnd()) {
            try {
                var nextType = peekNextHidden().type();
                Ast node;
                if (check(IDENTIFIER) && nextType != SEMICOLON && nextType != EOL && nextType != EOF) {
                    node = statement();
                } else {
                    node = expression();
                    if (!isAtEndHidden() && !matchHidden(SEMICOLON)) {
                        consumeHidden(EOL, "Expect ';' or newline after expression.");
                    }
                }
                nodes.add(node);
            } catch (ParseError ex) {
                errors.add(ex.getParserMessage());
                synchronize();
            }
        }
        return new Ast.Repl(nodes);
    }

    /**
     * <pre>
     *  annotations :: annotation ( EOL annotation )* EOL? "]"
     * </pre>
     */
    private Ast annotations() {
        throw new UnsupportedOperationException("todo"); // TODO
    }

    /**
     * <pre>
     *  annotation  :: ID ":" expression
     * </pre>
     */
    private Ast annotation() {
        throw new UnsupportedOperationException("todo"); // TODO
    }

    /**
     * <pre>
     *  expression  :: lambda
     * </pre>
     */
    private Ast expression() {
        return lambda();
    }

    /**
     * <pre>
     *  lambda      :: ( ID ( "," ID )* "->" expression ) | binding
     * </pre>
     */
    private Ast lambda() {
        return binding(); // TODO
    }

    /**
     * <pre>
     *  binding     :: call ( ":" expression )?
     * </pre>
     */
    private Ast binding() {
        var left = call();
        if (check(COLON)) {
            var colon = advance();
            var right = expression();
            return new Ast.Binding(left, colon, right);
        }
        return left;
    }

    /**
     * <pre>
     * `call        :: primary ( "(" expression ( "," expression )* ")" )?
     * </pre>
     */
    private Ast call() {
        return primary(); // TODO
    }

    /**
     * <pre>
     *  primary     :: STRING | INTEGER | DECIMAL | TRUE | FALSE
     *              | ( ID ( "{" builder "}" )? )
     *              | "[]" | ( "[" sequence "]" )
     *              | ( "(" expression ")" )
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
                var builder = builder();
                consume(BRACE_RIGHT, "Expect '}' after builder.");
                return builder;
            }
            return new Ast.Variable(previous());
        }
        if (match(PAREN_LEFT)) {
            var expression = expression();
            consume(PAREN_RIGHT, "Expect ')' after expression.");
            return new Ast.Grouping(expression);
        }
        if (match(BRACKET_LEFT)) {
            var sequence = sequence();
            consume(BRACKET_RIGHT, "Expect ']' after sequence.");
            return sequence;
        }
        throw error(peek(), "Expect primary.");
    }

    /**
     * <pre>
     *  builder     :: statement*
     * </pre>
     */
    private Ast builder() {
        var identifier = previous();
        advance(); // BRACE_LEFT
        var statements = new ArrayList<Ast.Stmt>();
        while (!check(BRACE_RIGHT) && !isAtEnd()) {
            statements.add(statement());
        }
        return new Ast.Builder(identifier, statements);
    }

    /**
     * <pre>
     *  sequence    :: expression ( EOL expression )* EOL?
     * </pre>
     */
    private Ast sequence() {
        var elements = new ArrayList<Ast>();

        do {
            elements.add(expression());
        } while (matchHidden(EOL, COMMA) && !check(BRACKET_RIGHT));

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
        return new ParseError(new Message(token, message));
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
