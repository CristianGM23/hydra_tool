package com.hydra.sirius.fixture.design;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

/**
 * Intentionally small AQL/Java service surface for HYDRA import tests.
 * The implementation avoids mutable global state and side effects.
 */
public final class Services {

    public String safeLabel(EObject self) {
        if (self == null || self.eClass() == null) {
            return "<null>";
        }
        Object value = self.eGet(self.eClass().getEStructuralFeature("name"));
        String name = value == null ? "" : String.valueOf(value).trim();
        return name.isEmpty() ? "<unnamed " + self.eClass().getName() + ">" : name;
    }

    public boolean hasUniquePortNames(EObject task) {
        if (task == null || task.eClass().getEStructuralFeature("ports") == null) {
            return true;
        }
        Object raw = task.eGet(task.eClass().getEStructuralFeature("ports"));
        if (!(raw instanceof Iterable<?> values)) {
            return true;
        }
        Set<String> seen = new HashSet<>();
        for (Object value : values) {
            if (value instanceof EObject port && port.eClass().getEStructuralFeature("name") != null) {
                Object n = port.eGet(port.eClass().getEStructuralFeature("name"));
                String name = n == null ? "" : n.toString();
                if (!seen.add(name)) {
                    return false;
                }
            }
        }
        return true;
    }
}
