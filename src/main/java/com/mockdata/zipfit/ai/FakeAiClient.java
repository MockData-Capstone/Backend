package com.mockdata.zipfit.ai;

import com.mockdata.zipfit.ai.dto.AiRecommendationRequest;
import com.mockdata.zipfit.ai.dto.AiRecommendationResponse;
import com.mockdata.zipfit.ai.dto.Confidence;
import com.mockdata.zipfit.ai.dto.ExtractedConditions;
import com.mockdata.zipfit.ai.dto.RoomType;
import com.mockdata.zipfit.ai.dto.SlopeGrade;
import com.mockdata.zipfit.ai.dto.SlopeRequest;
import com.mockdata.zipfit.ai.dto.SlopeResponse;
import com.mockdata.zipfit.ai.dto.VehicleAccess;
import com.mockdata.zipfit.ai.dto.VehicleAccessRequest;
import com.mockdata.zipfit.ai.dto.VehicleAccessResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 서버가 생기기 전까지 쓰는 가짜 구현. ai.base-url이 비어 있으면 쓴다.
 * 대화에서 키워드로 조건을 뽑고, 지역 중심 좌표 주변에 추천 위치 3곳을 만든다.
 * 경사도·진입성은 좌표로 정해지는 값이며 신뢰도는 항상 LOW(추정치)다. 실제 분석 결과가 아니다.
 */
public class FakeAiClient implements AiClient {

	private static final String SOURCE = "FAKE (AI 서버 연동 전 임시값)";

	/** 지역 이름 → 중심 좌표 */
	private static final Map<String, double[]> REGIONS = new LinkedHashMap<>();

	static {
		REGIONS.put("성수", new double[] {37.5446, 127.0559});
		REGIONS.put("왕십리", new double[] {37.5612, 127.0371});
		REGIONS.put("건대", new double[] {37.5404, 127.0692});
		REGIONS.put("홍대", new double[] {37.5572, 126.9245});
		REGIONS.put("신촌", new double[] {37.5551, 126.9369});
		REGIONS.put("강남", new double[] {37.4979, 127.0276});
	}

	private static final Pattern DEPOSIT = Pattern.compile("보증금\\s*(\\d+)\\s*(천|만)");
	private static final Pattern MONTHLY_RENT = Pattern.compile("월세\\s*(\\d+)\\s*만");
	private static final List<String> FACILITIES = List.of("편의점", "헬스장", "마트", "카페", "세탁소", "병원");

	@Override
	public AiRecommendationResponse recommend(AiRecommendationRequest request) {
		String conversation = request.messages().stream()
			.map(AiRecommendationRequest.Message::content)
			.collect(Collectors.joining("\n"));
		ExtractedConditions conditions = extract(conversation);
		if (conditions.isEmpty()) {
			return new AiRecommendationResponse(conditions, List.of());
		}
		String region = conditions.region() == null ? "성수" : conditions.region();
		double[] center = REGIONS.get(region);
		List<AiRecommendationResponse.Place> places = new ArrayList<>();
		String[] suffixes = {"역 인근 주택가", "공원 옆 골목", "대로변 신축 빌라"};
		for (int i = 0; i < suffixes.length; i++) {
			places.add(new AiRecommendationResponse.Place(
				region + suffixes[i],
				round(center[0] + (i - 1) * 0.003),
				round(center[1] + (i - 1) * 0.004),
				"%s 조건으로 찾은 예시 위치입니다. (AI 서버 연동 전 임시 추천)".formatted(region),
				null
			));
		}
		return new AiRecommendationResponse(conditions, places);
	}

	@Override
	public SlopeResponse estimateSlope(SlopeRequest request) {
		double average = 1.0 + seed(request.latitude(), request.longitude()) % 90 / 10.0;
		double max = Math.round((average + 3.0) * 10) / 10.0;
		String destination = request.destinations().isEmpty() ? "가장 가까운 지하철역" : request.destinations().getFirst();
		return new SlopeResponse(average, max, SlopeGrade.of(max), destination, SOURCE, Confidence.LOW);
	}

	@Override
	public VehicleAccessResponse estimateVehicleAccess(VehicleAccessRequest request) {
		VehicleAccess access = VehicleAccess.values()[(int) (seed(request.latitude(), request.longitude()) % 3)];
		String reason = switch (access) {
			case POSSIBLE -> "도로 폭이 넓어 차량이 드나들 수 있습니다. (임시값)";
			case LIMITED -> "도로 폭이 좁아 차량 교행이 어렵습니다. (임시값)";
			case IMPOSSIBLE -> "계단이 있어 차량이 들어갈 수 없습니다. (임시값)";
			case UNKNOWN -> "판단할 자료가 없습니다.";
		};
		return new VehicleAccessResponse(access, reason, SOURCE, Confidence.LOW);
	}

	private static ExtractedConditions extract(String conversation) {
		String region = REGIONS.keySet().stream().filter(conversation::contains).findFirst().orElse(null);
		Integer deposit = null;
		Matcher depositMatcher = DEPOSIT.matcher(conversation);
		if (depositMatcher.find()) {
			int amount = Integer.parseInt(depositMatcher.group(1));
			deposit = "천".equals(depositMatcher.group(2)) ? amount * 1000 : amount;
		}
		Integer monthlyRent = null;
		Matcher rentMatcher = MONTHLY_RENT.matcher(conversation);
		if (rentMatcher.find()) {
			monthlyRent = Integer.parseInt(rentMatcher.group(1));
		}
		RoomType roomType = conversation.contains("쓰리룸") ? RoomType.THREE_ROOM_OR_MORE
			: conversation.contains("투룸") ? RoomType.TWO_ROOM
			: conversation.contains("원룸") ? RoomType.ONE_ROOM
			: null;
		List<String> facilities = FACILITIES.stream().filter(conversation::contains).toList();
		Boolean avoidSlope = containsAny(conversation, "오르막", "언덕", "경사") ? Boolean.TRUE : null;
		Boolean needVehicleAccess = containsAny(conversation, "주차", "차량", "차 가지고", "차가") ? Boolean.TRUE : null;
		return new ExtractedConditions(
			region, null, deposit, null, monthlyRent, roomType, null, null, facilities, List.of(), avoidSlope,
			needVehicleAccess
		);
	}

	private static boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private static long seed(double latitude, double longitude) {
		return Math.abs(Math.round(latitude * 10_000) * 31 + Math.round(longitude * 10_000));
	}

	private static double round(double value) {
		return Math.round(value * 1_000_000) / 1_000_000.0;
	}
}
