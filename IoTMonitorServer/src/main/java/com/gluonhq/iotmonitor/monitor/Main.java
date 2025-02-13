package com.gluonhq.iotmonitor.monitor;

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.MapChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.controlsfx.control.NotificationPane;
import org.controlsfx.control.action.Action;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material.Material;

import static com.gluonhq.iotmonitor.monitor.Model.nodeMapper;
import static com.gluonhq.iotmonitor.monitor.Model.unresponsiveNodes;

public class Main extends Application {

    static boolean TEST_MODE = "test".equalsIgnoreCase(System.getenv("picluster_mode"));

    private NotificationPane notificationPane;
    private FlowPane flowPane;
    private DataReader dr;

    @Override
    public void start(Stage stage) {
        dr = new DataReader();
        dr.startReading();
        BorderPane bp = new BorderPane();
        flowPane = new FlowPane(Orientation.HORIZONTAL);
        flowPane.setPadding(new Insets(5));
        flowPane.setHgap(5);
        flowPane.setVgap(5);
        ScrollPane scrollPane = new ScrollPane();
        flowPane.prefWrapLengthProperty().bind(scrollPane.widthProperty().subtract(20));
        scrollPane.setContent(flowPane);
        bp.setCenter(scrollPane);
        bp.setTop(setUpTopPane());

        notificationPane = new NotificationPane(bp);
        final Action showNotification = new Action("Show All", e -> {
            final ListView<Node> unresponsiveNodeListView = new ListView<>(unresponsiveNodes);
            unresponsiveNodeListView.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(Node item, boolean empty) {
                    if (empty) {
                        setText(null);
                    } else {
                        setText(formatTextForUnresponsiveNode(item));
                    }
                }
            });
            final Scene scene = new Scene(new StackPane(unresponsiveNodeListView), 500, 500);
            final Stage notificationStage = new Stage();
            notificationStage.setTitle("Unresponsive Devices");
            notificationStage.setScene(scene);
            notificationStage.show();
            notificationPane.hide();
        });
        
        unresponsiveNodes.addListener((InvalidationListener) o -> {
            if (unresponsiveNodes.isEmpty()) {
                notificationPane.hide();
            } else {
                if (unresponsiveNodes.size() > 2) {
                    notificationPane.getActions().setAll(showNotification);
                }
                notificationPane.show();
            }
        });

        notificationPane.textProperty().bind(Bindings.createStringBinding(() ->
                 unresponsiveNodes.stream()
                .limit(2)
                .map(this::formatTextForUnresponsiveNode)
                .reduce("", (s1, s2) -> s1 + s2), unresponsiveNodes)
        );

        Scene scene = new Scene(notificationPane, 640, 480);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();

        nodeMapper.addListener((MapChangeListener<String, Node>) change -> {
            if (change.wasAdded()) {
                final Node node = change.getValueAdded();
                NodeView view = new NodeView(node);
                flowPane.getChildren().add(view);
            } else if (change.wasRemoved()) {
                flowPane.getChildren().removeIf(n -> {
                    if (n instanceof NodeView) {
                        return ((NodeView) n).getNodeId().equals(change.getValueRemoved().getId());
                    }
                    return false;
                });
            }
        });
    }

    @Override
    public void stop() {
        if (dr != null) {
            dr.stopReading();
        }
        nodeMapper.forEach((k, v) -> v.stop());
    }

    private GridPane setUpTopPane() {

        Counter totalNodeCounter = new Counter("Total\nNodes");
        Counter unresponsiveNodeCounter = new Counter("Unresponsive\nNodes");
        final Button fixAll = new Button("Fix All");
        fixAll.setGraphic(FontIcon.of(Material.FLASH_ON, 18));
        fixAll.setOnAction(e -> unresponsiveNodes.clear());

        final GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("top-pane");

        javafx.scene.Node[] gridNodes = {totalNodeCounter, unresponsiveNodeCounter, fixAll};
        for (int i = 0; i < gridNodes.length; i++) {
            gridPane.add(gridNodes[i], i, 0);
            GridPane.setHgrow(gridNodes[i], Priority.ALWAYS);
            GridPane.setHalignment(gridNodes[i], HPos.CENTER);
        }

        totalNodeCounter.countLabel.textProperty().bind(Bindings.concat("").concat(Bindings.size(nodeMapper)));
        unresponsiveNodeCounter.countLabel.textProperty().bind(Bindings.concat("").concat(Bindings.size(unresponsiveNodes)));

        return gridPane;
    }

    private String formatTextForUnresponsiveNode(Node item) {
        return String.format("Device %s needs powercycle.\n", item.lastKnownIp().get());
    }

}

class Counter extends HBox {

    final Label countLabel;

    Counter( String title ) {
        countLabel = new Label();
        countLabel.setStyle("-fx-font-size: 3em");
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: .9em; -fx-opacity: .7;");
        setSpacing(10);
        getChildren().addAll(countLabel, titleLabel);
        setAlignment(Pos.CENTER);
    }

}
