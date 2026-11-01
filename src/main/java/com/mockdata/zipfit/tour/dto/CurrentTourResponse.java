package com.mockdata.zipfit.tour.dto;

/** TOUR-03 진행 중인 투어가 없으면 tour가 null이다. */
public record CurrentTourResponse(TourResponse tour) {
}
