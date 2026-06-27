package com.sshmanager;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sshmanager.dto.ContainerGetDto;
import com.sshmanager.dto.ContainerInfoDto;
import com.sshmanager.dto.DevpodListDto;
import com.sshmanager.dto.DockerInspectDto;
import com.sshmanager.dto.RemotePodInfoDto;
import com.sshmanager.dto.WorkspaceResponseDto;

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
    @FXML private Node createSection;
    @FXML private HBox workspacesNav;
    @FXML private HBox serversNav;
    @FXML private Label serversNavLabel;
    @FXML private Button headerCreateButton;
    @FXML private Button cancelWorkspaceButton;
    @FXML private Node newServerModal;
    @FXML private Node workspaceActionModal;

    private Node createView;
    private Node workspaceDetailView;
    private VBox workspaceDetailContent;
    private Node emptyWorkspaceState;
    private Node workspaceLoadingState;
    private Node workspaceListScrollPane;
    private Node emptyServerState;
    private VBox workspaceList;
    private VBox serverList;
    private ComboBox<String> workspaceServerFilterBox;
    private ComboBox<String> workspaceStatusFilterBox;
    private TextField workspaceNameField;
    private ComboBox<ServerInfo> sshServerComboBox;
    private TextField projectPathField;
    private TextField devcontainerPathField;
    private Button createWorkspaceButton;
    private Button editDevcontainerButton;
    private Node consoleView;
    private TextArea consoleOutputArea;
    private Node devcontainerEditorView;
    private Label devcontainerEditorPathLabel;
    private Label devcontainerEditorStatusLabel;
    private TextArea devcontainerEditorArea;
    private Button saveDevcontainerButton;
    private TextField newServerAddressField;
    private TextField newServerUserField;
    private PasswordField newServerPasswordField;
    private Label workspaceActionTitle;
    private Label workspaceActionMessage;
    private Button confirmWorkspaceActionButton;
    private Button cancelWorkspaceActionButton;

    //ssh 연결을 위한 서비스
    private final SshService sshService = new SshService();

    //Json문자열을 key-value로 변환하기위한 ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final ServersController serversController = new ServersController();
    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();
    private final List<WorkspaceResponseDto> loadedWorkspaces = new ArrayList<>();
    private final Preferences serverPreferences = Preferences.userNodeForPackage(DevPodController.class).node("servers");
    private Task<String> activeCreateTask;
    private ServerInfo activeEditorServer;
    private String activeEditorRemotePath;
    private long workspaceLoadGeneration;
    private int pendingWorkspaceLoads;
    private WorkspaceResponseDto editingWorkspace;
    private WorkspaceResponseDto pendingWorkspaceAction;
    private WorkspaceInput pendingUpdateInput;
    private WorkspaceAction pendingAction;

    @FXML
    private void initialize() {
        bindIncludedViewControls();
        loadSvgIcons(workspaceView);
        loadSvgIcons(createSection);
        loadSvgIcons(consoleView);
        loadSvgIcons(devcontainerEditorView);
        sshServerComboBox.setItems(servers);
        loadServers();
        updateWorkspaceServerFilterOptions();
        workspaceNameField.textProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        sshServerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        projectPathField.textProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        devcontainerPathField.textProperty().addListener((observable, oldValue, newValue) -> updateWorkspaceActionButtonStyles());
        updateWorkspaceActionButtonStyles();
        loadWorkspacesFromSavedServers();
    }

    private void bindIncludedViewControls() {
        emptyWorkspaceState = lookupRequired(workspaceView, "#emptyWorkspaceState", Node.class);
        workspaceLoadingState = lookupRequired(workspaceView, "#workspaceLoadingState", Node.class);
        workspaceListScrollPane = lookupRequired(workspaceView, "#workspaceListScrollPane", Node.class);
        workspaceList = lookupRequired(workspaceView, "#workspaceList", VBox.class);
        workspaceServerFilterBox = lookupRequired(workspaceView, "#workspaceServerFilterBox", ComboBox.class);
        workspaceStatusFilterBox = lookupRequired(workspaceView, "#workspaceStatusFilterBox", ComboBox.class);
        Button emptyCreateWorkspaceButton =
                lookupRequired(workspaceView, "#emptyCreateWorkspaceButton", Button.class);

        emptyServerState = lookupRequired(serversView, "#emptyServerState", Node.class);
        serverList = lookupRequired(serversView, "#serverList", VBox.class);
        Button serversNewServerButton =
                lookupRequired(serversView, "#serversNewServerButton", Button.class);

        createView = lookupRequired(createSection, "#createView", Node.class);
        workspaceDetailView = lookupRequired(createSection, "#workspaceDetailView", Node.class);
        workspaceDetailContent = lookupRequired(createSection, "#workspaceDetailContent", VBox.class);
        workspaceNameField = lookupRequired(createSection, "#workspaceNameField", TextField.class);
        sshServerComboBox = lookupRequired(createSection, "#sshServerComboBox", ComboBox.class);
        projectPathField = lookupRequired(createSection, "#projectPathField", TextField.class);
        devcontainerPathField = lookupRequired(createSection, "#devcontainerPathField", TextField.class);
        createWorkspaceButton = lookupRequired(createSection, "#createWorkspaceButton", Button.class);
        editDevcontainerButton = lookupRequired(createSection, "#editDevcontainerButton", Button.class);
        Button createNewServerButton =
                lookupRequired(createSection, "#createNewServerButton", Button.class);
        consoleView = lookupRequired(createSection, "#consoleView", Node.class);
        consoleOutputArea = lookupRequired(createSection, "#consoleOutputArea", TextArea.class);
        devcontainerEditorView = lookupRequired(createSection, "#devcontainerEditorView", Node.class);
        devcontainerEditorPathLabel =
                lookupRequired(createSection, "#devcontainerEditorPathLabel", Label.class);
        devcontainerEditorStatusLabel =
                lookupRequired(createSection, "#devcontainerEditorStatusLabel", Label.class);
        devcontainerEditorArea =
                lookupRequired(createSection, "#devcontainerEditorArea", TextArea.class);
        saveDevcontainerButton =
                lookupRequired(createSection, "#saveDevcontainerButton", Button.class);

        newServerAddressField =
                lookupRequired(newServerModal, "#newServerAddressField", TextField.class);
        newServerUserField =
                lookupRequired(newServerModal, "#newServerUserField", TextField.class);
        newServerPasswordField =
                lookupRequired(newServerModal, "#newServerPasswordField", PasswordField.class);
        Button closeNewServerModalButton =
                lookupRequired(newServerModal, "#closeNewServerModalButton", Button.class);
        Button cancelNewServerButton =
                lookupRequired(newServerModal, "#cancelNewServerButton", Button.class);
        Button saveNewServerButton =
                lookupRequired(newServerModal, "#saveNewServerButton", Button.class);
        workspaceActionTitle =
                lookupRequired(workspaceActionModal, "#workspaceActionTitle", Label.class);
        workspaceActionMessage =
                lookupRequired(workspaceActionModal, "#workspaceActionMessage", Label.class);
        confirmWorkspaceActionButton =
                lookupRequired(workspaceActionModal, "#confirmWorkspaceActionButton", Button.class);
        Button closeWorkspaceActionButton =
                lookupRequired(workspaceActionModal, "#closeWorkspaceActionButton", Button.class);
        cancelWorkspaceActionButton =
                lookupRequired(workspaceActionModal, "#cancelWorkspaceActionButton", Button.class);

        emptyCreateWorkspaceButton.setOnAction(event -> showCreateWorkspace());
        workspaceServerFilterBox.setOnAction(event -> applyWorkspaceFilters());
        workspaceStatusFilterBox.setItems(FXCollections.observableArrayList(
                "All", "Running", "Exited", "Created", "Restarting", "Removing", "Paused", "Dead"
        ));
        workspaceStatusFilterBox.getSelectionModel().select("All");
        workspaceStatusFilterBox.setOnAction(event -> applyWorkspaceFilters());
        serversNewServerButton.setOnAction(event -> showNewServerModal());
        createNewServerButton.setOnAction(event -> showNewServerModal());
        createWorkspaceButton.setOnAction(event -> createWorkspace());
        editDevcontainerButton.setOnAction(event -> editDevcontainer());
        saveDevcontainerButton.setOnAction(event -> saveDevcontainer());
        closeNewServerModalButton.setOnAction(event -> hideNewServerModal());
        cancelNewServerButton.setOnAction(event -> hideNewServerModal());
        saveNewServerButton.setOnAction(event -> saveNewServer());
        closeWorkspaceActionButton.setOnAction(event -> hideWorkspaceActionModal());
        cancelWorkspaceActionButton.setOnAction(event -> hideWorkspaceActionModal());
        confirmWorkspaceActionButton.setOnAction(event -> confirmWorkspaceAction());
    }

    private <T extends Node> T lookupRequired(Node root, String selector, Class<T> type) {
        String id = selector.startsWith("#") ? selector.substring(1) : selector;
        Node node = findNodeById(root, id);
        if (node == null) {
            throw new IllegalStateException("Missing FXML control: " + selector);
        }
        return type.cast(node);
    }

    private Node findNodeById(Node node, String id) {
        if (id.equals(node.getId())) {
            return node;
        }

        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            Node match = findNodeById(scrollPane.getContent(), id);
            if (match != null) {
                return match;
            }
        }

        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node match = findNodeById(child, id);
                if (match != null) {
                    return match;
                }
            }
        }

        return null;
    }

    @FXML
    private void goBack() {
        if (devcontainerEditorView.isVisible()) {
            sshService.disconnect();
            activeEditorServer = null;
            activeEditorRemotePath = null;
            returnToWorkspaceForm();
            return;
        }

        showWorkspaces();
    }

    @FXML
    private void showCreateWorkspace() {
        workspaceLoadGeneration++;
        serversController.disconnectAll();
        editingWorkspace = null;
        createWorkspaceButton.setText("Create Workspace");
        createWorkspaceButton.setOnAction(event -> createWorkspace());
        titleLabel.setText("Create Workspace");
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
        createSection.setVisible(true);
        createSection.setManaged(true);
        createView.setVisible(true);
        createView.setManaged(true);
        workspaceDetailView.setVisible(false);
        workspaceDetailView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);
    }

    @FXML
    public void showWorkspaces() {
        workspaceLoadGeneration++;
        serversController.disconnectAll();
        titleLabel.setText("Workspaces");
        backButton.setVisible(false);
        backButton.setManaged(false);
        headerCreateButton.setVisible(true);
        headerCreateButton.setManaged(true);
        cancelWorkspaceButton.setVisible(false);
        cancelWorkspaceButton.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        workspaceDetailView.setVisible(false);
        workspaceDetailView.setManaged(false);
        createSection.setVisible(false);
        createSection.setManaged(false);
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
        loadWorkspacesFromSavedServers();
    }

    @FXML
    private void showServers() {
        workspaceLoadGeneration++;
        serversController.disconnectAll();
        titleLabel.setText("Servers");
        backButton.setVisible(false);
        backButton.setManaged(false);
        headerCreateButton.setVisible(false);
        headerCreateButton.setManaged(false);
        cancelWorkspaceButton.setVisible(false);
        cancelWorkspaceButton.setManaged(false);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        createSection.setVisible(false);
        createSection.setManaged(false);
        createView.setVisible(false);
        createView.setManaged(false);
        workspaceDetailView.setVisible(false);
        workspaceDetailView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);
        serversView.setVisible(true);
        serversView.setManaged(true);
        setActiveNavigation(true);
    }

    private void loadWorkspacesFromSavedServers() {
        long generation = ++workspaceLoadGeneration;
        loadedWorkspaces.clear();
        workspaceList.getChildren().clear();
        pendingWorkspaceLoads = servers.size();
        updateWorkspaceServerFilterOptions();
        updateWorkspaceListVisibility();

        if (servers.isEmpty()) {
            return;
        }

        serversController.connectAll(servers, new ServersController.ConnectionListener() {
            @Override
            public void onConnected(ServerInfo server, SshService connection) {
                Task<List<WorkspaceResponseDto>> task = new Task<>() {
                    @Override
                    protected List<WorkspaceResponseDto> call() throws Exception {
                        return handleServerConnected(server, connection);
                    }

                    @Override
                    protected void succeeded() {
                        if (generation != workspaceLoadGeneration) {
                            return;
                        }
                        loadedWorkspaces.addAll(getValue());
                        applyWorkspaceFilters();
                        completeWorkspaceLoad();
                    }

                    @Override
                    protected void failed() {
                        if (generation != workspaceLoadGeneration) {
                            return;
                        }
                        handleServerConnectionFailed(server, getException());
                        completeWorkspaceLoad();
                    }
                };

                Thread thread = new Thread(task, "workspace-list-" + server.getHost());
                thread.setDaemon(true);
                thread.start();
            }

            @Override
            public void onFailed(ServerInfo server, Exception exception) {
                if (generation != workspaceLoadGeneration) {
                    return;
                }
                handleServerConnectionFailed(server, exception);
                completeWorkspaceLoad();
            }
        });
    }

    private void completeWorkspaceLoad() {
        pendingWorkspaceLoads = Math.max(0, pendingWorkspaceLoads - 1);
        updateWorkspaceListVisibility();
    }

    private void updateWorkspaceListVisibility() {
        boolean hasWorkspaces = !workspaceList.getChildren().isEmpty();
        boolean showEmptyState = pendingWorkspaceLoads == 0 && !hasWorkspaces;
        emptyWorkspaceState.setVisible(showEmptyState);
        emptyWorkspaceState.setManaged(showEmptyState);
        workspaceListScrollPane.setVisible(hasWorkspaces);
        workspaceListScrollPane.setManaged(hasWorkspaces);
        workspaceList.setVisible(hasWorkspaces);
        workspaceList.setManaged(hasWorkspaces);
        workspaceLoadingState.setVisible(pendingWorkspaceLoads > 0);
        workspaceLoadingState.setManaged(pendingWorkspaceLoads > 0);
    }

    private void updateWorkspaceServerFilterOptions() {
        if (workspaceServerFilterBox == null) {
            return;
        }

        String selectedServer = workspaceServerFilterBox.getValue();
        List<String> serverOptions = new ArrayList<>();
        serverOptions.add("All");
        for (ServerInfo server : servers) {
            String serverInfo = server.getInfo();
            if (!serverOptions.contains(serverInfo)) {
                serverOptions.add(serverInfo);
            }
        }

        workspaceServerFilterBox.setItems(FXCollections.observableArrayList(serverOptions));
        if (selectedServer != null && serverOptions.contains(selectedServer)) {
            workspaceServerFilterBox.getSelectionModel().select(selectedServer);
        } else {
            workspaceServerFilterBox.getSelectionModel().select("All");
        }
    }

    private void applyWorkspaceFilters() {
        if (workspaceList == null) {
            return;
        }

        String selectedServer = workspaceServerFilterBox == null ? "All" : workspaceServerFilterBox.getValue();
        String selectedStatus = workspaceStatusFilterBox == null ? "All" : workspaceStatusFilterBox.getValue();
        workspaceList.getChildren().clear();

        for (WorkspaceResponseDto workspace : loadedWorkspaces) {
            boolean serverMatches = selectedServer == null
                    || "All".equals(selectedServer)
                    || selectedServer.equals(workspace.getServerInfo());
            boolean statusMatches = selectedStatus == null
                    || "All".equals(selectedStatus)
                    || normalizeStatus(selectedStatus).equals(normalizeStatus(workspace.getStatus()));

            if (serverMatches && statusMatches) {
                workspaceList.getChildren().add(createWorkspaceCard(workspace));
            }
        }

        updateWorkspaceListVisibility();
    }

    /**
     * SSH connection is ready here.
     * Add the devpod list command and result rendering in this method.
     * @throws IOException
     */
    private List<WorkspaceResponseDto> handleServerConnected(ServerInfo server, SshService connection) throws IOException {
        List<WorkspaceResponseDto> responseList = new ArrayList<>();

        String json = connection.executeCheckedJson("docker ps -a --filter \"label=devcontainer.metadata\" --format json", 30);
        // List<ContainerGetDto> devpodList =
        //         objectMapper.readValue(json, new TypeReference<List<ContainerGetDto>>() {});

        List<ContainerGetDto> devpodList = Arrays.stream(json.split("\n"))
            .filter(line -> !line.isBlank())
            .map(line -> {
                try {
                    return objectMapper.readValue(line, ContainerGetDto.class);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());

        for (ContainerGetDto devpod : devpodList) {
            String dockerCommand = "docker inspect " + shellQuote(devpod.getId());
            String dockerJson = connection.executeCheckedJson(dockerCommand, 30);
            List<DockerInspectDto> dockerInspectList = objectMapper.readValue(
                    dockerJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DockerInspectDto.class)
            );
            if (dockerInspectList.isEmpty()) {
                continue;
            }

            DockerInspectDto dockerInspect = dockerInspectList.getFirst();
            RemotePodInfoDto remotePodInfo = readRemotePodInfo(connection, dockerInspect.getWorkspaceName());
            responseList.add(WorkspaceResponseDto.builder()
                    .workspaceName(dockerInspect.getWorkspaceName())
                    .created(dockerInspect.getCreated())
                    .gateway(dockerInspect.getGateway())
                    .ipAddress(dockerInspect.getIpAddress())
                    .mountsInfo(dockerInspect.getMountsInfo())
                    .pid(dockerInspect.getPid())
                    .portInfo(dockerInspect.getPortInfo())
                    .status(dockerInspect.getStatus())
                    .serverInfo(server.getInfo())
                    .projectPath(remotePodInfo.getProjectPath())
                    .devcontainerPath(remotePodInfo.getDevcontainerPath())
                    .build());
        }

        return responseList;
    }

    private RemotePodInfoDto readRemotePodInfo(SshService connection, String containerName) {
        try {
            String command = "docker cp "
                    + shellQuote(containerName + ":/root/.remote-pod/info.json")
                    + " - | tar -xO";
            String infoJson = connection.executeCheckedJson(command, 30);
            if (infoJson == null || infoJson.isBlank()) {
                return new RemotePodInfoDto();
            }
            return objectMapper.readValue(infoJson, RemotePodInfoDto.class);
        } catch (Exception exception) {
            System.err.println("Could not read remote pod info for " + containerName + ": "
                    + exception.getMessage());
            return new RemotePodInfoDto();
        }
    }

    private void handleServerConnectionFailed(ServerInfo server, Throwable exception) {
        System.err.println("Workspace load failed for " + server.getInfo() + ": "
                + (exception == null ? "Unknown error" : exception.getMessage()));
    }

    @FXML
    private void updateWorkspace() {
        if (editingWorkspace == null || editingWorkspace.getWorkspaceName() == null
                || editingWorkspace.getWorkspaceName().isBlank()) {
            showWarning("Update Workspace failed", "Original workspace information is missing.");
            return;
        }

        WorkspaceInput input = readWorkspaceInput();
        if (input == null) {
            return;
        }

        showUpdateWorkspaceModal(input);
    }

    private void performUpdateWorkspace(WorkspaceInput input) {
        String originalWorkspaceName = editingWorkspace.getWorkspaceName();
        ServerInfo originalServer = findServerByInfo(editingWorkspace.getServerInfo());
        if (originalServer == null) {
            showWarning("Update Workspace failed", "Could not find the original SSH server.");
            return;
        }

        try {
            deleteDevpodMetadata(input.server());
            if (!originalWorkspaceName.equals(input.workspaceName())) {
                isExistedName(input.workspaceName(), input.server());
            }
            removeWorkspaceContainer(originalWorkspaceName, originalServer);

            String command = "devpod up "
                    + shellQuote(input.projectPath())
                    + " --devcontainer-path "
                    + shellQuote(input.devcontainerPath())
                    + " --id "
                    + shellQuote(input.workspaceName())
                    + " --ide none";
            runCreateWorkspaceTask(input, command);
        } catch (IllegalArgumentException e) {
            showWarning("Update Workspace failed", e.getMessage());
        }
    }

    private ServerInfo findServerByInfo(String serverInfo) {
        if (serverInfo == null) {
            return null;
        }
        for (ServerInfo server : servers) {
            if (serverInfo.equals(server.getInfo())) {
                return server;
            }
        }
        return null;
    }

    private void removeWorkspaceContainer(String workspaceName, ServerInfo server) {
        try {
            sshService.connect(server);
            sshService.execute("docker stop " + shellQuote(workspaceName), 60);
            sshService.executeChecked("docker rm " + shellQuote(workspaceName), 60);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to remove existing workspace: " + e.getMessage(), e);
        } finally {
            sshService.disconnect();
        }
    }

    private void restartWorkspace(WorkspaceResponseDto workspace) {
        if (workspace.getProjectPath() == null || workspace.getProjectPath().isBlank()
                || workspace.getDevcontainerPath() == null || workspace.getDevcontainerPath().isBlank()) {
            showWorkspaceNoticeModal(
                    "Cannot Restart Workspace",
                    "Project Path or Devcontainer Path is missing. Use Edit to configure the workspace first."
            );
            return;
        }

        ServerInfo server = findServerByInfo(workspace.getServerInfo());
        if (server == null) {
            showWorkspaceNoticeModal(
                    "Cannot Restart Workspace",
                    "Could not find the SSH server for this workspace."
            );
            return;
        }

        WorkspaceInput input = new WorkspaceInput(
                workspace.getWorkspaceName(),
                server,
                workspace.getProjectPath(),
                workspace.getDevcontainerPath()
        );
        try {
            deleteDevpodMetadata(server);
            removeWorkspaceContainer(workspace.getWorkspaceName(), server);
            String command = "devpod up "
                    + shellQuote(input.projectPath())
                    + " --devcontainer-path "
                    + shellQuote(input.devcontainerPath())
                    + " --id "
                    + shellQuote(input.workspaceName())
                    + " --ide none";
            runCreateWorkspaceTask(input, command);
        } catch (IllegalArgumentException e) {
            showWarning("Restart Workspace failed", e.getMessage());
        }
    }

    private void deleteWorkspace(WorkspaceResponseDto workspace) {
        ServerInfo server = findServerByInfo(workspace.getServerInfo());
        if (server == null) {
            showWorkspaceNoticeModal(
                    "Cannot Delete Workspace",
                    "Could not find the SSH server for this workspace."
            );
            return;
        }

        try {
            deleteDevpodMetadata(server);
            removeWorkspaceContainer(workspace.getWorkspaceName(), server);
            showWorkspaces();
        } catch (IllegalArgumentException e) {
            showWarning("Delete Workspace failed", e.getMessage());
        }
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
        updateWorkspaceServerFilterOptions();
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
        try {
            WorkspaceInput input = readWorkspaceInput();
            if (input == null) {
                return;
            }

            //devpod Metadata삭제
            deleteDevpodMetadata(input.server());

            //이름 검증
            //docker ps 명령을 입력했을때 해당 이름이 이미 존재한다면 예외처리
            isExistedName(input.workspaceName(), input.server());

            //데브 컨테이너 생성
            String command = "devpod up "
                    + shellQuote(input.projectPath())
                    + " --devcontainer-path "
                    + shellQuote(input.devcontainerPath())
                    + " --id "
                    + shellQuote(input.workspaceName())
                    + " --ide none";

            runCreateWorkspaceTask(input, command);


            //데브 컨테이너 생성 성공 여부 확인

            //devpod Metadata삭제

        } catch (IllegalArgumentException e) {
            showWarning("Create Workspace failed", e.getMessage());
        }
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
                returnToWorkspaceForm();
            }
        };

        Thread thread = new Thread(task, "devcontainer-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void returnToWorkspaceForm() {
        if (editingWorkspace != null) {
            showEditWorkspace(editingWorkspace);
        } else {
            showCreateWorkspace();
        }
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

    private void deleteDevpodMetadata(ServerInfo server) {
        try {
            sshService.connect(server);
            String command = "rm -rf ~/.devpod/contexts/default/workspaces/*";
            sshService.executeChecked(command, 30);
        } catch (Exception e) {
            System.err.println("Failed to delete devpod metadata on server " + server.getInfo() + ": " + e.getMessage());
        } finally {
            sshService.disconnect();
        }
    }

    private void deleteDevpodMetadata() throws IOException {
        String command = "rm -rf ~/.devpod/contexts/default/workspaces/*";
        sshService.executeChecked(command, 30);
    }

    private void isExistedName(String workspaceName, ServerInfo server) {
        //이미 해당 이름을 가진 컨테이너가 존재하는지 확인하기 위해 docker ps 명령어를 사용
        String command = "docker ps -a --filter \"name=^" + workspaceName + "$\" --format json";
        try {
            sshService.connect(server);
            String result = sshService.executeChecked(command, 30);
            if (result != null && !result.trim().isEmpty()) {
                throw new IllegalArgumentException("Workspace name already exists on the server.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to check existing workspace names: " + e.getMessage(), e);
        } finally {
            sshService.disconnect();
        }
    }

    private void runCreateWorkspaceTask(WorkspaceInput input, String command) {
        String workspaceName = input.workspaceName();
        ServerInfo server = input.server();
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
                String output = sshService.executeCheckedStreaming(command, 3600, DevPodController.this::appendConsole);
                appendConsole("\nVerifying workspace...\n");
                isSuccessfullyCreated(workspaceName);
                appendConsole("Writing remote pod info...\n");
                writeRemotePodInfo(input);
                appendConsole("Cleaning DevPod metadata...\n");
                deleteDevpodMetadata();
                return output;
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

    private void isSuccessfullyCreated(String workspaceName) {

        try {
            String json = sshService.executeCheckedJson("devpod list --output json", 30);
            System.out.println("1");
            DevpodListDto devpodInfo =
                    objectMapper.readValue(json, new TypeReference<List<DevpodListDto>>() {}).get(0);
            System.out.println("2");
            String containerJson = sshService.executeCheckedJson(
                    "docker ps -a --filter "
                            + shellQuote("label=dev.containers.id=" + devpodInfo.getUid())
                            + " --format json",
                    30
            );
            System.out.println("3");
            System.out.println("docker ps -a --filter "
                        + shellQuote("label=dev.containers.id=" + devpodInfo.getUid())
                        + " --format json");
            System.out.println("containerJson: " + containerJson);
            ContainerInfoDto containerInfoDto =
                    objectMapper.readValue(containerJson, ContainerInfoDto.class);

            System.out.println("Container Info: " + containerInfoDto.toString());

            if (containerInfoDto != null &&(devpodInfo.getUid().equals(containerInfoDto.getUid()))) {
                if (workspaceName.equals(containerInfoDto.getNames())) {
                    appendConsole("Container already has the target name. Skipping rename.\n");
                } else {
                    try {
                        sshService.executeChecked(
                                "docker rename " + shellQuote(containerInfoDto.getId()) + " " + shellQuote(workspaceName),
                                30
                        );
                    } catch (IOException exception) {
                        if (!isSameContainerNameRenameError(exception)) {
                            throw exception;
                        }
                        appendConsole("Container already has the target name. Skipping rename.\n");
                    }
                }
            } else {
                throw new IllegalArgumentException("Workspace creation failed: container not found.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to verify workspace creation: " + e.getMessage(), e);
        }
    }

    private boolean isSameContainerNameRenameError(IOException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("Renaming a container with the same name as its current name");
    }

    private void writeRemotePodInfo(WorkspaceInput input) throws IOException {
        var info = objectMapper.createObjectNode();
        info.put("workspaceName", input.workspaceName());
        info.put("sshServerInfo", input.server().getInfo());
        info.put("projectPath", input.projectPath());
        info.put("devcontainerPath", input.devcontainerPath());

        String json = objectMapper.writeValueAsString(info);
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String script = "mkdir -p ~/.remote-pod && printf %s "
                + shellQuote(encodedJson)
                + " | base64 -d > ~/.remote-pod/info.json";
        String command = "docker exec "
                + shellQuote(input.workspaceName())
                + " bash -lc "
                + shellQuote(script);

        sshService.executeChecked(command, 30);
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
        createSection.setVisible(true);
        createSection.setManaged(true);
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
        createSection.setVisible(true);
        createSection.setManaged(true);
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

        Label status = new Label(formatStatus(statusText));
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px; -fx-font-weight: 700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        row.getChildren().addAll(name, type, spacer, status);
        return row;
    }

    private Node createWorkspaceCard(WorkspaceResponseDto workspace) {
        HBox card = new HBox(18);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMinHeight(164);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dce3ec;"
                + " -fx-border-radius: 8; -fx-background-radius: 8;");

        VBox details = new VBox(7);
        details.setPadding(new Insets(8, 0, 0, 0));
        String statusText = normalizeStatus(workspace.getStatus());
        Circle statusDot = new Circle(6, statusColor(statusText));
        Label statusLabel = new Label(formatStatus(statusText));
        statusLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 14px; -fx-font-weight: 800;");
        HBox status = new HBox(8, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_LEFT);

        Label workspaceName = new Label(displayValue(workspace.getWorkspaceName()));
        workspaceName.setStyle("-fx-text-fill: #111827; -fx-font-size: 16px; -fx-font-weight: 800;");

        Label serverInfo = createWorkspaceDetailLabel("Server", workspace.getServerInfo());
        Label path = createWorkspaceDetailLabel("Path", workspace.getProjectPath());
        Label portInfo = createWorkspaceDetailLabel("Port", workspace.getPortInfo());
        details.getChildren().addAll(status, workspaceName, serverInfo, path, portInfo);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button detailsButton = new Button("Show Details");
        detailsButton.setFocusTraversable(false);
        detailsButton.setStyle("-fx-background-color: #a855f7; -fx-background-radius: 6;"
                + " -fx-padding: 8 13; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 700;");
        detailsButton.setOnAction(event -> showWorkspaceDetails(workspace));

        Button editButton = new Button("Edit");
        editButton.setFocusTraversable(false);
        editButton.setStyle("-fx-background-color: #38a169; -fx-background-radius: 6;"
                + " -fx-padding: 8 18; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 700;");
        editButton.setOnAction(event -> showEditWorkspace(workspace));

        HBox actions = new HBox(9, detailsButton, editButton);
        actions.setAlignment(Pos.BOTTOM_RIGHT);

        Button moreButton = new Button("\u22EE");
        moreButton.setFocusTraversable(false);
        moreButton.setMinSize(26, 26);
        moreButton.setPrefSize(26, 26);
        moreButton.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;"
                + " -fx-text-fill: #4b5563; -fx-font-size: 19px; -fx-font-weight: 800;"
                + " -fx-padding: 0; -fx-cursor: hand;");

        Button restartButton = createWorkspaceMenuButton("Restart", false);
        restartButton.setOnAction(event -> showWorkspaceActionModal(workspace, WorkspaceAction.RESTART));
        Button deleteButton = createWorkspaceMenuButton("Delete", true);
        deleteButton.setOnAction(event -> showWorkspaceActionModal(workspace, WorkspaceAction.DELETE));
        VBox popupMenu = new VBox(2, restartButton, deleteButton);
        popupMenu.setVisible(false);
        popupMenu.setManaged(false);
        popupMenu.setMinWidth(92);
        popupMenu.setPrefWidth(92);
        popupMenu.setMaxWidth(92);
        popupMenu.setPadding(new Insets(5));
        popupMenu.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 6;"
                + " -fx-border-color: #dce3ec; -fx-border-radius: 6;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.16), 12, 0.1, 0, 4);");
        moreButton.setOnAction(event -> {
            boolean show = !popupMenu.isVisible();
            popupMenu.setVisible(show);
            popupMenu.setManaged(show);
            moreButton.setStyle("-fx-background-color: " + (show ? "#eef2f6" : "transparent")
                    + "; -fx-background-radius: 6; -fx-text-fill: #4b5563;"
                    + " -fx-font-size: 19px; -fx-font-weight: 800;"
                    + " -fx-padding: 0; -fx-cursor: hand;");
            card.requestLayout();
        });

        HBox menuRow = new HBox(5, popupMenu, moreButton);
        menuRow.setAlignment(Pos.TOP_RIGHT);
        Region rightSpacer = new Region();
        VBox.setVgrow(rightSpacer, javafx.scene.layout.Priority.ALWAYS);
        VBox right = new VBox(menuRow, rightSpacer, actions);
        right.setAlignment(Pos.TOP_RIGHT);
        right.setMinWidth(190);
        right.setMinHeight(128);
        right.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(right, javafx.scene.layout.Priority.NEVER);

        card.getChildren().addAll(details, spacer, right);
        return card;
    }

    private Button createWorkspaceMenuButton(String text, boolean destructive) {
        Button button = new Button(text);
        button.setFocusTraversable(false);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle("-fx-background-color: transparent; -fx-background-radius: 4;"
                + " -fx-padding: 5 8; -fx-text-fill: "
                + (destructive ? "#dc2626" : "#374151")
                + "; -fx-font-size: 11px; -fx-font-weight: 600; -fx-cursor: hand;");
        return button;
    }

    private void showWorkspaceActionModal(WorkspaceResponseDto workspace, WorkspaceAction action) {
        pendingWorkspaceAction = workspace;
        pendingUpdateInput = null;
        pendingAction = action;
        boolean deleting = action == WorkspaceAction.DELETE;
        workspaceActionTitle.setText(deleting ? "Delete Workspace" : "Restart Workspace");
        workspaceActionMessage.setText(deleting
                ? "Delete " + displayValue(workspace.getWorkspaceName()) + "? This action cannot be undone."
                : "Restart " + displayValue(workspace.getWorkspaceName())
                        + "? The workspace will be temporarily unavailable.");
        confirmWorkspaceActionButton.setText(deleting ? "Delete" : "Restart");
        confirmWorkspaceActionButton.setStyle("-fx-background-color: "
                + (deleting ? "#dc2626" : "#38a169")
                + "; -fx-background-radius: 6; -fx-padding: 8 16; -fx-text-fill: white;"
                + " -fx-font-size: 13px; -fx-font-weight: 700;");
        cancelWorkspaceActionButton.setVisible(true);
        cancelWorkspaceActionButton.setManaged(true);
        workspaceActionModal.setVisible(true);
        workspaceActionModal.setManaged(true);
    }

    private void showUpdateWorkspaceModal(WorkspaceInput input) {
        pendingWorkspaceAction = null;
        pendingUpdateInput = input;
        pendingAction = WorkspaceAction.UPDATE;
        workspaceActionTitle.setText("Update Workspace");
        workspaceActionMessage.setText("The existing workspace will be completely deleted and rebuilt as a new workspace.");
        confirmWorkspaceActionButton.setText("Update");
        confirmWorkspaceActionButton.setStyle("-fx-background-color: #38a169; -fx-background-radius: 6;"
                + " -fx-padding: 8 16; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 700;");
        cancelWorkspaceActionButton.setVisible(true);
        cancelWorkspaceActionButton.setManaged(true);
        workspaceActionModal.setVisible(true);
        workspaceActionModal.setManaged(true);
    }

    private void showWorkspaceNoticeModal(String title, String message) {
        pendingWorkspaceAction = null;
        pendingUpdateInput = null;
        pendingAction = WorkspaceAction.NOTICE;
        workspaceActionTitle.setText(title);
        workspaceActionMessage.setText(message);
        confirmWorkspaceActionButton.setText("OK");
        confirmWorkspaceActionButton.setStyle("-fx-background-color: #38a169; -fx-background-radius: 6;"
                + " -fx-padding: 8 16; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 700;");
        cancelWorkspaceActionButton.setVisible(false);
        cancelWorkspaceActionButton.setManaged(false);
        workspaceActionModal.setVisible(true);
        workspaceActionModal.setManaged(true);
    }

    private void hideWorkspaceActionModal() {
        workspaceActionModal.setVisible(false);
        workspaceActionModal.setManaged(false);
        pendingWorkspaceAction = null;
        pendingUpdateInput = null;
        pendingAction = null;
    }

    private void confirmWorkspaceAction() {
        WorkspaceResponseDto workspace = pendingWorkspaceAction;
        WorkspaceInput updateInput = pendingUpdateInput;
        WorkspaceAction action = pendingAction;
        hideWorkspaceActionModal();
        if (action == null || action == WorkspaceAction.NOTICE) {
            return;
        }
        if (action == WorkspaceAction.UPDATE) {
            if (updateInput != null) {
                performUpdateWorkspace(updateInput);
            }
        } else if (action == WorkspaceAction.RESTART && workspace != null) {
            restartWorkspace(workspace);
        } else if (action == WorkspaceAction.DELETE && workspace != null) {
            deleteWorkspace(workspace);
        }
    }

    private void showWorkspaceDetails(WorkspaceResponseDto workspace) {
        workspaceLoadGeneration++;
        serversController.disconnectAll();
        titleLabel.setText("Workspace Details");
        backButton.setVisible(true);
        backButton.setManaged(true);
        headerCreateButton.setVisible(false);
        headerCreateButton.setManaged(false);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        serversView.setVisible(false);
        serversView.setManaged(false);
        createSection.setVisible(true);
        createSection.setManaged(true);
        createView.setVisible(false);
        createView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);
        workspaceDetailView.setVisible(true);
        workspaceDetailView.setManaged(true);

        workspaceDetailContent.getChildren().setAll(
                createWorkspaceDetailRow("Status", formatStatus(workspace.getStatus()), true),
                createWorkspaceDetailRow("Workspace Name", workspace.getWorkspaceName(), false),
                createWorkspaceDetailRow("Server", workspace.getServerInfo(), false),
                createWorkspaceDetailRow("Path", workspace.getProjectPath(), false),
                createWorkspaceDetailRow("Devcontainer Path", workspace.getDevcontainerPath(), false),
                createWorkspaceDetailRow("Port", workspace.getPortInfo(), false),
                createWorkspaceDetailRow("IP Address", workspace.getIpAddress(), false),
                createWorkspaceDetailRow("Gateway", workspace.getGateway(), false),
                createWorkspaceDetailRow("PID", workspace.getPid(), false),
                createWorkspaceDetailRow("Created", workspace.getCreated(), false),
                createWorkspaceDetailRow("Mounts", workspace.getMountsInfo(), false)
        );
    }

    private Node createWorkspaceDetailRow(String title, String value, boolean statusValue) {
        HBox row = new HBox(18);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(15, 18, 15, 18));
        row.setStyle("-fx-border-color: transparent transparent #e5e7eb transparent;"
                + " -fx-border-width: 0 0 1 0;");

        Label titleLabel = new Label(title);
        titleLabel.setMinWidth(145);
        titleLabel.setStyle("-fx-text-fill: #697386; -fx-font-size: 12px; -fx-font-weight: 700;");

        Node valueNode;
        if (statusValue) {
            String status = normalizeStatus(value);
            Circle dot = new Circle(6, statusColor(status));
            Label label = new Label(formatStatus(status));
            label.setStyle("-fx-text-fill: #111827; -fx-font-size: 14px; -fx-font-weight: 800;");
            HBox statusBox = new HBox(8, dot, label);
            statusBox.setAlignment(Pos.CENTER_LEFT);
            valueNode = statusBox;
        } else {
            Label label = new Label(displayValue(value));
            label.setWrapText(true);
            label.setStyle("-fx-text-fill: #111827; -fx-font-size: 13px;");
            HBox.setHgrow(label, javafx.scene.layout.Priority.ALWAYS);
            label.setMaxWidth(Double.MAX_VALUE);
            valueNode = label;
        }

        row.getChildren().addAll(titleLabel, valueNode);
        return row;
    }

    private void showEditWorkspace(WorkspaceResponseDto workspace) {
        workspaceLoadGeneration++;
        serversController.disconnectAll();
        editingWorkspace = workspace;
        titleLabel.setText("Edit Workspace");
        backButton.setVisible(true);
        backButton.setManaged(true);
        headerCreateButton.setVisible(false);
        headerCreateButton.setManaged(false);
        workspaceView.setVisible(false);
        workspaceView.setManaged(false);
        serversView.setVisible(false);
        serversView.setManaged(false);
        createSection.setVisible(true);
        createSection.setManaged(true);
        createView.setVisible(true);
        createView.setManaged(true);
        workspaceDetailView.setVisible(false);
        workspaceDetailView.setManaged(false);
        consoleView.setVisible(false);
        consoleView.setManaged(false);
        devcontainerEditorView.setVisible(false);
        devcontainerEditorView.setManaged(false);

        workspaceNameField.setText(workspace.getWorkspaceName() == null ? "" : workspace.getWorkspaceName());
        projectPathField.setText(workspace.getProjectPath() == null ? "" : workspace.getProjectPath());
        devcontainerPathField.setText(
                workspace.getDevcontainerPath() == null ? "" : workspace.getDevcontainerPath()
        );
        selectWorkspaceServer(workspace.getServerInfo());
        createWorkspaceButton.setText("Update Workspace");
        createWorkspaceButton.setOnAction(event -> updateWorkspace());
        updateWorkspaceActionButtonStyles();
    }

    private void selectWorkspaceServer(String serverInfo) {
        sshServerComboBox.getSelectionModel().clearSelection();
        if (serverInfo == null) {
            return;
        }
        for (ServerInfo server : servers) {
            if (serverInfo.equals(server.getInfo())) {
                sshServerComboBox.getSelectionModel().select(server);
                return;
            }
        }
    }

    private Label createWorkspaceDetailLabel(String title, String value) {
        Label label = new Label(title + "  " + displayValue(value));
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #697386; -fx-font-size: 12px;");
        return label;
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "unknown" : status.trim().toLowerCase();
    }

    private String formatStatus(String status) {
        String normalizedStatus = normalizeStatus(status);
        return normalizedStatus.substring(0, 1).toUpperCase() + normalizedStatus.substring(1);
    }

    private Color statusColor(String status) {
        return switch (status) {
            case "created" -> Color.web("#3b82f6");
            case "restarting" -> Color.web("#f59e0b");
            case "running" -> Color.web("#22c55e");
            case "removing" -> Color.web("#a855f7");
            case "paused" -> Color.web("#eab308");
            case "exited" -> Color.web("#94a3b8");
            case "dead" -> Color.web("#ef4444");
            default -> Color.web("#64748b");
        };
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
        showWorkspaceNoticeModal(title, message);
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

    private enum WorkspaceAction {
        UPDATE,
        RESTART,
        DELETE,
        NOTICE
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
