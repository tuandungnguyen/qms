package com.ntd.qms.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.ntd.qms.R;
import com.ntd.qms.data.DeviceItem;

import java.util.List;
import java.util.Locale;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
    private AsyncListDiffer<DeviceItem> listDevices = new AsyncListDiffer<>(this, new DIFFER_CALLBACK());
    private ClickListener clickListener;
    private Context context;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView text1, text2;

        public ViewHolder(View v) {
            super(v);
            text1 = v.findViewById(R.id.text1);
            text2 = v.findViewById(R.id.text2);
        }
    }

    public DeviceAdapter(Context context, ClickListener mClickListener) {
        this.context = context;
        this.clickListener = mClickListener;
    }

    @Override
    public DeviceAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_usb_device, parent, false);

        return new ViewHolder(itemView);
    }


    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {

        final List<DeviceItem> currentList = listDevices.getCurrentList();

        DeviceItem item = currentList.get(position);

        if(item.getDriver() == null)
            holder.text1.setText("<no driver>");
        else if(item.getDriver().getPorts().size() == 1)
            holder.text1.setText(item.getDriver().getClass().getSimpleName().replace("SerialDriver",""));
        else
            holder.text1.setText(item.getDriver().getClass().getSimpleName().replace("SerialDriver","")+", Port "+item.getPort());

        holder.text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.getDevice().getVendorId(), item.getDevice().getProductId()));


        holder.itemView.setOnClickListener(view -> {
            clickListener.onSelectDevice(currentList.get(position));
        });
    }

    @Override
    public int getItemCount() {
        return listDevices.getCurrentList().size();
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(position);
    }


    public interface ClickListener {
        void onSelectDevice(DeviceItem item);
    }

    public AsyncListDiffer<DeviceItem> getDiffer() {
        return listDevices;
    }

    static class DIFFER_CALLBACK extends DiffUtil.ItemCallback<DeviceItem> {

        @Override
        public boolean areItemsTheSame(DeviceItem oldItem, DeviceItem newItem) {
            return (oldItem == newItem);
        }

        @Override
        public boolean areContentsTheSame(DeviceItem oldItem, DeviceItem newItem) {
            return (oldItem == newItem);
        }
    }


}
