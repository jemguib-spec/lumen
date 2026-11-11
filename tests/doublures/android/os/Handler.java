// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh) : le strict nécessaire d'Android pour faire tourner deux TV dans un même programme.
package android.os;
import java.util.concurrent.*;
public class Handler { static final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "main"); t.setDaemon(true); Looper.mainThread = t; return t; });
  static final java.util.Map<Runnable, java.util.List<ScheduledFuture<?>>> d = new ConcurrentHashMap<>();
  public Handler(Looper l) { }
  public boolean post(Runnable r) { ex.execute(r); return true; }
  public boolean postDelayed(Runnable r, long ms) { d.computeIfAbsent(r, k -> new CopyOnWriteArrayList<>()).add(ex.schedule(r, ms, TimeUnit.MILLISECONDS)); return true; }
  public void removeCallbacks(Runnable r) { java.util.List<ScheduledFuture<?>> l = d.remove(r); if (l != null) for (ScheduledFuture<?> f : l) f.cancel(false); } }
