package com.sshmanager;

import javafx.application.Platform;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages SSH connections used by the Servers page.
 *
 * Command execution and UI rendering intentionally remain outside this class.
 * Use the connected callback to continue with commands such as {@code devpod list}.
 */
public final class ServersController implements AutoCloseable {

    private final Map<ServerInfo, SshService> connections = new ConcurrentHashMap<>();
    private final Map<ServerInfo, Boolean> connectingServers = new ConcurrentHashMap<>();
    private final AtomicLong connectionGeneration = new AtomicLong();
    
    //Connection이 맺어질때 CachedThreadPool을 사용함으로서, idle Thread를 바로 삭제하지않고
    //60초간 대기 시켰다가 그 사이 신규 요청이 들어오면 재사용한다.
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool(task -> {
        //Thread명 : servers-page-ssh-connect
        Thread thread = new Thread(task, "servers-page-ssh-connect");
        //메인Thread가 삭제되면 동시에 삭제되는 데몬스레드로 지정
        //해당 설정을 하지않으면 프로그램 종료 이후에도 Zombie스레드가 발생
        thread.setDaemon(true);
        return thread;
    });

    public void connectAll(Collection<ServerInfo> servers, ConnectionListener listener) {
        for (ServerInfo server : servers) {
            connect(server, listener);
        }
    }

    public void connect(ServerInfo server, ConnectionListener listener) {
        SshService existingConnection = connections.get(server);
        if (existingConnection != null && existingConnection.isConnected()) {
            notifyConnected(listener, server, existingConnection);
            return;
        }

        if (connectingServers.putIfAbsent(server, Boolean.TRUE) != null) {
            return;
        }

        long requestedGeneration = connectionGeneration.get();
        connectionExecutor.submit(() -> {
            SshService connection = new SshService();
            try {
                connection.connect(server);

                if (requestedGeneration != connectionGeneration.get()) {
                    connection.disconnect();
                    return;
                }

                SshService previousConnection = connections.put(server, connection);
                if (previousConnection != null && previousConnection != connection) {
                    previousConnection.disconnect();
                }

                notifyConnected(listener, server, connection);
            } catch (Exception exception) {
                connection.disconnect();
                notifyFailed(listener, server, exception);
            } finally {
                connectingServers.remove(server);
            }
        });
    }

    /**
     * Returns the active SSH connection for a server.
     * The caller can use this later to execute {@code devpod list}.
     */
    public Optional<SshService> getConnection(ServerInfo server) {
        SshService connection = connections.get(server);
        if (connection == null || !connection.isConnected()) {
            return Optional.empty();
        }
        return Optional.of(connection);
    }

    public void disconnectAll() {
        connectionGeneration.incrementAndGet();
        connections.values().forEach(SshService::disconnect);
        connections.clear();
        connectingServers.clear();
    }

    @Override
    public void close() {
        disconnectAll();
        connectionExecutor.shutdownNow();
    }

    private void notifyConnected(ConnectionListener listener, ServerInfo server, SshService connection) {
        Platform.runLater(() -> listener.onConnected(server, connection));
    }

    private void notifyFailed(ConnectionListener listener, ServerInfo server, Exception exception) {
        Platform.runLater(() -> listener.onFailed(server, exception));
    }

    public interface ConnectionListener {
        void onConnected(ServerInfo server, SshService connection);

        void onFailed(ServerInfo server, Exception exception);
    }
}
