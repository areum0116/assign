package com.example.assignment.domain.company.service;

import com.example.assignment.domain.company.dto.request.CompanySaveRequest;
import com.example.assignment.domain.company.dto.response.CompanySaveResponse;
import com.example.assignment.domain.company.repository.CompanyRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyService {

    private static final String REFERER_URL = "https://www.ftc.go.kr/www/selectBizCommOpenList.do?key=255";
    private static final String BASE_URL = "https://www.ftc.go.kr/www/downloadBizComm.do";
    private static final String DOWNLOAD_PATH = "C:/Users/arkim/Workspace/assignment/src/main/resources/static/download/";
    private final RestTemplate restTemplate;
    private final CompanyRepository companyRepository;

    // 파일 다운로드
    private String saveFile(byte[] fileData, String city, String district) {
        String fileName = "company_" + city + "_" + district + ".csv";
        Path filePath = Paths.get(DOWNLOAD_PATH, fileName);
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, fileData, StandardOpenOption.CREATE);
            return "파일 저장 완료: " + filePath;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 오류: " + e.getMessage(), e);
        }
    }

    // '법인'만 필터링
    private void filterCompanyByCorporation(String inputPath, String outputPath) throws Exception{
        try (
                Reader reader = Files.newBufferedReader(Paths.get(inputPath));
                CSVReader csvReader = new CSVReader(reader);
                Writer writer = Files.newBufferedWriter(Paths.get(outputPath), StandardCharsets.UTF_8);
                CSVWriter csvWriter = new CSVWriter(writer);
                ) {
            String[] header = csvReader.readNext();
            if(header == null) return;

            // TODO : 컬럼 인덱스 찾은 후 존재한다면 필터링, 파일 저장.
        }
    }

    public CompanySaveResponse saveCompany(CompanySaveRequest requestDto) throws URISyntaxException {
        // 파일명 url 인코딩
        String encodedFileName = URLEncoder.encode("통신판매사업자_" + requestDto.getCity() + "_" + requestDto.getDistrict() + ".csv", StandardCharsets.UTF_8);
        // 최종 url
        URI url = new URI(BASE_URL + "?atchFileUrl=dataopen&atchFileNm=" + encodedFileName);

        // 요청 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
        headers.set("Referer", REFERER_URL);
        headers.set("Accept", "*/*");
        headers.set("Accept-Encoding", "gzip, deflate, br, zstd");
        headers.set("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.set("Connection", "keep-alive");
        headers.set("Cookie", "SCOUTER=z6ockqsmn02u69; session_key=s_1743654901.06536_5d3a5dc00e6a4b69923e691b17a5bd37; pathplot_seq=0; JSESSIONID=eILD9HAOWXQwKYUupSpypKzGre8Fhip-JVob3A90.KFTCEX11");
        headers.set("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzIiwiZW1haWwiOiJhcmV1bTNAZW1haWwuY29tIiwidXNlclJvbGUiOiJVU0VSIiwiZXhwIjoxNzMxNzY2MDUwLCJpYXQiOjE3MzE2Nzk2NTB9.6e4eU9BdMDmfhVZTcyLEbDiMl2F7rdn1T0UVuooB3SY");
        headers.set("Host", "www.ftc.go.kr");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

        if(response.getStatusCode() == HttpStatus.OK) {
            String message = saveFile(response.getBody(), requestDto.getCity(), requestDto.getDistrict());
            log.info(message);
            return new CompanySaveResponse(response.getStatusCode().value(), message);
        } else {
            throw new RuntimeException("파일 다운로드 실패, " + response.getStatusCode());
        }
    }

}
