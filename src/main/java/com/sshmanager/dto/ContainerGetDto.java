package com.sshmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ContainerGetDto {
    
    @JsonProperty("ID")
    private String id;

}
