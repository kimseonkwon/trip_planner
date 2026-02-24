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
                        <title>AI 감성 여행 플래너</title>
                        <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=6deb6ae8b97d2bfd8b8e9697b733bae5&libraries=services&autoload=false"></script>
                        <style>
                            @import url('https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css');
                            body { font-family: 'Pretendard', sans-serif; margin: 0; padding: 0; height: 100vh; display: flex; flex-direction: column; overflow: hidden; background-color: #f8f9fa; }
                            header { background: white; padding: 15px 25px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); z-index: 100; }
                            .main-layout { display: flex; flex: 1; overflow: hidden; }
                            
                            /* 왼쪽 스크롤 패널 */
                            .side-panel { width: 500px; background: white; border-right: 1px solid #eaeaea; padding: 25px; overflow-y: auto; display: flex; flex-direction: column; }
                            .map-panel { flex: 1; position: relative; }
                            #map { width: 100%; height: 100%; }
                            
                            /* 입력 폼 */
                            .section-title { font-size: 14px; font-weight: 700; color: #333; margin: 18px 0 10px 0; }
                            input[type="text"]#prompt { width: 100%; padding: 14px; border: 1px solid #ddd; border-radius: 10px; box-sizing: border-box; font-size: 15px; background: #fafafa; transition: 0.2s; }
                            input[type="text"]#prompt:focus { background: #fff; border-color: #007bff; outline: none; box-shadow: 0 0 0 3px rgba(0,123,255,0.1); }
                            
                            .radio-group { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
                            .radio-group label { padding: 8px 16px; background: #f1f3f5; border-radius: 20px; font-size: 14px; cursor: pointer; transition: 0.2s; color: #555; font-weight: 500; }
                            .radio-group input[type="radio"] { display: none; }
                            .radio-group input[type="radio"]:checked + label { background: #3b82f6; color: white; box-shadow: 0 4px 10px rgba(59, 130, 246, 0.3); }
                            .custom-input { display: none; padding: 8px 12px; border: 1px solid #ccc; border-radius: 10px; font-size: 14px; width: 90px; }
                            
                            .submit-btn { width: 100%; padding: 16px; background: linear-gradient(135deg, #3b82f6, #2563eb); color: white; border: none; border-radius: 12px; font-weight: 700; font-size: 16px; cursor: pointer; margin-top: 30px; box-shadow: 0 6px 15px rgba(37, 99, 235, 0.2); transition: 0.3s; }
                            .submit-btn:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(37, 99, 235, 0.3); }
                            
                            .loader { display: none; text-align: center; margin-top: 20px; color: #3b82f6; font-weight: bold; }
                            
                            /* 🌟 예쁜 결과창 UI CSS */
                            #planOutput { display: none; margin-top: 30px; animation: fadeIn 0.5s ease; }
                            @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
                            
                            .plan-header { background: #eff6ff; padding: 20px; border-radius: 16px; margin-bottom: 25px; border: 1px solid #dbeafe; }
                            .plan-header h2 { margin: 0 0 10px 0; color: #1e3a8a; font-size: 20px; }
                            .plan-header p { margin: 5px 0; color: #475569; font-size: 14px; }
                            .plan-header .badge { display: inline-block; background: #bfdbfe; color: #1d4ed8; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: bold; margin-right: 5px; }
                            
                            .alert-box { background: #fef2f2; color: #dc2626; padding: 15px; border-radius: 12px; border-left: 5px solid #ef4444; font-size: 14px; font-weight: 600; margin-bottom: 25px; line-height: 1.5; }
                            
                            /* 타임라인 CSS */
                            .day-wrapper { margin-bottom: 35px; }
                            .day-title { font-size: 18px; font-weight: 800; color: #333; margin-bottom: 15px; display: flex; align-items: center; }
                            .day-title::before { content: ''; display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; }
                            .day-1::before { background: #ff4d4d; box-shadow: 0 0 0 4px rgba(255, 77, 77, 0.2); }
                            .day-2::before { background: #3b82f6; box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2); }
                            .day-3::before { background: #10b981; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.2); }
                            
                            .timeline { border-left: 2px solid #e2e8f0; margin-left: 15px; padding-left: 25px; position: relative; }
                            .timeline-item { position: relative; margin-bottom: 20px; }
                            .timeline-icon { position: absolute; left: -39px; top: 0; width: 26px; height: 26px; background: white; border: 2px solid #cbd5e1; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 13px; z-index: 2; }
                            .timeline-content { background: white; border: 1px solid #f1f5f9; padding: 15px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.03); transition: 0.2s; }
                            .timeline-content:hover { border-color: #bfdbfe; transform: translateX(3px); }
                            .t-time { font-weight: 700; color: #64748b; font-size: 13px; margin-bottom: 4px; display: block; }
                            .t-title { font-weight: 800; color: #1e293b; font-size: 15px; margin-bottom: 4px; }
                            .t-desc { font-size: 13px; color: #64748b; }
                            
                            /* 세부 정보 카드 CSS */
                            .details-section { margin-top: 40px; padding-top: 20px; border-top: 2px dashed #f1f5f9; }
                            .detail-card { display: flex; justify-content: space-between; align-items: center; padding: 14px; background: #f8fafc; border-radius: 12px; margin-bottom: 10px; }
                            .d-info { flex: 1; }
                            .d-name { font-weight: 700; font-size: 15px; color: #334155; }
                            .d-cat { font-size: 12px; color: #94a3b8; margin-left: 6px; }
                            .d-cost { font-size: 13px; color: #0f172a; font-weight: 600; margin-top: 4px; display: block; }
                            .d-link { text-decoration: none; padding: 8px 14px; background: white; border: 1px solid #cbd5e1; color: #475569; font-size: 13px; font-weight: 600; border-radius: 8px; transition: 0.2s; white-space: nowrap; }
                            .d-link:hover { background: #f1f5f9; color: #0f172a; border-color: #94a3b8; }
                        </style>
                    </head>
                    <body>
                        <header><h2 style="margin:0; color:#1e293b;">✈️ AI 감성 여행 플래너</h2></header>
                        <div class="main-layout">
                            <div class="side-panel">
                                
                                <div class="section-title" style="margin-top:0;">💭 특별히 원하는 점이 있나요?</div>
                                <input type="text" id="prompt" placeholder="예: 바다가 보이는 숙소, 유명한 빵집 추가해줘" />
                                
                                <div class="section-title">🗓️ 숙박 일정</div>
                                <div class="radio-group">
                                    <input type="radio" name="duration" id="d1" value="1박 2일" checked onclick="toggleCustom('duration', false)">
                                    <label for="d1">1박 2일</label>
                                    <input type="radio" name="duration" id="d2" value="2박 3일" onclick="toggleCustom('duration', false)">
                                    <label for="d2">2박 3일</label>
                                    <input type="radio" name="duration" id="d_custom" value="custom" onclick="toggleCustom('duration', true)">
                                    <label for="d_custom">직접입력</label>
                                    <input type="text" id="duration_input" class="custom-input" placeholder="예: 3박 4일">
                                </div>

                                <div class="section-title">👨‍👩‍👧‍👦 인원수</div>
                                <div class="radio-group">
                                    <input type="radio" name="people" id="p1" value="1명" checked onclick="toggleCustom('people', false)">
                                    <label for="p1">1명</label>
                                    <input type="radio" name="people" id="p2" value="2명" onclick="toggleCustom('people', false)">
                                    <label for="p2">2명</label>
                                    <input type="radio" name="people" id="p_custom" value="custom" onclick="toggleCustom('people', true)">
                                    <label for="p_custom">직접입력</label>
                                    <input type="number" id="people_input" class="custom-input" placeholder="명">
                                </div>

                                <div class="section-title">💰 1인당 예산</div>
                                <div class="radio-group">
                                    <input type="radio" name="budget" id="b10" value="10만원" onclick="toggleCustom('budget', false)">
                                    <label for="b10">10만원</label>
                                    <input type="radio" name="budget" id="b30" value="30만원" checked onclick="toggleCustom('budget', false)">
                                    <label for="b30">30만원</label>
                                    <input type="radio" name="budget" id="b_custom" value="custom" onclick="toggleCustom('budget', true)">
                                    <label for="b_custom">직접입력</label>
                                    <input type="number" id="budget_input" class="custom-input" placeholder="만원">
                                </div>

                                <div class="section-title">🎡 주 목적 (테마)</div>
                                <div class="radio-group">
                                    <input type="radio" name="theme" id="t_hist" value="역사" checked onclick="toggleCustom('theme', false)">
                                    <label for="t_hist">역사명소</label>
                                    <input type="radio" name="theme" id="t_nat" value="자연" onclick="toggleCustom('theme', false)">
                                    <label for="t_nat">자연/휴양</label>
                                    <input type="radio" name="theme" id="t_sport" value="스포츠" onclick="toggleCustom('theme', false)">
                                    <label for="t_sport">액티비티</label>
                                    <input type="radio" name="theme" id="t_custom" value="custom" onclick="toggleCustom('theme', true)">
                                    <label for="t_custom">직접입력</label>
                                    <input type="text" id="theme_input" class="custom-input" placeholder="테마명">
                                </div>

                                <button class="submit-btn" onclick="generatePlan()">✨ 최적의 여행 코스 만들기</button>
                                
                                <div class="loader" id="loader">⏳ AI가 장소와 동선을 계산 중입니다...</div>
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
                                    center: new kakao.maps.LatLng(35.836, 129.213), // 경주/부산 중간
                                    level: 8
                                });
                            });

                            function toggleCustom(groupName, isShow) {
                                document.getElementById(groupName + '_input').style.display = isShow ? 'inline-block' : 'none';
                            }

                            async function generatePlan() {
                                let baseText = document.getElementById('prompt').value || "경주 여행 짜줘";
                                
                                let durVal = document.querySelector('input[name="duration"]:checked').value;
                                if(durVal === 'custom') durVal = (document.getElementById('duration_input').value || "1박 2일");
                                let peopleVal = document.querySelector('input[name="people"]:checked').value;
                                if(peopleVal === 'custom') peopleVal = (document.getElementById('people_input').value || "1") + "명";
                                let budgetVal = document.querySelector('input[name="budget"]:checked').value;
                                if(budgetVal === 'custom') budgetVal = (document.getElementById('budget_input').value || "30") + "만원";
                                let themeVal = document.querySelector('input[name="theme"]:checked').value;
                                if(themeVal === 'custom') themeVal = document.getElementById('theme_input').value || "일반";

                                const combinedPrompt = `${baseText}. 조건: 일정 ${durVal}, 인원수 ${peopleVal}, 예산 ${budgetVal}, 테마 ${themeVal}`;
                                
                                document.getElementById('loader').style.display = 'block';
                                document.getElementById('planOutput').style.display = 'none';
                                
                                markers.forEach(m => m.setMap(null)); markers = [];
                                polylines.forEach(p => p.setMap(null)); polylines = [];

                                try {
                                    const res = await fetch('/api/plan', {
                                        method: 'POST', body: 'prompt=' + encodeURIComponent(combinedPrompt),
                                        headers: {'Content-Type': 'application/x-www-form-urlencoded'}
                                    });
                                    const resultText = await res.text();
                                    
                                    if(resultText.includes('===PLAN_DATA===')) {
                                        // 파이썬 JSON 데이터 파싱
                                        const pParts = resultText.split('===PLAN_DATA===')[1].split('===MAP_DATA===');
                                        const planData = JSON.parse(pParts[0].trim());
                                        const mParts = pParts[1].split('===PATH_DATA===');
                                        const markerData = JSON.parse(mParts[0].trim());
                                        const pathData = JSON.parse(mParts[1].trim());

                                        // ==========================================
                                        // 🌟 1. 세련된 UI 렌더링 시작
                                        // ==========================================
                                        let html = `
                                            <div class="plan-header">
                                                <h2>🎉 ${planData.meta.dest} ${planData.meta.nights}박 ${planData.meta.days}일 코스</h2>
                                                <span class="badge">${planData.meta.themes} 위주</span>
                                                <span class="badge">${planData.meta.people}명</span>
                                                <p style="margin-top:12px;">💰 예상 비용: <strong>${planData.meta.total_cost.toLocaleString()}원</strong> (예산: ${planData.meta.budget.toLocaleString()}원)</p>
                                                <p>🏨 메인 숙소: <strong>${planData.meta.lodging_name}</strong></p>
                                            </div>
                                        `;

                                        if (planData.meta.warning) {
                                            html += `<div class="alert-box">${planData.meta.warning}</div>`;
                                        }

                                        // 타임라인 그리기
                                        planData.timeline.forEach(day => {
                                            let dayClass = day.day > 3 ? 'day-3' : 'day-' + day.day;
                                            html += `
                                            <div class="day-wrapper">
                                                <div class="day-title ${dayClass}">Day ${day.day} 일정</div>
                                                <div class="timeline">
                                            `;
                                            
                                            day.schedule.forEach(item => {
                                                html += `
                                                    <div class="timeline-item">
                                                        <div class="timeline-icon">${item.icon}</div>
                                                        <div class="timeline-content">
                                                            <span class="t-time">${item.time}</span>
                                                            <div class="t-title">${item.title}</div>
                                                            <div class="t-desc">${item.desc}</div>
                                                        </div>
                                                    </div>
                                                `;
                                            });
                                            html += `</div></div>`;
                                        });

                                        // 세부 정보(가격, 링크) 추가
                                        html += `<div class="details-section"><div class="day-title">🔍 장소별 세부 정보</div>`;
                                        planData.details.forEach(item => {
                                            const urlBtn = item.url ? `<a href="${item.url}" target="_blank" class="d-link">상세보기 ↗</a>` : '';
                                            html += `
                                                <div class="detail-card">
                                                    <div class="d-info">
                                                        <span>${item.icon}</span> 
                                                        <span class="d-name">${item.name}</span>
                                                        <span class="d-cat">${item.category}</span>
                                                        <span class="d-cost">약 ${item.cost.toLocaleString()}원</span>
                                                    </div>
                                                    ${urlBtn}
                                                </div>
                                            `;
                                        });
                                        html += `</div>`;
                                        
                                        document.getElementById('planOutput').innerHTML = html;

                                        // ==========================================
                                        // 🌟 2. 지도 마커 및 화살표 렌더링
                                        // ==========================================
                                        const bounds = new kakao.maps.LatLngBounds();
                                        
                                        markerData.forEach(item => {
                                            const pos = new kakao.maps.LatLng(item.lat, item.lng);
                                            const marker = new kakao.maps.Marker({position: pos, map: map});
                                            markers.push(marker); bounds.extend(pos);
                                            const iw = new kakao.maps.InfoWindow({content: `<div style="padding:5px;font-size:12px;"><b>${item.icon || item.type.split(' ')[0]} ${item.name}</b></div>`});
                                            kakao.maps.event.addListener(marker, 'mouseover', () => iw.open(map, marker));
                                            kakao.maps.event.addListener(marker, 'mouseout', () => iw.close());
                                        });

                                        // 날짜별 선 색상: 1일차(빨강), 2일차(파랑), 3일차(초록), 4일차(주황)...
                                        const dayColors = ['#ef4444', '#3b82f6', '#10b981', '#f59e0b', '#8b5cf6'];

                                        for (let i = 0; i < pathData.length - 1; i++) {
                                            const p1 = pathData[i];
                                            const p2 = pathData[i+1];
                                            // 날짜가 바뀌는 구간은 선을 긋지 않음 (ex: 1일차 숙소 -> 2일차 첫 식당)
                                            if (p1.day !== p2.day) continue;

                                            const startPos = new kakao.maps.LatLng(p1.lat, p1.lng);
                                            const endPos = new kakao.maps.LatLng(p2.lat, p2.lng);
                                            const lineColor = dayColors[(p1.day - 1) % dayColors.length];

                                            const polyline = new kakao.maps.Polyline({
                                                path: [startPos, endPos],
                                                strokeWeight: 5,
                                                strokeColor: lineColor,
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
            // 🌟 텍스트에서 JSON 블록만 정확히 잘라냄
            return fullLog.contains("===PLAN_DATA===") ? 
                   fullLog.substring(fullLog.indexOf("===PLAN_DATA===")) : fullLog;
        } catch (Exception e) { return "에러: " + e.getMessage(); }
    }
}