package com.mockdata.zipfit.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로컬 디스크 저장소. 저장한 파일은 {@code /uploads/**} 경로로 서빙한다 (WebConfig). */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

	public static final String URL_PATH = "/uploads/";

	@Getter
	private final Path root;
	private final String urlPrefix;

	public LocalFileStorage(
		@Value("${storage.local.dir}") String dir,
		@Value("${storage.public-base-url}") String publicBaseUrl
	) {
		this.root = Paths.get(dir).toAbsolutePath().normalize();
		this.urlPrefix = publicBaseUrl.replaceAll("/+$", "") + URL_PATH;
	}

	@Override
	public String store(String directory, byte[] content, String extension) {
		// 파일 이름은 서버가 만든 UUID만 쓴다 (사용자 입력 파일명으로 경로를 조작할 수 없게)
		String relativePath = directory + "/" + UUID.randomUUID() + "." + extension;
		Path target = root.resolve(relativePath).normalize();
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		} catch (IOException e) {
			throw new UncheckedIOException("파일 저장 실패: " + target, e);
		}
		return urlPrefix + relativePath;
	}

	@Override
	public void delete(String url) {
		if (url == null || !url.startsWith(urlPrefix)) {
			return;
		}
		Path target = root.resolve(url.substring(urlPrefix.length())).normalize();
		if (!target.startsWith(root)) {
			return;
		}
		try {
			Files.deleteIfExists(target);
		} catch (IOException e) {
			log.warn("파일 삭제 실패: {}", target, e);
		}
	}
}
