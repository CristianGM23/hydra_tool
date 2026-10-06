package com.example.editor.event.bus;
/** Suscripción cancelable a un bus de eventos. */
@FunctionalInterface
public interface EventSubscription extends AutoCloseable { @Override void close(); }
