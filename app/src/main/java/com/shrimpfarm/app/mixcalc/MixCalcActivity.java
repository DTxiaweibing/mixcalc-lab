package com.shrimpfarm.app.mixcalc;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.BuildConfig;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.mixcalc.model.FeedData;
import com.shrimpfarm.app.mixcalc.prefs.MixCalcPrefs;
import com.shrimpfarm.app.mixcalc.model.Batch;
import com.shrimpfarm.app.mixcalc.table.TabletLayout;
import com.shrimpfarm.app.mixcalc.table.CellStyle;
import com.shrimpfarm.app.mixcalc.table.CellTextProvider;
import com.shrimpfarm.app.mixcalc.table.MixCalcTableView;
import com.shrimpfarm.app.mixcalc.table.OnCellEditListener;
import com.shrimpfarm.app.mixcalc.table.OnCellTapListener;
import com.shrimpfarm.app.mixcalc.table.TableColumn;
import com.shrimpfarm.app.mixcalc.table.TableColumnConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MixCalcActivity extends BaseActivity {

    private static final String TAG = "MixCalc";
    // 已同意的 versionCode：>= 当前版本才视为已同意（升级后需重新同意）

    // 视图组件
    private MixCalcTableView table;
    private TableColumnConfig tableConfig;
    private TextView tvErrorDisplay;

    // 数据
    private List<FeedData> dataList;
    private final List<Batch> batches = new ArrayList<>();
    private int currentBatchIndex = 0;
    private int globalWaterPercentage = 20;

    // 发酵料计算标志
    private boolean fermentedIncludedInWater = false;
    private boolean fermentedIncludedInAverage = false;

    // 正在编辑的格 + 进入编辑那一刻的原值（浮层逐字符提交，退格清空需要它还原原值）
    private int editBeginRow = -1;
    private String editBeginColumn;
    private double editBeginValue;
    // 棚数格的进入编辑快照：净结果没变时要还原，不能留下“手动修改”标记
    private int editBeginShedCount;
    private int editBeginShedBase;
    private int editBeginShedBaseSum;
    private boolean editBeginShedManual;

    // 列管理
    private final Map<String, Boolean> columnVisibility = new HashMap<String, Boolean>();
    private final Map<String, Boolean> columnFixed = new HashMap<String, Boolean>();
    private PopupWindow columnSettingsPopup;
    private final String[] columnIds = {
        "seq", "shed_number", "shed_count", "average_feed", 
        "fermented", "powder", "feed03", "feed05", "feed10", 
        "water", "weighed_feed"
    };

    private String[] columnNames;

    private final int[] columnWidths = {40, 60, 40, 40, 60, 60, 60, 60, 60, 60, 60};

    // 平板适配相关成员变量
    private boolean isTabletMode = false;
    private int[] adaptiveColumnWidths;

    // 表尾统计值（由 updateFooterRow 填充，绘制时读取）
    private final Map<String, Double> footerTotals = new HashMap<>();

    // 非发酵料列（按粒径由小到大），换料比例只在这几种料之间生效
    private static final String[] NF_FEED_COLUMNS = {"powder", "feed03", "feed05", "feed10"};

    // 总数据行数
    private static final int TOTAL_ROWS = 30;
    // 第 1 行是棚数基数行：棚数 = 基数 − 第 2..N 行棚数之和
    private static final int FIRST_ROW = 0;

    // ==================== 自绘引擎提供者 ====================

    private final CellTextProvider textProvider = new CellTextProvider() {
        @Override
        public String getHeaderText(String columnId) {
            if ("average_feed".equals(columnId)) {
                return getString(R.string.mix_col_avg_feed);
            }
            if ("water".equals(columnId)) {
                return getString(R.string.water_percentage_header, globalWaterPercentage);
            }
            int idx = getColumnIndex(columnId);
            return (idx >= 0 && columnNames != null) ? columnNames[idx] : "";
        }

        @Override
        public int getHeaderState(String columnId) {
            if (!"fermented".equals(columnId)) {
                return CellStyle.HEADER_NORMAL;
            }
            if (fermentedIncludedInAverage) {
                return fermentedIncludedInWater
                        ? CellStyle.HEADER_FERMENTED_GREEN_BROWN
                        : CellStyle.HEADER_FERMENTED_GRAY_BROWN;
            }
            return fermentedIncludedInWater
                    ? CellStyle.HEADER_FERMENTED_GREEN
                    : CellStyle.HEADER_FERMENTED_GRAY;
        }

        @Override
        public String getCellText(int row, String columnId) {
            FeedData data = getData(row);
            if (data == null) {
                return "";
            }
            switch (columnId) {
                case "seq":
                    return String.valueOf(row + 1);
                case "shed_number":
                    return data.getShedNumber();
                case "water":
                case "average_feed":
                case "weighed_feed":
                    return getCellDisplayValue(data, columnId);
                default:
                    return getCellInitialValue(data, columnId);
            }
        }

        @Override
        public int getCellState(int row, String columnId) {
            FeedData data = getData(row);
            if (data == null) {
                return CellStyle.STATE_NORMAL;
            }
            if ("shed_count".equals(columnId)) {
                // 第 1 行：基数被后面各行扣光（≤0）时标警告色
                if (row == FIRST_ROW && data.getShedCountBase() > 0
                        && data.getShedCount() <= 0) {
                    return CellStyle.STATE_WARNING;
                }
            }
            if (isEditableColumn(columnId)
                    && !"average_feed".equals(columnId)
                    && !isCellEnabled(columnId, data, row)) {
                return CellStyle.STATE_DISABLED;
            }
            if (isCellEnabled(columnId, data, row) && isSwitchRatioCell(data, columnId)) {
                return CellStyle.STATE_HIGHLIGHT;
            }
            return CellStyle.STATE_NORMAL;
        }

        @Override
        public boolean isCellEditable(int row, String columnId) {
            FeedData data = getData(row);
            if (data == null || !isEditableColumn(columnId)) {
                return false;
            }
            return isCellEnabled(columnId, data, row);
        }

        @Override
        public String getFooterText(String columnId) {
            if ("seq".equals(columnId)) {
                return getString(R.string.seq_stat_format, getFilledRowCount(), TOTAL_ROWS);
            }
            if ("shed_number".equals(columnId)) {
                return getString(R.string.mix_btn_help);
            }
            if ("weighed_feed".equals(columnId)) {
                return getString(R.string.mix_btn_reset);
            }
            Double v = footerTotals.get(columnId);
            double value = v == null ? 0 : v;
            if (value == 0) {
                return "0";
            }
            if (value == (int) value) {
                return String.valueOf((int) value);
            }
            return String.format(Locale.ROOT, "%.2f", value);
        }

        @Override
        public int getFooterStyle(String columnId) {
            if ("shed_number".equals(columnId)) {
                return CellStyle.FOOTER_BLUE;
            }
            if ("weighed_feed".equals(columnId)) {
                return CellStyle.FOOTER_CARMINE;
            }
            return CellStyle.FOOTER_NORMAL;
        }
    };

    private final OnCellTapListener tapListener = new OnCellTapListener() {
        @Override
        public void onCellClick(int row, String columnId) {
            // 编辑由引擎的 beginEdit 处理，这里无需额外动作
        }

        @Override
        public void onCellLongPress(int row, String columnId, Rect cellRect) {
            FeedData data = getData(row);
            if (data == null || !isNonFermentedFeedColumn(columnId)) {
                return;
            }
            if (!isCellEnabled(columnId, data, row)) {
                return;
            }
            // 长按“较大那档”：恰好两档时是较大的那档；只有一档时是它的“大一号”（该格可以为空）
            String target = getSwitchTargetColumn(data);
            if (target != null && target.equals(columnId)) {
                showSwitchRatioDrawer(data, row, cellRect);
            }
        }

        @Override
        public void onHeaderClick(String columnId, Rect cellRect) {
            if ("water".equals(columnId)) {
                showWaterPercentageDialog(cellRect);
            } else if ("fermented".equals(columnId)) {
                showFermentedCalculationDialog(cellRect);
            }
        }

        @Override
        public void onHeaderLongPress(String columnId, Rect cellRect) {
            int idx = getColumnIndex(columnId);
            if (idx >= 0) {
                showColumnOperationMenu(idx, columnId, cellRect);
            }
        }

        @Override
        public void onFooterClick(String columnId) {
            if ("shed_number".equals(columnId)) {
                showHelpPanel();
            } else if ("weighed_feed".equals(columnId)) {
                showResetConfirmationDialog();
            }
        }
    };

    private final OnCellEditListener editListener = new OnCellEditListener() {
        @Override
        public void onCellEditBegin(int row, String columnId) {
            FeedData data = getData(row);
            editBeginRow = row;
            editBeginColumn = columnId;
            editBeginValue = data == null ? 0 : getFeedValueByColumn(data, columnId);
            editBeginShedCount = data == null ? 0 : data.getShedCount();
            editBeginShedBase = data == null ? 0 : data.getShedCountBase();
            editBeginShedBaseSum = data == null ? 0 : data.getShedCountBaseSum();
            editBeginShedManual = data != null && data.isShedCountManuallyModified();
        }

        @Override
        public void onCellCommitted(int row, String columnId, String text) {
            commitCell(row, columnId, text);
        }

        @Override
        public void onEditNext(int row, String columnId) {
            moveFocusToNextEditableCell(row, getColumnIndex(columnId));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // API 35+ 强制 edge-to-edge：先把窗口设为 edge-to-edge，首帧就按 insets 给系统栏留白；
        // 老系统仍由窗口自己让位，spacer 保持 0，避免重复留白。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }

        try {
            setContentView(R.layout.activity_mix_calc);
            // 键盘弹起时把布局往上顶，让底部统计行跟着键盘上移
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            // ---------- 系统栏留白（状态栏深绿 / 底部导航栏白），由 WindowInsets 驱动 ----------
            setupSystemBarInsets();

            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // 初始化视图
            initViews();
            initColumnNames();
            loadQuickSettings();

            // 列可见 / 固定状态
            initColumnVisibility();
            initColumnFixedState();
            loadColumnVisibility();
            loadColumnFixedState();

            // 平板自适应（放在列可见性之后：按真实可见列铺满屏幕宽度）
            initTabletAdaptiveSettings();

            // 装配自绘引擎
            initData();
            applyColumnConfig();
            table.setCellTextProvider(textProvider);
            table.setOnCellTapListener(tapListener);
            table.setOnCellEditListener(editListener);
            table.setRows(dataList);

            // 加载已保存数据并计算
            loadSavedData();
            updateAllCalculations();

        } catch (Exception e) {
            Log.e(TAG, "启动异常: " + e.getMessage(), e);
        }

        // 首次启动：隐私政策与使用协议同意弹窗（覆盖在主页上）。
        // 放在 try 外，主页初始化即使出错也要能弹出并完成同意/拒绝流程。
        showPrivacyConsentIfNeeded();
    }

    // 系统栏留白：状态栏那块涂深绿、底部导航栏那块涂白，高度取 WindowInsets。
    // API 35+ 强制 edge-to-edge，内容会钻到系统栏底下，所以拿两块 spacer 显式让开；
    // 老系统窗口自己已经让过了，这时两块 spacer 保持 0，别重复留。
    // spacer 高度必须在这次 insets 回调里同步设好，不能 post：否则首帧仍是 0，
    // 标题栏会先躲到状态栏下面再跳下来（recreate 时看起来就是闪一下）。
    private void setupSystemBarInsets() {
        final View root = findViewById(R.id.main_layout);
        final View statusSpacer = findViewById(R.id.status_bar_spacer);
        final View navSpacer = findViewById(R.id.nav_bar_spacer);
        if (root == null || statusSpacer == null || navSpacer == null) {
            return;
        }
        final boolean edgeToEdge = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, wInsets) -> {
            final Insets bars = wInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // 输入法弹出时底部让位取"导航栏 / 输入法"的较大者，
            // 与原项目 MyApplication 给 android.R.id.content 加底 padding 的做法一致：
            // 表格整体变矮 → 统计行跟着被推上来。
            final Insets ime = wInsets.getInsets(WindowInsetsCompat.Type.ime());
            final int bottomInset = Math.max(bars.bottom, ime.bottom);
            setSpacerHeight(statusSpacer, edgeToEdge ? bars.top : 0);
            setSpacerHeight(navSpacer, edgeToEdge ? bottomInset : 0);
            return wInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static void setSpacerHeight(View spacer, int heightPx) {
        ViewGroup.LayoutParams lp = spacer.getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
            spacer.setLayoutParams(lp);
            return;
        }
        // insets 可能被重复派发（弹窗/焦点变化）。高度没变就别再 setLayoutParams，
        // 否则会白跑一次 measure/layout，表格的 clampOffsets 可能让内容闪一下。
        if (lp.height == heightPx) {
            return;
        }
        lp.height = heightPx;
        spacer.setLayoutParams(lp);
    }

    private void initColumnNames() {
        columnNames = new String[]{
            getString(R.string.mix_col_seq), getString(R.string.mix_col_shed), getString(R.string.mix_col_count), getString(R.string.mix_col_avg_feed),
            getString(R.string.mix_col_fermented), getString(R.string.mix_col_powder), getString(R.string.mix_col_feed03), getString(R.string.mix_col_feed05), getString(R.string.mix_col_feed10),
            getString(R.string.mix_col_water), getString(R.string.mix_col_weigh)
        };
    }

    // 快速加载设置
    private void loadQuickSettings() {
        // 水比与发酵开关跟随批次，见 bindCurrentBatch()
    }

    private boolean isTabletDevice(Context context) {
        return TabletLayout.isTabletDevice(context);
    }

    // 从 SharedPreferences 同步加载已保存数据（数据量小，主线程解析）
    private void loadSavedData() {
        List<Batch> loaded = MixCalcPrefs.loadBatches(this, TOTAL_ROWS);
        batches.clear();
        batches.addAll(loaded);
        currentBatchIndex = MixCalcPrefs.loadActiveIndex(this);
        if (currentBatchIndex < 0 || currentBatchIndex >= batches.size()) {
            currentBatchIndex = 0;
        }
        bindCurrentBatch();
        recalcBatch(batches.get(currentBatchIndex));
        recomputeFirstRowRemainder();
    }

    // 获取列索引
    private int getColumnIndex(String columnId) {
        for (int i = 0; i < columnIds.length; i++) {
            if (columnIds[i].equals(columnId)) {
                return i;
            }
        }
        return -1;
    }

    private FeedData getData(int row) {
        if (dataList == null || row < 0 || row >= dataList.size()) {
            return null;
        }
        return dataList.get(row);
    }

    // ==================== 编辑提交（迁移自原 TextWatcher / 焦点监听） ====================

    private void commitCell(int row, String columnId, String text) {
        FeedData data = getData(row);
        if (data == null) {
            return;
        }
        String value = text == null ? "" : text.trim();

        switch (columnId) {
            case "shed_number":
                data.setShedNumber(value);
                break;
            case "shed_count": {
                double newValue = 0;
                if (!value.isEmpty()) {
                    try {
                        newValue = Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        newValue = 0;
                    }
                }
                int newCount = (int) newValue;
                boolean sameCell = row == editBeginRow && columnId.equals(editBeginColumn);
                int originCount = sameCell ? editBeginShedCount : data.getShedCount();
                if (newCount != originCount) {
                    data.setShedCount(newCount);
                    data.setShedCountManuallyModified(!(newCount == 0 || value.isEmpty()));
                    if (row == FIRST_ROW) {
                        // 第 1 行手改：改后的显示值就是新锚点，同时记下刻后面各行之和，
                        // 之后只按“后面各行的增量”扣，不再重复扣
                        data.setShedCountBase(newCount);
                        data.setShedCountBaseSum(sumShedCountsBelowFirstRow());
                    }
                } else if (sameCell) {
                    // 净结果没变（只是获得焦点 / 输入后又改回原值）：还原快照，不算手动修改
                    data.setShedCountManuallyModified(editBeginShedManual);
                    data.setShedCountBase(editBeginShedBase);
                    data.setShedCountBaseSum(editBeginShedBaseSum);
                }
                break;
            }
            case "average_feed": {
                data.setAverageManually(parseDouble(value), fermentedIncludedInAverage);
                break;
            }
            default: {
                double v = parseDouble(value);
                data.setAverageManuallyModified(false);
                int nfIndex = nonFermentedIndex(columnId);
                if (v == 0 && nfIndex >= 0) {
                    // 用进入编辑那一刻的原值（浮层逐字符提交，退格清空到最后一位时
                    // 模型中只剩最后一位数字，不能拿它当"细粒径料的数值"）
                    double origin = (row == editBeginRow && columnId.equals(editBeginColumn))
                            ? editBeginValue : getFeedValueByColumn(data, columnId);
                    if (data.mergeSmallerWhenCleared(nfIndex, origin) > 0) {
                        // 细粒径料并入粗粒径料 → 只剩一档，换料完成
                        editBeginValue = 0;
                        showStyledConfirmDialog70(
                                getString(R.string.mix_dialog_switch_done_title),
                                getString(R.string.mix_dialog_switch_done_msg),
                                new String[]{getString(R.string.btn_ok)},
                                new int[]{0xFF2E7D32},
                                new DialogInterface.OnClickListener[]{null});
                        break;
                    }
                }
                switch (columnId) {
                    case "fermented":
                        data.setFermentedFeed(v);
                        break;
                    case "powder":
                        data.setPowderFeed(v);
                        break;
                    case "feed03":
                        data.setFeed03(v);
                        break;
                    case "feed05":
                        data.setFeed05(v);
                        break;
                    case "feed10":
                        data.setFeed10(v);
                        break;
                }
                // 手改任意料格：作废已设置的换料比例，回到两料实际比例
                if ("fermented".equals(columnId) || isNonFermentedFeedColumn(columnId)) {
                    data.setSwitchRatio(0);
                }
                break;
            }
        }

        applyRowChange(row);
    }

    // 行数据改动的统一出口：没棚数就清空料格（就是旧的"禁用即清空"），重算并整表重绘
    private void applyRowChange(int row) {
        FeedData data = getData(row);
        if (data == null) {
            return;
        }

        // 第 1 行的棚数是“基数 − 后面各行之和”，允许 ≤ 0，故豁免“无棚数即清空料格”
        if (row != FIRST_ROW && data.getShedCount() <= 0) {
            data.setFermentedFeed(0);
            data.setPowderFeed(0);
            data.setFeed03(0);
            data.setFeed05(0);
            data.setFeed10(0);
            data.setWater(0);
            data.setWeighedFeed(0);
            data.setAverage(0);
            data.setAverageManuallyModified(false);
            data.setSwitchRatio(0);
        }

        if (row == FIRST_ROW) {
            // 第 1 行：棚数 = 基数 − 后面各行之和，派生值也要用“剩余棚数”算，
            // 所以不走 updateAllCalculations()（它会先把棚数还原成基数，把平均算反）
            recomputeFirstRowRemainder();
        } else {
            data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
            recomputeFirstRowRemainder();
        }
        if (table != null) {
            table.notifyAllChanged();
        }
        updateFooterRow();
        saveAllData();
    }

    // 第 1 行棚数 = 基数 − 第 2..N 行棚数之和，允许 0 或负数。
    // 基数来自第 1 行棚号自动推出来的棚数；棚数格被手改过的话，基数就是改后的值。
    // 得在各行 updateAllCalculations() 之后再调，不然基数会被棚号重算盖掉。
    private void recomputeFirstRowRemainder() {
        if (dataList == null || dataList.size() <= FIRST_ROW + 1) {
            return;
        }
        FeedData first = getData(FIRST_ROW);
        if (first == null) {
            return;
        }

        // 记下进门前第 1 行的剩余棚数和它对应的每棚平均。平均是锚点，棚数变了它不变
        int oldShedCount = first.getShedCount();
        double oldAverage = first.getAverage(fermentedIncludedInAverage);

        // 未被手改：由棚号重新推出基数（先写进 shedCount）；手改过则基数是手改后的锚点值
        if (!first.isShedCountManuallyModified()) {
            first.calculateShedCount();
            first.setShedCountBase(first.getShedCount());
            // 自动推导：基数要减掉后面各行全部（锚点为 0）
            first.setShedCountBaseSum(0);
        } else if (first.getShedCountBase() <= 0) {
            // 覆盖安装升级上来的旧数据没有 shedCountBase：
            // 旧棚数 > 0 就当基数迁移；旧棚数为 0（无意义）则放开手改标记，交回棚号推导
            if (first.getShedCount() > 0) {
                first.setShedCountBase(first.getShedCount());
                first.setShedCountBaseSum(0);
            } else {
                first.setShedCountManuallyModified(false);
                first.calculateShedCount();
                first.setShedCountBase(first.getShedCount());
                first.setShedCountBaseSum(0);
            }
        }

        int sum = 0;
        for (int i = FIRST_ROW + 1; i < dataList.size(); i++) {
            FeedData d = dataList.get(i);
            if (d != null) {
                sum += d.getShedCount();
            }
        }

        // 手改过：基数是“手改那一刻的显示值”，只扣后面各行相对那一刻的增量，
        // 不重复扣（手改 32→28、第 2 行 3 个时，显示就该是 28 而不是 25）
        int remainder = first.getShedCountBase() - (sum - first.getShedCountBaseSum());
        first.setShedCountDirectly(remainder, first.isShedCountManuallyModified());

        // 棚数变了：钉住“每棚平均”，让料量(含称料) / 水量按新棚数重算；
        // 反过来（料量不变、平均被除出来）是错的
        if (remainder != oldShedCount && oldAverage > 0
                && !first.isAverageManuallyModified()) {
            first.setAverage(oldAverage);
            first.setAverageManuallyModified(true);
        }

        first.recalculateDerivedOnly(globalWaterPercentage,
                fermentedIncludedInWater, fermentedIncludedInAverage);
    }

    // 第 2..N 行棚数之和，手改第 1 行时用来记锚点
    private int sumShedCountsBelowFirstRow() {
        if (dataList == null) {
            return 0;
        }
        int sum = 0;
        for (int i = FIRST_ROW + 1; i < dataList.size(); i++) {
            FeedData d = dataList.get(i);
            if (d != null) {
                sum += d.getShedCount();
            }
        }
        return sum;
    }

    private double parseDouble(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 移动到下一个可编辑单元格
    private void moveFocusToNextEditableCell(int currentRow, int currentColumnIndex) {
        try {
            if (currentColumnIndex < 0 || table == null) {
                return;
            }

            for (int nextColIndex = currentColumnIndex + 1; nextColIndex < columnIds.length; nextColIndex++) {
                String nextColumnId = columnIds[nextColIndex];
                Boolean visible = columnVisibility.get(nextColumnId);
                if (visible != null && visible && isEditableColumn(nextColumnId)) {
                    FeedData data = getData(currentRow);
                    if (data != null && isCellEnabled(nextColumnId, data, currentRow)) {
                        if (table.beginEdit(currentRow, nextColumnId)) {
                            return;
                        }
                    }
                }
            }

            int nextRow = currentRow + 1;
            if (dataList != null && nextRow < dataList.size()) {
                for (int colIndex = 0; colIndex < columnIds.length; colIndex++) {
                    String columnId = columnIds[colIndex];
                    Boolean visible = columnVisibility.get(columnId);
                    if (visible != null && visible && isEditableColumn(columnId)) {
                        FeedData data = getData(nextRow);
                        if (data != null && isCellEnabled(columnId, data, nextRow)) {
                            if (table.beginEdit(nextRow, columnId)) {
                                return;
                            }
                        }
                    }
                }
            }

            // 没有下一个可编辑格：收起浮层并隐藏输入法
            table.endEdit();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(table.getWindowToken(), 0);
            }

        } catch (Exception e) {
            Log.e(TAG, "移动焦点异常: " + e.getMessage());
        }
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onPause() {
        super.onPause();
        saveAllData();
        saveGlobalWaterPercentage();
        saveFermentedIncludedInWater();
        saveFermentedIncludedInAverage();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        isTabletMode = isTabletDevice(this);
        initTabletAdaptiveSettings();
        rebuildCompleteTable();
    }

    // 更新统计行
    private void updateFooterRow() {
        try {
            double totalShedCount = 0;
            double totalFermented = 0;
            double totalPowder = 0;
            double totalFeed03 = 0;
            double totalFeed05 = 0;
            double totalFeed10 = 0;
            double totalWater = 0;
            // 平均吃料只统计“行平均 > 0”的行：没填料、或只有发酵料且未参与算料的行走平均为 0，整行不参与。
            // 分母是这些行的棚数，分子是 行平均 × 行棚数：普通行等价于 总料/总棚，
            // 手改过平均的行则直接采用改后的平均（手改时料格也按这个平均重算过）。
            double includedShedCount = 0;
            double includedFeedTotal = 0;

            for (FeedData data : dataList) {
                totalShedCount += data.getShedCount();
                totalFermented += data.getFermentedFeed();
                totalPowder += data.getPowderFeed();
                totalFeed03 += data.getFeed03();
                totalFeed05 += data.getFeed05();
                totalFeed10 += data.getFeed10();
                totalWater += data.getWater();

                double rowAverage = data.getAverage(fermentedIncludedInAverage);
                double rowShedCount = data.getShedCount();
                if (rowAverage > 0 && rowShedCount > 0) {
                    includedShedCount += rowShedCount;
                    includedFeedTotal += rowAverage * rowShedCount;
                }
            }

            footerTotals.put("shed_count", totalShedCount);
            footerTotals.put("fermented", totalFermented);
            footerTotals.put("powder", totalPowder);
            footerTotals.put("feed03", totalFeed03);
            footerTotals.put("feed05", totalFeed05);
            footerTotals.put("feed10", totalFeed10);
            footerTotals.put("water", totalWater);
            footerTotals.put("average_feed",
                    includedShedCount > 0 ? includedFeedTotal / includedShedCount : 0);

            updateErrorDisplay();
            if (table != null) {
                table.notifyAllChanged();
            }

        } catch (Exception e) {
            Log.e(TAG, "更新统计行异常: " + e.getMessage());
        }
    }

    // 标题栏误差显示
    private void updateErrorDisplay() {
        try {
            double sumIngredients = 0;
            double sumWeighedRounded = 0;
            boolean hasData = false;

            for (FeedData data : dataList) {
                double fermented = data.getFermentedFeed();
                double powder = data.getPowderFeed();
                double feed03 = data.getFeed03();
                double feed05 = data.getFeed05();
                double feed10 = data.getFeed10();
                double water = data.getWater();

                sumIngredients += fermented + powder + feed03 + feed05 + feed10 + water;

                if (data.getShedCount() > 0 && data.getWeighedFeed() > 0) {
                    double weighedRounded = Math.round(data.getWeighedFeed() * 100.0) / 100.0;
                    sumWeighedRounded += weighedRounded * data.getShedCount();
                }

                if (fermented != 0 || powder != 0 || feed03 != 0 || feed05 != 0 || feed10 != 0 || water != 0) {
                    hasData = true;
                }
            }

            double error = sumIngredients - sumWeighedRounded;

            if (tvErrorDisplay != null) {
                if (!hasData || Math.abs(error) < 0.005) {
                    tvErrorDisplay.setText("");
                } else {
                    String text;
                    if (error >= 0) {
                        text = "+" + String.format(Locale.ROOT, "%.2f", error);
                    } else {
                        text = String.format(Locale.ROOT, "%.2f", error);
                    }
                    if (text.length() > 5) {
                        text = text.substring(0, 5);
                    }
                    tvErrorDisplay.setText(text);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "更新误差显示异常: " + e.getMessage());
        }
    }

    // ==================== 平板自适应 ====================

    private void initTabletAdaptiveSettings() {
        isTabletMode = TabletLayout.isTabletDevice(this);
        float density = getResources().getDisplayMetrics().density;
        if (isTabletMode) {
            adaptiveColumnWidths = TabletLayout.computeWidths(
                    columnWidths, columnIds, visibleColumns(), getScreenWidth(), density);
        } else {
            adaptiveColumnWidths = TabletLayout.fixedWidths(columnWidths, density);
        }
    }

    private void recalcAdaptiveWidths() {
        float density = getResources().getDisplayMetrics().density;
        adaptiveColumnWidths = TabletLayout.computeWidths(
                columnWidths, columnIds, visibleColumns(), getScreenWidth(), density);
    }

    /** 当前可见性数组，缺失的列按可见处理。 */
    private boolean[] visibleColumns() {
        boolean[] visible = new boolean[columnIds.length];
        for (int i = 0; i < columnIds.length; i++) {
            Boolean value = columnVisibility.get(columnIds[i]);
            visible[i] = value == null || value;
        }
        return visible;
    }



    private int getScreenWidth() {
        try {
            View mainLayout = findViewById(R.id.main_layout);
            if (mainLayout != null && mainLayout.getWidth() > 0) {
                return mainLayout.getWidth();
            }
            return getResources().getDisplayMetrics().widthPixels;
        } catch (Exception e) {
            Log.e(TAG, "获取屏幕宽度异常: " + e.getMessage());
            return 1080;
        }
    }

    // ==================== 视图接线 ====================

    private void initViews() {
        try {
            table = findViewById(R.id.mix_calc_table);
            tvErrorDisplay = findViewById(R.id.tv_error_display);

            // 菜单按钮（下拉：快捷设置 / 隐私政策 / 用户协议 / 使用帮助）
            View menuBtn = findViewById(R.id.btn_menu);
            menuBtn.setOnClickListener(v -> showMainMenu());
        } catch (Exception e) {
            Log.e(TAG, "initViews异常: " + e.getMessage());
        }
    }

    private void initData() {
        batches.clear();
        for (int i = 0; i < MixCalcPrefs.BATCH_COUNT; i++) {
            batches.add(new Batch(TOTAL_ROWS));
        }
        currentBatchIndex = 0;
        bindCurrentBatch();
    }

    // 按 columnIds、adaptiveColumnWidths、可见和固定状态，重搭引擎的列配置
    private void applyColumnConfig() {
        if (table == null) {
            return;
        }

        List<TableColumn> cols = new ArrayList<>();
        for (int i = 0; i < columnIds.length; i++) {
            int width = (adaptiveColumnWidths != null && i < adaptiveColumnWidths.length)
                    ? adaptiveColumnWidths[i] : dpToPx(columnWidths[i]);
            cols.add(createTableColumn(columnIds[i], width));
        }

        TableColumnConfig cfg = new TableColumnConfig(cols);
        cfg.setFixed("seq", true);
        for (int i = 1; i < columnIds.length; i++) {
            String id = columnIds[i];
            Boolean visible = columnVisibility.get(id);
            Boolean fixed = columnFixed.get(id);
            cfg.setVisible(id, visible == null || visible);
            cfg.setFixed(id, fixed != null && fixed);
        }

        tableConfig = cfg;
        table.setColumnConfig(cfg);
    }

    private TableColumn createTableColumn(String id, int widthPx) {
        boolean editable = isEditableColumn(id);
        int color = Color.BLACK;
        int inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

        if ("seq".equals(id)) {
            color = 0xFF666666;
        } else if ("average_feed".equals(id) || "water".equals(id) || "weighed_feed".equals(id)) {
            color = 0xFF0000FF;
        }

        if ("shed_number".equals(id)) {
            inputType = InputType.TYPE_CLASS_TEXT;
        }

        return new TableColumn(id, widthPx, editable, color, inputType);
    }

    private void rebuildCompleteTable() {
        try {
            if (isTabletMode) {
                recalcAdaptiveWidths();
            }
            applyColumnConfig();
            if (table != null) {
                table.notifyAllChanged();
            }
            updateBatchTitle();
            updateFooterRow();
        } catch (Exception e) {
            Log.e(TAG, "重建完整表格异常: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.mix_toast_rebuild_fail), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 列可见性 / 固定状态 ====================

    private void initColumnVisibility() {
        try {
            columnVisibility.put("seq", true);
            for (int i = 1; i < columnIds.length; i++) {
                columnVisibility.put(columnIds[i], true);
            }

        } catch (Exception e) {
            Log.e(TAG, "initColumnVisibility异常: " + e.getMessage());
        }
    }

    private void initColumnFixedState() {
        try {
            columnFixed.put("seq", true);
            for (int i = 1; i < columnIds.length; i++) {
                columnFixed.put(columnIds[i], false);
            }
        } catch (Exception e) {
            Log.e(TAG, "initColumnFixedState异常: " + e.getMessage());
        }
    }

    private void loadColumnVisibility() {
        boolean forceVisible = isTabletDevice(this);
        for (int i = 0; i < columnIds.length; i++) {
            String id = columnIds[i];
            Boolean stored = columnVisibility.get(id);
            columnVisibility.put(id, forceVisible || stored == null || stored);
        }
    }

    private void loadColumnFixedState() {
        for (int i = 0; i < columnIds.length; i++) {
            String id = columnIds[i];
            Boolean stored = columnFixed.get(id);
            columnFixed.put(id, stored != null ? stored : "seq".equals(id));
        }
    }

    private void saveColumnVisibility() {
        saveAllData();
    }

    private void saveColumnFixedState() {
        saveAllData();
    }

    // 保存全局水百分比
    private void saveGlobalWaterPercentage() {
        saveAllData();
    }

    // 保存发酵料参与水分计算设置
    private void saveFermentedIncludedInWater() {
        saveAllData();
    }

    // 保存发酵料参与平均吃料计算设置
    private void saveFermentedIncludedInAverage() {
        saveAllData();
    }

    // 保存所有数据
    // ==================== 批次 ====================

    /** 把当前批次的设置与行数据绑定到视图层字段。 */
    /** 把当前批次的设置、列配置与行数据绑定到视图层字段。 */
    private void bindCurrentBatch() {
        if (batches.isEmpty()) {
            return;
        }
        Batch batch = batches.get(currentBatchIndex);
        dataList = batch.getRows();
        globalWaterPercentage = batch.getWaterPercentage();
        fermentedIncludedInWater = batch.isFermentedInWater();
        fermentedIncludedInAverage = batch.isFermentedInAverage();
        columnVisibility.clear();
        columnVisibility.putAll(batch.getColumnVisibility());
        columnFixed.clear();
        columnFixed.putAll(batch.getColumnFixed());
        loadColumnVisibility();
        loadColumnFixedState();
        if (table != null) {
            table.setRows(dataList);
        }
        updateBatchTitle();
    }

    /** 把视图层的水比/发酵开关写回当前批次。 */
    /** 把视图层的设置与列配置写回当前批次。 */
    private void pushSettingsToBatch() {
        if (batches.isEmpty()) {
            return;
        }
        Batch batch = batches.get(currentBatchIndex);
        batch.setWaterPercentage(globalWaterPercentage);
        batch.setFermentedInWater(fermentedIncludedInWater);
        batch.setFermentedInAverage(fermentedIncludedInAverage);
        batch.getColumnVisibility().clear();
        batch.getColumnVisibility().putAll(columnVisibility);
        batch.getColumnFixed().clear();
        batch.getColumnFixed().putAll(columnFixed);
    }

    /** 按批次自己的设置重算除首行外的所有行。 */
    private void recalcBatch(Batch batch) {
        for (int i = 0; i < batch.getRows().size(); i++) {
            if (i != FIRST_ROW) {
                batch.getRows().get(i).updateAllCalculations(
                        batch.getWaterPercentage(),
                        batch.isFermentedInWater(),
                        batch.isFermentedInAverage());
            }
        }
    }

    /** 切换批次：先提交编辑并保存，再换数据源重建。 */
    private void switchBatch(int index) {
        if (index == currentBatchIndex || index < 0 || index >= batches.size()) {
            return;
        }
        closeEditorAndIme();
        pushSettingsToBatch();
        saveAllData();

        currentBatchIndex = index;
        bindCurrentBatch();
        recalcBatch(batches.get(currentBatchIndex));
        recomputeFirstRowRemainder();
        rebuildCompleteTable();
        if (table != null) {
            table.resetScroll();
        }
        saveAllData();
    }

    /** 标题里用的两位批次号。 */
    private String batchNumber(int index) {
        return index == 0 ? "01" : "02";
    }

    private String batchLabel(int index) {
        return getString(index == 0 ? R.string.mix_batch_01 : R.string.mix_batch_02);
    }

    /** 标题栏：原标题 + 当前批次编号。 */
    /** 标题栏：原标题 + 当前批次编号（批次号缩小，避免标题被截断）。 */
    /** 标题栏：原标题 + 批次号（01 / 02）。 */
    /** 标题栏：批次 01 保持原标题，批次 02 追加 02。 */
    private void updateBatchTitle() {
        TextView title = findViewById(R.id.toolbar);
        if (title == null || batches.isEmpty()) {
            return;
        }
        String base = getString(R.string.title_mix_calculator);
        title.setText(currentBatchIndex == 0
                ? base
                : base + " " + batchNumber(currentBatchIndex));
    }

    private void saveAllData() {
        pushSettingsToBatch();
        MixCalcPrefs.saveBatches(this, batches, currentBatchIndex);
    }

    @android.annotation.SuppressLint("InflateParams")
    private void showStyledConfirmDialog70(String title, String message, String[] buttonTexts, final int[] buttonColors, final DialogInterface.OnClickListener[] listeners) {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_confirm, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        tvMessage.setText(message);

        LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
        for (int i = 0; i < buttonTexts.length; i++) {
            Button btn = new Button(this);
            btn.setText(buttonTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            btn.setTextColor(buttonColors != null && i < buttonColors.length ? buttonColors[i] : 0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < buttonTexts.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(0xFFE0E0E0);
                buttonLayout.addView(divider);
            }
            final int index = i;
            btn.setOnClickListener(v -> {
                if (listeners != null && index < listeners.length && listeners[index] != null) {
                    listeners[index].onClick(dialog, index);
                }
                dialog.dismiss();
            });
            buttonLayout.addView(btn, lp);
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout((int)(screenWidth * 0.7), ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    // 表头弹窗：PopupWindow 贴在被点表头格左下角，位置跟"换料比例"弹窗一样。
    // 用 PopupWindow 而不是 Dialog，是因为 Dialog 的窗口默认 gravity 是 CENTER，
    // 改成 NO_GRAVITY 还是会被系统重新居中，贴不稳格子。
    private void showAnchoredListDialog(String title, String[] items, Rect cellRect,
                                        final DialogInterface.OnClickListener listener) {
        try {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackgroundResource(R.drawable.bg_dialog);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextSize(18);
            tvTitle.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            tvTitle.setTextColor(0xFF000000);
            tvTitle.setGravity(Gravity.CENTER);
            tvTitle.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(12));
            container.addView(tvTitle);

            container.addView(createDivider());

            final PopupWindow popup = new PopupWindow(container);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);
            popup.setElevation(10);

            for (int i = 0; i < items.length; i++) {
                final int index = i;
                TextView itemTv = new TextView(this);
                itemTv.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(40)));
                itemTv.setText(items[i]);
                itemTv.setTextSize(15);
                itemTv.setTextColor(0xFF333333);
                itemTv.setGravity(Gravity.CENTER);
                itemTv.setClickable(true);
                itemTv.setFocusable(true);
                itemTv.setOnClickListener(v -> {
                    popup.dismiss();
                    if (listener != null) {
                        // DialogInterface 参数传 null：各调用方都不使用该参数（关闭由 PopupWindow 负责）
                        listener.onClick(null, index);
                    }
                });
                container.addView(itemTv);
                if (i < items.length - 1) {
                    container.addView(createDivider());
                }
            }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            popup.setWidth(Math.min(dm.widthPixels, dpToPx(180)));
            popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            int[] loc = new int[2];
            if (table != null) {
                table.getLocationOnScreen(loc);
            }
            int x = loc[0] + (cellRect != null ? cellRect.left : 0);
            int y = loc[1] + (cellRect != null ? cellRect.bottom : 0);
            popup.showAtLocation(table, Gravity.NO_GRAVITY, x, y);
        } catch (Exception e) {
            Log.e(TAG, "showAnchoredListDialog: " + e.getMessage(), e);
        }
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);
        return divider;
    }

    // 显示发酵料计算设置的对话框（四个选项）
    private void showFermentedCalculationDialog(Rect cellRect) {
        try {
            String[] options = new String[4];

            String incWater = getString(R.string.mix_ferment_include_water);
            String excWater = getString(R.string.mix_ferment_exclude_water);
            String incAvg = getString(R.string.mix_ferment_include_avg);
            String excAvg = getString(R.string.mix_ferment_exclude_avg);
            if (fermentedIncludedInWater) {
                options[0] = "● " + incWater;
                options[1] = "○ " + excWater;
            } else {
                options[0] = "○ " + incWater;
                options[1] = "● " + excWater;
            }
            if (fermentedIncludedInAverage) {
                options[2] = "● " + incAvg;
                options[3] = "○ " + excAvg;
            } else {
                options[2] = "○ " + incAvg;
                options[3] = "● " + excAvg;
            }

            showAnchoredListDialog(getString(R.string.mix_title_fermented_setting), options, cellRect, (dialog, which) -> {
                boolean needRefresh = false;

                switch (which) {
                    case 0:
                        if (!fermentedIncludedInWater) {
                            fermentedIncludedInWater = true;
                            needRefresh = true;
                        }
                        break;
                    case 1:
                        if (fermentedIncludedInWater) {
                            fermentedIncludedInWater = false;
                            needRefresh = true;
                        }
                        break;
                    case 2:
                        if (!fermentedIncludedInAverage) {
                            fermentedIncludedInAverage = true;
                            needRefresh = true;
                        }
                        break;
                    case 3:
                        if (fermentedIncludedInAverage) {
                            fermentedIncludedInAverage = false;
                            needRefresh = true;
                        }
                        break;
                }

                if (needRefresh) {
                    saveFermentedIncludedInWater();
                    saveFermentedIncludedInAverage();
                    updateAllCalculations();
                    String status = getString(R.string.mix_setting_status);
                    status += fermentedIncludedInWater ? getString(R.string.mix_ferment_include_water) : getString(R.string.mix_ferment_exclude_water);
                    status += "，";
                    status += fermentedIncludedInAverage ? getString(R.string.mix_ferment_include_avg) : getString(R.string.mix_ferment_exclude_avg);
                    Toast.makeText(MixCalcActivity.this, status, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MixCalcActivity.this, getString(R.string.mix_toast_no_change), Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "显示发酵料计算设置对话框异常: " + e.getMessage());
            Toast.makeText(this, getString(R.string.mix_toast_setting_fail), Toast.LENGTH_SHORT).show();
        }
    }

    // 计算有数据的行数
    private int getFilledRowCount() {
        int count = 0;
        for (FeedData data : dataList) {
            if (data.getShedCount() > 0) {
                count++;
            }
        }
        return count;
    }

    private void resetAllData() {
        try {
            for (FeedData data : dataList) {
                data.setShedNumber("");
                data.setShedCountDirectly(0, false);
                data.setShedCountBase(0);
                data.setShedCountBaseSum(0);
                data.setFermentedFeed(0);
                data.setPowderFeed(0);
                data.setFeed03(0);
                data.setFeed05(0);
                data.setFeed10(0);
                data.setWater(0);
                data.setWeighedFeed(0);
                data.setAverage(0);
                data.setShedCountManuallyModified(false);
                data.setAverageManuallyModified(false);
                data.setSwitchRatio(0);
            }

            rebuildCompleteTable();
            saveAllData();

            Toast.makeText(this, getString(R.string.mix_toast_reset_done), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "重置数据异常: " + e.getMessage());
        }
    }

    // 长按较大料格（或只有一档时它的“大一号”）：竖直抽屉式菜单选择换料比例(10%~90%)，当前比例打勾
    private void showSwitchRatioDrawer(final FeedData data, final int row, final Rect cellRect) {
        try {
            View popupView = getLayoutInflater().inflate(R.layout.popup_switch_ratio, null);
            LinearLayout container = popupView.findViewById(R.id.ratio_items_container);
            container.removeAllViews();

            final PopupWindow popup = new PopupWindow(popupView);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);
            popup.setElevation(10);

            double current = data.getSwitchRatio();

            for (int p = 10; p <= 90; p += 10) {
                final double ratio = p / 100.0;

                LinearLayout itemRow = new LinearLayout(this);
                itemRow.setOrientation(LinearLayout.HORIZONTAL);
                itemRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(40)));
                itemRow.setGravity(Gravity.CENTER_VERTICAL);
                itemRow.setPadding(dpToPx(16), 0, dpToPx(16), 0);
                itemRow.setClickable(true);
                itemRow.setFocusable(true);

                TextView valueTv = new TextView(this);
                valueTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                valueTv.setText(p + "%");
                valueTv.setTextSize(15);
                valueTv.setTextColor(0xFF333333);
                valueTv.setGravity(Gravity.CENTER);
                itemRow.addView(valueTv);

                TextView checkTv = new TextView(this);
                checkTv.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(24), ViewGroup.LayoutParams.MATCH_PARENT));
                checkTv.setText(getString(R.string.mix_switch_checked));
                checkTv.setTextSize(16);
                checkTv.setTextColor(0xFF2E7D32);
                checkTv.setGravity(Gravity.CENTER);
                checkTv.setVisibility(Math.abs(current - ratio) < 0.0001 ? View.VISIBLE : View.INVISIBLE);
                itemRow.addView(checkTv);

                itemRow.setOnClickListener(v -> {
                    data.setSwitchRatio(ratio);
                    // 选完立即重算一次：该行每棚平均不变，料量按新比例分到两档
                    applySwitchRatioNow(row);
                    popup.dismiss();
                });

                container.addView(itemRow);

                if (p < 90) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(0xFFE0E0E0);
                    container.addView(divider);
                }
            }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            popup.setWidth(Math.min(dm.widthPixels, dpToPx(160)));
            popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            int[] loc = new int[2];
            if (table != null) {
                table.getLocationOnScreen(loc);
            }
            int x = loc[0] + (cellRect != null ? cellRect.left : 0);
            int y = loc[1] + (cellRect != null ? cellRect.bottom : 0);
            popup.showAtLocation(table, Gravity.NO_GRAVITY, x, y);
        } catch (Exception e) {
            Log.e(TAG, "showSwitchRatioDrawer: " + e.getMessage(), e);
        }
    }

    // 检查单元格是否应该启用
    private boolean isCellEnabled(String columnId, FeedData data, int rowIndex) {
        if ("shed_number".equals(columnId) || "shed_count".equals(columnId)) {
            return true;
        } else if ("average_feed".equals(columnId)) {
            return data.canEditAverage();
        } else {
            return data.getShedCount() > 0;
        }
    }

    private boolean isNonFermentedFeedColumn(String columnId) {
        for (String c : NF_FEED_COLUMNS) {
            if (c.equals(columnId)) {
                return true;
            }
        }
        return false;
    }

    // 非发酵料列的下标（0=粉 1=0.3 2=0.5 3=1.0），不是非发酵料列返回 -1
    private int nonFermentedIndex(String columnId) {
        for (int i = 0; i < NF_FEED_COLUMNS.length; i++) {
            if (NF_FEED_COLUMNS[i].equals(columnId)) {
                return i;
            }
        }
        return -1;
    }

    private double getFeedValueByColumn(FeedData data, String columnId) {
        switch (columnId) {
            case "powder": return data.getPowderFeed();
            case "feed03": return data.getFeed03();
            case "feed05": return data.getFeed05();
            case "feed10": return data.getFeed10();
            default: return 0;
        }
    }

    // 长按换料的“较大那档”：
    //   恰好两档有数据 → 两者中较大的（原行为）
    //   只有一档有数据 → 它的“大一号”（粉→0.3 / 0.3→0.5 / 0.5→1.0）；只有 1.0 有数据则无处可换
    //   0 档或 ≥3 档 → null
    private String getSwitchTargetColumn(FeedData data) {
        if (data == null) {
            return null;
        }
        int count = 0;
        int lastIdx = -1;
        for (int i = 0; i < NF_FEED_COLUMNS.length; i++) {
            if (getFeedValueByColumn(data, NF_FEED_COLUMNS[i]) > 0) {
                count++;
                lastIdx = i;
            }
        }
        if (count == 2) {
            return NF_FEED_COLUMNS[lastIdx];
        }
        if (count == 1) {
            int next = lastIdx + 1;
            return next < NF_FEED_COLUMNS.length ? NF_FEED_COLUMNS[next] : null;
        }
        return null;
    }

    // 该料格是否为"已设置换料比例"的提示格（“较大那档”）
    private boolean isSwitchRatioCell(FeedData data, String columnId) {
        if (data == null || data.getSwitchRatio() <= 0) {
            return false;
        }
        String target = getSwitchTargetColumn(data);
        return target != null && target.equals(columnId);
    }

    // 选完换料比例马上重算：把该行当前的每棚平均钉住（平均不变），
    // 再按新比例把总量分给两档（大一号 = 总量×ratio），派出水量和称料。
    private void applySwitchRatioNow(int row) {
        FeedData data = getData(row);
        if (data == null) {
            return;
        }
        double avg = data.getAverage(fermentedIncludedInAverage);
        if (!data.isAverageManuallyModified() && avg > 0) {
            data.setAverage(avg);
            data.setAverageManuallyModified(true);
        }
        if (row == FIRST_ROW) {
            // 第 1 行由 recomputeFirstRowRemainder() 统一处理（棚数 / 平均 / 料量）
            recomputeFirstRowRemainder();
        } else {
            data.recalculateDerivedOnly(globalWaterPercentage,
                    fermentedIncludedInWater, fermentedIncludedInAverage);
            recomputeFirstRowRemainder();
        }
        if (table != null) {
            table.notifyAllChanged();
        }
        updateFooterRow();
        saveAllData();
    }

    // 更新所有计算
    private void updateAllCalculations() {
        try {
            for (int i = FIRST_ROW + 1; i < dataList.size(); i++) {
                FeedData data = dataList.get(i);
                // 重新计算所有行，传入两个标志
                data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
            }
            // 第 1 行由 recomputeFirstRowRemainder() 统一处理（棚数 / 每棚平均 / 料量一起算）
            recomputeFirstRowRemainder();

            if (table != null) {
                table.notifyAllChanged();
            }
            updateFooterRow();
            saveAllData();

        } catch (Exception e) {
            Log.e(TAG, "更新所有计算异常: " + e.getMessage());
        }
    }

    private String getCellInitialValue(FeedData data, String columnId) {
        switch (columnId) {
            case "shed_number":
                return data.getShedNumber();
            case "shed_count":
                return data.getShedCount() == 0 ? "" : String.valueOf(data.getShedCount());
            case "average_feed":
                return formatNumber(data.getAverage(fermentedIncludedInAverage));
            case "fermented":
                return data.getFermentedFeed() == 0 ? "" : formatNumber(data.getFermentedFeed());
            case "powder":
                return data.getPowderFeed() == 0 ? "" : formatNumber(data.getPowderFeed());
            case "feed03":
                return data.getFeed03() == 0 ? "" : formatNumber(data.getFeed03());
            case "feed05":
                return data.getFeed05() == 0 ? "" : formatNumber(data.getFeed05());
            case "feed10":
                return data.getFeed10() == 0 ? "" : formatNumber(data.getFeed10());
            default:
                return "";
        }
    }

    private String getCellDisplayValue(FeedData data, String columnId) {
        switch (columnId) {
            case "seq":
                return "";
            case "shed_number":
                String shedName = data.getShedNumber();
                return (shedName == null || shedName.isEmpty()) ? "" : shedName;
            case "average_feed":
                // 使用新的方法获取平均吃料值，考虑是否包含发酵料
                double averageValue = data.getAverage(fermentedIncludedInAverage);
                return averageValue == 0 ? "" : formatNumber(averageValue);
            case "water":
                double waterValue = data.getWater();
                return waterValue == 0 ? "" : formatNumber(waterValue);
            case "weighed_feed":
                double weighedFeed = data.getWeighedFeed();
                int shedCount = data.getShedCount();
                if (shedCount > 0 && weighedFeed > 0) {
                    if (weighedFeed == (int) weighedFeed) {
                        return String.valueOf((int) weighedFeed) + "×" + shedCount;
                    } else {
                        return String.format(java.util.Locale.ROOT, "%.2f", weighedFeed) + "×" + shedCount;
                    }
                }
                return "";
            default:
                return "";
        }
    }

    private String formatNumber(double value) {
        if (value == 0) {
            return "";
        }

        if (value == (int) value) {
            return String.valueOf((int) value);
        } else {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }

    private boolean isEditableColumn(String columnId) {
        return "shed_number".equals(columnId) || 
            "shed_count".equals(columnId) ||
            "average_feed".equals(columnId) ||
            "fermented".equals(columnId) ||
            "powder".equals(columnId) ||
            "feed03".equals(columnId) ||
            "feed05".equals(columnId) ||
            "feed10".equals(columnId);
    }

    // ==================== 列操作 ====================

    private void showColumnOperationMenu(final int columnIndex, final String columnId, final Rect cellRect) {
        try {
            boolean isVisible = columnVisibility.get(columnId);
            boolean isFixed = columnFixed.get(columnId);

            List<String> menuItems = new ArrayList<String>();

            if (isFixed) {
                menuItems.add(getString(R.string.mix_menu_unfix_col));
            } else {
                menuItems.add(getString(R.string.mix_menu_fix_col));
            }

            if (isVisible) {
                menuItems.add(getString(R.string.mix_menu_hide_col));
            } else {
                menuItems.add(getString(R.string.mix_menu_show_col));
            }

            menuItems.add(getString(R.string.mix_menu_show_all));
            menuItems.add(getString(R.string.mix_menu_unfix_all));

            final String[] items = menuItems.toArray(new String[0]);

            showAnchoredListDialog(getString(R.string.mix_menu_col_op_format, columnNames[columnIndex]), items, cellRect, (dialog, which) -> {
                handleColumnOperation(columnIndex, columnId, items[which]);
            });

        } catch (Exception e) {
            Log.e(TAG, "显示列操作菜单异常: " + e.getMessage());
        }
    }

    private void handleColumnOperation(int columnIndex, String columnId, String operation) {
        try {
            String colName = columnNames[columnIndex];
            if (operation.equals(getString(R.string.mix_menu_fix_col)) || operation.equals(getString(R.string.mix_menu_unfix_col))) {
                boolean newFixedState = !columnFixed.get(columnId);
                columnFixed.put(columnId, newFixedState);
                saveColumnFixedState();
                String msg = colName + (newFixedState ? getString(R.string.mix_col_fixed) : getString(R.string.mix_col_unfixed));
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } else if (operation.equals(getString(R.string.mix_menu_hide_col)) || operation.equals(getString(R.string.mix_menu_show_col))) {
                boolean newVisibility = !columnVisibility.get(columnId);
                columnVisibility.put(columnId, newVisibility);
                saveColumnVisibility();
                String msg = colName + (newVisibility ? getString(R.string.mix_col_shown) : getString(R.string.mix_col_hidden));
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } else if (operation.equals(getString(R.string.mix_menu_show_all))) {
                showAllColumns();
                return;
            } else if (operation.equals(getString(R.string.mix_menu_unfix_all))) {
                unfixAllColumns();
                return;
            }

            rebuildCompleteTable();

        } catch (Exception e) {
            Log.e(TAG, "处理列操作异常: " + e.getMessage());
        }
    }

    private void showAllColumns() {
        try {
            boolean hasChanged = false;

            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                if (!columnVisibility.get(columnId)) {
                    columnVisibility.put(columnId, true);
                    hasChanged = true;
                }
            }

            if (hasChanged) {
                saveColumnVisibility();
                rebuildCompleteTable();
                Toast.makeText(this, getString(R.string.mix_toast_shown_all), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "显示所有列异常: " + e.getMessage());
        }
    }

    private void unfixAllColumns() {
        try {
            boolean hasChanged = false;

            for (int i = 1; i < columnIds.length; i++) {
                String columnId = columnIds[i];
                if (columnFixed.get(columnId)) {
                    columnFixed.put(columnId, false);
                    hasChanged = true;
                }
            }

            if (hasChanged) {
                saveColumnFixedState();
                rebuildCompleteTable();
                Toast.makeText(this, getString(R.string.mix_toast_unfixed_all), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "取消所有固定列异常: " + e.getMessage());
        }
    }

    private void showWaterPercentageDialog(Rect cellRect) {
        try {
            final String[] percentages = {"10", "15", "20", "25", "30", "35", "40"};
            final String[] displayTexts = new String[percentages.length];
            for (int j = 0; j < percentages.length; j++) {
                displayTexts[j] = getString(R.string.water_percentage_header, Integer.parseInt(percentages[j]));
            }

            showAnchoredListDialog(getString(R.string.mix_title_water_pct), displayTexts, cellRect, (dialog, which) -> {
                try {
                    globalWaterPercentage = Integer.parseInt(percentages[which]);
                    saveGlobalWaterPercentage();
                    // updateAllCalculations 会重绘（表头水百分比也由 provider 重新取）
                    updateAllCalculations();
                    Toast.makeText(MixCalcActivity.this, getString(R.string.mix_water_pct_format, globalWaterPercentage), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "选择水百分比异常: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "显示水百分比对话框异常: " + e.getMessage());
        }
    }

    private int dpToPx(int dp) {
        try {
            float density = getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        } catch (Exception e) {
            return dp;
        }
    }

    // 显示重置确认对话框
    private void showResetConfirmationDialog() {
        try {
            int filledRows = getFilledRowCount();
            String message = filledRows > 0
                ? getString(R.string.mix_reset_msg_has_data, filledRows)
                : getString(R.string.mix_reset_msg_no_data);

            showStyledConfirmDialog70(getString(R.string.mix_title_confirm_reset), message,
                new String[]{getString(R.string.btn_cancel), getString(R.string.mix_btn_confirm_reset)},
                new int[]{0xFF666666, 0xFFD32F2F},
                new DialogInterface.OnClickListener[]{null, (dialog, which) -> resetAllData()});

        } catch (Exception e) {
            Log.e(TAG, "显示重置确认对话框异常: " + e.getMessage());
            resetAllData();
        }
    }

    // ==================== 帮助面板（棚数上限 + 操作手册） ====================

    // 帮助入口：直接打开 assets/help 下的 HTML 使用帮助页
    private void showHelpPanel() {
        openAssetPage("help/index.html");
    }

    // 打开 assets 里的静态页（隐私政策、用户协议、使用帮助）
    private void openAssetPage(String assetPath) {
        try {
            Intent intent = new Intent(this, com.shrimpfarm.app.AssetWebViewActivity.class);
            intent.putExtra(com.shrimpfarm.app.AssetWebViewActivity.EXTRA_ASSET_PATH, assetPath);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "打开页面异常: " + e.getMessage());
        }
    }

    // 打开菜单/弹窗前先把正在编辑的格收掉、键盘收起。
    // 否则弹窗抢焦点时 IME 再收，表格会在弹窗后面先缩一下，看起来就是左上角闪一下。
    private void closeEditorAndIme() {
        if (table != null) {
            table.endEdit();
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View decor = getWindow() == null ? null : getWindow().getDecorView();
            if (decor != null) {
                imm.hideSoftInputFromWindow(decor.getWindowToken(), 0);
            }
        }
    }

    // 批次选择：两个固定批次，当前批次预先选中。切换时自动提交未完成的编辑。
    private void showBatchDialog() {
        closeEditorAndIme();
        final String[] labels = {getString(R.string.mix_batch_01), getString(R.string.mix_batch_02)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.mix_batch_dialog_title)
                .setSingleChoiceItems(labels, currentBatchIndex, (dialog, which) -> {
                    dialog.dismiss();
                    switchBatch(which);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
    // 语言选择：单选列表 + 取消，选完存偏好并重建界面。
    private void showLanguageDialog() {
        try {
            closeEditorAndIme();

            final String[] keys = {
                    com.shrimpfarm.app.LocaleManager.LANG_SYSTEM,
                    com.shrimpfarm.app.LocaleManager.LANG_ZH,
                    com.shrimpfarm.app.LocaleManager.LANG_EN
            };
            String[] labels = {
                    getString(R.string.mix_lang_option_system),
                    getString(R.string.mix_lang_option_zh),
                    getString(R.string.mix_lang_option_en)
            };
            String current = com.shrimpfarm.app.LocaleManager.getSavedLanguage(this);
            int checked = 0;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(current)) { checked = i; break; }
            }

            Dialog dialog = new Dialog(this);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_language, null);
            dialog.setContentView(dialogView);
            dialog.setCanceledOnTouchOutside(true);

            TextView tvTitle = dialogView.findViewById(R.id.tv_title);
            tvTitle.setText(R.string.mix_lang_dialog_title);

            android.widget.ListView lv = dialogView.findViewById(R.id.lv_languages);
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this, R.layout.item_language, labels);
            lv.setAdapter(adapter);
            lv.setItemChecked(checked, true);
            lv.setOnItemClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                applyLanguage(keys[position]);
            });

            Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
            btnCancel.setOnClickListener(v -> dialog.dismiss());

            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // 关掉系统默认的对话框动画：华为上它的阴影/缩放会扫过下面露出的表格
            dialog.getWindow().setWindowAnimations(0);
            dialog.show();
            if (table != null) {
                table.postInvalidateOnAnimation();
            }
        } catch (Exception e) {
            Log.e(TAG, "显示语言选择异常: " + e.getMessage());
        }
    }

    private void applyLanguage(String lang) {
        String current = com.shrimpfarm.app.LocaleManager.getSavedLanguage(this);
        if (lang != null && lang.equals(current)) {
            return;
        }
        com.shrimpfarm.app.LocaleManager.setLanguage(this, lang);
        recreate();
    }

    // ==================== 主菜单下拉（快捷设置 / 隐私政策 / 用户协议 / 使用帮助） ====================

    private void showMainMenu() {
        try {
            // 菜单一弹出就抢焦点，先收掉编辑浮层和键盘，避免表格随后才缩一下。
            closeEditorAndIme();

            View popupView = getLayoutInflater().inflate(R.layout.popup_main_menu, null);

            final PopupWindow popup = new PopupWindow(popupView);
            // 关掉菜单窗口的进出动画，避免和后续语言弹窗同帧切换时带出表格抖动
            popup.setAnimationStyle(0);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);
            popup.setElevation(10);

            // 快捷设置：平板不显示
            if (isTabletMode) {
                popupView.findViewById(R.id.menu_item_quick_settings).setVisibility(View.GONE);
                popupView.findViewById(R.id.menu_divider_quick).setVisibility(View.GONE);
                // 首项被隐藏后由隐私政策顶上：最小高度补回 40dp，分隔线才落在表格 40/80/120 网格上；
                // 字体放大时仍用 wrap_content，让文字能撑高
                View privacyItem = popupView.findViewById(R.id.menu_item_privacy_policy);
                ViewGroup.LayoutParams lp = privacyItem.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                privacyItem.setLayoutParams(lp);
                privacyItem.setMinimumHeight(dpToPx(40));
            } else {
                popupView.findViewById(R.id.menu_item_quick_settings).setOnClickListener(v -> {
                    popup.dismiss();
                    showColumnSettingsPopup();
                });
            }

            popupView.findViewById(R.id.menu_item_batch).setOnClickListener(v -> {
                popup.dismiss();
                showBatchDialog();
            });            popupView.findViewById(R.id.menu_item_privacy_policy).setOnClickListener(v -> {
                popup.dismiss();
                openAssetPage("privacy.html");
            });
            popupView.findViewById(R.id.menu_item_user_agreement).setOnClickListener(v -> {
                popup.dismiss();
                openAssetPage("user.html");
            });
            popupView.findViewById(R.id.menu_item_help).setOnClickListener(v -> {
                popup.dismiss();
                showHelpPanel();
            });
            popupView.findViewById(R.id.menu_item_language).setOnClickListener(v -> {
                popup.dismiss();
                // 菜单窗口先移除，下一帧再弹语言框：两个窗口同一帧切换时，
                // 菜单盖住的那块表格会重新露出，华为上会看到左上角抖一下。
                View main = findViewById(R.id.main_layout);
                if (main != null) {
                    main.post(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            showLanguageDialog();
                        }
                    });
                } else {
                    showLanguageDialog();
                }
            });

            // 版本号：取构建配置里的真实 versionName（app/build.gradle）
            TextView versionTv = popupView.findViewById(R.id.menu_item_version);
            versionTv.setText(getString(R.string.mix_menu_version_format, BuildConfig.VERSION_NAME));

            // 宽度：正常字体下手机 1/3、平板 1/4；字体放大时按 fontScale 加宽（最多屏幕 60%），
            // 避免“版本:v0.3.1”在窄菜单里被挤成每行一个字。高度由 wrap_content 撑开。
            View mainLayout = findViewById(R.id.main_layout);
            int totalWidth = mainLayout != null && mainLayout.getWidth() > 0
                    ? mainLayout.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
            int baseWidth = isTabletMode ? totalWidth / 4 : totalWidth / 3;
            int maxWidth = (int) (totalWidth * 0.6f);
            int popupWidth = Math.min(maxWidth, Math.max(baseWidth, (int) (baseWidth * fontScale)));
            popup.setWidth(popupWidth);
            popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            showPopupUnderTitleBar(popup);
        } catch (Exception e) {
            Log.e(TAG, "显示主菜单异常: " + e.getMessage());
        }
    }

    // ==================== 首次启动隐私同意弹窗 ====================

    // 没同意过隐私政策与使用协议就弹同意框盖在主页上，拒绝就退出。
    // "已同意"按版本记（KEY_PRIVACY_AGREED 存当前 versionCode），升级后要重新同意，
    // 免得老用户升级上来直接跳过。
    private void showPrivacyConsentIfNeeded() {
        final int agreedVersion = MixCalcPrefs.getPrivacyAgreedVersion(this);
        if (agreedVersion >= BuildConfig.VERSION_CODE) {
            return;
        }

        // 按语言选弹窗文案：英文环境读 assets/en 下的英文版，否则用中文版
        boolean isEnglish = com.shrimpfarm.app.LocaleManager.LANG_EN
                .equals(com.shrimpfarm.app.LocaleManager.getEffectiveLanguage(this));
        String consentFile = isEnglish ? "en/privacy_consent.txt" : "隐私政策与使用协议弹窗.txt";

        // 读取弹窗文案；失败时不弹同意框（不能拿空文案完成授权），提示后退出
        String[] lines = readAssetLines(consentFile);
        if (lines.length < 4) {
            Log.e(TAG, "隐私弹窗文案缺失/损坏，行数=" + lines.length);
            Toast.makeText(this, getString(R.string.privacy_consent_load_fail), Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return;
        }

        String title = lines[0];
        String body = lines[1] + "\n\n" + lines[2];
        // 文案里的链接词要跟正文用的语言一致，不能用 getString（另一语言环境下会找不到）。
        final String linkUser = isEnglish ? "User Agreement" : "用户协议";
        final String linkPrivacy = isEnglish ? "Privacy Policy" : "隐私政策";

        try {
            final Dialog dialog = new Dialog(this);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_confirm, null);
            dialog.setContentView(dialogView);
            dialog.setCancelable(false);

            TextView tvTitle = dialogView.findViewById(R.id.tv_title);
            tvTitle.setText(title);

            TextView tvMessage = dialogView.findViewById(R.id.tv_message);
            // 隐私正文单独调小一号，免得差一个字就折行
            tvMessage.setTextSize(13);
            // 两个链接都在正文里，分别定位「用户协议」「隐私政策」的位置套 ClickableSpan。
            // 隐私政策在正文中只出现一次；用户协议也只出现一次，直接 indexOf 即可。
            SpannableString span = new SpannableString(body);
            int userStart = body.indexOf(linkUser);
            int privacyStart = body.indexOf(linkPrivacy);
            if (userStart >= 0) {
                span.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        openAssetPage("user.html");
                    }
                }, userStart, userStart + linkUser.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (privacyStart >= 0) {
                span.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        openAssetPage("privacy.html");
                    }
                }, privacyStart, privacyStart + linkPrivacy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
            tvMessage.setText(span);

            LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
            addConsentButton(dialog, buttonLayout, getString(R.string.privacy_btn_disagree), 0xFFD32F2F,
                    (d, w) -> finishAndRemoveTask());
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
            divider.setBackgroundColor(0xFFE0E0E0);
            buttonLayout.addView(divider);
            addConsentButton(dialog, buttonLayout, getString(R.string.privacy_btn_agree), 0xFF2D8C42,
                    (d, w) -> {
                        MixCalcPrefs.setPrivacyAgreedVersion(this, BuildConfig.VERSION_CODE);
                        d.dismiss();
                    });

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout((int) (screenWidth * 0.85), ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.show();
        } catch (Exception e) {
            // 弹窗构造失败：不能静默放行，明确提示并退出，等待用户重试
            Log.e(TAG, "显示隐私同意弹窗异常: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.privacy_consent_load_fail), Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
        }
    }

    private void addConsentButton(Dialog dialog, LinearLayout buttonLayout, String text, int color,
                                  DialogInterface.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        btn.setTextColor(color);
        btn.setBackgroundResource(android.R.color.transparent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        btn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(dialog, 0);
            }
        });
        buttonLayout.addView(btn, lp);
    }

    // 逐行读 assets 里的文本文件（UTF-8）
    private String[] readAssetLines(String fileName) {
        java.io.BufferedReader reader = null;
        try {
            reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    getAssets().open(fileName), java.nio.charset.StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines.toArray(new String[0]);
        } catch (Exception e) {
            Log.e(TAG, "读取资产文件异常: " + e.getMessage());
            return new String[0];
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ==================== 列设置弹出菜单 ====================

    private void showColumnSettingsPopup() {
        try {
            View popupView = getLayoutInflater().inflate(R.layout.popup_column_settings, null);

            LinearLayout rowsContainer = popupView.findViewById(R.id.settings_rows_container);
            rowsContainer.removeAllViews();

            String[] settingsIds = {"shed_number", "shed_count", "average_feed", "fermented", "powder", "feed03", "feed05", "feed10", "water"};
            String[] settingsNames = {
                getString(R.string.mix_col_shed), getString(R.string.mix_col_count),
                getString(R.string.mix_col_avg_feed), getString(R.string.mix_col_fermented),
                getString(R.string.mix_col_powder), getString(R.string.mix_col_feed03),
                getString(R.string.mix_col_feed05), getString(R.string.mix_col_feed10),
                getString(R.string.mix_col_water)
            };

            Map<String, List<TextView>> toggleViews = new HashMap<>();
            float density = getResources().getDisplayMetrics().density;

            for (int i = 0; i < settingsNames.length; i++) {
                String colId = settingsIds[i];

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (40 * density));
                row.setLayoutParams(rowLp);
                row.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
                row.setGravity(Gravity.CENTER_VERTICAL);

                TextView nameTv = new TextView(this);
                nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                nameTv.setText(settingsNames[i]);
                nameTv.setTextSize(14);
                nameTv.setTextColor(0xFF333333);
                nameTv.setGravity(Gravity.CENTER);
                row.addView(nameTv);

                List<TextView> toggles = new ArrayList<>(4);
                String[] actions = {getString(R.string.mix_btn_fix), getString(R.string.mix_btn_unfix), getString(R.string.mix_btn_hide), getString(R.string.mix_btn_show)};

                for (String action : actions) {
                    TextView btn = new TextView(this);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                    btn.setGravity(Gravity.CENTER);
                    btn.setText(action);
                    btn.setTextSize(12);
                    btn.setClickable(true);
                    btn.setFocusable(true);
                    btn.setTag(new String[]{colId, action});
                    btn.setOnClickListener(v -> {
                        String[] tag = (String[]) v.getTag();
                        String cId = tag[0];
                        String act = tag[1];
                        if (getString(R.string.mix_btn_fix).equals(act)) {
                            columnFixed.put(cId, true);
                        } else if (getString(R.string.mix_btn_unfix).equals(act)) {
                            columnFixed.put(cId, false);
                        } else if (getString(R.string.mix_btn_hide).equals(act)) {
                            columnVisibility.put(cId, false);
                        } else if (getString(R.string.mix_btn_show).equals(act)) {
                            columnVisibility.put(cId, true);
                        }
                        updateSettingsRowStyles(toggleViews.get(cId), cId);
                    });
                    row.addView(btn);
                    toggles.add(btn);
                }

                rowsContainer.addView(row);
                toggleViews.put(colId, toggles);
                updateSettingsRowStyles(toggles, colId);

                if (i < settingsNames.length - 1) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(0xFFE0E0E0);
                    rowsContainer.addView(divider);
                }
            }

            PopupWindow popup = new PopupWindow(popupView);
            columnSettingsPopup = popup;
            popup.setOnDismissListener(() -> columnSettingsPopup = null);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);
            popup.setElevation(10);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int maxWidth = (int) (360 * dm.density);
            popup.setWidth(Math.min(dm.widthPixels, maxWidth));
            popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            popupView.findViewById(R.id.btn_cancel_all).setOnClickListener(v -> popup.dismiss());
            popupView.findViewById(R.id.btn_unfix_all).setOnClickListener(v -> {
                unfixAllColumns();
                reopenColumnSettings(popup);
            });
            popupView.findViewById(R.id.btn_show_all).setOnClickListener(v -> {
                showAllColumns();
                reopenColumnSettings(popup);
            });
            popupView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
                saveColumnVisibility();
                saveColumnFixedState();
                rebuildCompleteTable();
                popup.dismiss();
                Toast.makeText(this, getString(R.string.mix_toast_column_saved), Toast.LENGTH_SHORT).show();
            });

            showPopupUnderTitleBar(popup);

        } catch (Exception e) {
            Log.e(TAG, "showColumnSettingsPopup: " + e.getMessage(), e);
        }
    }

    // 关掉后重开列设置：用 OnDismissListener 接着重开，不用 postDelayed，
    // 不然延迟一叠加会冒出两个弹窗。
    private void reopenColumnSettings(PopupWindow popup) {
        popup.setOnDismissListener(() -> {
            columnSettingsPopup = null;
            if (!isFinishing() && !isDestroyed()) {
                showColumnSettingsPopup();
            }
        });
        popup.dismiss();
    }

    // 把弹窗顶边贴到标题栏下沿、左边缘跟表格对齐，手机平板通用。
    // 用屏幕坐标求差，因为 showAtLocation 的 y 是相对 main_layout 的，
    // 平板 content 被系统栏内缩时会少算偏移，菜单就顶到标题栏上去了。
    private void showPopupUnderTitleBar(PopupWindow popup) {
        View menuBtn = findViewById(R.id.btn_menu);
        View titleBar = findViewById(R.id.title_bar);
        View mainLayout = findViewById(R.id.main_layout);
        if (menuBtn != null && titleBar != null && mainLayout != null) {
            int[] btnLoc = new int[2];
            int[] titleLoc = new int[2];
            int[] mainLoc = new int[2];
            menuBtn.getLocationOnScreen(btnLoc);
            titleBar.getLocationOnScreen(titleLoc);
            mainLayout.getLocationOnScreen(mainLoc);
            int offsetY = (titleLoc[1] + titleBar.getHeight()) - (btnLoc[1] + menuBtn.getHeight());
            int offsetX = mainLoc[0] - btnLoc[0];
            popup.showAsDropDown(menuBtn, offsetX, offsetY);
        } else {
            popup.showAtLocation(mainLayout, Gravity.NO_GRAVITY, 0, dpToPx(60));
        }
    }

    private void updateSettingsRowStyles(List<TextView> toggles, String columnId) {
        if (toggles == null || toggles.size() < 4) return;
        boolean isFixed = columnFixed.get(columnId);
        boolean isVisible = columnVisibility.get(columnId);
        updateToggleStyle(toggles.get(0), isFixed);
        updateToggleStyle(toggles.get(1), !isFixed);
        updateToggleStyle(toggles.get(2), !isVisible);
        updateToggleStyle(toggles.get(3), isVisible);
    }

    private void updateToggleStyle(TextView btn, boolean active) {
        if (active) {
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundResource(R.drawable.bg_button_green);
        } else {
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundResource(R.drawable.bg_button_gray);
        }
    }
}
