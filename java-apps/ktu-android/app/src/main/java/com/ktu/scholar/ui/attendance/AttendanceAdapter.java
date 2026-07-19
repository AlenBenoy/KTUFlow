package com.ktu.scholar.ui.attendance;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.ktu.scholar.R;
import com.ktu.scholar.core.model.SubjectAttendance;

import java.util.List;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.VH> {

    private List<SubjectAttendance> data;

    public void submitList(List<SubjectAttendance> list) {
        this.data = list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SubjectAttendance s = data.get(pos);
        h.tvName.setText(s.getSubjectCode() + "  " + s.getSubjectName());
        h.tvPct.setText(String.format("%.1f%%  (%d/%d)", s.getPercentage(),
                s.getAttendedClasses(), s.getTotalClasses()));
        h.pbBar.setProgress((int) s.getPercentage());
        h.tvStatus.setText(s.getStatus().name());
        h.tvRecommendation.setText(s.getRecommendation());

        int color = switch (s.getStatus()) {
            case SAFE        -> Color.parseColor("#34D399");
            case WARNING     -> Color.parseColor("#FBBF24");
            case DANGER      -> Color.parseColor("#EF4444");
            case CONDONATION -> Color.parseColor("#FF6B35");
        };
        h.tvStatus.setTextColor(color);
        h.tvPct.setTextColor(color);
        h.pbBar.getProgressDrawable().setTint(color);
    }

    @Override public int getItemCount() { return data == null ? 0 : data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvPct, tvStatus, tvRecommendation;
        ProgressBar pbBar;

        VH(View v) {
            super(v);
            tvName           = v.findViewById(R.id.tv_subject_name);
            tvPct            = v.findViewById(R.id.tv_percentage);
            tvStatus         = v.findViewById(R.id.tv_status);
            tvRecommendation = v.findViewById(R.id.tv_recommendation);
            pbBar            = v.findViewById(R.id.pb_bar);
        }
    }
}
