package mackerel.lang;

import lombok.NonNull;
import lombok.Value;

@Value
final class Token {
    @NonNull Type type;
    @NonNull String lexeme;
    Object literal;
    int line;

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }

    enum Type {
        PAREN_LEFT,
        PAREN_RIGHT,
        BRACE_LEFT,
        BRACE_RIGHT,
        COMMA,
        DOT,
        MINUS,
        PLUS,
        QUESTION,
        COLON,
        SEMICOLON,
        SLASH,
        STAR,
        BANG,
        BANG_EQUAL,
        EQUAL,
        EQUAL_EQUAL,
        GREATER,
        GREATER_EQUAL,
        LESS,
        LESS_EQUAL,
        DASH_ARROW,
        EQUAL_ARROW,

        // literals
        IDENTIFIER,
        STRING,
        NUMBER,

        // keywords
        DECL,
        THIS,
        TRUE,
        FALSE,

        // end-of-file
        EOF;
    }
}
