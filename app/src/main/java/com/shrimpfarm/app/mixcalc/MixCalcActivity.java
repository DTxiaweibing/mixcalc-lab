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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.shrimpfarm.app.mixcalc.model.RowSettlement;
import com.shrimpfarm.app.mixcalc.model.ShedCoverage;
import com.shrimpfarm.app.mixcalc.model.ShedDeletePlan;
import com.shrimpfarm.app.mixcalc.model.ShedDraft;
import com.shrimpfarm.app.mixcalc.model.ShedText;
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
import com.shrimpfarm.app.mixcalc.ui.FlowLayout;
import com.shrimpfarm.app.mixcalc.ui.PopupGeometry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    // 正在编辑棚号格：每敲一个字符就全表重算（棚数、料量、别人的棚号文本都跟着变）
    private boolean editingShedNumber;
    // 进入棚号编辑那一刻的全表文本快照。每次按键都从快照重新推导，而不是在上一次结果上叠加：
    // "16-40" 打到一半是 "16-4"，会被解析成 4~16 抢走别人的号；没有快照这步就是永久改写，
    // 输完也还不回来。从快照重推 → 半成品只影响这一帧，输完立刻恢复。
    private List<String> shedEditSnapshot;
    private int shedEditRow = -1;

    // 棚数按钮点开的棚号气泡
    private PopupWindow shedListPopup;
    // 待转池：只记号码和来源行，来源行的数据一个字都不动。
    // 只有"点开另一行的气泡 → 菜单里点【转入】"才真正执行；其它任何一次按下都作废。
    private List<Integer> pendingTransferNumbers;
    private int pendingTransferSourceRow = -1;
    // 转出后气泡要自己关掉，这一下关窗不能把刚记下的待转池清掉
    private boolean keepPendingOnPopupDismiss;

    private static final int SHED_ACTION_SELECT_ALL = 0;
    private static final int SHED_ACTION_SELECT_INVERT = 1;
    private static final int SHED_ACTION_DELETE = 2;
    private static final int SHED_ACTION_TRANSFER_OUT = 3;
    private static final int SHED_ACTION_TRANSFER_IN = 4;
    // 超上限提示别每敲一个字弹一次
    private long lastOverLimitToastAt = 0;

    // 列管理
    private final Map<String, Boolean> columnVisibility = new HashMap<String, Boolean>();
    private final Map<String, Boolean> columnFixed = new HashMap<String, Boolean>();
    private PopupWindow columnSettingsPopup;
    // 打开着的主菜单：折叠/展开、分屏、转屏时直接关掉，避免停留在旧宽度/旧状态
    private PopupWindow mainMenuPopup;
    // 上次的主容器宽度：有些设备改窗口大小不触发 onConfigurationChanged，这里兜底重算
    private int lastWindowWidth;
    private final String[] columnIds = {
        "seq", "shed_number", "shed_count", "average_feed", 
        "fermented", "powder", "feed03", "feed05", "feed10", 
        "water", "weighed_feed"
    };

    private String[] columnNames;

    // shed_number 72dp：棚号列字号与其它列一致（16sp），9 字符一行的折叠结果
    // 最宽约 70dp（如 "…9-15 17"），60dp 会把首尾裁掉，故单独加宽
    private final int[] columnWidths = {40, 72, 40, 40, 60, 60, 60, 60, 60, 60, 60};

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
                    // 显示的是“实际拥有”的号，不是用户原文；两者不同时以覆盖结果为准
                    return data.getShedDisplay();
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
            // 顺序录入：前面有空行、这一行又是空的，就不让编辑
            if (isOrderBlocked(row)) {
                return false;
            }
            // 棚数要求本行棚号先填（第一行也一样）
            if (needsShedNumberFirst(row, columnId)) {
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
            // 棚数是按钮：点开看本行棚号气泡，不进编辑
            if ("shed_count".equals(columnId)) {
                showShedListPopup(row);
                return;
            }
            // 编辑由引擎的 beginEdit 处理；这里只负责把被拦的原因告诉用户
            FeedData data = getData(row);
            if (data == null || !isEditableColumn(columnId)) {
                return;
            }
            if (!isCellEnabled(columnId, data, row)) {
                return;
            }
            if (isOrderBlocked(row) || needsShedNumberFirst(row, columnId)) {
                showOrderHintDialog();
            }
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
            editingShedNumber = "shed_number".equals(columnId);
            if (editingShedNumber) {
                // 记下全表原始文本：打字途中所有重算都从这份快照出发，可随时回退
                shedEditRow = row;
                shedEditSnapshot = snapshotShedTexts();
            }
            // 第一次点第一行棚号：告诉用户格式，并给一个"查看帮助"入口
            if (row == 0 && "shed_number".equals(columnId) && !MixCalcPrefs.isShedInputHintShown(MixCalcActivity.this)) {
                MixCalcPrefs.setShedInputHintShown(MixCalcActivity.this);
                showShedInputHint();
            }
        }

        @Override
        public void onCellCommitted(int row, String columnId, String text) {
            // 每个字符都提交：全表重算棚数/料量，别人的棚号文本也跟着按实际归属改写。
            // 半成品区间（"16-4"）不会改坏别人的号——applyShedCoverage 每次从编辑前的
            // 快照重新推导，上一帧的临时结果不带进下一帧。
            commitCell(row, columnId, text);
        }

        @Override
        public void onEditNext(int row, String columnId) {
            moveFocusToNextEditableCell(row, getColumnIndex(columnId));
        }

        @Override
        public void onEditNextRow(int row, String columnId) {
            moveFocusToNextRow(row, columnId);
        }

        @Override
        public void onCellEditEnd(int row, String columnId) {
            // 棚号这一格输完了：把快照作废，按最终文本做一次全表定稿重排
            //（打字途中别人的文本已经是改写过的临时结果，这一遍把它们钉死）
            boolean wasShedNumber = "shed_number".equals(columnId);
            // 原本有棚号、被一个字一个字删到空：这行彻底没棚了，等于用户把这行作废，
            // 要和气泡里删除一样把整行回收掉，不能让表中间留个洞
            boolean emptiedByTyping = false;
            if (wasShedNumber) {
                FeedData rowData = getData(row);
                String before = (shedEditSnapshot != null && row >= 0 && row < shedEditSnapshot.size())
                        ? shedEditSnapshot.get(row) : null;
                emptiedByTyping = before != null && !before.trim().isEmpty()
                        && rowData != null
                        && rowData.getShedNumber().trim().isEmpty()
                        && rowData.getShedCount() <= 0;
            }
            editingShedNumber = false;
            shedEditSnapshot = null;
            shedEditRow = -1;
            if (wasShedNumber) {
                applyRowChange(row);
            }
            // 换格流程正攥着旧行号开新格（begin 先回调这里再落行号），此刻同步删行
            // 会让新格开到错的行上。推迟到本轮交互结束、新格已打开之后再结算：
            // 删行时编辑浮层的行号由 onRowsRecycled 平移，不会错位。
            final Set<Integer> emptied = emptiedByTyping
                    ? Collections.singleton(row) : null;
            if (table != null) {
                table.post(() -> settleZeroRows(emptied));
            } else {
                settleZeroRows(emptied);
            }
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
            rebuildCompleteTable();

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
        // 载入后清一次历史脏数据：棚数为 0 却还留着料的，按新行处理
        settleZeroRows();
        updateFooterRow();
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
        Log.d(TAG, "提交 第" + (row + 1) + "行 " + columnId + " = [" + value + "]");

        switch (columnId) {
            case "shed_number": {
                // 改棚号本质是改棚数：平均保持不变，料量按新棚数重算
                int oldCount = data.getShedCount();
                double oldAverage = data.getAverage(fermentedIncludedInAverage);
                data.setShedNumber(value);
                if (data.getShedCount() != oldCount && oldAverage > 0) {
                    data.setAverageManually(oldAverage, fermentedIncludedInAverage);
                }
                int over = ShedCoverage.findOverLimit(value);
                if (over > 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastOverLimitToastAt > 1500) {
                        lastOverLimitToastAt = now;
                        Toast.makeText(this,
                                getString(R.string.mix_shed_over_limit, ShedCoverage.MAX_SHED_NUMBER),
                                Toast.LENGTH_SHORT).show();
                    }
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

    // 行数据改动的统一出口：编辑途中只隐藏显示，不动数据（等失焦结算），重算并整表重绘
    private void applyRowChange(int row) {
        FeedData data = getData(row);
        if (data == null) {
            return;
        }
        Log.d(TAG, "重算 第" + (row + 1) + "行 棚号=[" + data.getShedNumber() + "] 棚数="
                + data.getShedCount() + " 平均=" + data.getAverage());

        // 先按打点覆盖算出权威棚数（棚数真的变了才按旧平均重算料量），再刷新派生值：
        // 顺序反了会拿“本行棚号自己数的个数”当权威值，和别的行重叠时
        // 每敲一个键都会把料量按错误棚数改一遍（手动填料被改写）
        recomputeFirstRowRemainder();
        data.updateAllCalculations(globalWaterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
        if (table != null) {
            table.notifyAllChanged();
        }
        updateFooterRow();
        saveAllData();
    }

    // 失焦结算：有效棚数为 0 的行整行回收——数据清空、从列表移除、下面的行序号自动上移，
    // 底部补一行新空行保持总行数不变。只在结算时刻调用（换格收起、切批次、退出、载入），
    // 编辑途中绝不动行（显示层早就把 0 棚行的料列置灰了，数据等结算再清）。
    private void settleZeroRows() {
        settleZeroRows(null);
    }

    /**
     * @param emptiedByUser 本次被用户删空的行的下标（删棚号删到一点不剩）：
     *                      这些行虽然已经变成纯空行，也要回收，否则表中间会留洞。
     */
    private void settleZeroRows(Set<Integer> emptiedByUser) {
        if (dataList == null) {
            return;
        }
        // 先回收再兜底清：回收的判断要看“有没有填过数据”，先清了会把只剩数据的脏行漏成中间空行
        List<Integer> removed = RowSettlement.recycleDeadRows(dataList, emptiedByUser);
        boolean changed = !removed.isEmpty();
        if (RowSettlement.settleZeroRows(dataList)) {
            changed = true;
        }
        if (!changed) {
            return;
        }
        if (!removed.isEmpty()) {
            Log.d(TAG, "行回收 删除" + removed.size() + "行 下标=" + removed);
            finishRowRemoval(removed);
            return;
        }
        if (table != null) {
            table.notifyAllChanged();
        }
        updateFooterRow();
        saveAllData();
    }

    // 行级销毁的统一收尾（行已从 dataList 移除）：底部补空行保持总行数、
    // 编辑浮层/「下一行」按钮的行号平移、重灌表格、重算、落盘
    private void finishRowRemoval(List<Integer> removedIndices) {
        // 底部补空行，总数回到 TOTAL_ROWS，录入容量不减
        for (int i = 0; i < removedIndices.size(); i++) {
            dataList.add(new FeedData());
        }
        // 待转池的来源行可能刚被回收，作废最稳
        clearPendingTransfer();
        // 棚号气泡还开着就关掉，别让它的回调攥着旧行号
        if (shedListPopup != null) {
            shedListPopup.dismiss();
        }
        if (table != null) {
            // 编辑浮层/「下一行」按钮的行号先平移，再重灌行数
            table.onRowsRecycled(removedIndices);
            table.setRows(dataList);
            table.notifyAllChanged();
        }
        recomputeFirstRowRemainder();
        updateFooterRow();
        saveAllData();
    }

    // 所有行一视同仁：只做打点覆盖（后行优先），没有“第 1 行 = 总棚数”这回事
    private void recomputeFirstRowRemainder() {
        applyShedCoverage();
    }

    /**
     * 全表棚号文本快照（进入棚号编辑时拍一张）。返回 null 表示当前没有可用的基线。
     */
    private List<String> snapshotShedTexts() {
        if (dataList == null) {
            return null;
        }
        List<String> texts = new ArrayList<>();
        for (FeedData d : dataList) {
            texts.add(d == null ? "" : d.getShedNumber());
        }
        return texts;
    }

    // 打点覆盖：按行序铺开棚号，后行优先；棚数变化的那几行顺带重算派生值
    private void applyShedCoverage() {
        if (dataList == null || dataList.size() < 1) {
            return;
        }
        List<String> texts = new ArrayList<>();
        for (FeedData d : dataList) {
            texts.add(d == null ? "" : d.getShedNumber());
        }

        // 打字途中：以“进入编辑前的快照”为基线，只把正在编辑的这一行换成当前输入，
        // 再整体推导一遍。这样每一个按键都是全量重算（别人的文本、棚数、料量都跟着动），
        // 而半成品 "16-4" 造成的临时归属只存在于这一帧——下一帧又从干净基线重来。
        boolean liveTyping = editingShedNumber
                && shedEditSnapshot != null
                && shedEditSnapshot.size() == texts.size()
                && shedEditRow >= 0
                && shedEditRow < texts.size();

        int[] counts;
        List<String> canonical;
        if (liveTyping) {
            ShedDraft.Result draft = ShedDraft.recompute(
                    shedEditSnapshot, shedEditRow, texts.get(shedEditRow));
            counts = draft.getCounts();
            canonical = draft.getTexts();
        } else {
            // 棚号文本按“实际归属”校正：号码被后面的行占走的行，文本重写成它真正拥有的号码，
            // 这样 1-40 被下面一行写入 1-15 之后会显示成 16-40，看到的号码 = 参与算料的号码。
            canonical = ShedCoverage.renormalize(texts);
            List<List<Integer>> sets = ShedCoverage.ownedSets(texts);
            counts = new int[sets.size()];
            for (int i = 0; i < sets.size(); i++) {
                counts[i] = sets.get(i).size();
            }
        }

        for (int i = 0; i < dataList.size(); i++) {
            FeedData d = dataList.get(i);
            if (d == null) {
                continue;
            }
            int oldCount = d.getShedCount();
            // 只刷新“显示文本”，绝不回写声明原文：1-40 被下面一行占走 1-15 时显示 16-40，
            // 但申明里仍然留着 1-40——把下面那行改小/清空，15 个号会自己退回来。
            d.setShedDisplay(canonical.get(i));
            int count = counts[i];
            // 比较用的是改写文本之前的棚数：setShedNumber 会按新文本重算棚数，
            // 改写后再比就永远相等，料量/水就不会跟着新棚数重算了
            if (oldCount != count) {
                Log.d(TAG, "覆盖 第" + (i + 1) + "行 棚数 " + oldCount + "→" + count
                        + " 平均=" + d.getAverage());
                // 棚数变了：平均不动，料量按“平均 × 新棚数”重算。
                // 用存储的平均值，不要用 getAverage(...) 现算——它会被本行
                // 棚号自己数的个数影响，重叠时算出的平均是错的。
                double oldAverage = d.getAverage();
                d.setShedCountDirectly(count, false);
                if (oldAverage > 0) {
                    d.setAverageManually(oldAverage, fermentedIncludedInAverage);
                }
                d.recalculateDerivedOnly(globalWaterPercentage,
                        fermentedIncludedInWater, fermentedIncludedInAverage);
            }
        }
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
                // 跨行前先过一遍顺序规则：下一行是空行且前面还有空行 → 拦住、停在原格
                if (isOrderBlocked(nextRow)) {
                    showOrderHintDialog();
                    return;
                }
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

    // 「下一行 ▾」按钮：同列向下移一行，同样守顺序规则；到底或该列不可编辑就停在原处
    private void moveFocusToNextRow(int row, String columnId) {
        try {
            if (table == null || dataList == null) {
                return;
            }
            int nextRow = row + 1;
            if (nextRow >= dataList.size()) {
                return;
            }
            if (isOrderBlocked(nextRow)) {
                showOrderHintDialog();
                return;
            }
            FeedData data = getData(nextRow);
            if (data == null || !isCellEnabled(columnId, data, nextRow)) {
                return;
            }
            table.beginEdit(nextRow, columnId);
        } catch (Exception e) {
            Log.e(TAG, "下一行异常: " + e.getMessage());
        }
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onPause() {
        super.onPause();
        // 退到后台：待转池只在内存，直接作废
        clearPendingTransfer();
        // 离开页面：棚数仍为 0 的行彻底清空，再落盘
        settleZeroRows();
        saveAllData();
        saveGlobalWaterPercentage();
        saveFermentedIncludedInWater();
        saveFermentedIncludedInAverage();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onWindowMetricsChanged();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode,
                                         android.content.res.Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        onWindowMetricsChanged();
    }

    // 窗口尺寸/形态变了：重算平板判定与列宽，旧菜单直接关掉，下次打开按新窗口判断。
    private void onWindowMetricsChanged() {
        // 转屏 / 折叠 / 分屏都不是"点开另一行气泡"，待转池作废
        clearPendingTransfer();
        initTabletAdaptiveSettings();
        dismissOpenPopups();
        rebuildCompleteTable();
    }

    private void dismissOpenPopups() {
        if (mainMenuPopup != null) {
            mainMenuPopup.dismiss();
        }
        if (columnSettingsPopup != null) {
            columnSettingsPopup.dismiss();
        }
        if (shedListPopup != null) {
            shedListPopup.dismiss();
        }
    }

    // 棚数按钮 → 本行棚号气泡：上边贴格子下沿，左边缘对齐棚数格。
    // 点号码进入选中（绿 → 蓝），⋮ 菜单做 全选/反选/删除/转出，另一行还多一个【转入】。
    private void showShedListPopup(final int row) {
        try {
            FeedData data = getData(row);
            if (data == null) {
                return;
            }
            List<Integer> numbers = shedNumbersOf(row);
            // 打开条件：本行棚号有内容（哪怕棚数被后行盖成 0），或者有待转池要往这行转入。
            // 两者都没有就没什么可点的，跟以前一样不弹。
            boolean hasShedText = data.getShedDisplay() != null
                    && !data.getShedDisplay().trim().isEmpty();
            boolean canTransferIn = hasPendingTransfer() && row != pendingTransferSourceRow;
            if (numbers.isEmpty() && !hasShedText && !canTransferIn) {
                return;
            }
            if (shedListPopup != null) {
                shedListPopup.dismiss();
            }
            // 每次打开都是全新的选择态；待转池不受影响（它只在"关掉气泡却没转入"时作废）
            final Set<Integer> selected = new LinkedHashSet<>();
            final Map<Integer, TextView> chips = new LinkedHashMap<>();

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenH = dm.heightPixels;
            // 字号上限：系统字体放大最多按 1.0 倍算，免得弹窗字被系统放大得过大
            float cappedScale = Math.min(dm.scaledDensity, dm.density);

            // 标题 / 数量先建出来，算好“至少要放多宽”，免得标题折行
            TextView title = new TextView(this);
            title.setText(getString(R.string.mix_shed_list_title, row + 1));
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16f * cappedScale);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Color.BLACK);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);

            TextView count = new TextView(this);
            count.setText(getString(R.string.mix_shed_list_count, numbers.size()));
            count.setTextSize(TypedValue.COMPLEX_UNIT_PX, 11f * cappedScale);
            count.setTextColor(0xFF666666);
            count.setSingleLine(true);

            // ⋮ 菜单入口：全选 / 反选 / 删除 / 转出 / 转入
            // 点击区域放宽：左右各留一段 padding + 最小 48dp 宽、占满表头 40dp 高
            TextView more = new TextView(this);
            more.setText("⋮");
            more.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f * cappedScale);
            more.setTypeface(Typeface.DEFAULT_BOLD);
            more.setTextColor(0xFF333333);
            more.setGravity(Gravity.CENTER);
            more.setIncludeFontPadding(false);
            more.setPadding(dpToPx(16), 0, dpToPx(12), 0);
            more.setMinWidth(dpToPx(48));
            more.setMinHeight(dpToPx(40));
            more.setClickable(true);
            more.setContentDescription(getString(R.string.mix_shed_menu_more));

            int headerNeedW = (int) (title.getPaint().measureText(title.getText().toString())
                    + count.getPaint().measureText(count.getText().toString()))
                    + dpToPx(30) + dpToPx(58);

            // 量出号码块的真实尺寸：最小 28dp 圆点，数字变宽自动撑成胶囊
            Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chipPaint.setTextSize(12f * cappedScale);
            chipPaint.setTypeface(Typeface.DEFAULT_BOLD);
            float maxTextW = 0;
            for (int n : numbers) {
                maxTextW = Math.max(maxTextW, chipPaint.measureText(String.valueOf(n)));
            }
            int chipW = (int) Math.max(dpToPx(28), maxTextW + dpToPx(12));
            int pitch = chipW + dpToPx(2);
            int padH = dpToPx(6) * 2;

            // 锚点先算：左边缘贴住棚数格左边缘，右边留 4dp
            int windowW = getScreenWidth();
            int[] loc = new int[2];
            table.getLocationOnScreen(loc);
            Rect cellRect = table.getCellRect(row, "shed_count");
            int x = PopupGeometry.leftAlignedX(loc[0] + cellRect.left, windowW);
            int y = loc[1] + cellRect.bottom + dpToPx(2);

            // 宽度 = max(标题所需, 能排下的整块号码宽度)，上限 80% 窗口宽，
            // 还要让到不越过窗口右边缘：宁可窄一点让号码多换几行，也不把弹窗往左挪，
            // 一挪左边缘就对不上棚数格了（标题是单行 + 省略号，窄了也不会折行）
            int maxAllowed = Math.min((int) (windowW * 0.8), windowW - dpToPx(8));
            int spaceRight = windowW - dpToPx(4) - x;
            if (spaceRight >= dpToPx(120)) {
                maxAllowed = PopupGeometry.leftAlignedWidth(maxAllowed, x, windowW, dpToPx(4));
            } else {
                // 棚数格被横向滚到最右、右边实在没地方：只能往左挪，别把弹窗挤成一条缝
                x = Math.max(dpToPx(6), windowW - maxAllowed - dpToPx(4));
            }
            int perRow = Math.max(1, (maxAllowed - padH) / pitch);
            int w = Math.max(headerNeedW, padH + perRow * pitch);
            if (w > maxAllowed) {
                w = maxAllowed;
            }
            perRow = Math.max(1, (w - padH) / pitch);
            final int popupWidthPx = w;

            int rows = (numbers.size() + perRow - 1) / perRow;
            // 没有棚号（被后行盖光 / 只等转入）时留一块空白位置放提示文字
            int scrollH = numbers.isEmpty()
                    ? dpToPx(46)
                    : rows * (dpToPx(28) + dpToPx(2)) + dpToPx(18);
            int headH = dpToPx(41);
            // 弹窗最高只长到表尾行上沿：超过就内部滚动，不再盖住表尾
            int footerTopOnScreen = loc[1] + table.getHeight() - table.getFooterHeightPx();
            int avail = footerTopOnScreen - dpToPx(4) - y;
            if (avail < headH + scrollH) {
                if (avail >= headH + dpToPx(120)) {
                    // 下面地方不够就缩短气泡、内部滚动
                    scrollH = avail - headH;
                } else {
                    // 实在放不下：翻到格子上方
                    y = loc[1] + cellRect.top - headH - scrollH - dpToPx(2);
                    if (y < dpToPx(8)) {
                        y = dpToPx(8);
                    }
                }
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dpToPx(12));
            card.setBackground(cardBg);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dpToPx(10), 0, dpToPx(4), 0);

            header.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(count);
            // 和前面的“共 N 个”之间留出 10dp，别贴在一起
            LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            moreLp.setMargins(dpToPx(10), 0, 0, 0);
            header.addView(more, moreLp);

            card.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(40)));

            View divider = new View(this);
            divider.setBackgroundColor(0xFFE0E0E0);
            card.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));

            final ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(true);
            if (numbers.isEmpty()) {
                // 行里已经没有自己的棚号了（被后行盖光，或纯粹是转入目标）
                TextView empty = new TextView(this);
                empty.setText(getString(R.string.mix_shed_list_empty));
                empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, 12f * cappedScale);
                empty.setTextColor(0xFF888888);
                empty.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
                scroll.addView(empty);
            } else {
                FlowLayout flow = new FlowLayout(this);
                flow.setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(8));
                for (final int n : numbers) {
                    final TextView chip = buildShedChip(n);
                    chips.put(n, chip);
                    chip.setOnClickListener(v -> {
                        if (!selected.remove(n)) {
                            selected.add(n);
                        }
                        applyShedChipStyle(chip, selected.contains(n));
                    });
                    flow.addView(chip);
                }
                scroll.addView(flow);
            }
            card.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, scrollH));

            PopupWindow popup = new PopupWindow(card, w, ViewGroup.LayoutParams.WRAP_CONTENT);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);
            popup.setElevation(dpToPx(8));
            popup.setOnDismissListener(() -> {
                shedListPopup = null;
                if (keepPendingOnPopupDismiss) {
                    // 转出后自己关的窗：待转池还要留着等转入
                    keepPendingOnPopupDismiss = false;
                } else {
                    // 气泡关掉了却没走来转入这条路，这次转出作废
                    clearPendingTransfer();
                }
            });
            shedListPopup = popup;

            more.setOnClickListener(v -> showShedMenu(row, numbers, selected, chips,
                    header, popupWidthPx, popup));
            popup.showAtLocation(table, Gravity.NO_GRAVITY, x, y);
        } catch (Exception e) {
            Log.e(TAG, "showShedListPopup: " + e.getMessage(), e);
        }
    }

    // 号码块选中态：绿底黑字 ↔ 蓝底白字
    private void applyShedChipStyle(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? 0xFF2F6FED : 0xFFA8DDB5);
        bg.setCornerRadius(dpToPx(14));
        chip.setBackground(bg);
        chip.setTextColor(selected ? Color.WHITE : Color.BLACK);
    }

    private TextView buildShedChip(int number) {
        TextView chip = new TextView(this);
        chip.setText(String.valueOf(number));
        // 与弹窗其它文字一致：字号上限按 1.0 倍系统字体算
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float cappedScale = Math.min(dm.scaledDensity, dm.density);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, 12f * cappedScale);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(Color.BLACK);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setMinWidth(dpToPx(28));
        chip.setPadding(dpToPx(6), 0, dpToPx(6), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFA8DDB5);
        bg.setCornerRadius(dpToPx(14));
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28));
        lp.setMargins(0, 0, dpToPx(2), dpToPx(2));
        chip.setLayoutParams(lp);
        return chip;
    }

    // ==================== 棚号气泡：⋮ 菜单 / 删除 / 转出 / 转入 ====================

    private void showShedMenu(final int row, final List<Integer> numbers,
                              final Set<Integer> selected, final Map<Integer, TextView> chips,
                              final View anchor, final int popupWidth, final PopupWindow popup) {
        final List<Integer> actions = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        actions.add(SHED_ACTION_SELECT_ALL);
        labels.add(getString(R.string.mix_shed_sel_all));
        actions.add(SHED_ACTION_SELECT_INVERT);
        labels.add(getString(R.string.mix_shed_sel_invert));
        if (!selected.isEmpty()) {
            actions.add(SHED_ACTION_DELETE);
            labels.add(getString(R.string.mix_shed_delete));
            actions.add(SHED_ACTION_TRANSFER_OUT);
            labels.add(getString(R.string.mix_shed_transfer_out));
        }
        // 转入：只要不是来源行自己，上下都能转——
        // 往下转靠后行优先压过来；往上转靠把来源行的单号删掉，号码自动退回给它上面的行
        if (hasPendingTransfer() && row != pendingTransferSourceRow) {
            actions.add(SHED_ACTION_TRANSFER_IN);
            labels.add(getString(R.string.mix_shed_transfer_in));
        }

        try {
            PopupWindow menu = createListPopup(getString(R.string.mix_shed_menu_title),
                    labels.toArray(new String[0]), (dialog, which) -> {
                        if (which < 0 || which >= actions.size()) {
                            return;
                        }
                        handleShedMenuAction(actions.get(which), row, numbers, selected, chips,
                                popup);
                    });
            // 菜单右边缘跟气泡右边缘对齐（header 左边缘就是气泡左边缘）
            int offsetX = Math.max(0, popupWidth - listPopupWidth() - dpToPx(10));
            menu.showAsDropDown(anchor, offsetX, dpToPx(2));
        } catch (Exception e) {
            Log.e(TAG, "showShedMenu: " + e.getMessage(), e);
        }
    }

    private void handleShedMenuAction(int action, int row, List<Integer> numbers,
                                      Set<Integer> selected, Map<Integer, TextView> chips,
                                      PopupWindow popup) {
        switch (action) {
            case SHED_ACTION_SELECT_ALL:
            case SHED_ACTION_SELECT_INVERT: {
                boolean all = action == SHED_ACTION_SELECT_ALL;
                for (int n : numbers) {
                    boolean on = all || !selected.contains(n);
                    if (on) {
                        selected.add(n);
                    } else {
                        selected.remove(n);
                    }
                    TextView chip = chips.get(n);
                    if (chip != null) {
                        applyShedChipStyle(chip, on);
                    }
                }
                break;
            }
            case SHED_ACTION_DELETE: {
                confirmDeleteShedNumbers(row, selected, popup);
                break;
            }
            case SHED_ACTION_TRANSFER_OUT: {
                // 只把号码记进待转池，来源行的数据一个字都不改，气泡直接关掉
                startPendingTransfer(row, selected);
                if (popup != null && popup.isShowing()) {
                    // 这次关窗是转出自己关的，别把刚记下的待转池清掉
                    keepPendingOnPopupDismiss = true;
                    popup.dismiss();
                }
                break;
            }
            case SHED_ACTION_TRANSFER_IN: {
                applyTransferIn(row);
                if (popup != null && popup.isShowing()) {
                    popup.dismiss();
                }
                break;
            }
            default:
                break;
        }
    }

    // 删除 = 这些棚卖了：从本行的棚号里剔掉（区间不动），然后整表重算。
    //
    // 「有地方退就退」：本行拥有的号如果上面某行还申明着（原本是从那儿退下来的），
    // 这里只把它从本行申明里拿掉，号会立刻退回上面那行继续算料；
    // 「没地方退就等于删除」：别的行都没有它，本行剔掉就是全表消失。
    // 两种情况动作相同——只动本行申明。所以绝不能像 0.3.14 那样把号从全表每行一起删，
    // 那会把本该退回来源行的号一块儿埋掉。
    private void confirmDeleteShedNumbers(final int row, Set<Integer> selected,
                                          final PopupWindow popup) {
        final List<Integer> targets = new ArrayList<>(selected);
        if (targets.isEmpty()) {
            return;
        }
        // 点了删除就不是转入这条路，待转池作废
        clearPendingTransfer();
        // 先算清楚这批号的去向：上面某行还申明着的会退回（不是真删），
        // 上面没有任何来路的才是彻底消失——确认框里必须把它们点名，别让用户以为都只是"挪个位置"
        List<String> declared = new ArrayList<>();
        if (dataList != null) {
            for (FeedData d : dataList) {
                declared.add(d == null ? "" : d.getShedNumber());
            }
        }
        ShedDeletePlan plan = ShedDeletePlan.of(declared, row, targets);
        showStyledConfirmDialog70(
                getString(R.string.mix_shed_delete_title),
                buildShedDeleteMessage(plan),
                new String[]{getString(R.string.btn_cancel), getString(R.string.mix_shed_delete)},
                new int[]{0xFF333333, 0xFFD32F2F},
                new DialogInterface.OnClickListener[]{null, (dialog, which) -> {
                    FeedData data = getData(row);
                    if (data == null) {
                        return;
                    }
                    String oldText = data.getShedNumber();
                    String newText = ShedText.remove(oldText, targets);
                    if (!newText.equals(oldText)) {
                        Log.d(TAG, "删除 第" + (row + 1) + "行 号码=" + targets
                                + " 棚号 [" + oldText + "] → [" + newText + "]");
                        data.setShedNumber(newText);
                        applyRowChange(row);
                    }
                    // 号删光了：这行既没棚号也没棚数，它就是一张废纸，整行回收，
                    // 下面的行往上顶，底部补空行（见 settleZeroRows 的 emptiedByUser）
                    final Set<Integer> emptied = new LinkedHashSet<>();
                    if (data.getShedNumber().trim().isEmpty() && data.getShedCount() <= 0) {
                        emptied.add(row);
                    }
                    if (table != null) {
                        // 推迟到弹窗关完再删行，否则回调里攥着的行号会立刻错位
                        table.post(() -> settleZeroRows(emptied));
                    }
                    if (popup != null) {
                        popup.dismiss();
                    }
                }});
    }

    /**
     * 删除确认框的正文：说清楚"会退回上面"和"真被删掉"各有多少个，
     * 后半段把要真删的号码点名（升序、连号收区间），用户才知道自己删掉的是什么。
     */
    private String buildShedDeleteMessage(ShedDeletePlan plan) {
        int fallback = plan.getFallback().size();
        int permanent = plan.getPermanent().size();
        if (permanent > 0 && fallback > 0) {
            return getString(R.string.mix_shed_delete_msg_mixed,
                    fallback + permanent, fallback, permanent,
                    compactNumberList(plan.getPermanent()));
        }
        if (permanent > 0) {
            return getString(R.string.mix_shed_delete_msg_permanent,
                    permanent, compactNumberList(plan.getPermanent()));
        }
        return getString(R.string.mix_shed_delete_msg_fallback, fallback);
    }

    /** 号码列表压成一行；太长就只列前 12 个，别把确认框撑出屏幕。 */
    private String compactNumberList(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return "";
        }
        String full = ShedText.normalize(numbers);
        if (full.length() <= 80) {
            return full;
        }
        List<Integer> head = new ArrayList<>(numbers.subList(0, Math.min(12, numbers.size())));
        return ShedText.normalize(head) + " …（共 " + numbers.size() + " 个）";
    }

    private boolean hasPendingTransfer() {
        return pendingTransferSourceRow >= 0
                && pendingTransferNumbers != null
                && !pendingTransferNumbers.isEmpty();
    }

    private void startPendingTransfer(int row, Set<Integer> selected) {
        if (selected == null || selected.isEmpty()) {
            return;
        }
        pendingTransferNumbers = new ArrayList<>(selected);
        pendingTransferSourceRow = row;
        Log.d(TAG, "转出 记录待转池 来源第" + (row + 1) + "行 号码=" + pendingTransferNumbers
                + "（来源行数据不动，等另一行点【转入】才真正搬走）");
    }

    private void clearPendingTransfer() {
        pendingTransferNumbers = null;
        pendingTransferSourceRow = -1;
        keepPendingOnPopupDismiss = false;
    }

    // 转入：号码池的作用就是任意行之间随便转，所以上下都要能走通。
    //   往下转：来源行一个字都不用动——目标行在下面，后行优先自然把这批号压过去，
    //          来源行的 20-30 这种连续段也原样留着（不白拆）。
    //   往上转：来源行必须真的松手，否则它还在下面压着。单号直接删；号在连续段里
    //          就只能把这段拆开（20-30 去 25 → 20-24 26-30），拆完自动退回给
    //          包着它的上一行（父辈/爷辈）。
    // 目标行补写规则：落在它最小号~最大号之间的不写，范围外的按空格接到末尾。
    private void applyTransferIn(int targetRow) {
        if (!hasPendingTransfer() || targetRow == pendingTransferSourceRow) {
            Log.d(TAG, "转入放弃：池子空或目标行就是来源行 target=" + targetRow);
            clearPendingTransfer();
            return;
        }
        final List<Integer> numbers = new ArrayList<>(pendingTransferNumbers);
        final int sourceRow = pendingTransferSourceRow;
        FeedData target = getData(targetRow);
        FeedData source = getData(sourceRow);
        if (target == null) {
            Log.d(TAG, "转入放弃：目标行没数据 target=" + targetRow);
            clearPendingTransfer();
            return;
        }
        String oldTarget = target.getShedNumber();
        String oldSource = source == null ? "" : source.getShedNumber();
        boolean sourceHadContent = oldSource != null && !oldSource.trim().isEmpty();
        boolean targetHadContent = oldTarget != null && !oldTarget.trim().isEmpty();

        // 防呆：整行的号全转给一个空行，等于只是把整行往下挪一格（来源行被回收、下面整体
        // 上移），屏幕闪一下却什么都没变——直接不让转；目标行不是空的（真合并）才照常转。
        if (targetRow != sourceRow
                && ShedText.pointlessWholeRowMove(oldTarget, shedNumbersOf(sourceRow), numbers)) {
            Log.d(TAG, "转入放弃：整行转到空行（只会闪一下） 来源第" + (sourceRow + 1)
                    + "行→目标第" + (targetRow + 1) + "行");
            Toast.makeText(this, getString(R.string.mix_shed_transfer_whole_to_blank),
                    Toast.LENGTH_SHORT).show();
            clearPendingTransfer();
            return;
        }

        // 两边文本都按"转移后的实际号码"重排重写（升序、连号合成区间）。
        // 没有任何"落在目标范围内就跳过"的暗规则——文本里的号就是这行认领的号，
        // 棚数归谁由打点覆盖（后行优先）说了算。
        String[] moved = ShedText.transfer(oldSource, oldTarget, numbers);

        Log.d(TAG, "转入 开始 第" + (sourceRow + 1) + "行→第" + (targetRow + 1) + "行 号码=" + numbers
                + " | 来源棚号 [" + oldSource + "] → [" + moved[0] + "]"
                + " | 目标棚号 [" + oldTarget + "] → [" + moved[1] + "]");

        // 先把池子清掉：后面 commitCell 可能弹提示，别让池子停在半路
        clearPendingTransfer();

        if (source != null) {
            commitCell(sourceRow, "shed_number", moved[0]);
        }
        commitCell(targetRow, "shed_number", moved[1]);

        // 重排：把两行文本重写成各自"实际拥有"的号码（覆盖之后的真相），
        // 别的行后来居上抢走的号不再挂在文本里，后续转移不再被幽灵号干扰
        rewriteToOwned(sourceRow);
        rewriteToOwned(targetRow);
        forceRecomputeRows(sourceRow, targetRow);

        FeedData t = getData(targetRow);
        FeedData s = getData(sourceRow);
        Log.d(TAG, "转入 完成 目标第" + (targetRow + 1) + "行 棚数="
                + (t == null ? -1 : t.getShedCount()) + " 棚号=[" + (t == null ? "" : t.getShedNumber()) + "]"
                + " 平均=" + (t == null ? 0 : t.getAverage())
                + " | 来源第" + (sourceRow + 1) + "行 棚数="
                + (s == null ? -1 : s.getShedCount()) + " 棚号=[" + (s == null ? "" : s.getShedNumber()) + "]");

        // 一次算齐所有该回收的行：被转空的两行 + 别的死行（幽灵号清掉后露出来的），
        // 一起删、一次上顶。分两次删会让用户看到"先进一行、再进一行"。
        Set<Integer> emptied = new LinkedHashSet<>();
        if (s != null && s.getShedCount() <= 0 && sourceHadContent) {
            emptied.add(sourceRow);
        }
        if (t != null && t.getShedCount() <= 0 && targetHadContent) {
            emptied.add(targetRow);
        }
        List<Integer> toRecycle = RowSettlement.recycleDeadRows(dataList, emptied);
        if (!toRecycle.isEmpty()) {
            Log.d(TAG, "行回收（转空） 删除" + toRecycle.size() + "行 下标=" + toRecycle);
            finishRowRemoval(toRecycle);
        }
    }

    // 重排：把一行的棚号文本重写成它"实际拥有"的号码（打点覆盖、后行优先之后的真相），
    // 升序、连号合成区间。转移 / 删除之后调用，保证文本永远和棚数对得上。
    private void rewriteToOwned(int row) {
        FeedData data = getData(row);
        if (data == null || dataList == null) {
            return;
        }
        List<String> texts = new ArrayList<>();
        for (FeedData d : dataList) {
            texts.add(d == null ? "" : d.getShedNumber());
        }
        List<List<Integer>> sets = ShedCoverage.ownedSets(texts);
        if (row < 0 || row >= sets.size()) {
            return;
        }
        String owned = ShedText.normalize(sets.get(row));
        String current = data.getShedNumber() == null ? "" : data.getShedNumber().trim();
        if (!owned.equals(current)) {
            Log.d(TAG, "重排 第" + (row + 1) + "行 棚号 [" + current + "] → [" + owned + "]");
            commitCell(row, "shed_number", owned);
        }
    }

    // 一次动好几行的操作（转入）收尾用：先跑打点覆盖，再把参与的行强制重算一遍派生值
    private void forceRecomputeRows(int... rows) {
        if (dataList == null) {
            return;
        }
        applyShedCoverage();
        for (int r : rows) {
            FeedData d = getData(r);
            if (d != null) {
                d.updateAllCalculations(globalWaterPercentage,
                        fermentedIncludedInWater, fermentedIncludedInAverage);
            }
        }
        if (table != null) {
            table.notifyAllChanged();
        }
        updateFooterRow();
        saveAllData();
    }

    // 防呆：待转池有号码时，除了"点另一行的棚数格"，任何一次按下都把它作废
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && hasPendingTransfer()
                && !isTapOnOtherRowShedCount(ev)) {
            clearPendingTransfer();
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isTapOnOtherRowShedCount(MotionEvent ev) {
        if (table == null || table.getWidth() <= 0) {
            return false;
        }
        int[] loc = new int[2];
        table.getLocationInWindow(loc);
        float lx = ev.getX() - loc[0];
        float ly = ev.getY() - loc[1];
        if (lx < 0 || ly < 0 || lx >= table.getWidth() || ly >= table.getHeight()) {
            return false;
        }
        if (!"shed_count".equals(table.columnIdAtLocal(lx, ly))) {
            return false;
        }
        int row = table.rowAtLocal(lx, ly);
        return row >= 0 && row != pendingTransferSourceRow;
    }

    private List<Integer> shedNumbersOf(int row) {
        List<String> texts = new ArrayList<>();
        for (FeedData d : dataList) {
            texts.add(d == null ? "" : d.getShedNumber());
        }
        List<List<Integer>> sets = ShedCoverage.ownedSets(texts);
        if (row < 0 || row >= sets.size()) {
            return new ArrayList<>();
        }
        return sets.get(row);
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
                // 棚数被后面的行覆盖成 0 的行不参与料/水合计，避免重叠棚的料算两遍
                if (data.getShedCount() > 0) {
                    totalFermented += data.getFermentedFeed();
                    totalPowder += data.getPowderFeed();
                    totalFeed03 += data.getFeed03();
                    totalFeed05 += data.getFeed05();
                    totalFeed10 += data.getFeed10();
                    totalWater += data.getWater();
                }

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
                // 棚号为 0 的行（棚号被后面的行全部覆盖）不参与误差合计
                if (data.getShedCount() <= 0) {
                    continue;
                }
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
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.round(getScreenWidth() / density);
        isTabletMode = TabletLayout.isTabletWidth(widthDp);
        Log.i(TAG, "布局模式: " + (isTabletMode ? "平板" : "手机")
                + ", 窗口宽 " + widthDp + "dp (阈值 " + TabletLayout.TABLET_MIN_WIDTH_DP + "dp)");
        if (isTabletMode) {
            adaptiveColumnWidths = TabletLayout.computeWidths(
                    columnWidths, columnIds, visibleColumns(), getScreenWidth(), density);
        } else {
            adaptiveColumnWidths = TabletLayout.fixedWidths(columnWidths, density);
        }
        // 隐藏列太多时把可见列铺满屏宽（否则右侧留白，像少了一列）
        TabletLayout.fillToWidth(adaptiveColumnWidths, visibleColumns(), getScreenWidth());
    }

    private void recalcAdaptiveWidths() {
        float density = getResources().getDisplayMetrics().density;
        adaptiveColumnWidths = TabletLayout.computeWidths(
                columnWidths, columnIds, visibleColumns(), getScreenWidth(), density);
        TabletLayout.fillToWidth(adaptiveColumnWidths, visibleColumns(), getScreenWidth());
    }

    /**
     * 表格是否已经铺满全部列：全部可见 + 自然宽度放得下。
     * 满足时菜单不再出现「快捷设置」；折叠/展开、分屏后按实时窗口重新判断。
     */
    private boolean isElevenColumnsFilled() {
        return TabletLayout.fillsAllColumns(columnWidths, columnIds, visibleColumns(),
                getScreenWidth(), getResources().getDisplayMetrics().density, isTabletMode);
    }

    // 折叠/分屏/窗口拖拽时不一定触发 onConfigurationChanged，这里按主容器宽度兜底重算。
    private void installWindowSizeWatcher() {
        View main = findViewById(R.id.main_layout);
        if (main == null) {
            return;
        }
        main.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width <= 0) {
                return;
            }
            if (lastWindowWidth == 0) {
                lastWindowWidth = width; // 首次布局只记录，不重复重建
                return;
            }
            if (width != lastWindowWidth) {
                lastWindowWidth = width;
                onWindowMetricsChanged();
            }
        });
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
            installWindowSizeWatcher();
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
        } else if ("shed_count".equals(id)) {
            // 棚数是可点的按钮，用深绿提示可点
            color = 0xFF1F6B30;
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
            } else {
                // 手机模式也要每次重算：隐藏列后要把可见列铺满屏宽
                initTabletAdaptiveSettings();
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
        for (int i = 0; i < columnIds.length; i++) {
            String id = columnIds[i];
            Boolean stored = columnVisibility.get(id);
            columnVisibility.put(id, stored == null || stored);
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

    /** 按批次自己的设置重算所有行（所有行平等，没有首行特例）。 */
    private void recalcBatch(Batch batch) {
        for (int i = 0; i < batch.getRows().size(); i++) {
            batch.getRows().get(i).updateAllCalculations(
                    batch.getWaterPercentage(),
                    batch.isFermentedInWater(),
                    batch.isFermentedInAverage());
        }
    }

    /** 切换批次：先提交编辑并保存，再换数据源重建。 */
    private void switchBatch(int index) {
        if (index == currentBatchIndex || index < 0 || index >= batches.size()) {
            return;
        }
        closeEditorAndIme();
        // 切批次：待转池作废（号码只在本批次内有意义）
        clearPendingTransfer();
        // 切批次前先把当前批次里棚数为 0 的行结算掉
        settleZeroRows();
        pushSettingsToBatch();
        saveAllData();

        currentBatchIndex = index;
        bindCurrentBatch();
        recalcBatch(batches.get(currentBatchIndex));
        recomputeFirstRowRemainder();
        updateFooterRow();
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
    /** 构建统一列表弹窗（圆角 + 分隔线 + 40dp 行），返回未显示的实例。 */
    private PopupWindow createListPopup(String title, String[] items,
                                        final DialogInterface.OnClickListener listener) {
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
                    listener.onClick(null, index);
                }
            });
            container.addView(itemTv);
            if (i < items.length - 1) {
                container.addView(createDivider());
            }
        }

        popup.setWidth(listPopupWidth());
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        return popup;
    }

    // 列表弹窗宽度：水比 / 发酵料 / 列操作 / 批次子菜单共用；定位时用同一个宽度算右边缘
    private int listPopupWidth() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.min(dm.widthPixels, dpToPx(180));
    }

    /** 锚定在指定格子下方显示（水比 / 发酵料 / 列操作），右边缘贴住单元格右边缘。 */
    private void showAnchoredListDialog(String title, String[] items, Rect cellRect,
                                        final DialogInterface.OnClickListener listener) {
        try {
            PopupWindow popup = createListPopup(title, items, listener);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int popupWidth = listPopupWidth();
            int[] loc = new int[2];
            if (table != null) {
                table.getLocationOnScreen(loc);
            }
            int x;
            if (cellRect == null) {
                x = loc[0];
            } else {
                x = PopupGeometry.rightAlignedX(loc[0] + cellRect.right, popupWidth, dm.widthPixels);
            }
            int y = loc[1] + (cellRect != null ? cellRect.bottom : 0);
            popup.showAtLocation(table, Gravity.NO_GRAVITY, x, y);
        } catch (Exception e) {
            Log.e(TAG, "showAnchoredListDialog: " + e.getMessage(), e);
        }
    }

    /** 屏幕居中显示（批次选择等不依附格子的场景）。 */
    private void showCenteredListDialog(String title, String[] items,
                                        final DialogInterface.OnClickListener listener) {
        try {
            PopupWindow popup = createListPopup(title, items, listener);
            View anchor = findViewById(R.id.main_layout);
            popup.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        } catch (Exception e) {
            Log.e(TAG, "showCenteredListDialog: " + e.getMessage(), e);
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

    // ==================== 顺序录入（与鸿蒙版一致） ====================

    // 这一行是否已经有输入：棚号非空 或 棚数 > 0
    private boolean rowHasInput(int row) {
        FeedData data = getData(row);
        if (data == null) {
            return false;
        }
        String sn = data.getShedNumber();
        return (sn != null && !sn.trim().isEmpty()) || data.getShedCount() > 0;
    }

    // 从第一行往下找，第一个还没输入的行（都填了返回 -1）
    private int firstEmptyRow() {
        if (dataList == null) {
            return -1;
        }
        for (int i = 0; i < dataList.size(); i++) {
            if (!rowHasInput(i)) {
                return i;
            }
        }
        return -1;
    }

    // 是否被顺序规则拦住：前面还有空行，而这一行本身也是空的
    // 注意：已经有数据的行永远放行，免得中间清空一行就把后面全挡死
    private boolean isOrderBlocked(int row) {
        int firstEmpty = firstEmptyRow();
        return firstEmpty >= 0 && row > firstEmpty && !rowHasInput(row);
    }

    // 特例：只有**第一行**的棚数要求先填棚号（棚号空、棚数也空着时拦截）。
    // 其余行仍走原来的顺序规则（isOrderBlocked），不要在这里扩大范围。
    private boolean needsShedNumberFirst(int row, String columnId) {
        if (row != 0 || !"shed_count".equals(columnId)) {
            return false;
        }
        FeedData data = getData(row);
        if (data == null) {
            return false;
        }
        String shedNumber = data.getShedNumber();
        boolean noShedNumber = shedNumber == null || shedNumber.trim().isEmpty();
        return noShedNumber && data.getShedCount() <= 0;
    }

    // 提示：请先填写第 N 行的「棚号」。样式与鸿蒙一致（70% 宽、标题 18、正文 15、单个绿色按钮）
    // 注意：N 永远是"最靠前的空行"，不是被点的那一行。
    // 例：第 1 行空着，点第 5 行 → 提示填写第 1 行的棚号。
    private void showOrderHintDialog() {
        int target = firstEmptyRow();
        if (target < 0) {
            return;
        }
        showStyledConfirmDialog70(
                getString(R.string.mix_order_title),
                getString(R.string.mix_order_message, target + 1),
                new String[]{getString(R.string.mix_btn_got_it)},
                new int[]{0xFF2D8C42},
                new DialogInterface.OnClickListener[]{null});
    }

    // 第一次点第一行「棚号」：提示怎么填，并可直接进帮助（只提示一次）
    // 按钮顺序按用户习惯：确认类（知道了）放右手边
    private void showShedInputHint() {
        showStyledConfirmDialog70(
                getString(R.string.mix_shed_hint_title),
                getString(R.string.mix_shed_hint_msg),
                new String[]{getString(R.string.mix_btn_view_help), getString(R.string.mix_btn_got_it)},
                new int[]{0xFF2D8C42, 0xFF2D8C42},
                new DialogInterface.OnClickListener[]{
                        (dialog, which) -> showHelpPanel(),
                        (dialog, which) -> {
                            if (table != null) {
                                table.beginEdit(0, "shed_number");
                            }
                        }
                });
    }

    private boolean isNonFermentedFeedColumn(String columnId) {
        for (String c : NF_FEED_COLUMNS) {
            if (c.equals(columnId)) {
                return true;
            }
        }
        return false;
    }

    // 5 种饲料列：发酵料 + 粉料 / 0.3 / 0.5 / 1.0
    private boolean isFeedColumn(String columnId) {
        return "fermented".equals(columnId) || isNonFermentedFeedColumn(columnId);
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
        data.recalculateDerivedOnly(globalWaterPercentage,
                fermentedIncludedInWater, fermentedIncludedInAverage);
        recomputeFirstRowRemainder();
        if (table != null) {
            table.notifyAllChanged();
        }
        updateFooterRow();
        saveAllData();
    }

    // 更新所有计算
    private void updateAllCalculations() {
        try {
            for (int i = 0; i < dataList.size(); i++) {
                FeedData data = dataList.get(i);
                // 所有行一视同仁
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
        // 棚数为 0：5 种饲料列不显示数据（格子本身还是置灰的），底层数据等失焦结算再清
        if (data.getShedCount() <= 0 && isFeedColumn(columnId)) {
            return "";
        }
        switch (columnId) {
            case "shed_number":
                // 点进去编辑时给的是屏幕上看到的文本（实际拥有的号），改完就按这个重新声明
                return data.getShedDisplay();
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
        if (data.getShedCount() <= 0) {
            // 棚数为 0：平均/水/称料纯空白（白底），不显示 0 也不显示旧数据
            if ("average_feed".equals(columnId) || "water".equals(columnId)
                    || "weighed_feed".equals(columnId)) {
                return "";
            }
        }
        switch (columnId) {
            case "seq":
                return "";
            case "shed_number":
                String shedName = data.getShedDisplay();
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

    // 批次子菜单：贴着菜单右侧展开，顶边与"批次切换"行对齐；选中后菜单一起关闭。
    private void showBatchPopup(View anchorRow, final PopupWindow menuPopup) {
        String[] labels = new String[MixCalcPrefs.BATCH_COUNT];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = i == currentBatchIndex
                    ? getString(R.string.mix_batch_selected, batchLabel(i))
                    : batchLabel(i);
        }
        PopupWindow popup = createListPopup(getString(R.string.mix_batch_dialog_title), labels,
                (dialog, which) -> {
                    menuPopup.dismiss();
                    switchBatch(which);
                });

        int[] loc = new int[2];
        anchorRow.getLocationOnScreen(loc);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = listPopupWidth();
        int x = Math.min(loc[0] + anchorRow.getWidth(), dm.widthPixels - width);
        int y = loc[1];
        View anchor = findViewById(R.id.main_layout);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
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
            mainMenuPopup = popup;
            popup.setOnDismissListener(() -> mainMenuPopup = null);

            // 快捷设置：表格已经铺满 11 列时不显示（隐藏后由批次行顶上，行高统一由 fitMenuRowHeights 处理）
            configureQuickSettingsItem(popupView, popup);

            fitMenuRowHeights(popupView);

            // 批次切换：菜单不关闭，在"批次切换"行右侧展开子菜单
            final View batchRow = popupView.findViewById(R.id.menu_item_batch);
            batchRow.setOnClickListener(v -> showBatchPopup(batchRow, popup));

            popupView.findViewById(R.id.menu_item_privacy_policy).setOnClickListener(v -> {
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

            // 宽度：正常字体下手机 1/3、平板/11 列全显 1/4；字体放大时按 fontScale 加宽（最多屏幕 60%），
            // 避免版本号在窄菜单里被挤成每行一个字。高度由 wrap_content 撑开。
            View mainLayout = findViewById(R.id.main_layout);
            int totalWidth = mainLayout != null && mainLayout.getWidth() > 0
                    ? mainLayout.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
            boolean wideMenu = isTabletMode || isElevenColumnsFilled();
            int baseWidth = wideMenu ? totalWidth / 4 : totalWidth / 3;
            int maxWidth = (int) (totalWidth * 0.6f);
            int popupWidth = Math.min(maxWidth, Math.max(baseWidth, (int) (baseWidth * fontScale)));
            popup.setWidth(popupWidth);
            popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            showPopupUnderTitleBar(popup);
        } catch (Exception e) {
            Log.e(TAG, "显示主菜单异常: " + e.getMessage());
        }
    }

    // 表格已经铺满 11 列（没有列要藏）时不显示「快捷设置」；每次打开菜单都按当前窗口重算。
    private void configureQuickSettingsItem(View popupView, final PopupWindow popup) {
        View quickItem = popupView.findViewById(R.id.menu_item_quick_settings);
        View quickDivider = popupView.findViewById(R.id.menu_divider_quick);
        if (isElevenColumnsFilled()) {
            quickItem.setVisibility(View.GONE);
            quickDivider.setVisibility(View.GONE);
            return;
        }
        quickItem.setOnClickListener(v -> {
            popup.dismiss();
            showColumnSettingsPopup();
        });
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

            // 左右铺满当前窗口：折叠/展开、分屏后跟着窗口宽度走，不再固定 360dp
            popup.setWidth(PopupGeometry.fullWidth(getScreenWidth()));
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
    /**
     * 菜单行高固定为 40dp 网格：首行 40dp，其余 39dp + 1dp 分隔线。
     * 系统字体放大时按可用高度回缩字号，避免行被撑高、分隔线与表格横线错位。
     */
    private void fitMenuRowHeights(View popupView) {
        int[] ids = {
                R.id.menu_item_quick_settings, R.id.menu_item_batch,
                R.id.menu_item_privacy_policy, R.id.menu_item_user_agreement,
                R.id.menu_item_help, R.id.menu_item_language, R.id.menu_item_version
        };
        boolean firstVisible = true;
        for (int id : ids) {
            View view = popupView.findViewById(id);
            if (!(view instanceof TextView) || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            TextView item = (TextView) view;
            int targetPx = dpToPx(firstVisible ? 40 : 39);
            firstVisible = false;

            ViewGroup.LayoutParams params = item.getLayoutParams();
            params.height = targetPx;
            item.setLayoutParams(params);
            item.setIncludeFontPadding(false);
            item.setMinHeight(0);

            int available = targetPx - item.getPaddingTop() - item.getPaddingBottom();
            Paint.FontMetrics metrics = item.getPaint().getFontMetrics();
            float textHeight = metrics.descent - metrics.ascent;
            if (available > 0 && textHeight > available) {
                item.setTextSize(TypedValue.COMPLEX_UNIT_PX, item.getTextSize() * available / textHeight);
            }
        }
    }

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
