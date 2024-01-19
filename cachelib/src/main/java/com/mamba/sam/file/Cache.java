package com.mamba.sam.file;

import com.mamba.sam.MediaCacheException;

/**
 * media cache.
 **/
public interface Cache {

    long available() throws MediaCacheException;

    int read(byte[] buffer, long offset, int length) throws MediaCacheException;

    void append(byte[] data, int length) throws MediaCacheException;

    void close() throws MediaCacheException;

    void complete() throws MediaCacheException;

    boolean isCompleted();
}
