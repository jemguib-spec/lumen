// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh) : le strict nécessaire d'Android pour faire tourner deux TV dans un même programme.
package android.net.nsd;
public class NsdServiceInfo { String n, t; int p; java.net.InetAddress h;
  public void setServiceName(String s) { n = s; } public void setServiceType(String s) { t = s; } public void setPort(int x) { p = x; }
  public String getServiceName() { return n; } public String getServiceType() { return t; } public int getPort() { return p; } public java.net.InetAddress getHost() { return h; } }
