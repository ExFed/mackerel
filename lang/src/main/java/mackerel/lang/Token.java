package mackerel.lang;

import lombok.NonNull;

record Token(
    @NonNull Type type,
    @NonNull String lexeme,
    int line,
    int column,
    boolean hidden) {

    @Override
    public String toString() {
        return "(Token " + type + " " + lexeme + " " + line + ":" + column + (hidden ? " HIDDEN" : "") + ")" ;
    }

    enum Type {
        AMPERSAND_AMPERSAND,
        AMPERSAND,
        BANG_EQUAL,
        BANG,
        BRACE_LEFT,
        BRACE_RIGHT,
        BRACKET_LEFT,
        BRACKET_RIGHT,
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
        PIPE_PIPE,
        PIPE,
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
        SEMICOLON,

        // end-of-file
        EOF;
    }
}
