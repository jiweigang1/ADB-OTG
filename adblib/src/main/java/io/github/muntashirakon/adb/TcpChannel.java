package io.github.muntashirakon.adb;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TcpChannel implements AdbChannel {

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Socket getSocket() {
        return socket;
    }

    private  String host;
    private  int    port;

    /** The underlying socket that this class uses to communicate with the target device. */
    private Socket socket;

    /**
     * The input stream that this class uses to read from the socket.
     */
    private InputStream inputStream;

    /**
     * The output stream that this class uses to read from the socket.
     */
    private OutputStream outputStream;
    @Override
    public void close() throws IOException {
        socket.close();
    }

    /**
     * 读取指定长度的数据，或者等待，
     * @param buffer
     * @param length
     * @throws IOException
     */
    @Override
    public void readx(byte[] buffer, int length) throws IOException {

        int dataRead = 0;
        do {
            int bytesRead = inputStream.read(buffer, dataRead, length - dataRead);

            if (bytesRead < 0)
                throw new IOException("Stream closed");
            else
                dataRead += bytesRead;
        }
        while (dataRead < length);
    }

    @Override
    public void writex(byte[] buffer) throws IOException {
        outputStream.write(buffer);
        outputStream.flush();
    }

    @Override
    public boolean isConnected() {
        return !socket.isClosed() && socket.isConnected();
    }

    public  TcpChannel(@NonNull String host, int port){
        this.host = host;
        this.port = port;
        try {
            this.socket = new Socket(host, port);
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } catch (Throwable th) {
            //noinspection UnnecessaryInitCause
            throw new RuntimeException(th);
        }
    }

}