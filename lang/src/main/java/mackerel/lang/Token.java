package mackerel.lang;

import lombok.NonNull;

record Token(@NonNull Type type, @NonNull String lexeme, int line) {

    @Override
    public String toString() {
        return type + " " + lexeme + " " + line;
    }

    enum Type {
        AMPERSAND,
        AMPERSAND_AMPERSAND,
        PIPE,
        PIPE_PIPE,
        BANG_EQUAL,
        BANG,
        BRACE_LEFT,
        BRACE_RIGHT,
        COLON,
        COMMA,
        DASH_ARROW,
        DOT,
        EQUAL_ARROW,
        EQUAL_EQUAL,
        EQUAL,
        GREATER_EQUAL,
        GREATER,
        LESS_EQUAL,
        LESS,
        MINUS,
        PAREN_LEFT,
        PAREN_RIGHT,
        PLUS,
        QUESTION,
        SLASH,
        STAR,
        TILDE,

        // literals
        DECIMAL,
        IDENTIFIER,
        INTEGER,
        STRING,

        // keywords
        TRUE,
        FALSE,

        // end-of-line
        EOL,

        // end-of-file
        EOF;
    }
}
