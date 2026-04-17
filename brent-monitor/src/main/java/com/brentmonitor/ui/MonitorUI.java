package com.brentmonitor.ui;

import com.brentmonitor.model.MonitorStats;
import com.brentmonitor.model.NewsItem;
import com.brentmonitor.scheduler.MonitorScheduler;
import com.brentmonitor.util.Logger;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * JavaFX 主界面
 *
 * 界面布局（从上到下）：
 * 1. 顶部状态栏（执行次数 / 抓取数 / 新增数 / 命中数）
 * 2. 中央表格（TableView：时间 / 来源 / 内容 / 油价）
 * 3. 底部控制栏（Start / Stop / 提示音开关）
 *
 * 线程安全说明：
 * - UI 更新必须在 JavaFX Application Thread
 * - 使用 Platform.runLater() 将后台线程的数据更新到 UI
 * - ObservableList 是线程安全的（JavaFX 内部处理同步）
 */
public class MonitorUI extends Application {

    private MonitorScheduler scheduler;

    // UI 组件
    private TableView<NewsItem> tableView;
    private ObservableList<NewsItem> newsData;

    private Label lblRunsValue, lblFetchedValue, lblNewValue, lblMatchedValue;
    private Label lblStatus;
    private Button btnStart, btnStop;
    private CheckBox cbSound;
    private ProgressIndicator progressIndicator;

