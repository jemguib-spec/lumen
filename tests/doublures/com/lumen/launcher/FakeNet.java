// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh).
package com.lumen.launcher;
final class FakeNet { static java.net.InetAddress localAddress() { try { return java.net.InetAddress.getByName("127.0.0.1"); } catch (Exception e) { return null; } } }
