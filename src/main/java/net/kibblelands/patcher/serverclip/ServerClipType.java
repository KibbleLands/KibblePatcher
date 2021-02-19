package net.kibblelands.patcher.serverclip;

import java.io.File;

public enum ServerClipType {

    PAPERCLIP("Paperclip", "cache" + File.separator + "patched_" + ServerClipSupport.MC_VER + ".jar", false),
    YATOCLIP("Yatoclip", "cache" + File.separator + ServerClipSupport.MC_VER + File.separator +
            "Minecraft" + File.separator + ServerClipSupport.MC_VER + "-patched.jar", false),
    PAPERCLIP_LEGACY("Paperclip", new String[]{
            "cache" + File.separator + "patched_" + ServerClipSupport.MC_VER + ".jar",
            "cache" + File.separator + "patched.jar"}, true);

    private final String displayName;
    private final String[] fileSuffixes;
    // If true, the serverClip will use thread hax to force patch only
    private final boolean needHax;

    ServerClipType(String displayName, String fileSuffix, boolean needHax) {
        this(displayName, new String[]{fileSuffix}, needHax);
    }

    ServerClipType(String displayName, String[] fileSuffixes, boolean needHax) {
        this.displayName = displayName;
        this.fileSuffixes = fileSuffixes;
        this.needHax = needHax;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String[] getFileSuffixes() {
        return fileSuffixes;
    }

    public boolean isNeedHax() {
        return needHax;
    }
}