// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh) : le strict nécessaire d'Android pour faire tourner deux TV dans un même programme.
package android.net.nsd;
public class NsdManager { public static final int PROTOCOL_DNS_SD = 1;
  public interface RegistrationListener { void onRegistrationFailed(NsdServiceInfo i, int e); void onUnregistrationFailed(NsdServiceInfo i, int e); void onServiceRegistered(NsdServiceInfo i); void onServiceUnregistered(NsdServiceInfo i); }
  public interface DiscoveryListener { void onStartDiscoveryFailed(String t, int e); void onStopDiscoveryFailed(String t, int e); void onDiscoveryStarted(String t); void onDiscoveryStopped(String t); void onServiceFound(NsdServiceInfo i); void onServiceLost(NsdServiceInfo i); }
  public interface ResolveListener { void onResolveFailed(NsdServiceInfo i, int e); void onServiceResolved(NsdServiceInfo i); }
  public void registerService(NsdServiceInfo i, int p, RegistrationListener l) { } public void unregisterService(RegistrationListener l) { }
  public void discoverServices(String t, int p, DiscoveryListener l) { l.onStartDiscoveryFailed(t, 0); } public void stopServiceDiscovery(DiscoveryListener l) { }
  public void resolveService(NsdServiceInfo i, ResolveListener l) { } }
