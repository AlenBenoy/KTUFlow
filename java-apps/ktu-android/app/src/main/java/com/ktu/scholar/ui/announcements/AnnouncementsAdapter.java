package com.ktu.scholar.ui.announcements;

import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.ktu.scholar.R;
import com.ktu.scholar.data.db.AnnouncementEntity;

import java.util.List;

public class AnnouncementsAdapter extends RecyclerView.Adapter<AnnouncementsAdapter.VH> {

    private List<AnnouncementEntity> data;

    public AnnouncementsAdapter(List<AnnouncementEntity> data) { this.data = data; }

    public void submitList(List<AnnouncementEntity> list) {
        this.data = list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_announcement, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AnnouncementEntity a = data.get(pos);
        h.tvTitle.setText(a.title);
        h.tvDate.setText(a.date != null ? a.date : "");
        h.tvCategory.setText(a.category != null ? a.category : "GENERAL");

        if (a.link != null && !a.link.isEmpty()) {
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(a.link));
                v.getContext().startActivity(i);
            });
        }
    }

    @Override public int getItemCount() { return data == null ? 0 : data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvCategory;

        VH(View v) {
            super(v);
            tvTitle    = v.findViewById(R.id.tv_title);
            tvDate     = v.findViewById(R.id.tv_date);
            tvCategory = v.findViewById(R.id.tv_category);
        }
    }
}
