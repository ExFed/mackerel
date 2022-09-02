package mackerel.lang;

import java.util.function.Function;
import java.util.function.Supplier;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Lazy<T> implements Supplier<T> {

    public static <T> Lazy<T> of(T value) {
        return new Lazy<>(null, value);
    }

    public static <T> Lazy<T> lazy(@NonNull Supplier<T> supplier) {
        return new Lazy<>(supplier, null);
    }

    private Supplier<T> supplier;
    private T value;

    public <R> Lazy<R> map(Function<T, R> mapper) {
        return lazy(() -> mapper.apply(get()));
    }

    public <R> Lazy<R> flatMap(Function<T, Lazy<R>> mapper) {
        return lazy(() -> mapper.apply(get()).get());
    }

    @Override
    public T get() {
        if (supplier != null) {
            value = supplier.get();
            supplier = null;
        }
        return value;
    }
}
