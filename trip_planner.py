import os
import sys
import json
import re
import math
import requests
import random
from datetime import datetime, timedelta
from typing import Dict, Any, List, TypedDict
from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph
from tavily import TavilyClient

# ==========================================
# 🌟 전역 변수 및 API 키 설정 (본인 키로 유지)
# ==========================================
llm = ChatOllama(model="llama3", temperature=0)
DATA_GO_KR_KEY = "c4407b5c5f60c9b952bc1f472d9c3231e98d8d6844b2a9cd9b9324887c9fcb86"
KAKAO_API_KEY = "6ebf3f3fd63b0a4d455916cb4f631ea4"
TAVILY_API_KEY = "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"
SERP_API_KEY = "fbb08fded2aca6608570b8ffc92ace4abf6a108ffa9f1f36db9009f5519af50a"

STATION_DB = {
    "기차": {"서울": "NAT010000", "용산": "NAT010032", "부산": "NAT011573", "울산": "NAT014445", "태화강": "NAT011599", "동대구": "NAT011668", "대전": "NAT010032", "광주": "NAT010522", "강릉": "NAT010065", "목포": "NAT010486"},
    "버스": {"서울": "NAEK010", "부산": "NAEK700", "울산": "NAEK715", "대구": "NAEK801", "대전": "NAEK300", "광주": "NAEK500"}
}
CITY_CODES = {"서울": "11", "용산": "11", "부산": "21", "대구": "22", "인천": "23", "광주": "24", "대전": "25", "울산": "26", "경기": "31", "강원": "32", "충북": "33", "충남": "34", "전북": "35", "전남": "36", "경북": "37", "경남": "38"}

# ==========================================
# 🌟 [수정] 카카오 장소 검색 (정렬 기준 보완)
# ==========================================
def fetch_kakao_places(keyword: str, category_code: str = "", size: int = 5, x: str = None, y: str = None, radius: int = None) -> List[Dict]:
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_API_KEY}"}
    params = {"query": keyword, "size": size}

    if category_code:
        params["category_group_code"] = category_code
    
    if x and y and radius:
        params["x"] = x
        params["y"] = y
        params["radius"] = radius
        # 🌟 너무 몰리는 현상 해결: 'distance' 대신 'accuracy(정확도/인기도)' 우선
        params["sort"] = "accuracy" 

    try:
        res = requests.get(url, headers=headers, params=params, timeout=5)
        if res.status_code == 200:
            return res.json().get('documents', [])
    except:
        pass
    return []

