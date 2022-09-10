package mackerel.lang;

import static mackerel.lang.Token.Type.EOF;
import static mackerel.lang.Token.Type.EOL;
import static mackerel.lang.Token.Type.IDENTIFIER;
import static mackerel.lang.Token.Type.INTEGER;
import static mackerel.lang.Token.Type.SEMICOLON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TokenStreamTest {

    TokenStream stream;

    boolean expectAtEnd;
    boolean includeHidden;

    private void assertNextToken(Token expect) {
        if (includeHidden) {
            assertEquals(expectAtEnd, stream.isAtEnd(true));
            assertEquals(expect, stream.peek(true));
            assertEquals(expect, stream.advance(true));
        } else {
            assertEquals(expectAtEnd, stream.isAtEnd());
            assertEquals(expect, stream.peek());
            assertEquals(expect, stream.advance());
        }
        assertEquals(expect, stream.previous());
    }

    @BeforeEach
    void setUp() {
        var source = "hello;\nworld;42\n";
        var scanner = new Scanner(source);
        stream = new TokenStream(scanner.getTokens());
        includeHidden = false;
        expectAtEnd = false;
    }

    @Test
    void hidden() {
        includeHidden = true;
        assertNextToken(new Token(IDENTIFIER, "hello", 1, false));
        assertNextToken(new Token(SEMICOLON, ";", 1, false));
        assertNextToken(new Token(EOL, "\n", 1, true));
        assertNextToken(new Token(IDENTIFIER, "world", 2, false));
        assertNextToken(new Token(SEMICOLON, ";", 2, false));
        assertNextToken(new Token(INTEGER, "42", 2, false));
        assertNextToken(new Token(EOL, "\n", 2, true));
        expectAtEnd = true;
        assertNextToken(new Token(EOF, "", 3, false));
    }

    @Test
    void visible() {
        includeHidden = false;
        assertNextToken(new Token(IDENTIFIER, "hello", 1, false));
        assertNextToken(new Token(SEMICOLON, ";", 1, false));
        assertNextToken(new Token(IDENTIFIER, "world", 2, false));
        assertNextToken(new Token(SEMICOLON, ";", 2, false));
        assertNextToken(new Token(INTEGER, "42", 2, false));
        expectAtEnd = true;
        assertNextToken(new Token(EOF, "", 3, false));
    }
}
