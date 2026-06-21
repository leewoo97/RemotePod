package com.sshmanager;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class DevPodController {

    @FXML private Label titleLabel;
    @FXML private Label backButton;
    @FXML private Node workspaceView;
    @FXML private Node createView;
    @FXML private Node emptyWorkspaceState;
    @FXML private VBox workspaceList;
    @FXML private TextField workspaceNameField;

    @FXML
    private void showCreateWorkspace() {
        titleLabel.setText("Create Workspace");
        backButton.setVisible(true);
        backButton.setManaged(true);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        createView.setVisible(true);
        createView.setManaged(true);
    }

    @FXML
    private void showWorkspaces() {
        titleLabel.setText("Workspaces");
        backButton.setVisible(false);
        backButton.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        workspaceView.setVisible(true);
        workspaceView.setManaged(true);
    }

    @FXML
    private void createWorkspace() {
        String workspaceName = workspaceNameField.getText() == null
                ? ""
                : workspaceNameField.getText().trim();

        if (workspaceName.isEmpty()) {
            workspaceName = "my-workspace";
        }

        emptyWorkspaceState.setVisible(false);
        emptyWorkspaceState.setManaged(false);
        workspaceList.getChildren().add(createWorkspaceRow(workspaceName));
        showWorkspaces();
    }

    private Node createWorkspaceRow(String workspaceName) {
        HBox row = new HBox(14);
        row.setMinHeight(58);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dce3ec; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label name = new Label(workspaceName);
        name.setStyle("-fx-text-fill: #111827; -fx-font-size: 15px; -fx-font-weight: 800;");

        Label type = new Label("SSH");
        type.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px; -fx-font-weight: 700;");

        Label status = new Label("Stopped");
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px; -fx-font-weight: 700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        row.getChildren().addAll(name, type, spacer, status);
        return row;
    }
}
