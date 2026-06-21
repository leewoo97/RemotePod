package com.sshmanager;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DevPodController {

    @FXML private Label titleLabel;
    @FXML private Label backButton;
    @FXML private Node workspaceView;
    @FXML private Node createView;
    @FXML private Node emptyWorkspaceState;
    @FXML private VBox workspaceList;
    @FXML private TextField workspaceNameField;
    @FXML private TextField sshServerAddressField;
    @FXML private TextField sshUserField;
    @FXML private PasswordField sshPasswordField;
    @FXML private TextField projectPathField;
    @FXML private TextField devcontainerPathField;
    @FXML private Button createWorkspaceButton;
    @FXML private Button headerCreateButton;
    @FXML private Button cancelWorkspaceButton;
    @FXML private Node consoleView;
    @FXML private TextArea consoleOutputArea;

    private final SshService sshService = new SshService();
    private Task<String> activeCreateTask;

    @FXML
    private void initialize() {
        loadSvgIcons(workspaceView);
        loadSvgIcons(createView);
        loadSvgIcons(consoleView);
    }

    @FXML
    private void showCreateWorkspace() {
        titleLabel.setText("Create Workspace");
        backButton.setVisible(true);
        backButton.setManaged(true);
        headerCreateButton.setVisible(true);
        headerCreateButton.setManaged(true);
        cancelWorkspaceButton.setVisible(false);
        cancelWorkspaceButton.setManaged(false);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        createView.setVisible(true);
        createView.setManaged(true);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
    }

    @FXML
    private void showWorkspaces() {
        titleLabel.setText("Workspaces");
        backButton.setVisible(false);
        backButton.setManaged(false);
        headerCreateButton.setVisible(true);
        headerCreateButton.setManaged(true);
        cancelWorkspaceButton.setVisible(false);
        cancelWorkspaceButton.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        workspaceView.setVisible(true);
        workspaceView.setManaged(true);
    }

    @FXML
    private void cancelWorkspaceCreation() {
        appendConsole("\nCancel requested. Closing SSH session...\n");
        if (activeCreateTask != null) {
            activeCreateTask.cancel();
        }
        sshService.disconnect();
        cancelWorkspaceButton.setDisable(true);
    }

    @FXML
    private void createWorkspace() {
        String workspaceName = workspaceNameField.getText() == null
                ? ""
                : workspaceNameField.getText().trim();

        if (workspaceName.isEmpty()) {
            workspaceName = "my-workspace";
        }

        String address = getTrimmedText(sshServerAddressField);
        String user = getTrimmedText(sshUserField);
        String password = sshPasswordField.getText() == null ? "" : sshPasswordField.getText();
        String projectPath = getTrimmedText(projectPathField);
        String devcontainerPath = getTrimmedText(devcontainerPathField);

        if (address.isEmpty() || user.isEmpty() || projectPath.isEmpty() || devcontainerPath.isEmpty()) {
            showWarning("Missing required fields",
                    "SSH Server Address, User, Project Path, and Devcontainer Path are required.");
            return;
        }

        ServerEndpoint endpoint;
        try {
            endpoint = parseServerAddress(address);
        } catch (IllegalArgumentException e) {
            showWarning("Invalid SSH server address", e.getMessage());
            return;
        }

        String command = "devpod up "
                + shellQuote(projectPath)
                + " --devcontainer-path "
                + shellQuote(devcontainerPath)
                + " --ide none";
        ServerInfo server = new ServerInfo(workspaceName, endpoint.host(), endpoint.port(), user, password);
        runCreateWorkspaceTask(workspaceName, server, command);
    }

    private void runCreateWorkspaceTask(String workspaceName, ServerInfo server, String command) {
        createWorkspaceButton.setDisable(true);
        createWorkspaceButton.setText("Creating...");
        showConsole(workspaceName);
        appendConsole("Connecting to " + server.getUser() + "@" + server.getHost() + ":" + server.getPort() + "...\n");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                sshService.connect(server);
                appendConsole("Connected.\n");
                appendConsole("$ " + command + "\n\n");
                return sshService.executeCheckedStreaming(command, 3600, DevPodController.this::appendConsole);
            }

            @Override
            protected void succeeded() {
                sshService.disconnect();
                createWorkspaceButton.setDisable(false);
                createWorkspaceButton.setText("Create Workspace");
                cancelWorkspaceButton.setVisible(false);
                cancelWorkspaceButton.setManaged(false);
                cancelWorkspaceButton.setDisable(false);
                emptyWorkspaceState.setVisible(false);
                emptyWorkspaceState.setManaged(false);
                workspaceList.getChildren().add(createWorkspaceRow(workspaceName, "Running"));
                appendConsole("\nWorkspace is running.\n");
            }

            @Override
            protected void failed() {
                sshService.disconnect();
                createWorkspaceButton.setDisable(false);
                createWorkspaceButton.setText("Create Workspace");
                cancelWorkspaceButton.setVisible(false);
                cancelWorkspaceButton.setManaged(false);
                cancelWorkspaceButton.setDisable(false);
                Throwable exception = getException();
                appendConsole("\nCreate Workspace failed: "
                        + (exception == null ? "Unknown error" : exception.getMessage())
                        + "\n");
                showWarning("Create Workspace failed",
                        exception == null ? "Unknown error" : exception.getMessage());
            }

            @Override
            protected void cancelled() {
                sshService.disconnect();
                createWorkspaceButton.setDisable(false);
                createWorkspaceButton.setText("Create Workspace");
                cancelWorkspaceButton.setVisible(false);
                cancelWorkspaceButton.setManaged(false);
                cancelWorkspaceButton.setDisable(false);
                appendConsole("Cancelled.\n");
            }
        };

        activeCreateTask = task;
        Thread thread = new Thread(task, "devpod-create-workspace");
        thread.setDaemon(true);
        thread.start();
    }

    private void showConsole(String workspaceName) {
        titleLabel.setText("start " + workspaceName);
        backButton.setVisible(true);
        backButton.setManaged(true);
        headerCreateButton.setVisible(false);
        headerCreateButton.setManaged(false);
        cancelWorkspaceButton.setVisible(true);
        cancelWorkspaceButton.setManaged(true);
        cancelWorkspaceButton.setDisable(false);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        consoleView.setVisible(true);
        consoleView.setManaged(true);
        consoleOutputArea.clear();
    }

    private void appendConsole(String text) {
        Platform.runLater(() -> {
            consoleOutputArea.appendText(text);
            consoleOutputArea.positionCaret(consoleOutputArea.getLength());
        });
    }

    private Node createWorkspaceRow(String workspaceName, String statusText) {
        HBox row = new HBox(14);
        row.setMinHeight(58);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dce3ec; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label name = new Label(workspaceName);
        name.setStyle("-fx-text-fill: #111827; -fx-font-size: 15px; -fx-font-weight: 800;");

        Label type = new Label("SSH");
        type.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px; -fx-font-weight: 700;");

        Label status = new Label(statusText);
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px; -fx-font-weight: 700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        row.getChildren().addAll(name, type, spacer, status);
        return row;
    }

    private String getTrimmedText(TextField textField) {
        return textField.getText() == null ? "" : textField.getText().trim();
    }

    private ServerEndpoint parseServerAddress(String address) {
        String host = address;
        int port = 22;

        int separatorIndex = address.lastIndexOf(':');
        if (separatorIndex > 0 && separatorIndex < address.length() - 1) {
            host = address.substring(0, separatorIndex).trim();
            String portText = address.substring(separatorIndex + 1).trim();
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port must be a number.");
            }
        }

        if (host.isEmpty()) {
            throw new IllegalArgumentException("Host is required.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }

        return new ServerEndpoint(host, port);
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record ServerEndpoint(String host, int port) {
    }

    private void loadSvgIcons(Node node) {
        if (node instanceof WebView webView && webView.getUserData() instanceof String iconName) {
            webView.setPageFill(Color.TRANSPARENT);
            webView.setStyle("-fx-background-color: transparent;");
            var iconUrl = getClass().getResource("/icons/" + iconName);
            if (iconUrl == null) {
                iconUrl = getClass().getResource("/images/" + iconName);
            }
            if (iconUrl != null) {
                try {
                    String svg = new String(iconUrl.openStream().readAllBytes(), StandardCharsets.UTF_8);
                    svg = svg.replaceFirst("<svg\\s+", "<svg style=\"color:#111827\" ");
                    String html = """
                        <html>
                          <head>
                            <style>
                              html, body { margin: 0; width: 100%%; height: 100%%; overflow: hidden; background: rgba(0,0,0,0); color: #111827; }
                              body { display: flex; align-items: center; justify-content: center; }
                              svg { width: 100%%; height: 100%%; display: block; }
                            </style>
                          </head>
                          <body>
                            %s
                          </body>
                        </html>
                        """.formatted(svg);
                    webView.getEngine().loadContent(html);
                    webView.setContextMenuEnabled(false);
                    Platform.runLater(() -> {
                        webView.setPageFill(Color.TRANSPARENT);
                        webView.lookupAll(".scroll-bar").forEach(child -> child.setVisible(false));
                        Node page = webView.lookup(".web-page");
                        if (page != null) {
                            page.setStyle("-fx-background-color: transparent;");
                        }
                    });
                } catch (IOException ignored) {
                    // Missing or unreadable decorative icon; leave the slot empty.
                }
            }
        }

        if (node instanceof Labeled labeled && labeled.getGraphic() != null) {
            loadSvgIcons(labeled.getGraphic());
        }

        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            loadSvgIcons(scrollPane.getContent());
        }

        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                loadSvgIcons(child);
            }
        }
    }
}
