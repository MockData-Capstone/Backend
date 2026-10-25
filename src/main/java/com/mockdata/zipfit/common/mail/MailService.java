package com.mockdata.zipfit.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 메일 발송. 비동기로 보내 요청 응답 시간이 메일 발송에 영향받지 않게 한다
 * (비밀번호 재설정에서 응답 시간으로 가입 여부가 드러나지 않게 하기 위함).
 * spring.mail.host 를 설정하지 않으면(로컬) 메일 대신 로그로 내용을 출력한다.
 */
@Slf4j
@Service
public class MailService {

	private final ObjectProvider<JavaMailSender> mailSender;
	private final String from;

	public MailService(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Async
	public void send(String to, String subject, String text) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			log.info("[메일 서버 미설정 — 로그로 대신 출력]\nTo: {}\nSubject: {}\n{}", to, subject, text);
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		try {
			sender.send(message);
		} catch (MailException e) {
			log.warn("메일 발송 실패. to={}, subject={}", to, subject, e);
		}
	}
}
