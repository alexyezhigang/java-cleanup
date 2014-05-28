package ch.claude_martin.cleanup;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.function.Consumer;

/**
 * A {@link PhantomReference} that has a {@link #cleanup}-function and a {@link #value}. However,
 * there is no reference to the actual object for which this was created.
 * 
 * @param <T>
 *          Type of the original object.
 * @param <V>
 *          Type of the value.
 */
final class CleanupPhantomRef<T, V> extends PhantomReference<T> {
  private final Consumer<V> cleanup;
  private final V value;

  void runFinalization() {
    this.cleanup.accept(this.value);
  }

  @SuppressWarnings("unchecked")
  CleanupPhantomRef(T referent, Consumer<V> cleanup, V value) {
    super(referent, (ReferenceQueue<? super T>) CleanupDaemon.getQueue());
    this.cleanup = cleanup;
    this.value = value;
  }
}