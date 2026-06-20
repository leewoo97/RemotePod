package com.sshmanager;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * SSH 연결 및 명령 실행을 담당하는 서비스 클래스
 */
public class SshService {

    private SSHClient sshClient;
    private ServerInfo currentServer;

    /**
     * 서버에 SSH 접속
     */
    public void connect(ServerInfo server) throws IOException {
        // 이미 연결되어 있으면 먼저 끊기
        disconnect();

        sshClient = new SSHClient();
        sshClient.setConnectTimeout(300_000);
        sshClient.setTimeout(300_000);

        // 호스트 키 검증 비활성화 (실무에서는 known_hosts 사용 권장)
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());

        sshClient.connect(server.getHost(), server.getPort());
        sshClient.authPassword(server.getUser(), server.getPassword());

        this.currentServer = server;
    }

    /**
     * 명령어 실행 후 결과 반환
     */
    public String execute(String command) throws IOException {
        if (!isConnected()) {
            throw new IOException("서버에 연결되어 있지 않습니다.");
        }

        try (Session session = sshClient.startSession()) {
            Session.Command cmd = session.exec(command);

            // 표준 출력 읽기
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cmd.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 표준 에러 읽기
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(cmd.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            cmd.join(5, TimeUnit.SECONDS);

            // 에러가 있으면 에러도 포함해서 반환
            if (!errorOutput.isEmpty()) {
                return output + "\n[STDERR]\n" + errorOutput;
            }

            return output.toString();
        }
    }

    /**
     * SSH 연결 종료
     */
    public void disconnect() {
        if (sshClient != null && sshClient.isConnected()) {
            try {
                sshClient.disconnect();
            } catch (IOException e) {
                // 종료 시 예외는 무시
            }
        }
        sshClient = null;
        currentServer = null;
    }

    /**
     * 현재 연결 상태 확인
     */
    public boolean isConnected() {
        return sshClient != null && sshClient.isConnected() && sshClient.isAuthenticated();
    }

    public ServerInfo getCurrentServer() {
        return currentServer;
    }
}
