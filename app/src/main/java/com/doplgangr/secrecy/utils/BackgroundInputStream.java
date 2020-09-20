package com.doplgangr.secrecy.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class BackgroundInputStream extends InputStream {
    private static final int DEFAULT_QUEUE_SIZE = 3;
    private static final int DEFAULT_BUFFER_SIZE = 512*1024;
    private final int queueSize;
    private final int bufferSize;
    private volatile boolean eof = false;
    private LinkedBlockingQueue<byte[]> bufferQueue;
    private final InputStream wrappedInputStream;
    private byte[] currentBuffer;
    private volatile byte[] freeBuffer;
    private int pos;

    public BackgroundInputStream(InputStream wrappedInputStream) {
        this(wrappedInputStream, DEFAULT_QUEUE_SIZE, DEFAULT_BUFFER_SIZE);
    }

    public BackgroundInputStream(InputStream wrappedInputStream,int queueSize,int bufferSize) {
        this.wrappedInputStream = wrappedInputStream;
        this.queueSize = queueSize;
        this.bufferSize = bufferSize;
    }

    @Override
    public int read() throws IOException {
        if (bufferQueue == null) {
            bufferQueue = new LinkedBlockingQueue<byte[]>(queueSize);
            BackgroundReader backgroundReader = new BackgroundReader();
            Thread backgroundReaderThread = new Thread(backgroundReader, "Background InputStream");
            backgroundReaderThread.start();
        }
        if (currentBuffer == null) {
            try {
                if ((!eof) || (bufferQueue.size() > 0)) {
                    currentBuffer = bufferQueue.take();
                    pos = 0;
                } else {
                    return -1;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        int b = currentBuffer[pos++];
        if (pos == currentBuffer.length) {
            freeBuffer = currentBuffer;
            currentBuffer = null;
        }
        return b;
    }


    @Override
    public int available() throws IOException {
        if (currentBuffer == null) return 0;
        return currentBuffer.length;
    }

    @Override
    public void close() throws IOException {
        wrappedInputStream.close();
        currentBuffer = null;
        freeBuffer = null;
    }

    class BackgroundReader implements Runnable {

        @Override
        public void run() {
            try {
                while (!eof) {
                    byte[] newBuffer;
                    if (freeBuffer != null) {
                        newBuffer = freeBuffer;
                        freeBuffer = null;
                    } else {
                        newBuffer = new byte[bufferSize];
                    }
                    int bytesRead = 0;
                    int writtenToBuffer = 0;
                    while (((bytesRead = wrappedInputStream.read(newBuffer, writtenToBuffer, bufferSize - writtenToBuffer)) != -1) && (writtenToBuffer < bufferSize)) {
                        writtenToBuffer += bytesRead;
                    }
                    if (writtenToBuffer > 0) {
                        if (writtenToBuffer < bufferSize) {
                            newBuffer = Arrays.copyOf(newBuffer, writtenToBuffer);
                        }
                        bufferQueue.put(newBuffer);
                    }
                    if (bytesRead == -1) {
                        eof = true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}