def fetch_price_via_tavily(query: str, min_price=3000, max_price=1000000) -> int:
    try:
        tavily = TavilyClient(api_key=TAVILY_API_KEY) 
        response = tavily.search(query=query, search_depth="basic", max_results=2)
        context = "\n".join([r['content'] for r in response.get('results', [])])
        prices = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})*)원', context)
        valid_prices = [int(p.replace(',', '')) for p in prices if min_price <= int(p.replace(',', '')) <= max_price]
        if valid_prices: return (sum(valid_prices) // len(valid_prices) // 100) * 100
    except: pass
    return 0

def format_time_str(time_str: str) -> str:
    try:
        s = str(time_str)
        if len(s) >= 12: return f"{s[8:10]}:{s[10:12]}"
        return s
    except: return str(time_str)

def fetch_station_code(station_name: str, city_code: str) -> str:
    base_url = "https://apis.data.go.kr/1613000/TrainInfoService/getCtyAcctoTrainSttnList"
    request_url = f"{base_url}?serviceKey={DATA_GO_KR_KEY}"
    try:
        res = requests.get(request_url, params={"cityCode": city_code, "numOfRows": 100, "_type": "json"})
        items = res.json().get('response', {}).get('body', {}).get('items', {}).get('item', [])
        if isinstance(items, dict): items = [items]
        for item in items:
            if station_name in item['nodename']: return item['nodeid']
    except: pass
    return None

def fetch_bus_api(origin: str, dest: str) -> List[Dict]:
    dep_id = STATION_DB["버스"].get(origin)
    arr_id = STATION_DB["버스"].get(dest)
    if not dep_id or not arr_id: return []
    base_url = "https://apis.data.go.kr/1613000/ExpBusInfoService/getStrtpntAlocFndExpbusInfo"
    request_url = f"{base_url}?serviceKey={DATA_GO_KR_KEY}"
    try:
        res = requests.get(request_url, params={"depTerminalId": dep_id, "arrTerminalId": arr_id, "depPlandTime": datetime.now().strftime("%Y%m%d"), "numOfRows": 10, "_type": "json"})
        items = res.json().get('response', {}).get('body', {}).get('items', {}).get('item', [])
        if isinstance(items, dict): items = [items]
        return [{"type": "버스", "vehicle": f"고속버스({i['gradeNm']})", "start_station": i['depPlaceNm'], "end_station": i['arrPlaceNm'], "departure_time": format_time_str(i['depPlandTime']), "arrival_time": format_time_str(i['arrPlandTime']), "cost": i['charge']} for i in items]
    except: return []

class TravelState(TypedDict):
    user_query: str  
    constraints: Dict[str, Any]
    transport: Dict[str, Any]
    lodging: Dict[str, Any]
    food: Dict[str, Any]
    attractions: Dict[str, Any]
    integrated: Dict[str, Any]
    validation: Dict[str, Any]
    react_decision: str
    revision_request: str
    retry_count: int
    tried_steps: list
    final_plan: str

def extract_json(text: str) -> Dict[str, Any]:
    try:
        match = re.search(r"```json\s*(.*?)```", text, re.DOTALL)
        if match: text = match.group(1)
        else:
            match = re.search(r"```\s*(.*?)```", text, re.DOTALL)
            if match: text = match.group(1)
        start_idx = text.find('{')
        end_idx = text.rfind('}')
        if start_idx != -1 and end_idx != -1: text = text[start_idx : end_idx + 1]
        text = re.sub(r"//.*", "", text)
        return json.loads(text)
    except: return {}

# --- Nodes ---

def supervisor_node(state: Dict[str, Any]) -> Dict[str, Any]:
    print("\n🧠 [Supervisor] 사용자 자연어 요청 분석 중...")
    query = state.get("user_query", "")
    
    # 🌟 [수정] 프롬프트에 '역사' 등 테마 추출을 강제하도록 보강
    prompt = f"""
    당신은 여행 플래너의 Supervisor입니다.
    사용자의 자연어 요청을 분석하여 JSON으로 추출하세요.
    [사용자 요청] "{query}"
    [규칙] 
    1. origin 2. destination 3. budget_total(숫자만) 4. people(숫자만) 5. duration_nights, duration_days
    6. theme: 사용자가 '역사', '자연', '맛집', '휴양' 등을 언급하면 반드시 배열로 추출하세요. 없으면 ["일반"]
    [출력 예시] {{"origin": "서울", "destination": "부산", "duration_nights": 1, "duration_days": 2, "budget_total": 200000, "people": 2, "theme": ["역사"]}}
    """
    response = llm.invoke(prompt)
    constraints = extract_json(response.content)
    if not constraints: constraints = {"destination": "서울", "people": 1, "theme": ["일반"]}
    
    man_match = re.search(r'(\d+)\s*만', query)
    if man_match: constraints["budget_total"] = int(man_match.group(1)) * 10000
    elif not constraints.get("budget_total"): constraints["budget_total"] = 300000
    if not constraints.get("origin"): constraints["origin"] = "서울"
    if not constraints.get("people"): constraints["people"] = 1
    
    # 테마 보정
    if not constraints.get("theme"): constraints["theme"] = ["일반"]
    elif isinstance(constraints["theme"], str): constraints["theme"] = [constraints["theme"]]
        
    constraints["duration"] = f"{constraints.get('duration_nights', 1)}박{constraints.get('duration_days', 2)}일" 
    
    print(f"   📋 추출된 제약조건: {json.dumps(constraints, ensure_ascii=False)}")
    return {"constraints": constraints}

def transport_node(state: Dict[str, Any]) -> Dict[str, Any]:
    decision = state.get("react_decision", "")
    if state.get("retry_count", 0) > 0 and decision != "transport" and state.get("transport", {}).get("selected"):
        return {"transport": state.get("transport")}
    print("\n🚄 [Transport] 실시간 교통편 탐색")
    c = state.get("constraints", {})
    dest, origin = c.get("destination", "부산").strip(), c.get("origin", "서울").strip() 
    mode = "고속버스" if state.get("revision_request", "") and decision == "transport" else "KTX"
    selected = {}
    
    if mode == "KTX":
        dep_id = fetch_station_code(origin, CITY_CODES.get(origin[:2], "11")) or STATION_DB["기차"].get(origin)
        arr_id = fetch_station_code(dest, CITY_CODES.get(dest[:2], "21")) or STATION_DB["기차"].get(dest)
        if dep_id and arr_id:
            try:
                res = requests.get(f"https://apis.data.go.kr/1613000/TrainInfoService/getStrtpntAlocFndTrainInfo?serviceKey={DATA_GO_KR_KEY}", params={"depPlaceId": dep_id, "arrPlaceId": arr_id, "depPlandTime": (datetime.now() + timedelta(days=3)).strftime("%Y%m%d"), "numOfRows": 10, "_type": "json"})
                items = res.json().get('response', {}).get('body', {}).get('items', {}).get('item', [])
                if isinstance(items, dict): items = [items]
                if items: selected = {"type": "기차", "name": f"{origin} ↔ {dest} {items[0]['traingradename']}", "cost": int(items[0]['adultcharge'])}
            except: pass
    elif mode == "고속버스":
        bus_res = fetch_bus_api(origin, dest)
        if bus_res: selected = {"type": "버스", "name": f"{bus_res[0]['start_station']} ↔ {bus_res[0]['end_station']} {bus_res[0]['vehicle']}", "cost": int(bus_res[0]['cost'])}

    if not selected:
        price = fetch_price_via_tavily(f"{origin} {dest} {mode} 요금", 5000, 100000) or (15000 if mode == "고속버스" else 30000)
        selected = {"type": mode, "name": f"{origin} ↔ {dest} {mode}", "cost": price}

    return {"transport": {"selected": selected}}

def lodging_node(state: Dict[str, Any]) -> Dict[str, Any]:
    decision = state.get("react_decision", "")
    if state.get("retry_count", 0) > 0 and decision != "lodging" and state.get("lodging", {}).get("selected"):
        return {"lodging": state.get("lodging")}
    print("\n🏨 [Lodging] 실시간 숙소 탐색")
    dest = state.get("constraints", {}).get("destination", "").strip()
    is_low = (decision == "lodging")
    kws = [f"{dest} 모텔", f"{dest} 호텔"] if is_low else [f"{dest} 호텔", f"{dest} 레지던스"]
    
    places = []
    for kw in kws:
        res = fetch_kakao_places(kw, category_code="AD5", size=10)
        if res: places = res; break

    selected = {"name": "숙소 정보 없음", "estimated_cost": 50000, "type": "숙소", "url": ""}
    if places:
        cands = []
        for p in places[:2]:
            try:
                res = requests.get("https://serpapi.com/search", params={"engine": "google", "q": f"{p['place_name']} 숙박 가격", "api_key": SERP_API_KEY, "hl": "ko", "gl": "kr"}, timeout=10).json()
                context = " ".join([str(res.get("answer_box", "")), str(res.get("knowledge_graph", ""))] + [o.get("snippet", "") for o in res.get("organic_results", [])[:3]])
                valid_prices = [int(cp.replace(',', '')) for cp in re.findall(r'(?:₩|가격\s*:?\s*)?([1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\s*원)?', context)]
                valid_prices += [int(mp) * 10000 for mp in re.findall(r'([1-9][0-9]*)\s*만\s*원?', context)]
                fp = min([v for v in valid_prices if v >= (30000 if is_low else 50000)]) if valid_prices else (45000 if is_low else 180000)
            except: fp = 45000 if is_low else 150000
            cands.append({"name": p['place_name'], "estimated_cost": fp, "type": p.get('category_name', '숙소').split(' > ')[-1], "x": p.get('x'), "y": p.get('y')})
        selected = min(cands, key=lambda x: x['estimated_cost']) if is_low else random.choice(cands)
    return {"lodging": {"selected": selected}}

def attraction_node(state: Dict[str, Any]) -> Dict[str, Any]:
    decision = state.get("react_decision", "")
    if state.get("retry_count", 0) > 0 and decision != "attraction" and state.get("attractions", {}).get("selected_list"):
        return {"attractions": state.get("attractions")}
    
    print("\n🎡 [Attraction] 숙소 반경 10km 이내 맞춤형 테마 관광지 탐색")
    dest = state.get("constraints", {}).get("destination", "").strip()
    themes = state.get("constraints", {}).get("theme", ["일반"]) 
    lodging = state.get("lodging", {}).get("selected", {})
    
    lx, ly = lodging.get('x'), lodging.get('y')
    radius = 10000 if lx and ly else None

    # 🌟 [핵심 변경] 테마에 따른 키워드 맵핑 및 제약 완화
    theme_kw = "가볼만한곳"
    is_specific_theme = False
    
    main_theme = themes[0]
    if "역사" in main_theme: 
        theme_kw = "역사 유적지 문화재"
        is_specific_theme = True
    elif "자연" in main_theme: 
        theme_kw = "자연명소 공원"
        is_specific_theme = True
    elif "문화" in main_theme: 
        theme_kw = "박물관 미술관"
        is_specific_theme = True
    elif "액티비티" in main_theme: 
        theme_kw = "테마파크 체험"
        is_specific_theme = True
    elif main_theme != "일반":
        theme_kw = main_theme
        is_specific_theme = True

    places, seen = [], set()
    def add_p(new_p):
        for p in new_p:
            if p['place_name'] not in seen: seen.add(p['place_name']); places.append(p)

    kw = f"{dest} {theme_kw}"
    
    # 🌟 테마가 명확하면(예: 역사) 카테고리(AT4) 제한을 풀고 키워드 위주로 넓게 검색합니다.
    if is_specific_theme:
        if res1 := fetch_kakao_places(kw, size=15, x=lx, y=ly, radius=radius): add_p(res1)
    else:
        if res1 := fetch_kakao_places(kw, category_code="AT4", size=15, x=lx, y=ly, radius=radius): add_p(res1)
    
    # 부족할 경우 문화시설(CT1) 및 일반 검색 추가 수행
    if len(places) < 2 and (res2 := fetch_kakao_places(kw, category_code="CT1", size=10, x=lx, y=ly, radius=radius)): add_p(res2)
    if len(places) < 2 and (res3 := fetch_kakao_places(f"{dest} 가볼만한곳", category_code="AT4", size=10, x=lx, y=ly, radius=radius)): add_p(res3)

    selected_list = []
    if places:
        # 상위 10개 풀에서 랜덤하게 2개를 뽑아 지나친 밀집을 방지
        candidates_pool = places[:10]
        random.shuffle(candidates_pool)
        candidates = candidates_pool[:2]
        
        for p in candidates:
            try:
                res = requests.get("https://serpapi.com/search", params={"engine": "google", "q": f"{dest} {p['place_name']} 성인 입장료", "api_key": SERP_API_KEY, "hl": "ko", "gl": "kr"}, timeout=10).json()
                context = " ".join([str(res.get("answer_box", "")), str(res.get("knowledge_graph", ""))] + [o.get("snippet", "") for o in res.get("organic_results", [])[:2]])
                if any(k in context.replace(" ", "") for k in ["입장료무료", "무료입장", "무료개방", "입장료:0원", "무료이용"]): fp = 0
                else:
                    vps = [int(cp.replace(',', '')) for cp in re.findall(r'(?:₩|입장료\s*:?\s*)?([1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\s*원)?', context)]
                    vps += [int(mp) * 10000 for mp in re.findall(r'([1-9][0-9]*)\s*만\s*원?', context)]
                    vps += [int(rp) for rp in re.findall(r'([1-9][0-9]{2,4})\s*원', context)]
                    reals = [v for v in vps if 500 <= v <= 200000]
                    fp = min(reals) if reals else 0
                if any(k in p.get('category_name', '') for k in ["공원", "산", "휴양림", "계곡", "해수욕장", "정원", "해변"]) and fp > 15000: fp = 0
            except: fp = 0
            selected_list.append({"name": p['place_name'], "type": p.get('category_name', '관광지').split(' > ')[-1], "estimated_cost": fp, "x": p.get('x'), "y": p.get('y')})

    return {"attractions": {"selected_list": selected_list}}

def food_node(state: Dict[str, Any]) -> Dict[str, Any]:
    decision = state.get("react_decision", "")
    if state.get("retry_count", 0) > 0 and decision != "food" and state.get("food", {}).get("selected_list"):
        return {"food": state.get("food")}
    
    print("\n🍽️ [Food] 숙소 반경 10km 이내 맛집 탐색")
    dest = state.get("constraints", {}).get("destination", "").strip()
    lodging = state.get("lodging", {}).get("selected", {})
    
    lx, ly = lodging.get('x'), lodging.get('y')
    radius = 10000 if lx and ly else None

    target = 7 if "2박" in state.get("constraints", {}).get("duration", "") else 4
    kws = [f"{dest} 기사식당", f"{dest} 국밥"] if decision == "food" else [f"{dest} 맛집"]
    
    places = []
    for kw in kws:
        res = fetch_kakao_places(kw, category_code="FD6", size=15, x=lx, y=ly, radius=radius)
        valid = [p for p in res if "카페" not in p.get('category_name','')]
        if valid: places = valid; break 
    if not places: places = fetch_kakao_places(f"{dest} 식당", category_code="FD6", size=15, x=lx, y=ly, radius=radius)

    selected_list = []
    if places:
        processed = []
        
        # 맛집도 약간 섞어서 지나치게 한 동네에 뭉치는 것을 방지
        candidates_pool = places[:12]
        if decision != "food": random.shuffle(candidates_pool)
        
        for p in candidates_pool[:7]:
            try:
                res = requests.get("https://serpapi.com/search", params={"engine": "google", "q": f"{dest} {p['place_name']} 대표 메뉴 가격", "api_key": SERP_API_KEY, "hl": "ko", "gl": "kr"}, timeout=10).json()
                context = " ".join([str(res.get("knowledge_graph", ""))] + [o.get("snippet", "") for o in res.get("organic_results", [])[:3]])
                vps = [int(cp.replace(',', '')) for cp in re.findall(r'([1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\s*원)?', context)]
                reals = [v for v in vps if (5000 if decision=="food" else 8000) <= v <= (20000 if decision=="food" else 80000)]
                fp = min(reals) if reals else (8000 if decision=="food" else 15000)
            except: fp = 8000 if decision=="food" else 15000
            processed.append({"name": p['place_name'], "type": p.get('category_name', '').split(' > ')[-1], "estimated_cost": fp, "x": p.get('x'), "y": p.get('y')})
        
        if decision == "food": processed.sort(key=lambda x: x['estimated_cost'])
        selected_list = processed[:target]
        
    return {"food": {"selected_list": selected_list}}

def integrator_node(state: Dict[str, Any]) -> Dict[str, Any]:
    c = state.get("constraints", {})
    people, dur = c.get("people", 1), c.get("duration", "1박2일")
    trans, lodg = state.get("transport", {}).get("selected", {}), state.get("lodging", {}).get("selected", {})
    foods, attrs = state.get("food", {}).get("selected_list", []), state.get("attractions", {}).get("selected_list", [])

    tc = trans.get("cost", 0) * (1 if "자차" in trans.get("type", "") else people * 2)
    lc = lodg.get("estimated_cost", 0) * ((people + 1) // 2) * (2 if "2박" in dur else 1)
    fc = sum(f.get('estimated_cost', 0) for f in foods) * people
    ac = sum(a.get('estimated_cost', 0) for a in attrs) * people
    
    total = tc + lc + fc + ac
    state["integrated"] = {
        "total_cost": total,
        "breakdown": {"transport": {"desc": f"{tc:,}원"}, "lodging": {"desc": f"{lc:,}원"}, "food": {"desc": f"{fc:,}원"}, "attraction": {"desc": f"{ac:,}원"}}
    }
    return state

def validation_node(state: Dict[str, Any]) -> Dict[str, Any]:
    b = state.get("constraints", {}).get("budget_total", 0)
    t = state.get("integrated", {}).get("total_cost", 0)
    state["validation"] = {"passed": (b - t >= 0), "reason": "Budget Exceeded" if b - t < 0 else "OK"}
    return state

def react_decision_node(state: Dict[str, Any]) -> Dict[str, Any]:
    tried = state.get("tried_steps", [])
    if state.get("retry_count", 0) >= 4: return {"react_decision": "planner"}
    costs = {k: int(re.findall(r'[0-9,]+', str(v.get("desc","0")))[0].replace(',','')) for k, v in state.get("integrated", {}).get("breakdown", {}).items() if k not in tried}
    target = max(costs, key=costs.get) if costs else "planner"
    return {"react_decision": target, "retry_count": state.get("retry_count", 0) + 1, "tried_steps": tried + [target]}

def calculate_distance(lat1, lon1, lat2, lon2):
    try:
        R = 6371  
        dlat = math.radians(float(lat2) - float(lat1))
        dlon = math.radians(float(lon2) - float(lon1))
        a = math.sin(dlat/2)**2 + math.cos(math.radians(float(lat1))) * math.cos(math.radians(float(lat2))) * math.sin(dlon/2)**2
        return R * (2 * math.atan2(math.sqrt(a), math.sqrt(1-a)))
    except: return 0

def planner_node(state: Dict[str, Any]) -> Dict[str, Any]:
    c = state.get("constraints", {})
    dest, budget, people = c.get("destination", "부산"), c.get("budget_total", 0), c.get("people", 1)
    
    transport = state.get("transport", {}).get("selected", {})
    lodging = state.get("lodging", {}).get("selected", {})
    all_foods = state.get("food", {}).get("selected_list", [])[:]
    all_attrs = state.get("attractions", {}).get("selected_list", [])[:]
    
    foods_temp, attrs_temp = all_foods[:], all_attrs[:]
    total_cost = state.get("integrated", {}).get("total_cost", 0)

    ordered_path = []
    def add_to_path(item):
        if item and item.get('x') and item.get('y'):
            ordered_path.append({"lat": float(item['y']), "lng": float(item['x'])})

    def get_nearest(cur, cands):
        if not cands: return None
        if cur and 'y' in cur and 'y' in cands[0]:
            cands.sort(key=lambda i: calculate_distance(cur['y'], cur['x'], i['y'], i['x']))
        return cands.pop(0)

    cur = {} 
    timeline = []
    timeline.append("🌴 [1일차]\n" + f"  🕒 10:00 | 🚄 {dest} 도착 및 시작 ({transport.get('name')})")
    
    if f1 := get_nearest(cur, foods_temp): timeline.append(f"  🕒 11:30 | 🍽️ 식사: {f1['name']}"); cur = f1; add_to_path(f1)
    if a1 := get_nearest(cur, attrs_temp): timeline.append(f"  🕒 14:00 | 🎡 관광: {a1['name']}"); cur = a1; add_to_path(a1)
    if f2 := get_nearest(cur, foods_temp): timeline.append(f"  🕒 18:00 | 🍽️ 식사: {f2['name']}"); cur = f2; add_to_path(f2)
    timeline.append(f"  🕒 20:00 | 🏨 숙소 체크인: {lodging.get('name')}"); cur = lodging; add_to_path(lodging)

    timeline.append("\n🌅 [2일차]\n  🕒 10:00 | 🏨 숙소 체크아웃")
    if f3 := get_nearest(cur, foods_temp): timeline.append(f"  🕒 11:30 | 🍽️ 식사: {f3['name']}"); cur = f3; add_to_path(f3)
    if a2 := get_nearest(cur, attrs_temp): timeline.append(f"  🕒 14:00 | 🎡 관광: {a2['name']}"); cur = a2; add_to_path(a2)
    if f4 := get_nearest(cur, foods_temp): timeline.append(f"  🕒 17:00 | 🍽️ 식사: {f4['name']}"); add_to_path(f4)
    timeline.append(f"  🕒 19:00 | 🚄 {dest} 출발 및 종료")

    plan_text = f"==========================================\n✈️  {dest} 완벽 여행 플랜\n==========================================\n💰 총 예상 비용: {total_cost:,}원 (예산: {budget:,}원)\n\n"
    plan_text += "\n".join(timeline) + "\n=========================================="
    print(plan_text)

    map_data = []
    if lodging.get('x'): map_data.append({"name": lodging['name'], "type": "🏨 숙소", "lat": float(lodging['y']), "lng": float(lodging['x'])})
    for f in all_foods:
        if f.get('x'): map_data.append({"name": f['name'], "type": "🍽️ 맛집", "lat": float(f['y']), "lng": float(f['x'])})
    for a in all_attrs:
        if a.get('x'): map_data.append({"name": a['name'], "type": "🎡 관광지", "lat": float(a['y']), "lng": float(a['x'])})

    print("===MAP_DATA===\n" + json.dumps(map_data, ensure_ascii=False))
    print("===PATH_DATA===\n" + json.dumps(ordered_path, ensure_ascii=False))
    return {"react_decision": "done"}

workflow = StateGraph(TravelState)
workflow.add_node("supervisor", supervisor_node)
workflow.add_node("transport", transport_node)
workflow.add_node("lodging", lodging_node)
workflow.add_node("food", food_node)
workflow.add_node("attractions", attraction_node) 
workflow.add_node("integrator", integrator_node)
workflow.add_node("validation", validation_node)
workflow.add_node("react", react_decision_node)
workflow.add_node("planner", planner_node)
workflow.set_entry_point("supervisor")
workflow.add_edge("supervisor", "transport")
workflow.add_edge("transport", "lodging")
workflow.add_edge("lodging", "food")
workflow.add_edge("food", "attractions")
workflow.add_edge("attractions", "integrator")
workflow.add_edge("integrator", "validation")
workflow.add_conditional_edges("validation", lambda x: "pass" if x["validation"]["passed"] else "fail", {"pass": "planner", "fail": "react"})
workflow.add_conditional_edges("react", lambda x: x.get("react_decision", "planner"), {"transport": "transport", "lodging": "lodging", "food": "food", "attraction": "attractions", "planner": "planner"})

app = workflow.compile()

if __name__ == "__main__":
    my_request = sys.argv[1] if len(sys.argv) > 1 else "부산 1박2일 70만원 역사명소 위주"
    app.invoke({"user_query": my_request, "retry_count": 0})