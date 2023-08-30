package io.github.muntashirakon.adb;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public interface AdbChannel  extends Closeable {
    void readx(byte[] buffer, int length) throws IOException;

    void writex(byte[] buffer) throws IOException;

    boolean isConnected();

}
