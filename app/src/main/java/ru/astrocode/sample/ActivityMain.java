package ru.astrocode.sample;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.RecyclerView;
import ru.astrocode.flm.FlowLayoutManager;

import java.util.ArrayList;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ActivityMain extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener {

    SeekBar mMaxLineBar;
    FlowLayoutManager lm =
        new FlowLayoutManager(FlowLayoutManager.VERTICAL, Gravity.CENTER)
            .lookBehind(Integer.MAX_VALUE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int spacing = getResources().getDimensionPixelSize(R.dimen.spacing);
        mMaxLineBar = new SeekBar(this);
        mMaxLineBar.setPadding(mMaxLineBar.getPaddingLeft(), spacing, mMaxLineBar.getPaddingRight(), spacing);
        mMaxLineBar.setMax(24);
        mMaxLineBar.setOnSeekBarChangeListener(this);
        mMaxLineBar.setProgress(mMaxLineBar.getMax());
        root.addView(mMaxLineBar);

        final RecyclerView recyclerView =
            (RecyclerView) getLayoutInflater().inflate(R.layout.recycler_with_scrollbars, null);
        root.addView(recyclerView, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));
        recyclerView.setPadding(spacing, 4 * spacing, spacing, 4 * spacing);
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutManager(lm.spacingBetweenItems(spacing).spacingBetweenLines(spacing));
        recyclerView.setAdapter(new Adapter(this, lm));
        setContentView(root);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        lm.maxLines(i == seekBar.getMax() ? Integer.MAX_VALUE : (i + 1), true, true);
    }
    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final int mBtnCloseSize, mBtnCloseLeftMargin;

        final ArrayList<Object /* String | Character*/> mData;
        private final FlowLayoutManager mLayoutManager;

        public Adapter(Context context, FlowLayoutManager layoutManager) {
            String[] countries = context.getResources().getStringArray(R.array.Countries);
            mData = new ArrayList<>(countries.length);
            char last = '\0';
            for (String country : countries) {
                if (country.charAt(0) != last) mData.add(last = country.charAt(0));
                mData.add(country);
            }
            this.mLayoutManager = layoutManager;
            float dp = context.getResources().getDisplayMetrics().density;
            mBtnCloseSize = Math.round(16 * dp);
            mBtnCloseLeftMargin = Math.round(5 * dp);
        }

        @Override
        public int getItemViewType(int position) {
            return position == mData.size() || mData.get(position) instanceof String ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return viewType == 0 ? chipHolder(parent) : titleHolder(parent);
        }
        private ChipHolder chipHolder(ViewGroup parent) {
            Context context = parent.getContext();

            LinearLayout view = new LinearLayout(context);
            view.setBackgroundResource(R.drawable.shape_chips);

            TextView title = new TextView(context);
            title.setGravity(Gravity.CENTER);
            title.setTextColor(Color.BLACK);
            title.setTextSize(12f);
            title.setId(R.id.textView);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);

            view.addView(title, lp);

            AppCompatImageButton imageButton = new AppCompatImageButton(context);
            imageButton.setBackgroundResource(R.drawable.shape_chips_close_btn);
            imageButton.setImageResource(R.drawable.ic_close);
            imageButton.setScaleType(ImageView.ScaleType.FIT_XY);
            imageButton.setId(R.id.imageButton);

            lp = new LinearLayout.LayoutParams(mBtnCloseSize, mBtnCloseSize);
            lp.leftMargin = mBtnCloseLeftMargin;

            view.addView(imageButton, lp);

            return new ChipHolder(view);
        }
        private RecyclerView.ViewHolder titleHolder(ViewGroup parent) {
            TextView title = new TextView(parent.getContext());
            title.setTextColor(Color.BLACK);
            title.setTextSize(14f);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            lp.leftMargin = lp.rightMargin = parent.getResources().getDimensionPixelSize(R.dimen.spacing);
            title.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(title) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof ChipHolder) {
                ((ChipHolder) holder).mText.setText(position == mData.size()
                    ? (mLayoutManager.ellipsisCount() + " more...")
                    : (String) mData.get(position));
            } else {
                ((TextView) holder.itemView).setText(mData.get(position).toString());
            }
        }

        @Override
        public int getItemCount() {
            return mData.size() + 1;
        }

        final class ChipHolder extends RecyclerView.ViewHolder {
            final TextView mText;

            public ChipHolder(View itemView) {
                super(itemView);
                mText = itemView.findViewById(R.id.textView);
                itemView.findViewById(R.id.imageButton).setOnClickListener(view -> {
                    int position = getBindingAdapterPosition();
                    if (position == mData.size()) {
                        mMaxLineBar.setProgress(mMaxLineBar.getMax());
                    } else if (position >= 0) {
                        mData.remove(position);
                        notifyItemRemoved(position);
                    }
                });
            }

        }
    }
}
