package de.gsi.chart.plugins.measurements;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxAssert;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.robot.Motion;
import org.testfx.util.DebugUtils;

import de.gsi.chart.XYChart;
import de.gsi.chart.plugins.ParameterMeasurements;
import de.gsi.chart.plugins.measurements.utils.CheckedValueField;
import de.gsi.dataset.testdata.spi.CosineFunction;
import de.gsi.dataset.testdata.spi.RandomWalkFunction;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * @author Alexander Krimm
 */
@ExtendWith(ApplicationExtension.class)
class ParameterMeasurementTest {
    Scene scene;
    FxRobot fxRobot = new FxRobot();

    @Start
    private void start(Stage stage) {
        // create chart
        XYChart chart = new XYChart();
        chart.setId("myChart");
        // add measurement plugin
        ParameterMeasurements parameterMeasurements = new ParameterMeasurements();
        chart.getPlugins().add(parameterMeasurements);
        // add DataSets
        chart.getDatasets().add(new RandomWalkFunction("random function", 512));
        CosineFunction dataset = new CosineFunction("Cosine", 512);
        chart.getDatasets().add(dataset);
        // show scene
        scene = new Scene(new StackPane(chart));
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void checkValueIndicator() {
        // check that the chart has two axes
        FxAssert.verifyThat("#myChart", node -> {
            return ((XYChart) node).getAxes().size() == 2;
        }, DebugUtils.informedErrorMessage(fxRobot));

        // Add a new measurement
        Point2D toolbarPoint = fxRobot.offset(fxRobot.lookup("#myChart").query(), Pos.TOP_CENTER, new Point2D(0, 1))
                .query();
        fxRobot.moveTo(toolbarPoint).sleep(1000).interrupt().clickOn("#parameterMeasurementMenu")
                .moveTo(((Node) fxRobot.lookup(LabeledMatchers.hasText("Indicators")).query()), Motion.DIRECT) //
                .sleep(10)//
                .press(KeyCode.DOWN) //
                .press(KeyCode.RIGHT).sleep(10)//
                .press(KeyCode.ENTER); //

        // confirm the dialog (not working)
        fxRobot.targetWindow("Measurement Config Dialog");
        // fxRobot.interact(() -> fxRobot.window("Measurement Config Dialog").requestFocus());
        //  .press(MouseButton.PRIMARY) //
        //  .clickOn(((Node) fxRobot.lookup(LabeledMatchers.hasText("Indicators")).query())) //
        //  .sleep(500)//
        //  .moveTo(((Node) fxRobot.lookup(LabeledMatchers.hasText("hor. value")).query())) //
        //  .sleep(500) //
        //  .clickOn(((Node) fxRobot.lookup(LabeledMatchers.hasText("hor. value")).query()));
        // fxRobot.interrupt().sleep(200)//
        //  .moveTo(fxRobot.point("OK").atOffset(0, -20)).sleep(500)//
        //  .press(MouseButton.PRIMARY) //
        //  .press(KeyCode.ENTER)//
        //  .interrupt().sleep(2000);//
        // System.out.println(fxRobot.targetWindow());
        // Hack: closing the dialog window does not work, so force the dialog closed.
        Window configWindow = fxRobot.window("Measurement Config Dialog");
        if (configWindow != null && configWindow instanceof Stage) {
            fxRobot.interact(() -> ((Stage) configWindow).close());
        }

        // check initial measurement value
        Node measurementLabel = fxRobot.lookup("#valueField_hor_value_[0_1]").query();
        Label label = null;
        if (measurementLabel instanceof CheckedValueField) {
            CheckedValueField valLabel = (CheckedValueField) measurementLabel;
            label = (Label) ((HBox) valLabel.getChildren().get(1)).getChildren().get(0);
        }
        if (label == null) {
            fail("could not locate measurement plane");
        }
        double valueBefore = Double.valueOf(label.getText());
        fxRobot.interrupt();
        // move the indicator
        fxRobot.clickOn(".value-indicator-line"); // arbitrary click, because else the drag does not work
        fxRobot.drag(".value-indicator-label").dropBy(200, 10).sleep(10);

        // check updated measurement value and verify that it is bigger than the original
        fxRobot.interrupt();
        double valueAfter = Double.valueOf(label.getText());
        FxAssert.verifyThat(label, node -> (Double.valueOf(node.getText()) > valueBefore),
                DebugUtils.informedErrorMessage(fxRobot));
    }
}
