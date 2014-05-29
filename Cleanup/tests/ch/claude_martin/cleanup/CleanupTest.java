package ch.claude_martin.cleanup;

import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link Cleanup}.
 *
 */
@SuppressWarnings("static-method")
public class CleanupTest {
  private static final class MyFinalizable implements Cleanup {

    @BeforeClass
    public static void before() {
      CleanupDaemon.THREAD.setPriority(Thread.MAX_PRIORITY); // only while testing
      CleanupDaemon.addExceptionHandler((ex) -> {
        fail("Exception: " + ex);
      });
    }

    public MyFinalizable() {
      super();
    }

    @SuppressWarnings("unused")
    public final byte[] data = new byte[5_000_000];

    public final Object getAnonymous() {
      return new Cloneable() {
        // this$0 = implicit reference to MyFinalizable.this
      };
    }
  }

  @Test
  public final void testCleanup() {
    final int answer = 42;
    AtomicInteger i = new AtomicInteger(0);
    {
      MyFinalizable test = new MyFinalizable();
      test.registerCleanup((v) -> {
        i.set(v);
      }, answer);
      test = null;
    }
    gc();
    Assert.assertTrue(answer == i.get());
  }

  private static void gc() {
    for (int j = 0; j < 10; j++) {
      Thread.yield();
      System.gc();
      System.runFinalization();
    }
  }

  @Test
  public final void testTwice() {
    AtomicInteger i = new AtomicInteger(0);
    MyFinalizable test = new MyFinalizable();
    test.registerCleanup((v) -> {
      i.incrementAndGet();
    }, 42);
    test.registerCleanup((v) -> {
      i.incrementAndGet();
    }, -1);
    test = null;
    gc();
    assertEquals(2, i.get());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testThis() {
    MyFinalizable test = new MyFinalizable();
    test.registerCleanup((v) -> {
    }, test);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAnonymous() {
    MyFinalizable test = new MyFinalizable();
    test.registerCleanup((v) -> {
    }, test.getAnonymous());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testLambda() {
    MyFinalizable test = new MyFinalizable();
    test.registerCleanup((v) -> {
    }, Function.identity());
  }

  @Test
  public final void testArray() {
    MyFinalizable test = new MyFinalizable();
    test.registerCleanup((v) -> {
    }, new byte[10]);
  }

  @Test
  public final void testParallel() throws InterruptedException {
    AtomicBoolean result = new AtomicBoolean(true);
    ExecutorService pool = Executors.newFixedThreadPool(4, (r) -> {
      Thread thread = new Thread(r);
      thread.setName("testMany");
      thread.setUncaughtExceptionHandler((t, ex) -> {
        result.set(false);
        fail("testMany: " + ex);
      });
      return thread;
    });
    for (int i = 0; i < 8; i++) {
      pool.execute(() -> {
        try {
          testCleanup();
        } catch (Throwable e) {
          result.set(false);
        }
      });
    }
    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.MINUTES);
    Thread.sleep(100);
    assertTrue(result.get());
  }

  @Test
  public final void testExceptionHandler() throws Exception {
    final List<String> refs = synchronizedList(new ArrayList<>());
    final String message = "test";
    {
      MyFinalizable test = new MyFinalizable();
      Consumer<Throwable> c = (t) -> {
        refs.add(t.getMessage());
      };
      Cleanup.addExceptionHandler(c);
      Cleanup.addExceptionHandler(c);
      Cleanup.addExceptionHandler(c);
      test.registerCleanup((v) -> {
        throw new RuntimeException(message);
      }, 42);
      test = null;
    }
    gc();
    assertEquals(3, refs.size());
    assertEquals(asList(message, message, message), refs);
  }
}