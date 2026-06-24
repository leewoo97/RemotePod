package com.sshmanager.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerInspectDto {

    // ── 실제 사용할 필드 ───────────────────────────────────
    private final String workspaceName;
    private final String portInfo;
    private final String pid;
    private final String gateway;
    private final String ipAddress;
    private final String mountsInfo;
    private final String created;
    private final String status;

    // ── JSON 역직렬화용 생성자 ─────────────────────────────
    @JsonCreator
    public DockerInspectDto(
            @JsonProperty("Created")      String created,
            @JsonProperty("State")        State state,
            @JsonProperty("Mounts")       List<Mount> mounts,
            @JsonProperty("NetworkSettings") NetworkSettings networkSettings,
            @JsonProperty("Name")         String name
    ) {
        // workspaceName : "/se2-backend-dldnwls0009" → 앞 "/" 제거
        this.workspaceName = name != null ? name.replaceFirst("^/", "") : "";

        this.created = created != null ? created : "";
        this.status  = state   != null ? state.getStatus() : "";
        this.pid     = state   != null ? String.valueOf(state.getPid()) : "";

        // portInfo : "8443/tcp → 0.0.0.0:8084" 형태로 조합
        if (networkSettings != null && networkSettings.getPorts() != null) {
            this.gateway   = networkSettings.getGateway();
            this.ipAddress = networkSettings.getIpAddress();
            this.portInfo = networkSettings.getPorts().entrySet().stream()
                    .flatMap(e -> e.getValue().stream()
                            .map(b -> b.getHostIp() + ":" + b.getHostPort() + " \u2192 " + e.getKey()))
                    .collect(Collectors.joining(", "));
        } else {
            this.gateway   = "";
            this.ipAddress = "";
            this.portInfo  = "";
        }

        // mountsInfo : "bind: /home/... → /workspaces/..." 형태로 조합
        if (mounts != null) {
            this.mountsInfo = mounts.stream()
                    .map(m -> String.format("[%s] %s \u2192 %s (rw=%s)",
                            m.getType(), m.getSource(), m.getDestination(), m.isRw()))
                    .collect(Collectors.joining("\n"));
        } else {
            this.mountsInfo = "";
        }
    }

    // ── 내부 파싱용 클래스 (생성자 파라미터 수신용) ────────
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class State {
        @JsonProperty("Status") private String status;
        @JsonProperty("Pid")    private int pid;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mount {
        @JsonProperty("Type")        private String type;
        @JsonProperty("Source")      private String source;
        @JsonProperty("Destination") private String destination;
        @JsonProperty("RW")          private boolean rw;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkSettings {
        @JsonProperty("Gateway")   private String gateway;
        @JsonProperty("IPAddress") private String ipAddress;
        @JsonProperty("Ports")     private Map<String, List<PortBinding>> ports;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PortBinding {
        @JsonProperty("HostIp")   private String hostIp;
        @JsonProperty("HostPort") private String hostPort;
    }
}