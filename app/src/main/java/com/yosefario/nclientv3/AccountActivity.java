package com.yosefario.nclientv3;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiAuth;
import com.yosefario.nclientv3.components.activities.GeneralActivity;
import com.yosefario.nclientv3.settings.Login;

public class AccountActivity extends GeneralActivity {
    private TextView nhentaiStatus;
    private TextView ehentaiStatus;
    private MaterialButton nhentaiAction;
    private MaterialButton ehentaiAction;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        setUpToolbar();

        nhentaiStatus = findViewById(R.id.nhentai_status);
        nhentaiAction = findViewById(R.id.nhentai_action);
        ehentaiStatus = findViewById(R.id.ehentai_status);
        ehentaiAction = findViewById(R.id.ehentai_action);

        nhentaiAction.setOnClickListener(v -> {
            if (Login.isLogged()) {
                Login.logout(this);
                refresh();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });
        ehentaiAction.setOnClickListener(v -> {
            if (EHentaiAuth.isLoggedIn()) {
                EHentaiAuth.logout(this);
                clearEhentaiWebCookies();
                refresh();
            } else {
                startActivity(new Intent(this, EHentaiLoginActivity.class));
            }
        });
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean nh = Login.isLogged();
        nhentaiStatus.setText(nh && Login.getUser() != null
            ? getString(R.string.logged_in_as, Login.getUser().getUsername())
            : getString(R.string.not_logged_in));
        nhentaiAction.setText(nh ? R.string.logout : R.string.login);

        boolean eh = EHentaiAuth.isLoggedIn();
        ehentaiStatus.setText(eh
            ? (EHentaiAuth.getDisplayName() != null
                ? getString(R.string.logged_in_as, EHentaiAuth.getDisplayName())
                : getString(R.string.eh_logged_in))
            : getString(R.string.not_logged_in));
        ehentaiAction.setText(eh ? R.string.logout : R.string.login);
    }

    private void clearEhentaiWebCookies() {
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        String[][] scopes = {
            {"https://e-hentai.org/", ".e-hentai.org"},
            {"https://forums.e-hentai.org/", ".e-hentai.org"},
            {"https://exhentai.org/", ".exhentai.org"},
        };
        String[] names = {"ipb_member_id", "ipb_pass_hash", "igneous", "sk", "sl"};
        for (String[] scope : scopes)
            for (String name : names) {
                cm.setCookie(scope[0], name + "=; Path=/; Max-Age=0");
                cm.setCookie(scope[0], name + "=; Domain=" + scope[1] + "; Path=/; Max-Age=0");
            }
        cm.flush();
    }
}
