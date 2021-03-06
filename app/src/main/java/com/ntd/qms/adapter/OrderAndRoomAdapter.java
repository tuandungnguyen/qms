package com.ntd.qms.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.common.util.ArrayUtils;
import com.ntd.qms.MainActivity;
import com.ntd.qms.R;
import com.ntd.qms.data.OrderAndRoomItem;
import com.ntd.qms.util.Utils;

import java.util.List;

import static android.content.Context.MODE_PRIVATE;

public class OrderAndRoomAdapter extends RecyclerView.Adapter<OrderAndRoomAdapter.ViewHolder> {
    private AsyncListDiffer<OrderAndRoomItem> listItems = new AsyncListDiffer<>(this, new DIFFER_CALLBACK());

    private Context context;
    private int typeColumn;

    private static int TYPE_1_COLUMN = 1;

    private static int BACKGROUND_WHITE = 1;
    private static int BACKGROUND_GREY = 2;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvOrder, tvRoomName;
        public ImageView imvDirectionArrow;

        public ViewHolder(View v) {
            super(v);
            tvOrder = v.findViewById(R.id.tvOrder);
            tvRoomName = v.findViewById(R.id.tvRoomName);
            imvDirectionArrow = v.findViewById(R.id.imvDirectionArrow);
        }
    }

    public OrderAndRoomAdapter(Context context, int mTypeColumn) {
        this.context = context;
        this.typeColumn = mTypeColumn;
    }


  /*  @Override
    public int getItemViewType(int position) {
        int number = getDiffer().getCurrentList().get(position).getQueueNumber();
        try {
            if (typeColumn == TYPE_1_COLUMN) {
                if (number % 2 == 1)
                    return BACKGROUND_GREY;
                else return BACKGROUND_WHITE;
            } else {
                if (ArrayUtils.contains(new int[]{0, 1, 4, 5, 8, 9, 12, 13}, position)) {
                    return BACKGROUND_WHITE;
                } else {
                    return BACKGROUND_GREY;
                }
            }
        } catch (Exception ex) {
            return BACKGROUND_WHITE;
        }
    }*/

    @Override
    public OrderAndRoomAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_and_room, parent, false);

        GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) itemView.getLayoutParams();
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.MY_PREFS_NAME, MODE_PRIVATE);
        int lines = prefs.getInt(MainActivity.KEY_LINE_NUMBER, 3);
        lp.height = parent.getMeasuredHeight() / lines;
        itemView.setLayoutParams(lp);
/*
        if (viewType == BACKGROUND_WHITE)
            itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_grey));
        else
            itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.dark_grey));*/

        return new ViewHolder(itemView);
    }


    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {

        final List<OrderAndRoomItem> currentList = listItems.getCurrentList();

        OrderAndRoomItem item = currentList.get(position);

        holder.tvOrder.setText(Utils.formatQueueNumber(item.getQueueNumber(), 4));

        holder.tvRoomName.setText(String.valueOf(item.getRoom()));

        if (getItemCount() > 0 && position == getItemCount() - 1) {
            holder.tvOrder.setAnimation(AnimationUtils.loadAnimation(context, R.anim.blink));
            holder.tvRoomName.setAnimation(AnimationUtils.loadAnimation(context, R.anim.blink));
        }

        try {
            switch (item.getDirection()) {

                case 0:
                    holder.imvDirectionArrow.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_left));
                    break;

                case 1:
                    holder.imvDirectionArrow.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_right));
                    break;

                case 2:
                    holder.imvDirectionArrow.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_up));
                    break;

                case 3:
                    holder.imvDirectionArrow.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_down));
                    break;

                default:
                    throw new IllegalStateException("Unexpected value: " + item.getDirection());
            }

        } catch (Exception ex) {

        }
    }

    @Override
    public int getItemCount() {
        return listItems.getCurrentList().size();
    }

    public AsyncListDiffer<OrderAndRoomItem> getDiffer() {
        return listItems;
    }

    static class DIFFER_CALLBACK extends DiffUtil.ItemCallback<OrderAndRoomItem> {

        @Override
        public boolean areItemsTheSame(OrderAndRoomItem oldItem, OrderAndRoomItem newItem) {
            return (oldItem.getRoom() == newItem.getRoom());
        }

        @Override
        public boolean areContentsTheSame(OrderAndRoomItem oldItem, OrderAndRoomItem newItem) {
            return (oldItem.getDirection() == newItem.getDirection()) && (oldItem.getQueueNumber() == newItem.getDirection());
        }
    }


}
