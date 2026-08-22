package net.klaaswhite.c2w.bootstrap.listeners;

// Dummy stand-in for the NMS/Craft world classes so ProtocolLib's
// BukkitConverters static initializer can find a matching field without
// a real CraftBukkit server on the classpath.
public class DummyWorld {
    @SuppressWarnings("unused")
    public DummyWorld craftWorld;
}
