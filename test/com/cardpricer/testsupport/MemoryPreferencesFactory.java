package com.cardpricer.testsupport;
import java.util.*;
import java.util.prefs.*;
/** No test can open the workstation registry preferences. */
public final class MemoryPreferencesFactory implements PreferencesFactory {
    private static final Preferences ROOT=new Node(null,"");
    public Preferences systemRoot() { return ROOT; }
    public Preferences userRoot() { return ROOT; }
    private static final class Node extends AbstractPreferences {
        private final Map<String,String> data=new HashMap<>();
        Node(AbstractPreferences parent,String name) { super(parent,name); }
        protected void putSpi(String key,String value) {data.put(key,value);}
        protected String getSpi(String key) {return data.get(key);}
        protected void removeSpi(String key) {data.remove(key);}
        protected void removeNodeSpi() {data.clear();}
        protected String[] keysSpi() {return data.keySet().toArray(String[]::new);}
        protected String[] childrenNamesSpi() {return new String[0];}
        protected AbstractPreferences childSpi(String name) {return new Node(this,name);}
        protected void syncSpi() {}
        protected void flushSpi() {}
    }
}
