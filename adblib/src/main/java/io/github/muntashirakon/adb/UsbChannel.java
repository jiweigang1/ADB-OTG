package io.github.muntashirakon.adb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.cgutman.adblib.AdbMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

public class UsbChannel implements AdbChannel {

    private final UsbDeviceConnection mDeviceConnection;
    private final UsbEndpoint mEndpointOut;
    private final UsbEndpoint mEndpointIn;
    private final UsbInterface mInterface;

    private final int defaultTimeout = 1000;

    private final LinkedList<UsbRequest> mInRequestPool = new LinkedList<UsbRequest>();

    // return an IN request to the pool
    public void releaseInRequest(UsbRequest request) {
        synchronized (mInRequestPool) {
            mInRequestPool.add(request);
        }
    }


    // get an IN request from the pool
    public UsbRequest getInRequest() {
        synchronized (mInRequestPool) {
            if (mInRequestPool.isEmpty()) {
                UsbRequest request = new UsbRequest();
                request.initialize(mDeviceConnection, mEndpointIn);
                return request;
            } else {
                return mInRequestPool.removeFirst();
            }
        }
    }

    @Override
    public void readx(byte[] buffer, int length) throws IOException {


        UsbRequest usbRequest = getInRequest();

        ByteBuffer expected = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        usbRequest.setClientData(expected);

        if (!usbRequest.queue(expected, length)) {
            throw new IOException("fail to queue read UsbRequest");
        }

        while (true) {
            Log.d("","readA:" + length);
            UsbRequest wait = mDeviceConnection.requestWait();
            if (wait == null) {
                throw new IOException("Connection.requestWait return null");
            }
            Log.d("","readB:" + length);
            ByteBuffer clientData = (ByteBuffer) wait.getClientData();
            wait.setClientData(null);

            if (wait.getEndpoint() == mEndpointOut) {
                // a write UsbRequest complete, just ignore
                // 是输入流，并且和发送的是同一个对象
            } else if (expected == clientData) {
                releaseInRequest(wait);
                break;

            } else {
                throw new IOException("unexpected behavior");
            }
        }

        expected.flip();
        expected.get(buffer);
    }

    public void writex(byte[] buffer) throws IOException{
        //这里有个坑，如果超出 header 长度的数据直接一起发出去，无法触发 ADB 调试，这里先发送 header 数据，再发送body数据
        if(buffer.length>24){
            byte[] header = new byte[24];
            System.arraycopy(buffer, 0, header, 0, 24);
            byte[] plod   = new byte[buffer.length - 24];
            System.arraycopy(buffer, header.length, plod, 0, buffer.length - header.length);
            writex0(header);
            writex0(plod);
        }else {
            writex0(buffer);
        }
    }
    // API LEVEL 18 is needed to invoke bulkTransfer(mEndpointOut, buffer, offset, buffer.length - offset, defaultTimeout)
//    @Override

    private void writex0(byte[] buffer) throws IOException {
        Log.d("ADB","to send   bytes = " + buffer.length );

        int offset = 0;
        int transferred = 0;

        byte[] tmp = new byte[buffer.length];
        System.arraycopy(buffer, 0, tmp, 0, buffer.length);

        while ((transferred = mDeviceConnection.bulkTransfer(mEndpointOut, tmp, buffer.length - offset, defaultTimeout)) >= 0) {
            offset += transferred;
            Log.d("ADB","send one  bytes = " + transferred );
            if (offset >= buffer.length) {
                break;
            } else {
                System.arraycopy(buffer, offset, tmp, 0, buffer.length - offset);
            }
        }
        if (transferred < 0) {
            throw new IOException("bulk transfer fail");
        }
        Log.d("ADB","all  send   bytes = " + transferred );


    }

   // @Override
    public void writex(AdbProtocol.Message message) throws IOException {
        // TODO: here is the weirdest thing
        // write (message.head + message.payload) is totally different with write(message.head) + write(head.payload)
        writex(message.getHeader());
        if (message.getPayload() != null) {
            writex(message.getPayload());
        }
    }


    /**
     * 是否连接 ADB 这里是不能判断的
     * @return
     */
    @Override
    public boolean isConnected() {
        return mDeviceConnection != null ;
    }


    @Override
    public void close() throws IOException {
        Log.d("","close--------------------");
        mDeviceConnection.releaseInterface(mInterface);
        mDeviceConnection.close();
    }

    public UsbChannel(UsbDeviceConnection connection, UsbInterface intf) {
        mDeviceConnection = connection;
        mInterface = intf;
        UsbEndpoint epOut = null;
        UsbEndpoint epIn = null;
        // look for our bulk endpoints
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            Log.d("Nightmare", "ep -> " + ep);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    epOut = ep;
                } else {
                    epIn = ep;
                }
            }
        }
        if (epOut == null || epIn == null) {
            throw new IllegalArgumentException("not all endpoints found");
        }
        mEndpointOut = epOut;
        Log.d("Nightmare", epOut + "");
        mEndpointIn = epIn;
    }

}