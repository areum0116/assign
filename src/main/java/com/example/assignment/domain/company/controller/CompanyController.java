package com.example.assignment.domain.company.controller;

import com.example.assignment.domain.company.dto.request.CompanySaveRequest;
import com.example.assignment.domain.company.dto.response.CompanySaveResponse;
import com.example.assignment.domain.company.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping("/companies")
    public CompanySaveResponse saveCompany(@RequestBody @Valid CompanySaveRequest requestDto) throws Exception {
        return companyService.saveCompany(requestDto);
    }
}
