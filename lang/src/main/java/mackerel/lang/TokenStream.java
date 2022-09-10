package mackerel.lang;

import static mackerel.lang.Token.Type.*;

import java.util.List;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TokenStream {

    private final @NonNull List<Token> tokens;

    private int current = 0;
    private Token previous = null;

    public Token previous() {
        return previous != null ? previous : peek();
    }

    public boolean isAtEnd() {
        return isAtEnd(false);
    }

    public boolean isAtEnd(boolean includeHidden) {
        return peek(includeHidden).type() == EOF;
    }

    public Token peek() {
        return peek(false);
    }

    public Token peek(boolean includeHidden) {
        return peekFrom(current, includeHidden);
    }

    public Token peekNext() {
        return peekNext(false);
    }

    public Token peekNext(boolean includeHidden) {
        if (isAtEnd(includeHidden)) {
            return peek(includeHidden);
        }
        return peekFrom(current + 1, includeHidden);
    }

    public Token advance() {
        return advance(false);
    }

    public Token advance(boolean includeHidden) {
        previous = tokens.get(current);
        if (!isAtEnd(includeHidden)) {
            current++;
        }
        if (!includeHidden) {
            current = nextVisible(current);
        }
        return previous;
    }

    private int nextVisible(int index) {
        var token = tokens.get(index);
        while (token.hidden() && EOF != token.type()) {
            index++;
            token = tokens.get(index);
        }
        return index;
    }

    private Token peekFrom(int index, boolean includeHidden) {
        if (!includeHidden) {
            index = nextVisible(index);
        }
        return tokens.get(index);
    }
}
