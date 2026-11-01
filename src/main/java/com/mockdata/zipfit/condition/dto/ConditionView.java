package com.mockdata.zipfit.condition.dto;

/**
 * COND-02 조건 확정 화면
 *
 * @param condition 확정한 조건. 아직 없으면 null (조건 확정을 안내한다)
 * @param draft     화면을 미리 채울 초기값: 최근 AI 분석에서 추출한 조건과 받아들인 조건 제안(REC-03).
 *                  추출한 조건이 없으면 null이고 빈 화면에서 직접 입력한다.
 */
public record ConditionView(ConditionResponse condition, ConditionRequest draft) {
}
