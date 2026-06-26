package com.sshmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ContainerInfoDto {
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("uid")
    private String uid;

}
