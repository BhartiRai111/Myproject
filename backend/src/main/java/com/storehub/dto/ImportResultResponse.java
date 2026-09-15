package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ImportResultResponse {
    private int totalRows;
    private int created;
    private int updated;
    private int skipped;
    private List<String> errors;
}
