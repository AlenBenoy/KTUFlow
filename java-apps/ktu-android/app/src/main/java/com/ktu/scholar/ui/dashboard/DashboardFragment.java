package com.ktu.scholar.ui.dashboard;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.ktu.scholar.R;
import com.ktu.scholar.ui.announcements.AnnouncementsAdapter;
import com.ktu.scholar.ui.todos.TodosAdapter;

/**
 * Home dashboard — hero CGPA card, attendance ring, upcoming tasks, announcements.
 */
public class DashboardFragment extends Fragment {

    private DashboardViewModel viewModel;
    private TextView tvCgpa, tvAttendance, tvWelcome;
    private ProgressBar pbAttendance;
    private RecyclerView rvTodos, rvAnnouncements;
    private SwipeRefreshLayout swipeRefresh;
    private TodosAdapter todosAdapter;
    private AnnouncementsAdapter announcementsAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        tvCgpa          = view.findViewById(R.id.tv_cgpa);
        tvAttendance    = view.findViewById(R.id.tv_attendance);
        tvWelcome       = view.findViewById(R.id.tv_welcome);
        pbAttendance    = view.findViewById(R.id.pb_attendance);
        rvTodos         = view.findViewById(R.id.rv_todos);
        rvAnnouncements = view.findViewById(R.id.rv_announcements);
        swipeRefresh    = view.findViewById(R.id.swipe_refresh);

        // Todos
        todosAdapter = new TodosAdapter(null, true);
        rvTodos.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTodos.setAdapter(todosAdapter);

        // Announcements
        announcementsAdapter = new AnnouncementsAdapter(null);
        rvAnnouncements.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAnnouncements.setAdapter(announcementsAdapter);

        // Observe
        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) tvWelcome.setText("Welcome, " + profile.name);
        });

        viewModel.getCgpa().observe(getViewLifecycleOwner(), cgpa -> {
            tvCgpa.setText(cgpa != null ? String.format("%.2f", cgpa) : "–.––");
        });

        viewModel.getOverallAttendance().observe(getViewLifecycleOwner(), pct -> {
            tvAttendance.setText(pct != null ? String.format("%.1f%%", pct) : "--%");
            if (pct != null) pbAttendance.setProgress((int)(double) pct);
        });

        viewModel.getPendingTodos().observe(getViewLifecycleOwner(), todos ->
                todosAdapter.submitList(todos));

        viewModel.getAnnouncements().observe(getViewLifecycleOwner(), anns ->
                announcementsAdapter.submitList(anns));

        viewModel.getIsLoading().observe(getViewLifecycleOwner(),
                loading -> swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading)));

        swipeRefresh.setOnRefreshListener(() -> viewModel.sync(requireContext()));
    }
}
