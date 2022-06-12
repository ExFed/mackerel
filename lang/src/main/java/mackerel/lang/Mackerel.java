package mackerel.lang;

import static lombok.AccessLevel.PRIVATE;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = PRIVATE)
public class Mackerel {
    public static void main(String[] args) {
        System.out.println("Hello, Mackerel!");
    }
}
