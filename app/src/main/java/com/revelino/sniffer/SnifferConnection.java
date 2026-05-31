package com.revelino.sniffer;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.content.Context;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;

public class SnifferConnection implements SerialInputOutputManager.Listener {
    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onData(byte[] data, int len);
        void onError(Exception e);
    }

    private final Context context;
    private final Listener listener;
    private final SniffParser parser = new SniffParser();
    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;

    public SnifferConnection(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean connect(UsbDevice device) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            listener.onError(new Exception("No driver for device"));
            return false;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            listener.onError(new Exception("USB permission denied"));
            return false;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port.setDTR(true);
            port.setRTS(true);

            ioManager = new SerialInputOutputManager(port, this);
            ioManager.start();

            listener.onConnected();
            return true;
        } catch (IOException e) {
            listener.onError(e);
            return false;
        }
    }

    public void disconnect() {
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        if (port != null) {
            try {
                port.close();
            } catch (IOException ignored) {}
            port = null;
        }
        listener.onDisconnected();
    }

    @Override
    public void onNewData(byte[] data) {
        listener.onData(data, data.length);
    }

    @Override
    public void onRunError(Exception e) {
        listener.onError(e);
    }
}
