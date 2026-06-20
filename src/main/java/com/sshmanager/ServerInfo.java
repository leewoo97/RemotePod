package com.sshmanager;

/**
 * 서버 접속 정보를 담는 모델 클래스
 */
public class ServerInfo {
    private String name;   // 서버 별칭
    private String host;   // IP 또는 도메인
    private int port;      // SSH 포트 (기본 22)
    private String user;   // 계정명
    private String password;

    public ServerInfo(String name, String host, int port, String user, String password) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    // Getters
    public String getName()     { return name; }
    public String getHost()     { return host; }
    public int getPort()        { return port; }
    public String getUser()     { return user; }
    public String getPassword() { return password; }

    @Override
    public String toString() {
        return name + " (" + user + "@" + host + ":" + port + ")";
    }
}
