package ru.astrocode.sample;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import ru.astrocode.flm.FlowLayoutManager;

import java.util.*;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ActivityMain extends AppCompatActivity
    implements SeekBar.OnSeekBarChangeListener, View.OnClickListener, View.OnLongClickListener {

    private SeekBar mMaxLineBar;
    private FlowLayoutManager lm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView rv = findViewById(R.id.rv);
        rv.getRecycledViewPool().setMaxRecycledViews(0, 20);
        lm = ((FlowLayoutManager) rv.getLayoutManager()).lookBack(FlowLayoutManager.LookBack.EXACT);
        Adapter adapter = new Adapter(lm, Arrays.asList(getResources().getStringArray(R.array.Countries)));
        rv.setAdapter(adapter);

        mMaxLineBar = findViewById(R.id.maxLineBar);
        mMaxLineBar.setMax(24);
        mMaxLineBar.setOnSeekBarChangeListener(this);
        mMaxLineBar.setProgress(mMaxLineBar.getMax());

        CheckBox alpha = findViewById(R.id.alpha);
        alpha.setOnCheckedChangeListener((_cb, checked) -> {
            adapter.alphaIndex(checked);
        });
    }

    @Override public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        lm.maxLines(i == seekBar.getMax() ? Integer.MAX_VALUE : (i + 1), true, true);
    }
    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}

    static final Collection<Object> COLLECTION_OF_NULL = Collections.singleton(null);
    private final class Adapter extends RecyclerView.Adapter<VH> {
        private final FlowLayoutManager mLayoutManager;
        private final ArrayList<Object /* String | Character*/> mData;

        Adapter(FlowLayoutManager layoutManager, List<String> countries) {
            mData = new ArrayList<>(countries);
            mLayoutManager = layoutManager;
        }

        @Override public int getItemCount() {
            return mData.size() + 1;
        }
        private Object /* String | Character*/ getItemAt(int position) {
            return position == mData.size()
                ? mLayoutManager.ellipsisCount() + " more..."
                : mData.get(position);
        }

        private boolean index = false;
        void alphaIndex(boolean whether) {
            if (index != whether) {
                index = whether;
                if (whether) {
                    char last = '\0', current;
                    for (int i = 0, size = mData.size(); i < size; i++)
                        if ((current = ((String) mData.get(i)).charAt(0)) != last) {
                            mData.add(i, last = current);
                            notifyItemInserted(i++);
                        }
                } else {
                    int removed = 0;
                    for (int i = 0, size = mData.size(); i < size; i++) {
                        if (mData.get(i) instanceof Character) {
                            mData.set(i, null);
                            notifyItemRemoved(i - removed++);
                        }
                    }
                    mData.removeAll(COLLECTION_OF_NULL);
                }
            }
        }

        @Override public int getItemViewType(int position) {
            return getItemAt(position) instanceof String ? 0 : 1;
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return viewType == 0 ? chipHolder(parent) : titleHolder(parent);
        }
        private VH chipHolder(ViewGroup parent) {
            TextView title = new TextView(parent.getContext());
            title.setBackgroundResource(R.drawable.shape_chips);
            title.setGravity(Gravity.CENTER);
            title.setTextColor(Color.BLACK);
            title.setTextSize(12f);
            return new VH(title);
        }
        private VH titleHolder(ViewGroup parent) {
            TextView title = new TextView(parent.getContext());
            title.setTextColor(Color.BLACK);
            title.setTextSize(14f);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            lp.leftMargin = lp.rightMargin = parent.getResources().getDimensionPixelSize(R.dimen.spacing);
            title.setLayoutParams(lp);
            return new VH(title);
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            ((TextView) holder.itemView).setText(getItemAt(position).toString());
            boolean last = position == mData.size();
            holder.itemView.setOnClickListener(last ? ActivityMain.this : null);
            holder.itemView.setOnLongClickListener(last ? null : ActivityMain.this);
        }

        void removeAt(int position) {
            mData.remove(position);
            notifyItemRemoved(position);
        }
    }
    private static final class VH extends RecyclerView.ViewHolder {
        VH(View itemView) {
            super(itemView);
        }
    }

    @Override public void onClick(View view) {
        removeOrExpand(view);
    }
    @Override public boolean onLongClick(View view) {
        removeOrExpand(view);
        return true;
    }
    private void removeOrExpand(View view) {
        RecyclerView rv = (RecyclerView) view.getParent();
        RecyclerView.ViewHolder holder = rv.getChildViewHolder(view);
        int position = holder.getBindingAdapterPosition();
        Adapter adapter = (Adapter) holder.getBindingAdapter();
        if (position == adapter.getItemCount() - 1) {
            mMaxLineBar.setProgress(mMaxLineBar.getMax());
        } else if (position >= 0) {
            adapter.removeAt(position);
        }
    }
}
