package com.sshmanager;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 메인 화면 컨트롤러
 * FXML과 연결되어 UI 이벤트를 처리
 */
public class MainController {

    // ── 서버 목록 영역 ──────────────────────────────
    @FXML private ListView<ServerInfo> serverListView;
    @FXML private TextField nameField;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField userField;
    @FXML private PasswordField passwordField;

    // ── 상태 표시 영역 ──────────────────────────────
    @FXML private Circle statusCircle;
    @FXML private Label statusLabel;

    // ── 명령 실행 영역 ──────────────────────────────
    @FXML private TextField commandField;
    @FXML private TextArea outputArea;

    // ── 즐겨찾기 명령어 ─────────────────────────────
    @FXML private ComboBox<String> quickCommandBox;

    private final SshService sshService = new SshService();
    private final ObservableList<ServerInfo> serverList = FXCollections.observableArrayList();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        // 서버 목록 초기화
        serverListView.setItems(serverList);

        // 기본 포트 설정
        portField.setText("22");

        // 즐겨찾기 명령어 세팅
        quickCommandBox.setItems(FXCollections.observableArrayList(
                "df -h",           // 디스크 사용량
                "free -h",         // 메모리 사용량
                "top -bn1",        // CPU/프로세스 현황
                "uptime",          // 서버 가동 시간
                "ps aux",          // 실행 중인 프로세스
                "netstat -tuln",   // 포트 현황
                "ls -la",          // 파일 목록
                "cat /etc/os-release" // OS 정보
        ));

        // 즐겨찾기 선택 시 명령어 필드에 자동 입력
        quickCommandBox.setOnAction(e -> {
            String selected = quickCommandBox.getValue();
            if (selected != null) {
                commandField.setText(selected);
            }
        });

        // 서버 목록 클릭 시 폼에 자동 채우기
        serverListView.setOnMouseClicked(e -> {
            ServerInfo selected = serverListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                nameField.setText(selected.getName());
                hostField.setText(selected.getHost());
                portField.setText(String.valueOf(selected.getPort()));
                userField.setText(selected.getUser());
                passwordField.setText(selected.getPassword());
            }
        });

        // 초기 상태: 연결 안 됨
        setStatus(false, "연결 안 됨");

        // 엔터키로 명령 실행
        commandField.setOnAction(e -> handleExecute());

        // 예시 서버 추가 (테스트용)
        serverList.add(new ServerInfo("내 서버", "192.168.0.1", 22, "ubuntu", "password123"));
    }

    /**
     * 서버 추가 버튼
     */
    @FXML
    private void handleAddServer() {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String user = userField.getText().trim();
        String password = passwordField.getText();

        if (name.isEmpty() || host.isEmpty() || user.isEmpty()) {
            showAlert("입력 오류", "이름, 호스트, 사용자명은 필수입니다.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showAlert("입력 오류", "포트 번호가 올바르지 않습니다.");
            return;
        }

        serverList.add(new ServerInfo(name, host, port, user, password));
        clearForm();
        appendOutput("서버 추가됨: " + name);
    }

    /**
     * 서버 삭제 버튼
     */
    @FXML
    private void handleRemoveServer() {
        ServerInfo selected = serverListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("선택 필요", "삭제할 서버를 선택하세요.");
            return;
        }
        serverList.remove(selected);
        appendOutput("서버 삭제됨: " + selected.getName());
    }

    /**
     * 접속 버튼
     */
    @FXML
    private void handleConnect() {
        ServerInfo selected = serverListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("선택 필요", "접속할 서버를 목록에서 선택하세요.");
            return;
        }

        setStatus(false, "접속 중...");
        appendOutput("▶ " + selected + " 접속 시도 중...");

        // 백그라운드 스레드에서 SSH 접속 (UI 블로킹 방지)
        Task<Void> connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                sshService.connect(selected);
                return null;
            }

            @Override
            protected void succeeded() {
                setStatus(true, "연결됨: " + selected.getName());
                appendOutput("✅ 접속 성공!\n");
            }

            @Override
            protected void failed() {
                setStatus(false, "연결 실패");
                appendOutput("❌ 접속 실패: " + getException().getMessage() + "\n");
            }
        };

        new Thread(connectTask).start();
    }

    /**
     * 접속 해제 버튼
     */
    @FXML
    private void handleDisconnect() {
        sshService.disconnect();
        setStatus(false, "연결 안 됨");
        appendOutput("🔌 접속 해제됨\n");
    }

    /**
     * 명령어 실행 버튼
     */
    @FXML
    private void handleExecute() {
        if (!sshService.isConnected()) {
            showAlert("연결 필요", "먼저 서버에 접속하세요.");
            return;
        }

        String command = commandField.getText().trim();
        if (command.isEmpty()) {
            return;
        }

        appendOutput("$ " + command);

        // 백그라운드 스레드에서 명령 실행
        Task<String> executeTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return sshService.execute(command);
            }

            @Override
            protected void succeeded() {
                appendOutput(getValue());
                commandField.clear();
            }

            @Override
            protected void failed() {
                appendOutput("❌ 실행 실패: " + getException().getMessage() + "\n");
            }
        };

        new Thread(executeTask).start();
    }

    /**
     * 출력 영역 초기화
     */
    @FXML
    private void handleClearOutput() {
        outputArea.clear();
    }

    // ── 유틸리티 메서드 ──────────────────────────────

    private void setStatus(boolean connected, String message) {
        Platform.runLater(() -> {
            statusCircle.setFill(connected ? Color.LIMEGREEN : Color.RED);
            statusLabel.setText(message);
        });
    }

    private void appendOutput(String text) {
        Platform.runLater(() -> {
            String time = LocalDateTime.now().format(TIME_FORMAT);
            outputArea.appendText("[" + time + "] " + text + "\n");
        });
    }

    private void clearForm() {
        nameField.clear();
        hostField.clear();
        portField.setText("22");
        userField.clear();
        passwordField.clear();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
