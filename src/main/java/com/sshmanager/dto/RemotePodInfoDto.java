package com.sshmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class RemotePodInfoDto {

    @JsonProperty("workspaceName")
    private String workspaceName;

    @JsonProperty("sshServerInfo")
    private String sshServerInfo;

    @JsonProperty("projectPath")
    private String projectPath;

    @JsonProperty("devcontainerPath")
    private String devcontainerPath;

}
