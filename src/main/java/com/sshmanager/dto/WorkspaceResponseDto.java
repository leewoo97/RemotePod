package com.sshmanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceResponseDto {
    
    private String workspaceName;
    private String portInfo;
    private String pid;
    private String gateway;
    private String ipAddress;
    private String mountsInfo;
    private String created;
    private String status;
    private String serverInfo;
    private String projectPath;
    private String devcontainerPath;
}
