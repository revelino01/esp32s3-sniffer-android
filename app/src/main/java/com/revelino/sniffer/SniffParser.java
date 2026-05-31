package com.revelino.sniffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SniffParser {
    private static final int SNFF_MAGIC = 0x534E4646;
    private static final int HEADER_SIZE = 16;

    private final ByteBuffer buffer = ByteBuffer.allocate(4096);

    public SniffParser() {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public synchronized SniffPacket[] feed(byte[] data, int len) {
        buffer.put(data, 0, len);
        buffer.flip();

        java.util.List<SniffPacket> packets = new java.util.ArrayList<>();

        while (buffer.remaining() >= HEADER_SIZE) {
            buffer.mark();
            int magic = buffer.getInt();
            if (magic != SNFF_MAGIC) {
                buffer.reset();
                buffer.position(buffer.position() + 1);
                continue;
            }

            short version = buffer.getShort();
            short channel = buffer.getShort();
            byte rssi = buffer.get();
            byte rate = buffer.get();
            int timestamp = buffer.getInt();
            short dataLen = buffer.getShort();

            if (buffer.remaining() < dataLen) {
                buffer.reset();
                break;
            }

            byte[] payload = new byte[dataLen];
            buffer.get(payload);
            packets.add(new SniffPacket(channel, rssi, rate, dataLen,
                timestamp & 0xFFFFFFFFL, payload));
        }

        buffer.compact();
        return packets.toArray(new SniffPacket[0]);
    }
}
