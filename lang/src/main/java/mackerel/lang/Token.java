package mackerel.lang;

import lombok.NonNull;
import lombok.Value;

@Value
class Token {
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

        // literals
        IDENTIFIER, STRING, NUMBER,

        // keywords
        AND, CLASS, ELSE, FUN, FOR, IF, NIL, OR, PRINT, RETURN, SUPER, THIS, TRUE, FALSE, VAR, WHILE,

        EOF;
    }
}
