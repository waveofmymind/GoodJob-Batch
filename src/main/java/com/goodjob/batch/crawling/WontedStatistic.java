package com.goodjob.batch.crawling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodjob.batch.batch.BatchProducer;
import com.goodjob.batch.dto.JobCheckDto;
import com.goodjob.batch.dto.JobResponseDto;
import com.goodjob.batch.exception.CrawlingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.openqa.selenium.*;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.goodjob.batch.Constants.*;


@Component
@Slf4j
@RequiredArgsConstructor
public class WontedStatistic {

    private final BatchProducer producer;
    private final ObjectMapper objectMapper;

    //TODO: 쓰레드풀 생성하여 드라이버 관리 확인
//    private static ConcurrentLinkedQueue<WebDriver> driverPool;

    /**
     * @param jobCode 프론트 669, 백엔드 872, 풀스택(웹개발) 873
     * @param career  년차를 뜻함
     * @throws IOException
     * @throws InterruptedException
     */
//    @Async
    public void crawlWebsite(int jobCode, int career) throws InterruptedException, IOException, WebDriverException, ExecutionException {
//        ExecutorService executorService = Executors.newFixedThreadPool(20);
//        setDriverPool();
//        WebDriver driver = getDriverFromPool(); TODO: 추후 쓰레드풀 조정 하여 성능개선

        String company; //회사명
        String subject; // 제목
        String url; // url
        String sector = null; // 직무 분야
        int sectorCode = 0; // 직무 코드
        LocalDateTime localDateTime = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String createDate = localDateTime.format(dateFormatter);


        WebDriver driver = setDriver().get();

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

        // url = base1 + jobCode + base2 + year + wontedEnd
        String wontedURL = WONTED_BASE1 + jobCode + WONTED_BASE2 + career + WONTED_END;

        driver.get(wontedURL); // 크롤링하고자 하는 웹페이지 주소로 변경해주세요.

        JavascriptExecutor js = (JavascriptExecutor) driver;

        int errorCnt = 0;
        while (true) {
            //현재 높이 저장
            try {
                Long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");


                // 스크롤
                js.executeScript("window.scrollTo(0, document.body.scrollHeight)");

                // 새로운 내용이 로드될 때까지 대기
                Thread.sleep(2000);

                // 새로운 높이를 얻음
                Long newHeight = (Long) js.executeScript("return document.body.scrollHeight");
                if (newHeight.equals(lastHeight)) {
                    break;
                }
            } catch (InterruptedException e) {
                errorCnt++;
                if (errorCnt > 100) {
                    driver.close();
                    throw new CrawlingException("메인페이지 스크롤 에러");
                }
            }
        }

        switch (jobCode) {
            case 669 -> {
                sectorCode = 92;
                sector = "프론트";
            }
            case 872 -> {
                sectorCode = 84;
                sector = "백엔드";
            }
            case 873 -> {
                sectorCode = 2232;
                sector = "풀스택";
            }
        }

        List<WebElement> webElements = driver.findElements(By.className("Card_className__u5rsb"));
        List<JobCheckDto> checkDtos = new ArrayList<>();
        int listSize = webElements.size();
        for (int i = 1; i < listSize; i++) {
            try {
                WebElement webElement = webElements.get(i);

                subject = webElement.findElement(By.xpath(String.format("//*[@id=\"__next\"]/div[3]/div/div/div[4]/ul/li[%d]/div/a/div/div[1]", i))).getText();// 공고제목
                company = webElement.findElement(By.xpath(String.format("//*[@id=\"__next\"]/div[3]/div/div/div[4]/ul/li[%d]/div/a/div/div[2]", i))).getText();// 회사명
                url = webElement.findElement(By.xpath(String.format("//*[@id=\"__next\"]/div[3]/div/div/div[4]/ul/li[%d]/div/a", i))).getAttribute("href");
                JobCheckDto checkDto = new JobCheckDto(company, subject, url, sector, sectorCode, createDate, career);
                checkDtos.add(checkDto);
            } catch (StaleElementReferenceException e) {
                log.error(e.getMessage());
            }

        }
        driver.close();

        System.out.println(checkDtos.size() + "개의 채용공고가 있습니다.");
        for (JobCheckDto checkDto : checkDtos) {
            try {
                detailPage(checkDto);
            } catch (CrawlingException e) {
                continue;
            }
        }
    }

