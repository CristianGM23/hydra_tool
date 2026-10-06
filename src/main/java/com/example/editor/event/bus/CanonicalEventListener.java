package com.example.editor.event.bus;
import com.example.editor.event.CanonicalEvent;
@FunctionalInterface
public interface CanonicalEventListener { void onEvent(CanonicalEvent event); }
