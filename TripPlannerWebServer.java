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
                        <title>AI 여행 동선 플래너</title>
                        <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=6deb6ae8b97d2bfd8b8e9697b733bae5&libraries=services&autoload=false"></script>
                        <style>
                            body { font-family: 'Pretendard', sans-serif; margin: 0; padding: 0; height: 100vh; display: flex; flex-direction: column; overflow: hidden; }
                            header { background: white; padding: 15px 25px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); z-index: 100; }
                            .main-layout { display: flex; flex: 1; overflow: hidden; }
                            .side-panel { width: 450px; background: white; border-right: 1px solid #ddd; padding: 20px; overflow-y: auto; display: flex; flex-direction: column; }
                            .map-panel { flex: 1; background: #e5e3df; position: relative; }
                            #map { width: 100%; height: 100%; }
                            input { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; box-sizing: border-box; margin-bottom: 10px; }
                            button { width: 100%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer; }
                            #planOutput { margin-top: 20px; font-size: 14px; color: #333; line-height: 1.6; white-space: pre-wrap; display: none; border-top: 1px solid #eee; padding-top: 20px; }
                            .loader { display: none; text-align: center; margin-top: 15px; color: #007bff; font-weight: bold; }
                        </style>
                    </head>
                    <body>
                        <header><h2 style="margin:0;">✈️ AI 여행 동선 플래너</h2></header>
                        <div class="main-layout">
                            <div class="side-panel">
                                <input type="text" id="prompt" placeholder="부산 1박 2일 예산 70만원" />
                                <button onclick="generatePlan()">계획 생성하기</button>
                                <div class="loader" id="loader">⏳ AI가 동선을 계산 중입니다...</div>
                                <div id="planOutput"></div>
                            </div>
                            <div class="map-panel"><div id="map"></div></div>
                        </div>
                        <script>
                            let map = null;
                            let markers = [];
                            let polyline = null;

                            kakao.maps.load(() => {
                                map = new kakao.maps.Map(document.getElementById('map'), {
                                    center: new kakao.maps.LatLng(35.1795, 129.0756), level: 8
                                });
                            });

                            async function generatePlan() {
                                const p = document.getElementById('prompt').value;
                                if(!p) return;
                                document.getElementById('loader').style.display = 'block';
                                document.getElementById('planOutput').style.display = 'none';
                                
                                // 기존 요소 제거
                                markers.forEach(m => m.setMap(null)); markers = [];
                                if (polyline) { polyline.setMap(null); }

                                try {
                                    const res = await fetch('/api/plan', {
                                        method: 'POST', body: 'prompt=' + encodeURIComponent(p),
                                        headers: {'Content-Type': 'application/x-www-form-urlencoded'}
                                    });
                                    const resultText = await res.text();
                                    
                                    if(resultText.includes('===MAP_DATA===')) {
                                        const mainParts = resultText.split('===MAP_DATA===');
                                        document.getElementById('planOutput').innerText = mainParts[0].trim();
                                        
                                        const dataParts = mainParts[1].split('===PATH_DATA===');
                                        const markerData = JSON.parse(dataParts[0].trim());
                                        const pathData = JSON.parse(dataParts[1].trim());

                                        const bounds = new kakao.maps.LatLngBounds();
                                        
                                        // 1. 마커 생성
                                        markerData.forEach(item => {
                                            const pos = new kakao.maps.LatLng(item.lat, item.lng);
                                            const marker = new kakao.maps.Marker({position: pos, map: map});
                                            markers.push(marker); bounds.extend(pos);
                                            const iw = new kakao.maps.InfoWindow({content: `<div style="padding:5px;font-size:12px;"><b>${item.type}</b><br>${item.name}</div>`});
                                            kakao.maps.event.addListener(marker, 'mouseover', () => iw.open(map, marker));
                                            kakao.maps.event.addListener(marker, 'mouseout', () => iw.close());
                                        });

                                        // 2. 동선 선(Polyline) 그리기
                                        const linePath = pathData.map(p => new kakao.maps.LatLng(p.lat, p.lng));
                                        polyline = new kakao.maps.Polyline({
                                            path: linePath,
                                            strokeWeight: 5,
                                            strokeColor: '#FF3366',
                                            strokeOpacity: 0.8,
                                            strokeStyle: 'solid'
                                        });
                                        polyline.setMap(map);

                                        if (markerData.length > 0) map.setBounds(bounds);
                                    } else {
                                        document.getElementById('planOutput').innerText = resultText;
                                    }
                                    document.getElementById('planOutput').style.display = 'block';
                                } catch(e) { alert("Error: " + e.message); }
                                finally { document.getElementById('loader').style.display = 'none'; }
                            }
                        </script>
                    </body>
                    </html>
                """;
                byte[] rb = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, rb.length);
                exchange.getResponseBody().write(rb);
                exchange.getResponseBody().close();
            }
        });

        server.createContext("/api/plan", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                    String prompt = URLDecoder.decode(br.readLine().replace("prompt=", ""), StandardCharsets.UTF_8.name());
                    String result = runPythonPlanner(prompt);
                    byte[] rb = result.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    exchange.sendResponseHeaders(200, rb.length);
                    exchange.getResponseBody().write(rb);
                    exchange.getResponseBody().close();
                }
            }
        });
        server.start();
        System.out.println("🚀 http://localhost:8080 서버 가동 시작");
    }

    private static String runPythonPlanner(String prompt) {
        try {
            String pyPath = "/Users/seon/anaconda3/envs/trip_planner/bin/python";
            ProcessBuilder pb = new ProcessBuilder(pyPath, "trip_planner.py", prompt);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) { output.append(line).append("\n"); }
            process.waitFor();
            String fullLog = output.toString();
            return fullLog.contains("🎉 [Planner]") ? fullLog.substring(fullLog.indexOf("🎉 [Planner]")) : fullLog;
        } catch (Exception e) { return "에러: " + e.getMessage(); }
    }
}