package com.mockdata.zipfit.listinglink;

import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.condition.entity.HouseCondition;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 네이버 부동산, 직방 (FR-041).
 * TODO(PRD Q6): 사이트별 가격·면적·방 구조 파라미터를 확인하면 supported와 buildUrl에 반영한다.
 * 지금은 네이버 부동산 지도 위치(ms=위도,경도,줌)만 넘기고, 직방은 지도 메인으로 연결한다.
 */
@Configuration
public class ListingSites {

	@Bean
	public ListingSite naverLand() {
		return new ListingSite() {
			@Override
			public String code() {
				return "NAVER_LAND";
			}

			@Override
			public String displayName() {
				return "네이버 부동산";
			}

			@Override
			public Set<ListingCondition> supported() {
				return Set.of(ListingCondition.LOCATION);
			}

			@Override
			public String buildUrl(Candidate candidate, HouseCondition condition) {
				return String.format(Locale.ROOT, "https://new.land.naver.com/rooms?ms=%.6f,%.6f,16",
					candidate.getLatitude(), candidate.getLongitude());
			}

			@Override
			public String fallbackUrl() {
				return "https://new.land.naver.com/";
			}
		};
	}

	@Bean
	public ListingSite zigbang() {
		return new ListingSite() {
			@Override
			public String code() {
				return "ZIGBANG";
			}

			@Override
			public String displayName() {
				return "직방";
			}

			@Override
			public Set<ListingCondition> supported() {
				return Set.of();
			}

			@Override
			public String buildUrl(Candidate candidate, HouseCondition condition) {
				return "https://www.zigbang.com/home/oneroom/map";
			}

			@Override
			public String fallbackUrl() {
				return "https://www.zigbang.com/";
			}
		};
	}
}
