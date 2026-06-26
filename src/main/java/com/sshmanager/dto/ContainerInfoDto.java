package com.sshmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ContainerInfoDto {

    @JsonProperty("ID")
    private String id;

    @JsonProperty("Labels")
    private String labelsRaw; // 문자열로 받기

    // 파싱 후 원하는 값 추출
    public String getUid() {
        if (labelsRaw == null) return null;
        
        for (String entry : labelsRaw.split(",")) {
            String[] kv = entry.split("=", 2);
            if (kv.length == 2 && "dev.containers.id".equals(kv[0].trim())) {
                return kv[1].trim();
            }
        }
        return null;
    }
}
