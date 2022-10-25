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
        var hiddenTag = hidden ? " HIDDEN" : "";
        return "(Token " + type + " \"" + lexeme + "\" " + line + ":" + column + hiddenTag + ")";
    }

    enum Type {
        BRACE_LEFT,
        BRACE_RIGHT,
        BRACKET_LEFT,
        BRACKET_RIGHT,
        COLON,
        COMMA,
        DASH_ARROW,
        PAREN_LEFT,
        PAREN_RIGHT,

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
