import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;

public class TripPlannerWebServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 1. 프론트엔드 UI (HTML/CSS/JS) 제공
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String html = """
                    <!DOCTYPE html>
                    <html lang="ko">
                    <head>
                        <meta charset="UTF-8">
                        <title>AI 부산 여행 플래너</title>
                        <style>
                            body { font-family: 'Pretendard', sans-serif; background-color: #f4f7f6; display: flex; justify-content: center; padding: 50px; }
                            .container { background: white; padding: 40px; border-radius: 16px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); width: 100%; max-width: 650px; }
                            h1 { text-align: center; color: #2c3e50; margin-bottom: 30px; }
                            .input-group { margin-bottom: 20px; }
                            input { width: 100%; padding: 15px; border-radius: 8px; border: 1px solid #ddd; font-size: 16px; box-sizing: border-box; transition: 0.3s; }
                            input:focus { border-color: #007bff; outline: none; box-shadow: 0 0 0 3px rgba(0,123,255,0.1); }
                            button { width: 100%; padding: 15px; background-color: #007bff; color: white; border: none; border-radius: 8px; font-size: 18px; font-weight: bold; cursor: pointer; transition: 0.3s; }
                            button:hover { background-color: #0056b3; }
                            .loader { display: none; text-align: center; margin-top: 30px; font-size: 16px; font-weight: bold; color: #007bff; }
                            .spinner { display: inline-block; width: 30px; height: 30px; border: 4px solid rgba(0,123,255,0.3); border-radius: 50%; border-top-color: #007bff; animation: spin 1s ease-in-out infinite; margin-bottom: 10px; }
                            @keyframes spin { to { transform: rotate(360deg); } }
                            #result { display: none; margin-top: 30px; border-top: 2px dashed #eee; padding-top: 20px; }
                            pre { background: #f8f9fa; padding: 20px; border-radius: 12px; white-space: pre-wrap; word-wrap: break-word; font-size: 15px; color: #333; line-height: 1.6; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>✈️ AI 맞춤형 여행 플래너</h1>
                            <div class="input-group">
                                <input type="text" id="prompt" placeholder="예: 부산 1박 2일 예산 40만원 자연 명소 위주로 짜줘" />
                            </div>
                            <button onclick="generatePlan()">최적 동선 생성하기</button>
                            
                            <div class="loader" id="loader">
                                <div class="spinner"></div><br>
                                AI가 거리를 계산하여 최적의 동선을 짜고 있습니다... ⏳
                            </div>
                            
                            <div id="result">
                                <pre id="planOutput"></pre>
                            </div>
                        </div>

                        <script>
                            async function generatePlan() {
                                const prompt = document.getElementById('prompt').value;
                                if(!prompt) { alert("여행 조건을 입력해주세요!"); return; }
                                
                                document.getElementById('loader').style.display = 'block';
                                document.getElementById('result').style.display = 'none';
                                
                                try {
                                    const response = await fetch('/api/plan', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                        body: 'prompt=' + encodeURIComponent(prompt)
                                    });
                                    const text = await response.text();
                                    document.getElementById('planOutput').innerText = text;
                                    document.getElementById('result').style.display = 'block';
                                } catch(e) {
                                    alert("에러가 발생했습니다: " + e.message);
                                } finally {
                                    document.getElementById('loader').style.display = 'none';
                                }
                            }
                        </script>
                    </body>
                    </html>
                """;
                byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            }
        });

        // 2. API 엔드포인트: 파이썬 스크립트 실행 및 결과 반환
        server.createContext("/api/plan", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String formData = br.readLine();
                    String prompt = URLDecoder.decode(formData.replace("prompt=", ""), StandardCharsets.UTF_8.name());

                    String result = runPythonPlanner(prompt);

                    byte[] responseBytes = result.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                }
            }
        });

        server.setExecutor(null);
        System.out.println("🚀 웹 서버가 시작되었습니다. 브라우저에서 접속하세요: http://localhost:" + port);
        server.start();
    }

    private static String runPythonPlanner(String prompt) {
        try {
            // 운영체제 환경에 따라 "python" 또는 "python3"로 변경
            // 사용하시는 anaconda3 가상환경의 절대 경로를 직접 지정해 줍니다.
            String pythonPath = "/Users/seon/anaconda3/envs/trip_planner/bin/python";
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "trip_planner.py", prompt);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();

            String fullLog = output.toString();
            
            // 파이썬 터미널 로그 중 사용자에게 보여줄 '최종 타임라인' 영역만 잘라서 반환
            if (fullLog.contains("🎉 [Planner]")) {
                return fullLog.substring(fullLog.indexOf("🎉 [Planner]"));
            }
            return fullLog;
        } catch (Exception e) {
            return "실행 중 서버 에러 발생: " + e.getMessage();
        }
    }
}