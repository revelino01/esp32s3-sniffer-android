package com.revelino.sniffer;

public class SniffPacket {
    public final int channel;
    public final int rssi;
    public final int rate;
    public final int length;
    public final long timestamp;
    public final byte[] payload;
    public final String macAddr;

    public SniffPacket(int channel, int rssi, int rate, int length,
                       long timestamp, byte[] payload) {
        this.channel = channel;
        this.rssi = rssi;
        this.rate = rate;
        this.length = length;
        this.timestamp = timestamp;
        this.payload = payload;
        this.macAddr = extractMac(payload);
    }

    private static String extractMac(byte[] data) {
        if (data.length >= 12) {
            return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                data[4], data[5], data[6], data[7], data[8], data[9]);
        }
        return "??";
    }
}
