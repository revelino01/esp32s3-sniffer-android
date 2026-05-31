package com.revelino.sniffer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PacketAdapter extends RecyclerView.Adapter<PacketAdapter.ViewHolder> {
    private final List<SniffPacket> packets = new ArrayList<>();

    public void addPacket(SniffPacket p) {
        packets.add(0, p);
        if (packets.size() > 100) packets.remove(packets.size() - 1);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_packet, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        SniffPacket p = packets.get(pos);
        h.tvCh.setText(String.format("%02d", p.channel));
        h.tvRssi.setText(String.format("%d", p.rssi));
        h.tvLen.setText(String.valueOf(p.length));
        h.tvMac.setText(p.macAddr);
    }

    @Override
    public int getItemCount() {
        return packets.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCh, tvRssi, tvLen, tvMac;
        ViewHolder(View v) {
            super(v);
            tvCh = v.findViewById(R.id.tvCh);
            tvRssi = v.findViewById(R.id.tvRssi);
            tvLen = v.findViewById(R.id.tvLen);
            tvMac = v.findViewById(R.id.tvMac);
        }
    }
}
