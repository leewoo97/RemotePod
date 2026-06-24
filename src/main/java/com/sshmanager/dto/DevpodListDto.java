package com.sshmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class DevpodListDto {
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("uid")
    private String uid;

    @JsonProperty("source")
    private Source source;

    @JsonProperty("devContainerPath")
    private String devContainerPath;

    @Data
    public static class Source {
        @JsonProperty("localFolder")
        private String localFolder; 
    }

}