    // 时间格式化
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("GMT"));

    /** 将 LocalDateTime 转换为 GMT 格式字符串 */
    private static String formatGMT(java.time.LocalDateTime ldt) {
        if (ldt == null) return "";
        ZonedDateTime gmt = ldt.atZone(ZoneId.systemDefault())
                                .withZoneSameInstant(ZoneId.of("GMT"));
        return TIME_FMT.format(gmt) + " GMT";
    }

    // 全局 ID 计数器（用于 TableView 显示）
    private long nextId = 1;

    @Override
    public void start(Stage primaryStage) {
        // 初始化调度器（10分钟间隔，5秒初始延迟）
        scheduler = new MonitorScheduler(30, 3);

        // 构建界面
        primaryStage.setTitle("Brent Oil Monitor - 高并发监控系统");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(createScene());
        primaryStage.setOnCloseRequest(e -> {
            scheduler.shutdown();
            System.exit(0);
        });

        primaryStage.show();

        // ✅ 初始化完成后自动启动监控（30秒间隔）
        Platform.runLater(() -> startMonitor());
    }

    private Scene createScene() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #1e1e2e;");

        // 1. 顶部状态栏
        HBox statusBar = createStatusBar();
        root.getChildren().add(statusBar);

        // 2. 表格
        tableView = createTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);

        // 3. 底部控制栏
        HBox controlBar = createControlBar();
        root.getChildren().add(controlBar);

        // 设置回调（将后台数据推送到 UI）
        setupCallbacks();

        return new Scene(root);
    }

    /**
     * 创建顶部状态栏
     */
    private HBox createStatusBar() {
        HBox bar = new HBox(20);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setStyle(
            "-fx-background-color: #2a2a3e;" +
            "-fx-background-radius: 8;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        // 标题
        Label title = new Label("🛢️ Brent Oil Monitor / 布伦特原油监控");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#f1c40f"));

        // 分隔线
        Separator sep = new Separator();

        // 状态指标
        VBox runsBox   = makeStatBox("Runs",    lblRunsValue    = new Label("0"));
        VBox fetchedBox= makeStatBox("Fetched", lblFetchedValue = new Label("0"));
        VBox newBox    = makeStatBox("New",     lblNewValue     = new Label("0"));
        VBox matchedBox= makeStatBox("Matched", lblMatchedValue = new Label("0"));

        // 运行状态
        lblStatus = new Label("● Standby / 待机");
        lblStatus.setFont(Font.font("System", FontWeight.NORMAL, 13));
        lblStatus.setTextFill(Color.web("#888"));

        // 进度指示器
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setProgress(-1);

        Region spacer = new Region();

        bar.getChildren().addAll(
            title, sep,
            runsBox, fetchedBox, newBox, matchedBox,
            spacer, lblStatus, progressIndicator
        );

        // 设置权重，让状态标签均匀分布
        HBox.setHgrow(sep, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return bar;
    }

    private VBox makeStatBox(String label, Label valueLabel) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);

        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        valueLabel.setTextFill(Color.web("#00d4ff"));

        Label desc = new Label(label);
        desc.setFont(Font.font("System", FontWeight.NORMAL, 11));
        desc.setTextFill(Color.web("#888"));

        box.getChildren().addAll(valueLabel, desc);
        return box;
    }

    /**
     * 创建表格（TableView）
     */
    private TableView<NewsItem> createTableView() {
        TableView<NewsItem> table = new TableView<>();
        newsData = FXCollections.observableArrayList();
        table.setItems(newsData);

        // 样式
        table.setStyle(
            "-fx-background-color: #252535;" +
            "-fx-control-inner-background: #252535;" +
            "-fx-table-cell-border-color: #3a3a4e;" +
            "-fx-table-header-border-color: #3a3a4e;"
        );

        // 列：时间
        TableColumn<NewsItem, String> colTime = new TableColumn<>("Time / 时间");
        colTime.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                formatGMT(c.getValue().getTimestamp())
            )
        );
        colTime.setPrefWidth(150);

        // 列：来源
        TableColumn<NewsItem, String> colSource = new TableColumn<>("Source / 来源");
        colSource.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getSource())
        );
        colSource.setPrefWidth(100);

        // 列：内容
        TableColumn<NewsItem, String> colContent = new TableColumn<>("Summary / 内容摘要");
        colContent.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getContent())
        );
        colContent.setPrefWidth(500);

        // 列：油价
        TableColumn<NewsItem, String> colPrice = new TableColumn<>("Price / 油价");
        colPrice.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getFormattedPrice())
        );
        colPrice.setPrefWidth(100);

        table.getColumns().addAll(colTime, colSource, colContent, colPrice);

        // 表头样式 + 自适应宽度
        table.widthProperty().addListener((obs, oldVal, newVal) -> {
            double w = newVal.doubleValue();
            colTime.setPrefWidth(w * 0.15);
            colSource.setPrefWidth(w * 0.10);
            colContent.setPrefWidth(w * 0.60);
            colPrice.setPrefWidth(w * 0.15);
        });

        // 行悬停效果
        table.setRowFactory(tv -> {
            TableRow<NewsItem> row = new TableRow<>();
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) row.setStyle("-fx-background-color: #3a3a5e;");
            });
            row.setOnMouseExited(e -> {
                if (!row.isEmpty()) row.setStyle("-fx-background-color: transparent;");
            });
            return row;
        });

        return table;
    }

    /**
     * 创建底部控制栏
     */
    private HBox createControlBar() {
        HBox bar = new HBox(15);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setStyle(
            "-fx-background-color: #2a2a3e;" +
            "-fx-background-radius: 8;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        btnStart = new Button("▶ Start / 启动");
        btnStart.setStyle(
            "-fx-background-color: #27ae60;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 20 6 20;"
        );
        btnStart.setOnAction(e -> startMonitor());

        btnStop = new Button("■ Stop / 停止");
        btnStop.setDisable(true);
        btnStop.setStyle(
            "-fx-background-color: #c0392b;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 20 6 20;"
        );
        btnStop.setOnAction(e -> stopMonitor());

        cbSound = new CheckBox("🔔 Sound / 提示音");
        cbSound.setStyle("-fx-text-fill: #ccc; -fx-font-size: 13px;");

        // 间隔信息
        Label intervalLabel = new Label("Auto / 每 30 秒自动执行");
        intervalLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");

        Region spacer = new Region();
        bar.getChildren().addAll(btnStart, btnStop, cbSound, spacer, intervalLabel);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return bar;
    }

    /**
     * 设置数据回调（从后台线程推送到 UI）
     */
    private void setupCallbacks() {
        // 命中新闻回调：在 UI 线程更新表格
        scheduler.setOnNewsAccepted(item -> {
            Platform.runLater(() -> {
                NewsItem displayItem = new NewsItem(
                    nextId++,
                    item.getTimestamp(),
                    item.getSource(),
                    item.getContent(),
                    item.getPrice()
                );
                newsData.add(displayItem);

                // 按时间排序（最新的在最上面）
                FXCollections.sort(newsData, (a, b) ->
                    b.getTimestamp().compareTo(a.getTimestamp()));

                // 限制显示条数（最多 200 条）
                while (newsData.size() > 200) {
                    newsData.remove(newsData.size() - 1);
                }

                // 提示音（若开启）
                if (cbSound.isSelected()) {
                    playAlertSound();
                }
            });
        });

        // 统计回调：更新状态栏
        scheduler.setOnStatsUpdated(stats -> {
            Platform.runLater(() -> {
                lblRunsValue.setText(String.valueOf(stats.getRunCount()));
                lblFetchedValue.setText(String.valueOf(stats.getFetchedCount()));
                lblNewValue.setText(String.valueOf(stats.getNewCount()));
                lblMatchedValue.setText(String.valueOf(stats.getMatchedCount()));
            });
        });
    }

    private void startMonitor() {
        Logger.info("[UI] startMonitor() 被调用，scheduler.isRunning()=" + scheduler.isRunning());
        if (!scheduler.isRunning()) {
            scheduler.start();
            btnStart.setDisable(true);
            btnStop.setDisable(false);
            lblStatus.setText("● Running / 运行中");
            lblStatus.setTextFill(Color.web("#2ecc71"));
            progressIndicator.setVisible(true);

            // 闪烁状态（模拟运行中）
            Timeline blink = new Timeline(
                new KeyFrame(Duration.seconds(0.5), e -> lblStatus.setOpacity(1.0)),
                new KeyFrame(Duration.seconds(1.0), e -> lblStatus.setOpacity(0.5))
            );
            blink.setCycleCount(Animation.INDEFINITE);
            blink.play();
            lblStatus.setUserData(blink);
        }
    }

    private void stopMonitor() {
        scheduler.stop();
        btnStart.setDisable(false);
        btnStop.setDisable(true);
        lblStatus.setText("● 已停止");
        lblStatus.setTextFill(Color.web("#e74c3c"));
        progressIndicator.setVisible(false);

        Timeline blink = (Timeline) lblStatus.getUserData();
        if (blink != null) blink.stop();
        lblStatus.setOpacity(1.0);
    }

    private void playAlertSound() {
        try {
            // 使用系统提示音
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            System.err.println("提示音播放失败: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
