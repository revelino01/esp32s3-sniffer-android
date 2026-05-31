package com.revelino.sniffer;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.content.Context;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
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
    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;

    public SnifferConnection(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean connect(UsbDevice device) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // Try the default prober first
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            // Fallback: try CDC-ACM directly
            driver = new CdcAcmSerialDriver(device);
            if (driver.getPorts().isEmpty()) {
                listener.onError(new Exception(
                    "No driver for " + device.getProductName() +
                    " VID:0x" + Integer.toHexString(device.getVendorId()) +
                    " PID:0x" + Integer.toHexString(device.getProductId())));
                return false;
            }
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
            listener.onError(new Exception("Failed to open port: " + e.getMessage()));
            try { port.close(); } catch (IOException ignored) {}
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
