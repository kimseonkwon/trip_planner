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
        
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String html = """
                    <!DOCTYPE html>
                    <html lang="ko">
                    <head>
                        <meta charset="UTF-8">
                        <title>AI 부산 여행 플래너</title>
                        <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=6deb6ae8b97d2bfd8b8e9697b733bae5&libraries=services&autoload=false"></script>
                        <style>
                            body { font-family: 'Pretendard', sans-serif; background-color: #f4f7f6; margin: 0; padding: 0; height: 100vh; display: flex; flex-direction: column; }
                            header { background: white; padding: 15px 25px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); z-index: 100; }
                            .main-container { display: flex; flex: 1; overflow: hidden; }
                            
                            /* 왼쪽: 계획 정보 */
                            .side-panel { width: 420px; background: white; border-right: 1px solid #ddd; display: flex; flex-direction: column; padding: 20px; overflow-y: auto; }
                            .input-box { margin-bottom: 20px; }
                            input { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 15px; box-sizing: border-box; }
                            button { width: 100%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer; margin-top: 10px; }
                            
                            /* 오른쪽: 지도 */
                            .map-panel { flex: 1; position: relative; background: #e5e3df; }
                            #map { width: 100%; height: 100%; }

                            .loader { display: none; text-align: center; margin: 15px 0; font-weight: bold; color: #007bff; }
                            #planOutput { white-space: pre-wrap; font-size: 14px; color: #333; line-height: 1.6; border-top: 1px solid #eee; padding-top: 20px; }
                        </style>
                    </head>
                    <body>
                        <header><h2>✈️ AI 여행 동선 플래너</h2></header>
                        <div class="main-container">
                            <div class="side-panel">
                                <div class="input-box">
                                    <input type="text" id="prompt" placeholder="부산 1박 2일 자연 여행 (예산 50만)" />
                                    <button onclick="generatePlan()">계획 생성하기</button>
                                </div>
                                <div class="loader" id="loader">⏳ AI가 동선을 계산 중입니다...</div>
                                <div id="planOutput">여행지를 입력하면 최적의 경로가 이곳에 나타납니다.</div>
                            </div>
                            <div class="map-panel">
                                <div id="map"></div>
                            </div>
                        </div>

                        <script>
                            let map = null;
                            let markers = [];

                            // 🌟 페이지가 열리자마자 지도를 즉시 로드합니다.
                            kakao.maps.load(function() {
                                const container = document.getElementById('map');
                                const options = {
                                    center: new kakao.maps.LatLng(35.1795543, 129.0756416), // 부산 중심
                                    level: 8
                                };
                                map = new kakao.maps.Map(container, options);
                                console.log("카카오 지도 로드 완료");
                            });

                            async function generatePlan() {
                                const prompt = document.getElementById('prompt').value;
                                if(!prompt) return;

                                document.getElementById('loader').style.display = 'block';
                                document.getElementById('planOutput').innerText = "";
                                clearMarkers();

                                try {
                                    const response = await fetch('/api/plan', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                        body: 'prompt=' + encodeURIComponent(prompt)
                                    });
                                    const text = await response.text();

                                    if(text.includes('===MAP_DATA===')) {
                                        const parts = text.split('===MAP_DATA===');
                                        document.getElementById('planOutput').innerText = parts[0].trim();
                                        const mapData = JSON.parse(parts[1].trim());
                                        addMarkers(mapData);
                                    } else {
                                        document.getElementById('planOutput').innerText = text;
                                    }
                                } catch(e) {
                                    alert("에러가 발생했습니다: " + e.message);
                                } finally {
                                    document.getElementById('loader').style.display = 'none';
                                }
                            }

                            function addMarkers(data) {
                                if(!map) return;
                                const bounds = new kakao.maps.LatLngBounds();
                                
                                data.forEach(p => {
                                    const pos = new kakao.maps.LatLng(p.lat, p.lng);
                                    const marker = new kakao.maps.Marker({ position: pos, map: map });
                                    markers.push(marker);
                                    bounds.extend(pos);

                                    const infowindow = new kakao.maps.InfoWindow({
                                        content: '<div style="padding:5px;font-size:12px;font-weight:bold;">' + p.name + '</div>'
                                    });
                                    kakao.maps.event.addListener(marker, 'mouseover', () => infowindow.open(map, marker));
                                    kakao.maps.event.addListener(marker, 'mouseout', () => infowindow.close());
                                });
                                map.setBounds(bounds);
                            }

                            function clearMarkers() {
                                markers.forEach(m => m.setMap(null));
                                markers = [];
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

        // API 연동 (파이썬 호출)
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
        System.out.println("🚀 웹 서버 가동: http://localhost:8080");
        server.start();
    }

    private static String runPythonPlanner(String prompt) {
        try {
            // 🚨 선권님의 아나콘다 가상환경 파이썬 경로를 다시 확인해 주세요!
            String pythonPath = "/Users/seon/anaconda3/envs/trip_planner/bin/python";
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "trip_planner.py", prompt);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) { output.append(line).append("\\n"); }
            process.waitFor();

            String fullLog = output.toString();
            if (fullLog.contains("🎉 [Planner]")) {
                return fullLog.substring(fullLog.indexOf("🎉 [Planner]"));
            }
            return fullLog;
        } catch (Exception e) {
            return "서버 오류: " + e.getMessage();
        }
    }
}