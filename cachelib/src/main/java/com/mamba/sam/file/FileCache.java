package com.mamba.sam.file;


import com.mamba.sam.MediaCacheException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * {@link Cache} that uses file for storing media file.
 *
 */
public class FileCache implements Cache {

    private static final String TEMP_POSTFIX = ".download";

    private final DiskUsage diskUsage;
    public File file;
    private RandomAccessFile dataFile;

    public FileCache(File file) throws MediaCacheException {
        this(file, new UnlimitedDiskUsage());
    }

    public FileCache(File file, DiskUsage diskUsage) throws MediaCacheException {
        try {
            if (diskUsage == null) {
                throw new NullPointerException();
            }
            this.diskUsage = diskUsage;
            File directory = file.getParentFile();
            Files.makeDir(directory);
            boolean completed = file.exists();
            this.file = completed ? file : new File(file.getParentFile(), file.getName() + TEMP_POSTFIX);
            this.dataFile = new RandomAccessFile(this.file, completed ? "r" : "rw");
        } catch (IOException e) {
            throw new MediaCacheException("Error using file " + file + " as disc cache", e);
        }
    }

    @Override
    public synchronized long available() throws MediaCacheException {
        try {
            return (int) dataFile.length();
        } catch (IOException e) {
            throw new MediaCacheException("Error reading length of file " + file, e);
        }
    }

    @Override
    public synchronized int read(byte[] buffer, long offset, int length) throws MediaCacheException {
        try {
            dataFile.seek(offset);
            return dataFile.read(buffer, 0, length);
        } catch (IOException e) {
            String format = "Error reading %d bytes with offset %d from file[%d bytes] to buffer[%d bytes]";
            throw new MediaCacheException(String.format(format, length, offset, available(), buffer.length), e);
        }
    }

    @Override
    public synchronized void append(byte[] data, int length) throws MediaCacheException {
        try {
            if (isCompleted()) {
                throw new MediaCacheException("Error append cache: cache file " + file + " is completed!");
            }
            dataFile.seek(available());
            dataFile.write(data, 0, length);
        } catch (IOException e) {
            String format = "Error writing %d bytes to %s from buffer with size %d";
            throw new MediaCacheException(String.format(format, length, dataFile, data.length), e);
        }
    }

    @Override
    public synchronized void close() throws MediaCacheException {
        try {
            dataFile.close();
            diskUsage.touch(file);
        } catch (IOException e) {
            throw new MediaCacheException("Error closing file " + file, e);
        }
    }

    @Override
    public synchronized void complete() throws MediaCacheException {
        if (isCompleted()) {
            return;
        }

        close();
        String fileName = file.getName().substring(0, file.getName().length() - TEMP_POSTFIX.length());
        File completedFile = new File(file.getParentFile(), fileName);
        boolean renamed = file.renameTo(completedFile);
        if (!renamed) {
            throw new MediaCacheException("Error renaming file " + file + " to " + completedFile + " for completion!");
        }
        file = completedFile;
        try {
            dataFile = new RandomAccessFile(file, "r");
            diskUsage.touch(file);
        } catch (IOException e) {
            throw new MediaCacheException("Error opening " + file + " as disc cache", e);
        }
    }

    @Override
    public synchronized boolean isCompleted() {
        return !isTempFile(file);
    }

    /**
     * Returns file to be used fo caching. It may as original file passed in constructor as some temp file for not completed cache.
     *
     * @return file for caching.
     */
    public File getFile() {
        return file;
    }

    private boolean isTempFile(File file) {
        return file.getName().endsWith(TEMP_POSTFIX);
    }

}
