package com.mockdata.zipfit.common.storage;

/** 업로드 파일 저장소. 지금은 로컬 디스크({@link LocalFileStorage}), 배포 시 S3 구현으로 바꾼다. */
public interface FileStorage {

	/**
	 * @param directory 저장할 하위 폴더 (예: profile-images)
	 * @return 브라우저에서 접근할 수 있는 공개 URL
	 */
	String store(String directory, byte[] content, String extension);

	/** 이 저장소가 만든 URL이면 파일을 지운다. 외부 URL(카카오 CDN 등)이나 null은 무시한다. */
	void delete(String url);
}
