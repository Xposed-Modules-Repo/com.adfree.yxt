package com.adfree.yxt;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends Activity {

    static final String LAUNCHER_ALIAS = "com.adfree.yxt.LauncherAlias";

    private int headerColor;   // 莫奈动态主色
    private int onHeader;      // 顶栏上的文字/图标颜色(按主色明暗自动取)
    private int contentBg;     // 内容区背景(莫奈中性色)

    private TextView statusView;
    private TextView logView;
    private RadioGroup policyGroup;
    private RadioButton rbOff, rbDaily, rbWeekly;
    private Switch hideSwitch;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 莫奈(Material You)动态取色,失败则回退
        headerColor = sysColor("system_accent1_600", 0xFF6750A4);
        onHeader = isLight(headerColor) ? 0xFF1C1C1E : 0xFFFFFFFF;
        contentBg = sysColor("system_neutral1_50", 0xFFF5F5F7);
        setupStatusBar();
        Store.autoClear(this);
        setContentView(buildUi());
        refresh();
    }

    private int sysColor(String name, int fallback) {
        try {
            int id = getResources().getIdentifier(name, "color", "android");
            if (id != 0) return getResources().getColor(id);
        } catch (Throwable ignore) {}
        return fallback;
    }

    private boolean isLight(int c) {
        int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 150;
    }

    // 状态栏染成顶栏同款(莫奈)色 + 按明暗自动选图标颜色,保证可读
    private void setupStatusBar() {
        try {
            getWindow().addFlags(0x80000000);   // FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            getWindow().clearFlags(0x04000000);  // 清除 TRANSLUCENT_STATUS
            try {
                getWindow().getClass().getMethod("setStatusBarColor", int.class)
                        .invoke(getWindow(), headerColor);
            } catch (Throwable ignore) {}
            View decor = getWindow().getDecorView();
            int vis = decor.getSystemUiVisibility();
            if (isLight(headerColor)) vis |= 0x2000;   // 浅色主色 -> 深色图标
            else vis &= ~0x2000;                       // 深色主色 -> 白色图标
            decor.setSystemUiVisibility(vis);
        } catch (Throwable ignore) {}
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(contentBg);

        // 顶栏:莫奈主色,延伸到状态栏后(paddingTop 含状态栏高度)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(headerColor);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView title = new TextView(this);
        title.setText("易校通去广告");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(onHeader);
        header.addView(title);
        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTextColor((onHeader & 0x00FFFFFF) | 0xCC000000);
        statusView.setPadding(0, dp(4), 0, 0);
        statusView.setText("模块运行中");
        header.addView(statusView);
        root.addView(header);

        // 内容区(可滚动权重容器)
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(12));
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content);

        // 隐藏桌面图标
        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        iconRow.setPadding(0, dp(4), 0, dp(4));
        TextView iconLabel = new TextView(this);
        iconLabel.setText("隐藏桌面图标");
        iconLabel.setTextSize(16);
        iconLabel.setTypeface(Typeface.DEFAULT_BOLD);
        iconLabel.setTextColor(0xFF1C1C1E);
        iconLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hideSwitch = new Switch(this);
        iconRow.addView(iconLabel);
        iconRow.addView(hideSwitch);
        content.addView(iconRow);

        TextView iconHint = new TextView(this);
        iconHint.setText("隐藏后仍可从 LSPosed 管理器 → 本模块 → 打开 进入本界面");
        iconHint.setTextSize(11);
        iconHint.setTextColor(0xFF999999);
        iconHint.setPadding(0, 0, 0, dp(12));
        content.addView(iconHint);

        hideSwitch.setChecked(isIconHidden());
        hideSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) { setIconHidden(checked); }
        });

        // 自动清理
        TextView secTitle = new TextView(this);
        secTitle.setText("自动清理日志");
        secTitle.setTextSize(16);
        secTitle.setTypeface(Typeface.DEFAULT_BOLD);
        secTitle.setTextColor(0xFF1C1C1E);
        content.addView(secTitle);

        policyGroup = new RadioGroup(this);
        policyGroup.setOrientation(RadioGroup.HORIZONTAL);
        policyGroup.setPadding(0, dp(6), 0, dp(6));
        rbOff = radio("关闭");
        rbDaily = radio("每天");
        rbWeekly = radio("每周");
        policyGroup.addView(rbOff);
        policyGroup.addView(rbDaily);
        policyGroup.addView(rbWeekly);
        content.addView(policyGroup);
        attachPolicyListener();

        // 操作按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(8), 0, dp(8));
        Button clearBtn = button("手动清空日志");
        Button exportBtn = button("导出为 txt");
        Button refreshBtn = button("刷新");
        btnRow.addView(clearBtn);
        btnRow.addView(exportBtn);
        btnRow.addView(refreshBtn);
        content.addView(btnRow);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Store.clear(SettingsActivity.this); toast("日志已清空"); refresh(); }
        });
        exportBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { exportLog(); }
        });
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refresh(); }
        });

        TextView logTitle = new TextView(this);
        logTitle.setText("拦截记录");
        logTitle.setTextSize(16);
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setTextColor(0xFF1C1C1E);
        logTitle.setPadding(0, dp(8), 0, dp(6));
        content.addView(logTitle);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        GradientDrawable svBg = new GradientDrawable();
        svBg.setColor(Color.WHITE);
        svBg.setCornerRadius(dp(14));
        sv.setBackground(svBg);
        sv.setPadding(dp(14), dp(12), dp(14), dp(12));
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextColor(0xFF333333);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setMovementMethod(new ScrollingMovementMethod());
        sv.addView(logView);
        content.addView(sv);

        TextView foot = new TextView(this);
        foot.setText("拦截记录由模块自动收集,无需 root\n© 2026 github@zzdwymk · Powered by zzdwymk");
        foot.setTextSize(11);
        foot.setTextColor(0xFF999999);
        foot.setPadding(0, dp(8), 0, 0);
        content.addView(foot);

        return root;
    }

    private void attachPolicyListener() {
        policyGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String p = checkedId == rbDaily.getId() ? "daily"
                        : checkedId == rbWeekly.getId() ? "weekly" : "off";
                Store.setPolicy(SettingsActivity.this, p);
                toast("自动清理已设为:" + zh(p));
            }
        });
    }

    private RadioButton radio(String text) {
        RadioButton rb = new RadioButton(this);
        rb.setText(text);
        rb.setTextColor(0xFF1C1C1E);
        rb.setPadding(0, 0, dp(24), 0);
        return rb;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void refresh() {
        String policy = Store.getPolicy(this);
        policyGroup.setOnCheckedChangeListener(null);
        if (policy.equals("daily")) rbDaily.setChecked(true);
        else if (policy.equals("weekly")) rbWeekly.setChecked(true);
        else rbOff.setChecked(true);
        attachPolicyListener();
        int count = Store.count(this);
        statusView.setText("模块运行中 · 已记录 " + count + " 条拦截");
        String log = Store.read(this);
        logView.setText(log.length() == 0 ? "(暂无记录)" : Store.legend() + log);
    }

    private void exportLog() {
        try {
            String log = Store.read(this);
            String header = "易校通去广告 · 拦截日志\n© 2026 github@zzdwymk · Powered by zzdwymk\n"
                    + "导出时间:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + "\n════════════════════════════\n";
            String content = header + Store.legend() + (log.length() == 0 ? "(暂无记录)" : log);
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = getExternalFilesDir(null);
            File out = new File(dir, "yxt_adfree_log_" + ts + ".txt");
            FileWriter w = new FileWriter(out, false);
            w.write(content);
            w.close();
            toast("已导出:" + out.getAbsolutePath());
        } catch (Throwable e) {
            toast("导出失败:" + e.getMessage());
        }
    }

    private String zh(String p) { return p.equals("daily") ? "每天" : p.equals("weekly") ? "每周" : "关闭"; }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    // ---------- 隐藏/显示桌面图标(切换 activity-alias 组件,无需 root) ----------
    private boolean isIconHidden() {
        try {
            ComponentName alias = new ComponentName(this, LAUNCHER_ALIAS);
            return getPackageManager().getComponentEnabledSetting(alias)
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable e) { return false; }
    }

    private void setIconHidden(boolean hidden) {
        try {
            ComponentName alias = new ComponentName(this, LAUNCHER_ALIAS);
            int state = hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            getPackageManager().setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
            toast(hidden ? "桌面图标已隐藏,可从 LSPosed 打开本界面" : "桌面图标已显示");
        } catch (Throwable e) { toast("切换失败:" + e.getMessage()); }
    }
}
