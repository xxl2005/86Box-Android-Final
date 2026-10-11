package net.box86.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent ownership of game keys by Android pointer ID. */
final class KeyTouchTracker {
    private final Map<Integer, String> owners = new LinkedHashMap<>();

    boolean owns(int pointerId) { return owners.containsKey(pointerId); }

    boolean capture(int pointerId, String keyId) {
        if (keyId == null || owners.containsKey(pointerId)) return false;
        owners.put(pointerId, keyId);
        return true;
    }

    String release(int pointerId) { return owners.remove(pointerId); }

    boolean anyPointerOnKey(String keyId) { return owners.containsValue(keyId); }

    List<Integer> pointerIds() { return new ArrayList<>(owners.keySet()); }

    void clear() { owners.clear(); }
}
