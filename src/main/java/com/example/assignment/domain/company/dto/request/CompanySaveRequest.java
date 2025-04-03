package com.example.assignment.domain.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CompanySaveRequest {
    @NotBlank
    private String city;
    @NotBlank
    private String district;
}
