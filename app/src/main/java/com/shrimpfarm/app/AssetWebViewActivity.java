package com.shrimpfarm.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 通用静态页查看器：加载 assets 下的本地 HTML。
 *
 * <p>用法：{@code putExtra(EXTRA_ASSET_PATH, "help/index.html")}，
 * 相对 assets 根目录；图片等同目录资源用相对路径即可。</p>
 *
 * <p>显示区域严格贴在状态栏之下、导航栏之上：先关掉系统栏的自动内边距，
 * 再把状态栏 / 导航栏 / 挖孔的高度补到根布局的 padding 上，
 * 因此页面既不会被白边挡住，也不会钻到系统栏底下。</p>
 */
public class AssetWebViewActivity extends BaseActivity {

    public static final String EXTRA_ASSET_PATH = "asset_path";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 系统栏区域由我们自己用 padding 留出，不再让系统自动裁剪内容
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // 白底白栏，系统栏图标转深色
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        setContentView(R.layout.activity_asset_webview);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        root.requestApplyInsets();

        WebView webView = findViewById(R.id.web_view);
        WebSettings settings = webView.getSettings();
        // 帮助页自带很慢的自动滚动脚本，需要开启 JS
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);

        String path = getIntent().getStringExtra(EXTRA_ASSET_PATH);
        webView.loadUrl("file:///android_asset/" + resolveAssetPath(path == null ? "" : path));
    }

    // 英文环境优先加载 assets-en 下的同名页；没有就退回中文 assets。
    private String resolveAssetPath(String path) {
        if (LocaleManager.LANG_EN.equals(LocaleManager.getEffectiveLanguage(this))) {
            String enPath = "en/" + path;
            try {
                getAssets().open(enPath).close();
                return enPath;
            } catch (Exception ignored) {
                // assets-en 下没这个文件，退回中文
            }
        }
        return path;
    }
}
