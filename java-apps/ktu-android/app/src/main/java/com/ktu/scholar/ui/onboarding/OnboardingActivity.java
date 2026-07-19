package com.ktu.scholar.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.ktu.scholar.data.db.AppDatabase;
import com.ktu.scholar.data.db.ProfileEntity;
import com.ktu.scholar.ui.main.MainActivity;
import com.ktu.scholar.util.CryptoUtil;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 3-step onboarding wizard:
 *  Step 0 — Basic info
 *  Step 1 — KTU Portal credentials
 *  Step 2 — EzyGo credentials
 *
 * Uses a ViewFlipper to swap between steps.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ViewFlipper flipper;
    private Button btnNext, btnBack;
    private int currentStep = 0;
    private static final int TOTAL_STEPS = 3;

    // Step 0
    private EditText etName, etRegNum, etDob, etBranch, etCollege;
    private Spinner spSemester;

    // Step 1
    private EditText etKtuPass;

    // Step 2
    private EditText etEzygoPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        flipper    = findViewById(R.id.flipper);
        btnNext    = findViewById(R.id.btn_next);
        btnBack    = findViewById(R.id.btn_back);

        etName     = findViewById(R.id.et_name);
        etRegNum   = findViewById(R.id.et_reg_num);
        etDob      = findViewById(R.id.et_dob);
        etBranch   = findViewById(R.id.et_branch);
        etCollege  = findViewById(R.id.et_college);
        spSemester = findViewById(R.id.sp_semester);
        etKtuPass  = findViewById(R.id.et_ktu_pass);
        etEzygoPass = findViewById(R.id.et_ezygo_pass);

        // Semester spinner 1–8
        ArrayAdapter<Integer> semAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new Integer[]{1, 2, 3, 4, 5, 6, 7, 8});
        semAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSemester.setAdapter(semAdapter);

        btnNext.setOnClickListener(v -> navigate(1));
        btnBack.setOnClickListener(v -> navigate(-1));
        updateButtons();
    }

    private void navigate(int dir) {
        if (dir > 0) {
            if (!validateCurrentStep()) return;
            if (currentStep == TOTAL_STEPS - 1) { finish_(); return; }
        }
        currentStep = Math.max(0, Math.min(TOTAL_STEPS - 1, currentStep + dir));
        flipper.setDisplayedChild(currentStep);
        updateButtons();
    }

    private boolean validateCurrentStep() {
        if (currentStep == 0) {
            if (TextUtils.isEmpty(etName.getText())) {
                etName.setError("Required"); return false;
            }
            if (TextUtils.isEmpty(etRegNum.getText())) {
                etRegNum.setError("Required"); return false;
            }
        }
        return true;
    }

    private void updateButtons() {
        btnBack.setVisibility(currentStep > 0 ? View.VISIBLE : View.GONE);
        btnNext.setText(currentStep == TOTAL_STEPS - 1 ? "Finish" : "Next →");
    }

    private void finish_() {
        ProfileEntity p = new ProfileEntity();
        p.id             = UUID.randomUUID().toString();
        p.name           = etName.getText().toString().trim();
        p.registerNumber = etRegNum.getText().toString().trim();
        p.dateOfBirth    = etDob.getText().toString().trim();
        p.branch         = etBranch.getText().toString().trim();
        p.college        = etCollege.getText().toString().trim();
        p.semester       = (Integer) spSemester.getSelectedItem();
        p.createdAt      = Instant.now().toString();
        p.updatedAt      = p.createdAt;

        String ktu   = etKtuPass.getText().toString();
        String ezygo = etEzygoPass.getText().toString();
        if (!ktu.isEmpty())   p.ktuPasswordEnc   = CryptoUtil.encrypt(ktu,   this);
        if (!ezygo.isEmpty()) p.ezygoPasswordEnc = CryptoUtil.encrypt(ezygo, this);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(this).profileDao().insert(p);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
