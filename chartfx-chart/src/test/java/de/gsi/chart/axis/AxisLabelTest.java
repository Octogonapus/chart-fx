package de.gsi.chart.axis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxAssert;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.DebugUtils;

import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.spi.AbstractAxis;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * @author Alexander Krimm
 */
@ExtendWith(ApplicationExtension.class)
class AxisLabelTest {
    AbstractAxis axis;
    Scene scene;
    FxRobot fxRobot = new FxRobot();

    @Start
    private void start(Stage stage) {
        axis = new DefaultNumericAxis();
        scene = new Scene(new StackPane(axis));
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void test() {
        FxAssert.verifyThat(axis, ax -> (((Axis) ax).getLength() == scene.getWidth()),
                DebugUtils.informedErrorMessage(fxRobot));
        // intentionally fails to test error reporting
        FxAssert.verifyThat(axis, ax -> (((Axis) ax).getLength() == scene.getWidth() + 1),
                DebugUtils.informedErrorMessage(fxRobot));
    }
}
