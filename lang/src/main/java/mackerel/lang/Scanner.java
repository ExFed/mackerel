package mackerel.lang;

import static java.util.Map.entry;
import static mackerel.lang.Token.Type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
final class Scanner {

    static record Error(int line, String message) {};

    private static final Map<String, Token.Type> keywords = Map.ofEntries(
        entry("decl", DECL),
        entry("this", THIS),
        entry("true", TRUE),
        entry("false", FALSE));

    private final @NonNull String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Error> errors = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    boolean hasErrors() {
        return !errors.isEmpty();
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void scanToken() {
        var c = advance();
        switch (c) {
        case '(':
            addToken(PAREN_LEFT);
            break;
        case ')':
            addToken(PAREN_RIGHT);
            break;
        case '{':
            addToken(BRACE_LEFT);
            break;
        case '}':
            addToken(BRACE_RIGHT);
            break;
        case ',':
            addToken(COMMA);
            break;
        case '.':
            addToken(DOT);
            break;
        case '-':
            addToken(match('>') ? DASH_ARROW : MINUS);
            break;
        case '+':
            addToken(PLUS);
            break;
        case '?':
            addToken(QUESTION);
            break;
        case ':':
            addToken(COLON);
            break;
        case ';':
            addToken(SEMICOLON);
            break;
        case '*':
            addToken(STAR);
            break;
        case '!':
            addToken(match('=') ? BANG_EQUAL : BANG);
            break;
        case '=':
            if (match('>')) {
                addToken(EQUAL_ARROW);
            } else if (match('=')) {
                addToken(EQUAL_EQUAL);
            } else {
                addToken(EQUAL);
            }
            break;
        case '<':
            addToken(match('=') ? LESS_EQUAL : LESS);
            break;
        case '>':
            addToken(match('=') ? GREATER_EQUAL : GREATER);
            break;
        case '/':
            if (match('/')) {
                while (peek() != '\n' && !isAtEnd()) {
                    advance();
                }
            } else if (match('*')) {
                blockComment();
            } else {
                addToken(SLASH);
            }
            break;

        // whitespace
        case ' ':
        case '\r':
        case '\t':
            break;

        case '\n':
            line++;
            break;

        case '"':
            string();
            break;

        default:
            if (isDigit(c)) {
                number();
            } else if (isAlpha(c)) {
                identifier();
            } else {
                error(line, "Unexpected character: '" + String.valueOf(c) + "'");
            }
        }
    }

    private static boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || c == '_';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <='9';
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        var text = source.substring(start, current);
        var type = keywords.getOrDefault(text, IDENTIFIER);
        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) {
            advance();
        }

        // is decimal?
        if (peek() == '.' && isDigit(peekNext())) {
            // consume the decimal
            advance();

            while (isDigit(peek())) {
                advance();
            }
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void string() {
        var value = new StringBuilder();
        var esc = false;
        while ((peek() != '"' || esc) && !isAtEnd()) {
            if (peek() == '\n') {
                line++;
            }

            if (esc) {
                switch (peek()) {
                    case 'n':
                        advance();
                        value.append('\n');
                        break;
                    case '\n':
                        advance(); // skip newlines
                        break;
                    case '\\':
                    case '\"':
                        value.append(advance());
                        break;
                    default:
                        error(line, "Unexpected escape sequence.");
                        return;
                }
                esc = false;
            } else if (peek() == '\\') {
                advance(); // skip escape character
                esc = true;
            } else {
                value.append(advance());
            }
        }
        if (isAtEnd()) {
            error(line, "Unterminated string.");
            return;
        }
        advance();
        addToken(STRING, value.toString());
    }

    private void blockComment() {
        while (!isAtEnd() && !(peek() == '*' && peekNext() == '/')) {
            if (peek() == '\n') {
                line++;
            }
            var c = advance();
            if (c == '/' && match('*')) {
                blockComment(); // nested comment
            }
        }

        if (isAtEnd()) {
            error(line, "Unterminated comment.");
            return;
        }
        // discard the `*/`
        advance();
        advance();
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean match(char expected) {
        if (isAtEnd()) {
            return false;
        }
        if (source.charAt(current) != expected) {
            return false;
        }

        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) {
            return '\0';
        }
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) {
            return '\0';
        }
        return source.charAt(current + 1);
    }

    private void addToken(Token.Type type) {
        addToken(type, null);
    }

    private void addToken(Token.Type type, Object literal) {
        var text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private void error(int line, String msg) {
        errors.add(new Error(line, msg));
    }
}
