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
                            .side-panel { width: 450px; background: white; border-right: 1px solid #ddd; padding: 25px; overflow-y: auto; display: flex; flex-direction: column; }
                            .map-panel { flex: 1; background: #e5e3df; position: relative; }
                            #map { width: 100%; height: 100%; }
                            
                            /* UI 폼 스타일 */
                            .section-title { font-size: 13px; font-weight: bold; color: #555; margin: 15px 0 8px 0; }
                            input[type="text"]#prompt { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; box-sizing: border-box; margin-bottom: 10px; font-size: 14px; }
                            
                            .radio-group { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
                            .radio-group label { padding: 6px 14px; background: #f0f2f5; border-radius: 20px; font-size: 13px; cursor: pointer; transition: 0.2s; color: #333; }
                            .radio-group input[type="radio"] { display: none; }
                            .radio-group input[type="radio"]:checked + label { background: #007bff; color: white; font-weight: bold; }
                            .custom-input { display: none; padding: 6px 10px; border: 1px solid #ddd; border-radius: 8px; font-size: 13px; width: 80px; }
                            
                            button.submit-btn { width: 100%; padding: 14px; background: #007bff; color: white; border: none; border-radius: 8px; font-weight: bold; font-size: 15px; cursor: pointer; margin-top: 25px; transition: 0.2s; }
                            button.submit-btn:hover { background: #0056b3; }
                            
                            #planOutput { margin-top: 20px; font-size: 14px; color: #333; line-height: 1.6; white-space: pre-wrap; display: none; border-top: 1px solid #eee; padding-top: 20px; }
                            .loader { display: none; text-align: center; margin-top: 15px; color: #007bff; font-weight: bold; }
                        </style>
                    </head>
                    <body>
                        <header><h2 style="margin:0;">✈️ AI 맞춤형 여행 플래너</h2></header>
                        <div class="main-layout">
                            <div class="side-panel">
                                <div class="section-title" style="margin-top:0;">자연어 추가 요청 (선택)</div>
                                <input type="text" id="prompt" placeholder="예: 서울에서 출발해. 교통편은 KTX로 해줘." />
                                
                                <div class="section-title">👨‍👩‍👧‍👦 인원수</div>
                                <div class="radio-group">
                                    <input type="radio" name="people" id="p1" value="1명" checked onclick="toggleCustom('people', false)">
                                    <label for="p1">1명</label>
                                    <input type="radio" name="people" id="p2" value="2명" onclick="toggleCustom('people', false)">
                                    <label for="p2">2명</label>
                                    <input type="radio" name="people" id="p3" value="3명" onclick="toggleCustom('people', false)">
                                    <label for="p3">3명</label>
                                    <input type="radio" name="people" id="p_custom" value="custom" onclick="toggleCustom('people', true)">
                                    <label for="p_custom">직접입력</label>
                                    <input type="number" id="people_input" class="custom-input" placeholder="명">
                                </div>

                                <div class="section-title">💰 예산 (1인당)</div>
                                <div class="radio-group">
                                    <input type="radio" name="budget" id="b10" value="10만원" onclick="toggleCustom('budget', false)">
                                    <label for="b10">10만원</label>
                                    <input type="radio" name="budget" id="b20" value="20만원" onclick="toggleCustom('budget', false)">
                                    <label for="b20">20만원</label>
                                    <input type="radio" name="budget" id="b30" value="30만원" checked onclick="toggleCustom('budget', false)">
                                    <label for="b30">30만원</label>
                                    <input type="radio" name="budget" id="b_custom" value="custom" onclick="toggleCustom('budget', true)">
                                    <label for="b_custom">직접입력</label>
                                    <input type="number" id="budget_input" class="custom-input" placeholder="만원">
                                </div>

                                <div class="section-title">🎡 관광지 테마</div>
                                <div class="radio-group">
                                    <input type="radio" name="theme" id="t_hist" value="역사" checked onclick="toggleCustom('theme', false)">
                                    <label for="t_hist">역사</label>
                                    <input type="radio" name="theme" id="t_nat" value="자연" onclick="toggleCustom('theme', false)">
                                    <label for="t_nat">자연</label>
                                    <input type="radio" name="theme" id="t_sport" value="스포츠" onclick="toggleCustom('theme', false)">
                                    <label for="t_sport">스포츠</label>
                                    <input type="radio" name="theme" id="t_custom" value="custom" onclick="toggleCustom('theme', true)">
                                    <label for="t_custom">직접입력</label>
                                    <input type="text" id="theme_input" class="custom-input" placeholder="테마명">
                                </div>

                                <button class="submit-btn" onclick="generatePlan()">최적 동선 생성하기</button>
                                
                                <div class="loader" id="loader">⏳ AI가 맞춤형 동선을 계산 중입니다...</div>
                                <div id="planOutput"></div>
                            </div>
                            
                            <div class="map-panel"><div id="map"></div></div>
                        </div>
                        
                        <script>
                            let map = null;
                            let markers = [];
                            let polylines = [];

                            kakao.maps.load(() => {
                                map = new kakao.maps.Map(document.getElementById('map'), {
                                    center: new kakao.maps.LatLng(35.1795, 129.0756), level: 8
                                });
                            });

                            // 직접입력 클릭 시 텍스트 박스 표시 토글
                            function toggleCustom(groupName, isShow) {
                                document.getElementById(groupName + '_input').style.display = isShow ? 'inline-block' : 'none';
                            }

                            async function generatePlan() {
                                // 1. 폼 데이터 수집
                                let baseText = document.getElementById('prompt').value || "부산 1박2일 여행 짜줘";
                                
                                let peopleVal = document.querySelector('input[name="people"]:checked').value;
                                if(peopleVal === 'custom') peopleVal = (document.getElementById('people_input').value || "1") + "명";
                                
                                let budgetVal = document.querySelector('input[name="budget"]:checked').value;
                                if(budgetVal === 'custom') budgetVal = (document.getElementById('budget_input').value || "30") + "만원";
                                
                                let themeVal = document.querySelector('input[name="theme"]:checked').value;
                                if(themeVal === 'custom') themeVal = document.getElementById('theme_input').value || "일반";

                                // 2. 프롬프트 병합 (파이썬 LLM이 인식하기 쉽게 포맷팅)
                                const combinedPrompt = `${baseText}. 조건: 인원수 ${peopleVal}, 예산 ${budgetVal}, 테마 ${themeVal}`;
                                console.log("전달되는 프롬프트:", combinedPrompt);

                                document.getElementById('loader').style.display = 'block';
                                document.getElementById('planOutput').style.display = 'none';
                                
                                // 기존 요소 제거
                                markers.forEach(m => m.setMap(null)); markers = [];
                                polylines.forEach(p => p.setMap(null)); polylines = [];

                                try {
                                    const res = await fetch('/api/plan', {
                                        method: 'POST', body: 'prompt=' + encodeURIComponent(combinedPrompt),
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
                                        
                                        // 마커 생성
                                        markerData.forEach(item => {
                                            const pos = new kakao.maps.LatLng(item.lat, item.lng);
                                            const marker = new kakao.maps.Marker({position: pos, map: map});
                                            markers.push(marker); bounds.extend(pos);
                                            const iw = new kakao.maps.InfoWindow({content: `<div style="padding:5px;font-size:12px;"><b>${item.type}</b><br>${item.name}</div>`});
                                            kakao.maps.event.addListener(marker, 'mouseover', () => iw.open(map, marker));
                                            kakao.maps.event.addListener(marker, 'mouseout', () => iw.close());
                                        });

                                        // 화살표 동선 그리기
                                        for (let i = 0; i < pathData.length - 1; i++) {
                                            const startPos = new kakao.maps.LatLng(pathData[i].lat, pathData[i].lng);
                                            const endPos = new kakao.maps.LatLng(pathData[i+1].lat, pathData[i+1].lng);
                                            
                                            const polyline = new kakao.maps.Polyline({
                                                path: [startPos, endPos],
                                                strokeWeight: 4,
                                                strokeColor: '#FF3366',
                                                strokeOpacity: 0.8,
                                                strokeStyle: 'solid',
                                                endArrow: true
                                            });
                                            polyline.setMap(map);
                                            polylines.push(polyline);
                                        }

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