package com.example.assignment.domain.company.service;

import com.example.assignment.domain.company.dto.request.CompanySaveRequest;
import com.example.assignment.domain.company.dto.response.CompanySaveResponse;
import com.example.assignment.domain.company.repository.CompanyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyService {

    private static final String REFERER_URL = "https://www.ftc.go.kr/www/selectBizCommOpenList.do?key=255";
    private static final String BASE_URL = "https://www.ftc.go.kr/www/downloadBizComm.do";
    private static final String DOWNLOAD_PATH = "C:/Users/arkim/Workspace/assignment/src/main/resources/static/download/";
    private String fileName;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.fair-trade-commission.base-url}")
    private String fairTradeUrl;
    @Value("${api.fair-trade-commission.key}")
    private String fairTradeKey;

    private final CompanyRepository companyRepository;

    // 파일 다운로드
    private String saveFile(byte[] fileData, String city, String district) {
        fileName = "company_" + city + "_" + district + ".csv";
        Path filePath = Paths.get(DOWNLOAD_PATH, fileName);
        try {
            Files.createDirectories(filePath.getParent());

            // decoding (euc-kr -> string)
            String str = new String(fileData, "euc-kr");
            // encoding (string -> utf8)
            byte[] utf8Data = str.getBytes(StandardCharsets.UTF_8);
            // utf8 byte[] 저장
            Files.write(filePath, utf8Data, StandardOpenOption.CREATE);

            return "파일 저장 완료: " + filePath;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 오류: " + e.getMessage(), e);
        }
    }

    // '법인'만 필터링
    private void filterCompanyByCorporation(String inputPath, String outputPath) throws Exception{
        try (
                BufferedReader reader = Files.newBufferedReader(Paths.get(inputPath));
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath))
                ) {
            String headerLine = reader.readLine();
            if(headerLine == null || headerLine.isEmpty()) throw new RuntimeException("파일이 없거나 헤더를 찾을 수 없습니다.");

            String[] header = headerLine.split(",");
            int columnIdx = Arrays.asList(header).indexOf("법인여부");
            if(columnIdx == -1) throw new RuntimeException("해당 컬럼을 찾을 수 없습니다.");

            writer.write(headerLine);
            writer.newLine();
            String line;
            while((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length > columnIdx && "법인".equals(fields[columnIdx].trim())) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            log.info("법인 필터링 완료 : {}", outputPath);
        }
    }

    // 법인등록번호 추가
    private void addCorporationNumber(String inputPath, String outputPath) throws Exception {
        Set<String> brnoSet = new HashSet<>();
        List<String[]> csvRows = new ArrayList<>();

        try(
                BufferedReader reader = Files.newBufferedReader(Paths.get(inputPath));
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath))
        ) {
            String headerLine = reader.readLine();
            if(headerLine == null || headerLine.isEmpty()) throw new RuntimeException("파일이 없거나 헤더를 찾을 수 없습니다.");

            String[] header = headerLine.split(",");
            int brnoIdx = Arrays.asList(header).indexOf("사업자등록번호");
            if(brnoIdx == -1) throw new RuntimeException("해당 컬럼을 찾을 수 없습니다.");

            String line;
            while((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length > brnoIdx) {
                    brnoSet.add(fields[brnoIdx].trim());
                }
                csvRows.add(fields);
            }

            Map<String, String> brnoToCrno = fetchAllCrno(brnoSet);

            for (String[] row : csvRows) {
                if (row.length == 0) continue;
                String brno = row[brnoIdx].trim();
                String crno = brnoToCrno.getOrDefault(brno, "");
                writer.write(String.join(",", ArrayUtils.add(row, crno)));
                writer.newLine();
            }
        }
    }

    // 공정거래위원회 통신판매사업자 등록상세 api 호출 통해 법인등록번호 가져오기
    private Map<String, String> fetchAllCrno(Set<String> brnos) throws Exception {

        Map<String, String> resultMap = new HashMap<>();
        int pageNo = 1;
        int numOfRows = 1000;
        int totalCount = Integer.MAX_VALUE;

        while ((pageNo - 1) * numOfRows < totalCount) {
            String url = UriComponentsBuilder.fromUriString(fairTradeUrl)
                    .queryParam("serviceKey", fairTradeKey)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .build()
                    .toUriString();     // 인코딩 x url

            try {
                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);    // string -> json
                totalCount = root.path("response").path("body").path("totalCount").asInt();

                JsonNode items = root.path("response").path("body").path("items");
                for (JsonNode item : items) {
                    String brno = item.path("brno").asText();
                    String crno = item.path("crno").asText();
                    if (brnos.contains(brno)) {
                        resultMap.put(brno, crno);
                    }
                }

                pageNo++;
            } catch (Exception e) {
                throw new RuntimeException("API 응답 처리 중 오류", e);
            }
        }
        return resultMap;
    }

    public CompanySaveResponse saveCompany(CompanySaveRequest requestDto) throws Exception {
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

        // 요청 결과 받기
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

        if(response.getStatusCode() == HttpStatus.OK) {
            // 파일 저장
            String message = saveFile(response.getBody(), requestDto.getCity(), requestDto.getDistrict());
            log.info(message);

            // 법인 필터링
            String filteredFilePath = DOWNLOAD_PATH + "filtered_" + fileName;
            filterCompanyByCorporation(DOWNLOAD_PATH + fileName, filteredFilePath);

            // 파일에 법인등록번호 추가
            String addCorporationNumberFilePath = DOWNLOAD_PATH + "filtered_with_crno_" + fileName;
            addCorporationNumber(filteredFilePath, addCorporationNumberFilePath);

            // 결과 반환
            return new CompanySaveResponse(response.getStatusCode().value(), message);
        } else {
            throw new RuntimeException("파일 다운로드 실패, " + response.getStatusCode());
        }
    }

}
