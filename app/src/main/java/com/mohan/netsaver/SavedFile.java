package com.mohan.netsaver;

import java.io.File;

public class SavedFile {
    public final File   file;
    public final String displayName;
    public final long   sizeBytes;

    public SavedFile(File file) {
        this.file        = file;
        this.displayName = file.getName();
        this.sizeBytes   = file.length();
    }

    public String getFormattedSize() {
        if (sizeBytes < 1024)           return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024)    return String.format("%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1024L * 1024 * 1024)
                                        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        return String.format("%.2f GB", sizeBytes / (1024.0 * 1024 * 1024));
    }
}
