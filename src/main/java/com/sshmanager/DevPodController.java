package com.sshmanager;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
import java.util.Base64;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

public class DevPodController {
    private static final Pattern ANSI_ESCAPE_SEQUENCE = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]|\\[[0-9;]*m");
    private static final String CREATE_BUTTON_READY_STYLE = "-fx-background-color: #c66cf0; -fx-background-radius: 5; -fx-padding: 8 14; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 700;";
    private static final String CREATE_BUTTON_PENDING_STYLE = "-fx-background-color: #ead3f8; -fx-background-radius: 5; -fx-padding: 8 14; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 700;";
    private static final String EDIT_BUTTON_READY_STYLE = "-fx-background-color: #8acb63; -fx-background-radius: 5; -fx-padding: 8 14; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 700;";
    private static final String EDIT_BUTTON_PENDING_STYLE = "-fx-background-color: #d7f2cc; -fx-background-radius: 5; -fx-padding: 8 14; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 700;";

    @FXML private Label titleLabel;
    @FXML private Label backButton;
    @FXML private Node workspaceView;
    @FXML private Node serversView;
    @FXML private Node createView;
    @FXML private Node emptyWorkspaceState;
    @FXML private Node emptyServerState;
    @FXML private VBox workspaceList;
    @FXML private VBox serverList;
    @FXML private HBox workspacesNav;
    @FXML private HBox serversNav;
    @FXML private Label serversNavLabel;
    @FXML private TextField workspaceNameField;
    @FXML private ComboBox<ServerInfo> sshServerComboBox;
    @FXML private TextField projectPathField;
    @FXML private TextField devcontainerPathField;
    @FXML private Button createWorkspaceButton;
    @FXML private Button editDevcontainerButton;
    @FXML private Button headerCreateButton;
    @FXML private Button cancelWorkspaceButton;
    @FXML private Node consoleView;
    @FXML private TextArea consoleOutputArea;
    @FXML private Node devcontainerEditorView;
    @FXML private Label devcontainerEditorPathLabel;
    @FXML private Label devcontainerEditorStatusLabel;
    @FXML private TextArea devcontainerEditorArea;
    @FXML private Button saveDevcontainerButton;
    @FXML private Node newServerModal;
    @FXML private TextField newServerAddressField;
    @FXML private TextField newServerUserField;
    @FXML private PasswordField newServerPasswordField;

    private final SshService sshService = new SshService();
    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();
    private final Preferences serverPreferences = Preferences.userNodeForPackage(DevPodController.class).node("servers");
    private Task<String> activeCreateTask;
    private ServerInfo activeEditorServer;
    private String activeEditorRemotePath;

    @FXML
    private void initialize() {
        loadSvgIcons(workspaceView);
        loadSvgIcons(createView);
        loadSvgIcons(consoleView);
        loadSvgIcons(devcontainerEditorView);
        sshServerComboBox.setItems(servers);
        loadServers();
        workspaceNameField.textProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        sshServerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        projectPathField.textProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        devcontainerPathField.textProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        updateWorkspaceActionButtonStyles();
    }

    @FXML
    private void goBack() {
        if (devcontainerEditorView.isVisible()) {
            sshService.disconnect();
            activeEditorServer = null;
            activeEditorRemotePath = null;
            showCreateWorkspace();
            return;
        }

        showWorkspaces();
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
        serversView.setVisible(false);
        serversView.setManaged(false);
        createView.setVisible(true);
        createView.setManaged(true);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);
    }

    @FXML
    public void showWorkspaces() {
        titleLabel.setText("Workspaces");
        backButton.setVisible(false);
        backButton.setManaged(false);
        headerCreateButton.setVisible(true);
        headerCreateButton.setManaged(true);
        cancelWorkspaceButton.setVisible(false);
        cancelWorkspaceButton.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        serversView.setVisible(false);
        serversView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);
        workspaceView.setVisible(true);
        workspaceView.setManaged(true);
        setActiveNavigation(false);
        sshService.disconnect();
    }

    @FXML
    private void showServers() {
        titleLabel.setText("Servers");
        backButton.setVisible(false);
        backButton.setManaged(false);
        headerCreateButton.setVisible(false);
        headerCreateButton.setManaged(false);
        cancelWorkspaceButton.setVisible(false);
        cancelWorkspaceButton.setManaged(false);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);
        serversView.setVisible(true);
        serversView.setManaged(true);
        setActiveNavigation(true);
    }

    @FXML
    private void showNewServerModal() {
        newServerAddressField.clear();
        newServerUserField.clear();
        newServerPasswordField.clear();
        newServerModal.setVisible(true);
        newServerModal.setManaged(true);
        Platform.runLater(newServerAddressField::requestFocus);
    }

    @FXML
    private void hideNewServerModal() {
        newServerModal.setVisible(false);
        newServerModal.setManaged(false);
    }

    @FXML
    private void saveNewServer() {
        String address = getTrimmedText(newServerAddressField);
        String user = getTrimmedText(newServerUserField);
        String password = newServerPasswordField.getText() == null ? "" : newServerPasswordField.getText();
        if (address.isEmpty() || user.isEmpty() || password.isEmpty()) {
            showWarning("Missing required fields", "SSH Server Address, User, and Password are required.");
            return;
        }

        ServerEndpoint endpoint;
        try {
            endpoint = parseServerAddress(address);
        } catch (IllegalArgumentException e) {
            showWarning("Invalid SSH server address", e.getMessage());
            return;
        }

        ServerInfo server = new ServerInfo(
                user + "@" + endpoint.host() + ":" + endpoint.port(),
                endpoint.host(),
                endpoint.port(),
                user,
                password
        );
        servers.add(server);
        persistServers();
        refreshServerList();
        sshServerComboBox.getSelectionModel().select(server);
        hideNewServerModal();
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
        WorkspaceInput input = readWorkspaceInput();
        if (input == null) {
            return;
        }

        String command = "devpod up "
                + shellQuote(input.projectPath())
                + " --devcontainer-path "
                + shellQuote(input.devcontainerPath())
                + " --id "
                + shellQuote(input.workspaceName())
                + " --ide none";
        runCreateWorkspaceTask(input.workspaceName(), input.server(), command);
    }

    @FXML
    private void editDevcontainer() {
        WorkspaceInput input = readEditDevcontainerInput();
        if (input == null) {
            return;
        }

        String remotePath = combineRemotePath(input.projectPath(), input.devcontainerPath());
        activeEditorServer = input.server();
        activeEditorRemotePath = remotePath;
        showDevcontainerEditor(remotePath);
        devcontainerEditorArea.clear();
        devcontainerEditorArea.setDisable(true);
        saveDevcontainerButton.setDisable(true);
        editDevcontainerButton.setDisable(true);
        devcontainerEditorStatusLabel.setText("Connecting to " + input.server().getUser()
                + "@" + input.server().getHost() + ":" + input.server().getPort() + "...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                sshService.connect(input.server());
                return sshService.readTextFile(remotePath);
            }

            @Override
            protected void succeeded() {
                devcontainerEditorArea.setText(getValue());
                devcontainerEditorArea.setDisable(false);
                saveDevcontainerButton.setDisable(false);
                editDevcontainerButton.setDisable(false);
                devcontainerEditorStatusLabel.setText("Loaded from SSH.");
            }

            @Override
            protected void failed() {
                sshService.disconnect();
                devcontainerEditorArea.setDisable(false);
                saveDevcontainerButton.setDisable(false);
                editDevcontainerButton.setDisable(false);
                Throwable exception = getException();
                String message = exception == null ? "Unknown error" : exception.getMessage();
                devcontainerEditorStatusLabel.setText("Failed to load file.");
                showWarning("Edit devcontainer.json failed", message);
                showCreateWorkspace();
            }
        };

        Thread thread = new Thread(task, "devcontainer-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void saveDevcontainer() {
        if (activeEditorServer == null || activeEditorRemotePath == null) {
            showWarning("Cannot save devcontainer.json", "No remote devcontainer.json file is open.");
            return;
        }

        String content = devcontainerEditorArea.getText() == null ? "" : devcontainerEditorArea.getText();
        saveDevcontainerButton.setDisable(true);
        devcontainerEditorStatusLabel.setText("Saving...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!sshService.isConnected()) {
                    sshService.connect(activeEditorServer);
                }
                sshService.writeTextFile(activeEditorRemotePath, content);
                return null;
            }

            @Override
            protected void succeeded() {
                saveDevcontainerButton.setDisable(false);
                devcontainerEditorStatusLabel.setText("Saved.");
            }

            @Override
            protected void failed() {
                saveDevcontainerButton.setDisable(false);
                Throwable exception = getException();
                String message = exception == null ? "Unknown error" : exception.getMessage();
                devcontainerEditorStatusLabel.setText("Save failed.");
                showWarning("Save devcontainer.json failed", message);
            }
        };

        Thread thread = new Thread(task, "devcontainer-save");
        thread.setDaemon(true);
        thread.start();
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
        serversView.setVisible(false);
        serversView.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        consoleView.setVisible(true);
        consoleView.setManaged(true);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);
        consoleOutputArea.clear();
    }

    private void showDevcontainerEditor(String remotePath) {
        titleLabel.setText("Edit devcontainer.json");
        backButton.setVisible(true);
        backButton.setManaged(true);
        headerCreateButton.setVisible(false);
        headerCreateButton.setManaged(false);
        cancelWorkspaceButton.setVisible(false);
        cancelWorkspaceButton.setManaged(false);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        serversView.setVisible(false);
        serversView.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(true);
        devcontainerEditorView.setManaged(true);
        devcontainerEditorPathLabel.setText(remotePath);
    }

    private void appendConsole(String text) {
        String sanitizedText = stripAnsiEscapeSequences(text);
        Platform.runLater(() -> {
            consoleOutputArea.appendText(sanitizedText);
            consoleOutputArea.positionCaret(consoleOutputArea.getLength());
        });
    }

    private String stripAnsiEscapeSequences(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ANSI_ESCAPE_SEQUENCE.matcher(text).replaceAll("");
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

    private Node createServerRow(ServerInfo server) {
        HBox row = new HBox(14);
        row.setMinHeight(68);
        row.setPadding(new Insets(13, 16, 13, 16));
        row.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dce3ec; -fx-border-radius: 7; -fx-background-radius: 7;");

        VBox details = new VBox(4);
        Label name = new Label(server.getUser() + "@" + server.getHost());
        name.setStyle("-fx-text-fill: #111827; -fx-font-size: 14px; -fx-font-weight: 800;");
        Label address = new Label(server.getHost() + ":" + server.getPort());
        address.setStyle("-fx-text-fill: #697386; -fx-font-size: 12px;");
        details.getChildren().addAll(name, address);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Label type = new Label("SSH");
        type.setStyle("-fx-text-fill: #38a169; -fx-font-size: 12px; -fx-font-weight: 800;");
        row.getChildren().addAll(details, spacer, type);
        return row;
    }

    private void refreshServerList() {
        serverList.getChildren().clear();
        for (ServerInfo server : servers) {
            serverList.getChildren().add(createServerRow(server));
        }
        boolean empty = servers.isEmpty();
        emptyServerState.setVisible(empty);
        emptyServerState.setManaged(empty);
        serverList.setVisible(!empty);
        serverList.setManaged(!empty);
    }

    private void setActiveNavigation(boolean serversSelected) {
        workspacesNav.setStyle(serversSelected
                ? "-fx-cursor: hand;"
                : "-fx-background-color: #b35ff2; -fx-background-radius: 5; -fx-cursor: hand;");
        serversNav.setStyle(serversSelected
                ? "-fx-background-color: #b35ff2; -fx-background-radius: 5; -fx-cursor: hand;"
                : "-fx-cursor: hand;");
        for (Node child : workspacesNav.getChildren()) {
            if (child instanceof Label label) {
                label.setStyle(serversSelected
                        ? "-fx-text-fill: #535a67; -fx-font-size: 13px; -fx-font-weight: 500;"
                        : "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 500;");
            }
        }
        serversNavLabel.setStyle(serversSelected
                ? "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 500;"
                : "-fx-text-fill: #535a67; -fx-font-size: 13px;");
    }

    private void loadServers() {
        int count = serverPreferences.getInt("count", 0);
        for (int i = 0; i < count; i++) {
            String prefix = "server." + i + ".";
            String host = serverPreferences.get(prefix + "host", "");
            String user = serverPreferences.get(prefix + "user", "");
            int port = serverPreferences.getInt(prefix + "port", 22);
            String encodedPassword = serverPreferences.get(prefix + "password", "");
            if (!host.isEmpty() && !user.isEmpty()) {
                String password = decodePassword(encodedPassword);
                servers.add(new ServerInfo(user + "@" + host + ":" + port, host, port, user, password));
            }
        }
        refreshServerList();
    }

    private void persistServers() {
        try {
            serverPreferences.clear();
            serverPreferences.putInt("count", servers.size());
            for (int i = 0; i < servers.size(); i++) {
                ServerInfo server = servers.get(i);
                String prefix = "server." + i + ".";
                serverPreferences.put(prefix + "host", server.getHost());
                serverPreferences.putInt(prefix + "port", server.getPort());
                serverPreferences.put(prefix + "user", server.getUser());
                serverPreferences.put(prefix + "password", encodePassword(server.getPassword()));
            }
            serverPreferences.flush();
        } catch (Exception e) {
            showWarning("Could not save server", e.getMessage());
        }
    }

    private String encodePassword(String password) {
        return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    private String decodePassword(String encodedPassword) {
        try {
            return new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String getTrimmedText(TextField textField) {
        return textField.getText() == null ? "" : textField.getText().trim();
    }

    private void updateWorkspaceActionButtonStyles() {
        createWorkspaceButton.setStyle(isCreateWorkspaceInputComplete()
                ? CREATE_BUTTON_READY_STYLE
                : CREATE_BUTTON_PENDING_STYLE);
        editDevcontainerButton.setStyle(isEditDevcontainerInputComplete()
                ? EDIT_BUTTON_READY_STYLE
                : EDIT_BUTTON_PENDING_STYLE);
    }

    private boolean isCreateWorkspaceInputComplete() {
        return !getTrimmedText(workspaceNameField).isEmpty()
                && isEditDevcontainerInputComplete();
    }

    private boolean isEditDevcontainerInputComplete() {
        return sshServerComboBox.getValue() != null
                && !getTrimmedText(projectPathField).isEmpty()
                && !getTrimmedText(devcontainerPathField).isEmpty();
    }

    private WorkspaceInput readWorkspaceInput() {
        String workspaceName = getTrimmedText(workspaceNameField);
        String projectPath = getTrimmedText(projectPathField);
        String devcontainerPath = getTrimmedText(devcontainerPathField);

        if (!isCreateWorkspaceInputComplete()) {
            showWarning("Missing required fields",
                    "Workspace Name, SSH Server, Project Path, and Devcontainer Path are required.");
            return null;
        }
        ServerInfo server = sshServerComboBox.getValue();
        return new WorkspaceInput(workspaceName, server, projectPath, devcontainerPath);
    }

    private WorkspaceInput readEditDevcontainerInput() {
        String workspaceName = getTrimmedText(workspaceNameField);
        String projectPath = getTrimmedText(projectPathField);
        String devcontainerPath = getTrimmedText(devcontainerPathField);

        if (!isEditDevcontainerInputComplete()) {
            showWarning("Missing required fields",
                    "SSH Server, Project Path, and Devcontainer Path are required.");
            return null;
        }
        String serverName = workspaceName.isEmpty() ? "devcontainer-editor" : workspaceName;
        ServerInfo server = sshServerComboBox.getValue();
        return new WorkspaceInput(serverName, server, projectPath, devcontainerPath);
    }

    private String combineRemotePath(String projectPath, String devcontainerPath) {
        String normalizedProjectPath = projectPath.replaceAll("/+$", "");
        String normalizedDevcontainerPath = devcontainerPath.replaceAll("^/+", "");
        return normalizedProjectPath + "/" + normalizedDevcontainerPath;
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

    private record WorkspaceInput(
            String workspaceName,
            ServerInfo server,
            String projectPath,
            String devcontainerPath
    ) {
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
