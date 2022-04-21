package ru.astrocode.sample;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.RecyclerView;
import ru.astrocode.flm.FLMFlowLayoutManager;

import java.util.ArrayList;
import java.util.Arrays;

public class ActivityMain extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final RecyclerView recyclerView = new RecyclerView(this);
        int spacing = getResources().getDimensionPixelSize(R.dimen.spacing);
        recyclerView.setPadding(spacing, spacing, spacing, spacing);
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutManager(
            new FLMFlowLayoutManager(FLMFlowLayoutManager.VERTICAL, Gravity.CENTER, spacing, spacing)
        );
        recyclerView.setAdapter(new Adapter(this));
        setContentView(recyclerView);
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
        private final int mBtnCloseSize, mBtnCloseLeftMargin;

        final ArrayList<String> mData;

        public Adapter(Context context) {
            mData = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.Countries)));
            float dp = context.getResources().getDisplayMetrics().density;
            mBtnCloseSize = Math.round(16 * dp);
            mBtnCloseLeftMargin = Math.round(5 * dp);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();

            LinearLayout view = new LinearLayout(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            view.setBackgroundResource(R.drawable.shape_chips);

            TextView title = new TextView(context);
            title.setGravity(Gravity.CENTER);
            title.setTextColor(Color.BLACK);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            title.setId(R.id.textView);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

            view.addView(title, lp);

            AppCompatImageButton imageButton = new AppCompatImageButton(context);
            imageButton.setBackgroundResource(R.drawable.shape_chips_close_btn);
            imageButton.setImageResource(R.drawable.ic_close);
            imageButton.setScaleType(ImageView.ScaleType.FIT_XY);
            imageButton.setId(R.id.imageButton);

            lp = new LinearLayout.LayoutParams(mBtnCloseSize, mBtnCloseSize);
            lp.leftMargin = mBtnCloseLeftMargin;

            view.addView(imageButton, lp);

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Adapter.ViewHolder holder, int position) {
            holder.mText.setText(mData.get(position));
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView mText;

            public ViewHolder(View itemView) {
                super(itemView);
                mText = itemView.findViewById(R.id.textView);
                itemView.findViewById(R.id.imageButton).setOnClickListener(view -> {
                    int position = getBindingAdapterPosition();
                    if (position >= 0) {
                        mData.remove(position);
                        notifyItemRemoved(position);
                    }
                });
            }

        }
    }
}
