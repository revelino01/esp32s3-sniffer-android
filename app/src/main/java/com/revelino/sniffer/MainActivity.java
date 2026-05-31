package com.revelino.sniffer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION = "com.revelino.sniffer.USB_PERMISSION";

    private TextView tvStatus, tvPacketCount, tvChannel, tvLog;
    private Button btnConnect;
    private RecyclerView recycler;
    private PacketAdapter adapter;
    private SnifferConnection conn;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger totalPackets = new AtomicInteger(0);
    private volatile int currentChannel = 0;
    private final SniffParser parser = new SniffParser();

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectDevice(device);
                        }
                    } else {
                        log("USB permission denied");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) requestPermission(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (conn != null) conn.disconnect();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvPacketCount = findViewById(R.id.tvPacketCount);
        tvChannel = findViewById(R.id.tvChannel);
        tvLog = findViewById(R.id.tvLog);
        btnConnect = findViewById(R.id.btnConnect);
        recycler = findViewById(R.id.recyclerPackets);

        adapter = new PacketAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnConnect.setOnClickListener(v -> {
            if (conn != null && isConnected()) {
                conn.disconnect();
            } else {
                findAndConnect();
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        conn = new SnifferConnection(this, new SnifferConnection.Listener() {
            @Override public void onConnected() {
                uiHandler.post(() -> {
                    tvStatus.setText("Connected");
                    tvStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.online));
                    btnConnect.setText(R.string.disconnect);
                });
            }
            @Override public void onDisconnected() {
                uiHandler.post(() -> {
                    tvStatus.setText("Disconnected");
                    tvStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.offline));
                    btnConnect.setText(R.string.connect);
                });
            }
            @Override public void onData(byte[] data, int len) {
                SniffPacket[] pkts = parser.feed(data, len);
                for (SniffPacket p : pkts) {
                    totalPackets.incrementAndGet();
                    currentChannel = p.channel;
                    uiHandler.post(() -> {
                        adapter.addPacket(p);
                        tvPacketCount.setText(String.valueOf(totalPackets.get()));
                        tvChannel.setText(String.valueOf(currentChannel));
                    });
                }
            }
            @Override public void onError(Exception e) {
                uiHandler.post(() -> log("Error: " + e.getMessage()));
            }
        });

        tvLog.post(this::findAndConnect);
    }

    private void findAndConnect() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (isEsp32s3(device)) {
                requestPermission(device);
                return;
            }
        }
        log("No ESP32-S3 found. Connect via USB OTG.");
    }

    private boolean isEsp32s3(UsbDevice d) {
        return d.getVendorId() == 0x303A || d.getVendorId() == 0x10C4;
    }

    private void requestPermission(UsbDevice device) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
            new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(device, pi);
    }

    private void connectDevice(UsbDevice device) {
        if (conn.connect(device)) {
            log("Connected to " + device.getProductName());
        }
    }

    private boolean isConnected() {
        return tvStatus.getText().toString().equals("Connected");
    }

    private void log(String msg) {
        uiHandler.post(() -> {
            tvLog.append(msg + "\n");
            if (tvLog.getLayout() != null && tvLog.getLineCount() > 0) {
                int scroll = tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
                if (scroll > 0) tvLog.scrollTo(0, scroll);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        if (conn != null) conn.disconnect();
    }
}
