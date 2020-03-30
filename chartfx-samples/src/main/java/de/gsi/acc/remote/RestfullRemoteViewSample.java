package de.gsi.acc.remote;

import java.time.ZoneOffset;
import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.chart.XYChart;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.axes.spi.OscilloscopeAxis;
import de.gsi.chart.axes.spi.format.DefaultTimeFormatter;
import de.gsi.chart.plugins.EditAxis;
import de.gsi.chart.plugins.ParameterMeasurements;
import de.gsi.chart.plugins.Screenshot;
import de.gsi.chart.renderer.ErrorStyle;
import de.gsi.chart.renderer.datareduction.DefaultDataReducer;
import de.gsi.chart.renderer.spi.ErrorDataSetRenderer;
import de.gsi.chart.samples.ProfilerInfoBox;
import de.gsi.chart.samples.RollingBufferSample;
import de.gsi.chart.ui.geometry.Side;
import de.gsi.dataset.spi.LimitedIndexedTreeDataSet;
import de.gsi.dataset.utils.ProcessingProfiler;

public class RestfullRemoteViewSample extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestfullRemoteViewSample.class);
    private static final int DEBUG_UPDATE_RATE = 500;
    private static final int N_SAMPLES = 750; // 750 samples @ 25 Hz <-> 30 s
    private static final int UPDATE_PERIOD = 40; // [ms]
    private RestfullRemoteView remoteView;
    private XYChart chart;
    private final LimitedIndexedTreeDataSet currentDataSet = new LimitedIndexedTreeDataSet("dipole current [A]", N_SAMPLES);
    private final LimitedIndexedTreeDataSet intensityDataSet = new LimitedIndexedTreeDataSet("beam intensity [ppp]", N_SAMPLES);
    private Timer timer;
    private Runnable startStopTimerAction = () -> {
        if (timer == null) {
            timer = new Timer("sample-update-timer", true);
            intensityDataSet.reset();
            currentDataSet.reset();
            timer.scheduleAtFixedRate(getTask(), 0, UPDATE_PERIOD);
        } else {
            timer.cancel();
            timer = null;
        }
    };

    private BorderPane initComponents(Scene scene) {
        ErrorDataSetRenderer beamIntensityRenderer = new ErrorDataSetRenderer();
        initErrorDataSetRenderer(beamIntensityRenderer);
        ErrorDataSetRenderer dipoleCurrentRenderer = new ErrorDataSetRenderer();
        initErrorDataSetRenderer(dipoleCurrentRenderer);

        final DefaultNumericAxis xAxis = new DefaultNumericAxis("time");
        xAxis.setAutoRangeRounding(false);
        xAxis.setAutoRangePadding(0.001);
        xAxis.setTimeAxis(true);
        final OscilloscopeAxis yAxis1 = new OscilloscopeAxis("beam intensity", "ppp");
        final OscilloscopeAxis yAxis2 = new OscilloscopeAxis("dipole current", "A");
        yAxis2.setSide(Side.RIGHT);
        yAxis1.setAxisZeroPosition(0.05);
        yAxis2.setAxisZeroPosition(0.05);
        yAxis1.setAutoRangeRounding(true);
        yAxis2.setAutoRangeRounding(true);

        // N.B. it's important to set secondary axis on the 2nd renderer before
        // adding the renderer to the chart
        dipoleCurrentRenderer.getAxes().add(yAxis2);

        chart = new XYChart(xAxis, yAxis1);
        chart.getPlugins().add(new ParameterMeasurements());
        chart.getPlugins().add(new Screenshot());
        chart.getPlugins().add(new EditAxis());
        chart.legendVisibleProperty().set(true);
        chart.setAnimated(false);
        chart.getYAxis().setName(intensityDataSet.getName());
        chart.getRenderers().set(0, beamIntensityRenderer);
        chart.getRenderers().add(dipoleCurrentRenderer);
        chart.getPlugins().add(new EditAxis());

        beamIntensityRenderer.getDatasets().add(intensityDataSet);
        dipoleCurrentRenderer.getDatasets().add(currentDataSet);

        // set localised time offset
        if (xAxis.isTimeAxis() && xAxis.getAxisLabelFormatter() instanceof DefaultTimeFormatter) {
            final DefaultTimeFormatter axisFormatter = (DefaultTimeFormatter) xAxis.getAxisLabelFormatter();

            axisFormatter.setTimeZoneOffset(ZoneOffset.UTC);
            axisFormatter.setTimeZoneOffset(ZoneOffset.ofHoursMinutes(2, 0));
        }

        final BorderPane root = new BorderPane(chart);
        root.setTop(getHeaderBar(scene));

        return root;
    }

    @Override
    public void start(final Stage primaryStage) {
        ProcessingProfiler.setVerboseOutputState(true);
        ProcessingProfiler.setLoggerOutputState(true);
        ProcessingProfiler.setDebugState(false);

        final BorderPane root = new BorderPane();
        final Scene scene = new Scene(root, 1800, 400);
        root.setCenter(initComponents(scene));

        final long startTime = ProcessingProfiler.getTimeStamp();
        primaryStage.setTitle(this.getClass().getSimpleName());
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(evt -> System.exit(0));
        primaryStage.show();
        ProcessingProfiler.getTimeDiff(startTime, "for showing");

        remoteView = new RestfullRemoteView("status", chart, UPDATE_PERIOD);
        //        remoteView = new RestfullRemoteView("status", chart, 1000);
        chart.addListener(obs -> remoteView.handle(null));

        startStopTimerAction.run();
    }

    private void initErrorDataSetRenderer(final ErrorDataSetRenderer eRenderer) {
        eRenderer.setErrorType(ErrorStyle.ERRORSURFACE);
        eRenderer.setDashSize(1);
        eRenderer.setDrawMarker(false);
        final DefaultDataReducer reductionAlgorithm = (DefaultDataReducer) eRenderer.getRendererDataReducer();
        reductionAlgorithm.setMinPointPixelDistance(1);
    }

    private void createNewScene(WritableImage writableImage, final boolean platformExitOnClose) {
        Stage secondaryStage = new Stage();

        ImageView imageView = new ImageView(writableImage);
        Scene scene = new Scene(new BorderPane(imageView));

        secondaryStage.setTitle(this.getClass().getSimpleName() + " - copy ");
        secondaryStage.setScene(scene);
        if (platformExitOnClose) {
            secondaryStage.setOnCloseRequest(evt -> System.exit(0));
        }
        secondaryStage.show();
    }

    private void generateData() {
        final long startTime = ProcessingProfiler.getTimeStamp();
        final double now = System.currentTimeMillis() / 1000.0 + 1; // N.B. '+1' to check for resolution

        if (currentDataSet.getDataCount() == 0) {
            intensityDataSet.autoNotification().set(false);
            currentDataSet.autoNotification().set(false);
            for (int n = RollingBufferSample.N_SAMPLES; n > 0; n--) {
                final double t = now - n * RollingBufferSample.UPDATE_PERIOD / 1000.0;
                final double y = 25 * RollingBufferSample.rampFunctionDipoleCurrent(t);
                final double y2 = 100 * RollingBufferSample.rampFunctionBeamIntensity(t);
                final double ey = 1;
                currentDataSet.add(t, y, ey, ey);
                intensityDataSet.add(t, y2, ey, ey);
            }
            intensityDataSet.autoNotification().set(true);
            currentDataSet.autoNotification().set(true);
        } else {
            currentDataSet.autoNotification().set(false);
            final double t = now;
            final double y = 25 * RollingBufferSample.rampFunctionDipoleCurrent(t);
            final double y2 = 100 * RollingBufferSample.rampFunctionBeamIntensity(t);
            final double ey = 1;
            currentDataSet.add(t, y, ey, ey);
            intensityDataSet.add(t, y2, ey, ey);
            currentDataSet.autoNotification().set(true);
        }
        ProcessingProfiler.getTimeDiff(startTime, "adding data into DataSet");
    }

    private HBox getHeaderBar(Scene scene) {
        final Button newChartCopy = new Button("new chart copy");
        newChartCopy.setOnAction(evt -> createNewScene(remoteView.getWritableImage(), false));

        final Button newDataSet = new Button("new DataSet");
        newDataSet.setOnAction(evt -> Platform.runLater(getTask()));

        final Button startTimer = new Button("timer");
        startTimer.setOnAction(evt -> startStopTimerAction.run());

        // H-Spacer
        Region spacer = new Region();
        spacer.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(newChartCopy, newDataSet, startTimer, spacer, new ProfilerInfoBox(scene, DEBUG_UPDATE_RATE));
    }

    private TimerTask getTask() {
        return new TimerTask() {
            private int updateCount;

            @Override
            public void run() {
                Platform.runLater(() -> {
                    generateData();

                    if (updateCount % 10000 == 0) {
                        LOGGER.atInfo().log("update iteration #" + updateCount);
                    }
                    updateCount++;
                });
            }
        };
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}