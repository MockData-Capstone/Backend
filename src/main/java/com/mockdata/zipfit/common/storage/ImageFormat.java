package com.mockdata.zipfit.common.storage;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 허용하는 이미지 형식. 확장자나 Content-Type은 조작할 수 있어서 파일 앞부분(매직 넘버)으로 판별한다. */
@Getter
@RequiredArgsConstructor
public enum ImageFormat {
	JPEG("jpg"),
	PNG("png"),
	WEBP("webp");

	private final String extension;

	public static Optional<ImageFormat> detect(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
			return Optional.of(JPEG);
		}
		if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
			return Optional.of(PNG);
		}
		if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
			&& bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}
}