    private CompletableFuture<WebDriver> setDriver() throws InterruptedException, IOException {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            System.setProperty("webdriver.chrome.driver", "drivers/chromedriver_win.exe");
        } else if (os.contains("mac")) {
            Process process = Runtime.getRuntime().exec("xattr -d com.apple.quarantine drivers/chromedriver_mac");
            process.waitFor();
            System.setProperty("webdriver.chrome.driver", "drivers/chromedriver_mac");
        } else if (os.contains("linux")) {
            System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");
        }

        ChromeOptions chromeOptions = new ChromeOptions();

        chromeOptions.addArguments("--headless=new");
        chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--disable-dev-shm-usage");
        chromeOptions.addArguments("--disable-gpu");
        return CompletableFuture.supplyAsync(() -> new ChromeDriver(chromeOptions));
    }


    private void scrollDown(WebDriver driver) throws TimeoutException, NoSuchElementException{
        WebElement element = driver.findElement(By.className("JobDescription_JobDescription__VWfcb"));
        WebDriverWait wait1 = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait1.until(ExpectedConditions.visibilityOfElementLocated(By.className("JobDescription_JobDescription__VWfcb")));

        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({block: 'end', behavior: 'auto'});", element);
        js.executeScript("window.scrollBy(0, window.innerHeight);");
    }

    @Async
    public void detailPage(JobCheckDto checkDto) throws ExecutionException, InterruptedException, IOException {
            WebDriver driver = setDriver().get();
            driver.get(checkDto.url());

        try {
            scrollDown(driver);
            WebDriverWait xpathWait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement deadlineElement = xpathWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"__next\"]/div[3]/div[1]/div[1]/div/div[2]/section[2]/div[1]/span[2]")));
            WebElement workingAreaElement = xpathWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"__next\"]/div[3]/div[1]/div[1]/div/div[2]/section[2]/div[2]/span[2]")));

            // 웹 요소로부터 텍스트 추출
            String deadLine = deadlineElement.getText();
            String place = workingAreaElement.getText();
            log.debug("{}", deadLine);
            log.debug("{}", place);

            JobResponseDto jobResponseDto = new JobResponseDto
                    (
                            checkDto.company(), checkDto.subject(), checkDto.url(),
                            checkDto.sector(), checkDto.sectorCode(), checkDto.createDate(),
                            deadLine, checkDto.career(), place
                    );
            System.out.println(jobResponseDto.getUrl());
//        producer.batchProducer(objectMapper.writeValueAsString(jobResponseDto));
        }catch (Exception e){
            e.printStackTrace();
            throw new CrawlingException("detailPage 스크롤 에러");
        }finally {
            driver.close();
        }

    }


    /**
     * driver pool 관리
     * TODO: 확인 후 성능 비교
     */
//    private static void initializeDriverPool() {
//        for (int i = 0; i < 30; i++) {
//            ChromeOptions chromeOptions = new ChromeOptions();
//
//            chromeOptions.addArguments("--headless=new");
//            chromeOptions.addArguments("--no-sandbox");
//            chromeOptions.addArguments("--disable-dev-shm-usage");
//            chromeOptions.addArguments("--disable-gpu");
//            WebDriver driver = new ChromeDriver(chromeOptions);
//            driverPool.add(driver);
//        }
//    }

//    @Async
//    public void setDriverPool() {
//        initializeDriverPool();
//    }

//    private static WebDriver getDriverFromPool() {
//        return driverPool.poll();
//    }
//
//    private static void returnDriverToPool(WebDriver driver) {
//        driverPool.add(driver);
//    }
}