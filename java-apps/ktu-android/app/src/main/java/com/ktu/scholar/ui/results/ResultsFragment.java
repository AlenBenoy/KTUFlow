package com.ktu.scholar.ui.results;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.ktu.scholar.R;

public class ResultsFragment extends Fragment {

    private ResultsViewModel viewModel;
    private TextView tvCgpa, tvPercentage, tvClass;
    private RecyclerView rvSemesters;
    private ProgressBar progressBar;
    private Button btnSync;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel    = new ViewModelProvider(this).get(ResultsViewModel.class);
        tvCgpa       = view.findViewById(R.id.tv_cgpa);
        tvPercentage = view.findViewById(R.id.tv_percentage);
        tvClass      = view.findViewById(R.id.tv_class);
        rvSemesters  = view.findViewById(R.id.rv_semesters);
        progressBar  = view.findViewById(R.id.progress_bar);
        btnSync      = view.findViewById(R.id.btn_sync);

        SemesterAdapter adapter = new SemesterAdapter();
        rvSemesters.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSemesters.setAdapter(adapter);

        viewModel.getSemesters().observe(getViewLifecycleOwner(), sems -> {
            adapter.submitList(sems);
        });
        viewModel.getCgpa().observe(getViewLifecycleOwner(), c -> {
            tvCgpa.setText(c != null ? String.format("%.2f", c) : "–.––");
        });
        viewModel.getPercentage().observe(getViewLifecycleOwner(), p -> {
            tvPercentage.setText(p != null ? String.format("%.1f%%", p) : "--%");
        });
        viewModel.getClassification().observe(getViewLifecycleOwner(), cl ->
                tvClass.setText(cl != null ? cl : "—"));
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading ->
                progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        btnSync.setOnClickListener(v -> viewModel.sync(requireContext()));
    }
}
