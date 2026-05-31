package com.revelino.sniffer;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.content.Context;
import android.util.Log;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;

public class SnifferConnection implements SerialInputOutputManager.Listener {
    private static final String TAG = "Sniffer";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onData(byte[] data, int len);
        void onError(Exception e);
        void onLog(String msg);
    }

    private final Context context;
    private final Listener listener;
    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;

    // Custom prober that explicitly maps ESP32-S3 VID/PID to CDC-ACM driver
    private static UsbSerialProber createProber() {
        ProbeTable table = new ProbeTable();
        table.addProduct(0x303A, 0x1001, CdcAcmSerialDriver.class); // ESP32-S3 USB-JTAG/Serial
        table.addProduct(0x303A, 0x4002, CdcAcmSerialDriver.class); // ESP32-S3 USB Single Serial (CDC-ACM)
        table.addProduct(0x10C4, 0xEA60, CdcAcmSerialDriver.class); // CP210x
        table.addProduct(0x1A86, 0x7523, CdcAcmSerialDriver.class); // CH340
        return new UsbSerialProber(table);
    }

    public SnifferConnection(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean connect(UsbDevice device) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // Check permission before attempting to open
        if (!usbManager.hasPermission(device)) {
            listener.onError(new Exception("No USB permission — request first"));
            return false;
        }

        log("Device: " + device.getProductName()
            + " VID=0x" + Integer.toHexString(device.getVendorId())
            + " PID=0x" + Integer.toHexString(device.getProductId())
            + " ifaces=" + device.getInterfaceCount());

        // Log all interfaces for diagnostics
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            log("  iface[" + i + "]: class=" + iface.getInterfaceClass()
                + " subclass=" + iface.getInterfaceSubclass()
                + " protocol=" + iface.getInterfaceProtocol()
                + " endpoints=" + iface.getEndpointCount());
        }

        // Try custom prober first (ESP32-S3 VID/PID), then default, then direct CDC-ACM
        UsbSerialDriver driver = createProber().probeDevice(device);
        log("Custom prober: " + (driver != null ? driver.getClass().getSimpleName() : "null"));

        if (driver == null) {
            driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            log("Default prober: " + (driver != null ? driver.getClass().getSimpleName() : "null"));
        }
        if (driver == null) {
            try {
                driver = new CdcAcmSerialDriver(device);
                log("Direct CDC-ACM fallback: ports=" + driver.getPorts().size());
            } catch (Exception e) {
                log("CDC-ACM fallback failed: " + e.getMessage());
            }
        }
        if (driver == null || driver.getPorts().isEmpty()) {
            listener.onError(new Exception("No serial ports on device"));
            return false;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            listener.onError(new Exception("Failed to open USB device — permission may have been revoked"));
            return false;
        }

        log("USB device opened, serial=" + connection.getSerial());

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            log("Port opened: " + port.getClass().getSimpleName());

            // CDC-ACM doesn't use baud rate for USB transfers, but set it anyway
            port.setParameters(115200, UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            // Signal DTR so ESP32 knows host is ready (usb_otg_stream_connected checks DTR)
            port.setDTR(true);
            port.setRTS(true);
            log("DTR/RTS set, starting IO manager");

            ioManager = new SerialInputOutputManager(port, this);
            ioManager.start();

            listener.onConnected();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to open serial port", e);
            listener.onError(new Exception("Port open failed: " + e.getMessage()));
            try { port.close(); } catch (IOException ignored) {}
            return false;
        }
    }

    public void disconnect() {
        log("Disconnecting");
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        if (port != null) {
            try { port.close(); } catch (IOException ignored) {}
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
        Log.e(TAG, "IO manager error", e);
        listener.onError(e);
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        listener.onLog(msg);
    }
}