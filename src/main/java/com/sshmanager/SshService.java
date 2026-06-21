package com.sshmanager;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SshService {

    private SSHClient sshClient;
    private ServerInfo currentServer;

    public void connect(ServerInfo server) throws IOException {
        disconnect();

        sshClient = new SSHClient();
        sshClient.setConnectTimeout(300_000);
        sshClient.setTimeout(300_000);
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());

        sshClient.connect(server.getHost(), server.getPort());
        sshClient.authPassword(server.getUser(), server.getPassword());

        this.currentServer = server;
    }

    public String execute(String command) throws IOException {
        return execute(command, 300);
    }

    public String execute(String command, long timeoutSeconds) throws IOException {
        return execute(command, timeoutSeconds, false);
    }

    public String executeChecked(String command, long timeoutSeconds) throws IOException {
        return execute(command, timeoutSeconds, true);
    }

    private String execute(String command, long timeoutSeconds, boolean failOnNonZeroExit) throws IOException {
        if (!isConnected()) {
            throw new IOException("SSH server is not connected.");
        }

        try (Session session = sshClient.startSession()) {
            Session.Command cmd = session.exec(command);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<String> stdout = executor.submit(readStream(cmd.getInputStream()));
                Future<String> stderr = executor.submit(readStream(cmd.getErrorStream()));

                cmd.join(timeoutSeconds, TimeUnit.SECONDS);
                if (cmd.getExitStatus() == null) {
                    cmd.close();
                    throw new IOException("Command timed out after " + timeoutSeconds + " seconds.");
                }

                String output = getFuture(stdout);
                String errorOutput = getFuture(stderr);
                Integer exitStatus = cmd.getExitStatus();

                if (failOnNonZeroExit && exitStatus != null && exitStatus != 0) {
                    String details = (output + "\n" + errorOutput).trim();
                    if (details.isEmpty()) {
                        details = "No command output.";
                    }
                    throw new IOException("Command failed with exit code " + exitStatus + ":\n" + details);
                }

                if (!errorOutput.isBlank()) {
                    return output + "\n[STDERR]\n" + errorOutput;
                }

                return output;
            } finally {
                executor.shutdownNow();
            }
        }
    }

    public void disconnect() {
        if (sshClient != null && sshClient.isConnected()) {
            try {
                sshClient.disconnect();
            } catch (IOException ignored) {
                // Ignore close failures.
            }
        }
        sshClient = null;
        currentServer = null;
    }

    public boolean isConnected() {
        return sshClient != null && sshClient.isConnected() && sshClient.isAuthenticated();
    }

    public ServerInfo getCurrentServer() {
        return currentServer;
    }

    private Callable<String> readStream(InputStream inputStream) {
        return () -> {
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            return output.toString();
        };
    }

    private String getFuture(Future<String> future) throws IOException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command output reading was interrupted.", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Failed to read command output.", e);
        }
    }
}
