package com.ktu.scholar.ui.results;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.ktu.scholar.R;
import com.ktu.scholar.core.model.SemesterResult;
import com.ktu.scholar.core.model.SubjectResult;

import java.util.List;

public class SemesterAdapter extends RecyclerView.Adapter<SemesterAdapter.SemHolder> {

    private List<SemesterResult> data;

    public void submitList(List<SemesterResult> list) {
        this.data = list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public SemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_semester, parent, false);
        return new SemHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SemHolder h, int pos) {
        SemesterResult sem = data.get(pos);
        h.tvSemester.setText("Semester " + sem.getSemester());
        h.tvSgpa.setText(String.format("SGPA: %.2f", sem.getSgpa()));

        // Toggle subjects table
        h.tvSemester.setOnClickListener(v -> {
            boolean vis = h.llSubjects.getVisibility() == View.VISIBLE;
            h.llSubjects.setVisibility(vis ? View.GONE : View.VISIBLE);
            h.tvToggle.setText(vis ? "▼" : "▲");
        });

        h.llSubjects.removeAllViews();
        for (SubjectResult s : sem.getSubjects()) {
            TextView row = new TextView(h.itemView.getContext());
            row.setText(s.getSubjectCode() + "  " + s.getSubjectName() + "  " + s.getGrade());
            row.setTextColor(0xFFF0F4FF);
            row.setPadding(8, 6, 8, 6);
            h.llSubjects.addView(row);
        }
    }

    @Override public int getItemCount() { return data == null ? 0 : data.size(); }

    static class SemHolder extends RecyclerView.ViewHolder {
        TextView tvSemester, tvSgpa, tvToggle;
        LinearLayout llSubjects;

        SemHolder(View v) {
            super(v);
            tvSemester  = v.findViewById(R.id.tv_semester);
            tvSgpa      = v.findViewById(R.id.tv_sgpa);
            tvToggle    = v.findViewById(R.id.tv_toggle);
            llSubjects  = v.findViewById(R.id.ll_subjects);
        }
    }
}
