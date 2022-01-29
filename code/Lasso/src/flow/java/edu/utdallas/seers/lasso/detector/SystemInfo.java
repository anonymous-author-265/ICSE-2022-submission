package edu.utdallas.seers.lasso.detector;

import java.util.HashMap;

public class SystemInfo {

    private String systemName;
    private boolean hasExclusion;
    private boolean hasEntryPoint;
    private boolean oneCFABuilder;

    public SystemInfo(String systemName, boolean hasExclusion, boolean hasEntryPoint, boolean oneCFABuilder) {
        this.systemName = systemName;
        this.hasExclusion = hasExclusion;
        this.hasEntryPoint = hasEntryPoint;
        this.oneCFABuilder = oneCFABuilder;
    }

    public String getSystemName() {
        return systemName;
    }

    public boolean hasExclusion() {
        return hasExclusion;
    }

    public boolean hasEntryPoint() {
        return hasEntryPoint;
    }

    public boolean isOneCFABuilder() {
        return oneCFABuilder;
    }

    public static HashMap<String, SystemInfo> buildInfo() {
        HashMap<String, SystemInfo> hm = new HashMap<>();
        hm.put("ant", new SystemInfo("ant", true, false, true));
        hm.put("argouml", new SystemInfo("argouml", true, false, false));
        hm.put("guava", new SystemInfo("guava", true, false, true));
        hm.put("httpcore", new SystemInfo("httpcore", false, false, true));
        hm.put("itrust", new SystemInfo("itrust", false, true, true));
        hm.put("joda-time", new SystemInfo("joda-time", false, false, true));
        hm.put("jedit", new SystemInfo("jedit", false, false, true));
        hm.put("rhino", new SystemInfo("rhino", true, true, false));
        hm.put("swarm", new SystemInfo("swarm", true, false, true));
        hm.put("sample", new SystemInfo("sample", true, false, true));
        return hm;
    }
}
