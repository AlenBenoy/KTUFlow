package com.ktu.scholar.ui.attendance;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.ktu.scholar.R;

public class AttendanceFragment extends Fragment {

    private AttendanceViewModel viewModel;
    private TextView tvOverall;
    private ProgressBar pbOverall;
    private RecyclerView rvSubjects;
    private SwipeRefreshLayout swipe;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attendance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AttendanceViewModel.class);

        tvOverall  = view.findViewById(R.id.tv_overall);
        pbOverall  = view.findViewById(R.id.pb_overall);
        rvSubjects = view.findViewById(R.id.rv_subjects);
        swipe      = view.findViewById(R.id.swipe_refresh);

        AttendanceAdapter adapter = new AttendanceAdapter();
        rvSubjects.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSubjects.setAdapter(adapter);

        viewModel.getSubjects().observe(getViewLifecycleOwner(), subjects -> {
            adapter.submitList(subjects);
        });
        viewModel.getOverallPercentage().observe(getViewLifecycleOwner(), pct -> {
            tvOverall.setText(pct != null ? String.format("%.1f%%", pct) : "--%");
            if (pct != null) pbOverall.setProgress((int)(double)pct);
        });
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading ->
                swipe.setRefreshing(Boolean.TRUE.equals(loading)));

        swipe.setOnRefreshListener(() -> viewModel.sync(requireContext()));
    }
}
