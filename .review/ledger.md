# 问题台账（review ledger）

> 由 `review-check.ps1` 生成；规则见 `~/.agents/skills/code-standards/references/review-gate.md`。
> 只增不删；`deferred` 必须写触发条件；P0/P1 禁止延期。

| ID | 级别 | 类别 | 位置 | 问题 | 状态 | 决定/理由 | 触发条件 |
|---|---|---|---|---|---|---|---|
| R-001 | P1 | file-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:1` | 文件 1751 有效行 > 500 | deferred | 人工决定：项目将迁鸿蒙，重写时消解；Java 版仅维护 | 鸿蒙重写启动，或该文件再次大改 |
| R-002 | P2 | file-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\table\MixCalcTableView.java:1` | 文件 552 有效行 > 500 | open | | |
| R-003 | P1 | file-size | `copyright\tools\build_manual_pdf.py:1` | 文件 1027 有效行 > 500 | waived | E1 一次性排版脚本 | — |
| R-004 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:516` | 函数 69 行 > 50（private void commitCell(int row, String columnId, String tex…） | open | | |
| R-005 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:802` | 函数 95 行 > 50（private String formatDataToMessage() {） | fixed | 死代码已删除（190 行，编译通过） | — |
| R-006 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:1556` | 函数 56 行 > 50（private void showFermentedCalculationDialog(Rect cellRect) {） | open | | |
| R-007 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:1670` | 函数 58 行 > 50（private void showSwitchRatioDrawer(final FeedData data, fina…） | open | | |
| R-008 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:2209` | 函数 55 行 > 50（private void showMainMenu() {） | open | | |
| R-009 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:2298` | 函数 62 行 > 50（private void showPrivacyConsentIfNeeded() {） | open | | |
| R-010 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\MixCalcActivity.java:2430` | 函数 92 行 > 50（private void showColumnSettingsPopup() {） | open | | |
| R-011 | P2 | func-size | `app\src\main\java\com\shrimpfarm\app\mixcalc\table\MixCalcTableView.java:414` | 函数 71 行 > 50（public boolean onTouchEvent(MotionEvent ev) {） | open | | |
| R-012 | P2 | func-size | `copyright\tools\build_manual_pdf.py:231` | 函数 52 行 > 50（def data_table(...)） | waived | E1 一次性排版脚本 | — |
| R-013 | P2 | func-size | `copyright\tools\build_manual_pdf.py:294` | 函数 51 行 > 50（def cover(...)） | waived | E1 一次性排版脚本 | — |
| R-014 | P1 | func-size | `copyright\tools\build_manual_pdf.py:405` | 函数 614 行 > 50（def build_content(...)） | waived | E1 一次性排版脚本 | — |
| R-015 | P2 | func-size | `copyright\tools\build_source_pdf.py:139` | 函数 62 行 > 50（def strip_java_comments(...)） | open | | |
| R-016 | P2 | func-size | `copyright\tools\build_source_pdf.py:322` | 函数 52 行 > 50（def main(...)） | open | | |
