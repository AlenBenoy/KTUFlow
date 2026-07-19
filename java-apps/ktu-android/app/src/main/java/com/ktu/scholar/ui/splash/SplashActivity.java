package com.ktu.scholar.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.ktu.scholar.data.db.AppDatabase;
import com.ktu.scholar.data.db.ProfileEntity;
import com.ktu.scholar.ui.main.MainActivity;
import com.ktu.scholar.ui.onboarding.OnboardingActivity;
import java.util.concurrent.Executors;

/**
 * Entry point. Checks for an existing profile:
 *  - No profile → OnboardingActivity
 *  - Profile exists → MainActivity
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No layout — runs instantly

        Executors.newSingleThreadExecutor().execute(() -> {
            ProfileEntity profile = AppDatabase.getInstance(this).profileDao().getProfile();
            Intent next = profile == null
                    ? new Intent(this, OnboardingActivity.class)
                    : new Intent(this, MainActivity.class);
            startActivity(next);
            finish();
        });
    }
}
